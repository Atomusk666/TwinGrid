package com.rgds.ultimate.shell;
import java.util.*;
import org.json.*;

/** A scope is a description over the current library, never yesterday's result IDs. */
final class BrowseQuery {
    static final int VERSION=1;
    final String category,folder,genre,text;
    final boolean descendants;final int sort;
    final long libraryVersion,metadataVersion,overrideVersion;
    BrowseQuery(String c,String f,boolean d,String g,String t,int s,long l,long m,long o){category=c;folder=f;descendants=d;genre=GenreTaxonomy.id(g);text=t;sort=s;libraryVersion=l;metadataVersion=m;overrideVersion=o;}
    String exclusion(String id,List<String> types,Set<String> favorites,Set<String> recent,DirectoryIndex dirs){
        if(category.equals("FAVORITES")&&!favorites.contains(id))return "NOT_FAVORITE";
        if(category.equals("RECENT")&&!recent.contains(id))return "NOT_RECENT";
        if(category.equals("FOLDERS")){
            if(dirs==null)return "DIRECTORY_INDEX_UNAVAILABLE";
            if(!folder.equals(dirs.gameParents.get(id))&&!(descendants&&dirs.containsGame(folder,id)))return dirs.containsGame(folder,id)?"CHILD_DIRECTORY_ONLY":"DIFFERENT_DIRECTORY";
        }
        if(!genre.equals("all")&&!(genre.equals("unclassified")?types.isEmpty():types.contains(genre)))return types.isEmpty()?"NO_EFFECTIVE_TYPE":"TYPE_MISMATCH";
        return "";
    }
    JSONObject intentJson()throws JSONException{return new JSONObject().put("schema",VERSION).put("category",category).put("folderId",category.equals("FOLDERS")?folder:"").put("includeDescendants",descendants).put("genre",genre).put("text",text).put("sort",sort);}
    JSONObject json()throws JSONException{return intentJson().put("folderId",folder).put("libraryVersion",libraryVersion).put("metadataVersion",metadataVersion).put("overrideVersion",overrideVersion);}
}
