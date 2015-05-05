package com.gradians.pipeline

import java.io.FileInputStream;
import java.nio.file.Path

import groovy.json.JsonBuilder
import groovy.util.slurpersupport.GPathResult

import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory

import org.scilab.forge.jlatexmath.TeXConstants
import org.scilab.forge.jlatexmath.TeXFormula
import org.scilab.forge.jlatexmath.TeXIcon

class Question {
    
    //question info
    String uid
    GPathResult xml
    
    //tags
    String[] bundles
    String[] concepts
    
    final String delim = ",", sep = "/"
    
    def Question(Path qpath, Path bank) {
        assert isValidXML(qpath, bank)
        xml = new XmlSlurper().parse(qpath.resolve("question.xml").toFile())
        def tokens = qpath.toString().split(sep)
        uid = tokens[tokens.length-3] + sep + tokens[tokens.length-2] + sep  + tokens[tokens.length-1]
        println "$uid"        
    }
    
    def getStatement() {
        TeXFormula tex = new TeXFormula(xml.statement.tex.toString().replace("newline", "\\"))
        tex.createTeXIcon(TeXConstants.STYLE_DISPLAY, 15, TeXFormula.SANSSERIF)
    }
    
    def getChoices() {
        TeXIcon[] icons = new TeXIcon[4]
        xml.choices.tex.eachWithIndex { it, i ->
            TeXFormula tex = new TeXFormula(it.toString().replace("newline", "\\"))
            icons[i] = tex.createTeXIcon(TeXConstants.STYLE_DISPLAY, 15, TeXFormula.SANSSERIF) 
        }
        return icons
    }
    
    def getSteps() {
        def steps = []
        xml.step.eachWithIndex { it, i ->
            def latex = new StringBuilder()
            latex.append("\\text{Step ${i+1}: } ").append(it.context.toString().replace("newline", "\\"))
            latex.append(" \\\\ ")
            it.tex.each { iit ->
                latex.append("\\text{Option: }").append(iit.toString().replace("newline", "\\"))
                latex.append(" \\\\ ")
            }
            latex.append("\\text{Reason: }\\\\")
            latex.append(it.reason.toString().replace("newline", "\\"))
            
            def formula = new TeXFormula(latex.toString()) 
            steps << formula.createTeXIcon(TeXConstants.STYLE_DISPLAY, 15, TeXFormula.SANSSERIF)
        }
        return steps
    }
    
    def toJSONString(boolean pretty = false) {
        JsonBuilder builder = new JsonBuilder()
        builder.question {
            'uid' uid
            'bundles' bundles
            'concepts' concepts
        }
        if (pretty)
            builder.toString()
        else
            builder.toPrettyString()
    }

    private def isValidXML(Path qpath, Path bank) {
        def xsdStream = new StreamSource(bank.resolve("question.xsd").toFile())
        def xmlStream = new StreamSource(qpath.resolve("question.xml").toFile())
        SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
            .newSchema(xsdStream)
            .newValidator()
            .validate(xmlStream)
        true        
    }
}
