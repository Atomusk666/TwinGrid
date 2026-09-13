package com.rgds.ultimate.shell;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

/** Versioned NDS-only derived index. Descriptions stay on disk until a worker requests one. */
final class OpenVgdbIndex {
    static final String URL="https://github.com/OpenVGDB/OpenVGDB/releases/download/v29.0/openvgdb.zip";
    static final String SHA256="2441ff51ce4b8d942d342c057eaccc83bd3f4eba3118bc73bbc2ae269cb0f329";
    static final String VERSION="OpenVGDB29/schema3/"+SHA256.substring(0,12);
    interface Download {byte[] get(String url,int limit)throws Exception;}
    interface Cancellation {void check()throws Exception;}
    final Map<String,List<JSONObject>> bySha1=new HashMap<>(),bySerial=new HashMap<>(),byWork=new HashMap<>();
    private SQLiteDatabase source;
    private static final Pattern WORK=Pattern.compile("https?://(?:www\\.)?gamefaqs\\.(?:com|gamespot\\.com)/ds/([0-9]+)(?:[-/?].*)?",Pattern.CASE_INSENSITIVE);
    static String workId(String url){Matcher m=WORK.matcher(url==null?"":url);return m.matches()?"gamefaqs:ds:"+m.group(1):"";}
    void load(File directory,Download download)throws Exception {load(directory,download,()->{});}
    void load(File directory,Download download,Cancellation cancellation)throws Exception {
        File filtered=new File(directory,"openvgdb_nds_v29_s3.sqlite");
        if(!filtered.isFile())importSource(directory,filtered,download,cancellation);
        source=SQLiteDatabase.openDatabase(filtered.toString(),null,SQLiteDatabase.OPEN_READONLY|SQLiteDatabase.NO_LOCALIZED_COLLATORS);
        try(Cursor c=source.rawQuery("SELECT version FROM info",null)){if(!c.moveToFirst()||!VERSION.equals(c.getString(0)))throw new IOException("Derived schema mismatch; original cache retained");}
        try(Cursor c=source.rawQuery("SELECT data FROM releases",null)){while(c.moveToNext()){
            if(c.getPosition()%128==0)cancellation.check();
            JSONObject o=new JSONObject(c.getString(0));String sha=o.optString("sha1").toUpperCase(Locale.ROOT);
            if(sha.length()!=40)continue;bySha1.computeIfAbsent(sha,k->new ArrayList<>()).add(o);
            bySerial.computeIfAbsent(o.optString("serial"),k->new ArrayList<>()).add(o);
            String work=workId(o.optString("reference"));if(!work.isEmpty())byWork.computeIfAbsent(work,k->new ArrayList<>()).add(o);
        }}
    }
    private void importSource(File dir,File filtered,Download download,Cancellation cancellation)throws Exception {
        File partial=new File(dir,"openvgdb_v29.part");byte[] zip=partial.isFile()&&partial.length()==9118645?read(partial,12*1024*1024):download.get(URL,12*1024*1024);
        if(!MetadataManager.hashBytes(zip).equals(SHA256))throw new IOException("OpenVGDB source integrity changed");
        File temp=new File(dir,"openvgdb_import.tmp.sqlite"),derived=new File(dir,"openvgdb_s3_import.tmp.sqlite");
        try {
            boolean found=false;int entries=0;
            try(ZipInputStream in=new ZipInputStream(new ByteArrayInputStream(zip))){ZipEntry e;while((e=in.getNextEntry())!=null){
                if(++entries>32)throw new IOException("ZIP entries limit");if(!e.getName().equals("openvgdb.sqlite"))continue;
                if(found)throw new IOException("Duplicate database");found=true;
                try(FileOutputStream out=new FileOutputStream(temp)){byte[] buf=new byte[32768];int n;long bytes=0;while((n=in.read(buf))!=-1){cancellation.check();bytes+=n;if(bytes>50L*1024*1024)throw new IOException("Expanded size limit");out.write(buf,0,n);}out.getFD().sync();}
            }}if(!found)throw new IOException("Missing named database");
            if(derived.exists()&&!derived.delete())throw new IOException("Private import temp locked");
            try(SQLiteDatabase input=SQLiteDatabase.openDatabase(temp.toString(),null,SQLiteDatabase.OPEN_READONLY|SQLiteDatabase.NO_LOCALIZED_COLLATORS);
                SQLiteDatabase output=SQLiteDatabase.openOrCreateDatabase(derived,null)){
              try {
                output.execSQL("CREATE TABLE releases(id TEXT PRIMARY KEY,data TEXT NOT NULL,description TEXT NOT NULL)");output.execSQL("CREATE TABLE info(version TEXT)");
                output.beginTransaction();int count=0;
                try(Cursor c=input.rawQuery("SELECT romHashSHA1,romHashCRC,romFileName,releaseID,releaseTitleName,releaseGenre,releaseDeveloper,releasePublisher,releaseDate,romSerial,releaseReferenceURL,releaseDescription,romLanguage,TEMPregionLocalizedName FROM ROMs JOIN RELEASES USING(romID) JOIN SYSTEMS USING(systemID) WHERE systemName='Nintendo DS'",null)){
                    String[] keys={"sha1","crc","romName","releaseId","title","genre","developer","publisher","date","serial","reference"};
                    while(c.moveToNext()){
                        if(++count%128==0)cancellation.check();if(count>15000)throw new IOException("NDS row limit");JSONObject o=new JSONObject();for(int i=0;i<keys.length;i++)o.put(keys[i],c.isNull(i)?"":c.getString(i));
                        String description=c.isNull(11)?"":c.getString(11);if(description.length()>20000)description=description.substring(0,20000);
                        o.put("hasDescription",!description.trim().isEmpty()).put("romLanguage",c.isNull(12)?"":c.getString(12)).put("localizedRegion",c.isNull(13)?"":c.getString(13));
                        output.execSQL("INSERT INTO releases VALUES(?,?,?)",new Object[]{o.getString("releaseId"),o.toString(),description});
                    }
                }if(count<1000)throw new IOException("Incomplete NDS dataset");
                output.execSQL("INSERT INTO info VALUES(?)",new Object[]{VERSION});output.setTransactionSuccessful();
              }finally{if(output.inTransaction())output.endTransaction();}
            }
            if(!derived.renameTo(filtered))throw new IOException("Atomic derived index publication failed");
            if(partial.isFile())partial.delete();
        }finally{if(temp.isFile())temp.delete();}
    }
    private List<JSONObject> anchors(MetadataIndex.Release release){
        List<JSONObject> result=new ArrayList<>();for(JSONObject row:bySha1.getOrDefault(release.sha1,Collections.emptyList()))if(row.optString("serial").equals(release.serial))result.add(row);
        if(result.isEmpty())for(JSONObject row:bySerial.getOrDefault(release.serial,Collections.emptyList()))
            if(MetadataIndex.workRevision(row.optString("romName").replaceFirst("(?i)\\.nds$","")).equals(MetadataIndex.workRevision(release.name)))result.add(row);
        return result;
    }
    JSONObject enrich(MetadataIndex.Release release)throws JSONException {
        List<JSONObject> anchors=anchors(release);Set<String> works=new HashSet<>();for(JSONObject row:anchors){String id=workId(row.optString("reference"));if(!id.isEmpty())works.add(id);}
        String work=works.size()==1?works.iterator().next():"";
        List<JSONObject> related=work.isEmpty()?Collections.emptyList():byWork.getOrDefault(work,Collections.emptyList());
        JSONObject result=new JSONObject().put("sourceVersion",VERSION).put("workId",work).put("anchors",new JSONArray(anchors));
        List<JSONObject> genres=nonempty(anchors,false);boolean shared=false;if(genres.isEmpty()){genres=nonempty(related,false);shared=!genres.isEmpty();}
        Set<String> allTypes=new HashSet<>();boolean unmapped=false;JSONObject richest=null;
        for(JSONObject row:genres){GenreTaxonomy.Result mapped=GenreTaxonomy.parse("OpenVGDB",row.optString("genre"));allTypes.addAll(mapped.categories);unmapped|=!mapped.unmapped.isEmpty();}
        for(JSONObject row:genres)if(GenreTaxonomy.parse("OpenVGDB",row.optString("genre")).categories.containsAll(allTypes)){richest=row;break;}
        String genreState=genres.isEmpty()?"MISSING_SOURCE":richest==null?"CONFLICT":unmapped?(allTypes.isEmpty()?"UNMAPPED":"PARTIAL"):"READY";result.put("genreState",genreState).put("genreRows",new JSONArray(genres)).put("genreMergePolicy","same NDS work; choose one source-supported superset; disjoint evidence remains conflict");
        if(richest!=null)result.put("genre",richest.optString("genre")).put("genreBasis",basis(shared,release,richest,work));
        JSONArray excluded=new JSONArray();JSONObject chosen=descriptionRow(anchors,excluded);shared=false;
        if(chosen==null){chosen=descriptionRow(related,excluded);shared=chosen!=null;}
        result.put("descriptionState",chosen==null?(excluded.length()>0?"MISSING_GAMEPLAY_TEXT":"MISSING_SOURCE"):"READY").put("descriptionExcluded",excluded);
        if(chosen!=null){String text=chosen.optString("original");chosen.remove("original");result.put("descriptionOriginal",text).put("descriptionSource",chosen).put("descriptionBasis",basis(shared,release,chosen,work)).put("descriptionShared",shared);}
        LinkedHashSet<String> aliases=new LinkedHashSet<>();for(JSONObject r:anchors)aliases.add(r.optString("title"));for(JSONObject r:related)aliases.add(r.optString("title"));aliases.remove("");result.put("aliases",new JSONArray(aliases));
        return result;
    }
    private JSONObject descriptionRow(List<JSONObject> rows,JSONArray excluded)throws JSONException {
        List<JSONObject> ordered=nonempty(rows,true);ordered.sort(Comparator.comparingInt(r->r.optString("localizedRegion").equals("USA")?0:1));
        for(JSONObject row:ordered){String text="";try(Cursor c=source.rawQuery("SELECT description FROM releases WHERE id=?",new String[]{row.optString("releaseId")})){if(c.moveToFirst())text=c.getString(0);}
            if(GameDescription.usable(GameDescription.clean(text)))return new JSONObject(row.toString()).put("original",text);
            excluded.put(new JSONObject().put("releaseId",row.optString("releaseId")).put("originalSHA256",MetadataManager.hash(text)).put("reason",UiStrings.msg("ui_6470fbb77ba4")));
        }return null;
    }
    private static List<JSONObject> nonempty(List<JSONObject> rows,boolean description){List<JSONObject> result=new ArrayList<>();for(JSONObject row:rows)if(description?row.optBoolean("hasDescription"):!row.optString("genre").trim().isEmpty())result.add(row);return result;}
    private static String basis(boolean shared,MetadataIndex.Release release,JSONObject row,String work){return UiStrings.msg("ui_6ec3bac650d6")+(shared?UiStrings.msg("ui_5b6946b4dee7")+work+UiStrings.msg("ui_0a5e5a09d03f"):UiStrings.msg("ui_5cd62a579fcf"))+UiStrings.msg("ui_806b5b5ad72b")+row.optString("releaseId")+" / "+row.optString("serial")+" / "+row.optString("sha1")+UiStrings.msg("ui_ace1aa2a6856");}
    JSONObject genre(MetadataIndex.Release release)throws JSONException {JSONObject e=enrich(release);if(!e.optString("genreState").equals("READY"))return null;return e.put("provider","OpenVGDB v29.0").put("basis",e.optString("genreBasis")).put("sourceUrl",URL).put("archiveSHA256",SHA256);}
    void close(){if(source!=null)source.close();}
    private static byte[] read(File f,int limit)throws IOException{try(InputStream in=new FileInputStream(f)){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1){if(out.size()+n>limit)throw new IOException("Size limit");out.write(b,0,n);}return out.toByteArray();}}
}
