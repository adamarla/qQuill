package com.gradians.pipeline.edit

import java.awt.Color
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics
import java.awt.Insets
import java.awt.geom.RoundRectangle2D
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.nio.file.DirectoryStream
import java.nio.file.Path
import java.nio.file.Files

import javax.swing.JDialog
import javax.swing.JScrollPane
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.BorderFactory
import javax.swing.border.Border
import javax.swing.border.LineBorder
import javax.swing.border.TitledBorder

import org.apache.batik.dom.GenericDOMImplementation
import org.apache.batik.svggen.SVGGeneratorContext
import org.apache.batik.svggen.SVGGraphics2D
import org.apache.batik.swing.JSVGCanvas;
import org.scilab.forge.jlatexmath.DefaultTeXFont
import org.scilab.forge.jlatexmath.TeXConstants
import org.scilab.forge.jlatexmath.TeXFormula
import org.scilab.forge.jlatexmath.TeXIcon
import org.scilab.forge.jlatexmath.cyrillic.CyrillicRegistration
import org.scilab.forge.jlatexmath.greek.GreekRegistration
import org.w3c.dom.DOMImplementation

import com.gradians.pipeline.data.Artifact
import com.gradians.pipeline.data.Choices;
import com.gradians.pipeline.data.Question;
import com.gradians.pipeline.data.Statement;
import com.gradians.pipeline.data.Step;

import groovy.json.JsonBuilder
import groovy.swing.SwingBuilder
import static java.awt.Color.ORANGE
import static java.awt.Color.RED
import static java.awt.GridBagConstraints.HORIZONTAL
import static java.awt.GridBagConstraints.BOTH
import static javax.swing.WindowConstants.DISPOSE_ON_CLOSE
import static javax.swing.JFrame.EXIT_ON_CLOSE


class Renderer {
    
    Question q
    def fontSize
    
    SwingBuilder sb
    
    static {
//        TeXFormula.registerExternalFont(Character.UnicodeBlock.BASIC_LATIN, "Ubuntu")
        Map<String, String> map = TeXFormula.predefinedTeXFormulasAsString
        map.keySet().each {
            TeXFormula.get(it)
        }
        def ostream = Renderer.class.getClassLoader().getResourceAsStream("TeXMacros.xml")
        TeXFormula.addPredefinedCommands(ostream)
    }
    
    def Renderer(int fontSize = 15) {
        this.fontSize = fontSize
    }
    
    def Renderer(Question q, int fontSize = 15) {
        this(fontSize)
        this.q = q
    }
    
    def Renderer(Editor e, Question q, int fontSize = 15) {
        this(fontSize)
        this.q = q
        this.e = e    
    }
    
    def toTeX() {
        def sw = new StringWriter()
        
        sw.append("\\documentclass[12pt]{article}\n\\RequirePackage{prepwell}\n")
        sw.append("\\begin{document}\n\t\\begin{question}\n")
        
        sw.append("\t\t\\begin{statement}\n")
        sw.append("\t\t\t\\tex{${TeXHelper.toPureTeX(q.statement.tex)}}\n")
        if (q.statement.image.length() > 0)
            sw.append("\t\t\t\\img{${q.statement.image}}\n")
        sw.append("\t\t\\end{statement}\n")

        q.steps.each { stp ->
            if (stp != null && stp.context.length() != 0) {                
                sw.append("%STEP\n")
                sw.append("\t\t\\begin{step}\n")
                
                sw.append("\t\t\\begin{context}\n")
                sw.append("\t\t\t\\tex{${TeXHelper.toPureTeX(stp.context.trim())}}\n")
                sw.append("\t\t\\end{context}\n")
                
                sw.append("\t\t\\begin{options}\n")
                                                    
                if (stp.imageCorrect.length() > 0)
                    sw.append("\t\t\t\\image{${stp.imageCorrect}}\n")
                else if (stp.texCorrect.length() > 0)
                    sw.append("\t\t\t\\tex{${TeXHelper.toPureTeX(stp.texCorrect.trim())}}\n")
 
                if (stp.imageIncorrect.length() > 0)
                    sw.append("\t\t\t\\img[false]{${stp.imageIncorrect}}\n")
                else if (stp.texIncorrect.length() > 0)
                    sw.append("\t\t\t\\tex[false]{${TeXHelper.toPureTeX(stp.texIncorrect.trim())}}\n")

                sw.append("\t\t\\end{options}\n")
                
                sw.append("\t\t\\begin{reason}\n")
                if (stp.imageReason.length() > 0)
                    sw.append("\\img{${stp.imageReason.trim()}}\n")
                else
                    sw.append("\\tex{${TeXHelper.toPureTeX(stp.reason.trim())}}\n")
                sw.append("\t\t\\end{reason}\n")

                sw.append("\t\t\\end{step}\n")
            }
        }

        if (q.choices != null) {            
            sw.append("\t\t\\begin{choices}\n")
            q.choices.texs.eachWithIndex { tx, i ->
                if (q.choices.correct == i) {
                    sw.append("\t\t\t\\tex[true]{${tx}}\n")
                } else {
                    sw.append("\t\t\t\\tex[false]{${tx}}\n")
                }
            }
            sw.append("\t\t\\end{choices}\n")            
        }
            
        sw.append("\\end{document}\n\\end{question}")    
        q.qpath.resolve("outline.tex").toFile().write(sw.toString())
    }
    
    def toSVG(Artifact a) {
        Path path = a.qpath
        
        Map<String, String> toRender = a.toRender()
        // renderSVG(q.statement.tex, path.resolve("STMT_0.svg"))        
        DirectoryStream<Path> svgs = Files.newDirectoryStream(path, "*.svg")
        for (Path p : svgs) {
            def s = p.getFileName().toString()
            if (s.startsWith("CTX") || s.startsWith("RSN") ||
                s.startsWith("CRT") || s.startsWith("WRNG")) {
                Files.deleteIfExists(p)
            }
        }
        
        toRender.keySet().each { it ->
            renderSVG(toRender.get(it), path.resolve(it))
        }        
    }

    private def renderSVG(String tex, Path path, boolean negative = false) {
        Files.deleteIfExists(path)
        if (tex.length() == 0)
            return
            
        Icon icon = TeXHelper.createIcon(tex, fontSize, negative)
            
        // Get a DOMImplementation.
        DOMImplementation domImpl = GenericDOMImplementation.getDOMImplementation()
        // Create an instance of org.w3c.dom.Document.
        String svgNS = "http://www.w3.org/2000/svg"
        org.w3c.dom.Document document = domImpl.createDocument(svgNS, "svg", null)        
        
        // Create an instance of the SVG Generator.
        boolean glyphAsShape = true
        SVGGeneratorContext ctx = SVGGeneratorContext.createDefault(document)
        ctx.setEmbeddedFontsOn(glyphAsShape)
        SVGGraphics2D svgGenerator = new SVGGraphics2D(ctx, glyphAsShape)
        
        // Ask the test to render into the SVG Graphics2D implementation.
        svgGenerator.setSVGCanvasSize(new Dimension(icon.getIconWidth(), icon.getIconHeight()))
        icon.paintIcon(null, svgGenerator, 0, 0)
    
        // Finally, stream out SVG to the standard output using UTF-8 encoding.
        boolean useCSS = true // we want to use CSS style attributes
        Writer out = new OutputStreamWriter(new FileOutputStream(path.toFile()), "UTF-8")
        svgGenerator.stream(out, useCSS)
        out.close()
    }
    
    private def JSVGCanvas fileToIcon(String name) {
        JSVGCanvas svgCanvas = new JSVGCanvas()
        try {
            svgCanvas.setURI(q.qpath.resolve(name).toUri().toURL().toString())            
        } catch (Exception e) { 
        }
        svgCanvas
    }
    
}

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
            icon = formula.createTeXIcon(TeXConstants.STYLE_DISPLAY, 15)
            icon.setForeground(negative ? Color.WHITE : Color.BLACK)
        } catch (Exception e) {
            String exceptionText = TeXHelper.formatException(e)
            formula = new TeXFormula(exceptionText)
            icon = formula.createTeXIcon(TeXConstants.STYLE_DISPLAY, 15,
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
