package com.gradians.pipeline.data

import com.gradians.pipeline.Config
import com.gradians.pipeline.edit.IEditable
import com.gradians.pipeline.edit.Panel

import java.io.File
import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.Path

import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory
import javax.xml.XMLConstants


abstract class Asset implements Comparable {
    
    static Asset getInstance(def map, AssetClass assetClass) {
        Asset newAsset
        switch (assetClass) {
            case AssetClass.Question:
                newAsset = new Question(map)
                newAsset.SRC_FILE = "question.xml"
                newAsset.SCHEMA_FILE = "question.xsd"
                newAsset.REF_FILE = "question.xml"
                newAsset.assetClass = AssetClass.Snippet
                break
            case AssetClass.Skill:
                newAsset = new Skill(map)
                newAsset.SCHEMA_FILE = "skill.xsd"
                newAsset.REF_FILE = "skill.xml"
                newAsset.assetClass = AssetClass.Skill
                break
            default:
                newAsset = new Snippet(map)            
                newAsset.SCHEMA_FILE = "snippet.xsd"
                newAsset.REF_FILE = "snippet.xml"
                newAsset.assetClass = AssetClass.Snippet
        }
        newAsset
    }

    Asset load() {
        def bankPathString = (new Config()).get("bank_path")
        Path bank = new File(bankPathString).toPath()
        Path vault = bank.resolve("vault")
        assert vault.getFileName().toString().equals("vault")
        
        Path catalog = bank.resolve("common").resolve("catalog")
        qpath = vault.resolve(path)
        def xmlPath = qpath.resolve(this.SRC_FILE)
        
        // If the file has never been saved before, 
        // a skeleton reference file with blank fields is pulled
        // from the catalog
        if (Files.notExists(xmlPath)) {
            xmlPath = catalog.resolve(REF_FILE)
        } else {
            assert isValidXML(xmlPath, catalog)
        }
        parse(xmlPath)
        this
    }
    
    File getFile() {
        return qpath.resolve(SRC_FILE).toFile()
    }    
    
    @Override
    int compareTo(Object o) {
        ((Asset)o).id - id
    }
    
    @Override
    public String toString() {
        "${assetClass}: ${id}-${path}-${authorId}-${chapterId}"
    }

    abstract String toXMLString()
    
    abstract Map<String, String> toRender()
    
    protected boolean isValidXML(Path xmlPath, Path catalog) {
        def xmlStream = new StreamSource(xmlPath.toFile())
        def xsdStream = new StreamSource(catalog.resolve(SCHEMA_FILE).toFile())
        SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
            .newSchema(xsdStream)
            .newValidator()
            .validate(xmlStream)
        true
    }
        
    protected abstract void parse(Path xmlPath)
    
    int id
    String path
    int authorId
    int chapterId
    AssetClass assetClass
    
    Path qpath
    
    String REF_FILE, SCHEMA_FILE, LAYOUT_FILE = "layout.xml", SRC_FILE = "source.xml"
    
}

enum AssetClass {
    
    Skill,
    Snippet,
    Question
        
}

