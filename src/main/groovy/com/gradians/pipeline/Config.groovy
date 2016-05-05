package com.gradians.pipeline;

import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.nio.charset.StandardCharsets


class Config {
    
    ConfigObject config
    Path configPath
    
    public static Config getInstance() {
        if (theInstance == null) {
            theInstance = new Config()
        }
        theInstance
    }
    
    private Config() {        
        def userHome = System.getProperty("user.home")
        configPath = Paths.get("${userHome}/.quill/config.groovy")
        config = new ConfigSlurper().parse(configPath.toUri().toURL())
    }
    
    def get(String name) {
        config."${name}"
    }
    
    def addChapter(int id, String name) {
        config.put("chapter.c${id}".toString(), name)
    }
    
    def addAuthor(int id, String name) {
        config.put("author.a${id}".toString(), name)
    }
    
    def getChapter(int id) {
        config."chapter.c${id}"
    }
    
    def getAuthor(int id) {
        config."author.a${id}"
    }
    
    def commit() {
        config.writeTo(Files.newBufferedWriter(configPath,
            StandardCharsets.UTF_8, StandardOpenOption.WRITE))
    }
    
    private static Config theInstance

}
