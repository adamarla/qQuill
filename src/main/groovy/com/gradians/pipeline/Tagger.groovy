package com.gradians.pipeline

import ca.odell.glazedlists.GlazedLists
import ca.odell.glazedlists.matchers.TextMatcherEditor
import ca.odell.glazedlists.swing.AutoCompleteSupport

import groovy.json.JsonBuilder
import groovy.swing.SwingBuilder
import groovy.model.DefaultTableModel

import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Color
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.event.ActionEvent
import java.io.File;
import java.nio.file.DirectoryStream
import java.nio.file.Path
import java.nio.file.Files

import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComboBox
import javax.swing.JDialog
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.JScrollPane
import javax.swing.border.TitledBorder
import javax.swing.filechooser.FileView;
import javax.swing.filechooser.FileFilter

import static java.nio.file.LinkOption.NOFOLLOW_LINKS
import static java.awt.GridBagConstraints.HORIZONTAL
import static java.awt.GridBagConstraints.BOTH
import static javax.swing.WindowConstants.DISPOSE_ON_CLOSE
import static javax.swing.JFrame.EXIT_ON_CLOSE

class Tagger {
    
    final String delim = "\n"
    
    Question[] qsns
    Path path, vault
    
    // Workers
    TagLib lib
    Catalog catalog
    Network network
    
    // Widgets
    SwingBuilder sb
    
    public Tagger(Path p) {
        int level
        Path tmp = p
        while (!tmp.getFileName().toString().equals("vault") &&            
            !tmp.equals(p.getRoot())) {
            tmp = tmp.getParent()
        }
        vault = tmp
        
        Path relative = tmp.relativize(p)
        if (relative.getNameCount() == 2) {
            this.path = p
        } else if (relative.getNameCount() == 3) {
            this.path = p.getParent()
        }
        
        Path bank = vault.getParent()
        Path catalogPath = bank.resolve("common").resolve("catalog")
        catalog = new Catalog(catalogPath)
        lib = new TagLib(catalogPath)
        network = new Network()
        
    }

    def go(boolean topLevel = false) {
        sb = new SwingBuilder()
        sb.edt {
            lookAndFeel: 'MetalLookAndFeel'
            frame(title: "Quill (${Editor.VERSION}) - Tagger",
                    size: [600, 600], show: true, locationRelativeTo: null,
                    defaultCloseOperation: topLevel ? EXIT_ON_CLOSE : DISPOSE_ON_CLOSE) {
                panel(border: BorderFactory.createEmptyBorder()) {
                    gridBagLayout()
                    
                    panel(border: BorderFactory.createTitledBorder("Context"),
                        constraints: gbc(gridx: 0, gridy: 0, weightx: 1, weighty: 0, fill: HORIZONTAL)) {                        
                        ["Grade", "Subject", "Publisher"].each { 
                            label(text: "${it}")
                            comboBox(id: "cb${it}", model: catalog."get${it}s"(), selectedIndex: 0)
                        }
                    }
                        
                    panel(border: BorderFactory.createTitledBorder("Source Details"),
                        constraints: gbc(gridx: 0, gridy: 1, weightx: 1, fill: HORIZONTAL)) {
                        gridBagLayout()
                        
                        ["Book", "Chapter", "Exercise"].eachWithIndex { content, i ->
                            label(id: "lbl${content}", text: "${content}",
                                constraints: gbc(gridx: 0, gridy: i))
                            comboBox(id: "cb${content}",
                                constraints: gbc(gridx: 1, gridy: i, gridwidth: 4, weightx: 1,
                                    fill: HORIZONTAL, insets: [0, 5, 5, 0]))
                        }
                        
                        button(id: 'btnBrowse', text: 'Browse', actionPerformed: chooseDir,
                            constraints: gbc(gridx: 0, gridy: 3))
                        label(id: 'lblDirPath', border: BorderFactory.createEmptyBorder(5, 5, 5, 5),
                            constraints: gbc(gridx: 1, gridy: 3, weightx: 1, fill: HORIZONTAL))
                    }
                        
                    tabbedPane(id: 'tpFunction', 
                        constraints: gbc(gridx: 0, gridy: 3, weightx: 1, fill: HORIZONTAL)) {                    
                        
                        panel(name: 'List By') {
                            buttonGroup(id: 'bgListMode').with { group ->
                                radioButton(id: 'rbEx', text: 'Exercise', buttonGroup: group)
                                radioButton(id: 'rbDir',text: 'Directory', buttonGroup: group, selected: true)
                                button(text: 'Refresh', actionPerformed: refresh)
                            }
                            label(text: 'open for ')
                            button(text: 'Edit', actionPerformed: launchEditor)
                            button(text: 'Preview', actionPerformed: launchPreview)
                        }
                        
                        panel(name: 'Block Slots') {
                            label(text: 'Starting at')
                            ["Qsn", "Part", "Subpart"].eachWithIndex { component, i ->
                                comboBox(id: "cbLabel${component}", items: lib."get${component}"())
                            }
                            label(text: 'these many')
                            comboBox(id: 'cbLabelRange', items: lib.getQsn(), selectedIndex: 1)
                            label(text: '(default = 1)')
                        }
                    }
                        
                    tabbedPane(constraints: gbc(gridx: 0, gridy: 4, weightx: 1, weighty: 1, fill: BOTH)) {                        
                        vbox(name: "Bundles") {
                            scrollPane() {
                                table(id: 'tblSlots', mouseClicked: updateLabel) {
                                    tableModel() {
                                        propertyColumn(header: 'UID', propertyName: 'uid', editable: false)
                                        propertyColumn(header: 'Bundle', propertyName: 'bundle', editable: false)
                                        propertyColumn(header: 'Label', propertyName: 'label', editable: false)
                                    }
                                }
                            }
                        }
                        
                        panel(name: "Concepts") {
                            gridBagLayout()
            
                            scrollPane(constraints: gbc(gridx: 0, gridy: 0, gridwidth: 3,
                                weightx: 1.0, weighty: 1, fill: BOTH, insets: [5, 0, 5, 0])) {
                                textArea(id: 'taTopiks', focusable: false)
                            }
                            comboBox(id: 'cbTopik', actionPerformed: typeahead,
                                constraints: gbc(gridx: 0, gridy: 1,
                                    weightx: 1.0, weighty: 0, fill: HORIZONTAL, insets: [0, 0, 5, 0]))
                        }    
                    }
                    
                    panel(border: BorderFactory.createTitledBorder("Actions"),
                        constraints: gbc(gridx: 0, gridy: 5, weightx: 1, fill: HORIZONTAL)) {    
                        button(id: 'btnTag', text: 'Commit', enabled: false, actionPerformed: tag)
                    }
                }
            }
            pack: true
            
            ["Grade", "Subject", "Publisher"].each {
                sb."cb${it}".actionPerformed = popSource
            }
            
            sb.cbBook.actionPerformed = popChapters
            sb.cbChapter.actionPerformed = popExercises
            sb.cbExercise.actionPerformed = refresh
            
            sb.cbPublisher.selectedIndex = 0
            
            AutoCompleteSupport acs1 = AutoCompleteSupport.
                install(sb.cbTopik, GlazedLists.eventListOf(lib.getConcepts()))
            acs1.setFilterMode(TextMatcherEditor.CONTAINS)
            acs1.setSelectsTextOnFocusGain(true)
            acs1.setHidesPopupOnFocusLost(true)            
        }
    }
    
    def launchEditor = {
        int row = sb.tblSlots.getSelectedRow()
        if (row >= 0) {
            (new Editor(qsns[row])).launch(false)
        }
    }
    
    def launchPreview = {
        int row = sb.tblSlots.getSelectedRow()
        if (row >= 0) {
            (new Renderer(qsns[row])).toSwing()
        }
    }
    
    def refresh = { ActionEvent ae ->
        if (sb.tpFunction.selectedIndex != 0)
            return
        
        sb.tblSlots.dataModel.getRows().clear()
        if (sb.rbDir.selected) {
            if (path == null) {
                chooseDir()                
            } else if (!sb.lblDirPath.text.equals(path.toString())) {
                sb.lblDirPath.text = path.toString() 
                listDirectory()
            }
        } else {
            listExercise()
        }
        
        sb.tblSlots.dataModel.getRows().addAll getTableData()
        sb.tblSlots.dataModel.fireTableDataChanged()
    }
    
    def updateLabel = {
        if (sb.tpFunction.selectedIndex == 0)
            return
        
        if (sb.cbLabelQsn.selectedItem.empty)
            return
        
        def bundleId = getBundleId()

        int initial = Integer.valueOf(sb.cbLabelQsn.selectedItem)
        int range = Integer.valueOf(sb.cbLabelRange.selectedItem)
        for (int i = 1; i <= range; i++) {
            int row = sb.tblSlots.getSelectedRow()+(i-1)
                        
            if (row >= sb.tblSlots.dataModel.getRows().size())
                continue
            
            def qsn = initial + (i-1)
            def part = sb.cbLabelPart.selectedItem.empty ? "" : "-${sb.cbLabelPart.selectedItem}"
            def subpart = sb.cbLabelSubpart.selectedItem.empty ? "" : "-${sb.cbLabelSubpart.selectedItem}"
        
            sb.tblSlots.dataModel.setValueAt("${bundleId}", row, 1)
            sb.tblSlots.dataModel.setValueAt("${qsn}${part}${subpart}", row, 2)
            sb.tblSlots.dataModel.fireTableCellUpdated(row, 1)
            sb.tblSlots.dataModel.fireTableCellUpdated(row, 2)             
        }
        
        sb.btnTag.enabled = true        
    }
    
    def popExercises = { ActionEvent ae ->
        sb.cbExercise.model = catalog.getExercises(sb.cbBook.selectedItem.tag, sb.cbChapter.selectedItem.tag)
        if (sb.cbExercise.model.size() > 0)
            sb.cbExercise.selectedIndex = 0
    }
    
    def popChapters = { ActionEvent ae ->
        sb.cbChapter.model = catalog.getChapters(sb.cbBook.selectedItem.tag)
        if (sb.cbChapter.model.size() > 0)
            sb.cbChapter.selectedIndex = 0
    }
    
    def popSource = { ActionEvent ae ->
        if (!ae.getActionCommand().equals('comboBoxChanged'))
            return
            
        if (sb.cbGrade.getSelectedItem() == null ||
            sb.cbSubject.getSelectedItem() == null ||
            sb.cbPublisher.getSelectedItem() == null)
            return
              
        sb.cbBook.model = catalog.getBooks(sb.cbGrade.getSelectedItem().tag,
            sb.cbSubject.getSelectedItem().tag, sb.cbPublisher.getSelectedItem().tag)        
        if (sb.cbBook.model.size() > 0)
            sb.cbBook.selectedIndex = 0        
    }
    
    def listDirectory = { ActionEvent ae ->
        DirectoryStream<Path> stream = Files.newDirectoryStream(path)
        ArrayList<Question> list = new ArrayList<Question>()
        for (Path p : stream) {
            if (Files.isDirectory(p, NOFOLLOW_LINKS)) {
                list.add(new Question(p))
            }
        }
        for (Question q : list) {
            try {
                String bundleId = network.getBundleInfo(q)
                q.bundle = bundleId.length() > 1 ? bundleId : NO_BUNDLE_ASSIGNED
                if (!q.bundle.equals(NO_BUNDLE_ASSIGNED)) {
                    def lblFilePath = q.qpath.resolve("${q.bundle.replace('|', '-')}.lbl")
                    if (!Files.exists(lblFilePath))
                        Files.createFile(lblFilePath)
                }                
            } catch (Exception e) {
                q.bundle = NO_BUNDLE_INFO
            }
        }
        qsns = list.toArray(new Question[list.size()])
    }
    
    def listExercise = { ActionEvent ae ->
        def bqs = []
        def bundleId = getBundleId()
        try {
            bqs = network.getBundleQuestions(bundleId)
        } catch (Exception e) { }
        ArrayList<Question> list = new ArrayList<Question>()
        for (def bq : bqs) {
            Question q = new Question(vault.resolve(bq.uid))
            q.bundle = "${bundleId}|${bq.label}"
            def lblFilePath = q.qpath.resolve("${q.bundle.replace('|', '-')}.lbl")
            if (!Files.exists(lblFilePath))
                Files.createFile(lblFilePath)
            list.add(q)
        }
        Collections.sort(list, new QuestionSorter())
        qsns = list.toArray(new Question[list.size()])
    }
    
    def typeahead = { ActionEvent ae ->
        if (!ae.getActionCommand().equals('comboBoxEdited'))
            return
            
        JComboBox source 
        JTextArea target
        source = ae.getSource()
        switch (ae.getSource()) {
            case sb.cbTopik:
                target = sb.taTopiks
                break
            case sb.cbSource:
                target = sb.taSources
                break
            default:
                break
        }
        if (target != null) {
            def targetText = target.getText()
            if (!targetText.contains(source.getSelectedItem())) {
                if (targetText.length() != 0)
                    target.append(delim)
                target.append(source.getSelectedItem())
            }
            target.setFocusable(true)
        }
        source.setSelectedItem(null)
    }
    
    def tag = {
        boolean diff = false
        boolean error = false
        def model = sb.tblSlots.dataModel
        
        List<Question> alreadyTagged = new ArrayList<Question>()
        qsns.eachWithIndex { Question q, int i ->
            String bundleId = "${model.getValueAt(i, 1)}|${model.getValueAt(i, 2)}"
            def lblFile
            if (bundleId.length() > 1 && !q.bundle.equals(bundleId)) {
                diff = true
                if (!q.bundle.equals(NO_BUNDLE_ASSIGNED)) {
                    lblFile = q.qpath.resolve("${q.bundle.replace('|', '-')}.lbl")
                    Files.deleteIfExists(lblFile)                    
                }
                q.bundle = bundleId
                q.concepts = sb.taTopiks.getText().split(delim)
                try {
                    def uid = network.addToBundle(q)
                    if (model.getValueAt(i, 0).equals(uid)) {
                        lblFile = q.qpath.resolve("${q.bundle.replace('|', '-')}.lbl")
                        Files.createFile(q.qpath.resolve(lblFile))
                    } else {
                        q.bundle = NO_BUNDLE_ASSIGNED
                        def qExisting = new Question(vault.resolve(uid))
                        qExisting.bundle = bundleId
                        alreadyTagged.add(qExisting)
                    }
                } catch (Exception e) {
                    println e
                    error = true 
                }
            }
        }
        if (error) {
            sb.optionPane().showMessageDialog(null,
                "Network Error", "Result", JOptionPane.ERROR_MESSAGE)
        } else if (diff) {
            if (alreadyTagged.size() > 0) {
                model.getRows().clear()
                qsns = qsns + alreadyTagged.toArray(new Question[alreadyTagged.size()])
                model.getRows().addAll getTableData()
                model.fireTableDataChanged()        
            }
            sb.optionPane().showMessageDialog(null,
                "Tagged!", "Result", JOptionPane.INFORMATION_MESSAGE)            
        }
    }
    
    def getTableData = {
        def data = []
        qsns.each { Question q ->
            def row
            if (q.bundle.equals(NO_BUNDLE_ASSIGNED)) {
                row = [uid: q.uid, bundle: '', label: '']
            } else {
                def tokens = q.bundle.split("\\|")
                def hasXml = Files.exists(q.qpath.resolve(Question.XML_FILE))
                row = [uid: hasXml ? "<html><b>${q.uid}</b></html>" : "${q.uid}",
                    bundle: tokens[0], label: tokens[1]]           
            }
            data << row
        }
        data
    }
    
    private def String getBundleId() {
        ["Book", "Chapter", "Exercise"].collect{ def locator ->
            sb."cb${locator}".selectedItem.tag
        }.join("-")
    }
    
    private def chooseDir = {
        def openFileDialog = new JFileChooser(
            dialogTitle: "Choose dir", fileSelectionMode : JFileChooser.FILES_AND_DIRECTORIES,
            fileFilter: [getDescription: { -> "Directory" }, accept: { file -> file.isDirectory() }] as FileFilter)
        openFileDialog.setCurrentDirectory(vault.toFile())
        openFileDialog.setFileView(new FileView() {
            @Override
            public Boolean isTraversable(File f) {
                return !(f.isDirectory() &&
                    f.toPath().getParent().getParent().equals(vault))
            }
        })
        if (openFileDialog.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            path = openFileDialog.getSelectedFile().toPath()
            sb.lblDirPath.text = path.toString() 
            listDirectory()
        }
    }
    

    final String NO_BUNDLE_INFO = "No Bundle Info"
    final String NO_BUNDLE_ASSIGNED = "No Label"

}

class QuestionSorter implements Comparator {
    
    @Override
    public int compare(Object obj1, Object obj2) {
        return ((Question)obj1).bundle.compareTo(((Question)obj2).bundle)
    }
}
