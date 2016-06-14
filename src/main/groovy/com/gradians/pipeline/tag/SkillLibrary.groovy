package com.gradians.pipeline.tag

import java.awt.Component
import java.nio.file.Path

import com.gradians.pipeline.data.Asset
import com.gradians.pipeline.data.AssetClass
import com.gradians.pipeline.data.Skill
import com.gradians.pipeline.edit.TeXLabel
import com.gradians.pipeline.tex.SVGIcon;
import com.gradians.pipeline.tex.TeXHelper;
import com.gradians.pipeline.util.Config

import groovy.json.JsonSlurper
import groovy.swing.SwingBuilder

import javax.swing.BorderFactory
import javax.swing.ComboBoxModel
import javax.swing.DefaultComboBoxModel
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.ListCellRenderer
import javax.swing.border.Border
import javax.swing.border.EmptyBorder
import javax.swing.event.ListDataListener

import org.apache.batik.swing.JSVGCanvas

import static java.awt.BorderLayout.EAST
import static javax.swing.ListSelectionModel.SINGLE_SELECTION
import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
import static javax.swing.JList.VERTICAL
import static java.awt.GridBagConstraints.BOTH
import static java.awt.GridBagConstraints.HORIZONTAL
import static javax.swing.SwingConstants.LEFT
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
            dialog(id: 'dlgSkills', title: 'Skill Library', modal: true, preferredSize: [600, 400],
                defaultCloseOperation: DISPOSE_ON_CLOSE) {
                gridBagLayout()
                
                tabbedPane(id: 'tpSkill', border: A_BORDER, constraints: gbc(weighty: 1, fill: VERTICAL)) {
                    (0..2).each { idx ->
                        def order = idx == 0 ? "Primary" : ((idx == 1) ? "Secondary" : "Tertiary")
                        panel(name: order) {
                            vbox() {
                                comboBox(id: "cbChapters${idx}", items: chapters, selectedItem: chapter[idx],
                                    actionPerformed: {
                                        def skillList = getSkillList(sb."cbChapters${idx}".selectedItem)
                                        sb."cbSkills${idx}".model = skillList
                                        if (skillList.size)
                                            sb."cbSkills${idx}".selectedIndex = 0
                                    })
                                comboBox(id: "cbSkills${idx}", model: getSkillList(chapter[idx]),
                                    renderer: renderer, maximumRowCount: 5,
                                    actionPerformed: {
                                        def selectedSkill = sb."cbSkills${idx}".selectedItem
                                        sb.pnlSkillNote.removeAll()
                                        
                                        def drawable
                                        def tex = selectedSkill.xml.reason.tex
                                        if (!tex.@isImage.equals(true)) {
                                            drawable = new TeXLabel(tex.toString(), "Skill")
                                        } else {
                                            def path = selectedSkill.getDirPath().resolve(tex.toString())
                                            drawable = SkillLibrary.fileToJLabel(path)
                                        }

                                        sb.pnlSkillNote.add drawable
                                        sb.pnlSkillNote.revalidate()
                                        sb.pnlSkillNote.repaint()
                                    } )
                            }
                        }
                    }
                }
                    
                panel(border: A_BORDER, constraints: gbc(gridy: 1, weighty: 1, fill: VERTICAL)) {
                    button(id: 'btnSelectSkill', text: 'Apply', actionPerformed: { applySkills(skill) })
                    button(text: 'Cancel', actionPerformed: { sb.dlgSkills.dispose() })
                }
                                
                scrollPane(constraints: gbc(gridx: 1, gridheight: 2, weightx: 1, weighty: 1, fill: BOTH)) {
                    panel(id: 'pnlSkillNote')
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

    private def loadAssets() {
        chapters = new ArrayList<Category>()
        chapters.add NO_CHAPTER
        
        def config = Config.getInstance()
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
    
    private def applySkills(int[] skill) {
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
    }
    
    static def JLabel fileToJLabel(Path path) {        
        SVGIcon icon = new SVGIcon(path.toUri().toURL().toString())
        def label = new JLabel()
        label.icon = icon
        label
    }

    private List<Skill> skills
    private List<Category> chapters
    
    private final Category NO_CHAPTER = new Category([id: 0, name: "No Selection"])
    private final Border A_BORDER = BorderFactory.createCompoundBorder(
        BorderFactory.createEmptyBorder(2, 2, 0, 2),
        BorderFactory.createLineBorder(new java.awt.Color(0x9297a1)))    

}

class SkillRenderer implements ListCellRenderer {

    @Override
    public Component getListCellRendererComponent(JList list, Object value,
            int index, boolean isSelected, boolean cellHasFocus) {
        def drawable        
        if (value) {
            Skill skill = (Skill)value            
            def tex = skill.xml.render.tex            
            if (!tex.@isImage.equals(true)) {
                drawable = new TeXLabel(tex.toString(), "")
            } else {
                drawable = SkillLibrary.fileToJLabel(skill.getDirPath().resolve(tex.toString()))
            }
        } else {
            drawable = new TeXLabel("\\text{No Skills defined}", "")
        }
        
        if (isSelected) {
            drawable.setBackground(list.getSelectionBackground())
        } else {
            drawable.setBackground(list.getBackground())
        }
        drawable.border = new EmptyBorder(5, 5, 5, 5)
        drawable
    }

}

interface ISkillLibClient {

    def void applySelectedSkill(int[] skills)

    def Component getParentComponent()

}

