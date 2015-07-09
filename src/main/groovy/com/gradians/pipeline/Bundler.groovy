package com.gradians.pipeline

import groovy.util.slurpersupport.GPathResult
import groovy.xml.MarkupBuilder
import groovy.xml.StreamingMarkupBuilder
import groovy.xml.XmlUtil

import java.io.FileInputStream
import java.io.PrintWriter
import java.net.URI
import java.nio.file.DirectoryStream
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HashMap
import java.util.List
import java.util.Map

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING
import static java.nio.charset.StandardCharsets.UTF_8

class Bundler {
    
    Question q
    Path bank, vault, bundles
    
    def Bundler(Question q) {
        this.q = q
    }
    
    def bundle() {
        bank = q.qpath.getParent().getParent().getParent().getParent()
        vault = bank.resolve("vault") 
        bundles = bank.resolve("bundles")
        
        def xmlPath = q.qpath.resolve("bundle.xml")
        assert Files.exists(xmlPath)
        
        // Add
        def bundleXml = new XmlSlurper().parse(xmlPath.toFile())
        def bundleId = bundleXml.bundleId.toString()
        
        Path zip = bundles.resolve(String.format("%s.zip", bundleId))
        FileSystem fs
        if (Files.notExists(zip))
            fs = create(zip)
        else
            fs = FileSystems.newFileSystem(zip, null)
        
        (new Renderer(q)).toSVG()
        
        Path srcDir = vault.resolve(q.qpath)
        Path destDir = fs.getPath(fs.getPath("/").toString(), q.uid.replace('/', '-'))
        addQuestion(q.qpath, destDir)        
        addToOrUpdateManifest(fs, bundleId, bundleXml)
        (new Network()).updateBundleSignature(bundleId, getSHA1Sum(zip))
        
        Path oldBundleXml = q.qpath.resolve("old_bundle.xml")
        if (Files.notExists(oldBundleXml)) {
            return
        }
        
        // Remove        
        bundleXml = new XmlSlurper().parse(oldBundleXml.toFile())
        if (bundleId.equals(bundleXml.bundleId.toString()))
            return
        else
            bundleId = bundleXml.bundleId.toString()
        
        zip = bundles.resolve(String.format("%s.zip", bundleId))
        if (Files.notExists(zip)) {
            return
        }
        
        fs = FileSystems.newFileSystem(zip, null)
        destDir = fs.getPath(fs.getPath("/").toString(), q.uid.replace('/', '-'))
        deleteQuestion(destDir)
        removeFromManifest(fs, bundleId, bundleXml)
        (new Network()).updateBundleSignature(bundleId, getSHA1Sum(zip))        
    }
    
    private def addToOrUpdateManifest(FileSystem fs, String bundleId, GPathResult bundleXml) {
        String manifestName = String.format("%s.xml", bundleId)
        Path tmpManifest = bundles.resolve(manifestName)
        final Path root = fs.getPath("/")
        if (Files.exists(root.resolve(manifestName))) {
            tmpManifest = Files.copy(fs.getPath("/").resolve(manifestName),
                tmpManifest, REPLACE_EXISTING)
        } else {
            tmpManifest.toFile().write(String.format(MANIFEST_TXT, bundleId))
        }
        
        GPathResult manifestXml = new XmlSlurper().parse(tmpManifest.toFile())
        def qsnEntry = manifestXml.'*'.find { node ->
                node.@id == bundleXml.questionId.toString()
        }
        if (qsnEntry.isEmpty()) {
            manifestXml.appendNode {
                question(
                    tag: q.uid.replace('/', '-'),
                    label: bundleXml.label.toString(),
                    id: bundleXml.questionId.toString(),
                    signature: getSHA1Sum(q.qpath.resolve(this.QSN_XML))
                )
            }
        } else {
            qsnEntry.@label = bundleXml.label.toString()
            qsnEntry.@signature = getSHA1Sum(q.qpath.resolve(this.QSN_XML))
        }

        (new XmlUtil()).serialize(manifestXml, new FileWriter(tmpManifest.toFile()))
        Files.copy(tmpManifest, root.resolve(manifestName), REPLACE_EXISTING)
        Files.delete(tmpManifest)
        fs.close()
    }    

    private def removeFromManifest(FileSystem fs, String bundleId, GPathResult bundleXml) {
        String manifestName = String.format("%s.xml", bundleId)
        Path tmpManifest = bundles.resolve(manifestName)
        final Path root = fs.getPath("/")
        if (Files.exists(root.resolve(manifestName))) {
            tmpManifest = Files.copy(fs.getPath("/").resolve(manifestName),
                tmpManifest, REPLACE_EXISTING)
        } else {
            return
        }
        
        GPathResult manifestXml = new XmlSlurper().parse(tmpManifest.toFile())
        def qsnEntry = manifestXml.'*'.find { node ->
                node.@id == bundleXml.questionId.toString()
        }        
        if (!qsnEntry.isEmpty()) {
           qsnEntry.replaceNode { }
        }

        (new XmlUtil()).serialize(manifestXml, new FileWriter(tmpManifest.toFile()))
        Files.copy(tmpManifest, root.resolve(manifestName), REPLACE_EXISTING)
        Files.delete(tmpManifest)
        fs.close()
    }    

    private def addQuestion(Path srcDir, Path destDir) {
        if (Files.notExists(destDir)) 
            Files.createDirectory(destDir)
                 
        DirectoryStream<Path> stream = Files.newDirectoryStream(srcDir, "*.svg")
        for (Path entry: stream) {
            // http://stackoverflow.com/questions/22605666/java-access-files-in-jar-causes-java-nio-file-filesystemnotfoundexception
            // Yes, that is something which is not very well documented... You
            // should .resolve() or .relativize() the .toString() values of other paths
            // if these paths are not from the same filesystem provider
            Files.copy(entry, destDir.resolve(entry.getFileName().toString()), REPLACE_EXISTING)
        }
        Files.copy(srcDir.resolve(QSN_XML), destDir.resolve(QSN_XML), REPLACE_EXISTING)
    }
    
    private def deleteQuestion(Path destDir) {
        if (Files.notExists(destDir))
            return                 
        DirectoryStream<Path> stream = Files.newDirectoryStream(destDir, "*.svg")
        for (Path entry: stream) {
            Files.delete(destDir.resolve(entry.getFileName().toString()))
        }
        Files.delete(destDir.resolve(QSN_XML))
        Files.delete(destDir)
    }
    
    private def create(Path zipPath) {
        final URI uri = URI.create("jar:file:" + zipPath.toUri().getPath())       
        final Map<String, String> env = new HashMap<>()
        env.put("create", "true")
        FileSystems.newFileSystem(uri, env)
    }
    
    def getSHA1Sum(Path path) {
        MessageDigest md = MessageDigest.getInstance("SHA-1")
        int bytesRead = 0
        byte[] byteBuf = new byte[1024]
        File file = path.toFile()
        java.io.InputStream is = new FileInputStream(file)
        while ((bytesRead = is.read(byteBuf)) != -1) {
            md.update(byteBuf, 0, bytesRead)
        }
        is.close()
        
        byte[] SHA1digest = md.digest()
        StringBuffer sb = new StringBuffer()
        for (byte b : SHA1digest){
            sb.append(String.format("%02x", b))
        }
        return sb.toString().substring(0, 12)
    }
    
    final String QSN_XML = "question.xml"
    final String MANIFEST_TXT = '''<exercise tag="%s"></exercise>'''
}
