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
import javax.swing.filechooser.FileFilter
import javax.swing.border.TitledBorder
import javax.swing.filechooser.FileView
import javax.swing.text.JTextComponent
import javax.swing.text.Keymap
import javax.swing.text.TextAction

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
    
    def Editor(Question q) {
        this.q = q
    }
    
    def launch() {
        sb = new SwingBuilder()
        sb.edt {
            lookAndFeel: 'MetalLookAndFeel'
            frame(title: q.uid, size: [860, 600], show: true, locationRelativeTo: null, 
                defaultCloseOperation: EXIT_ON_CLOSE) {
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
        sb.vbox() {
            sb.vbox(constraints: BL.EAST, border: BorderFactory.createTitledBorder("Problem Statement")) {
                sb.scrollPane() {
                    sb.textArea(id: 'taQsnTeX', text: q.statement.tex, rows: 8, columns: 72)
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
                            sb.textArea(id: "taAnsTex${(char)option}", rows: 4, columns: 36,
                                text: (q.choices != null ? q.choices.texs[idx] : ""),
                                border: BorderFactory.createTitledBorder("${option}"))
                        }
                    }
                }
            }
            sb.panel() {
                sb.label(text: 'Correct Option')
                sb.comboBox(id: 'cbAns', items: ['A', 'B', 'C', 'D'])
            }
        }
        sb.taQsnTeX.setKeymap(addCtrlKeys(sb.taQsnTeX.getKeymap()))
        ['A', 'B', 'C', 'D'].each { option ->
            sb."taAnsTex${option}".setKeymap(addCtrlKeys(sb."taAnsTex${option}".getKeymap()))            
        }
    }
    
    private def stepTeX = { int idx ->
        def step
        if (idx > q.steps.size()) {
            step = new Step()
        } else {
            step = q.steps.get(idx-1)
        }
        sb.vbox(constraints: BL.EAST) {            
            sb.panel() {
                ["Context", "Reason"].each { tex ->
                    sb.scrollPane(border: BorderFactory.createTitledBorder("${tex}")) {
                        sb.textArea(id: "ta${tex}${idx}", 
                            text: step."${tex.toLowerCase()}", rows: 6, columns: 36)
                    }
                    sb."ta${tex}${idx}".setKeymap(addCtrlKeys(sb."ta${tex}${idx}".getKeymap()))
                }
            }
            sb.panel(border: BorderFactory.createTitledBorder("Options")) {
                ["Right", "Wrong"].each { side ->
                    sb.vbox(border: BorderFactory.createTitledBorder("${side}")) {
                        sb.scrollPane() {
                            textArea(id: "ta${side}Step${idx}", 
                                text: step."tex${side}", rows: 10, columns: 36)
                        }
                        sb.panel() {
                            sb.button(id: "btn${side}File${idx}", text: 'Image (optional):',
                                actionPerformed: { setImage("lbl${side}File${idx}", q.qpath) })
                            sb.label(id: "lbl${side}File${idx}", text: step."image${side}")
                        }    
                    }
                    sb."ta${side}Step${idx}".setKeymap(addCtrlKeys(sb."ta${side}Step${idx}".getKeymap()))
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
        statement.tex = sb.taQsnTeX.text.trim()
        statement.image = sb.lblFile.text
        q.statement = statement
        
        Step step
        [1, 2, 3, 4, 5, 6].each { idx ->
            if (sb."taContext${idx}".text.length() > 0 ||
                idx < 3) {
                step = new Step()
                step.context = sb."taContext${idx}".text
                step.texRight = sb."taRightStep${idx}".text
                step.imageRight = sb."lblRightFile${idx}".text
                step.texWrong = sb."taWrongStep${idx}".text
                step.imageWrong = sb."lblWrongFile${idx}".text
                step.reason = sb."taReason${idx}".text
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
        
        if (sb.taAnsTexA.text.length() > 0) {
            Choices choices = new Choices()
            ['A', 'B', 'C', 'D'].eachWithIndex { option, idx ->  
                choices.texs[idx] = sb."taAnsTex${option}".text
            }
            choices.correct = sb.cbAns.selectedIndex
            q.choices = choices
        } else {
            q.choices = null
        }        
    }

    private def Keymap addCtrlKeys(Keymap kmap) {
        Keymap latexMap = JTextComponent.addKeymap("LaTeXMap", kmap)
        KeyStroke t = KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK)
        latexMap.addActionForKeyStroke(t, new LaTeXAction("\\text{}", 1))
        KeyStroke p = KeyStroke.getKeyStroke(KeyEvent.VK_9, InputEvent.CTRL_DOWN_MASK|InputEvent.SHIFT_DOWN_MASK)
        latexMap.addActionForKeyStroke(p, new LaTeXAction("\\left(\\right)", 7))
        KeyStroke b = KeyStroke.getKeyStroke(KeyEvent.VK_OPEN_BRACKET, InputEvent.CTRL_DOWN_MASK)
        latexMap.addActionForKeyStroke(b, new LaTeXAction("\\left[\\right]", 7))
        KeyStroke d = KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK)
        latexMap.addActionForKeyStroke(d, new LaTeXAction("\\dfrac{}{}", 3))
        KeyStroke a = KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK)
        latexMap.addActionForKeyStroke(a, new LaTeXAction("\n\\begin{align}\n\\end{align}", 11))
        latexMap
    }
    
    private def tag = {
        try {
            (new Tagger(q)).go()
        } catch (Exception e) {
            println e
        }
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
        comp.insert(latex, comp.getCaretPosition())
        comp.setCaretPosition(comp.getCaretPosition() - offset)
    }

}
