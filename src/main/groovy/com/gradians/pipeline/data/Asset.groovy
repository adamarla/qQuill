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
                asset.REF_FILE = "question.xml"
                break
            case AssetClass.Skill:
                asset = new Skill(map)
                asset.REF_FILE = "skill.xml"
                break
            default:
                asset = new Snippet(map)
                asset.REF_FILE = "snippet.xml"
        }
        asset
    }
    
    void create() {
        Config config = Config.getInstance()        
        Path vault = Paths.get(config.get("bank_path")).resolve("vault")
        qpath = vault.resolve(path)
        
        if (Files.notExists(qpath)) {
            Files.createDirectories(qpath)
            Path makefile = qpath.resolve("Makefile")
            Path target = vault.resolve("bin").resolve("compile.mk")
            Files.createSymbolicLink(makefile, qpath.relativize(target))
        }
    }

    Asset load() {
        Config config = Config.getInstance()
        Path vault = Paths.get(config.get("bank_path")).resolve("vault")
        assert vault.getFileName().toString().equals("vault")
        
        qpath = vault.resolve(path)
        def xmlPath = qpath.resolve(SRC_FILE)
        def xmlStream
        if (Files.notExists(xmlPath)) {
             xmlStream = Asset.class.getClassLoader().getResourceAsStream(REF_FILE)
        } else {
            assert isValidXML(xmlPath)
            xmlStream = Files.newInputStream(xmlPath)
            assert isValidXML(xmlPath)
        }
        parse(xmlStream)
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

    private boolean isValidXML(Path xmlPath) {
        def schema = Asset.class.getClassLoader().getResourceAsStream(SCHEMA_FILE)
        def xml = new StreamSource(Files.newInputStream(xmlPath))
        def xsd = new StreamSource(schema)
        SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
            .newSchema(xsd)
            .newValidator()
            .validate(xml)
        true
    }
        
    protected abstract Asset parse(InputStream xmlStream)
    
    int id
    String path
    int authorId
    int chapterId
    AssetClass assetClass
    
    Path qpath
    
    String REF_FILE
    final String SCHEMA_FILE = "assets.xsd", LAYOUT_FILE = "layout.xml", SRC_FILE = "source.xml"
    
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
