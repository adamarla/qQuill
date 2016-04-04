package com.gradians.pipeline.editor

interface IEditable {

    Panel[] getPanels()
    
    void updateModel(Panel panel, int index)
    
}

class Panel {
    
    String title
    
    List<Component> components
    
    def Panel(String title) {
        this.title = title
        components = new ArrayList<Component>()
    }
    
    def addComponent(Component c) {
        components.add(c)
    }
}

class Component {
    
    boolean isTex    
    String title, tex, image
    int rows
    
    def Component(String title, String s, int rows, boolean isTex = false) {
        this.title = title
        this.image = isTex
        if (isTex)
            tex = s
        else
            image = s    
    }
    
}
