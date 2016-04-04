package com.gradians.pipeline.data

import java.io.FileInputStream
import java.nio.file.DirectoryStream
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.PathMatcher

import com.gradians.pipeline.editor.Component
import com.gradians.pipeline.editor.IEditable
import com.gradians.pipeline.editor.Panel;

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
    
    def toXMLString() {
        def sw = new StringWriter()
        def xml = new groovy.xml.MarkupBuilder(sw)
        xml.mkp.xmlDeclaration(version: "1.0", encoding: "utf-8")
        xml.question(xmlns: "http://www.gradians.com") {
            statement() {
                statement.toXMLString()
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
        Panel[] pnls = new Panel[8]        
        pnls[0] = new Panel("Problem")
        pnls[0].addComponent(new Component("Statement", statement.tex, 12, true))
        
        steps.eachWithIndex { step, idx ->
            if (step == null)
                step = new Step()
                        
            pnls[idx+1] = new Panel("Step ${idx+1}")
            pnls[idx+1].addComponent(new Component("Context", step.context, 4, true))
            pnls[idx+1].addComponent(new Component("Correct", step.texCorrect, 8, true))
            pnls[idx+1].addComponent(new Component("Incorrect", step.texIncorrect, 8, true))
            pnls[idx+1].addComponent(new Component("Reason", step.reason, 12, true))                
        }
        
        pnls[7] = new Panel("Choices")
        if (choices != null) {
            choices.texs.eachWithIndex { tex, idx ->
                pnls[7].addComponent(new Component("Choice ${idx+1}", tex, 4, true))
            }
        }        
        pnls
    }    
    
    public void updateModel(Panel[] panels) {        
        panels.eachWithIndex { Panel panel, int idx ->
            if (idx == 0) {
                statement.tex = panels[0].components[0].tex
            } else if (idx > 0 && idx < 7) {
                steps[idx].context = panels[idx].components[0].tex
                steps[idx].texCorrect = panels[idx].components[1].tex
                steps[idx].texIncorrect = panels[idx].components[2].tex
                steps[idx].reason = panels[idx].components[3].tex
            } else {
                choices = new Choices()
                panels[7].components.eachWithIndex { tex, i ->
                    choices.texs[i] = tex
                }
            }    
        }        
    }

    @Override
    public void updateModel(Panel panel, int idx) {        
        if (idx == 0) {
            statement.tex = panels[0].components[0].tex
        } else if (idx > 0 && idx < 7) {
            steps[idx].context = panels[idx].components[0].tex
            steps[idx].texCorrect = panels[idx].components[1].tex
            steps[idx].texIncorrect = panels[idx].components[2].tex
            steps[idx].reason = panels[idx].components[3].tex
        } else {
            choices = new Choices()
            panels[7].components.eachWithIndex { tex, i ->
                choices.texs[i] = tex
            }
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
