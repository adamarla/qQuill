package com.gradians.pipeline.data

import com.gradians.pipeline.edit.Component
import com.gradians.pipeline.edit.IEditable
import com.gradians.pipeline.edit.Panel


class Snippet extends Asset implements IEditable {
    
    @Override
    Panel[] getPanels() {
        Panel[] pnls = new Panel[1]
        pnls[0] = new Panel("Snippet")
        pnls[0].skill = skill
        pnls[0].addComponent(new Component("Correct", correct ? texStatement : "", 12, true))
        pnls[0].addComponent(new Component("Incorrect", correct ? "" : texStatement, 12, true))
        pnls[0].addComponent(new Component("Reason", texReason, 12, true))        
        pnls
    }

    @Override
    void updateModel(Panel[] panel) {
        if (panel[0].components[0].tex.length() > 0) {
            texStatement = panel[0].components[0].tex
            correct = true
        } else {
            texStatement = panel[0].components[1].tex
            correct = false
        }
        texReason = panel[0].components[2].tex
    }

    @Override
    String toXMLString() {
        def sw = new StringWriter()
        def xml = new groovy.xml.MarkupBuilder(sw)
        xml.mkp.xmlDeclaration(version: "1.0", encoding: "utf-8")
        xml.snippet(xmlns: "http://www.gradians.com") {
            render() {
                def map = [:]
                if (!correct)
                    map.correct = "false"
                tex(map, texStatement)
            }
            reason() {
                tex(texReason)
            }
            if (skill != -1) {
                delegate.skills() {
                    delegate.skill(id: skill)
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
            if (skill != -1) {
                delegate.skills() {
                    delegate.skill(id: skill)
                }
            }            
        }
        qpath.resolve(LAYOUT_FILE).toFile().write(sw.toString())
        svgs
    }
    
    @Override
    protected void parse(InputStream xmlStream) {
        def xml = new XmlSlurper().parse(xmlStream)
        texStatement = xml.render.tex.toString()
        correct = xml.render.tex.@correct.equals("false")
        texReason = xml.reason.tex.toString()
        if (!xml.skills.isEmpty()) {
            skill = xml.skills.skill.@id.toInteger()
        }
    }

    boolean correct = true
    String texStatement = "", texReason = ""
    int skill = -1
}

