package com.gradians.pipeline

import java.nio.file.Path

class TagLib {

    String[] concepts, qsn, part, subpart
    final String configFile = "config.xml", delim = ","
    
    public TagLib(Path bank) {
        def config = new XmlParser().parse(bank.resolve(configFile).toFile())
        concepts = getTags(config.tagset[0])
        qsn = getTags(config.tagset[1])
        part = getTags(config.tagset[2])
        subpart = getTags(config.tagset[3])        
    }
    
    def getTags = { tagset -> tagset.text().split(delim).collect { it.toLowerCase().trim() } }
    
}
