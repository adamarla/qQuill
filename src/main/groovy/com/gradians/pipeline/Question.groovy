package com.gradians.pipeline

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
    File file
    String uid
    GPathResult xml
    
    //tags
    String[] bundles
    String[] concepts
    
    final String delim = ","
    
    def Question(String path) {
        file = new File("${path}/question.xml")
        assert file.exists()
        assert isValidXML()
        
        xml = new XmlSlurper().parse(file)
        def tokens = file.getAbsolutePath().split("/")
        uid = tokens[tokens.length-4] + "/" + tokens[tokens.length-3] + "/"  + tokens[tokens.length-2]
    }
    
    def getStatement() {
        TeXFormula tex = new TeXFormula(xml.statement.tex.toString())
        tex.createTeXIcon(TeXConstants.STYLE_DISPLAY, 15, TeXFormula.SANSSERIF)
    }
    
    def getChoices() {
        TeXIcon[] icons = new TeXIcon[4]
        xml.choices.tex.eachWithIndex { it, i ->
            TeXFormula tex = new TeXFormula(it.toString())
            icons[i] = tex.createTeXIcon(TeXConstants.STYLE_DISPLAY, 15, TeXFormula.SANSSERIF) 
        }
        return icons
    }
    
    def getSteps() {
        def steps = []
        xml.step.each {
            def tex = it.context.toString()
            tex += "\\\\ "
            tex += it.reason.toString()
            tex += "\\\\ "
            it.tex.each { iit ->
                tex += iit.toString()
                tex += "\\\\ "
            }
            steps << (new TeXFormula(tex)).createTeXIcon(TeXConstants.STYLE_DISPLAY, 15, TeXFormula.SANSSERIF)            
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

    private isValidXML() {
        println file.getAbsolutePath()
        InputStream is = Question.class.getResourceAsStream("/question.xsd")
        assert is != null
        def xsdStream = new StreamSource(is)
        assert xsdStream != null
        def xmlStream = new StreamSource(file)        
        SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
            .newSchema(xsdStream)
            .newValidator()
            .validate(xmlStream)
        true        
    }
}
