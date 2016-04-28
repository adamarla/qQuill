package com.gradians.pipeline.edit

import groovy.swing.SwingBuilder

import java.awt.dnd.DropTarget
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTargetDropEvent
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.Files

import javax.swing.BorderFactory
import javax.swing.ImageIcon
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener

import org.apache.batik.swing.JSVGCanvas
import org.fife.ui.rsyntaxtextarea.SyntaxConstants
import org.fife.ui.rtextarea.RTextScrollPane

import com.gradians.pipeline.Config
import com.gradians.pipeline.data.Asset
import com.gradians.pipeline.data.AssetClass
import com.gradians.pipeline.data.Choices
import com.gradians.pipeline.data.Question
import com.gradians.pipeline.data.Skill
import com.gradians.pipeline.data.Statement
import com.gradians.pipeline.data.Step
import com.gradians.pipeline.tag.Gitter
import com.gradians.pipeline.tag.ISkillLibClient
import com.gradians.pipeline.tag.SkillLibrary

import static java.awt.GridBagConstraints.BOTH
import static java.awt.GridBagConstraints.VERTICAL
import static java.awt.GridBagConstraints.HORIZONTAL
import static javax.swing.SwingConstants.CENTER
import static javax.swing.SwingConstants.LEFT
import static javax.swing.WindowConstants.DISPOSE_ON_CLOSE


/** 
 * There are three kinds of objects that are kept in sync 
 * using HashMaps. These are,
 * 1. TextAreas  - for displaying latex
 * 2. Components - data objects for holding latex
 * 3. Display    - for displaying the rendered latex
 * 
 * HashMaps -
 * textToComponent    (1 -> 2)
 * componentToDisplay (2 -> 3)
 * componentToText    (2 -> 1)
 * 
 * @author adamarla
 * 
 */

class Editor implements ISkillLibClient {
    
    static final String VERSION = "2.0"
    
    Config config
    SwingBuilder sb
    Asset a
    EditGroup[] editGroups
    Map textToComponent, componentToDisplay, componentToText
        
    def Editor(Asset a) {
        this.a = a
        textToComponent = new HashMap()
        componentToDisplay = new HashMap()
        componentToText = new HashMap()
        sb = new SwingBuilder()
        config = new Config()
    }
    
    def launchGeneric() {
        IEditable e = (IEditable)a
        editGroups = e.getEditGroups()
        sb.edt {
            lookAndFeel 'nimbus'
            frame(id: 'frmEditor', title: "Quill (${VERSION}) - Editor - ${a.path}", size: [840, 600],
                show: true, resizable: true, locationRelativeTo: null, 
                defaultCloseOperation: DISPOSE_ON_CLOSE) {
                getMenuBar()
                gridBagLayout()
                // left panel
                tabbedPane(id: 'tpTeX', tabPlacement: LEFT,
                    constraints: gbc(weightx: 0.75, weighty: 1, gridheight: 2, fill: BOTH)) {
                    editGroups.each { pnl -> layoutPanel(pnl) }
                }
                // right panel
                panel(id: 'pnlReference', 
                    constraints: gbc(gridx: 1, weightx: 0.25, fill: HORIZONTAL))
                scrollPane(id: 'spDisplay',
                    constraints: gbc(gridx: 1, gridy: 1, weightx: 0.25, weighty: 1, fill: BOTH)) {
                    vbox(id: 'vbDisplay')
                }    
            }
            sb.tpTeX.addChangeListener new ChangeListener() {
                @Override
                void stateChanged(ChangeEvent changeEvent) {
                    int idx = sb.tpTeX.selectedIndex
                    previewPanel(editGroups[idx])
                }
            }
            previewPanel(editGroups[0])
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
    
    @Override
    public void applySelectedSkill(Skill skill) {
        int idx = sb.tpTeX.selectedIndex
        editGroups[idx].skill = skill.id
    }
    
    @Override
    public java.awt.Component getParentComponent() { sb.frmEditor }
    
    private def layoutPanel = { EditGroup pnl ->
        sb.vbox(name: pnl.title) {
            pnl.getEditItems().eachWithIndex { comp, i ->
                def ta = LaTeXArea.getInstance(this, 
                    comp.isTex ? comp.tex : "file: ${comp.image}", 6, TA_WIDTH)
                
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
                
                def wigit = new RTextScrollPane(ta, true)
                wigit.setBorder(BorderFactory.createTitledBorder(comp.title))                    
                sb.widget(wigit)
            }
        }
    }
    
    private def previewPanel = { EditGroup pnl ->
        def tex
        if (pnl.skill != -1) {
            def map = [path: "skills/${pnl.skill}", assetClass: "Skill"]
            Skill reference = Asset.getInstance(map).load()
            IEditable e = (IEditable)reference
            tex = e.getEditGroups()[0].getEditItems()[0].tex
        } else {
            tex = "\\text{Think of something good!}"        
        }
        sb.pnlReference.removeAll()
        sb.pnlReference.add new TeXLabel(tex, "Reference")
        sb.pnlReference.revalidate()
        sb.pnlReference.repaint()

        sb.vbDisplay.removeAll()
        pnl.getEditItems().eachWithIndex { comp, i ->
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
    
    private def launchSkillBuilder = {
        int idx = sb.tpTeX.selectedIndex        
        SkillLibrary sl = new SkillLibrary(this)
        sl.launch(a.chapterId, editGroups[idx].skill)
    }
    
    private def render = {
        ((IEditable)a).updateModel(editGroups)
        (new Renderer(a)).toSVG()
    }
    
    private def save = {
        ((IEditable)a).updateModel(editGroups)
        a.getFile().write(a.toXMLString())
    }
    
    private def commit = {
        def bankPathString = config.get("bank_path")
        String path = Paths.get(bankPathString).relativize(a.qpath)
        
        Gitter gitter = new Gitter(new File("${bankPathString}/.git"))        
        
        Set<String> toAdd = gitter.toAdd(path.toString())
        Set<String> toDelete = gitter.toDelete(path.toString())
        
        if (toAdd.size() + toDelete.size() > 0) {            
            String message =
            "# To Add: ${toAdd.toString()}\n# To Delete ${toDelete.toString()}\n" +
            "# Edit Commit message. Any line beginning with '#' will be ignored.\n" +
            "Created / Altered ${a.assetClass}."
            def dialog = sb.dialog(id: 'dlgCommit', title: 'Commit Message',
                locationRelativeTo: sb.frmEditor) {
                vbox() {
                    textArea(id: 'taCommitMessage', rows: 8, columns: 40, text: message)
                    panel() {
                        button(text: 'Commit', actionPerformed: {
                            gitter.commit(path.toString(), toAdd, toDelete, sb.taCommitMessage.text)
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
        editGroups[idx].editItems.each { comp ->
            componentToText.get(comp).text = ""
        }
    }
    
    private def getMenuBar = {
        sb.menuBar {
            menu(text: 'File', mnemonic: 'F') {
                menuItem(text: "Save", mnemonic: 'S', actionPerformed: { save() })
                menuItem(text: "Render", mnemonic: 'R', actionPerformed: { render() })
                separator()
                menuItem(text: "Exit", mnemonic: 'X', actionPerformed: { dispose() })
                separator()
                menuItem(text: "Save + Commit", actionPerformed: { save() 
                    commit() })
            }
            menu(text: 'Edit', mnemonic: 'E') {                
                menuItem(text: "Skill", mnemonic: 'K', actionPerformed: { launchSkillBuilder() })
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
    
    private def JSVGCanvas fileToIcon(String name) {
        JSVGCanvas svgCanvas = new JSVGCanvas()
        try {
            svgCanvas.setURI(((Asset)e).qpath.resolve(name).toUri().toURL().toString())
        } catch (Exception e) { }
        svgCanvas
    }
    
    private final int TA_WIDTH = 36
    
}
