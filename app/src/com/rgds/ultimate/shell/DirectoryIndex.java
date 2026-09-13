package com.rgds.ultimate.shell;

import android.net.Uri;
import org.json.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** Opaque SAF IDs and explicit parent edges. No content-URI/path splitting. */
final class DirectoryIndex {
    static final class Folder {
        final String id,rootId,documentId,parentId,name;
        int directGames,totalGames;
        Folder(String r,String d,String p,String n){rootId=r;documentId=d;id=key(r,d);parentId=p;name=n;}
    }
    static final class Picture {
        final String parentId,name,uri;
        Picture(String p,String n,String u){parentId=p;name=n;uri=u;}
    }
    static final class Item {
        final Folder folder;final GameEntry game;
        final int matchingGames;
        Item(Folder f){this(f,f.totalGames);}Item(Folder f,int matching){folder=f;game=null;matchingGames=matching;} Item(GameEntry g){folder=null;game=g;matchingGames=0;}
        String key(){return game==null?"folder:"+folder.id:game.gameId;}
    }
    final String tree,rootId;
    final Map<String,Folder> folders=new LinkedHashMap<>();
    final Map<String,String> gameParents=new HashMap<>();
    final List<Picture> pictures=new ArrayList<>();
    final Map<String,List<Folder>> children=new HashMap<>();
    final Map<String,Set<String>> ancestors=new HashMap<>();
    DirectoryIndex(String t,String rootDocument,String rootName){tree=t;rootId=key(t,rootDocument);add(rootDocument,"",rootName);}
    static String key(String root,String doc){return UUID.nameUUIDFromBytes((root+"\n"+doc).getBytes(StandardCharsets.UTF_8)).toString();}
    Folder add(String doc,String parent,String name){Folder f=new Folder(tree,doc,parent,name);folders.put(f.id,f);return f;}
    void finish(){
        ancestors.clear();
        children.clear();for(Folder f:folders.values()){f.totalGames=0;f.directGames=0;if(!f.parentId.isEmpty())children.computeIfAbsent(f.parentId,k->new ArrayList<>()).add(f);}
        for(List<Folder> list:children.values())list.sort(Comparator.comparing(f->f.name,String.CASE_INSENSITIVE_ORDER));
        for(Map.Entry<String,String> edge:gameParents.entrySet()){
            String parent=edge.getValue();
            Folder f=folders.get(parent);if(f!=null)f.directGames++;
            Set<String> seen=new HashSet<>();
            while(f!=null&&seen.add(f.id)){f.totalGames++;f=folders.get(f.parentId);}ancestors.put(edge.getKey(),Collections.unmodifiableSet(seen));
        }
    }
    boolean containsGame(String folder,String game){return ancestors.getOrDefault(game,Collections.emptySet()).contains(folder);}
    String path(String id){
        LinkedList<String> parts=new LinkedList<>();Set<String> seen=new HashSet<>();Folder f=folders.get(id);
        while(f!=null&&seen.add(f.id)){parts.addFirst(f.name);f=folders.get(f.parentId);}return String.join(" / ",parts);
    }
    List<Folder> childFolders(String id){List<Folder> x=children.get(id);return x==null?Collections.emptyList():x;}
    JSONObject json()throws JSONException{
        JSONArray fs=new JSONArray(),gs=new JSONArray(),ps=new JSONArray();
        for(Folder f:folders.values())fs.put(new JSONObject().put("documentId",f.documentId).put("parentId",f.parentId).put("name",f.name));
        for(Map.Entry<String,String> e:gameParents.entrySet())gs.put(new JSONArray().put(e.getKey()).put(e.getValue()));
        for(Picture p:pictures)ps.put(new JSONArray().put(p.parentId).put(p.name).put(p.uri));
        return new JSONObject().put("schema",4).put("tree",tree).put("rootDocument",folders.get(rootId).documentId).put("rootName",folders.get(rootId).name).put("folders",fs).put("games",gs).put("pictures",ps);
    }
    static DirectoryIndex parse(JSONObject o)throws JSONException{
        DirectoryIndex d=new DirectoryIndex(o.getString("tree"),o.getString("rootDocument"),o.getString("rootName"));
        JSONArray f=o.getJSONArray("folders");if(f.length()>20000)throw new JSONException("Too many folders");
        for(int i=0;i<f.length();i++){JSONObject v=f.getJSONObject(i);d.add(v.getString("documentId"),v.getString("parentId"),v.getString("name"));}
        JSONArray g=o.getJSONArray("games");for(int i=0;i<g.length();i++){JSONArray v=g.getJSONArray(i);d.gameParents.put(v.getString(0),v.getString(1));}
        JSONArray p=o.optJSONArray("pictures");if(p!=null)for(int i=0;i<Math.min(20000,p.length());i++){JSONArray v=p.getJSONArray(i);d.pictures.add(new Picture(v.getString(0),v.getString(1),v.getString(2)));}
        d.finish();return d;
    }
}
