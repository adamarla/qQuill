package com.gradians.pipeline.tag

import groovy.swing.SwingBuilder

import java.nio.file.Files

import javax.swing.BorderFactory
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.filechooser.FileFilter

import com.gradians.pipeline.util.Config;
import com.gradians.pipeline.util.Network;

import static javax.swing.WindowConstants.DISPOSE_ON_CLOSE

class Registration {

    Config config
    
    public Registration(Config config) {
        this.config = config
    }
    
    def launch() {
        def iconURL = Registration.class.getClassLoader().getResource("logo-prepwell.png")        
        SwingBuilder sb = new SwingBuilder()
        def pane1 = sb.optionPane(
            message: sb.panel() {
                label(text: 'Verify Email')
                textField(id: 'tfEmail', text: "your-email@gradians.com")
            },
            icon: new javax.swing.ImageIcon(iconURL),
            optionType: JOptionPane.YES_NO_OPTION,
            options: ["Next", "Cancel"].toArray(new String[2]))
        
        def dialog
        def userId, role, name, email
        while (true) {
            dialog = pane1.createDialog(null, 'Registration Step 1 - Welcome to Quill')
            dialog.visible = true
            email = sb.tfEmail.text
            dialog.dispose()
            
            String value = (String)pane1.getValue()
            if (value && !value.startsWith("Cancel")) {
                def json = Network.executeHTTPGet("quill/signin?email=${email}")
                if (json.allow) {
                    userId = json.id
                    role = json.role
                    name = json.name
                    break
                } else {
                    javax.swing.JOptionPane.showMessageDialog(null,
                        "Sorry your account is blocked",
                        "Error", javax.swing.JOptionPane.ERROR_MESSAGE)                    
                }
            } else {
                break
            }
        }
        if (!userId)
            return
            
        def vault
        def pane2 = sb.optionPane(
            message: sb.panel() {
                button(text: 'Locate Vault', actionPerformed: {
                    def locateVaultDialog = new JFileChooser(
                        dialogTitle: 'Locate vault',
                        fileSelectionMode: JFileChooser.DIRECTORIES_ONLY,
                        fileFilter: [ getDescription: { -> "Only directories" }, 
                            accept: { File file -> file.isDirectory() }] as FileFilter)
            
                    if (locateVaultDialog.showOpenDialog() == JFileChooser.APPROVE_OPTION) {
                        vault = locateVaultDialog.getSelectedFile().toPath()
                        sb.lblLocation.text = vault.toString()
                    }
                })
                label(text: 'path-to-vault-unspecified', id: 'lblLocation')
            },
            icon: new javax.swing.ImageIcon(iconURL),
            optionType: JOptionPane.YES_NO_OPTION,
            options: ["Finish", "Cancel"].toArray(new String[2]))
        
        while (true) {
            dialog = pane2.createDialog(null, "Registration Step 2 - Welcome ${name}")
            dialog.visible = true
            dialog.dispose()
            
            def value = (String)pane2.getValue()
            if (value.startsWith("Cancel")) {
                return
            } else {
                if (Files.exists(vault.resolve("bin/quill"))) {
                    config.registerUser(userId, email, role, vault.getParent().toString())
                    return
                } else {
                    javax.swing.JOptionPane.showMessageDialog(null,
                        "Specify valid path to vault directory",
                        "Error", javax.swing.JOptionPane.ERROR_MESSAGE)
                }
            }   
        }
    }
}
