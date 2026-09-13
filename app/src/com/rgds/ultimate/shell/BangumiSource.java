package com.rgds.ultimate.shell;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.util.*;
import java.text.Normalizer;
import java.util.regex.Pattern;

/** Public subject information only. Bounded NDS catalog, stable IDs and negative caches. */
final class BangumiSource {
    static final String API="https://api.bgm.tv";
    static final int VERSION=6;
    interface Transport {String request(String path,JSONObject post)throws Exception;}
    private final SQLiteDatabase db;private final Transport transport;
    private final Map<String,List<JSONObject>> names=new HashMap<>();
    private final Map<String,List<JSONObject>> seedNames=new HashMap<>();private final Map<Integer,JSONObject> seedSubjects=new HashMap<>();
    private String seedVersion="",seedFetched="",seedHash="";
    private boolean loaded;private android.icu.text.Transliterator simplify;
    BangumiSource(SQLiteDatabase database,Transport t){db=database;transport=t;db.execSQL("CREATE TABLE IF NOT EXISTS bangumi_cache (url TEXT PRIMARY KEY,data TEXT NOT NULL,fetched_at INTEGER NOT NULL,sha256 TEXT NOT NULL)");}
    void installSeed(java.io.InputStream in)throws Exception{
        java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[8192];int read;
        while((read=in.read(buffer))!=-1){if(bytes.size()+read>4*1024*1024)throw new java.io.IOException("Seed size");bytes.write(buffer,0,read);}
        JSONObject pack=new JSONObject(new String(bytes.toByteArray(),java.nio.charset.StandardCharsets.UTF_8));if(pack.getInt("formatVersion")!=1)throw new java.io.IOException("Seed schema");
        seedVersion=pack.getString("version");seedFetched=pack.getString("fetchedAt");seedHash=MetadataManager.hashBytes(bytes.toByteArray());JSONArray subjects=pack.getJSONArray("subjects");
        for(int n=0;n<subjects.length();n++){JSONObject subject=subjects.getJSONObject(n);if(!nds(subject))continue;seedSubjects.put(subject.getInt("id"),subject);for(String name:subjectNames(subject)){String key=normalize(name);if(key.length()>=3)seedNames.computeIfAbsent(key,k->new ArrayList<>()).add(subject);}}
    }
    JSONObject resolveOffline(List<GameEntry> games,JSONObject info,JSONObject previous)throws Exception{
        JSONObject result=new JSONObject().put("adapterVersion",VERSION).put("checkedAt",System.currentTimeMillis()).put("retryAt",System.currentTimeMillis()+30L*MetadataManager.DAY)
            .put("delivery","BUNDLED_OFFICIAL_SNAPSHOT").put("sourceVersion",seedVersion).put("sourceFetchedAt",seedFetched).put("datasetSHA256",seedHash).put("deviceHttp",false);
        if(seedSubjects.isEmpty())return result.put("state","LOCAL_SOURCE_UNAVAILABLE").put("reason","Bundled source could not be read");
        if(!info.optString("status").equals("IDENTIFIED"))return result.put("state","BLOCKED_IDENTITY");
        Set<String> local=localNames(games,info),normalized=new HashSet<>();for(String name:local)normalized.add(normalize(name));
        Map<Integer,JSONObject> matches=new LinkedHashMap<>();JSONArray evidence=new JSONArray();
        for(String n:normalized)for(JSONObject subject:seedNames.getOrDefault(n,Collections.emptyList())){matches.put(subject.getInt("id"),subject);evidence.put(new JSONObject().put("normalizedName",n).put("subjectId",subject.getInt("id")).put("basis","exact title/alias/banner + explicit NDS platform"));}
        result.put("searchedNames",new JSONArray(local)).put("matchingEvidence",evidence).put("candidateIds",new JSONArray(matches.keySet()));
        if(matches.size()!=1)return result.put("state",matches.isEmpty()?"NO_EXACT_MATCH_IN_SNAPSHOT":"AMBIGUOUS_SUBJECT").put("reason","Bundled NDS catalog only; live source coverage not implied");
        return describe(matches.values().iterator().next(),result);
    }
    private JSONObject cached(String path,JSONObject post,long age)throws Exception{
        String key=path+(post==null?"":"\n"+post.toString());long now=System.currentTimeMillis();
        try(Cursor c=db.rawQuery("SELECT data,fetched_at FROM bangumi_cache WHERE url=?",new String[]{key})){if(c.moveToFirst()&&now-c.getLong(1)<age)return new JSONObject(c.getString(0));}
        String text=transport.request(path,post);JSONObject o=new JSONObject(text);
        if(path.startsWith("/v0/subjects?"))o.getJSONArray("data");else if(path.startsWith("/v0/search/"))o.getJSONArray("data");else {o.getInt("id");o.getInt("type");}
        db.execSQL("INSERT OR REPLACE INTO bangumi_cache VALUES(?,?,?,?)",new Object[]{key,text,now,MetadataManager.hash(text)});return o;
    }
    private void catalog()throws Exception{
        if(loaded)return;names.clear();int total=1;
        for(int offset=0;offset<total&&offset<5000;offset+=100){
            JSONObject page=cached("/v0/subjects?type=4&platform=nds&sort=date&limit=100&offset="+offset,null,30L*MetadataManager.DAY);
            total=page.getInt("total");if(total>5000)throw new java.io.IOException("NDS catalog size changed");JSONArray data=page.getJSONArray("data");
            if(data.length()==0&&offset<total)throw new java.io.IOException("Incomplete NDS catalog");
            for(int i=0;i<data.length();i++){JSONObject subject=data.getJSONObject(i);if(!nds(subject))continue;for(String name:subjectNames(subject)){String key=normalize(name);if(key.length()>=3)names.computeIfAbsent(key,k->new ArrayList<>()).add(subject);}}
        }loaded=true;
    }
    static List<String> values(Object value){List<String> out=new ArrayList<>();if(value instanceof String)out.add((String)value);else if(value instanceof JSONArray){JSONArray a=(JSONArray)value;for(int i=0;i<a.length();i++){Object v=a.opt(i);if(v instanceof JSONObject)out.add(((JSONObject)v).optString("v"));else if(v instanceof String)out.add((String)v);}}return out;}
    static List<String> field(JSONObject s,String key){List<String> out=new ArrayList<>();JSONArray a=s.optJSONArray("infobox");if(a!=null)for(int i=0;i<a.length();i++){JSONObject f=a.optJSONObject(i);if(f!=null&&key.equals(f.optString("key")))out.addAll(values(f.opt("value")));}return out;}
    static boolean dsPlatform(String p){String n=normalize(p);return n.equals("nds")||n.equals("nintendods")||n.equals("ds")||n.equals("任天堂ds");}
    static boolean nds(JSONObject s){if(s.optInt("type")!=4)return false;for(String p:field(s,"平台"))for(String part:p.split("[,，/、;；]"))if(dsPlatform(part))return true;return false;}
    static boolean variantReview(JSONObject s){for(String p:field(s,"平台"))for(String part:p.split("[,，/、;；]"))if(!part.trim().isEmpty()&&!dsPlatform(part))return true;return false;}
    static Set<String> subjectNames(JSONObject s){Set<String> out=new LinkedHashSet<>();out.add(s.optString("name"));out.add(s.optString("name_cn"));out.addAll(field(s,"别名"));out.addAll(field(s,"中文名"));out.remove("");return out;}
    static String normalize(String text){String s=Normalizer.normalize(text==null?"":text,Normalizer.Form.NFKD).toLowerCase(Locale.ROOT).replaceAll("\\p{M}+","");
        s=s.replaceAll("(?i)\\((?:japan|usa|europe|world|korea|china|australia|en|ja|fr|de|es|it|rev ?\\d+|en,.*)\\)","").replaceAll("(?i)\\bversion$","");return s.replaceAll("[\\p{P}\\p{Z}\\s]+","");}
    static Set<String> localNames(List<GameEntry> games,JSONObject info){LinkedHashSet<String> out=new LinkedHashSet<>();
        out.add(info.optString("title").replaceAll("\\s*\\([^)]*\\)","").trim());JSONArray aliases=info.optJSONArray("aliases");if(aliases!=null)for(int i=0;i<aliases.length();i++)out.add(aliases.optString(i));
        for(GameEntry g:games)for(String title:g.bannerTitles){List<String> titleLines=new ArrayList<>();for(String line:title.split("\\n")){line=line.trim();if(line.isEmpty())continue;if(!titleLines.isEmpty()&&line.toLowerCase(Locale.ROOT).matches(".*(©|copyright|nintendo|konami|capcom|namco|sega|square|entertainment|atlus|electronic arts|任天堂|コナミ|カプコン|セガ|アトラス|スクウェア|エニックス|バンダイ|ナムコ|ハドソン|タイトー|マーベラス|ゲームス|ゲームズ|ソフトウェア|株式会社|有限会社|汉化|\\binc\\b|\\bltd\\b).*"))break;titleLines.add(line);}if(!titleLines.isEmpty())out.add(String.join(" ",titleLines));}
        out.remove("");return out;
    }
    private String simplify(String s){if(simplify==null)simplify=android.icu.text.Transliterator.getInstance("Traditional-Simplified");return simplify.transliterate(s);}
    static String language(String text){
        int han=0,latin=0;for(int i=0;i<text.length();i++){char c=text.charAt(i);if(Character.UnicodeScript.of(c)==Character.UnicodeScript.HAN)han++;else if(c>='A'&&c<='Z'||c>='a'&&c<='z')latin++;}
        Set<String> english=new HashSet<>();java.util.regex.Matcher words=Pattern.compile("(?i)\\b(the|is|an|of|and|based|game|with|you|your|players|in|to)\\b").matcher(text);while(words.find())english.add(words.group().toLowerCase(Locale.ROOT));
        return han>15&&latin>han*1.5&&english.size()>=4?"mixed":GameDescription.language(text);
    }
    JSONObject resolve(List<GameEntry> games,JSONObject info,JSONObject previous)throws Exception{
        long now=System.currentTimeMillis();JSONObject result=new JSONObject().put("adapterVersion",VERSION).put("checkedAt",now).put("retryAt",now+30L*MetadataManager.DAY);
        if(!info.optString("status").equals("IDENTIFIED"))return result.put("state","BLOCKED_IDENTITY").put("reason","Local release remains ambiguous; no series-level inference");
        Set<String> local=localNames(games,info),normalized=new HashSet<>();for(String n:local)if(normalize(n).length()>=3)normalized.add(normalize(n));
        catalog();Map<Integer,JSONObject> candidates=new LinkedHashMap<>();JSONArray evidence=new JSONArray();
        for(String n:normalized)for(JSONObject s:names.getOrDefault(n,Collections.emptyList())){candidates.put(s.optInt("id"),s);evidence.put(new JSONObject().put("normalizedName",n).put("subjectId",s.optInt("id")).put("basis","exact title/alias/banner + NDS platform"));}
        if(previous!=null&&previous.optInt("subjectId")>0){JSONObject s=cached("/v0/subjects/"+previous.optInt("subjectId"),null,30L*MetadataManager.DAY);if(nds(s)&&matches(s,normalized))candidates.put(s.optInt("id"),s);}
        // At most two searches for a work absent from the bounded NDS catalog alias index.
        int attempts=0;
        if(candidates.isEmpty())for(String name:local){if(name.length()<3||name.length()>160)continue;if(++attempts>2)break;
            JSONObject post=new JSONObject().put("keyword",name).put("sort","match").put("filter",new JSONObject().put("type",new JSONArray().put(4)));
            JSONObject response=cached("/v0/search/subjects?limit=10&offset=0",post,30L*MetadataManager.DAY);JSONArray data=response.getJSONArray("data");
            for(int i=0;i<data.length();i++){JSONObject summary=data.getJSONObject(i);boolean exact=false;for(String n:subjectNames(summary))if(normalized.contains(normalize(n)))exact=true;
                if(!exact)continue;JSONObject full=cached("/v0/subjects/"+summary.getInt("id"),null,30L*MetadataManager.DAY);if(nds(full)&&matches(full,normalized))candidates.put(full.getInt("id"),full);}
            if(!candidates.isEmpty())break;
        }
        result.put("searchedNames",new JSONArray(local)).put("matchingEvidence",evidence).put("candidateIds",new JSONArray(candidates.keySet()));
        if(candidates.size()!=1)return result.put("state",candidates.isEmpty()?"NO_EXACT_NDS_MATCH":"AMBIGUOUS_SUBJECT").put("reason","Need one exact work name/alias with explicit NDS platform; series and sequels not merged");
        JSONObject subject=candidates.values().iterator().next();subject=cached("/v0/subjects/"+subject.getInt("id"),null,30L*MetadataManager.DAY);
        if(!nds(subject)||!matches(subject,normalized))return result.put("state","DETAIL_IDENTITY_CHANGED");
        result.put("delivery","DEVICE_HTTP_CACHE").put("deviceHttp",true).put("sourceVersion","v0");return describe(subject,result);
    }
    private JSONObject describe(JSONObject subject,JSONObject result)throws Exception{
        String original=subject.optString("summary"),plain=GameDescription.cleanParagraphs(original),language=language(plain),quality=ChineseOverview.quality(plain);
        String simplified=language.equals("zh")?simplify(plain):plain;boolean variantReview=variantReview(subject)||plain.matches("(?si).*(3DS|Nintendo Switch|PS4|PS5|Steam|iOS|Android).*")&&plain.matches("(?s).*(重制|重製|复刻|復刻|移植).*");
        result.put("state",plain.isEmpty()?"MATCHED_EMPTY_SUMMARY":language.equals("zh")?"NATIVE_ZH":"MATCHED_NON_ZH").put("subjectId",subject.getInt("id"))
            .put("url","https://bgm.tv/subject/"+subject.getInt("id")).put("apiUrl",API+"/v0/subjects/"+subject.getInt("id"))
            .put("name",subject.optString("name")).put("nameCn",simplify(subject.optString("name_cn"))).put("aliases",new JSONArray(subjectNames(subject)))
            .put("platforms",new JSONArray(field(subject,"平台"))).put("date",subject.optString("date")).put("infobox",subject.optJSONArray("infobox"))
            .put("rawGenre",String.join(",",field(subject,"游戏类型"))).put("metaTags",subject.optJSONArray("meta_tags"))
            .put("summaryOriginal",original).put("summaryPlain",plain).put("summarySimplified",simplified).put("summaryLanguage",language)
            .put("summaryScript",plain.equals(simplified)?"UNCHANGED":"TRADITIONAL_OR_MIXED").put("conversionVersion","Android ICU Traditional-Simplified; no translation")
            .put("summarySHA256",MetadataManager.hash(original)).put("subjectSHA256",MetadataManager.hash(subject.toString())).put("quality",quality)
            .put("platformVariantReview",variantReview).put("platformReviewReason",variantReview?"MULTIPLATFORM_DESCRIPTION_REQUIRES_DS_REVIEW":"").put("overview",language.equals("zh")&&!variantReview?ChineseOverview.shortText(simplified):"").put("overviewVersion",ChineseOverview.VERSION).put("translation",false)
            .put("license","Bangumi subject contributors; CC BY-SA 3.0 https://creativecommons.org/licenses/by-sa/3.0/ per https://bgm.tv/about/copyright; artwork rights separate");
        return result;
    }
    private static boolean matches(JSONObject subject,Set<String> local){for(String n:subjectNames(subject))if(local.contains(normalize(n)))return true;return false;}
}
