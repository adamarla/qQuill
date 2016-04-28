package com.gradians.pipeline.data

import com.gradians.pipeline.edit.EditItem
import com.gradians.pipeline.edit.IEditable
import com.gradians.pipeline.edit.EditGroup


class Skill extends Asset implements IEditable {
    
    @Override
    EditGroup[] getEditGroups() {
        EditGroup[] pnls = new EditGroup[1]
        pnls[0] = new EditGroup("Skill")
        pnls[0].addEditItem(new EditItem("Statement", texStatement, 6, true))
        pnls[0].addEditItem(new EditItem("Study Note", texReason, 10, true))        
        pnls
    }

    @Override
    void updateModel(EditGroup[] panels) {
        texStatement = panels[0].editItems[0].tex
        texReason = panels[0].editItems[1].tex
    }

    @Override
    String toXMLString() {
        def sw = new StringWriter()
        def xml = new groovy.xml.MarkupBuilder(sw)
        xml.mkp.xmlDeclaration(version: "1.0", encoding: "utf-8")
        xml.skill(xmlns: "http://www.gradians.com") {
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
        def sw = new StringWriter()
        def xml = new groovy.xml.MarkupBuilder(sw)
        xml.mkp.xmlDeclaration(version: "1.0", encoding: "utf-8")
        xml.skill(xmlns: "http://www.gradians.com") {
            render() {
                tex(src: "${counter}.svg")
                svgs.put("${counter++}.svg", texStatement)
            }
            reason() {
                tex(src: "${counter}.svg")
                svgs.put("${counter++}.svg", texReason)
            }
        }
        qpath.resolve(LAYOUT_FILE).toFile().write(sw.toString())
        svgs
    }
    
    @Override
    protected void parse(InputStream xmlStream) {
        def xml = new XmlSlurper().parse(xmlStream)
        texStatement = xml.render.tex.toString()
        texReason = xml.reason.tex.toString()        
    }
    
    String texStatement = "", texReason = ""
    

}

