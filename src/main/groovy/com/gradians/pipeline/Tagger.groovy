package com.gradians.pipeline

import ca.odell.glazedlists.GlazedLists
import ca.odell.glazedlists.matchers.TextMatcherEditor
import ca.odell.glazedlists.swing.AutoCompleteSupport
import groovy.swing.SwingBuilder
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Color
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.event.ActionEvent
import java.nio.file.Path
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

import static java.awt.GridBagConstraints.HORIZONTAL
import static java.awt.GridBagConstraints.BOTH
import static javax.swing.WindowConstants.DISPOSE_ON_CLOSE
import static javax.swing.JFrame.EXIT_ON_CLOSE

class Tagger {
    
    final String delim = "\n"
    
    // Workers
    TagLib lib
    Catalog catalog
    Network network
    Question q
    
    // Widgets
    SwingBuilder sb
    
    public Tagger(Question q) {
        this.q = q
        Path bank = q.qpath.getParent().getParent().getParent().getParent()
        Path catalogPath = bank.resolve("common").resolve("catalog")
        this.catalog = new Catalog(catalogPath)
        this.lib = new TagLib(catalogPath)
        this.network = new Network()
        
        this.getBundleInfo()        
    }

    def go(boolean topLevel = false) {
        sb = new SwingBuilder()
        sb.edt {
            lookAndFeel: 'MetalLookAndFeel'
            frame(title: q.uid, size: [600, 480], show: true, locationRelativeTo: null,
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
                            button(id: 'btnAddBundle', text: 'Generate', actionPerformed: addBundle)
                            label(id: 'lblBundles', q.bundle)
                        }
                    }
                        
                    panel(border: BorderFactory.createTitledBorder("Concepts"), 
                        constraints: gbc(gridx: 0, gridy: 2, weightx: 1, weighty: 1, fill: BOTH)) {
                        gridBagLayout()
        
                        scrollPane(constraints: gbc(gridx: 0, gridy: 0, gridwidth: 3, 
                            weightx: 1.0, weighty: 1, fill: BOTH, insets: [5, 0, 5, 0])) {
                            textArea(id: 'taTopiks', focusable: false)
                        }                        
                        comboBox(id: 'cbTopik', actionPerformed: typeahead,
                            constraints: gbc(gridx: 0, gridy: 1, 
                                weightx: 1.0, weighty: 0, fill: HORIZONTAL, insets: [0, 0, 5, 0]))
                    }
                        
                    panel(border: BorderFactory.createTitledBorder("Actions"),
                        constraints: gbc(gridx: 0, gridy: 4, weightx: 1, fill: HORIZONTAL)) {    
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
    
    def addBundle = {
        def lblBundle = "${sb.cbBook.selectedItem.tag}-${sb.cbChapter.selectedItem.tag}-${sb.cbExercise.selectedItem.tag}"
        def lblQsn = "${sb.cbLabelQsn.selectedItem}"
        def lblPart = sb.cbLabelPart.selectedItem.empty ? "" : "-${sb.cbLabelPart.selectedItem}"
        def lblSubpart = sb.cbLabelSubpart.selectedItem.empty ? "" : "-${sb.cbLabelSubpart.selectedItem}"
        def text = "${lblBundle}|${lblQsn}${lblPart}${lblSubpart}"
        
        sb.lblBundles.text = text
        q.bundle = text
        
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
        q.concepts = sb.taTopiks.getText().split(delim)
        try {
            network.addToBundle(q)
            sb.optionPane().showMessageDialog(null, 
                "Tagged!", "Result", JOptionPane.INFORMATION_MESSAGE)
        } catch (Exception e) {
            println e
        }
    }        
    
    def getBundleInfo = {
        try {
            def bundleId = network.getBundleInfo(q)
            q.bundle = bundleId.length() == 0 ? NO_BUNDLE_ASSIGNED : bundleId
        } catch (Exception e) {
            q.bundle = NO_BUNDLE_INFO
        }
    }
    
    def final NO_BUNDLE_INFO = "No Bundle Info"
    def final NO_BUNDLE_ASSIGNED = "Not assigned Bundle"

}
