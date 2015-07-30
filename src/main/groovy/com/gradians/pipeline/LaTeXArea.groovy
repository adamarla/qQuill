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
            
            Path home = (new File(System.getProperty("user.home"))).toPath()
            Path path = home.resolve(".quill").resolve("autocomplete")
            def ostream = LaTeXArea.class.getClassLoader().getResourceAsStream("autocomplete")
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
        
        if (quillMap == null) {
            def defaultKeymap = JTextComponent.getKeymap(JTextComponent.DEFAULT_KEYMAP)
            quillMap = JTextComponent.addKeymap("quillMap", defaultKeymap)
             
            Path home = (new File(System.getProperty("user.home"))).toPath()
            Path path = home.resolve(".quill").resolve("shortcuts")
            def ostream = LaTeXArea.class.getClassLoader().getResourceAsStream("shortcuts")
            if (Files.notExists(path)) {
                Files.copy(ostream, path)
            }
            assert Files.exists(path)
            
            Properties p = new Properties()
            p.load(Files.newInputStream(path))
            Enumeration e = p.propertyNames()

            while (e.hasMoreElements()) {                
                def key = e.nextElement().toString()
                def value = p.getProperty(key, null)
                if (value == null || value.length() == 0)
                    continue
                    
                int keycode = KeyEvent.getExtendedKeyCodeForChar((int)key.charAt(0))
                KeyStroke ks = KeyStroke.getKeyStroke(keycode, InputEvent.CTRL_DOWN_MASK)
                quillMap.addActionForKeyStroke(ks, new TextAction("LaTeX-action") {                        
                    @Override
                    void actionPerformed(ActionEvent ae) {
                        JTextArea source = (JTextArea)ae.getSource()
                        source.insert(value, source.getCaretPosition())
                    }
                })
            }
            
            KeyStroke s = KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK)
            quillMap.addActionForKeyStroke(s, new TextAction("LaTeX-action") {
                
                @Override
                void actionPerformed(ActionEvent ae) {
                    LaTeXArea.editor.save()
                }
            })
        }
        return new LaTeXArea(tex, row, col)
    } 
    
    public LaTeXArea(String tex, int row, int col) {
        super(tex, row, col)
        setCodeFoldingEnabled(true)
        setPreferredFont()
        addAutoComplete()
        addShortCuts()
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
    
    private def addShortCuts() {
        this.setKeymap(quillMap)
    }
    
    static Editor editor
    private static DefaultCompletionProvider completionProvider
    private static SpellingParser spellingParser
    private static Keymap quillMap
    
}
