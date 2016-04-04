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


class Skill extends Artifact {
    
    String texStatement = "", texStudy = ""
    
    static String XML_FILE = "skill.xml", XSD_FILE = "skill.xsd"
    
    def File getFile() {
        return qpath.resolve(XML_FILE)
    }
    
    def parse(Path xmlPath) {
        def xml = new XmlSlurper().parse(xmlPath.toFile())
        
        texStatement = xml.tex.toString()
        texStudy = xml.study.tex.toString()
        
    }
    
}
