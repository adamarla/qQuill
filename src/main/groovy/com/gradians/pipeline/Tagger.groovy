package com.gradians.pipeline

import ca.odell.glazedlists.GlazedLists
import ca.odell.glazedlists.matchers.TextMatcherEditor
import ca.odell.glazedlists.swing.AutoCompleteSupport

import groovy.json.JsonBuilder
import groovy.swing.SwingBuilder
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Color
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.event.ActionEvent
import java.nio.file.DirectoryStream
import java.nio.file.Path
import java.nio.file.Files

import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComboBox
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.JScrollPane
import javax.swing.border.TitledBorder

import static java.nio.file.LinkOption.NOFOLLOW_LINKS
import static java.awt.GridBagConstraints.HORIZONTAL
import static java.awt.GridBagConstraints.BOTH
import static javax.swing.WindowConstants.DISPOSE_ON_CLOSE
import static javax.swing.JFrame.EXIT_ON_CLOSE

class Tagger {
    
    final String delim = "\n"
    
    Question[] qsns
    Path path
    
    // Workers
    TagLib lib
    Catalog catalog
    Network network
    
    // Widgets
    SwingBuilder sb
    
    public Tagger(Path p) {
        Path vault
        int level
        Path tmp = p.getParent()
        while (!tmp.getFileName().toString().equals("vault") &&            
            !tmp.equals(p.getRoot())) {
            tmp = tmp.getParent()
        }
        assert tmp.getFileName().toString().equals("vault")
        
        Path relative = tmp.relativize(p)
        if (relative.getNameCount() == 2) {
            this.path = p
        } else if (relative.getNameCount() == 3) {
            this.path = p.getParent()
        } else {
            throw new Exception("Run tagger from question dir or 1 level up")
        }
        
        Path bank = this.path.getParent().getParent().getParent()        
        Path catalogPath = bank.resolve("common").resolve("catalog")
        this.catalog = new Catalog(catalogPath)
        this.lib = new TagLib(catalogPath)
        this.network = new Network()
        
        this.getBundleInfo(this.path)
    }

    def go(boolean topLevel = false) {        
        sb = new SwingBuilder()
        sb.edt {
            lookAndFeel: 'MetalLookAndFeel'
            frame(title: "Quill (${Editor.VERSION}) - Tagger - ${path.getFileName()}",
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

                        label(text:"Label", constraints: gbc(gridx: 0, gridy: 3))
                        panel(constraints: gbc(gridx: 1, gridy: 3, gridwidth: 4, weightx: 1,
                            fill: HORIZONTAL, insets: [0, 5, 5, 0])) {
                            ["Qsn", "Part", "Subpart"].eachWithIndex { component, i ->
                                comboBox(id: "cbLabel${component}", items: lib."get${component}"())
                            }
                        }
                    }
                        
                    tabbedPane(constraints: gbc(gridx: 0, gridy: 2, weightx: 1, weighty: 1, fill: BOTH)) {
                        
                        vbox(name: "Bundles") {
                            panel() {
                                checkBox(id: 'chkBoxEditable', text: 'Editable', selected: false)
                                button(text: 'Launch Editor', actionPerformed: launchEditor)    
                                button(text: 'Launch Preview', actionPerformed: launchPreview)    
                            }
                            scrollPane() {
                                table(id: 'tblSlots', mouseClicked: updateLabel) {
                                    tableModel(list: getTableData()) {
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
                        constraints: gbc(gridx: 0, gridy: 3, weightx: 1, fill: HORIZONTAL)) {    
                        button(id: 'btnTag', text: 'Commit', enabled: false, actionPerformed: tag)
                    }
                }
            }
            pack: true
            
            ["Grade", "Subject", "Publisher"].each {
                sb."cb${it}".actionPerformed = popSource
            }
            
            sb.cbBook.actionPerformed = popBooks
            sb.cbChapter.actionPerformed = popChapters
            
            sb.cbPublisher.selectedIndex = 0
            
            AutoCompleteSupport acs1 = AutoCompleteSupport.install(sb.cbTopik, GlazedLists.eventListOf(lib.getConcepts()))
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
    
    def updateLabel = {
        if (!sb.chkBoxEditable.selected) {
            return
        }
        int row = sb.tblSlots.getSelectedRow()
        
        def qsn = sb.cbLabelQsn.selectedItem.empty ? "" : "${sb.cbLabelQsn.selectedItem}"
        def bundle = sb.cbLabelQsn.selectedItem.empty ? "" : 
            "${sb.cbBook.selectedItem.tag}-${sb.cbChapter.selectedItem.tag}-${sb.cbExercise.selectedItem.tag}"
        def part = sb.cbLabelPart.selectedItem.empty ? "" : "-${sb.cbLabelPart.selectedItem}"
        def subpart = sb.cbLabelSubpart.selectedItem.empty ? "" : "-${sb.cbLabelSubpart.selectedItem}"
        
        sb.tblSlots.dataModel.setValueAt("${bundle}", row, 1)
        sb.tblSlots.dataModel.setValueAt("${qsn}${part}${subpart}", row, 2)
        sb.tblSlots.dataModel.fireTableCellUpdated(row, 1)
        sb.tblSlots.dataModel.fireTableCellUpdated(row, 2)
        
        sb.btnTag.enabled = true        
    }
    
    def popChapters = { ActionEvent ae ->
        sb.cbExercise.model = catalog.getExercises(sb.cbBook.selectedItem.tag, sb.cbChapter.selectedItem.tag)
        if (sb.cbExercise.model.size() > 0)
            sb.cbExercise.selectedIndex = 0
    }
    
    def popBooks = { ActionEvent ae ->
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
                    network.addToBundle(q)
                    lblFile = q.qpath.resolve("${q.bundle.replace('|', '-')}.lbl")
                    Files.createFile(q.qpath.resolve(lblFile))
                } catch (Exception e) {
                    error = true 
                }
            }
        }
        if (error)
            sb.optionPane().showMessageDialog(null,
                "Network Error", "Result", JOptionPane.ERROR_MESSAGE)
        else if (diff)
            sb.optionPane().showMessageDialog(null,
                "Tagged!", "Result", JOptionPane.INFORMATION_MESSAGE)
    }
    
    def getTableData = {
        def data = []
        qsns.each { Question q ->
            def row
            if (q.bundle.equals(NO_BUNDLE_ASSIGNED)) {
                row = [uid: q.uid, bundle: '', label: '']
            } else {
                def tokens = q.bundle.split(BNDL_DELIM)
                def hasXml = Files.exists(q.qpath.resolve(QSN_XML))
                row = [uid: hasXml ? "<html><b>${q.uid}</b></html>" : "${q.uid}",
                    bundle: tokens[0], label: tokens[1]]           
            }
            data << row
        }
        data
    }
    
    def getBundleInfo(Path parent) {
        DirectoryStream<Path> stream = Files.newDirectoryStream(parent)
        ArrayList<Question> list = new ArrayList<Question>()
        for (Path path : stream) {
            if (Files.isDirectory(path, NOFOLLOW_LINKS)) {
                list.add(new Question(path))
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
        Collections.sort(list, new Comparator() {            
            @Override
            public int compare(Object obj1, Object obj2) {
                return ((Question)obj1).bundle.compareTo(((Question)obj2).bundle)
            }
        })
        qsns = list.toArray(new Question[list.size()])
    }

    final String QSN_XML = "question.xml"    
    final String BNDL_DELIM = "\\|"
    final String NO_BUNDLE_INFO = "No Bundle Info"
    final String NO_BUNDLE_ASSIGNED = "No Label"

}
