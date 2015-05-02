package com.gradians.pipeline

class TagLib {

    String[] gradeLevels, subjects, sources, topiks, labels, misc
    final String configFile = "/config.xml", delim = ","
    
    public TagLib() {
        def xmlStream = Question.class.getResourceAsStream(configFile)
        def config = new XmlParser().parse(xmlStream)
        gradeLevels = getTags(config.tagset[0])
        subjects = getTags(config.tagset[1])
        topiks = getTags(config.tagset[2])
        sources = getTags(config.tagset[3])
        labels = getTags(config.tagset[4])
        misc = getTags(config.tagset[5])
    }
    
    def getTags = { tagset -> tagset.text().split(delim).collect { it.toLowerCase().trim() } }
    
}
