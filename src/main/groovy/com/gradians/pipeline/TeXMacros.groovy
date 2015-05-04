package com.gradians.pipeline

import org.json.simple.parser.ParseException
import org.scilab.forge.jlatexmath.Atom
import org.scilab.forge.jlatexmath.TeXFormula
import org.scilab.forge.jlatexmath.TeXParser

class TeXMacros {
    
    public Atom dd(TeXParser tp, String[] args) throws ParseException {
        return new TeXFormula(String.format("\\dfrac{d}{d%s}%s", args[2], args[1])).root;
    }

}
