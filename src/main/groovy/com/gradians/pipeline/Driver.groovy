package com.gradians.pipeline

import java.nio.file.Path

class Driver {
    
    def static void main(String[] args) {
        
        // def path = "/home/adamarla/work/gutenberg/vault/2/3kh/s4a0m" + "/question.xml"
        def pwd = System.getProperty("user.dir")
        if (pwd.endsWith("Qquill")) {
            pwd = "/home/adamarla/work/gutenberg/vault/2/3kh/s4a0m"
        } else if (!pwd.contains("vault")) {
            println "Run from within a Question folder in vault"
            return
        }
        
        Path qpath = (new File(pwd)).toPath() 
        Path bank = qpath.resolve("../../../../common/catalog")
        
        try {
            Network n = new Network()
            Catalog c = new Catalog(bank)
            TagLib tl = new TagLib(bank)
            Question q = new Question(qpath, bank)
            
            UI ui = new UI(c, n, tl, q)
            ui.go()
        } catch (Exception e) {
            println e
        }
    }
        
}
