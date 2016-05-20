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

import com.gradians.pipeline.data.Asset
import com.gradians.pipeline.data.AssetClass
import com.gradians.pipeline.data.Question
import com.gradians.pipeline.data.Skill
import com.gradians.pipeline.tag.ISkillLibClient
import com.gradians.pipeline.tag.SkillLibrary
import com.gradians.pipeline.util.Config;
import com.gradians.pipeline.util.Gitter;
import com.gradians.pipeline.util.Network

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
    IEditable e
    EditGroup[] editGroups
    Map latexToItem, itemToDisplay, itemToLatex
        
    def Editor(IEditable editable) {
        e = editable
        
        latexToItem = new HashMap()
        itemToDisplay = new HashMap()
        itemToLatex = new HashMap()
        
        sb = new SwingBuilder()
        config = Config.getInstance()
    }
    
    def launchGeneric() {
        editGroups = e.getEditGroups()
        sb.edt {
            lookAndFeel 'nimbus'
            frame(id: 'frmEditor', size: [900, 600], show: true, resizable: true, locationRelativeTo: null, 
                title: "Quill (${VERSION}) - ChapterId ${((Asset)e).chapterId} - ${e.getDirPath()}",                
                defaultCloseOperation: DISPOSE_ON_CLOSE) {
                getMenuBar()
                gridBagLayout()
                
                // left panel
                tabbedPane(id: 'tpTeX', tabPlacement: LEFT,
                    constraints: gbc(weightx: 0.75, weighty: 1, gridheight: 2, fill: BOTH)) {
                    editGroups.each { group -> layoutEditGroup(group) }
                }
                    
                // right panel
                scrollPane(
                    constraints: gbc(gridx: 1, weightx: 0.25, weighty: 0.25, fill: BOTH)) {
                    vbox(id: 'vbReference')
                }
                
                scrollPane(
                    constraints: gbc(gridx: 1, gridy: 1, weightx: 0.25, weighty: 0.75, fill: BOTH)) {
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
        EditItem item = latexToItem.get(area)
        def display = itemToDisplay.get(item)
        if (!item.isImage) {
            display.setText(area.getText())
            display.revalidate()
            display.repaint()
        } else {
            item.isImage = false
            Files.deleteIfExists(e.getDirPath().resolve(item.text))
            itemToDisplay.remove(item)
            layoutEditGroupPreview(item.parent)
        }
        item.text = area.getText()
    }
    
    @Override
    public void applySelectedSkill(int[] skills) {
        int idx = sb.tpTeX.selectedIndex
        editGroups[idx].skills = skills
        refreshSkillPreview(editGroups[idx])
        
        // HTTP POST skills list to server
        def userId = config.get("user_id")
        def a = (Asset)e
        def url = "${a.assetClass.toString().toLowerCase()}/tag"
        Map map = [id: a.id, skills: editGroups.collect { it.skills }.flatten().findAll { it != 0 }]
        
        // create asset
        Network.executeHTTPPostBody(url, map)
    }
    
    @Override
    public java.awt.Component getParentComponent() { sb.frmEditor }
    
    private def layoutEditGroup = { EditGroup eg ->
        sb.vbox(name: eg.title) {
            eg.getEditItems().each { item ->
                def ta = LaTeXArea.getInstance(this, 
                    item.isImage ? "file: ${item.text}" : item.text, 6, TA_WIDTH)
                
                ta.dropTarget = [drop: { DropTargetDropEvent dtde ->
                    def t = dtde.transferable
                    if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        dtde.acceptDrop(DnDConstants.ACTION_REFERENCE)
                        File f = (File)dtde.transferable.getTransferData(DataFlavor.javaFileListFlavor)[0]
                        if (f.getName().toLowerCase().endsWith("svg")) {
                            Path dest = e.getDirPath().resolve("img_${f.getName()}")
                            
                            // this triggers the TextArea edit callback
                            // first let that run its course
                            ta.text = "file: ${dest.getFileName()}"
                            
                            // next, ensure directly that panel is repainted
                            Files.deleteIfExists(dest)
                            Files.copy(f.toPath(), dest)
                            item.text = dest.getFileName()
                            item.isImage = true
                            layoutEditGroupPreview(eg)
                        }
                    }
                }] as DropTarget
            
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
        eg.getEditItems().each { item ->
            itemToDisplay.remove(item)
            def drawable
            if (!item.isImage) {
                drawable = new TeXLabel(item.text, item.title)
            } else {
                drawable = fileToIcon(item.text)
            }
            itemToDisplay.put(item, drawable)
            sb.vbDisplay.add drawable
        }
        sb.vbDisplay.revalidate()
        sb.vbDisplay.repaint()
    }
    
    private def refreshSkillPreview(EditGroup eg) {
        sb.vbReference.removeAll()
        eg.skills.eachWithIndex { it, i ->
            if (it != 0) {
                def map = [path: "skills/${it}", assetClass: "Skill"]
                Skill reference = Asset.getInstance(map)
                IEditable e = (IEditable)reference
                def tex = e.getEditGroups()[0].getEditItems()[0].text
                sb.vbReference.add new TeXLabel(tex, "Skill ${i+1}")    
            }
        }
        sb.vbReference.revalidate()
        sb.vbReference.repaint()
    }
    
    private def launchSkillLibrary() {
        try {
            int idx = sb.tpTeX.selectedIndex
            SkillLibrary sl = new SkillLibrary(this)
            sl.launch(((Asset)e).chapterId, editGroups[idx].skills)    
        } catch (Exception e) {
            e.printStackTrace()
            javax.swing.JOptionPane.showMessageDialog(sb.frmEditor,
                "Crash. Send stack trace to akshay@gradians.com",
                "Ooops", javax.swing.JOptionPane.ERROR_MESSAGE)
        }   
    }
    
    private def commit() {
        Asset a = (Asset)e
        def bankPathString = config.getBankPath()
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
                menuItem(text: "Save", mnemonic: 'S', actionPerformed: {
                    e.updateModel(editGroups)
                    e.save()            
                })
                menuItem(text: "Render", mnemonic: 'R', actionPerformed: {
                    (new Renderer(e)).toSVG()            
                })
                separator()
                menuItem(text: "Exit", mnemonic: 'X', actionPerformed: { dispose() })
                separator()
                menuItem(text: "Save + Commit", actionPerformed: { 
                    e.updateModel(editGroups)
                    e.save()
                    if (config.get("mode").equals("production"))
                        commit() 
                })
            }
            menu(text: 'Edit', mnemonic: 'E') {                
                menuItem(text: "Skill", mnemonic: 'K', actionPerformed: { launchSkillLibrary() })
                menuItem(text: "Clear", mnemonic: 'C', actionPerformed: { clear() })
            }
        }
    }
    
    private def JSVGCanvas fileToIcon(String name) {
        JSVGCanvas svgCanvas = new JSVGCanvas()
        try {
            svgCanvas.setURI(e.getDirPath().resolve(name).toUri().toURL().toString())
        } catch (Exception e) { }
        svgCanvas
    }
    
    private final int TA_WIDTH = 36
    
}
