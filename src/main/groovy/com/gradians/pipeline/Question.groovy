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
    List<Step> steps
    Choices choices
    
    //question
    Path qpath
    String uid
    
    //tags
    String bundle
    String[] concepts
    
    final String SEP = "/", XML_FILE = "question.xml"
    
    def Question(Path qpath) {
        this.qpath = qpath
        Path vault = qpath.getParent().getParent().getParent()        
        assert vault.getFileName().toString().equals("vault")
        
        Path bank = vault.getParent()
        Path catalog = bank.resolve("common").resolve("catalog")
        
        def xmlPath = qpath.resolve(XML_FILE)
        if (Files.notExists(xmlPath)) {
            parse(catalog.resolve(XML_FILE))
        } else {
            assert isValidXML(xmlPath, catalog)
            parse(xmlPath)
            getLabel()
        }
        
        def tokens = qpath.toString().split(SEP)
        uid = "${tokens[tokens.length-3]}${SEP}${tokens[tokens.length-2]}${SEP}${tokens[tokens.length-1]}"        
    }
    
    private def parse(Path xmlPath) {
        def xml = new XmlSlurper().parse(xmlPath.toFile())
        statement = new Statement()
        statement.tex = xml.statement.tex.toString()
        if (!xml.statement.image.isEmpty())
            statement.image = xml.statement.image.toString()
            
        steps = new ArrayList<Step>()
        def step
        xml.step.eachWithIndex { it, i ->
            step = new Step()
            if (it.@swipe.isEmpty()) {
                step.noswipe = false
            } else if (it.@swipe.equals("false")) {
                step.noswipe = true
            }            
            step.context = it.context.toString()
            ["tex", "image"].each { content ->
                it."${content}".each { option ->
                    if (option.@correct.equals("true")) {
                        step."${content}Right" = option.toString()
                    } else {
                        step."${content}Wrong" = option.toString()
                    }    
                }
            }
            step.reason = it.reason.toString()
            steps.add step
        }
        
        if (!xml.choices.tex.isEmpty()) {
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
    String context = "", reason = ""
    String texRight = "", texWrong = "", imageRight = "", imageWrong = ""
}

class Choices {
    String[] texs = new String[4]
    int correct
}
