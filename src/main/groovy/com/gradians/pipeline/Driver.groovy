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


class Driver {
    
    def static void main(String[] args) {
        
        def pwd = System.getProperty("user.dir")
        
        Options options = new Options()
        options.addOption(Option.builder("e").argName("edit").longOpt("edit").build())
        options.addOption(Option.builder("b").argName("bundle").longOpt("bundle").build())        
        
        try {
            
            CommandLine cl = (new DefaultParser()).parse(options, args)
            boolean editOnly = cl.hasOption('e')
            boolean renderOnly = cl.hasOption('r')
            
            if (cl.argList.size() == 0) {
                (new Clerk()).go(true)
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
                    
                Config config = Config.getInstance()
                def bankPathString = config.get("bank_path")
                Path vault = Paths.get(bankPathString).resolve("vault")
                path = vault.relativize(path)
                
                def map = [path: path.toString(), assetClass: assetClass]
                Asset a = Asset.getInstance(map).load()
                
                if (renderOnly) {
                    (new Renderer(a)).toSVG()
                } else if (editOnly) {
                    (new Editor(a)).launchGeneric()
                }
            }
        } catch (Exception e) {
            e.printStackTrace()
        }
    }        
}

