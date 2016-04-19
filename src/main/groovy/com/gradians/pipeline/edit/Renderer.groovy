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

import com.gradians.pipeline.data.Asset
import com.gradians.pipeline.data.Choices;
import com.gradians.pipeline.data.Question;
import com.gradians.pipeline.data.Statement;
import com.gradians.pipeline.data.Step;

import groovy.json.JsonBuilder
import static java.awt.Color.ORANGE
import static java.awt.Color.RED
import static java.awt.GridBagConstraints.HORIZONTAL
import static java.awt.GridBagConstraints.BOTH
import static javax.swing.WindowConstants.DISPOSE_ON_CLOSE
import static javax.swing.JFrame.EXIT_ON_CLOSE


class Renderer {
    
    def fontSize
    Asset a
    
    def Renderer(int fontSize = 15) {
        this.fontSize = fontSize
    }
    
    def Renderer(Asset a, int fontSize = 15) {
        this(fontSize)
        this.a = a
    }
    
    def toTeX() {
        Question q = (Question)a        
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
    
    def toSVG() {
        Path path = a.qpath
        
        Map<String, String> toRender = a.toRender()
        DirectoryStream<Path> svgs = Files.newDirectoryStream(path, "*.svg")
        for (Path p : svgs) {
            def s = p.getFileName().toString()
            if (!(s =~ "img.*svg")) {
                Files.deleteIfExists(p)
            }
        }
                
        toRender.keySet().each { it ->
            createSVG(toRender.get(it), path.resolve(it))
        }
    }

    private def createSVG(String tex, Path path, boolean negative = false) {        
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
    
    
}
