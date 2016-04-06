package com.gradians.pipeline.edit

import groovy.swing.SwingBuilder

import java.awt.BorderLayout as BL
import java.awt.dnd.DropTarget
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTargetDropEvent
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable

import java.nio.file.Path
import java.nio.file.Files

import javax.swing.BorderFactory
import javax.swing.ImageIcon
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.JTabbedPane
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener
import javax.swing.filechooser.FileFilter
import javax.swing.filechooser.FileView

import org.apache.batik.swing.JSVGCanvas;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants
import org.fife.ui.rtextarea.RTextScrollPane

import com.gradians.pipeline.data.Artifact
import com.gradians.pipeline.data.Choices
import com.gradians.pipeline.data.Question
import com.gradians.pipeline.data.Statement
import com.gradians.pipeline.data.Step

import static java.awt.BorderLayout.EAST
import static java.awt.GridBagConstraints.NONE
import static java.awt.GridBagConstraints.BOTH
import static java.awt.GridBagConstraints.CENTER
import static java.awt.GridBagConstraints.HORIZONTAL
import static java.awt.GridBagConstraints.VERTICAL
import static javax.swing.JFrame.EXIT_ON_CLOSE
import static javax.swing.WindowConstants.DISPOSE_ON_CLOSE

/**
 * 
 * There are three kinds of objects that are kept in sync using
 * hashmaps declared near the top of the class. 
 * These are :
 * TextAreas (for displaying latex / svg file name)
 * Components (data structure for holding latex / svg file name)
 * Display (for displaying the rendered latex or prefab svg)
 * 
 * @author adamarla
 *
 */
class Editor {
    
    public static final String VERSION = "2.0"
    
    SwingBuilder sb, sbuilder
    IEditable e
    Artifact a
    Panel[] panels
    Map textToComponent, componentToDisplay, componentToText
    Renderer r
        
    def Editor(Question q) {
        e = (IEditable)q
        a = (Artifact)q

        textToComponent = new HashMap()
        componentToDisplay = new HashMap()
        componentToText = new HashMap()
        
        r = new Renderer()
    }
    
    def launchGeneric() {
        panels = e.getPanels()        
        sbuilder = new SwingBuilder()
        
        sbuilder.edt {
            lookAndFeel 'nimbus'
            sbuilder.frame(id: 'frmEditor', title: "Quill (${VERSION}) - ${a.uid}", size: [960, 600],
                show: true, locationRelativeTo: null, resizable: true, 
                defaultCloseOperation: EXIT_ON_CLOSE) {
                menuBar {
                    menu(text: 'File', mnemonic: 'F') {
                        menuItem(text: "Save", mnemonic: 'S', actionPerformed: { save() })
                        menuItem(text: "Render", mnemonic: 'R', actionPerformed: { render() })
                        menuItem(text: "Commit", mnemonic: 'C', actionPerformed: { commit() })
                        menuItem(text: "Exit", mnemonic: 'X', actionPerformed: { dispose() })
                    }
                    menu(text: 'Edit', mnemonic: 'E') {
                        menuItem(text: "Clear", mnemonic: 'C', actionPerformed: { clear() })
                    }
                    menu(text: 'Help', mnemonic: 'H') {
                        menuItem(text: "About Quill", mnemonic: 'A', actionPerformed: { about() })
                        menuItem(text: "Settings", mnemonic: 'S', actionPerformed: { prefs() })
                    }
                }
                panel() {
                    gridBagLayout()                    
                    // left panel
                    tabbedPane(id: 'tpTeX', tabPlacement: JTabbedPane.LEFT,
                        constraints: gbc(gridx: 0, gridy: 0, weightx: 0, weighty: 1, fill: VERTICAL)) {
                        panels.each { pnl -> layoutPanel(pnl) }
                    }                        
                    // right panel
                    scrollPane(id: 'spDisplay', 
                        constraints: gbc(gridx: 1, gridy: 0, weightx: 1, weighty: 1, fill: BOTH)) {
                        vbox(id: 'vbDisplay')
                    }
                }
            }            
        }
        
        sbuilder.tpTeX.addChangeListener new ChangeListener() {            
            @Override
            void stateChanged(ChangeEvent changeEvent) {
                int idx = sbuilder.tpTeX.selectedIndex
                previewPanel(panels[idx])
            }            
        }
        previewPanel(panels[0])
    }
    
    public def updatePreview(LaTeXArea area) {
        def comp = textToComponent.get(area)
        def display = componentToDisplay.get(comp)        
        if (comp.isTex) {
            display.setText(area.getText())
            display.revalidate()
            display.repaint()
        } else {
            comp.isTex = true
            Files.deleteIfExists(((Artifact)e).qpath.resolve(comp.image))
            componentToDisplay.remove(comp)
            previewPanel(comp.parent)
        }
        comp.tex = area.getText()
    }
    
    private def layoutPanel = { Panel pnl ->
        sbuilder.scrollPane(name: pnl.title) {
            sbuilder.vbox() {
                pnl.getComponents().eachWithIndex { comp, i ->
                    def ta = LaTeXArea.getInstance(this, 
                        comp.isTex ? comp.tex : "file: ${comp.image}", 6, TA_WIDTH)
                    def wigit = new RTextScrollPane(ta, true)
                    
                    ta.dropTarget = [
                        drop: { DropTargetDropEvent dtde ->
                            def t = dtde.transferable
                            if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                                dtde.acceptDrop(DnDConstants.ACTION_REFERENCE)
                                File f = (File)dtde.transferable.getTransferData(DataFlavor.javaFileListFlavor)[0]
                                if (f.getName().toLowerCase().endsWith("svg")) {
                                    ta.text = "file: ${f.getName()}"
                                    comp.image = f.getName()
                                    comp.isTex = false
                                    Path dest = ((Artifact)e).qpath.resolve(f.getName())
                                    Files.deleteIfExists(dest)
                                    Files.copy(f.toPath(), dest)
                                    previewPanel(comp.getParent())
                                }
                            }
                        }
                    ] as DropTarget

                    textToComponent.put(ta, comp)
                    componentToText.put(comp, ta)
                    wigit.setBorder(BorderFactory.createTitledBorder(comp.title))                    
                    sbuilder.widget(wigit)
                }
            }
        }
    }
    
    private def previewPanel = { Panel pnl ->        
        sbuilder.vbDisplay.removeAll()
        pnl.getComponents().eachWithIndex { comp, i ->
            componentToDisplay.remove(comp)
            def drawable
            if (comp.isTex) {
                drawable = new TeXLabel(comp.tex, comp.title)
            } else {
                drawable = fileToIcon(comp.image)
            }
            componentToDisplay.put(comp, drawable)
            sbuilder.vbDisplay.add drawable
        }
        sbuilder.vbDisplay.revalidate()
        sbuilder.vbDisplay.repaint()
    }
    
    private def save = {
        e.updateModel(panels)
        a.getFile().write(a.toXMLString())
    }
    
    private def commit = {
        // git add *.xml, *.svg
        // git push origin master
    }
    
    private def clear = {
        int idx = sbuilder.tpTeX.selectedIndex
        panels[idx].components.each { comp ->
            componentToText.get(comp).text = ""
        }
    }
    
    private def render = {
        e.updateModel(panels)
        r.toSVG(a)
    }
    
    private def about = {
        def imageURL = Editor.class.getResource("logo-prepwell.png")
        ImageIcon icon
        if (imageURL != null) {
            icon = new ImageIcon(imageURL)
        }
        def dialog = sbuilder.dialog(title: 'What about Quill?', preferredSize: [150, 50], 
            locationRelativeTo: sbuilder.frmEditor) {
            label(text: "The less said the better", icon: icon)            
        }
        dialog.pack()
        dialog.visible = true
    }
    
    private def prefs = {
        def userHome = System.getProperty("user.home")
        def xml = new XmlSlurper().parse(new File("${userHome}/.quill/user_prefs.xml"))
        def dialog = sbuilder.dialog(title: 'Preferences', preferredSize: [300, 200], 
            locationRelativeTo: sbuilder.frmEditor) {
            
            tableLayout {
                tr {
                    td {
                        label(text: 'User Id')
                    }
                    td {
                        label(text: xml.userId)
                    }
                }
                tr {
                    td {
                        label(text: 'Role')
                    }
                    td {
                        label(text: xml.role)
                    }
                }
            }            
        }
        dialog.pack()
        dialog.visible = true
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
    
    private def JSVGCanvas fileToIcon(String name) {
        JSVGCanvas svgCanvas = new JSVGCanvas()
        try {
            svgCanvas.setURI(((Artifact)e).qpath.resolve(name).toUri().toURL().toString())
        } catch (Exception e) { }
        svgCanvas
    }
    
    private final int TA_WIDTH = 36
    
}
