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
import com.gradians.pipeline.data.Question
import com.gradians.pipeline.data.Skill
import com.gradians.pipeline.data.Snippet
import com.gradians.pipeline.edit.Editor
import com.gradians.pipeline.edit.Renderer
import com.gradians.pipeline.tag.Clerk
import com.gradians.pipeline.tag.Tagger


class Driver {
    
    def static void main(String[] args) {
        
        def pwd = System.getProperty("user.dir")
        
        Options options = new Options()
        options.addOption(Option.builder("e").argName("edit").longOpt("edit").build())
        options.addOption(Option.builder("r").argName("render").longOpt("render").build())
        options.addOption(Option.builder("b").argName("bundle").longOpt("bundle").build())        
        
        try {
            
            CommandLine cl = (new DefaultParser()).parse(options, args)
            boolean editOnly = cl.hasOption('e')
            boolean renderOnly = cl.hasOption('r')
            boolean bundleOnly = cl.hasOption('b')
            
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
                AssetClass assetClass
                if (path.toString().contains("skill"))
                    assetClass = AssetClass.Skill
                else if (path.toString().contains("snippet"))
                    assetClass = AssetClass.Skill
                else
                    assetClass = AssetClass.Question                    
                Asset a = Asset.getInstance([path: path], assetClass).load()                                    
                if (renderOnly) {
                    (new Renderer(a)).toSVG()
                } else if (bundleOnly) {
                    (new Bundler(a)).bundle()
                } else if (editOnly) {
                    (new Editor(a)).launchGeneric()
                }
            }
        } catch (Exception e) {
            println e
        }
    }        
}

class Config {
    
    def config
    
    public Config() {
        def userHome = System.getProperty("user.home")
        config = (new ConfigSlurper()).parse(new File("${userHome}/.quill/config.groovy").toURI().toURL())
    }
    
    def get(String name) {
        config."${name}"
    }

}
