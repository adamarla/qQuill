package com.gradians.pipeline.data

import com.gradians.pipeline.edit.EditItem
import com.gradians.pipeline.edit.IEditable
import com.gradians.pipeline.edit.EditGroup

import groovy.xml.XmlUtil


class Skill extends Asset {
    
    @Override
    EditGroup[] getEditGroups() {
        EditGroup[] pnls = new EditGroup[1]
        pnls[0] = new EditGroup("Skill")
        pnls[0].addEditItem("Statement", xml.render.tex.toString(), 6, xml.render.tex.@isImage.equals(true))
        pnls[0].addEditItem("Study Note", xml.reason.tex.toString(), 10, xml.reason.tex.@isImage.equals(true))        
        pnls
    }

    @Override
    void updateModel(EditGroup[] panels) {
        xml.render.replaceNode {
            render() {
                def map = panels[0].editItems[0].isImage ? [isImage: true] : [:]
                tex(map, panels[0].editItems[0].text)    
            }
        }
        xml.reason.replaceNode {
            reason() {
                def map = panels[0].editItems[1].isImage ? [isImage: true] : [:]
                tex(map, panels[0].editItems[1].text)    
            }
        }
    }

    String toXMLString() {
        def sw = new StringWriter()
        def xml = new groovy.xml.MarkupBuilder(sw)
        xml.mkp.xmlDeclaration(version: "1.0", encoding: "utf-8")
        xml.skill([xmlns: "http://www.gradians.com", chapterId: chapterId]) {
            render() {
                tex(texStatement)
            }
            reason() {
                tex(texReason)
            }
        }
        sw.toString()
    }

    @Override
    Map<String, String> toRender() {
        def counter = 1
        HashMap<String, String> svgs = new HashMap<String, String>()
        
        // Create layout xml file for the svgs
        def xmlStream = java.nio.file.Files.newInputStream(qpath.resolve(SRC_FILE))
        def layoutXml = new XmlSlurper(false, false).parse(xmlStream)
        
        ["render", "reason"].each {
            if (!layoutXml."$it".tex.@isImage.equals(true)) {
                def src = "${counter++}.svg"
                svgs.put(src, layoutXml."$it".tex.toString())
                layoutXml."$it".replaceNode { node ->
                    "$it"() {
                        tex(src: src)
                    }
                }
            } else {
                layoutXml."$it".replaceNode { node ->
                    "$it"() {
                        tex(src: node.toString(), isImage: true)
                    }
                }
            }
        }
        qpath.resolve(LAYOUT_FILE).toFile().write(XmlUtil.serialize(layoutXml))
        
//        def sw = new StringWriter()
//        def xml = new groovy.xml.MarkupBuilder(sw)
//        xml.mkp.xmlDeclaration(version: "1.0", encoding: "utf-8")
//        xml.skill([xmlns: "http://www.gradians.com", chapterId: chapterId]) {
//            render() {
//                tex(src: "${counter}.svg")
//                svgs.put("${counter++}.svg", this.xml.render.tex.toString())
//            }
//            reason() {
//                tex(src: "${counter}.svg")
//                svgs.put("${counter++}.svg", this.xml.reason.tex.toString())
//            }
//        }
//        qpath.resolve(LAYOUT_FILE).toFile().write(sw.toString())
        svgs
    }
    
    String texStatement = "", texReason = ""

}

