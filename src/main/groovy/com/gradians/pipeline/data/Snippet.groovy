package com.gradians.pipeline.data

import java.io.FileInputStream
import java.nio.file.DirectoryStream
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.PathMatcher

import groovy.util.slurpersupport.GPathResult

import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING

class Snippet extends Artifact {
    
    boolean correct = false
    String imageStatement = "", texStatement = "", texReason = ""
    
    static String XML_FILE = "snippet.xml", XSD_FILE = "snippet.xsd"
    
    def File getFile() {
        return qpath.resolve(XML_FILE)
    }
    
    def parse(Path xmlPath) {
        def xml = new XmlSlurper().parse(xmlPath.toFile())
        
        if (!xml.image.isEmpty()) {
            imageStatement = xml.image.toString()
            correct = !xml.image.@correct.equals("true")
        } else {
            texStatement = xml.tex.toString()
            correct = !xml.tex.@correct.equals("true")
        }
        
        texReason = xml.reason.tex.toString()
    }
    
}

