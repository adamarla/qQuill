package com.gradians.pipeline.tag

import java.awt.Component
import java.util.EnumSet
import java.util.HashMap
import java.util.List

import com.gradians.pipeline.data.Asset
import com.gradians.pipeline.data.AssetClass
import com.gradians.pipeline.data.AssetState
import com.gradians.pipeline.data.Skill
import com.gradians.pipeline.edit.TeXHelper
import com.gradians.pipeline.util.Network;

import groovy.swing.SwingBuilder

import javax.swing.BorderFactory
import javax.swing.ComboBoxModel
import javax.swing.DefaultComboBoxModel
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.ListCellRenderer
import javax.swing.border.EmptyBorder
import javax.swing.event.ListDataListener

import static java.awt.BorderLayout.EAST
import static javax.swing.ListSelectionModel.SINGLE_SELECTION
import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
import static javax.swing.JList.VERTICAL
import static java.awt.GridBagConstraints.BOTH
import static java.awt.GridBagConstraints.HORIZONTAL
import static javax.swing.WindowConstants.DISPOSE_ON_CLOSE


class SkillLibrary {

    ISkillLibClient client
    SwingBuilder sb
    SkillRenderer renderer

    public SkillLibrary(ISkillLibClient client) {
        this.client = client
        loadAssets()
    }

    def launch(int chapterId, int[] skillId) {
        def chapter = [NO_CHAPTER, NO_CHAPTER, NO_CHAPTER]
        def skill = new int[3]
        
        skillId.eachWithIndex { it, i ->
            if (it != 0) {
                skill[i] = it
                Skill s = skills.find { s -> s.id == it }
                chapter[i] = chapters.find { c -> c.id == s.chapterId }
            }
        }
        
        renderer = new SkillRenderer()
        sb = new SwingBuilder()
        sb.edt {
            lookAndFeel 'nimbus'
            dialog(id: 'dlgSkills', title: 'Skill Library', modal: true,
                preferredSize: [360, 480],
                defaultCloseOperation: DISPOSE_ON_CLOSE) {
                gridBagLayout()
                
                vbox(border: BorderFactory.createTitledBorder("Primary Skill"),
                    constraints: gbc(weightx: 1, fill: HORIZONTAL)) {
                    label(text: chapter[0].name)
                    comboBox(id: 'cbSkills0', model: getSkillList(chapter[0]),
                        renderer: renderer, maximumRowCount: 5)    
                }                
                
                vbox(border: BorderFactory.createTitledBorder("Secondary Skill"),
                    constraints: gbc(gridy: 1, weightx: 1, fill: HORIZONTAL)) {
                    comboBox(id: 'cbChapters1', items: chapters, selectedItem: chapter[1])
                    comboBox(id: 'cbSkills1', model: getSkillList(chapter[1]),
                        renderer: renderer, maximumRowCount: 5)
                }
                
                vbox(border: BorderFactory.createTitledBorder("Tertiary Skill"),
                    constraints: gbc(gridy: 2, weightx: 1, fill: HORIZONTAL)) {
                    comboBox(id: 'cbChapters2', items: chapters, selectedItem: chapter[2])
                    comboBox(id: 'cbSkills2', model: getSkillList(chapter[2]),
                        renderer: renderer, maximumRowCount: 5)
                }
                
                panel(constraints: gbc(gridy: 3, weightx: 1, fill: HORIZONTAL)) {
                    button(id: 'btnSelectSkill', text: 'Select',
                        actionPerformed: {
                            sb.dlgSkills.modal = false
                            skill[0] = sb.cbSkills0.selectedItem != null ?
                                sb.cbSkills0.selectedItem.id : 0
                            (1..2).each {
                                if (!sb."cbChapters${it}".selectedItem.equals(NO_CHAPTER) &&
                                    sb."cbSkills${it}".selectedItem != null) {
                                    skill[it] = sb."cbSkills${it}".selectedItem.id
                                } else {
                                    skill[it] = 0
                                }
                            }
                            client.applySelectedSkill(skill)
                            sb.dlgSkills.dispose() })
                    button(text: 'Cancel', actionPerformed: { sb.dlgSkills.dispose() })
                }

            }
                
            sb.cbChapters1.actionPerformed = {
                sb.cbSkills1.model = getSkillList(sb.cbChapters1.selectedItem)
            }
            sb.cbChapters2.actionPerformed = {
                sb.cbSkills2.model = getSkillList(sb.cbChapters2.selectedItem)
            }
            
            skill.eachWithIndex { s, i ->
                if (s != 0) {
                    sb."cbSkills${i}".selectedItem = skills.find{ it.id == s }                 
                }
            }

            sb.dlgSkills.getContentPane().setBorder(new EmptyBorder(5, 5, 5, 5))
            sb.dlgSkills.pack()
            sb.dlgSkills.setLocationRelativeTo(client.getParentComponent())
            sb.dlgSkills.visible = true
            
        }
    }

    private DefaultComboBoxModel getSkillList(Category chapter) {
        def subSkills = new Vector<Skill>(skills.findAll { it.chapterId == chapter.id })
        new DefaultComboBoxModel<Skill>(subSkills)
    }

    private def loadAssets = {
        chapters = new ArrayList<Category>()
        chapters.add NO_CHAPTER
        def items = Network.executeHTTPGet("chapter/list")
        items.eachWithIndex{ item, i ->
            Category c = new Category(item)
            chapters.add c
        }
        
        skills = new ArrayList<Skill>()
        items = Network.executeHTTPGet("skills/all")
        items.each{ item ->
            def skill = Asset.getInstance(item)
            if (skill.xml)
                skills << skill 
        }
    }

    private List<Skill> skills
    private List<Category> chapters
    
    private final Category NO_CHAPTER = new Category([id: 0, name: "No Selection"])

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
        if (value) {
            def skill = (Skill)value
            this.icon = TeXHelper.createIcon(skill.xml.render.tex.toString(), 15, false)
        } else {
            this.icon = TeXHelper.createIcon("\\text{No Skills defined}", 15, false)
        }
        this.border = new EmptyBorder(5, 5, 5, 5)
        return this
    }

}

interface ISkillLibClient {

    def void applySelectedSkill(int[] skills)

    def Component getParentComponent()

}

