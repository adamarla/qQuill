package com.gradians.pipeline.tag

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.swing.SwingBuilder

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.awt.Color
import java.awt.event.MouseEvent
import java.awt.GridBagConstraints as GBC
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent

import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.SwingWorker
import javax.swing.border.Border
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener
import javax.swing.KeyStroke
import javax.swing.text.Keymap
import javax.swing.JOptionPane

import com.gradians.pipeline.data.Asset
import com.gradians.pipeline.data.AssetClass
import com.gradians.pipeline.data.AssetState
import com.gradians.pipeline.data.Question
import com.gradians.pipeline.data.Skill
import com.gradians.pipeline.data.Snippet
import com.gradians.pipeline.edit.Editor
import com.gradians.pipeline.edit.IEditable
import com.gradians.pipeline.edit.TeXLabel
import com.gradians.pipeline.tex.SVGIcon
import com.gradians.pipeline.tex.TeXHelper
import com.gradians.pipeline.util.Config
import com.gradians.pipeline.util.Gitter
import com.gradians.pipeline.util.Network

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
import static javax.swing.JFrame.EXIT_ON_CLOSE
import static javax.swing.ListSelectionModel.SINGLE_SELECTION
import static javax.swing.JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
import static javax.swing.WindowConstants.DISPOSE_ON_CLOSE


class Clerk {
    
    SwingBuilder sb
    Config config
    
    public Clerk() {
        config = Config.getInstance()
    }
    
    def go(boolean topLevel = true) {
        sb = new SwingBuilder()
        sb.edt {
            lookAndFeel 'nimbus'
            frame(id: 'frmClerk', title: "Quill (${Editor.VERSION}) - Clerk",
                size: [840, 600], show: true, locationRelativeTo: null,
                defaultCloseOperation: topLevel ? EXIT_ON_CLOSE : DISPOSE_ON_CLOSE) {
                getMenuBar()
                splitPane(orientation: javax.swing.JSplitPane.HORIZONTAL_SPLIT) {
                    vbox() { getSelectorLists() }
                    splitPane(id: 'spHoriz', orientation: javax.swing.JSplitPane.VERTICAL_SPLIT) {
                        scrollPane(id: 'spTable')
                        panel(id: 'pnlPreview', border: A_BORDER)
                    }
                }
            }
                
            sb.doOutside {                
                loadAssets()
                sb.edt {
                    sb.pbMessage.string = "syncing files..."
                }
                pullPush()
                
                sb.doLater {
                    sb.listChapters.listData = chapters
                    sb.listAuthors.listData = authors
                    sb.listClasses.listData = classes
                    sb.listStates.listData = states
                    
                    sb.spTable.viewport.add createTable()
                    sb.spHoriz.resetToPreferredSizes()
    
                    tableSorter = TableComparatorChooser.install(sb.tblAssets, sortedList,
                        TableComparatorChooser.MULTIPLE_COLUMN_MOUSE_WITH_UNDO)
                    sb.listChapters.setSelectedIndex 0
                    this.dlgProgress.visible = false
                }
            }
        }
        this.showProgressBar("Syncing db...")
    }
    
    private void loadAssets() {        
        chapters = new ArrayList<Category>()
        def items = Network.executeHTTPGet("chapter/list")
        Category c
        items.eachWithIndex{ item, i ->
            c = new Category(item)
            chapters.add c
            config.addChapter(c.id, c.name)
        }
        c = new Category(id: 0, name: 'Uncategorized')
        chapters.add c
        config.addChapter(c.id, c.name)
        
        authors = new ArrayList<Category>()
        items = Network.executeHTTPGet("examiner/list")
        items.eachWithIndex{ item, i ->
            c = new Category(item)
            authors.add c
            config.addAuthor(c.id, c.name)
        }
        
        config.commit()
        
        def skills = []
        // Collections for display table
        artefactsEventList = new BasicEventList<Asset>()
        sortedList = new SortedList(artefactsEventList)
        filteredList = new FilterList<Asset>(sortedList)
        artefactsEventList.clear()
        def assets = []
        items = Network.executeHTTPGet("sku/list")
        items.each{ item ->
            artefactsEventList.add Asset.getInstance(item)
            if (AssetClass.valueOf(item.assetClass) == AssetClass.Skill)
                skills << item
        }
        Path skillCache = config.configPath.resolveSibling("skills.json")
        skillCache.toFile().write new JsonBuilder(skills).toString()
    }
    
    private def onGridClick = { MouseEvent me ->
        def row = sb.tblAssets.rowAtPoint(me.getPoint())
        if (me.getClickCount() == 2) {
            Asset a = filteredList.get(row)
            launchEditor(a)
        } else if (me.getClickCount() == 1) {
            onRowSelect(row)
        }
    }
        
    private def onRowSelect = { int row ->
        if (!filteredList.size() || !(0..<filteredList.size()).contains(row))
            return
        Asset selected = filteredList.get(row)
        if (selected.isLoaded()) {
            IEditable e = (IEditable)selected
            def statement = e.getEditGroups()[0].getEditItems()[0]
            sb.pnlPreview.removeAll()
            if (statement.text.length()) {
                def drawable = new JLabel()
                if (statement.isImage) {
                    drawable = fileToJLabel(selected.getDirPath().resolve(statement.text))
                } else {
                    drawable = new TeXLabel(statement.text, "Preview")
                }                
                sb.pnlPreview.add drawable
            }    
            sb.pnlPreview.revalidate()
            sb.pnlPreview.repaint()
        }
    }
    
    private Asset createNewAsset(AssetClass assetClass) {
        // call server
        int chapterId = sb.listChapters.selectedValue.id
        def userId = config.getUser().id
        def url = "${assetClass.toString().toLowerCase()}/add"
        Map map = [e: userId, c: chapterId]
        
        // create asset
        def params = Network.executeHTTPPostBody(url, map)
        params.chapterId = chapterId
        params.authorId = userId
        params.assetClass = assetClass
        
        // add skill to the skill cache
        if (assetClass == AssetClass.Skill) {
            Path skillCache = config.configPath.resolveSibling("skills.json")
            def skills = new JsonSlurper().parse(skillCache.toFile())
            skills << params
            skillCache.toFile().write new JsonBuilder(skills).toString()
        }
        
        // refresh list
        Asset newAsset = Asset.getInstance(params)
        newAsset.create()
        artefactsEventList.add newAsset
        newAsset
    }
    
    private def launchEditor(Asset a) {
        new Editor(a).launch()
    }
    
    private def filter = {
        sb.tblAssets.clearSelection()
        List<Category> authorSelection = sb.listAuthors.getSelectedValuesList()
        List<Category> chapterSelection = sb.listChapters.getSelectedValuesList()
        List<AssetClass> classSelection = sb.listClasses.getSelectedValuesList()
        AssetsMatcher matcher = new AssetsMatcher(chapterSelection*.name, authorSelection*.name, classSelection)
        filteredList.setMatcher(matcher)
        filteredList.each { it.load() }
    }
    
    private def pullPush = {
        def bankPathString = config.getBankPath()
        Gitter gitter = new Gitter(new File("${bankPathString}/.git"))
        try {
             gitter.pullFromUpstream()
        } catch (Exception e) {
            JOptionPane.showMessageDialog(sb.frmClerk,
                "${e.getMessage()}\nResolve this issue on the command line",
                "Ooops", JOptionPane.ERROR_MESSAGE)
        }
    }
    
    private def createTable = {
        def tableAssets = sb.table(id: 'tblAssets', model: createTableModel(),
            selectionMode: SINGLE_SELECTION,
            mouseClicked: { MouseEvent me -> onGridClick(me) })        
        tableAssets.selectionModel.addListSelectionListener(
            [valueChanged: { ListSelectionEvent lse ->
                int row = tableAssets.selectedRow
                onRowSelect(tableAssets.selectedRow)
            }] as ListSelectionListener)
        tableAssets.getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).
            put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "Enter")
        tableAssets.getActionMap().put("Enter", 
            [actionPerformed: { ActionEvent ae -> 
                int row = tableAssets.selectedRow
                if (tableAssets.selectedRow != -1)
                    launchEditor(filteredList.get(row))
            }] as AbstractAction)
        tableAssets
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
                             config."get${columnNames[index]}"(object."${variableNames[index]}Id")
                             break
                     }
                 }
            ] as TableFormat)
        tableModel
    }
    
    private def getSelectorLists = {
        sb.scrollPane(horizontalScrollBarPolicy: HORIZONTAL_SCROLLBAR_NEVER) {
            list(id: 'listChapters', valueChanged: { filter() },
                layoutOrientation: VERTICAL, visibleRowCount: 12, selectionMode: SINGLE_SELECTION)
        }
        sb.scrollPane() {
            list(id: 'listAuthors', valueChanged: { filter() },
                layoutOrientation: VERTICAL, visibleRowCount: 3)
        }
        sb.scrollPane() {
            list(id: 'listClasses', valueChanged: { filter() },
                layoutOrientation: VERTICAL, visibleRowCount: 3)
        }
        sb.scrollPane() {
            list(id: 'listStates', layoutOrientation: VERTICAL, visibleRowCount: 4)
        }
    }
    
    private def getMenuBar = {
        sb.menuBar {
            menu(text: 'File', mnemonic: 'F') {
                menuItem(text: "Snippet", mnemonic: 'N',
                    accelerator: KeyStroke.getKeyStroke(KeyEvent.VK_N, KeyEvent.CTRL_DOWN_MASK),
                    actionPerformed: {
                        def asset = createNewAsset(AssetClass.Snippet) 
                        launchEditor(asset)                
                    })
                menuItem(text: "Problem", mnemonic: 'P',
                    accelerator: KeyStroke.getKeyStroke(KeyEvent.VK_P, KeyEvent.CTRL_DOWN_MASK),
                    actionPerformed: {
                        def asset = createNewAsset(AssetClass.Question) 
                        launchEditor(asset)
                    })
                menuItem(id: 'miNewSkill', text: "Skill", mnemonic: 'K',
                    accelerator: KeyStroke.getKeyStroke(KeyEvent.VK_K, KeyEvent.CTRL_DOWN_MASK),  
                    actionPerformed: {
                        def pane = sb.optionPane(
                            message: "Are you sure you want to create a new Skill?\n" +
                            "Not a Snippet or Question?",
                            optionType: JOptionPane.YES_NO_OPTION,
                            messageType: JOptionPane.QUESTION_MESSAGE,
                            options: ["Yes, New Skill", "No, never mind"])
                        def dialog = pane.createDialog(null, 'Hang on a sec!')
                        dialog.visible = true
                        
                        String value = (String)pane.getValue()
                        dialog.dispose()
                        if (value.startsWith("Yes")) {
                            def asset = createNewAsset(AssetClass.Skill)
                            launchEditor(asset)
                        }
                    })
                separator()
                menuItem(text: "Sync (pull)", mnemonic: 'Y',
                    accelerator: KeyStroke.getKeyStroke(KeyEvent.VK_Y, KeyEvent.CTRL_DOWN_MASK),
                    actionPerformed: {
                        sb.doOutside {  
                            pullPush()                            
                            sb.doLater {
                                this.dlgProgress.visible = false                               
                            }
                        }
                        this.showProgressBar("Syncing files ...")
                    })
                menuItem(text: "Quit", mnemonic: 'Q',
                    accelerator: KeyStroke.getKeyStroke(KeyEvent.VK_Q, KeyEvent.CTRL_DOWN_MASK),
                    actionPerformed: { dispose() })
            }
            menu(text: 'Help', mnemonic: 'H') {
                menuItem(text: "About", mnemonic: 'A', actionPerformed: { showAbout() })
                menuItem(text: "Settings", mnemonic: 'S', actionPerformed: { showSettings() })
            }            
        }
        sb.miNewSkill.visible = config.getUser().role.equals("admin")
    }
    
    private def showProgressBar(String message) {
        dlgProgress = sb.dialog(title: 'Please wait', modal: true, locationRelativeTo: null) {
            panel() {
                progressBar(id: 'pbMessage', indeterminate: true, string: message, stringPainted: true)
            }
        }
        dlgProgress.pack()
        dlgProgress.visible = true        
    }
    
    private def showSettings() {
        def dialog = sb.dialog(id: 'dlgSettings', title: 'Settings',
            modal: true, locationRelativeTo: sb.frmClerk) {
            vbox() {                
                table(id: 'tblSettings', showHorizontalLines: true, showVerticalLines: true) {
                    tableModel: {
                        def model = []
                        config.config.keySet().each {
                            model << [ 'name': it, 'value': config.get(it) ]
                        }
                        tableModel(list: model) {
                            closureColumn(header: 'Property', width: 100, read: { row -> return row.name} )
                            closureColumn(header: 'Value', width: 400, read: { row -> return row.value} )
                        }
                    }
                }
                panel() {
                    button(text: 'Apply', actionPerformed: { 
                        sb.dlgSettings.dispose() 
                    })
                    button(text: 'Cancel', actionPerformed: { 
                        sb.dlgSettings.dispose() 
                    })    
                }
            }
        }
        dialog.pack()
        dialog.visible = true
    }
    
    void showAbout() {
        def iconURL = Clerk.class.getClassLoader().getResource("logo-prepwell.png")
        def pane = sb.optionPane(message: 'Quill - author and administer assets\n Version 2.0',
            icon: new javax.swing.ImageIcon(iconURL))
        def dialog = pane.createDialog(sb.frmClerk, 'About Quill')
        dialog.visible = true
    }
    
    private JLabel fileToJLabel(Path path) {
        SVGIcon icon = new SVGIcon(path.toUri().toURL().toString())
        def label = new JLabel()
        label.icon = icon
        label
    }
    
    private def dlgProgress
        
    private List<Category> chapters
    private List<Category> authors
    private EnumSet<AssetClass> classes = EnumSet.allOf(AssetClass.class)
    private EnumSet<AssetState> states = EnumSet.allOf(AssetState.class)
    
    private FilterList<Asset> filteredList
    private SortedList<Asset> sortedList
    private EventList<Asset> artefactsEventList
    private TableComparatorChooser<Asset> tableSorter
    
    final Border A_BORDER = BorderFactory.createCompoundBorder(
        BorderFactory.createEmptyBorder(2, 2, 2, 2),
        BorderFactory.createLineBorder(new Color(0x9297a1)))    
}

class AssetsMatcher implements Matcher {

    protected Config config
    private Set<String> chapters = new HashSet<String>()
    private Set<String> authors = new HashSet<String>()
    private Set<AssetClass> classes = new HashSet<AssetClass>()

    /**
     * Create a new {@link AssetsByChapterMatcher} that matches only 
     * {@link Asset}s belonging to one or more chapters in the specified list.
     */
    public AssetsMatcher(Collection<String> chapters, Collection<String> authors, 
        Collection<AssetClass> classes) {
        this.config = Config.getInstance()
        
        // make a defensive copy of the assets
        this.chapters.addAll(chapters)
        this.authors.addAll(authors)
        this.classes.addAll(classes)
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
        def match = chapters.contains(config.getChapter(asset.chapterId))
        if (authors.size() > 0) {
            match = match && authors.contains(config.getAuthor(asset.authorId))
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
    public boolean equals(Object obj) {
        Category c = (Category)obj
        id == c.id && name.equals(c.name)
    }

    @Override
    public String toString() {
        name
    }
}

