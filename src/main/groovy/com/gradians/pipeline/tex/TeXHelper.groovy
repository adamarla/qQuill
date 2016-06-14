package com.gradians.pipeline.tex;

import java.awt.Component
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.font.FontRenderContext
import java.awt.geom.Rectangle2D

import javax.imageio.ImageIO
import javax.swing.Icon

import org.scilab.forge.jlatexmath.TeXFormula

import static java.awt.Color.BLACK
import static java.awt.Color.WHITE

import static org.scilab.forge.jlatexmath.TeXConstants.STYLE_DISPLAY

class TeXHelper {
        
    static {
        // TeXFormula.registerExternalFont(Character.UnicodeBlock.BASIC_LATIN, "Ubuntu")
        Map<String, String> map = TeXFormula.predefinedTeXFormulasAsString
        map.keySet().each { TeXFormula.get(it) }
        def ostream = TeXMacros.class.getClassLoader().getResourceAsStream("TeXMacros.xml")
        TeXFormula.addPredefinedCommands(ostream)
    }            
        
    public static Icon createIcon(String tex, int fontSize, boolean negative = false) {        
        Icon icon        
        String[] lines = tex.split("\n")
        def sb = new StringBuilder()
        
        boolean textMode = false
        for (int i = 0; i < lines.length; i++) {
            def line = lines[i]
            if (line.startsWith("%text")) {
                textMode = true
                continue
            } else if (line.startsWith("%")) {
                textMode = false
                continue
            }
            
            if (textMode)
                sb.append("\\text{${line}} \\\\\n")
            else
                sb.append("${line}\n")
        }
                
        TeXFormula formula
        try {
            formula = new TeXFormula(sb.toString())
            icon = formula.createTeXIcon(STYLE_DISPLAY, 15)
            icon.setForeground(negative ? WHITE : BLACK)
        } catch (Exception e) {
            icon = new DynamicIcon(TeXHelper.toPureTeX(tex))
        }
        icon
    }
    
    public static String toPureTeX(String tex) {
        String[] lines = tex.split("\n")
        def sw = new StringWriter()
        
        for (int i = 0; i < lines.length; i++) {
            def line = lines[i]
            if (line.startsWith("%text") || line.startsWith("%")) {
                continue
            }            
            sw.append("${line}\n")
        }
        sw.toString()
    }
    
}

class DynamicIcon implements Icon {
    String text
    int height, width
    Font font    
        
    public DynamicIcon(String text) {
        this.text = text
        font = new Font("Ubuntu", 0, 15)        
        FontRenderContext frc = new FontRenderContext()
        Rectangle2D bounds = font.getStringBounds(text, frc)
        width = (int)bounds.getWidth()
        height = (int)bounds.getHeight()
    }

    @Override
    public void paintIcon(Component c, Graphics graphics, int x, int y) {
        Graphics2D g = (Graphics2D)graphics
        g.setFont(font)
        g.setColor(java.awt.Color.BLACK)
        g.drawString(text, 30, 30)
    }

    @Override
    public int getIconWidth() { width }

    @Override
    public int getIconHeight() { height }
    
}