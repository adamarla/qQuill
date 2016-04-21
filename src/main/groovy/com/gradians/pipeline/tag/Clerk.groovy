package com.gradians.pipeline.tag

import groovy.swing.SwingBuilder

import java.awt.Color
import java.awt.event.MouseEvent
import java.awt.GridBagConstraints as GBC
import java.nio.file.Path

import javax.swing.BorderFactory
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener

import com.gradians.pipeline.Config
import com.gradians.pipeline.data.Asset
import com.gradians.pipeline.data.AssetClass
import com.gradians.pipeline.data.AssetState
import com.gradians.pipeline.data.Question
import com.gradians.pipeline.data.Skill
import com.gradians.pipeline.data.Snippet
import com.gradians.pipeline.edit.Editor
import com.gradians.pipeline.edit.IEditable
import com.gradians.pipeline.edit.TeXHelper;
import com.gradians.pipeline.edit.TeXLabel

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
    
    def go(boolean topLevel = true) {
        sb = new SwingBuilder()
        sb.edt {
            lookAndFeel 'nimbus'
            frame(id: 'frmClerk', title: "Quill (${Editor.VERSION}) - Clerk",
                size: [720, 600], show: true, locationRelativeTo: null,
                defaultCloseOperation: topLevel ? EXIT_ON_CLOSE : DISPOSE_ON_CLOSE) {
                getMenuBar()

                panel() {
                    gridBagLayout()
                    
                    vbox(constraints: gbc(gridx: 0, gridy: 0, gridheight: 2, weightx: 1, weighty: 1,
                            anchor: GBC.PAGE_START, fill: GBC.BOTH)) {
                        getSelectorLists()
                    }
                                                        
                    scrollPane(constraints: gbc(gridx: 1, gridy: 0, weightx: 1, weighty: 0.8,
                            anchor: GBC.PAGE_START, fill: GBC.BOTH)) {
                        table(id: 'tblAssets', model: createTableModel(), 
                            selectionMode: SINGLE_SELECTION,
                            mouseClicked: { MouseEvent me -> onGridClick(me) })
                    }
                            
                    panel(border: BorderFactory.createCompoundBorder(
                        BorderFactory.createEmptyBorder(2, 2, 2, 2),
                        BorderFactory.createLineBorder(new Color(0x9297a1))),
                        constraints: gbc(gridx: 1, gridy: 1, weightx: 1, weighty: 0.2,
                            anchor: GBC.PAGE_END, fill: GBC.BOTH), id: 'pnlPreview')
                }
            }
            tableSorter = TableComparatorChooser.install(sb.tblAssets, sortedList,
                TableComparatorChooser.MULTIPLE_COLUMN_MOUSE_WITH_UNDO)
            sb.listChapters.setSelectedIndex 0
        }
        sb.tblAssets.selectionModel.addListSelectionListener(
            [valueChanged: { ListSelectionEvent lse ->
                int row = sb.tblAssets.selectedRow
                if (row != -1) 
                    onRowSelect(sb.tblAssets.selectedRow)
            }] as ListSelectionListener)
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
        authorById = new HashMap<Integer, Category>()
        items = Network.executeHTTPGet("examiner/list")
        items.eachWithIndex{ item, i ->
            Category c = new Category(item)
            authors.add c
            authorById.put c.id, c
        }
        
        // Collections for display table
        artefactsEventList = new BasicEventList<Asset>()
        sortedList = new SortedList(artefactsEventList)
        filteredList = new FilterList<Asset>(sortedList)
        artefactsEventList.clear()
        def assets = []
        chapters.each { chapter ->
            items = Network.executeHTTPGet("sku/list?c=${chapter.id}")
            items.each{ item ->
                assets << Asset.getInstance(item, AssetClass."${item.assetClass}")
            }
        }
        artefactsEventList.addAll assets
    }
    
    private def onGridClick = { MouseEvent me ->
        def row = sb.tblAssets.rowAtPoint(me.getPoint())
        if (me.getClickCount() == 2) {
            new Editor(filteredList.get(row).load()).launchGeneric()
        } else if (me.getClickCount() == 1) {
            onRowSelect(row)
        }
    }
        
    private def onRowSelect = { int row ->
        Asset selected = filteredList.get(row).load()
        IEditable e = (IEditable)selected
        def tex = e.getPanels()[0].getComponents()[0].tex
        if (tex.length() > 0) {
            sb.pnlPreview.removeAll()
            sb.pnlPreview.add new TeXLabel(tex, "Preview")
            sb.pnlPreview.revalidate()
            sb.pnlPreview.repaint()        
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
        def url = "${assetClass.toString().toLowerCase()}/add?e=${userId}&c=${chapter.id}"
        def params = Network.executeHTTPPost(url)
        params.authorId = userId
        params.chapterId = chapter.id
        params.assetClass = assetClass
        Asset newAsset = Asset.getInstance(params, assetClass)
        newAsset.create()
        // refresh list
        artefactsEventList.add newAsset
        sb.listChapters.setSelectedValue(chapter, true)
    }
    
    private def filter = {
        sb.tblAssets.clearSelection()
        List<Catalog> authorSelection = sb.listAuthors.getSelectedValuesList()
        List<Catalog> chapterSelection = sb.listChapters.getSelectedValuesList()
        List<AssetClass> classSelection = sb.listClasses.getSelectedValuesList()
        AssetsMatcher matcher = new AssetsMatcher(chapterSelection, authorSelection, classSelection, this)
        filteredList.setMatcher(matcher)
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
            list(id: 'listChapters', listData: chapters, valueChanged: { filter() },
                layoutOrientation: VERTICAL, visibleRowCount: 12)
        }
        sb.scrollPane() {
            list(id: 'listAuthors', listData: authors, valueChanged: { filter() },
                layoutOrientation: VERTICAL, visibleRowCount: 3)
        }
        sb.scrollPane() {
            list(id: 'listClasses', listData: classes, valueChanged: { filter() },
                layoutOrientation: VERTICAL, visibleRowCount: 3)
        }
        sb.scrollPane() {
            list(id: 'listStates', listData: states,
                layoutOrientation: VERTICAL, visibleRowCount: 4)
        }
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
                    actionPerformed: { })
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
    private EnumSet<AssetClass> classes = EnumSet.allOf(AssetClass.class)
    private EnumSet<AssetState> states = EnumSet.allOf(AssetState.class)
    
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
    private Set classes = new HashSet<AssetClass>()

    /**
     * Create a new {@link AssetsByChapterMatcher} that matches only 
     * {@link Asset}s belonging to one or more chapters in the specified list.
     */
    public AssetsMatcher(Collection<Category> chapters, Collection<Category> authors, 
        Collection<AssetClass> classes, Clerk clerk) {
        // make a defensive copy of the assets
        this.chapters.addAll(chapters)
        this.authors.addAll(authors)
        this.classes.addAll(classes)
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
        
        def match = chapters.contains(clerk.chapterById.get(chapterId))
        if (authors.size() > 0) {
            match = match && authors.contains(clerk.authorById.get(authorId))
        }
        if (classes.size() > 0) {
            match = match && classes.contains(asset.assetClass)
        }
        match
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

