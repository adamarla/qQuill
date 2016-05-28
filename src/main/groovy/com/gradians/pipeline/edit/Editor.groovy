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
import javax.swing.JOptionPane

import org.apache.batik.swing.JSVGCanvas
import org.fife.ui.rsyntaxtextarea.SyntaxConstants
import org.fife.ui.rtextarea.RTextScrollPane

import com.gradians.pipeline.data.Asset
import com.gradians.pipeline.data.AssetClass
import com.gradians.pipeline.data.Question
import com.gradians.pipeline.data.Skill
import com.gradians.pipeline.tag.ISkillLibClient
import com.gradians.pipeline.tag.SkillLibrary
import com.gradians.pipeline.tag.Category
import com.gradians.pipeline.util.Config
import com.gradians.pipeline.util.Gitter

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
        Asset a = (Asset)e        
        if (!a.load()) {
            def author = a.authorId ? config.getAuthor(a.authorId) : "someone"
            def pane = sb.optionPane(message:
                "The slot for this ${a.assetClass} was created by\n" +
                "${author}, but it has not been filled yet.\n" +
                "Someone may be working on it. \n" +
                "Do you still want to edit this ${a.assetClass}?",
                optionType: JOptionPane.OK_CANCEL_OPTION,
                messageType: JOptionPane.INFORMATION_MESSAGE,
                options: ["Yes, I want to", "No, never mind"])
            def dialog = pane.createDialog(null, 'Hang on a sec!')
            dialog.visible = true
            String value = (String)pane.getValue()
            dialog.dispose()
            if (value.startsWith("Yes")) {
                a.create()
            } else {
                return
            }
        }

        editGroups = e.getEditGroups()
        sb.edt {
            lookAndFeel 'nimbus'
            frame(id: 'frmEditor', size: [900, 600], show: true, resizable: true, locationRelativeTo: null,
                title: "Quill (${VERSION}) - ChapterId ${((Asset)e).chapterId} - ${e.getDirPath()}",
                defaultCloseOperation: DISPOSE_ON_CLOSE) {
                getMenuBar()
                
                splitPane(orientation: javax.swing.JSplitPane.HORIZONTAL_SPLIT) {
                    // left panel
                    tabbedPane(id: 'tpTeX', tabPlacement: LEFT) {
                        editGroups.each { group -> layoutEditGroup(group) }
                    }
                        
                    splitPane(orientation: javax.swing.JSplitPane.VERTICAL_SPLIT) {
                        // right panel
                        scrollPane() {
                            vbox(id: 'vbReference')
                        }
                        
                        scrollPane() {
                            vbox(id: 'vbDisplay')
                        }        
                    }    
                }
            }
                
            sb.tpTeX.addChangeListener new ChangeListener() {
                                
                @Override
                void stateChanged(ChangeEvent changeEvent) {
                    int idx = sb.tpTeX.selectedIndex
                    layoutEditGroupPreview(editGroups[idx])
                    refreshSkillPreview(editGroups[idx])
                    
                    Asset asset = (Asset)e
                    sb.miEditSkill.enabled = !(asset.assetClass == AssetClass.Question && idx == 0)
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
        
        e.updateModel(editGroups)
        e.save()        
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
        int idx = sb.tpTeX.selectedIndex
        Asset a = (Asset)e
        SkillLibrary sl = new SkillLibrary(this)
        sl.launch(a.chapterId, editGroups[idx].skills)
    }
    
    private def launchChapterSelector() {
        def chapters = []
        def items = config.getChapters()
        items.each { item ->
            Category c = new Category(item)
            chapters.add c
        }

        Asset a = (Asset)e
        def initial = a.chapterId != 0 ? chapters.find { it.id == a.chapterId } : chapters[0]
        def selected = JOptionPane.showInputDialog(sb.frmEditor,
            "Select to assign chapter to question", "Chapter List", 
            JOptionPane.PLAIN_MESSAGE, null, 
            chapters*.name.toArray(new String[chapters.size()]),
            initial.name)
        
        if (selected) {
            a.chapterId = chapters.find { it.name.equals(selected) }.id
            e.updateModel(editGroups)
            e.save()
            sb.frmEditor.title = "Quill (${VERSION}) - ChapterId ${a.chapterId} - ${e.getDirPath()}"            
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
                    if (config.getMode().equals("production"))
                        commit() 
                })
            }
            menu(text: 'Edit', mnemonic: 'E') {
                menuItem(text: "Chapter", mnemonic: 'H', actionPerformed: { launchChapterSelector() })
                menuItem(id: 'miEditSkill', text: "Skill", mnemonic: 'K', actionPerformed: { launchSkillLibrary() })
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
