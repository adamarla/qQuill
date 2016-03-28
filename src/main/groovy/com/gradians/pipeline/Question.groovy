package com.gradians.pipeline

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

class Question {
    
    Statement statement
    Step[] steps
    Choices choices
    
    //question
    Path qpath
    String uid
    
    //tags
    String bundle
    String[] concepts
    
    static final String SEP = "/", XML_FILE = "question.xml"
    
    def Question(Path qpath) {
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
    
    def File getFile() {
        return qpath.resolve(XML_FILE)
    }
    
    def reload() {
        parse(qpath.resolve(XML_FILE))
    }
    
    private def parse(Path xmlPath) {
        def xml = new XmlSlurper().parse(xmlPath.toFile())
        statement = new Statement()
        statement.tex = xml.statement.tex.toString()
        if (!xml.statement.image.isEmpty())
            statement.image = xml.statement.image.toString()
            
        steps = new Step[6]
        xml.step.eachWithIndex { it, i ->
            Step step = new Step()
            if (it.@swipe.isEmpty()) {
                step.noswipe = false
            } else if (it.@swipe.equals("false")) {
                step.noswipe = true
            }
            
            if (it.context.@image.equals("true"))
                step.imageContext = it.context.toString()
            else
                step.context = it.context.toString()            
            
            ["tex", "image"].each { content ->
                it."${content}".each { option ->
                    if (option.@correct.equals("true")) {
                        step."${content}Correct" = option.toString()
                    } else {
                        step."${content}Incorrect" = option.toString()
                    }
                }
            }
            
            if (it.reason.@image.equals("true"))
                step.imageReason = it.reason.toString()
            else
                step.reason = it.reason.toString()
                        
            steps[i] = step
        }
        
        if (!xml.choices.tex.isEmpty()) {
            choices = new Choices()
            choices.texs = xml.choices.tex.collect {
                it.toString()
            }
            xml.choices.tex.eachWithIndex { it, i ->
                if (it.@correct == true) {
                    choices.correct = i
                }
            }    
        }
    }

    private def isValidXML(Path xmlPath, Path catalog) {
        def xmlStream = new StreamSource(xmlPath.toFile())
        def xsdStream = new StreamSource(catalog.resolve("question.xsd").toFile())
        SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
            .newSchema(xsdStream)
            .newValidator()
            .validate(xmlStream)
        true        
    }
    
    private def getLabel() {
        DirectoryStream<Path> stream = Files.newDirectoryStream(qpath, "*.lbl")
        for (Path path : stream) {
            bundle = path.getFileName().toString().split("\\.")[0]
        }
    }
}

class Statement {
    String tex = "", image = ""
}

class Step {
    boolean noswipe
    String imageCorrect = "", imageIncorrect = "", imageContext = "", imageReason = ""
    String texCorrect = "", texIncorrect = "", context = "", reason = ""
}

class Choices {
    String[] texs = new String[4]
    int correct
}
