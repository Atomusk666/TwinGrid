package com.rgds.ultimate.shell;

import java.util.*;
import java.util.regex.*;
import java.text.Normalizer;
import org.json.*;

/** Public DAT parsing and conservative release linkage; never a local ROM deduplicator. */
final class MetadataIndex {
    static final String BASE="https://raw.githubusercontent.com/libretro/libretro-database/master/metadat/";
    static final String NDS="Nintendo%20-%20Nintendo%20DS.dat";
    static final String[] GENRES={"all","action","adventure","rpg","strategy","racing","sports","fighting","shooter","puzzle","music","simulation","utility","other","unclassified"};
    static final class Release {
        String name="",serial="",crc="",sha1="",region="",genre="",developer="",publisher="",work="",catalogId="";
        long size;
        String id(){return catalogId.isEmpty()?serial+":"+sha1:catalogId;}
        JSONObject json()throws JSONException{return new JSONObject().put("id",id()).put("workId",work).put("name",name).put("serial",serial).put("crc",crc).put("sha1",sha1).put("size",size).put("region",region).put("genre",genre).put("developer",developer).put("publisher",publisher);}
    }
    static final class Match {
        String status="UNIDENTIFIED",basis="";Release release;List<Release> candidates=new ArrayList<>();
    }
    final Map<String,List<Release>> serials=new HashMap<>(),names=new HashMap<>();
    final Map<String,Release> ids=new HashMap<>();
    final Map<String,Set<String>> workAliases=new HashMap<>(),fingerprints=new HashMap<>();
    final Set<String> thumbnails=new HashSet<>();
    final Map<String,String> thumbnailBlobs=new HashMap<>();
    boolean thumbnailsReady;
    boolean directExactPaths;
    String thumbnailVersion="";
    String thumbnailRevision="";
    static final Pattern BLOCK=Pattern.compile("(?ms)^game \\(\\s*(.*?)^\\)");
    static final Pattern BOARD_GENRE=Pattern.compile("\\b(?:cards?|board)\\b");
    static final Pattern QUOTED=Pattern.compile("(?:^|\\s)([A-Za-z_]+) \\\"([^\\\"]*)\\\""),CRC=Pattern.compile("\\bcrc ([A-Fa-f0-9]+)"),SHA=Pattern.compile("\\bsha1 ([A-Fa-f0-9]+)"),SIZE=Pattern.compile("\\bsize ([0-9]+)"),PUNCT=Pattern.compile("[\\p{Punct}\\s]+"),EXT=Pattern.compile("(?i)\\.nds$");
    static String hex(Pattern p,String block){Matcher m=p.matcher(block);return m.find()?m.group(1).toUpperCase(Locale.ROOT):"";}
    static String field(String block,String key){Matcher m=Pattern.compile("(?m)(?:^|\\s)"+key+" \\\"([^\\\"]*)\\\"").matcher(block);return m.find()?m.group(1):"";}
    static String hex(String block,String key){Matcher m=Pattern.compile("\\b"+key+" ([A-Fa-f0-9]+)").matcher(block);return m.find()?m.group(1).toUpperCase(Locale.ROOT):"";}
    void load(String data,String genre,String developer,String publisher){
        Map<String,String> genres=byCrc(genre,"genre"),devs=byCrc(developer,"developer"),pubs=byCrc(publisher,"publisher");
        Matcher m=BLOCK.matcher(data);
        while(m.find()){
            String b=m.group(1);Release r=new Release();Map<String,String> values=new HashMap<>();Matcher fields=QUOTED.matcher(b);while(fields.find())values.putIfAbsent(fields.group(1),fields.group(2));
            r.name=values.getOrDefault("name","");r.serial=values.getOrDefault("serial","");r.region=values.getOrDefault("region","");
            r.crc=hex(CRC,b);r.sha1=hex(SHA,b);Matcher size=SIZE.matcher(b);if(size.find())r.size=Long.parseLong(size.group(1));
            r.genre=genres.getOrDefault(r.crc,"");r.developer=devs.getOrDefault(r.crc,"");r.publisher=pubs.getOrDefault(r.crc,"");
            if(r.serial.isEmpty()||r.name.isEmpty()||r.sha1.length()!=40)continue;
            serials.computeIfAbsent(r.serial,k->new ArrayList<>()).add(r);names.computeIfAbsent(normalize(r.name),k->new ArrayList<>()).add(r);ids.put(r.id(),r);
        }
        if(ids.size()<1000)throw new IllegalArgumentException("Incomplete NDS identity index");
    }
    static Map<String,String> byCrc(String text,String key){
        Map<String,String> map=new HashMap<>();Matcher m=BLOCK.matcher(text);
        while(m.find()){String c=hex(m.group(1),"crc"),v=field(m.group(1),key);if(!c.isEmpty()&&!v.isEmpty())map.put(c,v);}return map;
    }
    static String normalize(String name){
        // Only documented dump annotations. Do not erase numbers, colors or regional identity to select a release.
        return PUNCT.matcher(EXT.matcher(Normalizer.normalize(name,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)).replaceFirst("")).replaceAll(" ").trim();
    }
    static String workRevision(String name){return name.replaceAll(" \\(Rev [0-9]+\\)","");}
    Match match(GameEntry g){return ResolveDecision.resolve(g,this,null).match();}
    static boolean strongConflict(String internal,String release){
        String source=release.replaceAll("\\([^)]*\\)","").toUpperCase(Locale.ROOT),header=internal.toUpperCase(Locale.ROOT);
        Matcher h=Pattern.compile("([0-9]+)\\s*$").matcher(header),n=Pattern.compile("\\b([0-9]+)\\s*$").matcher(source.trim());
        if(h.find()&&n.find()&&!h.group(1).replaceFirst("^0+(?!$)","").equals(n.group(1).replaceFirst("^0+(?!$)",""))){
            // Four-digit release years and compact internal year suffixes are not sequel numbers.
            // Positive identity still requires a reviewed fingerprint or an exact banner alias.
            boolean year=n.group(1).matches("(?:19|20)[0-9]{2}")||h.group(1).matches("(?:19|20)[0-9]{2}");
            boolean range=source.trim().matches(".*[0-9]+\\s*[-–—]\\s*[0-9]+$");
            if(!year&&!range)return true;
        }
        // Nintendo's abbreviated internal titles retain the distinguishing B/W token.
        if(header.matches("POKEMON B2?")&&source.contains("WHITE"))return true;
        if(header.matches("POKEMON W2?")&&source.contains("BLACK"))return true;
        return false;
    }
    void loadThumbnails(String json)throws JSONException{
        directExactPaths=false;
        // Stream only path strings; a full GitHub tree object creates tens of thousands of unused JSON nodes.
        try(android.util.JsonReader r=new android.util.JsonReader(new java.io.StringReader(json))){
            r.beginObject();while(r.hasNext()){
                String name=r.nextName();if(name.equals("revision")){thumbnailRevision=r.nextString();}else if(name.equals("truncated")){if(r.nextBoolean())throw new JSONException("Truncated image index");}
                else if(name.equals("tree")){r.beginArray();while(r.hasNext()){
                    r.beginObject();String path="",sha="";while(r.hasNext()){String key=r.nextName();if(key.equals("path"))path=r.nextString();else if(key.equals("sha"))sha=r.nextString();else r.skipValue();}r.endObject();
                    if(path.startsWith("Named_Boxarts/")&&path.endsWith(".png")){thumbnails.add(path);if(sha.matches("[a-f0-9]{40}"))thumbnailBlobs.put(path,sha);}
                }r.endArray();}else r.skipValue();
            }r.endObject();if(thumbnails.size()<1000)throw new JSONException("Incomplete image index");
            thumbnailsReady=true;thumbnailVersion=MetadataManager.hash(json);
        }catch(java.io.IOException e){throw new JSONException("Invalid image index: "+e.getClass().getSimpleName());}
    }
    static String imageKey(Release r){return "Named_Boxarts/"+r.name.replace('&','_').replace('*','_').replace('/','_').replace(':','_').replace('`','_').replace('<','_').replace('>','_').replace('?','_').replace('\\','_').replace('|','_')+".png";}
    void useDirectExactPaths(){thumbnails.clear();thumbnailBlobs.clear();directExactPaths=true;thumbnailsReady=true;thumbnailVersion="direct-exact-release-name-v1; existence-unverified";}
    String imagePath(Release r){if(!thumbnailsReady)throw new IllegalStateException("Image index not loaded");String path=imageKey(r);return directExactPaths||thumbnails.contains(path)?path:"";}
    static List<String> categories(String raw){return GenreTaxonomy.categories(raw);}
}
