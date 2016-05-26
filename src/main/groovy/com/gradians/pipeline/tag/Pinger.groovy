package com.gradians.pipeline.tag

import com.gradians.pipeline.data.Asset
import com.gradians.pipeline.util.Config
import com.gradians.pipeline.util.Network

class Pinger {

    Asset a
    
    public Pinger(Asset a) {
        this.a = a
    }
    
    void ping() {        
        // HTTP POST chapterId, skills to server
        def url = "${a.assetClass.toString().toLowerCase()}/tag"
        def skills = a.getEditGroups().collect { it.skills }.flatten().findAll { it != 0 }
        url = "${a.assetClass.toString().toLowerCase()}/tag"
        def map = [id: a.id, c: a.chapterId, skills: skills]
        Network.executeHTTPPostBody(url, map)    
    }
}
