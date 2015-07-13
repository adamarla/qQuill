package com.gradians.pipeline

import groovy.swing.SwingBuilder

import java.awt.BorderLayout as BL
import java.nio.file.Path

import javax.swing.BorderFactory
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.filechooser.FileFilter
import javax.swing.filechooser.FileView

import org.fife.ui.rsyntaxtextarea.SyntaxConstants
import org.fife.ui.rtextarea.RTextScrollPane

import static java.awt.BorderLayout.EAST
import static java.awt.GridBagConstraints.NONE
import static java.awt.GridBagConstraints.BOTH
import static java.awt.GridBagConstraints.CENTER
import static java.awt.GridBagConstraints.HORIZONTAL
import static java.awt.GridBagConstraints.VERTICAL
import static javax.swing.JFrame.EXIT_ON_CLOSE
import static javax.swing.WindowConstants.DISPOSE_ON_CLOSE

class Editor {
    
    public static final String VERSION = "1.4"
    
    SwingBuilder sb
    Question q
    
    // Standalone widget handles
    def taQsnTeX, taAnsTeX
    def taContext, taReason, taCorrect, taIncorrect
    
    def Editor(Question q) {
        this.q = q
        taAnsTeX = new LaTeXArea[4]
        taContext = new LaTeXArea[6] 
        taReason = new LaTeXArea[6]
        taCorrect = new LaTeXArea[6]
        taIncorrect = new LaTeXArea[6]
        
        renderer = new Renderer(this.q)
        LaTeXArea.editor = this
    }
    
    def launch(boolean topLevel = true) {
        sb = new SwingBuilder()
        sb.edt {
            frame(title: "Quill (${VERSION}) - ${q.uid} (${q.bundle})", 
                size: [960, 600], show: true, locationRelativeTo: null, resizable: false, 
                defaultCloseOperation: topLevel ? EXIT_ON_CLOSE : DISPOSE_ON_CLOSE) {                
                panel() {
                    gridBagLayout()
                                        
                    // left panel
                    vbox(border: BorderFactory.createTitledBorder("Edit"),
                        constraints: gbc(gridx: 0, gridy: 0, gridwidth: 1)) {
                        
                        panel(id: 'pnlControls') {
                            checkBox(id: 'chkBxSpellCheck', text: 'Spell Check', selected: false, 
                                actionPerformed: toggleSpellCheck)
                            button(text: 'Clear', actionPerformed: clearCurrent)
                            button(text: 'Duplicate', actionPerformed: duplicateStep)
                            button(text: 'Insert', actionPerformed: insertStep)
                            button(text: 'Delete', actionPerformed: deleteStep)
                        }
                        
                        tabbedPane(id: 'tpTeX', changeListener: tabClicked) {      
                            panel(id: 'pnlQsnAns', name: 'Q&A') { qsnAnsTeX() }
                            [1, 2, 3, 4, 5, 6].each { idx ->
                                panel(id: "pnlStep${idx}", name: "Step ${idx}") { stepTeX(idx-1) }
                            }
                        }                    
                    }
                    
                    // right panel
                    vbox(constraints: gbc(gridx: 1, gridy: 0, gridwidth: 1, 
                        weightx: 1.0, weighty: 1.0, fill: BOTH)) {
                        panel(id: 'pnlDisplay', border: BorderFactory.createTitledBorder("Preview"))
                    }
                        
                    // bottom panel
                    panel(border: BorderFactory.createTitledBorder("Actions"),
                        constraints: gbc(gridx: 0, gridy: 2, gridwidth: 2, weightx: 1.0, fill: HORIZONTAL)) {
                        button(text: 'Preview', actionPerformed: previewAll)
                        button(id: 'btnSave', text: 'Save', actionPerformed: save)
                        button(id: 'btnRender', text: 'Render', actionPerformed: render)
                        button(text: 'Tag', actionPerformed: tag)
                        button(text: 'Refresh', actionPerformed: previewStep)
                    }
                }
            }
        }
        sb.cbAns.selectedIndex = q.choices == null ? 0 : q.choices.correct
    }
    
    private def qsnAnsTeX = {
        taQsnTeX = LaTeXArea.getInstance(q.statement.tex, 18, 36)
        sb.tabbedPane(id: 'tpQsnAns') {
            sb.vbox(constraints: BL.EAST, name: "Problem Statement") {
                widget(new RTextScrollPane(taQsnTeX, true), name: "Problem")
                sb.panel() {
                    sb.button(id: 'btnFile', text: 'Image (optional):',
                        actionPerformed: { setImage('lblFile', q.qpath) }
                    )
                    sb.label(id: 'lblFile', text: q.statement.image)
                }
            }
            sb.vbox(constraints: BL.EAST, name: "Answer Choices") {
                ['A', 'B', 'C', 'D'].each { option ->
                    def idx = (int)((char)option) - (int)'A'
                    taAnsTeX[idx] = LaTeXArea.getInstance(q.choices != null ? q.choices.texs[idx] : "", 4, 36)
                    taAnsTeX[idx].setBorder(BorderFactory.createTitledBorder("${option}"))
                    widget(new RTextScrollPane(taAnsTeX[idx], true))
                }
                sb.panel() {
                    sb.label(text: 'Correct Option')
                    sb.comboBox(id: 'cbAns', items: ['A', 'B', 'C', 'D'])
                }
            }
        }
    }
    
    private def stepTeX = { int idx ->
        def step = q.steps[idx]
        if (step == null)
            step = new Step()
        
        taContext[idx] = LaTeXArea.getInstance(step.context, 18, 36)
        taReason[idx] = LaTeXArea.getInstance(step.reason, 18, 36)
        taCorrect[idx] = LaTeXArea.getInstance(step.texCorrect, 18, 36)
        taIncorrect[idx] = LaTeXArea.getInstance(step.texIncorrect, 18, 36)

        sb.vbox(constraints: BL.EAST) {
            sb.checkBox(id: "chkBxSwipe${idx}", text: 'No Swipe', selected: step.noswipe)
            sb.tabbedPane(id: "tpStep${idx}") {
                sb.widget(new RTextScrollPane(taContext[idx], true), name: "Context")
                ["Correct", "Incorrect"].each { side ->
                    sb.vbox(name: "${side}") {
                        sb.widget(side.equals("Correct") ? 
                            new RTextScrollPane(taCorrect[idx], true) : 
                            new RTextScrollPane(taIncorrect[idx], true))
                        sb.panel() {
                            sb.button(text: 'Image (optional):',
                                actionPerformed: { setImage("lbl${side}File${idx}", q.qpath) })
                            sb.label(id: "lbl${side}File${idx}", text: step."image${side}")
                        }
                    }    
                }
                sb.widget(new RTextScrollPane(taReason[idx], true), name: "Reason / Takeaway")
            }
        }
    }
    
    private def previewAll = {
        updateModel()
        renderer.toSwing()
    }
    
    private def save = {
        sb.btnSave.enabled = false
        updateModel()
        renderer.toXMLString()
        sb.btnSave.enabled = true
    }
    
    private def render = {
        sb.btnRender.enabled = false
        try {
            renderer.toSVG()
        } catch (Exception e) {
            println e
        }
        sb.btnRender.enabled = true
    }
    
    private def tabClicked = {
        print "clicked"
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
    
    private def toggleSpellCheck = {
        boolean spellCheckOn = taQsnTeX.getSyntaxEditingStyle() == SyntaxConstants.SYNTAX_STYLE_NONE
        def style = spellCheckOn ? SyntaxConstants.SYNTAX_STYLE_LATEX : SyntaxConstants.SYNTAX_STYLE_NONE
        taQsnTeX.setSyntaxEditingStyle(style)
        if (spellCheckOn)
            taQsnTeX.removeParser(LaTeXArea.spellingParser)
        else
            taQsnTeX.addParser(LaTeXArea.spellingParser)
            
        [taAnsTeX, taContext, taCorrect, taIncorrect, taReason].each { LaTeXArea[] list ->
            list.each { LaTeXArea element ->
                element.setSyntaxEditingStyle(style)
                if (spellCheckOn)
                    element.removeParser(LaTeXArea.spellingParser)
                else
                    element.addParser(LaTeXArea.spellingParser)
            }
        }
    }
    
    private def previewStep = {
        sb.pnlDisplay.removeAll()
        updateModel()
        def stepIdx = sb.tpTeX.selectedIndex
        if (stepIdx == 0) {
            sb.pnlDisplay.add renderer.toPreview(sb, q.statement, q.choices)
        } else {
            stepIdx--
            sb.pnlDisplay.add renderer.toPreview(sb, q.steps[stepIdx], stepIdx)
        }
        sb.pnlDisplay.revalidate()
        sb.pnlDisplay.repaint()
    }
    
    private def clearCurrent = {
        def idx = sb.tpTeX.selectedIndex
        if (idx == 0) {
            clearQsnAns()
        } else {
            clearStep(--idx)
        }
    }
    
    private def duplicateStep = {
        def idx = sb.tpTeX.selectedIndex-1
        if (idx == 5)
            return
        shiftRight(idx)
    }
    
    private def insertStep = {
        def idx = sb.tpTeX.selectedIndex-1
        if (idx == 5)
            return
        shiftRight(idx)
        clearStep(idx)
    }
    
    private def deleteStep = {
        def idx = sb.tpTeX.selectedIndex-1
        shiftLeft(idx)
        clearStep(5)
    }

    private def updateModel = {        
        Statement statement = new Statement()
        statement.tex = taQsnTeX.text.trim()
        statement.image = sb.lblFile.text
        q.statement = statement
        
        [0, 1, 2, 3, 4, 5].each { idx ->
            def step = new Step()
            step.context = taContext[idx].text
            step.texCorrect = taCorrect[idx].text
            step.imageCorrect = sb."lblCorrectFile${idx}".text
            step.texIncorrect = taIncorrect[idx].text
            step.imageIncorrect = sb."lblIncorrectFile${idx}".text
            step.reason = taReason[idx].text
            step.noswipe = sb."chkBxSwipe${idx}".selected
            q.steps[idx] = step
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
    
    private def clearQsnAns() {
        taQsnTeX.text = ""
        taAnsTeX.each { LaTeXArea ta ->
            ta.text = ""
        }
    }
    
    private def clearStep(int idx) {
        taContext[idx].text = ""
        taCorrect[idx].text = ""
        taIncorrect[idx].text = ""
        taReason[idx].text = ""
        sb."lblCorrectFile${idx}".text = ""
        sb."lblIncorrectFile${idx}".text = ""
    }
    
    private def boolean shiftRight(int idx) {
        if (idx == 5)
            return false
        for (int i = 5; i > idx; i--) {
            taContext[i].text = taContext[i-1].text 
            taCorrect[i].text = taCorrect[i-1].text
            taIncorrect[i].text = taIncorrect[i-1].text
            taReason[i].text = taReason[i-1].text
            sb."lblCorrectFile${i}".text = sb."lblCorrectFile${(i-1)}".text
            sb."lblIncorrectFile${i}".text = sb."lblIncorrectFile${(i-1)}".text
            sb."chkBxSwipe${i}".selected = sb."chkBxSwipe${(i-1)}".selected
        }
    }
    
    private def boolean shiftLeft(int idx) {
        for (int i = idx; i < 5; i++) {
            taContext[i].text = taContext[i+1].text 
            taCorrect[i].text = taCorrect[i+1].text
            taIncorrect[i].text = taIncorrect[i+1].text
            taReason[i].text = taReason[i+1].text
            sb."lblCorrectFile${i}".text = sb."lblCorrectFile${(i+1)}".text
            sb."lblIncorrectFile${i}".text = sb."lblIncorrectFile${(i+1)}".text
            sb."chkBxSwipe${i}".selected = sb."chkBxSwipe${(i+1)}".selected
        }
    }
    
    private def tag = {
        try {
            (new Tagger(q.qpath.getParent())).go()
        } catch (Exception e) {
            println e
        }
    }
    
    private Renderer renderer
    
}
