package com.gradians.pipeline

import groovy.swing.SwingBuilder

import javax.swing.JDialog
import javax.swing.JScrollPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.BorderFactory
import javax.swing.border.TitledBorder

import static java.awt.BorderLayout.EAST
import static java.awt.GridBagConstraints.HORIZONTAL
import static java.awt.GridBagConstraints.BOTH

import org.scilab.forge.jlatexmath.TeXConstants
import org.scilab.forge.jlatexmath.TeXFormula
import org.scilab.forge.jlatexmath.TeXIcon

class Preview {
    
    SwingBuilder sb
    
    def Preview(SwingBuilder sb) {
        this.sb = sb
    }
    
    def display(Question q) {
        def steps = q.steps
        def choices = q.choices
        
        def panel = sb.panel() {
            gridBagLayout()
            
            panel(id: 'pnlStatement', border: BorderFactory.createTitledBorder("Problem"),
                constraints: gbc(gridx: 0, gridy: 0, weightx: 1, weighty: 0, fill: HORIZONTAL)) {
                label(icon: this.teXToIcon(q.statement.tex))
            }
                
            panel(id: 'pnlChoices', border: BorderFactory.createTitledBorder("Choices"),
                constraints: gbc(gridx: 1, gridy: 0, weightx: 1, weighty: 0, fill: HORIZONTAL)) {
                vbox(constraints: EAST) {
                    choices.texs.eachWithIndex { tex, i ->
                        label(icon: this.teXToIcon(tex))
                    }
                }
            }
                
            scrollPane(id: 'spSteps', border: BorderFactory.createTitledBorder("Steps"),
                constraints: gbc(gridx: 0, gridy: 1, gridwidth: 2, weightx: 2, weighty: 1, fill: BOTH),
                verticalScrollBarPolicy:JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED) {
                panel(constraints: EAST) {
                    vbox {
                        steps.each { step ->
                            label(icon: teXToIcon(step.context))
                            panel() {
                                label(icon: teXToIcon(step.texWrong))
                                label(icon: teXToIcon(step.texRight))                                
                            }
                            label(icon: this.teXToIcon(step.reason))
                        }    
                    }
                }
            }
        }
        
        JDialog dialog = new JDialog(title: "Preview", size: [720, 480],
            defaultCloseOperation: JDialog.DISPOSE_ON_CLOSE)
        dialog.add(panel)
        dialog.setLocationByPlatform(true)
        dialog.setVisible(true)
    }    
    
    private TeXIcon teXToIcon(String tex) {
        TeXFormula formula
        TeXIcon texIcon
        tex = tex.replace("\n", "\\\\").replace("\\qquad", "\\quad\\quad")
        try {
            formula = new TeXFormula(tex)
        } catch (Exception e) {
            def s = splitEqually(e.getMessage()).join("\\\\")
            formula = new TeXFormula("\\text{${s}}")
        }
        texIcon = formula.createTeXIcon(TeXConstants.STYLE_DISPLAY, 15, TeXFormula.SANSSERIF)            
    }
    
    private def String[] splitEqually(String s) {
        int n = (s.length() / WIDTH) + 1
        def parts = new String[n]
        for (int i = 0; i < n; i++) {
            parts[i] = s.substring(i*WIDTH,
                i == n-1 ? s.length() : (i+1)*WIDTH)
        }
        parts
    }
    
    private final int WIDTH = 35
    
}
