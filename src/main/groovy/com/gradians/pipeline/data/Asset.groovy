package com.gradians.pipeline.data

import com.gradians.pipeline.Config

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory
import javax.xml.XMLConstants


abstract class Asset implements Comparable {
    
    static Asset getInstance(def map) {
        Asset asset
        switch (AssetClass.valueOf(map.assetClass)) {
            case AssetClass.Question:
                asset = new Question(map)
                asset.SCHEMA_FILE = "question.xsd"
                asset.REF_FILE = "question.xml"
                break
            case AssetClass.Skill:
                asset = new Skill(map)
                asset.SCHEMA_FILE = "skill.xsd"
                asset.REF_FILE = "skill.xml"
                break
            default:
                asset = new Snippet(map)
                asset.SCHEMA_FILE = "snippet.xsd"
                asset.REF_FILE = "snippet.xml"
        }
        asset
    }
    
    void create() {
        def bankPathString = (new Config()).get("bank_path")
        Path bank = Paths.get(bankPathString)
        Path vault = bank.resolve("vault")
        Path assetDir = vault.resolve(path)
        
        if (Files.notExists(assetDir)) {
            Files.createDirectories(assetDir)
            Path makefile = assetDir.resolve("Makefile")
            Path target = vault.resolve("bin").resolve("compile.mk")
            Files.createSymbolicLink(makefile, assetDir.relativize(target))
        }
    }

    Asset load() {
        def bankPathString = (new Config()).get("bank_path")
        Path bank = Paths.get(bankPathString)
        Path vault = bank.resolve("vault")
        assert vault.getFileName().toString().equals("vault")
        
        qpath = vault.resolve(path)
        def xmlPath = qpath.resolve(SRC_FILE)        
        // If the file has never been saved before, 
        // a skeleton reference file with blank fields is pulled
        // from the catalog
        def xmlStream
        if (Files.notExists(xmlPath)) {
             xmlStream = Asset.class.getClassLoader().getResourceAsStream(REF_FILE)
        } else {
            assert isValidXML(xmlPath)
            xmlStream = Files.newInputStream(xmlPath)
        }
        parse(xmlStream)
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
    
    @Override
    boolean equals(Object obj) {
        Asset a = (Asset)obj
        return id == a.id && assetClass == a.assetClass
    }

    protected boolean isValidXML(Path xmlPath) {
        def schema = Asset.class.getClassLoader().getResourceAsStream(SCHEMA_FILE)
        def xml = new StreamSource(Files.newInputStream(xmlPath))
        def xsd = new StreamSource(schema)
        SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
            .newSchema(xsd)
            .newValidator()
            .validate(xml)
        true
    }
        
    protected abstract void parse(InputStream xmlStream)
    
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

enum AssetState {
    New,
    Saved,
    Ready
}
