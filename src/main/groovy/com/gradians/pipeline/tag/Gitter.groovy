package com.gradians.pipeline.tag

import com.gradians.pipeline.Config
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.Status
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder

class Gitter {
        
    public Gitter(File repoLocation) {
        Repository repo = loadRepo(repoLocation)        
        assert repo != null
        git = new Git(repo)
    }
    
    Set<String> toAdd() {
        Status status = git.status().call()
        status.getUntracked() + status.getModified()
    }
    
    Set<String> toDelete() {
        Status status = git.status().call()
        status.getMissing()
    }
    
    Set<String> toCommit() {
        Status status = git.status().call()
        status.getAdded() + status.getRemoved() + status.getChanged()
    }
    
    void printStatus() {
        Status status = git.status().call()
        System.out.println("Added: " + status.getAdded());
        System.out.println("Changed: " + status.getChanged());
        System.out.println("Conflicting: " + status.getConflicting());
        System.out.println("ConflictingStageState: " + status.getConflictingStageState());
        System.out.println("IgnoredNotInIndex: " + status.getIgnoredNotInIndex());
        System.out.println("Missing: " + status.getMissing());
        System.out.println("Modified: " + status.getModified());
        System.out.println("Removed: " + status.getRemoved());
        System.out.println("Untracked: " + status.getUntracked());
        System.out.println("UntrackedFolders: " + status.getUntrackedFolders());
    }
    
    void commit(Set<String> toAdd, Set<String> toDelete, String message) {
        toAdd.each { file ->
            git.add().addFilepattern(file).call()
        }        
        toDelete.each { file ->
            git.rm().addFilepattern(file).call()
        }        
        if (toCommit().size() > 0) {
            String[] lines = message.split("\n")
            StringBuilder sb = new StringBuilder()
            lines.each {
                if (!it.startsWith("#"))
                    sb.append(it).append("\n")
            }
            git.commit().setMessage(sb.toString()).call()
        }
    }
    
    void pushToRemote() {
        
    }
    
    private Repository loadRepo(File repoLocation) {
        new FileRepositoryBuilder()
            .setMustExist(true)        
            .setGitDir(repoLocation).build()
    }
    
    private Git git
    
}
