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
import static javax.swing.JFrame.EXIT_ON_CLOSE
import static java.awt.GridBagConstraints.HORIZONTAL
import static java.awt.GridBagConstraints.BOTH

class UI {
    
    final String delim = "\n"
    
    // Workers
    TagLib lib
    Catalog catalog
    Network network
    Question q
    
    // Widgets
    SwingBuilder sb    
    
    public UI(Catalog catalog, Network network, TagLib lib, Question q) {
        this.lib = lib
        this.q = q
        this.network = network
        this.catalog = catalog
    }

    def go() {
        sb = new SwingBuilder()
        sb.edt {
            lookAndFeel: 'MetalLookAndFeel'
            frame(title: q.uid, size: [600, 480], show: true, locationRelativeTo: null, 
                defaultCloseOperation: EXIT_ON_CLOSE) {
                panel(border: BorderFactory.createEmptyBorder()) {
                    gridBagLayout()
                    
                    panel(border: BorderFactory.createTitledBorder("Context"),
                        constraints: gbc(gridx: 0, gridy: 0, weightx: 1, weighty: 0, fill: HORIZONTAL)) {
                        
                        label(text: 'Grade Levels') 
                        comboBox(id: 'cbGradeLevel', 
                            model: catalog.getGradeLevels(), selectedIndex: 0)
                        label(text: 'Subjects')
                        comboBox(id: 'cbSubject', 
                            model: catalog.getSubjects(), selectedIndex: 0)
                        label(text: 'Source')
                        comboBox(id: 'cbSource', 
                            model: catalog.getPublishers(), selectedIndex: 0)
                    }
                    
                    panel(border: BorderFactory.createTitledBorder("Source Details"),
                        constraints: gbc(gridx: 0, gridy: 1, weightx: 1, weighty: 1, fill: BOTH)) {
                        gridBagLayout()

                        label(id: 'lblQSource', text: 'Book',
                            constraints: gbc(gridx: 0, gridy: 0, weightx: 0.1, weighty: 0))
                        comboBox(id: 'cbQSource',
                            constraints: gbc(gridx: 1, gridy: 0, gridwidth: 4, weightx: 1, weighty: 0,
                                fill: HORIZONTAL, insets: [0, 5, 5, 0]))
                        
                        label(id: 'lblQBatch', text: 'Chapter',
                            constraints: gbc(gridx: 0, gridy: 1, weightx: 0.1, weighty: 0))
                        comboBox(id: 'cbQBatch',                            
                            constraints: gbc(gridx: 1, gridy: 1, gridwidth: 4, weightx: 1, weighty: 0,
                                fill: HORIZONTAL, insets: [0, 5, 5, 0]))
                        
                        label(id: 'lblQBundle', text: 'Exercise',
                            constraints: gbc(gridx: 0, gridy: 2, weightx: 0.1, weighty: 0))
                        comboBox(id: 'cbQBundle',
                            constraints: gbc(gridx: 1, gridy: 2, gridwidth: 4, weightx: 1, weighty: 0, 
                                fill: HORIZONTAL, insets: [0, 5, 5, 0]))
                        
                        label(text:"Label (e.g. 4.(a)(i))",
                            constraints: gbc(gridx: 0, gridy: 3, weightx: 0.1, weighty: 0))
                        comboBox(id: 'cbLabelQsn', items: lib.getQsn(),
                            constraints: gbc(gridx: 1, gridy: 3,
                                fill: HORIZONTAL, insets: [0, 5, 5, 0]))
                        comboBox(id: 'cbLabelPart', items: lib.getPart(),
                            constraints: gbc(gridx: 2, gridy: 3,
                                fill: HORIZONTAL, insets: [0, 5, 5, 0]))
                        comboBox(id: 'cbLabelSubpart', items: lib.getSubpart(),
                            constraints: gbc(gridx: 3, gridy: 3,
                                fill: HORIZONTAL, insets: [0, 5, 5, 0]))
                        button(id: 'btnAddBundle', text: 'Add', actionPerformed: addBundle,
                            constraints: gbc(gridx: 4, gridy: 3, weightx: 0.50, weighty: 0))
                        
                        scrollPane(id: 'spBundles',
                            constraints: gbc(gridx: 0, gridy: 4, gridwidth: 5, weightx: 1, weighty: 1, 
                                fill: BOTH, insets: [5, 0, 5, 0])) {
                            textArea(id: 'taBundles', focusable: false)
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
                        constraints: gbc(gridx: 0, gridy: 4, weightx: 1, weighty: 0, fill: HORIZONTAL)) {
    
                        button(id: 'btnPreview', text: 'Preview', actionPerformed: showPreview)
                        button(id: 'btnTag', text: 'Add Tags', enabled: false, actionPerformed: tag)
                    }
                }
            }
            pack: true
            
            sb.cbGradeLevel.actionPerformed = popQSource
            sb.cbSubject.actionPerformed = popQSource
            sb.cbSource.actionPerformed = popQSource
            
            sb.cbQSource.actionPerformed = popQBatch
            sb.cbQBatch.actionPerformed = popQBundle
            
            sb.cbSource.selectedIndex = 0
            
            AutoCompleteSupport acs1 = AutoCompleteSupport.install(sb.cbTopik, GlazedLists.eventListOf(lib.getConcepts()))
            acs1.setFilterMode(TextMatcherEditor.CONTAINS)
            acs1.setSelectsTextOnFocusGain(true)
            acs1.setHidesPopupOnFocusLost(true)
        }
    }
    
    def addBundle = {
        def lblBundle = "${sb.cbQBatch.selectedItem.tag}-${sb.cbQSource.selectedItem.tag}-${sb.cbQBundle.selectedItem.tag}"
        def lblQsn = "${sb.cbLabelQsn.selectedItem}."
        def lblPart = sb.cbLabelPart.selectedItem.empty ? "" : "(${sb.cbLabelPart.selectedItem})"
        def lblSubpart = sb.cbLabelSubpart.selectedItem.empty ? "" : "(${sb.cbLabelSubpart.selectedItem})"
        def text = "${lblBundle}|${lblQsn}${lblPart}${lblSubpart}"
        
        def target = sb.taBundles
        def targetText = target.getText()
        if (!targetText.contains(text)) {
            if (targetText.length() != 0)
                target.append(delim)
            target.append(text)
        }
        target.setFocusable(true)
        sb.btnTag.enabled = true
    }
    
    def popQBundle = { ActionEvent ae ->
        sb.cbQBundle.model = catalog.getQBundles(sb.cbQBatch.selectedItem.tag)
        if (sb.cbQBundle.model.size() > 0)
            sb.cbQBundle.selectedIndex = 0
    }
    
    def popQBatch = { ActionEvent ae ->
        sb.cbQBatch.model = catalog.getQBatches(sb.cbQSource.selectedItem.tag)
        if (sb.cbQBatch.model.size() > 0)
            sb.cbQBatch.selectedIndex = 0
    }
    
    def popQSource = { ActionEvent ae ->
        if (!ae.getActionCommand().equals('comboBoxChanged'))
            return            
        if (sb.cbGradeLevel.getSelectedItem() == null ||
            sb.cbSubject.getSelectedItem() == null ||
            sb.cbSource.getSelectedItem() == null)
            return
        sb.cbQSource.model = catalog.getQSources(sb.cbGradeLevel.getSelectedItem().tag,
            sb.cbSubject.getSelectedItem().tag, sb.cbSource.getSelectedItem().tag)
        if (sb.cbQSource.model.size() > 0)
            sb.cbQSource.selectedIndex = 0
        
        if (sb.cbSource.getSelectedItem().label.contains("Book")) {
            sb.lblQSource.text = "Book"
            sb.lblQBatch.text = "Chapter"
            sb.lblQBundle.text = "Exercise"
        } else {
            sb.lblQSource.text = "Exam (Year)"
            sb.lblQBatch.text = "Set"
            sb.lblQBundle.text = "Section"
        }
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
        q.bundles = sb.taBundles.getText().split(delim)
        q.concepts = sb.taTopiks.getText().split(delim)
        println q.toJSONString(true)
        try {
            sb.btnTag.setEnabled(false)
            network.updateTags(q)
            sb.btnTag.setEnabled(true)
            sb.optionPane().showMessageDialog(null, "Tagged!", "Result", JOptionPane.INFORMATION_MESSAGE)
        } catch (Exception e) {
            println e.getMessage()
        }
    }
    
    def showPreview = {
        try {
            (new Preview(sb)).show(q)            
        } catch (Exception e) {
            println e.getClass().getName()
        }
    }

}
