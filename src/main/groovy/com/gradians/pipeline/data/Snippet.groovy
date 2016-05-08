package com.gradians.pipeline.data

import com.gradians.pipeline.edit.EditItem
import com.gradians.pipeline.edit.IEditable
import com.gradians.pipeline.edit.EditGroup


class Snippet extends Asset implements IEditable {
    
    @Override
    EditGroup[] getEditGroups() {
        EditGroup[] pnls = new EditGroup[1]
        pnls[0] = new EditGroup("Snippet")
        pnls[0].skill = skillId
        pnls[0].addEditItem(new EditItem("Correct", correct ? texStatement : "", 4, true))
        pnls[0].addEditItem(new EditItem("Incorrect", correct ? "" : texStatement, 4, true))
        pnls[0].addEditItem(new EditItem("Reason", texReason, 8, true))
        pnls
    }

    @Override
    void updateModel(EditGroup[] panel) {
        if (panel[0].editItems[0].tex.trim().length() > 0) {
            texStatement = panel[0].editItems[0].tex
            correct = true
        } else {
            texStatement = panel[0].editItems[1].tex
            correct = false
        }
        texReason = panel[0].editItems[2].tex
        skillId = panel[0].skill
    }

    @Override
    String toXMLString() {
        def sw = new StringWriter()
        def xml = new groovy.xml.MarkupBuilder(sw)
        xml.mkp.xmlDeclaration(version: "1.0", encoding: "utf-8")
        xml.snippet([xmlns: "http://www.gradians.com", skillId: skillId]) {
            render() {
                def map = [:]
                if (!correct)
                    map.correct = "false"
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
        def sw = new StringWriter()
        def xml = new groovy.xml.MarkupBuilder(sw)
        xml.mkp.xmlDeclaration(version: "1.0", encoding: "utf-8")
        xml.snippet(xmlns: "http://www.gradians.com") {
            render() {
                def map = [src: "${counter}.svg"]
                if (!correct)
                    map.correct = "false"
                tex(map)
                svgs.put("${counter++}.svg", texStatement)
            }
            reason() {
                tex(src: "${counter}.svg")
                svgs.put("${counter++}.svg", texReason)
            }
            if (skillId != -1) {
                delegate.skills() {
                    delegate.skill(id: skillId)
                }
            }            
        }
        qpath.resolve(LAYOUT_FILE).toFile().write(sw.toString())
        svgs
    }
    
    @Override
    protected Asset parse(InputStream xmlStream) {
        def xml = new XmlSlurper().parse(xmlStream)
        if (!xml.@skillId.isEmpty())
            skillId = xml.@skillId.toInteger()
        texStatement = xml.render.tex.toString()
        correct = !xml.render.tex.@correct.equals(false)
        texReason = xml.reason.tex.toString()
        this
    }

    boolean correct = true
    String texStatement = "", texReason = ""
    int skillId = -1
}

