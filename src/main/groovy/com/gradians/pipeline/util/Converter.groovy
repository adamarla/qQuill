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
    
    def convert(String chapterId) {
        asset.chapterId = chapterId.toInteger()
        def xml = "<?xml version='1.0' encoding='utf-8'?>\n" +
            "<question xmlns='http://www.gradians.com' chapterId='${asset.chapterId}' />"
        sourceXml = new XmlSlurper(false, false).parseText(xml)

        def context = questionXml.statement.tex.toString() + '\n'
        questionXml.step.each {
            context += it.context.toString() + '\n'
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
                        def text = ""
                        def tag = "tex"
                        if (!stepNode.image.isEmpty()) {
                            map.isImage = true
                            tag = "image"                        
                        }                        
                        stepNode."${tag}".each {
                            if (it.@correct.isEmpty()) {
                                map.correct = false
                            }
                            tex(map, it.toString())
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
    
    private def updateChapterId() {
        // HTTP POST chapterId to server
        def url = "question/tag"
        Map map = [id: asset.id, c: asset.chapterId]
        
        // create asset
        Network.executeHTTPPostBody(url, map)
    }
    
}
