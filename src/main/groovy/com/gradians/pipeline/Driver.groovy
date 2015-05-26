package com.gradians.pipeline

import java.nio.file.Path

import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.Option
import org.apache.commons.cli.Options
import org.apache.commons.cli.Option.Builder


class Driver {
    
    def static void main(String[] args) {
        
        def pwd = System.getProperty("user.dir")
        if (pwd.endsWith("Qquill")) {
            pwd = "/home/adamarla/work/gutenberg/vault/2/3kh/s4a0m"
        } else if (!pwd.contains("vault")) {
            println "Run from within a Question folder in vault"
            return
        }
        
        Options options = new Options()
        options.addOption(Option.builder("r").argName("render").longOpt("render").build())
        options.addOption(Option.builder("t").argName("tag").longOpt("tag").build())
        
        CommandLine cl = (new DefaultParser()).parse(options, args)        
        boolean renderOnly = cl.hasOption('r')
        boolean tagOnly = cl.hasOption('t')
        
        Path qpath = (new File(pwd)).toPath()
        Path bank = qpath.getParent().getParent().getParent().getParent()
        Path catalog = bank.resolve("common").resolve("catalog")
        
        try {
            Question q = new Question(qpath, catalog)
            if (renderOnly) {
                (new Renderer(q, 12)).toSVG(q.qpath)
            } else if (tagOnly) {
                (new Tagger(q, catalog)).go()
            } else {
                (new Editor(q)).launch()
            }        
        } catch (Exception e) {
            println e
        }
        
    }
        
}
