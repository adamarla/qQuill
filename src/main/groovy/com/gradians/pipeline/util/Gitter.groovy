package com.gradians.pipeline.util

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.MergeResult
import org.eclipse.jgit.api.Status
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.StoredConfig
import org.eclipse.jgit.lib.RefUpdate.Result
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.FetchResult
import org.eclipse.jgit.transport.PushResult
import org.eclipse.jgit.transport.TrackingRefUpdate

class Gitter {
            
    public Gitter(File repoLocation) {
        repo = loadRepo(repoLocation)
        git = new Git(repo)
    }
    
    Set<String> toAdd(String path) {
        Status status = git.status().addPath(path).call()
        status.getUntracked() + status.getModified()
    }
    
    Set<String> toDelete(String path) {
        Status status = git.status().addPath(path).call()
        status.getMissing()
    }
    
    Set<String> toCommit(String path) {
        Status status = git.status().addPath(path).call()
        status.getAdded() + status.getRemoved() + status.getChanged()
    }
    
    void printStatus(String path) {
        Status status = git.status().addPath(path).call()
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
    
    void commit(String path, Set<String> toAdd, Set<String> toDelete, String message) {
        toAdd.each { file ->
            git.add().addFilepattern(file).call()
        }        
        toDelete.each { file ->
            git.rm().addFilepattern(file).call()
        }        
        if (toCommit(path).size() > 0) {
            String[] lines = message.split("\n")
            StringBuilder sb = new StringBuilder()
            lines.each {
                if (!it.startsWith("#"))
                    sb.append(it).append("\n")
            }
            git.commit().setMessage(sb.toString()).call()
        }
    }
    
    void pullFromUpstream() {
        FetchResult fetchResult = git.fetch().setRefSpecs(["remotes/origin/master"]).call()
        MergeResult mergeResult = git.merge().call()
        println mergeResult.getMergeStatus()
//        TrackingRefUpdate refUpdate = fetchResult.getTrackingRefUpdate("refs/remotes/origin/master")
//        Result result = refUpdate.getResult()
//        println result    
    }
    
    void pushToRemote() {
        Iterable<PushResult> iterable = git.push().call()
        PushResult pushResult = iterable.iterator().next()
        Status status = pushResult.getRemoteUpdate("refs/heads/master").getStatus()
        println status
    }
    
    private Repository loadRepo(File repoLocation) {
        new FileRepositoryBuilder()
            .setMustExist(true)        
            .setGitDir(repoLocation).build()
    }
    
    private Git git
    private Repository repo
    
}
