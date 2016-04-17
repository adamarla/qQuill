package com.gradians.pipeline.edit;

import javax.swing.Icon;
import org.scilab.forge.jlatexmath.TeXFormula

import static java.awt.Color.BLACK
import static java.awt.Color.WHITE

import static org.scilab.forge.jlatexmath.TeXConstants.STYLE_DISPLAY
import static org.scilab.forge.jlatexmath.TeXConstants.STYLE_DISPLAY
import static org.scilab.forge.jlatexmath.TeXConstants.STYLE_DISPLAY

class TeXHelper {
        
    static {
//        TeXFormula.registerExternalFont(Character.UnicodeBlock.BASIC_LATIN, "Ubuntu")
        Map<String, String> map = TeXFormula.predefinedTeXFormulasAsString
        map.keySet().each { TeXFormula.get(it) }
        def ostream = Renderer.class.getClassLoader().getResourceAsStream("TeXMacros.xml")
        TeXFormula.addPredefinedCommands(ostream)
    }            
        
    public static def Icon createIcon(String tex, int fontSize, boolean negative = false) {        
        Icon icon        
        String[] lines = tex.split("\n")
        def sw = new StringWriter()
        
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
                sw.append("\\text{${line}} \\\\\n")
            else
                sw.append("${line}\n")
        }
                
        TeXFormula formula
        try {
            formula = new TeXFormula(sw.toString())
            icon = formula.createTeXIcon(STYLE_DISPLAY, 15)
            icon.setForeground(negative ? WHITE : BLACK)
        } catch (Exception e) {
            String exceptionText = TeXHelper.formatException(e)
            formula = new TeXFormula(exceptionText)
            icon = formula.createTeXIcon(STYLE_DISPLAY, 15,
                TeXFormula.SANSSERIF)
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
    
    private static String formatException(Exception e) {
        String message = e.getMessage()
        List<String> messages = new ArrayList<String>()        
        while (message.length() > 42) {
            messages.add("\\text{${message.substring(0, 41)}}")
            message = message.substring(42)
        }
        return messages.join('\\\\')
    }
    
}