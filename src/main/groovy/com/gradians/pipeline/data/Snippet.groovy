package com.gradians.pipeline.data

import com.gradians.pipeline.edit.EditItem
import com.gradians.pipeline.edit.IEditable
import com.gradians.pipeline.edit.EditGroup
import groovy.xml.XmlUtil


class Snippet extends Asset {
    
    @Override
    EditGroup[] getEditGroups() {
        EditGroup[] pnls = new EditGroup[1]
        pnls[0] = new EditGroup("Snippet")
        
        def correct = !xml.render.tex.@correct.equals(false)
        pnls[0].addEditItem("Correct", correct ? xml.render.tex.toString() : "", 4, 
            correct ? xml.render.tex.@isImage.equals(true) : false)
        pnls[0].addEditItem("Incorrect", correct ? "": xml.render.tex.toString(), 4, 
            correct ? false : xml.render.tex.@isImage.equals(true))
        pnls[0].addEditItem("Reason", xml.reason.tex.toString(), 8, 
            xml.reason.tex.@isImage.equals(true))
        
        pnls[0].skills = xml.skills.skill.@id*.toInteger()
        pnls
    }

    @Override
    void updateModel(EditGroup[] panels) {
        
        def item = panels[0].editItems[0]
        def map = [:]
                
        if (!item.text.trim()) {
            item = panels[0].editItems[1]
            map = [correct: false]
        }
                
        if (item.isImage)
            map.isImage = true
            
        xml.render.replaceNode {
            render() {
                tex(map, item.text.trim())
            }
        }
        
        xml.reason.replaceNode {
            reason() {
                map = panels[0].editItems[2].isImage ? [isImage: true] : [:]
                tex(map, panels[0].editItems[2].text)    
            }
        }
        
        if (panels[0].skills[0]) {
            xml.skills.replaceNode {
                skills() {
                    panels[0].skills.each {
                        if (it)
                            skill(id: it)
                    }
                }
            }    
        }        
    }

    @Override
    Map<String, String> toRender() {
        def counter = 1
        HashMap<String, String> svgs = new HashMap<String, String>()        
        
        // Create layout xml file for the svgs
        def xmlStream = java.nio.file.Files.newInputStream(qpath.resolve(SRC_FILE))
        def layoutXml = new XmlSlurper(false, false).parse(xmlStream)
        
        ["render", "reason"].each {
            def correct = !layoutXml."$it".tex.@correct.equals(false)
            def map = correct ? [:] : [correct: false] 
            
            if (!layoutXml."$it".tex.@isImage.equals(true)) {
                def src = "${counter++}.svg"
                svgs.put(src, layoutXml."$it".tex.toString())
                map.src = src
            } else {
                map.src = layoutXml."$it".tex.toString()
                map.isImage = true
            }
            
            // some wierd quirk, this does not work without
            // specifying "node" as parameter
            layoutXml."$it".replaceNode { node ->
                "$it"() {
                    tex(map)
                }
            }
        }
        serialize(layoutXml, java.nio.file.Files.newOutputStream(qpath.resolve(LAYOUT_FILE)))
        svgs
    }
    
}