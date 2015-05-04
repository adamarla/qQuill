package com.gradians.pipeline

import groovy.swing.SwingBuilder
import javax.swing.JDialog
import javax.swing.JScrollPane
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.border.TitledBorder
import static java.awt.BorderLayout.EAST
import static java.awt.GridBagConstraints.HORIZONTAL
import static java.awt.GridBagConstraints.BOTH

class Preview {
    
    SwingBuilder sb
    
    def Preview(SwingBuilder sb) {
        this.sb = sb
    }
    
    def show(Question q) {
        def steps = q.getSteps()
        def choices = q.getChoices()
        def panel = sb.panel() {
            gridBagLayout()
            
            panel(id: 'pnlStatement', border: BorderFactory.createTitledBorder("Problem"),
                constraints: gbc(gridx: 0, gridy: 0, weightx: 1, weighty: 0, fill: HORIZONTAL)) {
                label(icon: q.getStatement())
            }
                
            panel(id: 'pnlChoices', border: BorderFactory.createTitledBorder("Choices"),
                constraints: gbc(gridx: 1, gridy: 0, weightx: 1, weighty: 0, fill: HORIZONTAL)) {
                vbox(constraints: EAST) {
                    choices.each {
                        label(icon: it)
                    }
                }
            }
                
            scrollPane(id: 'spSteps', border: BorderFactory.createTitledBorder("Steps"),
                constraints: gbc(gridx: 0, gridy: 1, gridwidth: 2, weightx: 2, weighty: 1, fill: BOTH),
                verticalScrollBarPolicy:JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED) {
                panel(constraints: EAST) {
                    vbox {
                        steps.each { step ->
                            label(icon: step)
                        }    
                    }
                }
            }
        }
        
        JDialog dialog = new JDialog(title: "Preview", size: [720, 480],
            defaultCloseOperation: JDialog.DISPOSE_ON_CLOSE)
        dialog.add panel
        dialog.setLocationByPlatform(true)
        dialog.setVisible(true)
    }

}
