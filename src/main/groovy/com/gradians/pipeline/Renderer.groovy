package com.gradians.pipeline

import java.awt.Color
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics
import java.awt.Insets
import java.awt.geom.RoundRectangle2D
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.Writer
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

import groovy.json.JsonBuilder
import groovy.swing.SwingBuilder
import static java.awt.Color.ORANGE
import static java.awt.Color.RED
import static java.awt.BorderLayout.EAST
import static java.awt.GridBagConstraints.HORIZONTAL
import static java.awt.GridBagConstraints.BOTH
import static javax.swing.WindowConstants.DISPOSE_ON_CLOSE
import static javax.swing.JFrame.EXIT_ON_CLOSE


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
    
    def toSwing(boolean topLevel = false) {
        def sb = new SwingBuilder()
        def panel = sb.panel() {
            gridBagLayout()
            
            widget(toSwing(sb, q.statement),
                constraints: gbc(gridx: 0, gridy: 0, weightx: 1, fill: HORIZONTAL))
            
            widget(toSwing(sb, (Choices)q.choices),
                constraints: gbc(gridx: 1, gridy: 0, weightx: 1, fill: HORIZONTAL))
                        
            tabbedPane(constraints: gbc(gridx: 0, gridy: 1, gridwidth: 2, 
                weightx: 1, weighty: 1, fill: BOTH)) {
                q.steps.eachWithIndex { step, i ->
                    widget(toSwing(sb, step), name: "Step${i+1}")
                }
            }
        }
        sb.edt {
            lookAndFeel: 'MetalLookAndFeel'
            frame(title: q.uid, size: [860, 600], show: true, locationRelativeTo: null,
                defaultCloseOperation: topLevel ? EXIT_ON_CLOSE : DISPOSE_ON_CLOSE) {
                widget(panel)
            }
        }
    }
    
    def toSwing(SwingBuilder sb, Step step) {
        def panel = sb.panel() {
            gridBagLayout()
            
            ["Context", "Reason"].eachWithIndex { part, idx ->
                widget(new TeXLabel(teXToIcon(step."${part.toLowerCase()}"), part), 
                    constraints: gbc(gridx: idx, gridy: 0, weightx: 1, fill: HORIZONTAL))            
            }
            
            ["Right", "Wrong"].each { side ->
                def drawable
                if (step."image${side}".length() > 0) {
                    drawable = fileToIcon(step."image${side}")
                    drawable.setBorder(BorderFactory.createTitledBorder("${side}"))
                } else {
                    drawable = new TeXLabel(teXToIcon(step."tex${side}"), "${side}")
                }
                
                scrollPane(constraints: gbc(gridx: (side.equals("Right") ? 0 : 1), 
                        gridy: 1, weightx: 1, weighty: 1, fill: BOTH)) {
                    widget(drawable)
                }    
            }
        }
    }
    
    def toSwing(SwingBuilder sb, Statement statement) {
        def panel = sb.panel() {
            vbox(constraints: EAST) {
                if (statement.image.length() > 0)                
                    widget(fileToIcon(statement.image))
                else {
                    widget(new TeXLabel(teXToIcon(statement.tex), "Problem"))
                }    
            }
        }
    }
    
    def toSwing(SwingBuilder sb, Choices choices) {
        def panel = sb.panel(border: BorderFactory.createTitledBorder("Choices")) {
            vbox(constraints: EAST) {
                if (choices != null) {
                    choices.texs.eachWithIndex { tex, i ->
                        char c = (char)(((int)'A') + i)
                        widget(new TeXLabel(teXToIcon(tex), "${c}"))
                    }
                    char correct = (char)(((int)'A') + choices.correct)
                    label(text: "Correct Ans ${correct}")    
                }
            }
        }
    }
    
    def toJSONString(boolean pretty = false) {
        JsonBuilder builder = new JsonBuilder()
        builder.question {
            'uid' q.uid
            'bundle' q.bundle
            'concepts' q.concepts
        }
        if (pretty)
            builder.toString()
        else
            builder.toPrettyString()
    }

    def toXMLString() {
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
        }
        q.qpath.resolve("question.xml").toFile().write(sw.toString())
    }
    
    def toSVG() {
        Path path = q.qpath
        renderSVG(q.statement.tex, path.resolve("STMT_0.svg"))
        q.steps.eachWithIndex { step, idx ->
            renderSVG(step.context, path.resolve("CTX_${idx}.svg"))
            renderSVG(step.texRight, path.resolve("CRT_${idx}.svg"))
            renderSVG(step.texWrong, path.resolve("WRNG_${idx}.svg"))
            renderSVG(step.reason, path.resolve("RSN_${idx}.svg"))
        }
        if (q.choices != null) {
            q.choices.texs.eachWithIndex { tex, idx ->
                renderSVG(tex, path.resolve("CH_${idx}.svg"))
            }    
        }
    }

    private def renderSVG(String tex, Path path) {
        Files.deleteIfExists(path)
        if (tex.length() == 0)
            return
            
        TeXIcon icon = teXToIcon(tex)
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
        svgGenerator.setBackground(Color.WHITE)
    
        // Finally, stream out SVG to the standard output using UTF-8 encoding.
        boolean useCSS = true // we want to use CSS style attributes
        Writer out = new OutputStreamWriter(new FileOutputStream(path.toFile()), "UTF-8")
        svgGenerator.stream(out, useCSS)
        out.close()
    }    
    
    private def JSVGCanvas fileToIcon(String name) {
        JSVGCanvas svgCanvas = new JSVGCanvas()
        svgCanvas.setURI(q.qpath.resolve(name).toUri().toURL().toString())
        svgCanvas
    }
    
    private def Icon teXToIcon(String tex) {
        TeXIcon texIcon
        TeXFormula formula
        try {
            formula = new TeXFormula(tex)
        } catch (Exception e) {
            def s = splitEqually(e.getMessage()).join("\\\\")
            formula = new TeXFormula("\\text{${s}}")
        }
        texIcon = formula.createTeXIcon(TeXConstants.STYLE_DISPLAY, fontSize, 
            TeXFormula.SANSSERIF | TeXFormula.ROMAN)
        texIcon
    }
    
    private def String[] splitEqually(String s) {
        int n = (s.length() / CHAR_WIDTH) + 1
        def parts = new String[n]
        for (int i = 0; i < n; i++) {
            parts[i] = s.substring(i*CHAR_WIDTH,
                i == n-1 ? s.length() : (i+1)*CHAR_WIDTH)
        }
        parts
    }    

    private final int CHAR_WIDTH = 45
    
}

class TeXLabel extends JLabel {
    
    public TeXLabel(TeXIcon icon) {
        this.icon = icon
    }
    
    public TeXLabel(TeXIcon icon, String title) {
        this.icon = icon
        setBorder(title)
    }
    
    public void setBorder(String title) {
        TitledBorder b = BorderFactory.createTitledBorder(title)
        if (icon.iconWidth > ERROR_WIDTH)
            b.setBorder(new LineBorder(RED, (icon.iconWidth - ERROR_WIDTH)%5+1))
        else if (icon.iconWidth > WARNING_WIDTH)
            b.setBorder(new LineBorder(ORANGE))
        super.setBorder(b)
    }
    
    @Override
    public void paintComponent(Graphics g){
        super.paintComponent(g)        
        if (icon.iconWidth > ERROR_WIDTH) {
            g.setColor(Color.BLUE)
            g.drawLine(ERROR_WIDTH, 0, ERROR_WIDTH, icon.iconHeight)
        }
    }
    
    private final int ERROR_WIDTH = 300, WARNING_WIDTH = 280
    
}
