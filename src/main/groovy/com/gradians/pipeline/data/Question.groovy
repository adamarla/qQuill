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


class Question extends Artifact implements IEditable {
    
    Statement statement
    Step[] steps
    Choices choices
    
    //tags
    String bundle
    String[] concepts
    
    def Question(Path qpath) {
        super(qpath, "question.xml", "question.xsd")
    }
    
    def parse(Path xmlPath) {
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
    
    @Override
    public String toXMLString() {
        def sw = new StringWriter()
        def xml = new groovy.xml.MarkupBuilder(sw)
        xml.mkp.xmlDeclaration(version: "1.0", encoding: "utf-8")
        xml.question(xmlns: "http://www.gradians.com") {
            statement() {
                tex(statement.tex) 
            }
            
            steps.each { stp ->
                if (stp != null && stp.context.length() != 0) {
                    def contents = {
                        if (stp.imageContext.length() > 0)
                            context(image: "true", stp.imageContext)
                        else
                            context(stp.context)
                        
                        if (stp.imageCorrect.length() > 0)
                            image(correct: "true", stp.imageCorrect)
                        else if (stp.texCorrect.length() > 0)
                            tex(correct: "true", stp.texCorrect)
                            
                        if (stp.imageIncorrect.length() > 0)
                            image(stp.imageIncorrect)
                        else if (stp.texIncorrect.length() > 0)
                            tex(stp.texIncorrect)
                            
                        if (stp.imageReason.length() > 0)
                            reason(image: "true", stp.imageReason)
                        else
                            reason(stp.reason)
                    }
                    
                    if (stp.noswipe) {
                        step(swipe: "false", contents)
                    } else {
                        step(contents)
                    }
                }
            }
            
            if (choices != null) {
                choices() {
                    choices.texs.eachWithIndex { tx, i ->
                        if (choices.correct == i) {
                            tex(correct: "true", tx)
                        } else {
                            tex(tx)
                        }
                    }
                }
            }
        }
        sw.toString()
    }
    
    @Override
    public Panel[] getPanels() {
        Panel[] panels = new Panel[8]
        panels[0] = new Panel("Problem")
        panels[0].addComponent(new Component("Statement", statement.tex, 12))
        
        steps.eachWithIndex { step, idx ->
            if (step == null)
                step = new Step()
            panels[idx+1] = new Panel("Step ${idx+1}")
            panels[idx+1].addComponent(new Component("Context", step.context, 4))
            
            def text, isTex
            ["Correct", "Incorrect"].each { it ->
                if (step."tex${it}".length() > 0) {
                    isTex = true
                    text = step."tex${it}"
                } else if (step."image${it}".length() > 0) {
                    isTex = false
                    text = step."image${it}"
                } else {
                    isTex = true
                    text = ""
                }
                panels[idx+1].addComponent(new Component(it, text, 8, isTex))    
            }
            panels[idx+1].addComponent(new Component("Reason", step.reason, 12))                
        }
        
        panels[7] = new Panel("Choices")
        if (choices != null) {
            choices.texs.eachWithIndex { tex, idx ->
                panels[7].addComponent(new Component("Choice ${idx+1}", tex, 4))
            }
        }
        panels
    }

    @Override
    public void updateModel(Panel[] panels) {
        panels.eachWithIndex { Panel panel, int idx ->
            if (idx == 0) {
                statement.tex = panel.components[0].tex
            } else if (idx > 0 && idx < 7) {
                if (panel.components[0].tex.length() > 0) {
                    steps[idx-1].context = panels[idx].components[0].tex
                    if (panel.components[1].isTex)
                        steps[idx-1].texCorrect = panel.components[1].tex
                    else
                        steps[idx-1].texCorrect = panel.components[1].image
                    if (panel.components[2].isTex)
                        steps[idx-1].texIncorrect = panels[idx].components[2].tex
                    else
                        steps[idx-1].texCorrect = panel.components[2].image
                    if (panel.components[3])
                        steps[idx-1].reason = panels[idx].components[3].tex
                    else    
                        steps[idx-1].reason = panels[idx].components[3].image
                }
            } else {
                choices = new Choices()
                if (panels[7].components[0].tex.length() > 0) {
                    panels[7].components.eachWithIndex { comp, i ->
                        choices.texs[i] = comp.tex
                    }    
                }
            }    
        }        
    }

    @Override
    public Map<String, String> toRender() {
        HashMap<String, String> svgs = new HashMap<String, String>()
         
        if (statement.tex.length() > 0) {
            svgs.put("STMT_0.svg", statement.tex)
            svgs.put("PREVIEW.svg", statement.tex)
        }
            
        for (int idx = 0; idx < steps.length; idx++) {
            def step = steps[idx]
            if (step != null) {
                if (!(step.context.length() == 0 && step.imageContext.length() == 0)) {
                    def content = [step.context, step.texCorrect, step.texIncorrect, step.reason]
                    def images = ["CTX_${idx}.svg", "CRT_${idx}.svg", "WRNG_${idx}.svg", "RSN_${idx}.svg"]
                    images.eachWithIndex { part, posn ->
                        if (content[posn].length() > 0)
                            svgs.put(part, content[posn])
                    }
                }
            }
        }
        
        if (choices != null) {
            choices.texs.eachWithIndex { tex, idx ->
                svgs.put("CH_${idx}.svg", tex)
            }
        }
        svgs
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
