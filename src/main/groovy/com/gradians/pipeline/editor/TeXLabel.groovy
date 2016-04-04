package com.gradians.pipeline.editor

import java.awt.Color
import java.awt.Graphics

import javax.swing.JLabel
import javax.swing.border.Border
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder
import javax.swing.border.TitledBorder
import javax.swing.BorderFactory

import static java.awt.Color.ORANGE
import static java.awt.Color.RED

class TeXLabel extends JLabel {
    
    public TeXLabel(String text, String title) {
        icon = TeXHelper.createIcon(text, 15, false)
        setBorder(title)
    }
        
    @Override
    public void setText(String text) {
        icon = TeXHelper.createIcon(text, 15, false)
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g)
        if (icon != null) {
            if (icon.iconWidth > ERROR_WIDTH) {
                g.setColor(Color.BLUE)
                g.drawLine(ERROR_WIDTH, 0, ERROR_WIDTH, icon.iconHeight)
            }
        }
    }
    
    private void setBorder(String title) {
        TitledBorder b = BorderFactory.createTitledBorder(title)
        if (icon.iconWidth > ERROR_WIDTH) {
            int overflowPcnt = (icon.iconWidth - ERROR_WIDTH)*100/ERROR_WIDTH
            b.title += "(${overflowPcnt}%)"
            b.setBorder(new LineBorder(RED, (int)overflowPcnt/10+1))
        } else if (icon.iconWidth > WARNING_WIDTH) {
            b.setBorder(new LineBorder(ORANGE))
        }
        super.setBorder(BorderFactory.createCompoundBorder(b,
            new EmptyBorder(0, 0, 0, 0)))
    }
    
    private final int ERROR_WIDTH = 335, WARNING_WIDTH = 310
    
}

