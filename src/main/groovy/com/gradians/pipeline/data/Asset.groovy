package com.gradians.pipeline.data

import com.gradians.pipeline.edit.EditGroup
import com.gradians.pipeline.edit.IEditable
import com.gradians.pipeline.util.Config

import groovy.xml.XmlUtil

import java.io.InputStream;
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import javax.xml.transform.OutputKeys
import javax.xml.transform.Transformer
import javax.xml.transform.TransformerFactory
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory
import javax.xml.XMLConstants


abstract class Asset implements IEditable, Comparable {
    
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
        Config config = Config.getInstance()
        Path vault = Paths.get(config.getBankPath()).resolve("vault")
        asset.qpath = vault.resolve(asset.path)
        asset.load()
        asset
    }
    
    void create() {
        if (Files.notExists(qpath)) {
            Config config = Config.getInstance()
            Path vault = Paths.get(config.getBankPath()).resolve("vault")
            Files.createDirectories(qpath)
            
            Path makefile = qpath.resolve("Makefile")
            Path target = vault.resolve("bin").resolve("compile.mk")
            Files.createSymbolicLink(makefile, qpath.relativize(target))
        }
        parse(Asset.class.getClassLoader().getResourceAsStream(REF_FILE))
        save()
    }
    
    Asset load() {
        def xmlPath = qpath.resolve(SRC_FILE)
        if (Files.exists(xmlPath)) {
            if (isValidXML(xmlPath)) {
                def xmlStream = Files.newInputStream(xmlPath)
                parse(xmlStream)
            } else {
                throw new Exception("${xmlPath.toString()} contains invalid XML")
            }
        }
    }
    
    boolean isLoaded() {
        xml != null
    }

    @Override
    abstract EditGroup[] getEditGroups()

    @Override
    abstract void updateModel(EditGroup... panel)

    @Override
    void save() {
        serialize(xml, Files.newOutputStream(qpath.resolve(SRC_FILE)))
    }
    
    @Override
    Path getDirPath() {
        qpath.resolve(SRC_FILE).getParent()
    }
    
    File getFile() {
        qpath.resolve(SRC_FILE).toFile()
    }    
    
    @Override
    int compareTo(Object o) {
        ((Asset)o).id - id
    }
    
    @Override
    public String toString() {
        "${assetClass}: ${id}-${path}-${authorId}-${chapterId}"
    }

    abstract Map<String, String> toRender()
    
    @Override
    boolean equals(Object obj) {
        Asset a = (Asset)obj
        id == a.id && assetClass == a.assetClass
    }

    protected Asset parse(InputStream xmlStream) {
        xml = new XmlSlurper(false, false).parse(xmlStream)
        if (!xml.@chapterId.isEmpty())
            chapterId = xml.@chapterId.toInteger()
        this
    }
    
    protected void serialize(def xmlNode, OutputStream ostream) {
        TransformerFactory factory = TransformerFactory.newInstance()
        def smb = new groovy.xml.StreamingMarkupBuilder()
        smb.encoding = "utf-8"
        def xmlString = "<?xml version='1.0' encoding='utf-8'?>\n" + smb.bindNode(xmlNode).toString()
        def source = new StreamSource(new StringReader(xmlString))
        def target = new StreamResult(ostream)
                
        try {
            Transformer transformer = factory.newTransformer()
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
            transformer.setOutputProperty(OutputKeys.INDENT, "yes")
            transformer.setOutputProperty(OutputKeys.METHOD, "xml")
            transformer.setOutputProperty(OutputKeys.MEDIA_TYPE, "text/xml")
            
            transformer.transform(source, target)
        } catch (Exception e) {
            throw new GroovyRuntimeException(e.getMessage())
        }
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
        
    int id
    String path
    int authorId
    int chapterId
    AssetClass assetClass
    
    protected def xml    
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
