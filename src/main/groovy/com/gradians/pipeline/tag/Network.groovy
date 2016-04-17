package com.gradians.pipeline.tag

import com.gradians.pipeline.Config
import com.gradians.pipeline.data.Question
import com.gradians.pipeline.edit.Renderer

import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Status

import static groovyx.net.http.Method.POST
import static groovyx.net.http.Method.GET
import static groovyx.net.http.ContentType.JSON
import static groovyx.net.http.ContentType.XML
import static groovyx.net.http.ContentType.URLENC

class Network {
    
    static def executeHTTPGet(String url) {
        def payload
        def hostport = (new Config()).get("host_port")
        def httpClient = new HTTPBuilder("http://${hostport}/${url}")
        httpClient.setHeaders(Accept: 'application/json')
        def results = httpClient.request(GET, JSON) { req ->            
            response.success = { resp, json ->
                assert Status.SUCCESS.matches(resp.statusLine.statusCode)
                payload = json
            }
        }
        payload
    }

    static def executeHTTPPost(String url) {
        def payload
        def hostport = (new Config()).get("host_port")
        def httpClient = new HTTPBuilder("http://${hostport}/${url}")
        httpClient.setHeaders(Accept: 'application/json')
        def results = httpClient.request(POST, JSON) { req ->            
            response.success = { resp, json ->
                assert Status.SUCCESS.matches(resp.statusLine.statusCode)
                payload = json
            }
        }
        payload
    }
        
    def updateBundleSignature(String bundleId, String signature) {
        def httpClient = new HTTPBuilder(
            "http://www.gradians.com/bundle/update/?id=${bundleId}&signature=${signature}")
        httpClient.setHeaders(Accept: 'application/json')
        def results = httpClient.request(POST, JSON) { req ->
            
            response.success = { resp ->
                assert resp.statusLine.statusCode == 200
            }        
        }
    }
    
    def fetchAllBundles() {
        def bundles = []
        def httpClient = new HTTPBuilder("http://www.gradians.com/bundle/fetch_all")
        httpClient.setHeaders(Accept: 'application/json')
                
        def results = httpClient.request(GET, JSON) {
                         
            response.success = { resp, json ->
                assert resp.statusLine.statusCode == 200
                bundles = json.bundles
            }
        }
        bundles
    }
    
    def getBundleQuestions(String bundleId) {
        def questions = []
        def httpClient = new HTTPBuilder("http://www.gradians.com/bundle/questions?bundle_id=${bundleId}")
        httpClient.setHeaders(Accept: 'application/json')
                
        def results = httpClient.request(GET, JSON) {
                         
            response.success = { resp, json ->
                assert resp.statusLine.statusCode == 200
                questions = json.questions
            }
        }
        questions
    }
    
    def getBundleInfo(Question q) {
        def bundleId
        def httpClient = new HTTPBuilder("http://www.gradians.com/bundle/which?uid=${q.uid}")
        httpClient.setHeaders(Accept: 'application/json')
                
        def results = httpClient.request(GET, JSON) {
                         
            response.success = { resp, json ->
                assert resp.statusLine.statusCode == 200
                bundleId = json.bundleId
            }        
        }
        bundleId
    }
    
    def addToBundle(Question q) {
        def uid        
        def bodyMap = (new Renderer(q)).toJSONString()
        
        def httpClient = new HTTPBuilder("http://www.gradians.com/tag/question")
        httpClient.setHeaders(Accept: 'application/json')
        
        def results = httpClient.request(POST, JSON) { req ->
            requestContentType = JSON
            body = bodyMap
            
            response.success = { resp, json ->
                assert resp.statusLine.statusCode == 200
                uid = json.uid
                println "SUCCESS! ${json.uid}"
            }
        }        
        uid
    }
    
}
