package com.gradians.pipeline

import java.awt.Color
import java.awt.Dimension;
import java.awt.geom.RoundRectangle2D
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.nio.file.Path

import org.apache.batik.dom.GenericDOMImplementation
import org.apache.batik.svggen.SVGGeneratorContext
import org.apache.batik.svggen.SVGGraphics2D
import org.scilab.forge.jlatexmath.DefaultTeXFont
import org.scilab.forge.jlatexmath.TeXConstants
import org.scilab.forge.jlatexmath.TeXFormula
import org.scilab.forge.jlatexmath.TeXIcon
import org.scilab.forge.jlatexmath.cyrillic.CyrillicRegistration
import org.scilab.forge.jlatexmath.greek.GreekRegistration
import org.w3c.dom.DOMImplementation

import groovy.json.JsonBuilder

class Renderer {
    
    Question q
    
    def Renderer(Question q) {
        this.q = q
        DefaultTeXFont.registerAlphabet(new GreekRegistration());
        DefaultTeXFont.registerAlphabet(new CyrillicRegistration());        
    }
    
    def toJSONString(boolean pretty = false) {
        JsonBuilder builder = new JsonBuilder()
        builder.question {
            'uid' q.uid
            'bundles' q.bundles
            'concepts' q.concepts
        }
        if (pretty)
            builder.toString()
        else
            builder.toPrettyString()
    }

    def toXMLString(Path path) {
        def sw = new StringWriter()
        def xml = new groovy.xml.MarkupBuilder(sw)
        
        xml.question(xmlns: "http://www.gradians.com") {
            statement() {
                tex(q.statement.tex)
            }
            
            q.steps.each { stp ->
                step(swipe: "false") {
                    context(stp.context)
                    tex(correct: "true", stp.texRight)
                    tex(stp.texWrong)
                    reason(stp.reason)
                }
            }
            
            choices() {
                q.choices.texs.eachWithIndex { tx, i ->
                    if (q.choices.correct == i) {
                        tex(correct: "true", tx)
                    } else {
                        tex(tx)
                    }
                }
            }
        }
        path.resolve("generated.xml").toFile().write(sw.toString())
    }    
    
    def toSVG(Path path) {
        render(q.statement.tex, path.resolve("STMT_0.svg"))
        q.steps.eachWithIndex { step, idx ->
            render(step.context, path.resolve("CTX_${idx}.svg"))
            render(step.texRight, path.resolve("CRT_${idx}.svg"))
            render(step.texWrong, path.resolve("WRNG_${idx}.svg"))
            render(step.reason, path.resolve("RSN_${idx}.svg"))
        }
        q.choices.texs.eachWithIndex { tex, idx ->
            render(tex, path.resolve("CH_${idx}.svg"))
        }
    }

    def render(String tex, Path path) {
        tex = tex.replace("\n", "\\\\").replace("\\qquad", "\\quad\\quad")
        TeXFormula formula = new TeXFormula(tex)
        TeXIcon icon = formula.createTeXIcon(TeXConstants.STYLE_DISPLAY, 12, TeXFormula.SANSSERIF);

        // Get a DOMImplementation.
        DOMImplementation domImpl = GenericDOMImplementation.getDOMImplementation()
        // Create an instance of org.w3c.dom.Document.
        String svgNS = "http://www.w3.org/2000/svg";
        org.w3c.dom.Document document = domImpl.createDocument(svgNS, "svg", null)
        
        // Create an instance of the SVG Generator.
        boolean glyphAsShape = true
        SVGGeneratorContext ctx = SVGGeneratorContext.createDefault(document)
        ctx.setEmbeddedFontsOn(glyphAsShape)
        SVGGraphics2D svgGenerator = new SVGGraphics2D(ctx, glyphAsShape)
        
        // Ask the test to render into the SVG Graphics2D implementation.
        svgGenerator.setSVGCanvasSize(
            new Dimension(icon.getIconWidth() + OFFSET_X, icon.getIconHeight() + OFFSET_Y))
        icon.paintIcon(null, svgGenerator, OFFSET_X, OFFSET_Y)
    
        // Finally, stream out SVG to the standard output using
        // UTF-8 encoding.
        boolean useCSS = true; // we want to use CSS style attributes        
        Writer out = new OutputStreamWriter(new FileOutputStream(path.toFile()), "UTF-8")
        svgGenerator.stream(out, useCSS)
        out.close()
    }
    
    final def OFFSET_X = 3, OFFSET_Y = 2
    
}
