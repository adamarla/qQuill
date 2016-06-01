package com.gradians.pipeline.util;

import groovy.json.JsonSlurper

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
        def quillDir = Paths.get("${userHome}/.quill")
        configPath = quillDir.resolve("config.groovy")
        if (Files.notExists(quillDir)) {
            Files.createDirectory(quillDir)
            Files.createFile(configPath)
            initialize()
        } else {
            config = new ConfigSlurper().parse(configPath.toUri().toURL())        
        }
    }
    
    private def initialize() {
        config = new ConfigSlurper()
        config.sandbox.host_port = "http://localhost:3000"
        config.production.host_port = "http://www.gradians.com"
        config.general.mode = "production"
        commit()
    }
    
    void registerUser(int userId, String email, String role, String bankPath) {
        config.sandbox.bank_path = bankPath
        config.production.bank_path = bankPath
        config.general.user_id = userId
        config.general.email = email
        config.general.role = role
        commit()
    }
    
    boolean hasRegistered() {
        config.general.containsKey("user_id")
    }
    
    def getUser() {
        [id: config.general.user_id,
         email: config.general.email,
         role: config.general.role]
    }

    String getMode() {
        config.general.mode
    }
    
    void setMode(String mode) {
        config.general.mode = mode
    }
    
    String getBankPath() {
        def mode = getMode()
        config."${mode}".bank_path
    }
    
    String getHostPort() {
        def mode = getMode()
        config."${mode}".host_port
    }
    
    void addChapter(int id, String name) {
        config.chapter."c${id}" = name
    }
    
    void addAuthor(int id, String name) {
        config.author."a${id}" = name
    }
    
    String getChapter(int id) {
        config.chapter."c${id}"
    }
    
    String getAuthor(int id) {
        config.author."a${id}"
    }
    
    def getChapters() {
        config.chapter.collect {
            [ id: it.key.substring(1).toInteger(), name: it.value]
        }
    }
    
    void commit() {
        config.writeTo(new PrintWriter(configPath.toFile()))
    }
    
    private static Config theInstance

}
