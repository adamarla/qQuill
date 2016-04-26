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

import com.gradians.pipeline.Config
import com.gradians.pipeline.data.Asset
import com.gradians.pipeline.data.Choices
import com.gradians.pipeline.data.Question
import com.gradians.pipeline.data.Statement
import com.gradians.pipeline.data.Step
import com.gradians.pipeline.tag.Gitter
import com.gradians.pipeline.tag.SkillLibrary

import static java.awt.GridBagConstraints.NONE
import static java.awt.GridBagConstraints.BOTH
import static java.awt.GridBagConstraints.CENTER
import static java.awt.GridBagConstraints.HORIZONTAL
import static java.awt.GridBagConstraints.VERTICAL
import static javax.swing.WindowConstants.DISPOSE_ON_CLOSE


/** 
 * There are three kinds of objects that are kept in sync using
 * hash maps. These are :
 * 
 * TextAreas (for displaying latex / svg file name)
 * Components (data structure for holding latex / svg file name)
 * Display (for displaying the rendered latex or prefab svg)
 * 
 * @author adamarla
 *
 */

class Editor {
    
    static final String VERSION = "2.0"
    
    SwingBuilder sb
    Asset a
    Panel[] panels
    Map textToComponent, componentToDisplay, componentToText
        
    def Editor(Asset a) {
        this.a = a
        textToComponent = new HashMap()
        componentToDisplay = new HashMap()
        componentToText = new HashMap()
        sb = new SwingBuilder()
    }
    
    def launchGeneric() {
        panels = ((IEditable)a).getPanels()
        sb.edt {
            lookAndFeel 'nimbus'
            frame(id: 'frmEditor', title: "Quill (${VERSION}) - Editor - ${a.path}", size: [960, 600],
                show: true, locationRelativeTo: null, resizable: true, 
                defaultCloseOperation: DISPOSE_ON_CLOSE) {
                getMenuBar()
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
            sb.tpTeX.addChangeListener new ChangeListener() {
                @Override
                void stateChanged(ChangeEvent changeEvent) {
                    int idx = sb.tpTeX.selectedIndex
                    previewPanel(panels[idx])
                }
            }
            previewPanel(panels[0])
        }
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
            Files.deleteIfExists(a.qpath.resolve(comp.image))
            componentToDisplay.remove(comp)
            previewPanel(comp.parent)
        }
        comp.tex = area.getText()
    }
    
    private def layoutPanel = { Panel pnl ->
        def title = pnl.skill == -1 ? pnl.title : "${pnl.title} (skill: ${pnl.skill})"
        sb.vbox(name: title) {
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
                                ta.text = "file: img_${f.getName()}"
                                comp.image = f.getName()
                                comp.isTex = false
                                Path dest = a.qpath.resolve("img_${f.getName()}")
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
                sb.widget(wigit)
            }
        }
    }
    
    private def previewPanel = { Panel pnl ->
        sb.vbDisplay.removeAll()
        pnl.getComponents().eachWithIndex { comp, i ->
            componentToDisplay.remove(comp)
            def drawable
            if (comp.isTex) {
                drawable = new TeXLabel(comp.tex, comp.title)
            } else {
                drawable = fileToIcon(comp.image)
            }
            componentToDisplay.put(comp, drawable)
            sb.vbDisplay.add drawable
        }
        sb.vbDisplay.revalidate()
        sb.vbDisplay.repaint()
    }
    
    private def render = {
        ((IEditable)a).updateModel(panels)
        (new Renderer(a)).toSVG()
    }
    
    private def save = {
        ((IEditable)a).updateModel(panels)
        a.getFile().write(a.toXMLString())
    }
    
    private def saveAndCommit = {
        def bankPathString = (new Config()).get("bank_path")
        Gitter gitter = new Gitter(new File("${bankPathString}/.git"))
        Set<String> toAdd = gitter.toAdd()
        Set<String> toDelete = gitter.toDelete()
        Set<String> toCommit = gitter.toCommit()
        if (toAdd.size() + toDelete.size() + toCommit.size() > 0) {
            String message = 
            "# To Add: ${toAdd.toString()}\n# To Delete ${toDelete.toString()}\n" +
            "# Edit Commit message. Any line beginning with '#' will be ignored.\n" +
            "Created / Altered ${a.assetClass}."
            def dialog = sb.dialog(id: 'dlgCommit', title: 'Commit Message',
                locationRelativeTo: sb.frmEditor) {
                vbox() {
                    textArea(id: 'taMessage', rows: 8, columns: 40, text: message)
                    panel() {
                        button(text: 'Commit', actionPerformed: {
                            save()
                            gitter.commit(toAdd, toDelete, sb.taMessage.text)
                            sb.dlgCommit.dispose()
                        })
                        button(text: 'Cancel', actionPerformed: { sb.dlgCommit.dispose() })    
                    }
                }
            }
            dialog.pack()
            dialog.visible = true
        }
    }
    
    private def clear = {
        int idx = sb.tpTeX.selectedIndex
        panels[idx].components.each { comp ->
            componentToText.get(comp).text = ""
        }
    }
    
    private def getMenuBar = {
        sb.menuBar {
            menu(text: 'File', mnemonic: 'F') {
                menuItem(text: "Save", mnemonic: 'S', actionPerformed: { save() })
                menuItem(text: "Save + Commit", mnemonic: 'C', actionPerformed: { saveAndCommit() })
                menuItem(text: "Render", mnemonic: 'R', actionPerformed: { render() })
                menuItem(text: "Exit", mnemonic: 'X', actionPerformed: { dispose() })
            }
            menu(text: 'Edit', mnemonic: 'E') {                
                menuItem(text: "Skill (not implemented)", mnemonic: 'K', actionPerformed: { })
                menuItem(text: "Clear", mnemonic: 'C', actionPerformed: { clear() })
            }
            menu(text: 'Help', mnemonic: 'H') {
                menuItem(text: "About Quill", mnemonic: 'A', actionPerformed: { about() })
                menuItem(text: "Settings", mnemonic: 'S', actionPerformed: { prefs() })
            }
        }
    }
    
    private def about = {
        def imageURL = Editor.class.getResource("logo-prepwell.png")
        ImageIcon icon
        if (imageURL != null) {
            icon = new ImageIcon(imageURL)
        }
        def dialog = sb.dialog(title: 'About Quill', preferredSize: [150, 50],
            locationRelativeTo: sb.frmEditor) {
            label(text: "Administer and Author academic assets", icon: icon)
        }
        dialog.pack()
        dialog.visible = true
    }
    
    private def prefs = {
        Config config = new Config()
        def dialog = sb.dialog(title: 'Preferences', preferredSize: [300, 200], 
            locationRelativeTo: sb.frmEditor) {            
            tableLayout {
                tr {
                    td {
                        label(text: 'User Id')
                    }
                    td {
                        label(text: config.get("prefs.user_id"))
                    }
                }
                tr {
                    td {
                        label(text: 'Role')
                    }
                    td {
                        label(text: config.get("prefs.role"))
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
            svgCanvas.setURI(((Asset)e).qpath.resolve(name).toUri().toURL().toString())
        } catch (Exception e) { }
        svgCanvas
    }
    
    private final int TA_WIDTH = 36
    private final def THIS_BORDA = BorderFactory.createLineBorder(new java.awt.Color(0x9297a1))
    
}
