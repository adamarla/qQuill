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
        def hostport = Config.getInstance().get("host_port")
        def httpClient = new HTTPBuilder("http://${hostport}/${url}")
        httpClient.setHeaders(Accept: 'application/json')
        httpClient.request(GET, JSON) { req ->            
            response.success = { resp, json ->
                assert Status.SUCCESS.matches(resp.statusLine.statusCode)
                payload = json
            }
        }
        payload
    }

    static def executeHTTPPostBody(String url, Map params) {
        def payload
        def hostport = Config.getInstance().get("host_port")
        def httpClient = new HTTPBuilder("http://${hostport}/${url}")
        httpClient.setHeaders(Accept: 'application/json')
        httpClient.request(POST, JSON) { req ->
            body = params
            response.success = { resp, json ->
                assert Status.SUCCESS.matches(resp.statusLine.statusCode)
                payload = json
            }
        }
        payload
    }
        
}
