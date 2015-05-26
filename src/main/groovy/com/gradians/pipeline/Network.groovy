package com.gradians.pipeline

import groovyx.net.http.HTTPBuilder

import static groovyx.net.http.Method.POST
import static groovyx.net.http.ContentType.JSON
import static groovyx.net.http.ContentType.XML

class Network {
    
    def addToBundle(Question q) throws Exception {
        
        def bodyMap = (new Renderer(q)).toJSONString()
        
//        def httpClient = new HTTPBuilder('http://localhost:3000/tag/question')
        def httpClient = new HTTPBuilder('http://www.gradians.com/tag/question')
        httpClient.setHeaders(Accept: 'application/json')
        
        def results = httpClient.request(POST, JSON) { req ->
            requestContentType = JSON
            body = bodyMap
            
            response.success = { resp ->
                println "SUCCESS! ${resp.statusLine}"
                assert resp.statusLine.statusCode == 200
            }
        
        }
        
    }
    
}
