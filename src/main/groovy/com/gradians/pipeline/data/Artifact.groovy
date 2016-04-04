package com.gradians.pipeline.data

import java.io.File;
import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.Path;

import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory
import javax.xml.XMLConstants

abstract class Artifact {

    //question
    Path qpath
    String uid
    
    //tags
    String bundle
    String[] concepts
    
    static String SEP = "/", XML_FILE, XSD_FILE
    
    Artifact(Path qpath, String xml, String xsd) {
        Artifact.XML_FILE = xml
        Artifact.XSD_FILE = xsd
                
        this.qpath = qpath
        Path vault = qpath.getParent().getParent().getParent()
        assert vault.getFileName().toString().equals("vault")
        
        Path bank = vault.getParent()
        Path catalog = bank.resolve("common").resolve("catalog")
        
        def xmlPath = qpath.resolve(XML_FILE)
        if (Files.notExists(xmlPath)) {
            xmlPath = catalog.resolve(XML_FILE)
        } else {
            assert isValidXML(xmlPath, catalog)
        }
        parse(xmlPath)
        getLabel()
        
        def tokens = qpath.toString().split(SEP)
        uid = "${tokens[tokens.length-3]}${SEP}${tokens[tokens.length-2]}${SEP}${tokens[tokens.length-1]}"
    }
    
    boolean isValidXML(Path xmlPath, Path catalog) {
        def xmlStream = new StreamSource(xmlPath.toFile())
        def xsdStream = new StreamSource(catalog.resolve(XSD_FILE).toFile())
        SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
            .newSchema(xsdStream)
            .newValidator()
            .validate(xmlStream)
        true
    }
    
    
    protected getLabel() {
        DirectoryStream<Path> stream = Files.newDirectoryStream(qpath, "*.lbl")
        for (Path path : stream) {
            bundle = path.getFileName().toString().split("\\.")[0]
        }
    }

    protected File getFile() {
        return qpath.resolve(XML_FILE).toFile()
    }
    
    protected void reload() {
        parse(qpath.resolve(XML_FILE))
    }
    
    abstract parse(Path xmlPath) 

}
