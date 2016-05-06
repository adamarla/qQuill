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
 * 1. LatexAreas - text area objects for displaying latex
 * 2. Items      - generic data objects for holding latex be it
 *                 for a Question, Skill or Snippet
 * 3. Display    - display widgets for previewing rendered latex
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
    Map latexToItem, itemToDisplay, itemToLatex
        
    def Editor(Asset asset) {
        a = asset
        
        latexToItem = new HashMap()
        itemToDisplay = new HashMap()
        itemToLatex = new HashMap()
        
        sb = new SwingBuilder()
        config = Config.getInstance()
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
                    editGroups.each { group -> layoutEditGroup(group) }
                }
                // right panel
                panel(id: 'pnlReference', preferredSize: [420, 200],
                    constraints: gbc(gridx: 1, weightx: 0.25, fill: HORIZONTAL))
                
                scrollPane(id: 'spDisplay', preferredSize: [420, 400],
                    constraints: gbc(gridx: 1, gridy: 1, weightx: 0.25, weighty: 1, fill: BOTH)) {
                    vbox(id: 'vbDisplay')
                }    
            }
            sb.tpTeX.addChangeListener new ChangeListener() {
                @Override
                void stateChanged(ChangeEvent changeEvent) {
                    int idx = sb.tpTeX.selectedIndex
                    layoutEditGroupPreview(editGroups[idx])
                    refreshSkillPreview(editGroups[idx])
                }
            }
            layoutEditGroupPreview(editGroups[0])
            refreshSkillPreview(editGroups[0])
        }
    }
    
    public def refreshLaTeXPreview(LaTeXArea area) {
        def item = latexToItem.get(area)
        def display = itemToDisplay.get(item)
        if (item.isTex) {
            display.setText(area.getText())
            display.revalidate()
            display.repaint()
        } else {
            item.isTex = true
            Files.deleteIfExists(a.qpath.resolve(item.image))
            itemToDisplay.remove(item)
            layoutEditGroupPreview(item.parent)
        }
        item.tex = area.getText()
    }
    
    @Override
    public void applySelectedSkill(Skill skill) {
        int idx = sb.tpTeX.selectedIndex
        editGroups[idx].skill = skill.id
        refreshSkillPreview(editGroups[idx])
    }
    
    @Override
    public java.awt.Component getParentComponent() { sb.frmEditor }
    
    private def layoutEditGroup = { EditGroup eg ->
        sb.vbox(name: eg.title) {
            eg.getEditItems().eachWithIndex { item, i ->
                def ta = LaTeXArea.getInstance(this, 
                    item.isTex ? item.tex : "file: ${item.image}", 6, TA_WIDTH)
                
                ta.dropTarget = [
                    drop: { DropTargetDropEvent dtde ->
                        def t = dtde.transferable
                        if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                            dtde.acceptDrop(DnDConstants.ACTION_REFERENCE)
                            File f = (File)dtde.transferable.getTransferData(DataFlavor.javaFileListFlavor)[0]
                            if (f.getName().toLowerCase().endsWith("svg")) {
                                ta.text = "file: img_${f.getName()}"
                                item.image = f.getName()
                                item.isTex = false
                                Path dest = a.qpath.resolve("img_${f.getName()}")
                                Files.deleteIfExists(dest)
                                Files.copy(f.toPath(), dest)
                                layoutEditGroupPreview(item.getParent())
                            }
                        }
                    }
                ] as DropTarget
            
                latexToItem.put(ta, item)
                itemToLatex.put(item, ta)
                
                def wigit = new RTextScrollPane(ta, true)
                wigit.setBorder(BorderFactory.createTitledBorder(item.title))
                sb.widget(wigit)
            }
        }
    }
    
    private def layoutEditGroupPreview = { EditGroup eg ->
        sb.vbDisplay.removeAll()
        eg.getEditItems().eachWithIndex { item, i ->
            itemToDisplay.remove(item)
            def drawable
            if (item.isTex) {
                drawable = new TeXLabel(item.tex, item.title)
            } else {
                drawable = fileToIcon(item.image)
            }
            itemToDisplay.put(item, drawable)
            sb.vbDisplay.add drawable
        }
        sb.vbDisplay.revalidate()
        sb.vbDisplay.repaint()
    }
    
    private def refreshSkillPreview(EditGroup eg) {
        def tex
        if (eg.skill != -1) {
            def map = [path: "skills/${eg.skill}", assetClass: "Skill"]
            Skill reference = Asset.getInstance(map).load()
            IEditable e = (IEditable)reference
            tex = e.getEditGroups()[0].getEditItems()[0].tex
        } else {
            tex = "\\text{A skill. Is whistling a skill?}"
        }
        
        sb.pnlReference.removeAll()
        sb.pnlReference.add new TeXLabel(tex, "Reference")
        sb.pnlReference.revalidate()
        sb.pnlReference.repaint()
    }
    
    private def launchSkillBuilder() {
        int idx = sb.tpTeX.selectedIndex        
        SkillLibrary sl = new SkillLibrary(this)
        sl.launch(a.chapterId, editGroups[idx].skill)
    }
    
    private def render() {
        ((IEditable)a).updateModel(editGroups)
        (new Renderer(a)).toSVG()
    }
    
    private def save() {
        ((IEditable)a).updateModel(editGroups)
        a.getFile().write(a.toXMLString())
    }
    
    private def commit() {
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
    
    private def clear() {
        int idx = sb.tpTeX.selectedIndex
        editGroups[idx].editItems.each { comp ->
            itemToLatex.get(comp).text = ""
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
                menuItem(text: "Save + Commit", actionPerformed: { 
                    save()
                    if (config.get("mode").equals("production"))
                        commit() 
                    })
            }
            menu(text: 'Edit', mnemonic: 'E') {                
                menuItem(text: "Skill", mnemonic: 'K', actionPerformed: { launchSkillBuilder() })
                menuItem(text: "Clear", mnemonic: 'C', actionPerformed: { clear() })
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
    
}
