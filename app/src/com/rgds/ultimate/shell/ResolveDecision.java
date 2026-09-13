package com.rgds.ultimate.shell;

import org.json.*;
import java.util.*;

/** One offline identity policy. Catalog association is never a measured ROM digest. */
final class ResolveDecision {
    static final String VERSION="resolve-112.1";
    enum Kind { UNIQUE_RELEASE, WORK_ONLY, CONFLICT, UNKNOWN_MAPPING, INSUFFICIENT_EVIDENCE, USER_CONFIRMED, USER_CONFLICT, MISSING }
    final Kind kind;
    final String work,reason,inputFingerprint;
    final MetadataIndex.Release release;
    final List<MetadataIndex.Release> candidates;
    ResolveDecision(Kind k,String w,MetadataIndex.Release r,List<MetadataIndex.Release> rows,String why,String fingerprint){
        kind=k;work=w;release=r;candidates=Collections.unmodifiableList(new ArrayList<>(rows));reason=why;inputFingerprint=fingerprint;
    }
    static String banner(GameEntry g){int end=g.bannerTitle.indexOf('\n');return end<0?g.bannerTitle:g.bannerTitle.substring(0,end);}
    static String fingerprint(String code,String header,String banner){
        // GameEntry trims decoded strings; catalog keys must use exactly the
        // same boundary normalization. Preserve internal spaces and punctuation.
        String line=banner==null?"":banner.trim();int end=line.indexOf('\n');
        if(end>=0)line=line.substring(0,end);
        return (code==null?"":code.trim())+"\n"+(header==null?"":header.trim())+"\n"+line.trim();
    }
    static String fingerprint(GameEntry g){return fingerprint(g.gameCode,g.internalTitle,g.bannerTitle);}
    static String name(String value){String s=java.text.Normalizer.normalize(value.replace("™","").replace("®","").replace("©",""),java.text.Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replace("+"," plus ");StringBuilder out=new StringBuilder();for(int i=0;i<s.length();i++)if(Character.isLetterOrDigit(s.charAt(i)))out.append(s.charAt(i));return out.toString();}
    static Set<String> bannerNames(GameEntry g){Set<String> out=new HashSet<>();String joined="";for(String line:g.bannerTitle.split("\\r?\\n")){joined+=line;String n=name(joined);if(!n.isEmpty())out.add(n);}return out;}
    static ResolveDecision resolve(GameEntry g,MetadataIndex index,JSONObject user){
        String input=MetadataManager.hash(g.gameCode+"\n"+g.internalTitle+"\n"+g.bannerTitle+"\n"+g.basename());
        List<MetadataIndex.Release> same=new ArrayList<>(index.serials.getOrDefault(g.gameCode,Collections.emptyList()));
        same.sort(Comparator.comparing(MetadataIndex.Release::id));
        String chosen=user==null?"":user.optString("externalId"),explicit=user==null?"":user.optString("workId");
        if(!chosen.isEmpty()){
            MetadataIndex.Release r=index.ids.get(chosen);
            if(r==null||!explicit.isEmpty()&&!explicit.equals(r.work))return new ResolveDecision(Kind.USER_CONFLICT,explicit,null,same,"USER_WORK_RELEASE_CONFLICT",input);
            return new ResolveDecision(Kind.USER_CONFIRMED,explicit.isEmpty()?r.work:explicit,r,same,"USER_CONFIRMED_CATALOG_RELEASE",input);
        }
        if(!explicit.isEmpty())return new ResolveDecision(Kind.WORK_ONLY,explicit,null,same,"USER_CONFIRMED_WORK_ONLY",input);
        Set<String> verified=index.fingerprints.getOrDefault(fingerprint(g),Collections.emptySet());
        Set<String> titles=bannerNames(g);Set<String> works=new LinkedHashSet<>(),supported=new LinkedHashSet<>();boolean unsafe=false,unmapped=false;
        boolean suspicious=g.internalTitle.trim().isEmpty()||g.internalTitle.matches("(?i).*(HOMEBREW|R4MENU|MULTICART).*" );
        for(MetadataIndex.Release r:same){
            if(MetadataIndex.strongConflict(g.internalTitle,r.name))unsafe=true;
            if(r.work.isEmpty()){unmapped=true;continue;}else works.add(r.work);
            Set<String> aliases=index.workAliases.getOrDefault(r.work,Collections.emptySet());
            if(verified.contains(r.id())||!Collections.disjoint(titles,aliases))supported.add(r.work);
        }
        if(!suspicious&&!unsafe&&works.size()==1&&supported.size()==1){
            String work=works.iterator().next();
            boolean modified=g.bannerTitle.matches("(?s).*(汉化|漢化|APEX|修正版|CrystalTile).*" );
            if(modified)return new ResolveDecision(Kind.WORK_ONLY,work,null,same,"MODIFIED_BANNER_WORK_IDENTITY_ONLY",input);
            if(same.size()==1)return new ResolveDecision(Kind.UNIQUE_RELEASE,work,same.get(0),same,"FULL_CODE_AND_REVIEWED_HEADER_OR_BANNER",input);
            return new ResolveDecision(Kind.WORK_ONLY,work,null,same,"SAME_WORK_EXACT_REVISION_UNKNOWN",input);
        }
        if(same.isEmpty()){
            // Modified/unknown serials can identify a work only when two separate
            // catalog facts agree: a complete reviewed banner name and an exact
            // internal title already observed in a reviewed catalog header observation.
            // Never infer a release, digest or revision from these string fields.
            Set<String> bannerWorks=new LinkedHashSet<>(),headerWorks=new LinkedHashSet<>();
            for(Map.Entry<String,Set<String>> entry:index.workAliases.entrySet())
                if(!Collections.disjoint(titles,entry.getValue()))bannerWorks.add(entry.getKey());
            String header=g.internalTitle.trim();
            if(!suspicious&&!header.isEmpty()&&bannerWorks.size()==1){
                for(Map.Entry<String,Set<String>> entry:index.fingerprints.entrySet()){
                    String[] fields=entry.getKey().split("\n",-1);
                    if(fields.length!=3||!fields[1].equals(header))continue;
                    for(String releaseId:entry.getValue()){
                        MetadataIndex.Release known=index.ids.get(releaseId);
                        if(known!=null&&!known.work.isEmpty())headerWorks.add(known.work);
                    }
                }
                String work=bannerWorks.iterator().next();
                if(headerWorks.size()==1&&headerWorks.contains(work))
                    return new ResolveDecision(Kind.WORK_ONLY,work,null,same,"UNKNOWN_SERIAL_REVIEWED_BANNER_AND_CATALOG_HEADER_WORK_ONLY",input);
            }
            LinkedHashMap<String,MetadataIndex.Release> proposed=new LinkedHashMap<>();
            for(String n:new String[]{g.basename(),g.internalTitle,g.bannerTitle})for(MetadataIndex.Release r:index.names.getOrDefault(MetadataIndex.normalize(n),Collections.emptyList()))proposed.put(r.id(),r);
            same.addAll(proposed.values());same.sort(Comparator.comparing(MetadataIndex.Release::id));
        }
        Kind kind=same.isEmpty()?Kind.MISSING:unsafe||works.size()>1?Kind.CONFLICT:unmapped&&works.isEmpty()?Kind.UNKNOWN_MAPPING:Kind.INSUFFICIENT_EVIDENCE;
        String reason=kind==Kind.MISSING?"NO_LOCAL_EVIDENCE":kind==Kind.CONFLICT?"HEADER_OR_WORK_CONFLICT":kind==Kind.UNKNOWN_MAPPING?"CATALOG_CROSSWALK_MISSING":suspicious?"NON_RETAIL_HEADER_REQUIRES_EVIDENCE":"INSUFFICIENT_INDEPENDENT_EVIDENCE";
        return new ResolveDecision(kind,"",null,same,reason,input);
    }
    boolean needsInput(){return kind==Kind.CONFLICT||kind==Kind.USER_CONFLICT||kind==Kind.UNKNOWN_MAPPING||kind==Kind.INSUFFICIENT_EVIDENCE;}
    JSONObject json(String catalog)throws JSONException{return new JSONObject().put("kind",kind.name()).put("workId",work).put("releaseId",release==null?"":release.id()).put("reason",reason).put("catalogVersion",catalog).put("resolverVersion",VERSION).put("inputFingerprint",inputFingerprint).put("binaryDigestVerified",false);}
    MetadataIndex.Match match(){MetadataIndex.Match m=new MetadataIndex.Match();m.release=release;m.candidates.addAll(candidates);m.status=release!=null?"IDENTIFIED":kind==Kind.WORK_ONLY?"WORK_IDENTIFIED":needsInput()?"CANDIDATE":"UNIDENTIFIED";m.basis=reason;return m;}
}
