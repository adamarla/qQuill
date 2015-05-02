package com.gradians.pipeline

import java.nio.file.Path
import javax.swing.AbstractListModel
import javax.swing.ComboBoxModel

class Catalog {
    
    def catalog
    def qsource
    Path bank
    final String catalogFile = "catalog.xml"
    
    public Catalog(Path bank) {
        this.bank = bank
        def xmlFile = bank.resolve(catalogFile).toFile()
        catalog = new XmlSlurper().parse(xmlFile)
    }
    
    def getGradeLevels() {
        getTagLabelSet(catalog.gradeLevels.gradeLevel)
    }
    
    def getSubjects() {
        getTagLabelSet(catalog.subjects.subject)
    }
    
    def getPublishers() {
        getTagLabelSet(catalog.publishers.publisher)
    }
    
    def getQSources(String gradeLevel, subject, publisher) {
        getTagLabelSet(catalog.qsources.'*'.findAll { node ->
            node.@gradeLevel == gradeLevel && node.@subject == subject && node.@publisher == publisher
        })
    }
    
    def getQBatches(String tag) {
        def qsourceEntry = catalog.qsources.'*'.find { node ->
            node.@tag == tag
        }
        def xmlFile = bank.resolve(qsourceEntry.text()).toFile()
        qsource = new XmlSlurper().parse(xmlFile)
        getTagLabelSet(qsource.qbatch)
    }
    
    def getQBundles(String tag) {
        def qbatchEntry = qsource.'*'.find { node ->
            node.@tag == tag
        }
        getTagLabelSet(qbatchEntry.qbundle)
    }
    
    private def getTagLabelSet = {
        new TagLabelSet(it*.@tag.collect{ it.toString() }, it*.@label.collect{ it.toString() })
    }

}

class TagLabelSet extends AbstractListModel implements ComboBoxModel<TagLabel> {
    
    String[] tags, labels
    TagLabel selected = null
    
    def TagLabelSet(List<String> tags, List<String> labels) {
        this.tags = tags.toArray(new String[tags.size()])
        this.labels = labels.toArray(new String[labels.size()])
    }
    
    def int getSize() {
        tags.length
    }
    
    def TagLabel getElementAt(int i) {
        new TagLabel(tag: tags[i], label: labels[i])
    }
    
    def getSelectedItem() {
        return selected
    }
    
    def void setSelectedItem(Object o) {
        selected = (TagLabel)o
    }
    
}

class TagLabel {
    String tag, label
    
    def String toString() {
        label
    }    
    
    def boolean equals(Object o) {
        if (o == null)
            return false
        else
            tag.equals(((TagLabel)o).tag)
    }
}
