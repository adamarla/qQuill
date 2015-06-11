package com.gradians.pipeline

import groovy.swing.SwingBuilder;

import java.awt.MediaTracker
import java.awt.BorderLayout as BL
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.nio.file.Path
import javax.swing.Action
import javax.swing.BorderFactory
import javax.swing.ImageIcon
import javax.swing.JOptionPane
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JFileChooser
import javax.swing.JTextArea
import javax.swing.KeyStroke
import javax.swing.event.UndoableEditEvent
import javax.swing.event.UndoableEditListener
import javax.swing.filechooser.FileFilter
import javax.swing.border.TitledBorder
import javax.swing.filechooser.FileView
import javax.swing.text.JTextComponent
import javax.swing.text.Keymap
import javax.swing.text.TextAction
import javax.swing.undo.UndoManager

import org.scilab.forge.jlatexmath.TeXConstants
import org.scilab.forge.jlatexmath.TeXFormula
import org.scilab.forge.jlatexmath.TeXIcon

import org.apache.batik.swing.JSVGCanvas

import static java.awt.BorderLayout.EAST
import static java.awt.GridBagConstraints.VERTICAL
import static java.awt.GridBagConstraints.HORIZONTAL
import static java.awt.GridBagConstraints.BOTH
import static java.awt.GridBagConstraints.CENTER
import static javax.swing.JFrame.EXIT_ON_CLOSE

class Editor {
    
    SwingBuilder sb
    Question q
    
    // Standalone widget handles
    def taQsnTeX, taAnsTeX
    def taContext, taReason, taRight, taWrong
    
    def Editor(Question q) {
        this.q = q
        taAnsTeX = new LaTeXArea[4]
        taContext = new LaTeXArea[6] 
        taReason = new LaTeXArea[6] 
        taRight = new LaTeXArea[6] 
        taWrong = new LaTeXArea[6]
    }
    
    def launch() {
        sb = new SwingBuilder()
        sb.edt {
            lookAndFeel: 'MetalLookAndFeel'
            frame(title: "${q.uid}(${q.bundle})", size: [800, 600], show: true, locationRelativeTo: null,
                resizable: false, defaultCloseOperation: EXIT_ON_CLOSE) {
                panel() {
                    gridBagLayout()
                    
                    tabbedPane(id: 'tpTeX', border: BorderFactory.createTitledBorder("TeX"),
                        constraints: gbc(gridx: 0, gridy: 0, weightx: 1.0, weighty: 1, fill: BOTH)) {      
                        panel(id: 'pnlQsnAns', name: 'Q / A') { qsnAnsTeX() }
                        [1, 2, 3, 4, 5, 6].each { idx ->
                            panel(id: "pnlStep${idx}", name: "Step ${idx}") { stepTeX(idx) }
                        }
                    }
                        
                    panel(id: 'pnlButtons', border: BorderFactory.createTitledBorder("Actions"),
                        constraints: gbc(gridx: 0, gridy: 1, weightx: 1.0, fill: HORIZONTAL)) {
                        button(text: 'Preview', actionPerformed: previewAll)
                        button(text: 'Save', actionPerformed: save)
                        button(text: 'Render', actionPerformed: render)
                        button(text: 'Tag', actionPerformed: tag)
                    }
                }
            }
        }
        
        sb.cbAns.selectedIndex = q.choices == null ? 0 : q.choices.correct
    }
    
    private def qsnAnsTeX = {
        taQsnTeX = new LaTeXArea(q.statement.tex, 8, 40)
        sb.vbox() {
            sb.vbox(constraints: BL.EAST, border: BorderFactory.createTitledBorder("Problem Statement")) {
                sb.scrollPane() {
                    widget(taQsnTeX)
                }
                sb.panel() {
                    sb.button(id: 'btnFile', text: 'Image (optional):',
                        actionPerformed: { setImage('lblFile', q.qpath) }
                    )
                    sb.label(id: 'lblFile', text: q.statement.image)
                }
            }
            sb.vbox(constraints: BL.EAST, border: BorderFactory.createTitledBorder("Answer Choices")) {
                [['A', 'B'], ['C', 'D']].each { set ->
                    sb.panel() {
                        set.each { option ->
                            def idx = (int)((char)option) - (int)'A'
                            taAnsTeX[idx] = new LaTeXArea(q.choices != null ? q.choices.texs[idx] : "", 4, 32)
                            taAnsTeX[idx].setBorder(BorderFactory.createTitledBorder("${option}"))
                            widget(taAnsTeX[idx])
                        }
                    }
                }
            }
            sb.panel() {
                sb.label(text: 'Correct Option')
                sb.comboBox(id: 'cbAns', items: ['A', 'B', 'C', 'D'])
            }
        }
    }
    
    private def stepTeX = { int idx ->
        def step
        if (idx > q.steps.size()) {
            step = new Step()
        } else {
            step = q.steps.get(idx-1)
        }
        taContext[idx-1] = new LaTeXArea(step.context, 4, 32)
        taReason[idx-1] = new LaTeXArea(step.reason, 4, 32)
        taRight[idx-1] = new LaTeXArea(step.texRight, 10, 32)
        taWrong[idx-1] = new LaTeXArea(step.texWrong, 10, 32)

        sb.vbox(constraints: BL.EAST) {
            sb.panel() {
                sb.scrollPane(border: BorderFactory.createTitledBorder("Context")) {
                    widget(taContext[idx-1])                    
                }
                sb.scrollPane(border: BorderFactory.createTitledBorder("Reason")) {
                    widget(taReason[idx-1])
                }
            }
            sb.panel(border: BorderFactory.createTitledBorder("Options")) {
                ["Right", "Wrong"].each { side ->
                    sb.vbox(border: BorderFactory.createTitledBorder("${side}")) {
                        sb.scrollPane() {
                            widget(side.equals("Right") ? taRight[idx-1] : taWrong[idx-1])
                        }
                        sb.panel() {
                            sb.button(id: "btn${side}File${idx}", text: 'Image (optional):',
                                actionPerformed: { setImage("lbl${side}File${idx}", q.qpath) })
                            sb.label(id: "lbl${side}File${idx}", text: step."image${side}")
                        }    
                    }
                }
            }
            sb.panel() {
                sb.checkBox(id: "chkBxSwipe${idx}", text: 'No Swipe', selected: step.noswipe)
            }
        }
    }
    
    private def previewAll = {
        updateModel()
        (new Renderer(q)).toSwing()
    }
    
    private def save = {
        updateModel()
        (new Renderer(q)).toXMLString()
        sb.optionPane().showMessageDialog(null, "Saved!", "Result", JOptionPane.INFORMATION_MESSAGE)
    }
    
    private def render = {
        try {
            (new Renderer(q, 12)).toSVG()
        } catch (Exception e) {
            println e
        }
        sb.optionPane().showMessageDialog(null, "Rendered!", "Result", JOptionPane.INFORMATION_MESSAGE)
    }
    
    private def setImage = { String it, Path qpath ->
        def openSVGDialog = new JFileChooser(
            dialogTitle: "SVG Only", fileSelectionMode : JFileChooser.FILES_ONLY,
            fileFilter: [getDescription: { -> "*.svg" }, accept:{ file -> file.getName() ==~ /.*?\.svg/ }] as FileFilter)
        openSVGDialog.setCurrentDirectory(qpath.toFile())
        openSVGDialog.setFileView(new FileView() {
            @Override
            public Boolean isTraversable(File f) {
                return (f.toPath().equals(qpath))
            }
        })
        if (openSVGDialog.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            sb."${it}".text = openSVGDialog.getSelectedFile().getName()
        }
    }
    
    private def updateModel = {
        Statement statement = new Statement()
        statement.tex = taQsnTeX.text.trim()
        statement.image = sb.lblFile.text
        q.statement = statement
        
        Step step
        [1, 2, 3, 4, 5, 6].each { idx ->
            if (taContext[idx-1].text.length() > 0 || idx == 1) {
                step = new Step()
                step.context = taContext[idx-1].text
                step.texRight = taRight[idx-1].text
                step.imageRight = sb."lblRightFile${idx}".text
                step.texWrong = taWrong[idx-1].text
                step.imageWrong = sb."lblWrongFile${idx}".text
                step.reason = taReason[idx-1].text
                step.noswipe = sb."chkBxSwipe${idx}".selected
                
                if (q.steps.size() > idx-1)
                    q.steps.set(idx-1, step)                    
                else
                    q.steps.add(idx-1, step)
            } else {
                if (q.steps.size() > idx-1)
                    q.steps.remove(idx-1)
            }
        }
        
        if (taAnsTeX[0].text.length() > 0) {
            Choices choices = new Choices()
            ['A', 'B', 'C', 'D'].eachWithIndex { option, idx ->  
                choices.texs[idx] = taAnsTeX[idx].text
            }
            choices.correct = sb.cbAns.selectedIndex
            q.choices = choices
        } else {
            q.choices = null
        }        
    }

    private def tag = {
        try {
            (new Tagger(q.qpath.getParent())).go()
        } catch (Exception e) {
            println e
        }
    }
    
}

class LaTeXArea extends JTextArea {
    
    UndoManager undoManager    
    public LaTeXArea(String tex, int row, int col) {
        super(tex, row, col)
        undoManager = new UndoManager()
        
        getDocument().addUndoableEditListener(
            new UndoableEditListener() {
                public void undoableEditHappened(UndoableEditEvent e) {
                    undoManager.addEdit(e.getEdit())
                }
            })        
        addCtrlKeys()
    }
    
    private def addCtrlKeys() {
        Keymap latexMap = JTextComponent.addKeymap("LaTeXMap", this.keymap)
        KeyStroke t = KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK)
        latexMap.addActionForKeyStroke(t, new LaTeXAction("\\text{}", 1))
        KeyStroke p = KeyStroke.getKeyStroke(KeyEvent.VK_9, InputEvent.CTRL_DOWN_MASK|InputEvent.SHIFT_DOWN_MASK)
        latexMap.addActionForKeyStroke(p, new LaTeXAction("\\left(\\right)", 7))
        KeyStroke b = KeyStroke.getKeyStroke(KeyEvent.VK_OPEN_BRACKET, InputEvent.CTRL_DOWN_MASK)
        latexMap.addActionForKeyStroke(b, new LaTeXAction("\\left[\\right]", 7))
        KeyStroke d = KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK)
        latexMap.addActionForKeyStroke(d, new LaTeXAction("\\dfrac{}{}", 3))
        KeyStroke A = KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK|InputEvent.SHIFT_DOWN_MASK)
        latexMap.addActionForKeyStroke(A, new LaTeXAction("\n\\begin{align}\n\\end{align}", 11))
        KeyStroke z = KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK)
        latexMap.addActionForKeyStroke(z, new LaTeXAction("undo", 0))
        KeyStroke Z = KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK|InputEvent.SHIFT_DOWN_MASK)
        latexMap.addActionForKeyStroke(Z, new LaTeXAction("redo", 0))
        KeyStroke la = KeyStroke.getKeyStroke(KeyEvent.VK_COMMA, InputEvent.CTRL_DOWN_MASK|InputEvent.SHIFT_DOWN_MASK)
        latexMap.addActionForKeyStroke(la, new LaTeXAction("\\leftarrow", 0))
        KeyStroke ra = KeyStroke.getKeyStroke(KeyEvent.VK_PERIOD, InputEvent.CTRL_DOWN_MASK|InputEvent.SHIFT_DOWN_MASK)
        latexMap.addActionForKeyStroke(ra, new LaTeXAction("\\rightarrow", 0))
        KeyStroke i = KeyStroke.getKeyStroke(KeyEvent.VK_I, InputEvent.CTRL_DOWN_MASK)
        latexMap.addActionForKeyStroke(i, new LaTeXAction("\\implies", 0))
        this.keymap = latexMap
    }
    
}

class LaTeXAction extends TextAction {
    
    String latex
    int offset
    
    public LaTeXAction(String latex, int offset) {
        super("laTeX-action")
        this.latex = latex
        this.offset = offset
    }
    
    @Override
    void actionPerformed(ActionEvent ae) {
        JTextArea comp = (JTextArea)getTextComponent(ae)
        if (comp == null)
          return
        
        if (latex.equals("undo")) {
            if (ae.getSource().undoManager.canUndo()) {
                ae.getSource().undoManager.undo()
            }
        } else if (latex.equals("redo")) {
            if (ae.getSource().undoManager.canRedo()) {
                ae.getSource().undoManager.redo()
            }
        } else {
            comp.insert(latex, comp.getCaretPosition())
            comp.setCaretPosition(comp.getCaretPosition() - offset)
        }          
    }

}
