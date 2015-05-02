package com.gradians.pipeline

import groovyx.net.http.HTTPBuilder
import static groovyx.net.http.Method.*
import static groovyx.net.http.ContentType.*

class Network {
    
    def updateTags(Question q) throws Exception {
        
        def bodyMap = q.toJSONString()
        
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
