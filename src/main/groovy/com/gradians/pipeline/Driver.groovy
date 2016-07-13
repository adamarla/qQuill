package com.gradians.pipeline

import groovy.swing.SwingBuilder

import java.beans.MetaData.javax_swing_border_MatteBorder_PersistenceDelegate;
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import javax.swing.UIManager;

import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.Option
import org.apache.commons.cli.Options
import org.apache.commons.cli.Option.Builder

import com.gradians.pipeline.data.Asset
import com.gradians.pipeline.data.AssetClass
import com.gradians.pipeline.edit.Editor
import com.gradians.pipeline.tag.Clerk
import com.gradians.pipeline.tag.Pinger
import com.gradians.pipeline.tag.Registration
import com.gradians.pipeline.util.Config
import com.gradians.pipeline.util.Converter


class Driver {
    
    def static void main(String[] args) {
        def pwd = System.getProperty("user.dir")
        
        Options options = new Options()
        options.addOption(Option.builder("e").argName("edit").longOpt("edit").build())
        options.addOption(Option.builder("r").argName("render").longOpt("render").build())        
        options.addOption(Option.builder("m").argName("mode").longOpt("mode").build())
        options.addOption(Option.builder("c").argName("convert").longOpt("convert").build())
        options.addOption(Option.builder("p").argName("ping").longOpt("ping").build())
        
        def edit, render, mode, ping, list, convert 
        try {
            
            CommandLine cl = (new DefaultParser()).parse(options, args)
            if (cl.hasOption('e'))
                edit = true
            else if (cl.hasOption('r')) 
                render = true
            else if (cl.hasOption('m'))
                mode = true
            else if (cl.hasOption('c'))
                convert = true
            else if (cl.hasOption('p'))
                ping = true
            else
                list = true
            
            if (edit || convert || list)
                UIManager.lookAndFeel = 'javax.swing.plaf.nimbus.NimbusLookAndFeel'
            
            Config config = Config.getInstance()
            if (!config.hasRegistered())
                new Registration(config).launch()
                
            if (!config.hasRegistered())
                return
            
            if (mode) {
                def modes = ["sandbox", "production", "display"]
                def modeAction = cl.argList[0]
                if (!modeAction || !modes.contains(modeAction)) {
                    println "${modeAction} ???"
                    println "Please use one of the following options ${modes}"
                } else {
                    if (!modeAction.equals("display")) {
                        if (!config.getMode().equals(modeAction)) {
                            config.setMode(modeAction)
                            config.commit()
                        }
                    }
                    def modeOption = config.getMode()
                    def hostport = config.getHostPort()
                    println "Quill is in ${modeOption} mode connecting to ${hostport}"
                }
            } else {                             
                if (list) {
                    (new Clerk()).go(true)
                } else {
                    Path path = Paths.get(pwd)                
                    if (!cl.argList.empty)
                        path = path.resolve(cl.argList.get(0)).normalize()
        
                    if (!path.toString().contains("vault")) {
                        println "Locate path to a question folder"
                        return
                    }
                    
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
                    
                    if (edit) {
                        (new Editor(a)).launch()
                    } else if (convert) {
                        (new Converter(a)).convert()
                        (new Editor(a)).launch()
                    } else {
                        if (render) {
                            a.toSVG()
                        }                        
                        if (ping) {
                            new Pinger(a).ping()
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace()
            if (!render && !ping)
                javax.swing.JOptionPane.showMessageDialog(null,
                    "${e.getMessage()}\nSend stack trace to akshay@gradians.com", 
                    "Ooops", javax.swing.JOptionPane.ERROR_MESSAGE)
        }
    }        
}

