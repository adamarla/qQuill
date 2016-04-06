package com.gradians.pipeline.data

import java.io.FileInputStream
import java.nio.file.DirectoryStream
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.util.Map;

import com.gradians.pipeline.edit.Component;
import com.gradians.pipeline.edit.IEditable;
import com.gradians.pipeline.edit.Panel;

import groovy.util.slurpersupport.GPathResult

import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING

class Snippet extends Artifact implements IEditable {
    
    boolean correct = false
    String imageStatement = "", texStatement = "", texReason = ""
    
    static String XML_FILE = "snippet.xml", XSD_FILE = "snippet.xsd"
    
    def File getFile() {
        return qpath.resolve(XML_FILE)
    }
    
    @Override
    public Panel[] getPanels() {
        Panel[] pnls = new Panel[1]
        pnls[0] = new Panel("Snippet")
        pnls[0].addComponent(new Component("Statement", texStatement, 12, true))
        pnls[0].addComponent(new Component("Rason", texReason, 12, true))        
        pnls
    }

    @Override
    public void updateModel(Panel[] panel) {
        // TODO Auto-generated method stub
        
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

    @Override
    public String toXMLString() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Map<String, String> toRender() {
        // TODO Auto-generated method stub
        return null;
    }
    
}

