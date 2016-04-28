package com.gradians.pipeline.data

import com.gradians.pipeline.edit.EditItem
import com.gradians.pipeline.edit.IEditable
import com.gradians.pipeline.edit.EditGroup


class Question extends Asset implements IEditable {
    
    @Override
    String toXMLString() {
        def sw = new StringWriter()
        def xml = new groovy.xml.MarkupBuilder(sw)
        xml.mkp.xmlDeclaration(version: "1.0", encoding: "utf-8")
        xml.question(xmlns: "http://www.gradians.com") {
            statement() {
                tex(statement.tex) 
            }
            steps.each { step ->
                if (step != null && step.context.length() != 0) {
                    delegate.step(step.noswipe ? [swipe: false] : [:]) {
                        if (step.imageContext.length() > 0)
                            context(image: "true", step.imageContext)
                        else
                            context(step.context)
                        
                        if (step.imageCorrect.length() > 0)
                            image(correct: "true", step.imageCorrect)
                        else if (step.texCorrect.length() > 0)
                            tex(correct: "true", step.texCorrect)
                            
                        if (step.imageIncorrect.length() > 0)
                            image(step.imageIncorrect)
                        else if (step.texIncorrect.length() > 0)
                            tex(step.texIncorrect)
                            
                        if (step.imageReason.length() > 0)
                            reason(image: "true", step.imageReason)
                        else
                            reason(step.reason)
                    }
                }
            }
            if (choices.texs[0].length() > 0) {
                delegate.choices() {
                    choices.texs.eachWithIndex { tex, i ->
                        def map = choices.correct == i ? [correct: 'true'] : [:]
                        delegate.tex(map, tex)
                    }
                }
            }
        }
        sw.toString()
    }
    
    @Override
    public EditGroup[] getEditGroups() {
        EditGroup[] panels = new EditGroup[8]
        panels[0] = new EditGroup("Problem")
        panels[0].addEditItem(new EditItem("Statement", statement.tex, 12))
        
        steps.eachWithIndex { step, idx ->
            if (step == null)
                step = new Step()
            panels[idx+1] = new EditGroup("Step ${idx+1}")
            panels[idx+1].addEditItem(new EditItem("Context", step.context, 4))            
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
                panels[idx+1].addEditItem(new EditItem(it, text, 8, isTex))    
            }
            panels[idx+1].addEditItem(new EditItem("Reason", step.reason, 6))                
        }
        
        panels[7] = new EditGroup("Choices")
        choices.texs.eachWithIndex { tex, i ->
            panels[7].addEditItem(new EditItem(choices.correct == i ? "Correct" : "Incorrect", tex, 4))
        }
        panels
    }

    @Override
    public void updateModel(EditGroup[] panels) {
        panels.eachWithIndex { EditGroup panel, int idx ->
            if (idx == 0) {
                statement.tex = panel.editItems[0].tex
            } else if (idx > 0 && idx < 7) {
                if (panel.editItems[0].tex.length() > 0) {
                    steps[idx-1].context = panel.editItems[0].tex
                    
                    if (panel.editItems[1].isTex)
                        steps[idx-1].texCorrect = panel.editItems[1].tex
                    else
                        steps[idx-1].texCorrect = panel.editItems[1].image
                        
                    if (panel.editItems[2].isTex)
                        steps[idx-1].texIncorrect = panel.editItems[2].tex
                    else
                        steps[idx-1].texCorrect = panel.editItems[2].image
                        
                    if (panel.editItems[3])
                        steps[idx-1].reason = panel.editItems[3].tex
                    else    
                        steps[idx-1].reason = panel.editItems[3].image
                }
            } else {
                panels[7].editItems.eachWithIndex { comp, i ->
                    choices.texs[i] = comp.tex
                }
            }    
        }        
    }

    @Override
    public Map<String, String> toRender() {
        def counter = 1        
        HashMap<String, String> svgs = new HashMap<String, String>()
        
        // Create layout xml file for the svgs
        def sw = new StringWriter()
        def xml = new groovy.xml.MarkupBuilder(sw)
        xml.mkp.xmlDeclaration(version: "1.0", encoding: "utf-8")
        xml.question(xmlns: "http://www.gradians.com") {
            statement() {
                tex(src: "${counter}.svg")
                svgs.put("${counter++}.svg", statement.tex)
            }
            steps.each { step ->
                if (step != null && step.context.length() > 0) {
                    delegate.step() {
                        context() {
                            tex(src: "${counter}.svg")
                            svgs.put("${counter++}.svg", step.context)                    
                        }
                        options() {
                            ["Correct", "Incorrect"].each { option ->
                                def correct = option.equals("Correct") ? 'true' : 'false'
                                if (step."tex${option}".length() > 0) {
                                    tex(src: "${counter}.svg", correct: correct)
                                    svgs.put("${counter++}.svg", step."tex${option}")
                                } else if (step."image${option}".length() > 0) {
                                    image(src: "image${option}.svg", correct: correct)
                                }
                            }
                        }
                        reason() {
                            tex(src: "${counter}.svg")
                        }
                        svgs.put("${counter++}.svg", step.reason)
                    }
                }
            }
            if (choices.texs[0].length() > 0) {
                // http://mrhaki.blogspot.in/2012/01/groovy-goodness-solve-naming-conflicts.html
                delegate.choices() {
                    choices.texs.eachWithIndex { tex, i ->
                        def map = i == choices.correct ? [src: "${counter}.svg"] : 
                            [src: "${counter}.svg", correct: 'false']
                        delegate.tex(map)
                        svgs.put("${counter++}.svg", tex)
                    }
                }
            }
        }
        qpath.resolve(LAYOUT_FILE).toFile().write(sw.toString())
        svgs
    }
    
    @Override
    protected void parse(InputStream xmlStream) {
        def xml = new XmlSlurper().parse(xmlStream)
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
        
        choices = new Choices()
        if (!xml.choices.tex.isEmpty()) {
            xml.choices.tex.eachWithIndex { it, i ->
                choices.texs[i] = it.toString()
                if (it.@correct == true) {
                    choices.correct = i
                }
            }
        }
    }
    
    Statement statement
    Step[] steps
    Choices choices    

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
    public Choices() {
        correct = ((int)Math.random()*100)%4 
    }
    String[] texs = ["", "", "", ""]
    int correct    
}
