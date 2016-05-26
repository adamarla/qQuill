package com.gradians.pipeline.tag

import java.awt.Component

import java.nio.file.Path

import com.gradians.pipeline.data.Asset
import com.gradians.pipeline.data.AssetClass
import com.gradians.pipeline.data.Skill
import com.gradians.pipeline.edit.TeXHelper
import com.gradians.pipeline.util.Config

import groovy.json.JsonSlurper
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

    Config config
    ISkillLibClient client
    SwingBuilder sb
    SkillRenderer renderer

    public SkillLibrary(ISkillLibClient client) {
        this.client = client
        config = Config.getInstance()
        loadAssets()
    }
    
    def launch(int chapterId, int[] skillId) {
        def chapter = [chapters.find { c -> c.id == chapterId }, NO_CHAPTER, NO_CHAPTER]
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
                
                (0..2).each { idx ->
                    def order = idx == 0 ? "Primary" : ((idx == 1) ? "Secondary" : "Tertiary") 
                    vbox(border: BorderFactory.createTitledBorder("${order} Skill"),
                        constraints: gbc(gridy: idx, weightx: 1, fill: HORIZONTAL)) {
                        comboBox(id: "cbChapters${idx}", items: chapters, selectedItem: chapter[idx])
                        comboBox(id: "cbSkills${idx}", model: getSkillList(chapter[idx]),
                            renderer: renderer, maximumRowCount: 5)
                    }    
                }
                
                panel(constraints: gbc(gridy: 3, weightx: 1, fill: HORIZONTAL)) {
                    button(id: 'btnSelectSkill', text: 'Select',
                        actionPerformed: {
                            sb.dlgSkills.modal = false
                            
                            if (!sb.cbChapters0.selectedItem.equals(NO_CHAPTER) &&
                                sb.cbSkills0.selectedItem != null) {                                    
                                skill[0] = sb.cbSkills0.selectedItem.id
                            } else {
                                skill[0] = 0
                            }
                            
                            (1..2).each {
                                if (!sb."cbChapters${it}".selectedItem.equals(NO_CHAPTER) &&
                                    sb."cbSkills${it}".selectedItem != null) {
                                    // If previous skill is zero OR the same
                                    if (skill[it-1] == 0 || skill[it-1] == sb."cbSkills${it}".selectedItem.id) {
                                        skill[it-1] = sb."cbSkills${it}".selectedItem.id
                                        skill[it] = 0
                                    } else {
                                        skill[it] = sb."cbSkills${it}".selectedItem.id
                                    }
                                } else {
                                    skill[it] = 0
                                }
                            }
                            
                            client.applySelectedSkill(skill)
                            sb.dlgSkills.dispose() 
                        })
                    
                    button(text: 'Cancel', actionPerformed: { sb.dlgSkills.dispose() })
                }

            }
                
            (0..2).each { idx ->
                sb."cbChapters${idx}".actionPerformed = {
                    sb."cbSkills${idx}".model = getSkillList(sb."cbChapters${idx}".selectedItem)
                }    
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
        
        def items = config.getChapters()
        items.each { item ->
            Category c = new Category(item)
            chapters.add c
        }
        
        skills = new ArrayList<Skill>()
        Path skillCache = config.configPath.resolveSibling("skills.json")
        items = new JsonSlurper().parse(skillCache.toFile())
        items.each{ item ->
            def skill = Asset.getInstance(item)
            if (skill.isLoaded())
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

