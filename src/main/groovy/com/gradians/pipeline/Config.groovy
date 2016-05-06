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
        if (Config.theInstance == null) {
            Config.theInstance = new Config()
        }
        Config.theInstance
    }
    
    private Config() {        
        def userHome = System.getProperty("user.home")
        configPath = Paths.get("${userHome}/.quill/config.groovy")
        config = new ConfigSlurper().parse(configPath.toUri().toURL())
    }
    
    def get(String key) {
        config."${key}"
    }
    
    String getHostPort(String mode) {
        config."${mode}HostPort"
    }
    
    void add(String key, String value) {
        config."${key}" = value
    }
    
    void addChapter(int id, String name) {
        config."c${id}" = name
    }
    
    void addAuthor(int id, String name) {
        config."a${id}" = name
    }
    
    String getChapter(int id) {
        config."c${id}"
    }
    
    String getAuthor(int id) {
        config."a${id}"
    }
    
    void commit() {
        config.writeTo(new PrintWriter(configPath.toFile()))
    }
    
    private static Config theInstance

}
