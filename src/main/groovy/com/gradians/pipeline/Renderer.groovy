package com.gradians.pipeline

import java.awt.Color
import java.awt.Dimension;
import java.awt.geom.RoundRectangle2D
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.nio.file.Path

import javax.swing.JDialog
import javax.swing.JScrollPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.BorderFactory
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

import groovy.json.JsonBuilder
import groovy.swing.SwingBuilder

import static java.awt.BorderLayout.EAST
import static java.awt.GridBagConstraints.HORIZONTAL
import static java.awt.GridBagConstraints.BOTH


class Renderer {
    
    Question q
    def fontSize
    
    static {
        Map<String, String> map = TeXFormula.predefinedTeXFormulasAsString
        map.keySet().each {
            TeXFormula.get(it)
        }
    }
    
    def Renderer(int fontSize = 15) {
        this.fontSize = fontSize        
    }
    
    def Renderer(Question q, int fontSize = 15) {
        this()
        this.q = q
    }
    
    def toSwing(SwingBuilder sb) {
        def panel = sb.panel() {
            gridBagLayout()
            
            panel(constraints: gbc(gridx: 0, gridy: 0, weightx: 1, weighty: 0, fill: HORIZONTAL)) {
                widget(toSwing(sb, q.statement))
            }
                
            panel(constraints: gbc(gridx: 1, gridy: 0, weightx: 1, weighty: 0, fill: HORIZONTAL)) {
                widget(toSwing(sb, q.choices))
            }
                
            scrollPane(constraints: gbc(gridx: 0, gridy: 1, gridwidth: 2, weightx: 1, weighty: 1, fill: BOTH),
                verticalScrollBarPolicy:JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED) {
                panel(constraints: EAST) {
                    vbox {
                        q.steps.each { step ->
                            widget(toSwing(sb, step))
                        }
                    }
                }
            }
        }
    }
    
    def toSwing(SwingBuilder sb, Step step) {
        def panel = sb.panel(border: BorderFactory.createLineBorder(Color.BLACK)) {
            gridBagLayout()
            
            label(icon: teXToIcon(step.context),
                border: BorderFactory.createTitledBorder("Context"),
                constraints: gbc(gridx: 0, gridy: 0))
            
            label(icon: teXToIcon(step.reason),
                border: BorderFactory.createTitledBorder("Reason"),
                constraints: gbc(gridx: 1, gridy: 0))
            
            ["Right", "Wrong"].each { side ->
                panel(border: BorderFactory.createTitledBorder("${side}"),
                    constraints: gbc(gridx: (side.equals("Right") ? 0 : 1), gridy: 1)) {
                    scrollPane() {
                        if (step."image${side}".length() > 0)
                            widget(fileToIcon(step."image${side}"))
                        else
                            label(icon: teXToIcon(step."tex${side}"))                        
                    }
                }    
            }            
        }
    }
    
    def toSwing(SwingBuilder sb, Statement statement) {
        def panel = sb.panel(border: BorderFactory.createTitledBorder("Statement")) {
            vbox(constraints: EAST) {
                label(icon: teXToIcon(statement.tex))
                if (statement.image.length() > 0)
                    widget(fileToIcon(statement.image))    
            }
        }
    }
    
    def toSwing(SwingBuilder sb, Choices choices) {
        def panel = sb.panel(border: BorderFactory.createTitledBorder("Choices")) {
            vbox(constraints: EAST) {
                choices.texs.eachWithIndex { tex, i ->
                    label(icon: teXToIcon(tex))
                }
                char correct = (char)(((int)'A') + choices.correct)
                label(text: "Correct Ans ${correct}")
            }
        }
    }
    
    def toJSONString(boolean pretty = false) {
        JsonBuilder builder = new JsonBuilder()
        builder.question {
            'uid' q.uid
            'bundles' q.bundle
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
        xml.mkp.xmlDeclaration(version: "1.0", encoding: "utf-8")
        xml.question(xmlns: "http://www.gradians.com") {
            statement() {
                tex(q.statement.tex)
            }
            
            q.steps.each { stp ->
                def contents = {
                    context(stp.context)
                    if (stp.imageRight.length() > 0)
                        image(correct: "true", stp.imageRight)
                    else
                        tex(correct: "true", stp.texRight)
                    if (stp.imageWrong.length() > 0)
                        image(stp.imageWrong)
                    else
                        tex(stp.texWrong)
                    reason(stp.reason)
                }
                
                if (stp.noswipe) {
                    step(swipe: "false", contents)
                } else {
                    step(contents)
                }
            }
            
            if (q.choices != null) {
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
            
            if (q.bundle != null)
                bundleId(q.bundle)
        }
        path.resolve("question.xml").toFile().write(sw.toString())
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

    private def render(String tex, Path path) {
        TeXIcon icon = teXToIcon(tex)

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
    
    private def JSVGCanvas fileToIcon(String name) {
        JSVGCanvas svgCanvas = new JSVGCanvas()
        svgCanvas.setURI(q.qpath.resolve(name).toUri().toURL().toString())
        svgCanvas
    }

    private TeXIcon teXToIcon(String tex) {
        TeXIcon texIcon
        TeXFormula formula
        try {
            formula = new TeXFormula(tex)
        } catch (Exception e) {
            def s = splitEqually(e.getMessage()).join("\\\\")
            formula = new TeXFormula("\\text{${s}}")
        }
        texIcon = formula.createTeXIcon(TeXConstants.STYLE_DISPLAY, fontSize, TeXFormula.SANSSERIF)
    }
    
    private def String[] splitEqually(String s) {
        int n = (s.length() / WIDTH) + 1
        def parts = new String[n]
        for (int i = 0; i < n; i++) {
            parts[i] = s.substring(i*WIDTH,
                i == n-1 ? s.length() : (i+1)*WIDTH)
        }
        parts
    }    
    
    private final int WIDTH = 35
    private final def OFFSET_X = 3, OFFSET_Y = 2
    
}
