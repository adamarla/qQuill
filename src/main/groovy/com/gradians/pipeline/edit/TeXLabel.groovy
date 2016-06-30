package com.gradians.pipeline.edit

import java.awt.Color
import java.awt.Graphics

import javax.swing.JLabel
import javax.swing.border.Border
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder
import javax.swing.border.TitledBorder
import javax.swing.BorderFactory

import com.gradians.pipeline.tex.SVGIcon
import com.gradians.pipeline.tex.TeXHelper;

import static java.awt.Color.ORANGE
import static java.awt.Color.RED


/**
 * The purpose of this class is to add a title to a piece of TeX
 * and a marker to suggest optimal width. No other purpose.
 * 
 * @author adamarla
 *
 */

class TeXLabel extends JLabel {
    
    public TeXLabel(EditItem item) {
        if (item.isImage)
            icon = new SVGIcon(item.parent.parent.getDirPath().resolve(item.text).toUri().toURL().toString())
        else
            icon = TeXHelper.createIcon(item.text, 15, false)
        setBorder(item.title)
    }
        
    /**
     * Important method: Keeps preview updated as the user 
     * types LaTeX
     */
    public void updateTex(String tex) {
        icon = TeXHelper.createIcon(tex, 15, false)
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
        }
        super.setBorder(BorderFactory.createCompoundBorder(b,
            new EmptyBorder(0, 0, 0, 0)))
    }
    
    private final int ERROR_WIDTH = 335
    
}

