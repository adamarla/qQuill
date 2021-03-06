package com.gradians.pipeline.edit

import java.nio.file.Path

interface IEditable {

    EditGroup[] getEditGroups()
    
    void updateModel(EditGroup[] panel)
    
    void save()
    
    Path getDirPath()
}

class EditGroup {

    IEditable parent
    String title
    int[] skills
    List<EditItem> editItems
    
    def EditGroup(String title, IEditable parent) {
        this.title = title
        this.parent = parent
        editItems = new ArrayList<EditItem>()
    }
    
    def addEditItem(EditItem ei) {
        ei.parent = this
        editItems.add(ei)
    }
    
    def addEditItem(String title, String s, int rows, boolean isImage = false) {
        EditItem ei = new EditItem([title: title, text: s, rows: rows, isImage: isImage])
        addEditItem(ei)
    }
}

class EditItem {
    
    EditGroup parent
    boolean isImage
    String title = "", text = ""
    int rows
    
    public def getXmlNode = {
        def map = isImage ? [isImage: true] : [:]
        tex(map: map, text)
    }
}
