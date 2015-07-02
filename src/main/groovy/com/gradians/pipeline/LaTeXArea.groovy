package com.gradians.pipeline

import java.awt.Font
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.nio.file.Path
import java.nio.file.Files

import javax.swing.text.JTextComponent
import javax.swing.JTextArea
import javax.swing.KeyStroke
import javax.swing.text.Keymap
import javax.swing.text.TextAction;

import org.fife.ui.autocomplete.AutoCompletion
import org.fife.ui.autocomplete.DefaultCompletionProvider;
import org.fife.ui.autocomplete.ShorthandCompletion
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxScheme
import org.fife.ui.rsyntaxtextarea.SyntaxConstants
import org.fife.ui.rsyntaxtextarea.spell.SpellingParser
import org.fife.com.swabunga.spell.engine.SpellDictionaryHashMap


class LaTeXArea extends RSyntaxTextArea {

    public static LaTeXArea getInstance(String tex, int row, int col) {
        if (completionProvider == null) {
            completionProvider = new DefaultCompletionProvider()
            
            Path path = new File(System.getProperty("user.home")).toPath().resolve(".quill").resolve("shortcuts")
            def ostream = LaTeXArea.class.getClassLoader().getResourceAsStream("shortcuts")
            if (Files.notExists(path)) {
                Files.copy(ostream, path)
            }
            assert Files.exists(path)
            
            Properties p = new Properties()
            p.load(Files.newInputStream(path))
            Enumeration e = p.propertyNames()
            while (e.hasMoreElements()) {
                def key = e.nextElement()
                completionProvider.addCompletion(new ShorthandCompletion(
                    completionProvider, key, p.getProperty(key)))
            }
        }
        
        if (spellingParser == null) {
            SpellDictionaryHashMap dict = new SpellDictionaryHashMap()
            ["eng_com.dic", "color.dic", "colour.dic", "center.dic",
                "ise.dic", "ize.dic", "labeled.dic", "labelled.dic"].each { String dictName ->
                def rstream = LaTeXArea.class.getClassLoader().getResourceAsStream(dictName)
                dict.addDictionary(new BufferedReader(new InputStreamReader(rstream)))
            }
            spellingParser = new SpellingParser(dict)    
        }        
        return new LaTeXArea(tex, row, col)
    } 
    
    public LaTeXArea(String tex, int row, int col) {
        super(tex, row, col)
        setCodeFoldingEnabled(true)
        setPreferredFont()
        addCtrlKeys()
        addAutoComplete()
        setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_LATEX)
        discardAllEdits()
    }
    
    private def setPreferredFont() {
        font = new Font("SansSerif", Font.PLAIN, 12)
        SyntaxScheme ss = this.getSyntaxScheme()
        ss = (SyntaxScheme)ss.clone()
        for (int i = 0; i < ss.getStyleCount(); i++) {
           if (ss.getStyle(i) != null) {
              ss.getStyle(i).font = font
           }
        }
        this.setSyntaxScheme(ss)
    }
    
    private def addAutoComplete() {
        AutoCompletion ac = new AutoCompletion(LaTeXArea.completionProvider)
        KeyStroke t = KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SLASH, InputEvent.CTRL_DOWN_MASK)
        ac.setTriggerKey(t)
        ac.install(this)
    }
    
    private def addCtrlKeys() {
        Keymap latexMap = JTextComponent.addKeymap("LaTeXMap", this.keymap)
        KeyStroke s = KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK)
        latexMap.addActionForKeyStroke(s, new LaTeXAction(this, "save", 0))
        KeyStroke t = KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK)
        latexMap.addActionForKeyStroke(t, new LaTeXAction("\\text{}", 1))
        KeyStroke p = KeyStroke.getKeyStroke(KeyEvent.VK_9, InputEvent.CTRL_DOWN_MASK|InputEvent.SHIFT_DOWN_MASK)
        latexMap.addActionForKeyStroke(p, new LaTeXAction("\\left(\\right)", 7))
        KeyStroke b = KeyStroke.getKeyStroke(KeyEvent.VK_OPEN_BRACKET, InputEvent.CTRL_DOWN_MASK)
        latexMap.addActionForKeyStroke(b, new LaTeXAction("\\left[\\right]", 7))
        KeyStroke d = KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK)
        latexMap.addActionForKeyStroke(d, new LaTeXAction("\\dfrac{}{}", 3))
        KeyStroke A = KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_DOWN_MASK|InputEvent.SHIFT_DOWN_MASK)
        latexMap.addActionForKeyStroke(A, new LaTeXAction("\n\\begin{align}\n\\end{align}", 11))
        KeyStroke la = KeyStroke.getKeyStroke(KeyEvent.VK_COMMA, InputEvent.CTRL_DOWN_MASK|InputEvent.SHIFT_DOWN_MASK)
        latexMap.addActionForKeyStroke(la, new LaTeXAction("\\leftarrow", 0))
        KeyStroke ra = KeyStroke.getKeyStroke(KeyEvent.VK_PERIOD, InputEvent.CTRL_DOWN_MASK|InputEvent.SHIFT_DOWN_MASK)
        latexMap.addActionForKeyStroke(ra, new LaTeXAction("\\rightarrow", 0))
        KeyStroke i = KeyStroke.getKeyStroke(KeyEvent.VK_I, InputEvent.CTRL_DOWN_MASK)
        latexMap.addActionForKeyStroke(i, new LaTeXAction("\\implies", 0))
        KeyStroke M = KeyStroke.getKeyStroke(KeyEvent.VK_M, InputEvent.CTRL_DOWN_MASK|InputEvent.SHIFT_DOWN_MASK)
        latexMap.addActionForKeyStroke(M, new LaTeXAction(MATRIX_TEX, 0))
        this.keymap = latexMap
    }
    
    static DefaultCompletionProvider completionProvider
    static SpellingParser spellingParser
    
    final String MATRIX_TEX = 
        "\\left[\\begin{array}{ccc}\n a & b & c \\\\ \n" +
        " d & e & f \\\\\n g & h & i \\end{array} \\right]"    
}

class LaTeXAction extends TextAction {
    
    Editor editor
    String latex
    int offset
    
    public LaTeXAction(String latex, int offset) {
        super("laTeX-action")
        this.latex = latex
        this.offset = offset        
    }
    
    public LaTeXAction(Editor editor, String latex, int offset) {
        this(latex, offset)
        this.editor = editor
    }
    
    @Override
    void actionPerformed(ActionEvent ae) {
        JTextArea comp = (JTextArea)getTextComponent(ae)
        if (comp == null)
          return
        if (latex.equals("save") && editor != null) {
            editor.save()
        } else {
            comp.insert(latex, comp.getCaretPosition())
            comp.setCaretPosition(comp.getCaretPosition() - offset)
        }
    }

}
