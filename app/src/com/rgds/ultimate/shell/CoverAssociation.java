package com.rgds.ultimate.shell;
import java.util.*;
import org.json.*;

/** Artwork association only. Never promotes a work match to a binary release. */
final class CoverAssociation {
    static String artworkTitle(String name){return MetadataIndex.workRevision(name).replaceAll(" \\(Beta(?: [0-9]+)?\\)","");}
    final String path,reason;final MetadataIndex.Release source;final boolean review;
    JSONArray candidates=new JSONArray();
    CoverAssociation(String p,String why,MetadataIndex.Release r,boolean ask){path=p;reason=why;source=r;review=ask;}
    interface Probe { String digest(String path)throws Exception; }
    private static List<MetadataIndex.Release> artworkRows(ResolveDecision d,MetadataIndex index){
        List<MetadataIndex.Release> rows=new ArrayList<>();
        if(d==null||d.work.isEmpty()||d.needsInput())return rows;
        Collection<MetadataIndex.Release> source=d.release!=null?Collections.singletonList(d.release):d.candidates;
        // An unknown serial may identify a work without proposing any ROM release.
        // Enumerate same-work catalogue rows only as artwork provenance; never
        // mutate the resolver's release/candidate identity or infer a binary.
        if(d.release==null&&d.candidates.isEmpty())source=index.ids.values();
        for(MetadataIndex.Release r:source)if(d.work.equals(r.work))rows.add(r);
        rows.sort(Comparator.comparing(MetadataIndex.Release::id));return rows;
    }
    static JSONArray candidates(ResolveDecision d,MetadataIndex index)throws JSONException{
        JSONArray out=new JSONArray();if(d==null||d.work.isEmpty()||d.needsInput())return out;
        Map<String,MetadataIndex.Release> paths=new TreeMap<>();
        for(MetadataIndex.Release r:artworkRows(d,index)){
            String path=index.imagePath(r);if(!path.isEmpty())paths.put(path,r);
        }
        for(Map.Entry<String,MetadataIndex.Release> row:paths.entrySet()){MetadataIndex.Release r=row.getValue();out.put(new JSONObject().put("path",row.getKey()).put("source","libretro-thumbnails").put("sourceReleaseId",r.id()).put("region",r.region).put("title",r.name).put("status","UNCHECKED").put("sha256","").put("failure",""));}
        return out;
    }
    static CoverAssociation verifyDirect(ResolveDecision d,MetadataIndex index,Probe probe)throws Exception{
        CoverAssociation initial=choose(d,index);
        if(!index.directExactPaths||!initial.reason.equals("MULTIPLE_ARTWORK_RESOURCES"))return initial;
        MetadataIndex verified=new MetadataIndex();verified.thumbnailsReady=true;
        Set<String> paths=new TreeSet<>();for(MetadataIndex.Release r:artworkRows(d,index))paths.add(index.imagePath(r));
        verified.ids.putAll(index.ids);
        for(String path:paths){String digest=probe.digest(path);if(!digest.isEmpty()){verified.thumbnails.add(path);verified.thumbnailBlobs.put(path,digest);}}
        return choose(d,verified);
    }
    static CoverAssociation choose(ResolveDecision d,MetadataIndex index){
        if(d==null||d.work.isEmpty()||d.needsInput())return new CoverAssociation("","IDENTITY_EVIDENCE_REQUIRED",null,true);
        List<MetadataIndex.Release> rows=artworkRows(d,index);
        if(rows.isEmpty())return new CoverAssociation("","ARTWORK_WORK_CROSSWALK_MISSING",null,false);
        if(d.release!=null)return new CoverAssociation(index.imagePath(d.release),"SOURCE_RELEASE_ARTWORK",d.release,false);
        String region=rows.get(0).region,base=artworkTitle(rows.get(0).name);
        for(MetadataIndex.Release r:rows)if(!region.equals(r.region)||!base.equals(artworkTitle(r.name)))
            return new CoverAssociation("","DISTINCT_REGIONAL_OR_EDITION_ARTWORK",null,true);
        Map<String,MetadataIndex.Release> paths=new TreeMap<>();
        for(MetadataIndex.Release r:rows){String path=index.imagePath(r);if(!path.isEmpty())paths.put(path,r);}
        // A downloaded index may prove that several revisions have only one
        // image. Direct-path fallback cannot make that claim for several URLs.
        if(paths.size()>1){Set<String> blobs=new HashSet<>();for(String path:paths.keySet())blobs.add(index.thumbnailBlobs.getOrDefault(path,path));
            if(blobs.size()>1)return new CoverAssociation("","MULTIPLE_ARTWORK_RESOURCES",null,true);
        }
        if(paths.isEmpty())return new CoverAssociation("","INDEX_KEY_ABSENT",null,false);
        Map.Entry<String,MetadataIndex.Release> one=paths.entrySet().iterator().next();
        return new CoverAssociation(one.getKey(),"WORK_ARTWORK_RELEASE_UNCONFIRMED",one.getValue(),false);
    }
    JSONObject json()throws JSONException{return new JSONObject().put("path",path).put("reason",reason).put("sourceReleaseId",source==null?"":source.id()).put("region",source==null?"":source.region).put("needsUserChoice",review).put("binaryDigestVerified",false).put("candidates",candidates);}
}
