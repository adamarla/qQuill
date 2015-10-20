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
    
    def toSwing(boolean topLevel = false) {
        sb = new SwingBuilder()
        sb.edt {
            lookAndFeel: 'MetalLookAndFeel'
            frame(title: q.uid, size: [720, 480], show: true, locationRelativeTo: null,
                defaultCloseOperation: topLevel ? EXIT_ON_CLOSE : DISPOSE_ON_CLOSE) {
                vbox() {
                    tabbedPane(id: 'tpSteps') {
                        toSwing(sb, q.statement, q.choices)
                        q.steps.eachWithIndex { step, i ->
                            if (step != null) {
                                if (!(step.context.length() == 0 && step.imageContext.length() == 0))
                                    toSwing(sb, step, i)
                            }
                        }
                    }
                    button(text: 'Reload', actionPerformed: reload)
                }
            }
        }
    }
    
    def reload = {
        def selectedIndex = sb.tpSteps.selectedIndex 
        sb.tpSteps.removeAll()
        q.reload()
        
        sb.tpSteps.add(toSwing(sb, q.statement, q.choices))
        q.steps.eachWithIndex { step, i ->
            if (step != null) {
                if (!(step.context.length() == 0 && step.imageContext.length() == 0))
                    sb.tpSteps.add(toSwing(sb, step, i))
            }
        }

        if (sb.tpSteps.getTabCount() > selectedIndex) {
            sb.tpSteps.selectedIndex = selectedIndex
        }
        
        sb.tpSteps.revalidate()
        sb.tpSteps.repaint()
        
    }
    
    def toPreview(SwingBuilder sb, Step step, int i) {
        sb.vbox(name: "Step ${i+1}") {            
            def drawable
            if (step.imageContext.length() > 0) {
                drawable = fileToIcon(step.imageContext)
                drawable.setBorder(BorderFactory.createTitledBorder("Context"))                                       
            } else {
                drawable = new TeXLabel(step.context, "Context") 
            }
            sb.widget(drawable)
            
            ["Correct", "Incorrect"].eachWithIndex { side, idx ->
                if (step."image${side}".length() > 0) {
                    drawable = fileToIcon(step."image${side}")
                    drawable.setBorder(BorderFactory.createTitledBorder("${side}"))
                } else {
                    drawable = new TeXLabel(step."tex${side}", "${side}")
                }                
                sb.widget(drawable)
            }            
            
            if (step.imageReason.length() > 0) {
                drawable = fileToIcon(step.imageReason)
                drawable.setBorder(BorderFactory.createTitledBorder("Reason"))
            } else {
                drawable = new TeXLabel(step.reason, "Reason")
            }
            sb.widget(drawable)
        }
    }
    
    def toSwing(SwingBuilder sb, Step step, int i) {
        sb.panel(name: "Step ${i+1}") {
            gridBagLayout()
            
            ["Context", "Reason"].eachWithIndex { part, idx ->
                def drawable
                if (step."image${part}".length() > 0) {
                    drawable = fileToIcon(step."image${part}")
                    drawable.setBorder(BorderFactory.createTitledBorder("${part}"))                                       
                } else {
                    drawable = new TeXLabel(step."${part.toLowerCase()}", part) 
                }
                
                widget(drawable, constraints: gbc(gridx: idx, gridy: 0, weightx: 1, fill: HORIZONTAL))
            }
            
            ["Correct", "Incorrect"].each { side ->
                def drawable
                if (step."image${side}".length() > 0) {
                    drawable = fileToIcon(step."image${side}")
                    drawable.setBorder(BorderFactory.createTitledBorder("${side}"))
                } else {
                    drawable = new TeXLabel(step."tex${side}", "${side}")
                }
                
                scrollPane(constraints: gbc(gridx: (side.equals("Correct") ? 0 : 1), 
                        gridy: 1, weightx: 1, weighty: 1, fill: BOTH)) {
                    widget(drawable)
                }    
            }
        }
    }
    
    def toPreview(SwingBuilder sb, Statement statement, Choices choices) {
        sb.vbox() {
            if (statement.tex.length() > 0)
                sb.widget(new TeXLabel(statement.tex, "Problem"))
            
            if (statement.image.length() > 0)
                sb.widget(fileToIcon(statement.image))            
            
            if (choices != null) {
                choices.texs.eachWithIndex { tex, i ->
                    char c = (char)(((int)'A') + i)
                    sb.widget(new TeXLabel(tex, "${c}"))
                }
                char correct = (char)(((int)'A') + choices.correct)
                sb.label(text: "Correct Ans ${correct}")
            }
        }
    }    

    def toSwing(SwingBuilder sb, Statement statement, Choices choices) {
        sb.panel(name: "Q / A") {
            gridBagLayout()

            sb.vbox(border: BorderFactory.createTitledBorder("Problem Statement"),
                constraints: gbc(gridx: 0, gridy: 0, fill: BOTH)) {
                
                if (statement.tex.length() > 0) {
                    widget(new TeXLabel(statement.tex, ""))
                }
                
                if (statement.image.length() > 0)
                    widget(fileToIcon(statement.image))
                    
            }
                
            sb.vbox(border: BorderFactory.createTitledBorder("Answer Choices"),
                constraints: gbc(gridx: 1, gridy: 0, fill: BOTH)) {
                if (choices != null) {
                    choices.texs.eachWithIndex { tex, i ->
                        char c = (char)(((int)'A') + i)
                        widget(new TeXLabel(tex, "${c}"))
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
                if (q.statement.image.length() > 0)
                    image(q.statement.image)
            }
            
            q.steps.each { stp ->
                if (stp != null && stp.context.length() != 0) {
                    def contents = {
                        if (stp.imageContext.length() > 0) 
                            context(image: "true", stp.imageContext)
                        else
                            context(stp.context)                        
                        
                        if (stp.imageCorrect.length() > 0)
                            image(correct: "true", stp.imageCorrect)
                        else if (stp.texCorrect.length() > 0)
                            tex(correct: "true", stp.texCorrect)
                            
                        if (stp.imageIncorrect.length() > 0)
                            image(stp.imageIncorrect)
                        else if (stp.texIncorrect.length() > 0)
                            tex(stp.texIncorrect)
                            
                        if (stp.imageReason.length() > 0)
                            reason(image: "true", stp.imageReason)
                        else
                            reason(stp.reason)
                    }
                    
                    if (stp.noswipe) {
                        step(swipe: "false", contents)
                    } else {
                        step(contents)
                    }    
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
        DirectoryStream<Path> svgs = Files.newDirectoryStream(path, "*.svg")
        for (Path p : svgs) {
            def s = p.getFileName().toString()
            if (s.startsWith("CTX") || s.startsWith("RSN") ||
                s.startsWith("CRT") || s.startsWith("WRNG")) {
                Files.deleteIfExists(p)
            }
        }
        
        if (q.statement.tex.length() > 0) {
            renderSVG(q.statement.tex, path.resolve("STMT_0.svg"))
            renderSVG(q.statement.tex, path.resolve("PREVIEW.svg"), true)
        }
            
        for (int idx = 0; idx < q.steps.length; idx++) {
            def step = q.steps[idx]
            if (step != null) {
                if (!(step.context.length() == 0 && step.imageContext.length() == 0)) {
                    def content = [step.context, step.texCorrect, step.texIncorrect, step.reason]
                    def images = ["CTX_${idx}.svg", "CRT_${idx}.svg", "WRNG_${idx}.svg", "RSN_${idx}.svg"]
                    images.eachWithIndex { part, posn ->
                        if (content[posn].length() > 0)
                            renderSVG(content[posn], path.resolve(part))
                    }
                }
            }
        }
        
        if (q.choices != null) {
            q.choices.texs.eachWithIndex { tex, idx ->
                renderSVG(tex, path.resolve("CH_${idx}.svg"))
            }    
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

class TeXLabel extends JLabel {
    
    public TeXLabel(String tex, String title) {
        teXToIcon(tex)
        setBorder(title)
    }
    
    public void setBorder(String title) {
        TitledBorder b = BorderFactory.createTitledBorder(title)
        if (icon != null) {
            if (icon.iconWidth > ERROR_WIDTH) {
                int overflowPcnt = (icon.iconWidth - ERROR_WIDTH)*100/ERROR_WIDTH
                b.title += "(${overflowPcnt}%)"
                b.setBorder(new LineBorder(RED, (int)overflowPcnt/10+1))
            } else if (icon.iconWidth > WARNING_WIDTH) {
                b.setBorder(new LineBorder(ORANGE))
            }    
        } else {
            b.setBorder(new LineBorder(RED))
        }
        super.setBorder(b)
    }
    
    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g)
        if (icon != null) {
            if (icon.iconWidth > ERROR_WIDTH) {
                g.setColor(Color.BLUE)
                g.drawLine(ERROR_WIDTH, 0, ERROR_WIDTH, icon.iconHeight)
            }
        }
    }
    
    private def teXToIcon(String tex) {
        try {
            this.icon = TeXHelper.createIcon(tex, 15)
        } catch (Exception e) {
            this.text = "<html>${e.getMessage()}</html>"
        }
    }    

    private final int CHAR_WIDTH = 45    
    private final int ERROR_WIDTH = 300, WARNING_WIDTH = 280
    
}

class TeXHelper {
    
    public static def Icon createIcon(String tex, int fontSize, boolean negative = false) {
        Icon icon       
        String[] lines = tex.split("\n")
        
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
                lines[i] = "\\text{${line}} \\\\"
        }
        
        TeXFormula formula
        try {
            formula = new TeXFormula(lines.join('\n'))
            icon = formula.createTeXIcon(TeXConstants.STYLE_DISPLAY, 15,
                TeXFormula.SERIF | TeXFormula.ROMAN)            
            icon.setForeground(negative ? Color.WHITE : Color.BLACK)
        } catch (Exception e) {
            formula = new TeXFormula("\\text{${e.getMessage()}}>")
            icon = formula.createTeXIcon(TeXConstants.STYLE_DISPLAY, 15,
                TeXFormula.SERIF | TeXFormula.ROMAN)
        }
        icon
    }
    
}
