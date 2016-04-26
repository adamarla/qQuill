package com.gradians.pipeline.edit

interface IEditable {

    Panel[] getPanels()
    
    void updateModel(Panel[] panel)
    
}

class Panel {

    String title
    int skill
    
    List<Component> components
    
    def Panel(String title, int skill = -1) {
        this.title = title
        this.skill = skill
        components = new ArrayList<Component>()
    }
    
    def addComponent(Component c) {
        c.parent = this
        components.add(c)
    }
}

class Component {
    
    Panel parent
    boolean isTex
    String title = "", tex = "", image = ""
    int rows
    
    def Component(String title, String s, int rows, boolean isTex = true) {
        this.title = title
        this.isTex = isTex
        if (isTex)
            tex = s
        else
            image = s    
    }
    
}
