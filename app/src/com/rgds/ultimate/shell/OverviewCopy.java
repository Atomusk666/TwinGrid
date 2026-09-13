package com.rgds.ultimate.shell;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.util.*;

/** Small reviewed drafts keyed to exact source content; never loaded from the whole catalog. */
final class OverviewCopy {
    private static Map<String,String> drafts;
    static synchronized String get(Context context,String locale,String source){
        if(drafts==null){drafts=new HashMap<>();try(InputStream in=context.getResources().openRawResource(R.raw.overview_copy)){
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;
            while((n=in.read(b))>0){if(bytes.size()+n>262144)throw new IOException("Overview draft size");bytes.write(b,0,n);}
            JSONArray rows=new JSONObject(bytes.toString("UTF-8")).getJSONArray("drafts");
            for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);drafts.put(row.getString("locale")+":"+row.getString("sourceSHA256"),row.getString("text"));}
        }catch(Exception ignored){drafts.clear();}}
        return drafts.getOrDefault(locale+":"+MetadataManager.hash(source),source);
    }
}
