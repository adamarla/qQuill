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
        pnls[0].addEditItem("Statement", xml.render.tex.toString(), 6, 
            xml.render.tex.@isImage.equals(true))
        pnls[0].addEditItem("Study Note", xml.reason.tex.toString(), 10, 
            xml.reason.tex.@isImage.equals(true))        
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

    @Override
    Map<String, String> toRender() {
        def counter = 1
        HashMap<String, String> svgs = new HashMap<String, String>()
        
        // Create layout xml file for the svgs
        def xmlStream = java.nio.file.Files.newInputStream(qpath.resolve(SRC_FILE))
        def layoutXml = new XmlSlurper(false, false).parse(xmlStream)
        
        ["render", "reason"].each {
            def map = [:]
                        
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

