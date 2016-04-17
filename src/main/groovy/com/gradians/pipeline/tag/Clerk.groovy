package com.gradians.pipeline.tag

import groovy.swing.SwingBuilder

import java.awt.Color
import java.awt.event.MouseEvent
import java.awt.GridBagConstraints as GBC

import java.nio.file.Path

import javax.swing.BorderFactory
import javax.swing.event.ListSelectionEvent

import com.gradians.pipeline.Config
import com.gradians.pipeline.data.Asset
import com.gradians.pipeline.data.AssetClass
import com.gradians.pipeline.data.Question
import com.gradians.pipeline.data.Skill
import com.gradians.pipeline.data.Snippet
import com.gradians.pipeline.edit.Editor

import ca.odell.glazedlists.BasicEventList
import ca.odell.glazedlists.EventList
import ca.odell.glazedlists.FilterList
import ca.odell.glazedlists.SortedList
import ca.odell.glazedlists.gui.TableFormat
import ca.odell.glazedlists.matchers.Matcher
import ca.odell.glazedlists.swing.AdvancedTableModel
import ca.odell.glazedlists.swing.GlazedListsSwing
import ca.odell.glazedlists.swing.TableComparatorChooser

import static java.awt.BorderLayout.PAGE_START
import static java.awt.BorderLayout.PAGE_END
import static java.awt.BorderLayout.CENTER
import static java.awt.BorderLayout.LINE_START
import static java.awt.BorderLayout.LINE_END
import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
import static javax.swing.JList.VERTICAL
import static javax.swing.WindowConstants.DISPOSE_ON_CLOSE
import static javax.swing.JFrame.EXIT_ON_CLOSE
import static javax.swing.ListSelectionModel.SINGLE_SELECTION


class Clerk {
    
    SwingBuilder sb
    Config config
    
    public Clerk() {
        config = new Config()
        loadAssets()
    }
    
    def go(boolean topLevel = false) {
        sb = new SwingBuilder()
        sb.edt {
            lookAndFeel 'nimbus'
            frame(id: 'frmClerk', title: "Quill - Clerk",
                size: [720, 600], show: true, locationRelativeTo: null,
                defaultCloseOperation: topLevel ? EXIT_ON_CLOSE : DISPOSE_ON_CLOSE) {
                getMenuBar()

                panel() {
                    gridBagLayout()
                    
                    vbox(constraints: gbc(gridx: 0, gridy: 0, weightx: 0.25, weighty: 1,
                            anchor: GBC.PAGE_START, fill: GBC.BOTH)) {
                        getSelectorLists()
                    }
                    
                    scrollPane(constraints: gbc(gridx: 1, gridy: 0, weightx: 1, weighty: 1,
                                anchor: GBC.PAGE_END, fill: GBC.BOTH)) {
                        table(id: 'tblAssets', mouseClicked: { MouseEvent me -> launchEditor(me) }, 
                            selectionMode: SINGLE_SELECTION, model: createTableModel())
                    }
                }
            }
            tableSorter = TableComparatorChooser.install(sb.tblAssets, sortedList,
                TableComparatorChooser.MULTIPLE_COLUMN_MOUSE_WITH_UNDO)
        }
    }
    
    private def filter = {
        List<Catalog> authorSelection = sb.listAuthors.getSelectedValuesList()
        List<Catalog> chapterSelection = sb.listChapters.getSelectedValuesList()
        AssetsMatcher matcher = new AssetsMatcher(chapterSelection, authorSelection, this)
        filteredList.setMatcher(matcher)
    }
    
    private def loadAssets = {
        chapters = new ArrayList<Category>()
        chapterById = new HashMap<Integer, Category>()        
        def items = Network.executeHTTPGet("chapter/list")
        items.eachWithIndex{ item, i ->
            Category c = new Category(item)
            chapters.add c
            chapterById.put c.id, c
        }

        authors = new ArrayList<Category>()
        authors.add new Category(id: 1, name: "Abhinav")
        authors.add new Category(id: 2, name: "Akshay")
        authors.add new Category(id: 3, name: "Nimesh")
        authorById = new HashMap<Integer, Category>()
        authors.each { author -> authorById.put(author.id, author) }
        
        // Collections for display table
        artefactsEventList = new BasicEventList<Asset>()
        sortedList = new SortedList(artefactsEventList)
        filteredList = new FilterList<Asset>(sortedList,
            new AssetsMatcher(chapters, authors, this))

        artefactsEventList.clear()
        def assets = []
        chapters.each { chapter ->
            items = Network.executeHTTPGet("question/list?c=${chapter.id}")
            items.eachWithIndex{ item, i ->
                assets << Asset.getInstance(item, AssetClass.Question)
            }
        }
        artefactsEventList.addAll assets
    }
    
    private def launchEditor = { MouseEvent me ->
        def row = sb.tblAssets.rowAtPoint(me.getPoint())
        if (me.getClickCount() == 2) {
            Asset selected = filteredList.get(row)
            new Editor(selected.load()).launchGeneric()
        }
    }
    
    private def launchNewAssetDialog(AssetClass assetClass) {
        newAssetDialog = sb.dialog(title: "New ${assetClass} on?", locationRelativeTo: sb.frmClerk) {
            vbox() {
                comboBox(id: 'cbChapter', items: chapters, selectedIndex: 0)
                panel() {
                    button(text: 'Create', 
                        actionPerformed: { 
                            createNewAsset(assetClass, (Category)sb.cbChapter.getSelectedItem())
                            newAssetDialog.dispose() 
                        })
                }
            }
        }
        newAssetDialog.pack()
        newAssetDialog.visible = true
    }
    
    private def createNewAsset(AssetClass assetClass, Category chapter) {
        // call server
        def userId = config.get("user_id")
        def params = Network.executeHTTPPost("question/add?e=${userId}&c=${chapter.id}")
        params.authorId = userId
        params.chapterId = chapter.id
        Asset newAsset = Asset.getInstance(params, assetClass)
        // refresh list
        artefactsEventList.add newAsset
        sb.listChapters.setSelectedValue(chapter, true)
    }
    
    private def launchChangeAttributesDialog() {
                
    }
    
    private def createTableModel = {
        def columnNames = ['Id', 'Chapter', 'Author', 'Class']
        def variableNames = ['id', 'chapter', 'author', 'assetClass']
        def tableModel = GlazedListsSwing.eventTableModel(filteredList,
            [getColumnCount: { return columnNames.size()},
             getColumnName: { index -> columnNames[index] },  
             getColumnValue: { object, index ->
                     switch(index) {
                         case [0, 3]: 
                             object."${variableNames[index]}"
                             break
                         case [1, 2]:
                             HashMap<Integer, Category> reference = this."${variableNames[index]}ById" 
                             Category c = reference.get(object."${variableNames[index]}Id")
                             c.name
                             break
                     }
                 }
            ] as TableFormat)
        tableModel
    }
    
    private def getSelectorLists = {
        sb.scrollPane(horizontalScrollBarPolicy: HORIZONTAL_SCROLLBAR_NEVER) {
            list(id: 'listChapters', listData: chapters,
                layoutOrientation: VERTICAL, visibleRowCount: 12,
                valueChanged: { filter() })
        }
        sb.scrollPane() {
            list(id: 'listAuthors', listData: authors,
                layoutOrientation: VERTICAL, visibleRowCount: 3, 
                valueChanged: { filter() })
        }
        sb.scrollPane() {
            list(id: 'listTypes', listData: ["Question", "Skill", "Snippet"],
                layoutOrientation: VERTICAL, visibleRowCount: 3, selectedIndex: 0)
        }
        sb.scrollPane() {
            list(id: 'listStates', listData: ["New", "Saved", "Completed", "Bundled"],
                layoutOrientation: VERTICAL, visibleRowCount: 4, selectedIndex: 0)
        }
        sb.listChapters.setSelectedValue(chapters[0], true)
    }
    
    private def getMenuBar = {
        sb.menuBar {
            menu(text: 'File', mnemonic: 'F') {
                menu(text: "New", mnemonic: 'N') {
                    menuItem(text: "Skill", mnemonic: 'K', 
                        actionPerformed: { launchNewAssetDialog(AssetClass.Skill) })
                    menuItem(text: "Snippet", mnemonic: 'N', 
                        actionPerformed: { launchNewAssetDialog(AssetClass.Snippet) })
                    menuItem(text: "Problem", mnemonic: 'P', 
                        actionPerformed: { launchNewAssetDialog(AssetClass.Question) })
                }
                menuItem(text: "Exit", mnemonic: 'X', actionPerformed: { dispose() })
            }
            menu(text: 'Edit', mnemonic: 'E') {
                menuItem(text: "Change Attributes", mnemonic: 'C', 
                    actionPerformed: { launchChangeAttributesDialog() })
            }
            menu(text: 'Help', mnemonic: 'H') {
                menuItem(text: "About", mnemonic: 'A', actionPerformed: { })
                menuItem(text: "Settings", mnemonic: 'S', actionPerformed: { })
            }
        }
    }
    
    private def newAssetDialog
    
    private List<Category> chapters
    private List<Category> authors
    
    protected HashMap<Integer, Category> authorById
    protected HashMap<Integer, Category> chapterById

    private FilterList<Asset> filteredList
    private SortedList<Asset> sortedList
    private EventList<Asset> artefactsEventList
    private TableComparatorChooser<Asset> tableSorter
    
}

class AssetsMatcher implements Matcher {

    protected Clerk clerk
    private Set chapters = new HashSet<Category>()
    private Set authors = new HashSet<Category>()
    private Set types = new HashSet<String>()

    /**
     * Create a new {@link AssetsByChapterMatcher} that matches only 
     * {@link Asset}s belonging to one or more chapters in the specified list.
     */
    public AssetsMatcher(Collection<Category> chapters, Collection<Category> authors, Clerk clerk) {
        // make a defensive copy of the assets
        this.chapters.addAll(chapters)
        this.authors.addAll(authors)
        this.clerk = clerk
    }

    /**
     * Test whether to include or not include the specified asset based
     * on whether or not its chapter is selected.
     */
    public boolean matches(Object o) {
        if (o == null)
            return false

        if (chapters.isEmpty())
            return true

        Asset asset = (Asset)o
        int chapterId = asset.getChapterId()
        int authorId = asset.getAuthorId()
        
        if (this.authors.size() == 0)
            return chapters.contains(clerk.chapterById.get(chapterId))
        else
            return chapters.contains(clerk.chapterById.get(chapterId)) &&
                authors.contains(clerk.authorById.get(authorId))
    }
}

class Category {
    int id    
    String name
    
    @Override
    public String toString() {
        "${name}"
    }
}

