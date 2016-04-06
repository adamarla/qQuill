package com.gradians.pipeline.tag

import java.nio.file.Path
import javax.swing.AbstractListModel
import javax.swing.ComboBoxModel

class Catalog {
    
    def catalog
    Path catalogPath
    final String catalogFile = "main.xml"
    
    public Catalog(Path catalogPath) {
        this.catalogPath = catalogPath
        File xmlFile = catalogPath.resolve(catalogFile).toFile()
        assert xmlFile.exists()
        catalog = new XmlSlurper().parse(xmlFile)
    }
    
    def getGrades() {
        getCatalogItemSet(catalog.grades.grade)
    }

    def getSubjects() {
        getCatalogItemSet(catalog.subjects.subject)
    }

    def getPublishers() {
        getCatalogItemSet(catalog.publishers.publisher)
    }

    def getBooks(String grade, String subject, String publisher) {
        getCatalogItemSet(catalog.books.'*'.findAll { node ->
            node.@grade == grade && node.@subject == subject && node.@publisher == publisher
        })
    }

    def getChapters(String book) {
        def fileBookCatalog = catalog.books.'*'.find { node ->
            node.@tag == book
        }.text()
        def xmlFile = catalogPath.resolve(fileBookCatalog).toFile()
        def bookCatalog = new XmlSlurper().parse(xmlFile)
        getCatalogItemSet(bookCatalog.chapter)
    }

    def getExercises(String book, String chapter) {
        def fileBookCatalog = catalog.books.'*'.find { node ->
            node.@tag == book
        }.text()
        def xmlFile = catalogPath.resolve(fileBookCatalog).toFile()
        def bookCatalog = new XmlSlurper().parse(xmlFile)   
        def bookChapter = bookCatalog.'*'.find { node ->
            node.@tag == chapter
        }
        getCatalogItemSet(bookChapter.exercise)
    }

    private def getCatalogItemSet = {
        new CatalogItemSet(it*.@tag.collect{ it.toString() }, it*.@label.collect{ it.toString() })
    }

}

class CatalogItemSet extends AbstractListModel implements ComboBoxModel<CatalogItem> {
    
    String[] tags, labels
    CatalogItem selected = null
    
    def CatalogItemSet(List<String> tags, List<String> labels) {
        this.tags = tags.toArray(new String[tags.size()])
        this.labels = labels.toArray(new String[labels.size()])
    }
    
    def int getSize() {
        tags.length
    }
    
    def CatalogItem getElementAt(int i) {
        new CatalogItem(tag: tags[i], label: labels[i])
    }
    
    def getSelectedItem() {
        return selected
    }
    
    def void setSelectedItem(Object o) {
        selected = (CatalogItem)o
    }
    
}

class CatalogItem {
    
    String tag, label
    
    def String toString() {
        label
    }    
    
    def boolean equals(Object o) {
        if (o == null)
            return false
        else
            tag.equals(((CatalogItem)o).tag)
    }
}
