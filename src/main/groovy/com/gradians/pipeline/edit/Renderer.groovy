package com.gradians.pipeline.edit

import java.awt.Color
import java.awt.Dimension
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.nio.file.DirectoryStream
import java.nio.file.Path
import java.nio.file.Files

import javax.swing.Icon

import org.apache.batik.dom.GenericDOMImplementation
import org.apache.batik.svggen.SVGGeneratorContext
import org.apache.batik.svggen.SVGGraphics2D
import org.apache.batik.swing.JSVGCanvas
import org.scilab.forge.jlatexmath.DefaultTeXFont
import org.scilab.forge.jlatexmath.TeXConstants
import org.scilab.forge.jlatexmath.TeXFormula
import org.scilab.forge.jlatexmath.TeXIcon
import org.scilab.forge.jlatexmath.cyrillic.CyrillicRegistration
import org.scilab.forge.jlatexmath.greek.GreekRegistration
import org.w3c.dom.DOMImplementation

import com.gradians.pipeline.data.Asset

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
