package com.gradians.pipeline.util

import com.gradians.pipeline.util.Config
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
        Config config = Config.getInstance()
        def hostport = config.getHostPort(config.get("mode"))
        def httpClient = new HTTPBuilder("${hostport}/${url}")
        httpClient.setHeaders(Accept: 'application/json')
        httpClient.request(GET, JSON) { req ->            
            response.success = { resp, json ->
                if (Status.SUCCESS.matches(resp.statusLine.statusCode))
                    payload = json
            }
        }
        payload
    }

    static def executeHTTPPostBody(String url, Map params) {
        def payload
        Config config = Config.getInstance()
        def hostport = config.getHostPort(config.get("mode"))
        def httpClient = new HTTPBuilder("${hostport}/${url}")
        httpClient.setHeaders(Accept: 'application/json')
        httpClient.request(POST, JSON) { req ->
            body = params
            response.success = { resp, json ->
                if (Status.SUCCESS.matches(resp.statusLine.statusCode))
                    payload = json
            }
        }
        payload
    }
        
}
