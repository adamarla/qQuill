package com.gradians.pipeline.data

import com.gradians.pipeline.edit.EditItem
import com.gradians.pipeline.edit.IEditable
import com.gradians.pipeline.edit.EditGroup
import groovy.xml.XmlUtil
import java.nio.file.Files


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
        pnls[0].addEditItem("Reason", xml.reason.tex.toString(), 8, xml.reason.tex.@isImage.equals(true))
        
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
        
        xml.skills.replaceNode {
            skills() {
                panels[0].skills.each {
                    skill(id: it)
                }    
            }
        }
    }

    String toXMLString() {
        def sw = new StringWriter()
        def xml = new groovy.xml.MarkupBuilder(sw)
        xml.mkp.xmlDeclaration(version: "1.0", encoding: "utf-8")
        xml.snippet([xmlns: "http://www.gradians.com", skillId: skillId]) {
            render() {
                def map = correct ? [:] : [correct: false]
                tex(map, texStatement)
            }
            reason() {
                tex(texReason)
            }
            if (skillId != -1) {
                delegate.skills() {
                    delegate.skill(id: skillId)
                }
            }            
        }
        sw.toString()
    }

    @Override
    Map<String, String> toRender() {
        def counter = 1
        HashMap<String, String> svgs = new HashMap<String, String>()        
        
        // Create layout xml file for the svgs
        def xmlStream = Files.newInputStream(qpath.resolve(SRC_FILE))
        def layoutXml = new XmlSlurper(false, false).parse(xmlStream)
        
        ["render", "reason"].each {
            def correct = !layoutXml."$it".tex.@correct.equals(false)
            def map = correct ? [:] : [correct: false] 
            
            if (!layoutXml."$it".tex.@isImage.equals(true)) {
                def src = "${counter++}.svg"
                svgs.put(src, layoutXml."$it".tex.toString())
                map.src = src
                layoutXml."$it".replaceNode { node ->
                    "$it"() {
                        tex(map)
                    }
                }
            } else {                           
                layoutXml."$it".replaceNode { node ->
                    map.src = node.toString()
                    map.isImage = true                    
                    "$it"() {
                        tex(map)
                    }
                }
            }
        }
        qpath.resolve(LAYOUT_FILE).toFile().write(XmlUtil.serialize(layoutXml))

//        def sw = new StringWriter()
//        def xml = new groovy.xml.MarkupBuilder(sw)
//        xml.mkp.xmlDeclaration(version: "1.0", encoding: "utf-8")
//        xml.snippet(xmlns: "http://www.gradians.com") {
//            render() {
//                def map = [src: "${counter}.svg"]
//                if (!correct)
//                    map.correct = "false"
//                tex(map)
//                svgs.put("${counter++}.svg", texStatement)
//            }
//            reason() {
//                tex(src: "${counter}.svg")
//                svgs.put("${counter++}.svg", texReason)
//            }
//            if (skillId != -1) {
//                delegate.skills() {
//                    delegate.skill(id: skillId)
//                }
//            }            
//        }
//        qpath.resolve(LAYOUT_FILE).toFile().write(sw.toString())
        svgs
    }
    
    boolean correct = true
    String texStatement = "", texReason = ""
    int skillId = -1
}

