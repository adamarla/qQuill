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
        xml.question([xmlns: "http://www.gradians.com", chapterId: chapterId]) {
            statement() {
                tex(statement.tex) 
            }
            steps.each { step ->
                if (step.reason) {
                    def map = step.skill == -1 ? [:] : [skillId: step.skill]
                    delegate.step(map) {
                                               
                        if (step.imageCorrect)
                            image(correct: true, step.imageCorrect)
                        else if (step.texCorrect)
                            tex(correct: true, step.texCorrect)
                            
                        if (step.imageIncorrect)
                            image(step.imageIncorrect)
                        else if (step.texIncorrect)
                            tex(step.texIncorrect)
                            
                        reason(step.reason)                        
                    }
                }
            }
            
            if (choices.texs[0]) {
                delegate.choices() {
                    choices.texs.eachWithIndex { tex, i ->
                        def map = choices.correct == i ? [correct: true] : [:]
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
            panels[idx+1] = new EditGroup("Step ${idx+1}")
            panels[idx+1].skill = step.skill
            def text, isTex
            ["Correct", "Incorrect"].each { it ->
                if (step."tex${it}") {
                    isTex = true
                    text = step."tex${it}"
                } else if (step."image${it}") {
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
                if (panel.editItems[REASON_IDX].tex) {
                    
                    steps[idx-1].skill = panel.skill
                    
                    if (panel.editItems[CORRECT_IDX].isTex)
                        steps[idx-1].texCorrect = panel.editItems[CORRECT_IDX].tex
                    else
                        steps[idx-1].texCorrect = panel.editItems[CORRECT_IDX].image
                        
                    if (panel.editItems[INCORRECT_IDX].isTex)
                        steps[idx-1].texIncorrect = panel.editItems[INCORRECT_IDX].tex
                    else
                        steps[idx-1].texCorrect = panel.editItems[INCORRECT_IDX].image
                        
                    if (panel.editItems[REASON_IDX].isTex)
                        steps[idx-1].reason = panel.editItems[REASON_IDX].tex
                    else
                        steps[idx-1].reason = panel.editItems[REASON_IDX].image
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
                if (step.reason) {
                    def map = step.skill != -1 ? [skillId: step.skill] : [:]
                    delegate.step() {
                        options() {
                            ["Correct", "Incorrect"].each { option ->
                                def correct = option.equals("Correct")
                                if (step."tex${option}") {
                                    tex(src: "${counter}.svg", correct: correct)
                                    svgs.put("${counter++}.svg", step."tex${option}")
                                } else if (step."image${option}") {
                                    image(src: "image${option}.svg", correct: correct)
                                }
                            }
                        }
                        reason() {
                            tex(src: "${counter}.svg")
                        }
                        svgs.put("${counter++}.svg", step.reason)
                        
                        if (step.skill != -1) {
                            delegate.skills() {
                                delegate.skill(id: step.skill)
                            }
                        }
                    }
                }
            }
            if (choices.texs[0]) {
                // http://mrhaki.blogspot.in/2012/01/groovy-goodness-solve-naming-conflicts.html
                delegate.choices() {
                    choices.texs.eachWithIndex { tex, i ->
                        def map = i == choices.correct ? [src: "${counter}.svg"] : 
                            [src: "${counter}.svg", correct: false]
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
    protected Asset parse(InputStream xmlStream) {
        def xml = new XmlSlurper().parse(xmlStream)
        if (!xml.@chapterId.isEmpty())
            chapterId = xml.@chapterId.toInteger()
        statement = new Statement()
        statement.tex = xml.statement.tex.toString()
        if (!xml.statement.image.isEmpty())
            statement.image = xml.statement.image.toString()
            
        steps = new Step[6]
        steps.eachWithIndex { it, i -> steps[i] = new Step() }
        xml.step.eachWithIndex { it, i ->            
            ["tex", "image"].each { content ->
                it."${content}".each { option ->
                    if (option.@correct.equals("true")) {
                        steps[i]."${content}Correct" = option.toString()
                    } else {
                        steps[i]."${content}Incorrect" = option.toString()
                    }
                }
            }
            steps[i].reason = it.reason.toString()
            
            if (!it.@skillId.isEmpty()) {
                steps[i].skill = it.@skillId.toInteger()
            }    
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
        this
    }
    
    Statement statement
    Step[] steps
    Choices choices
    
    final int CORRECT_IDX = 0, INCORRECT_IDX = 1, REASON_IDX = 2 

}

class Statement {
    String tex = "", image = ""
}

class Step {
    boolean noswipe
    String imageCorrect = "", imageIncorrect = "", imageReason = ""
    String texCorrect = "", texIncorrect = "", reason = ""
    int skill = -1
}

class Choices {
    public Choices() {
        correct = ((int)Math.random()*100)%4 
    }
    String[] texs = ["", "", "", ""]
    int correct    
}
