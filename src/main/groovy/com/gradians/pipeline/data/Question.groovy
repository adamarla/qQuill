package com.gradians.pipeline.data

import com.gradians.pipeline.edit.EditItem
import com.gradians.pipeline.edit.IEditable
import com.gradians.pipeline.edit.EditGroup


class Question extends Asset implements IEditable {
    
    @Override
    public EditGroup[] getEditGroups() {
        EditGroup[] panels = new EditGroup[8]
        panels[0] = new EditGroup("Problem")
        panels[0].addEditItem("Statement", 
            xml.statement.tex.toString(), 12, 
            xml.statement.tex.@isImage.equals(true))
        
        xml.step.eachWithIndex { step, idx ->
            panels[idx+1] = new EditGroup("Step ${idx+1}")
            panels[idx+1].editItems = new EditItem[xml.options.tex.size()]
            
            def incorrect = step.options.tex.find { it -> it.@correct.equals(false) }
            def correct = step.options.tex.find { it -> it.@correct.isEmpty() }
            
            panels[idx+1].addEditItem("Correct", 
                correct.isEmpty() ? "" : correct.toString(), 8,
                correct.isEmpty() ? false : correct.@isImage.equals(true))
            panels[idx+1].addEditItem("Incorrect", 
                incorrect.isEmpty() ? "" : incorrect.toString(), 8,
                incorrect.isEmpty() ? false : incorrect.@isImage.equals(true))
            panels[idx+1].addEditItem("Reason", step.reason.tex.toString(), 8,
                step.reason.tex.@isImage.equals(true))
            
            panels[idx+1].skills = step.skills.skill.@id*.toInteger()            
        }
        
        (1..6).each {
            if (panels[it] == null) {
                panels[it] = new EditGroup("Step ${it}")
                panels[it].addEditItem("Correct", "", 8)
                panels[it].addEditItem("Incorrect", "", 8)
                panels[it].addEditItem("Reason", "", 8)
            }
        }
        
        panels[7] = new EditGroup("Choices")
        if (!xml.choices.isEmpty()) {
            xml.choices.tex.eachWithIndex { tex, i ->
                panels[7].addEditItem(tex.@correct.equals(false) ? "Incorrect" : "Correct" , 
                    tex.toString(), 4, tex.@isImage.equals(true))
            }
        } else {
            int correct = ((int)Math.random()*100)%4
            (0..3).each {
                panels[7].addEditItem( it == correct ? "Correct" : "Incorrect", "", 4)
            }
        }
        panels
    }

    @Override
    public void updateModel(EditGroup[] panels) {        
        def text = 
            "<?xml version='1.0' encoding='utf-8'?>\n" +
            "<question xmlns='http://www.gradians.com' chapterId='${chapterId}' />"
        xml = new XmlSlurper().parseText(text)
        
        xml.appendNode {
            statement() {
                def map = panels[0].editItems[0].isImage ? [isImage: true] : [:]
                tex(map, panels[0].editItems[0].text)
            }
        }

        (1..6).each { int idx ->
            EditGroup panel = panels[idx]
            
            if (panel.editItems[REASON_IDX].text) {                
                def stepNode = {
                    step() {
                        options() {
                            def map = panel.editItems[CORRECT_IDX].isImage ? [isImage: true] : [:]
                            tex(map, panel.editItems[CORRECT_IDX].text)
                            
                            map = panel.editItems[INCORRECT_IDX].isImage ? [isImage: true] : [:]
                            map.correct = false
                            tex(map, panel.editItems[INCORRECT_IDX].text)
                        }
                        
                        reason() {
                            def map = panel.editItems[REASON_IDX].isImage ? [isImage: true] : [:]
                            tex(map, panel.editItems[REASON_IDX].text)    
                        }
                        
                        if (panel.skills[0]) {
                            xml.skills.replaceNode {
                                skills() {
                                    panel.skills.each {
                                        if (it)
                                            skill(id: it)
                                    }
                                }
                            }    
                        }        
                    }                    
                }
                xml.appendNode stepNode
            }
        }

        if (panels[7].editItems[0].text) {
            def choicesNode = {
                choices() {
                    panels[7].editItems.each {
                        def map = it.title.equals("Correct") ? [:] : [correct: false]
                        if (it.isImage)
                            map.isImage = true
                        tex(map, it.text)
                    }
                }
            }
            xml.appendNode choicesNode
        }
    }   
    
    @Override
    public Map<String, String> toRender() {
        def counter = 1        
        HashMap<String, String> svgs = new HashMap<String, String>()
        
        // Create layout xml file for the svgs
        def xmlStream = java.nio.file.Files.newInputStream(qpath.resolve(SRC_FILE))
        def layoutXml = new XmlSlurper(false, false).parse(xmlStream)
        
        def mapStatement = [:]
        if (!layoutXml.statement.tex.@isImage.equals(true)) {
            def srcStatement = "${counter}.svg"
            counter++
            svgs.put(srcStatement, layoutXml.statement.tex.toString())
            mapStatement.src = srcStatement
        } else {
            mapStatement.src = layoutXml.statement.tex.toString()
            mapStatement.isImage = true
        }
        layoutXml.statement.replaceNode { node ->
            statement() {
                tex(mapStatement)
            }
        }

        layoutXml.step.each { stepNode ->
            // options
            stepNode.options.tex.each {
                def correct = !it.@correct.equals(false)
                def map = correct ? [:] : [correct: false]

                if (!it.@isImage.equals(true)) {
                    def src = "${counter++}.svg"
                    svgs.put(src, it.toString())
                    map.src = src
                } else {
                    map.src = node.toString()
                    map.isImage = true
                }                
                it.replaceNode { node ->
                    tex(map)
                }    
            }
            
            // reason
            def mapReason = [:]
            if (!stepNode.reason.tex.@isImage.equals(true)) {
                def srcReason = "${counter++}.svg"
                svgs.put(srcReason, stepNode.reason.tex.toString())
                mapReason.src = srcReason
            } else {
                mapReason.src = stepNode.reason.tex.toString()
                mapReason.isImage = true
            }                        
            stepNode.reason.tex.replaceNode { node ->
                tex(mapReason)
            }
        }
        
        layoutXml.choices.tex.each {
            def mapChoice = it.@correct.equals(false) ? [correct: false] : [:]
            if (!it.@isImage.equals(true)) {
                def srcChoice = "${counter++}.svg"
                svgs.put(srcChoice, it.toString())                
                mapChoice.src = srcChoice
            } else {
                mapChoice.src = it.toString()
                mapChoice.isImage = true
            }
            
            it.replaceNode { node ->
                tex(mapChoice)
            }
        }
        
        serialize(layoutXml, java.nio.file.Files.newOutputStream(qpath.resolve(LAYOUT_FILE)))
        svgs
    }
        
    final int CORRECT_IDX = 0, INCORRECT_IDX = 1, REASON_IDX = 2 

}
