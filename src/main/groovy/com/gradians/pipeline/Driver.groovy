package com.gradians.pipeline

import groovy.swing.SwingBuilder

import java.nio.file.Files
import java.nio.file.Path

import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.Option
import org.apache.commons.cli.Options
import org.apache.commons.cli.Option.Builder

import com.gradians.pipeline.data.Question;
import com.gradians.pipeline.editor.Editor;
import com.gradians.pipeline.editor.Renderer;
import com.gradians.pipeline.tagger.Tagger;


class Driver {
    
    def static void main(String[] args) {
        
        def pwd = System.getProperty("user.dir")
        if (pwd.endsWith("Qquill")) {
            pwd = "/home/adamarla/work/gutenberg/vault/2/9ai/9bfrg"
            args = ["-e"]
        }
        
        Options options = new Options()
        options.addOption(Option.builder("e").argName("edit").longOpt("edit").build())
        options.addOption(Option.builder("r").argName("render").longOpt("render").build())
        options.addOption(Option.builder("p").argName("preview").longOpt("preview").build())
        options.addOption(Option.builder("t").argName("tag").longOpt("tag").build())
        options.addOption(Option.builder("b").argName("bundle").longOpt("bundle").build())        
        
        try {
            
            CommandLine cl = (new DefaultParser()).parse(options, args)
            boolean editOnly = cl.hasOption('e')
            boolean renderOnly = cl.hasOption('r')
            boolean tagOnly = cl.hasOption('t')
            boolean bundleOnly = cl.hasOption('b')
            boolean previewOnly = cl.hasOption('p')
            
            Path path = (new File(pwd)).toPath()
            if (!cl.argList.empty)
                path = path.resolve(cl.argList.get(0))

            if (!path.toString().contains("vault")) {
                println "Run from within vault or locate path to vault"
                return
            }
            assert Files.isDirectory(path)
                
            if (tagOnly) {
                (new Tagger(path)).go(true)
            } else {
                Question q = new Question(path)
                if (renderOnly) {
                    (new Renderer(q)).toSVG()
                } else if (bundleOnly) {
                    (new Bundler(q)).bundle()
                } else if (previewOnly) {
                    (new Renderer(q)).toSwing(true)
                } else if (editOnly) {
                    (new Editor(q)).launchGeneric()
                }
            }
            
        } catch (Exception e) {
            println e
        }
        
    }
        
}
