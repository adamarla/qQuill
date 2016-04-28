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

import groovy.swing.SwingBuilder

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

    def launch(int chapterId, int skillId = -1) {
        Category chapter = chapters.find { it.id == chapterId }
        renderer = new SkillRenderer()
        sb = new SwingBuilder()
        sb.edt {
            lookAndFeel 'nimbus'
            dialog(id: 'dlgSkills', title: 'Skill Library', modal: true,
            defaultCloseOperation: DISPOSE_ON_CLOSE) {
                gridBagLayout()
                
                comboBox(id: 'cbChapters', items: chapters, selectedItem: chapter,
                    constraints: gbc(weightx: 1, fill: HORIZONTAL),
                    actionPerformed: {
                        sb.cbSkills.model = getSkillList(sb.cbChapters.selectedItem)
                    })
                
                comboBox(id: 'cbSkills', model: getSkillList(chapter),
                    constraints: gbc(gridy: 1, weightx: 1, fill: BOTH),
                    renderer: renderer, maximumRowCount: 5)
                
                panel(constraints: gbc(gridy: 2, weightx: 1, fill: HORIZONTAL)) {
                    button(text: 'Select',
                        actionPerformed: {
                            sb.dlgSkills.modal = false
                            client.applySelectedSkill(sb.cbSkills.selectedItem)
                            sb.dlgSkills.dispose() })
                    button(text: 'Cancel', actionPerformed: { sb.dlgSkills.dispose() })
                }
            }
            
            if (skillId != -1) {
                def model = sb.cbSkills.model
                for (int i = 0; i < model.getSize(); i++) {
                    Skill s = model.getElementAt(i)
                    if (s.id == skillId) {
                        model.selectedItem = s
                    }
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
        def items = Network.executeHTTPGet("chapter/list")
        items.eachWithIndex{ item, i ->
            Category c = new Category(item)
            chapters.add c
        }

        skills = new ArrayList<Skill>()
        chapters.each { chapter ->
            def json = Network.executeHTTPGet("skills/list?c=${chapter.id}")
            json.skills.each{ item -> skills << Asset.getInstance(item) }
        }
    }

    private List<Skill> skills
    private List<Category> chapters

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
        this.border = new EmptyBorder(5, 5, 5, 5)
        return this
    }

}

interface ISkillLibClient {

    def void applySelectedSkill(Skill skill)

    def Component getParentComponent()

}

