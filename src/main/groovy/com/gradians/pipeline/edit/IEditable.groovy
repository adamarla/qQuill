package com.gradians.pipeline.edit

interface IEditable {

    EditGroup[] getEditGroups()
    
    void updateModel(EditGroup[] panel)
    
}

class EditGroup {

    String title
    int skill    
    List<EditItem> editItems
    
    def EditGroup(String title, int skill = -1) {
        this.title = title
        this.skill = skill
        editItems = new ArrayList<EditItem>()
    }
    
    def addEditItem(EditItem c) {
        c.parent = this
        editItems.add(c)
    }
}

class EditItem {
    
    EditGroup parent
    boolean isTex
    String title = "", tex = "", image = ""
    int rows
    
    def EditItem(String title, String s, int rows, boolean isTex = true) {
        this.title = title
        this.isTex = isTex
        if (isTex)
            tex = s
        else
            image = s    
    }
    
}
