package com.gradians.pipeline.data

import java.io.FileInputStream
import java.nio.file.DirectoryStream
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.util.Map

import com.gradians.pipeline.edit.Component
import com.gradians.pipeline.edit.IEditable
import com.gradians.pipeline.edit.Panel

import groovy.util.slurpersupport.GPathResult

import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING


class Skill extends Asset implements IEditable {
    
    @Override
    Panel[] getPanels() {
        Panel[] pnls = new Panel[1]
        pnls[0] = new Panel("Skill")
        pnls[0].addComponent(new Component("Statement", texStatement, 12, true))
        pnls[0].addComponent(new Component("Study Note", texReason, 12, true))        
        pnls
    }

    @Override
    void updateModel(Panel[] panel) {
        // TODO Auto-generated method stub
        
    }

    @Override
    String toXMLString() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    Map<String, String> toRender() {
        // TODO Auto-generated method stub
        return null;
    }
    
    @Override
    protected void parse(Path xmlPath) {
        def xml = new XmlSlurper().parse(xmlPath.toFile())
        
        texStatement = xml.render.tex.toString()
        texReason = xml.reason.tex.toString()
        
    }
    
    String texStatement = "", texReason = ""
    

}

