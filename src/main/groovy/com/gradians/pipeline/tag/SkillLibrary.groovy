package com.gradians.pipeline.tag

import java.awt.Component;

import com.gradians.pipeline.data.Asset
import com.gradians.pipeline.data.AssetClass
import com.gradians.pipeline.data.Skill
import com.gradians.pipeline.edit.Editor
import com.gradians.pipeline.edit.TeXHelper
import com.gradians.pipeline.edit.TeXLabel

import groovy.swing.SwingBuilder

import javax.swing.ComboBoxModel
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.ListCellRenderer
import javax.swing.event.ListDataListener

import static java.awt.BorderLayout.CENTER
import static javax.swing.ListSelectionModel.SINGLE_SELECTION
import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
import static javax.swing.JList.VERTICAL
import static javax.swing.WindowConstants.DISPOSE_ON_CLOSE


class SkillLibrary {
    
    Clerk clerk    
    SwingBuilder sb
    Category chapter
    List<Skill> skills
    
    public SkillLibrary(Category chapter, Clerk clerk) {
        this.chapter = chapter
        this.clerk = clerk
    }
    
    def launch(List<Skill> skills) {
        def Skill selected = skills[0]
        def renderer = new SkillRenderer()
        sb = new SwingBuilder()
        sb.edt {
            lookAndFeel 'nimbus'
            frame(id: 'frmClerk', title: "Skill Library - ${chapter.name}",
                size: [480, 120], show: true, defaultCloseOperation: DISPOSE_ON_CLOSE) {
                panel(constraints: CENTER) {
                    comboBox(id: 'listSkills', renderer: renderer,
                        maximumRowCount: 5, preferredSize: [360, 100], 
                        model: [
                            setSelectedItem: { Object anItem -> selected = anItem},
                            getSelectedItem: { selected },
                            getSize: { skills.size() },
                            getElementAt: { skills[it] },
                            addListDataListener: {}] as ComboBoxModel)
                    button(text: 'Apply', preferredSize: [100, 100],
                        actionPerformed: {
                            def asset = clerk.createNewAsset(AssetClass.Snippet, chapter, 
                                new Category([id: selected.id]))
                            new Editor(asset).launchGeneric()
                            dispose()
                        })
                }
            }
        }
    }    
}

class SkillRenderer extends JLabel implements ListCellRenderer {

    @Override
    public Component getListCellRendererComponent(JList list, Object value,
        int index, boolean isSelected, boolean cellHasFocus) {        
        if (isSelected) {
            setBackground(list.getSelectionBackground())
        } else {
            setBackground(list.getBackground())
        }
        Skill skill = ((Skill)value).load()
        this.icon = TeXHelper.createIcon(skill.texStatement, 15, false)
        return this
    }
    
}

