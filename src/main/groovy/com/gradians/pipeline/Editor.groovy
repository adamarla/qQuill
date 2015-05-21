package com.gradians.pipeline

import groovy.swing.SwingBuilder;

import java.awt.MediaTracker
import java.awt.BorderLayout as BL
import java.nio.file.Path
import javax.swing.BorderFactory
import javax.swing.ImageIcon
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JFileChooser
import javax.swing.filechooser.FileFilter
import javax.swing.border.TitledBorder
import javax.swing.filechooser.FileView

import org.scilab.forge.jlatexmath.TeXConstants
import org.scilab.forge.jlatexmath.TeXFormula
import org.scilab.forge.jlatexmath.TeXIcon

import org.apache.batik.swing.JSVGCanvas

import static java.awt.BorderLayout.EAST
import static java.awt.GridBagConstraints.VERTICAL
import static java.awt.GridBagConstraints.HORIZONTAL
import static java.awt.GridBagConstraints.BOTH

class Editor {
    
    SwingBuilder sb
    Question q
    
    def Editor(SwingBuilder sb) {
        this.sb = sb
    }
    
    def launch(Question q) {
        def panel = sb.panel() {
            gridBagLayout()
            
            tabbedPane(id: 'tpQsn', border: BorderFactory.createTitledBorder("TeX"),
                constraints: gbc(gridx: 0, gridy: 0, gridwidth: 2, 
                    weightx: 1.0, weighty: 0.4, fill: BOTH)) {
                panel(id: 'pnlQsnAns', name: 'Q / A') { qsnAnsTeX(q) }
                [1, 2, 3, 4, 5, 6].each { idx ->
                    panel(id: "pnlStep${idx}", name: "Step ${idx}") { stepTeX(q, idx) }
                }
            }
                
            panel(id: 'pnlPreview', border: BorderFactory.createTitledBorder("Preview"),
                constraints: gbc(gridx: 0, gridy: 1, weightx: 1.0, weighty: 0.6, fill: BOTH)) {
                vbox(constraints: BL.EAST) {                    
                    panel(id: 'pnlContext')
                    panel(id: 'pnlStep') {
                        panel(id: 'pnlPrevWrong', border: BorderFactory.createTitledBorder("Wrong")) {
                            scrollPane {
                                vbox(id: 'vbPrevWrong', constraints: BL.EAST)
                            }
                        }            
                        panel(id: 'pnlPrevRight', border: BorderFactory.createTitledBorder("Right")) {
                            scrollPane {
                                vbox(id: 'vbPrevRight', constraints: BL.EAST)
                            }
                        }
                    }                    
                    panel(id: 'pnlReason')
                }
            }

            panel(id: 'pnlButtons', border: BorderFactory.createTitledBorder("Actions"),
                constraints: gbc(gridx: 0, gridy: 2, gridwidth: 2, weightx: 1.0, fill: HORIZONTAL)) {
                button(id: 'btnPreview', text: 'Preview', actionPerformed: displayPreview)
                button(id: 'btnSave', text: 'Save', actionPerformed: save)
            }
                                
        }
        
        JDialog dialog = new JDialog(title: "Editor", size: [720, 600],
            defaultCloseOperation: JDialog.DISPOSE_ON_CLOSE)
        dialog.add panel
        dialog.setLocationByPlatform(true)
        dialog.setVisible(true)
        
        this.q = q
    }
    
    private def qsnAnsTeX = { Question q ->
        sb.vbox(constraints: BL.EAST) {
            sb.label(text: 'Problem Statement')
            sb.scrollPane() {
                sb.textArea(id: 'taQsnTex', text: q.statement.tex, rows: 12, columns: 30)
            }
            sb.panel() {
                sb.button(id: 'btnFile', 
                    text: 'Image (optional):', actionPerformed: { setImage('lblFile') }
                )
                sb.label(id: 'lblFile', text: q.statement.image)
            }
        }
        sb.vbox(constraints: BL.EAST) {
            sb.label(text: 'Answer Choices')
            sb.scrollPane() {
                sb.textArea(id: 'taAnsTex', q.choices.texs.join('\n'), rows: 12, columns: 30)
            }
            sb.panel() {
                sb.label(id: 'lblPacking', text: 'One Choice Per Line')                
            }
        }
    }
    
    private def stepTeX = { Question q, int idx ->
        def step
        if (idx > q.steps.size()) {
            step = new Step()
        } else {
            step = q.steps.get(idx-1)
        }
        sb.vbox(constraints: BL.EAST) {
            sb.scrollPane() {
                textArea(id: "taCntxt${idx}", text: step.context, rows: 3, columns: 30)
            }
            sb.panel() {
                ["Wrong", "Right"].each { side ->
                    sb.vbox(constraints: BL.EAST) {
                        sb.scrollPane() {
                            textArea(id: "ta${side}Step${idx}", 
                                text: step."tex${side}", rows: 6, columns: 30)
                        }
                        sb.panel() {
                            sb.button(id: "btn${side}File${idx}", text: 'Image (optional):',
                                actionPerformed: { setImage("lbl${side}File${idx}", q.qpath) })
                            sb.label(id: "lbl${side}File${idx}", text: step."image${side}")
                        }    
                    }
                }    
            }
            sb.scrollPane() {
                textArea(id: "taRsn${idx}", text: step.reason, rows: 3, columns: 30)
            }
        }    
    }
    
    private def displayPreview = {
        [sb.pnlContext, sb.pnlReason].each { it.removeAll() }
        ["Wrong", "Right"].each { side -> sb."vbPrev${side}".removeAll() }
        int idx = sb.tpQsn.getSelectedIndex() 
        switch (idx) {
            case 0: // Q and Choices
                sb.vbPrevWrong.add(sb.label(icon: teXToIcon(sb.taQsnTex.getText())))
                if (sb.lblFile.text.length() > 0)
                    sb.vbPrevWrong.add(fileToIcon(sb.lblFile.text))
                sb.vbPrevRight.add(sb.label(icon: teXToIcon(sb.taAnsTex.getText())))
                break
            default: // Steps
                sb.pnlContext.add(sb.label(icon: teXToIcon(sb."taCntxt${idx}".getText())))
                ["Wrong", "Right"].each { side ->
                    sb."vbPrev${side}".add(sb.label(icon: teXToIcon(sb."ta${side}Step${idx}".getText())))
                }
                sb.pnlReason.add(sb.label(icon: teXToIcon(sb."taRsn${idx}".getText())))
                sb.doLater { [sb.pnlContext, sb.pnlReason].each { it.revalidate() } }
                break
        }
        sb.doLater { ["Wrong", "Right"].each { side -> sb."pnlPrev${side}".revalidate() } }
    }
    
    private def setImage = { String it, Path qpath ->
        def openSVGDialog = new JFileChooser(
            dialogTitle: "SVG Only", fileSelectionMode : JFileChooser.FILES_ONLY,
            fileFilter: [getDescription: { -> "*.svg" }, accept:{ file -> file.getName() ==~ /.*?\.svg/ }] as FileFilter)
        openSVGDialog.setCurrentDirectory(qpath.toFile())
        openSVGDialog.setFileView(new FileView() {
            @Override
            public Boolean isTraversable(File f) {
                return (f.toPath().equals(qpath));
            }
        })
        if (openSVGDialog.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            sb."${it}".text = openSVGDialog.getSelectedFile().getName()
        }
    }
    
    private def save = {
        Statement statement = new Statement()
        statement.tex = sb.taQsnTex.text.trim().replace("\\\\", "\n")
        statement.image = sb.lblFile.text
        q.statement = statement
        
        Step step
        [1, 2, 3, 4, 5, 6].each { idx ->
            if (sb."taCntxt${idx}".text.length() > 0) {
                step = new Step()
                step.context = sb."taCntxt${idx}".text
                step.texRight = sb."taRightStep${idx}".text
                step.imageRight = sb."lblRightFile${idx}".text
                step.texWrong = sb."taWrongStep${idx}".text
                step.imageWrong = sb."lblWrongFile${idx}".text
                step.reason = sb."taRsn${idx}".text
                q.steps.set(idx-1, step)
            }
        }
        
        Choices choices = new Choices()
        def tokens = sb.taAnsTex.text.split("\n")
        tokens.eachWithIndex { choice, idx ->
            choices.texs[idx] = choice
        }
        choices.correct = 2
        q.choices = choices
        
        (new Renderer(q)).toXMLString(q.qpath)
        sb.optionPane().showMessageDialog(null, "Saved!", "Result", JOptionPane.INFORMATION_MESSAGE)        
    }
    
    private def TeXIcon teXToIcon(String tex) {
        TeXFormula formula
        TeXIcon texIcon
        tex = tex.replace("\n", "\\\\").replace("\\qquad", "\\quad\\quad")
        try {
            formula = new TeXFormula(tex)
        } catch (Exception e) {
            def s = splitEqually(e.getMessage()).join("\\\\")
            formula = new TeXFormula("\\text{${s}}")
        }
        formula.createTeXIcon(TeXConstants.STYLE_DISPLAY, 15, TeXFormula.SANSSERIF)
    }
    
    private def JSVGCanvas fileToIcon(String name) {
        JSVGCanvas svgCanvas = new JSVGCanvas()
        svgCanvas.setURI(q.qpath.resolve(name).toUri().toURL().toString())
        svgCanvas
    }
    
    private def String[] splitEqually(String s) {        
        int n = (s.length() / WIDTH) + 1
        def parts = new String[n]
        for (int i = 0; i < n; i++) {
            parts[i] = s.substring(i*WIDTH, 
                i == n-1 ? s.length() : (i+1)*WIDTH)
        }
        parts
    }
    
    private final int WIDTH = 35
    private final def TXT_NO_FILE = "No File Selected"
}
