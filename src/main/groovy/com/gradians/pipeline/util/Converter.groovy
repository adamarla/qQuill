package com.gradians.pipeline.util

import com.gradians.pipeline.data.Asset

import java.nio.file.Files

/**
 * Temp class to conver question.xml to source.xml
 * @author adamarla
 *
 */

class Converter {
    
    def questionXml, sourceXml
    def Asset asset

    Converter(Asset a) {
        if (Files.notExists(a.qpath.resolve("question.xml")))
            throw new Exception("question.xml does not exist")
        asset = a
        def stream = Files.newInputStream(a.qpath.resolve("question.xml"))
        questionXml = new XmlSlurper(false, false).parse(stream)
    }
    
    def convert() {
        def xml = "<?xml version='1.0' encoding='utf-8'?>\n" +
            "<question xmlns='http://www.gradians.com' />"
        sourceXml = new XmlSlurper(false, false).parseText(xml)

        def context = questionXml.statement.tex.toString().trim() + '\\\\\n'
        questionXml.step.each {
            context += it.context.toString().trim() + '\\\\\n'
        }
        sourceXml.appendNode {
            delegate.statement() {
                tex(context)
            }
        }
        
        questionXml.step.each { stepNode ->
            sourceXml.appendNode {
                step() { 
                    options() {
                        def map = [:]
                        def tag = "tex"
                        if (!stepNode.image.isEmpty()) {
                            map.isImage = true
                            tag = "image"
                        }
                        stepNode."${tag}".each {
                            if (it.@correct.isEmpty()) {
                                map.correct = false
                            }
                            def text = it.toString()
                            if (map.isImage) {
                                if (!text.startsWith("img_")) {
                                    Files.move(asset.qpath.resolve(text), asset.qpath.resolve("img_${text}"))
                                    text = "img_${text}"
                                }
                            }                            
                            tex(map, text)
                        }
                    }
                    reason() {
                        tex(stepNode.reason.toString())
                    }
                }
            }
        }
        
        if (!questionXml.choices.isEmpty()) {
            sourceXml.appendNode {
                choices() {
                    questionXml.choices.tex.each {
                        def map = it.@correct.isEmpty() ? [correct: false] : [:]
                        tex(map, it.toString())
                    }
                }
            }
        }
        
        asset.xml = sourceXml
        asset.save()
    }

}
