package com.gradians.pipeline

import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Path

import groovy.util.slurpersupport.GPathResult

import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING

class Question {
    
    Statement statement
    List<Step> steps
    Choices choices
    
    //question
    Path qpath
    String uid
    
    //tags
    String[] bundles
    String[] concepts
    
    final String SEP = "/", XML_FILE = "question.xml"
    
    def Question(Path qpath, Path bank) {
        this.qpath = qpath
        def tokens = qpath.toString().split(SEP)
        uid = "${tokens[tokens.length-3]}${SEP}${tokens[tokens.length-2]}${SEP}${tokens[tokens.length-1]}"
        def xmlPath = qpath.resolve(XML_FILE)
        if (!Files.exists(xmlPath)) {
            Files.copy(bank.resolve(XML_FILE), xmlPath, REPLACE_EXISTING)
        }
        assert isValidXML(xmlPath, bank)
        parse(xmlPath)
    }
    
    private def parse(Path xmlPath) {
        def xml = new XmlSlurper().parse(xmlPath.toFile())
        statement = new Statement()
        statement.tex = xml.statement.tex.toString().replace("\\newline", "")
        if (!xml.statement.image.isEmpty())
            statement.image = xml.statement.image.toString()
            
        steps = new ArrayList<Step>()
        def step
        xml.step.eachWithIndex { it, i ->
            step = new Step()
            step.context = it.context.toString().replace("\\newline", "")
            
            it.tex.each { tex ->
                if (tex.@correct.equals("true")) {
                    step.texRight = tex.toString().replace("\\newline", "")
                } else {
                    step.texWrong = tex.toString().replace("\\newline", "")
                }
            }            
            step.reason = it.reason.toString().replace("\\newline", "")
            steps.add step
        }
        
        choices = new Choices()
        choices.texs = xml.choices.tex.collect { 
            it.toString().replace("\\newline", "")
        }
        xml.choices.tex.eachWithIndex { it, i ->
            if (it.@correct == true) {
                choices.correct = i
            }
        }
    }

    private def isValidXML(Path xmlPath, Path bank) {
        def xmlStream = new StreamSource(xmlPath.toFile())
        def xsdStream = new StreamSource(bank.resolve("question.xsd").toFile())
        SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
            .newSchema(xsdStream)
            .newValidator()
            .validate(xmlStream)
        true        
    }
}

class Statement {
    String tex = "", image = ""
}

class Step {
    String context = "", reason = ""
    String texRight = "", texWrong = "", imageRight = "", imageWrong = ""
}

class Choices {
    String[] texs = new String[4]
    int correct
}
