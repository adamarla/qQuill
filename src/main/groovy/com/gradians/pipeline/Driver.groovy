package com.gradians.pipeline

import groovy.swing.SwingBuilder

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.Option
import org.apache.commons.cli.Options
import org.apache.commons.cli.Option.Builder

import com.gradians.pipeline.data.Asset
import com.gradians.pipeline.data.AssetClass
import com.gradians.pipeline.edit.Editor
import com.gradians.pipeline.edit.Renderer
import com.gradians.pipeline.tag.Clerk
import com.gradians.pipeline.util.Config;


class Driver {
    
    def static void main(String[] args) {
        
        def pwd = System.getProperty("user.dir")
        
        Options options = new Options()
        options.addOption(Option.builder("e").argName("edit").longOpt("edit").build())
        options.addOption(Option.builder("r").argName("render").longOpt("render").build())        
        options.addOption(Option.builder("m").argName("mode").longOpt("mode").build())
        
        try {            
            CommandLine cl = (new DefaultParser()).parse(options, args)
            def edit, render, mode, list 
            if (cl.hasOption('e'))
                edit = true
            else if (cl.hasOption('r')) 
                render = true
            else if (cl.hasOption('m'))
                mode = true
            else
                list = true
            
            Config config = Config.getInstance()
            if (list) {
                (new Clerk()).go(true)
            } else if (mode) {
                if (!config.get("mode")) {
                    config.add("sandboxHostPort", "http://localhost:3000")
                    config.add("productionHostPort", "http://www.gradians.com")
                    config.add("mode", "sandbox")
                    config.commit()
                }
            
                def modes = ["sandbox", "production", "display"]
                def modeAction = cl.argList[0]
                if (!modeAction || !modes.contains(modeAction)) {
                    println "${modeAction} ???"
                    println "Please use one of the following options ${modes}"
                } else {
                    if (!modeAction.equals("display")) {
                        if (!config.get("mode").equals(modeAction)) {
                            config.add("mode", modeAction)
                            config.commit()
                        }
                    }
                    def modeOption = config.get("mode")
                    def hostport = config.getHostPort(modeOption)
                    println "Quill is in ${modeOption} mode connecting to ${hostport}"
                }
            } else {
                Path path = Paths.get(pwd)
                if (!cl.argList.empty)
                    path = path.resolve(cl.argList.get(0))
    
                if (!path.toString().contains("vault")) {
                    println "Locate path to a question folder"
                    return
                }
                
                assert Files.isDirectory(path)
                def assetClass = "Question"
                if (path.toString().contains("skill"))
                    assetClass = "Skill"
                else if (path.toString().contains("snippet"))
                    assetClass = "Snippet"
                    
                Path vault = Paths.get(config.getBankPath()).resolve("vault")
                path = vault.relativize(path)
                
                int id = Integer.parseInt(path.getFileName().toString())
                def map = [id: id, path: path.toString(), assetClass: assetClass]
                Asset a = Asset.getInstance(map)
                
                if (render) {
                    (new Renderer(a)).toSVG()
                } else if (edit) {
                    (new Editor(a)).launchGeneric()
                }
            }
        } catch (Exception e) {
            e.printStackTrace()
        }
    }        
}

