package com.gradians.pipeline

class Driver {
    
    def static void main(String[] args) {
        
        //def path = "/home/adamarla/work/gutenberg/vault/2/3kh/s4a0m" + "/question.xml"
        def pwd = System.getProperty("user.dir")
        if (!pwd.contains("vault")) {            
            println "Run from within a Question folder in vault"
        }
                
        Question q = new Question(pwd)
        Catalog c = new Catalog()
        Network n = new Network()
        TagLib tl = new TagLib()
        
        UI ui = new UI(c, n, tl, q)
        ui.go()
    }
        
}
