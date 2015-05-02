package com.gradians.pipeline

import java.nio.file.Path

class Driver {
    
    def static void main(String[] args) {
        
        //def path = "/home/adamarla/work/gutenberg/vault/2/3kh/s4a0m" + "/question.xml"
        def pwd = System.getProperty("user.dir")
        if (!pwd.contains("vault")) {            
            println "Run from within a Question folder in vault"
        }
                
        Path bank = (new File(pwd)).toPath().resolve("../../../../common/catalog")
        println bank        
        Catalog c = new Catalog(bank)
        Network n = new Network()
        TagLib tl = new TagLib()        
        Question q = new Question(pwd)
        
        UI ui = new UI(c, n, tl, q)
        ui.go()
    }
        
}
