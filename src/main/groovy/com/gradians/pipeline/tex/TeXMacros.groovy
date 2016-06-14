package com.gradians.pipeline.tex

import org.json.simple.parser.ParseException
import org.scilab.forge.jlatexmath.Atom
import org.scilab.forge.jlatexmath.TeXFormula
import org.scilab.forge.jlatexmath.TeXParser

class TeXMacros {
    
    public Atom dd(TeXParser tp, String[] args) throws ParseException {
        new TeXFormula(String.format("\\dfrac{d}{d%s}%s", args[2], args[1])).root
    }
    
    public Atom vector(TeXParser tp, String[] args) throws ParseException {
        String format = " "
        if (args[1].length() > 0) {
            if (args[1].equals("1") || args[1].equals("-1")) {
                args[1] = args[1].replace('1', ' ')
            }
            format += String.format("%s\\hat\\imath ", args[1])
        }
        if (args[2].length() > 0) {
            if (args[2].equals("1") || args[2].equals("-1")) {
                args[2] = args[2].replace('1', ' ')
            }
            if (!args[2].startsWith("-") && args[1].length() > 0) {
                format += "+"
            }
            format += String.format("%s\\hat\\jmath ", args[2])                
        }
        if (args[3].length() > 0) {
            if (args[3].equals("1") || args[3].equals("-1")) {
                args[3] = args[3].replace('1', ' ')
            }
            if (!args[3].startsWith("-") && args[2].length() > 0) {
                format += "+"
            }
            format += String.format("%s\\hat{\\it k} ", args[3])
        }
        new TeXFormula(format).root
    }
    
    public Atom ora(TeXParser tp, String[] args) throws ParseException {
        new TeXFormula(String.format("\\overrightarrow{%s}", args[1])).root
    }

}
