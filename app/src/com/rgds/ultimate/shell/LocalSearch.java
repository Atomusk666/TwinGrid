package com.rgds.ultimate.shell;
import android.content.Context;
import android.os.*;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

/** Local game IDs only. No SAF, ROM, HTTP or source database access in queries. */
final class LocalSearch {
    interface Result {void ready(long generation,long version,List<GameEntry> games);}
    private static final Pattern MARKS=Pattern.compile("\\p{M}+"),SEPARATORS=Pattern.compile("[\\p{P}\\p{Z}\\s]+");
    private static final String[] FAMILY={"精灵宝可梦","口袋妖怪","神奇宝贝","宝可梦","pocket monsters"};
    private final MetadataManager metadata;private final AtomicFile cache,fastCache;private final Context context;
    private String publishedKey="";
    private volatile String publishedSources="";
    private volatile boolean complete;
    private final Map<String,String[]> precomputed=new HashMap<>();
    private String preparedCatalog="";
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->new Thread(()->{android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_DEFAULT);r.run();},"local-search"));
    private final Map<Character,String> syllables=new HashMap<>();
    private final Map<String,String> phrases=new TreeMap<>((a,b)->{int length=Integer.compare(b.length(),a.length());return length!=0?length:a.compareTo(b);});
    private final ScheduledExecutorService queries=Executors.newSingleThreadScheduledExecutor(r->new Thread(r,"local-query"));
    private final Map<String,JSONObject> saved=new HashMap<>();private boolean loaded;
    private volatile boolean phoneticReady;
    private volatile List<Record> records=Collections.emptyList();private volatile long version,buildGeneration;private ScheduledFuture<?> build,query;
    private static final class Record {GameEntry game;String corpus,pinyin="",initials="",signature;Set<String> names,originalNames;}
    LocalSearch(Context c){context=c;metadata=MetadataManager.get(c);cache=new AtomicFile(new File(c.getFilesDir(),"local_search_v1.json"));fastCache=new AtomicFile(new File(c.getFilesDir(),"local_search_v8.bin"));}
    static String normalize(String text){return SEPARATORS.matcher(MARKS.matcher(Normalizer.normalize(text==null?"":text,Normalizer.Form.NFKD)).replaceAll("").toLowerCase(Locale.ROOT)).replaceAll(" ").trim();}
    static String fold(String text){String s=normalize(text);for(String alias:FAMILY)s=s.replace(alias,"pokemon");return s;}
    long version(){return version;}
    boolean phoneticReady(){return phoneticReady;}
    boolean ready(){return complete&&metadata.cacheInputsReady()&&publishedSources.equals(sourceKey());}
    private String sourceKey(){return metadata.catalog.fingerprint()+":"+metadata.searchInputFingerprint();}
    void rebuild(List<GameEntry> games,Runnable done){
        complete=false;long generation=++buildGeneration;if(build!=null)build.cancel(false);
        build=worker.schedule(()->{
            long start=SystemClock.uptimeMillis();if(generation!=buildGeneration||!metadata.cacheInputsReady())return;
            String sources=sourceKey(),key=cacheKey(games,sources);long keyed=SystemClock.uptimeMillis();
            if(key.equals(publishedKey)){List<Record> current=records;List<Record> refreshed=new ArrayList<>();for(int n=0;n<games.size();n++){Record prior=current.get(n),r=new Record();r.game=games.get(n);r.corpus=prior.corpus;r.pinyin=prior.pinyin;r.initials=prior.initials;r.names=prior.names;refreshed.add(r);}if(generation!=buildGeneration||!sources.equals(sourceKey()))return;records=Collections.unmodifiableList(refreshed);publishedSources=sources;complete=true;main.post(done);return;}
            if(loadFast(key,games,generation)){if(generation!=buildGeneration||!sources.equals(sourceKey()))return;publishedKey=key;publishedSources=sources;complete=true;PerfTrace.event("SEARCH_INDEX version="+version+" games="+games.size()+" fastCache=true keyMs="+(keyed-start)+" cacheReadMs="+(SystemClock.uptimeMillis()-keyed)+" elapsedMs="+(SystemClock.uptimeMillis()-start));main.post(done);android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);return;}
            if(generation!=buildGeneration||!metadata.searchReady())return;
            load();
            if(!preparedCatalog.equals(metadata.catalog.fingerprint())){precomputed.clear();preparedCatalog=metadata.catalog.fingerprint();}
            Set<String> wanted=new LinkedHashSet<>();for(GameEntry game:games){wanted.add(metadata.title(game));wanted.add(GameDisplay.name(game,metadata,"zh"));wanted.add(GameDisplay.name(game,metadata,"en"));wanted.add(game.basename());wanted.addAll(metadata.chineseAliases(game.gameId));}wanted.removeAll(precomputed.keySet());precomputed.putAll(metadata.catalog.searchNames(wanted));
            List<Record> next=new ArrayList<>();int changed=0,pinyinCount=0,aliasesCount=0;
            for(GameEntry g:games){
                if(generation!=buildGeneration)return;
                MetadataManager.Info info=metadata.info(g.gameId);LinkedHashSet<String> names=new LinkedHashSet<>();names.add(metadata.title(g));names.add(metadata.overrideCopy(g.gameId).optString("displayName_en"));names.add(GameDisplay.name(g,metadata,"zh"));names.add(GameDisplay.name(g,metadata,"en"));names.add(g.basename());
                OfflineCatalog.Head head=metadata.catalog.head(g.gameId);
                if(head!=null){names.add(head.title);names.add(head.original);names.add(head.englishName);names.addAll(head.aliases);}
                else if(info.status.equals("IDENTIFIED")){names.add(info.title);JSONArray aliases=info.raw.optJSONArray("aliases");if(aliases!=null)for(int i=0;i<aliases.length();i++)names.add(aliases.optString(i));}
                for(String name:new ArrayList<>(names)){String expanded=name;for(String family:new String[]{"精灵宝可梦","口袋妖怪","神奇宝贝"})expanded=expanded.replace(family,"宝可梦");names.add(expanded);}
                names.remove("");LinkedHashSet<String> fields=new LinkedHashSet<>(names);Collections.addAll(fields,g.fileName,g.internalTitle,g.bannerTitle,g.gameCode);Collections.addAll(fields,g.bannerTitles);String corpus=String.join("\n",fields);
                String signature=MetadataManager.hash("10.1:"+metadata.catalog.fingerprint()+":"+corpus);JSONObject old=saved.get(g.gameId);Record r=new Record();r.game=g;r.signature=signature;r.originalNames=names;r.names=new HashSet<>();
                if(old!=null&&old.optString("signature").equals(signature)&&old.has("corpus")){r.corpus=old.optString("corpus");JSONArray ns=old.optJSONArray("names");if(ns!=null)for(int n=0;n<ns.length();n++)r.names.add(ns.optString(n));}else{for(String n:names){String[] prepared=precomputed.get(n);r.names.add(prepared==null?fold(n):prepared[0]);}r.corpus=fold(corpus);}
                if(old!=null&&old.optString("signature").equals(signature)){r.pinyin=old.optString("pinyin");r.initials=old.optString("initials");}
                else changed++;
                if(!r.pinyin.isEmpty())pinyinCount++;if(names.size()>2)aliasesCount++;next.add(r);
            }
            if(generation!=buildGeneration)return;
            if(changed>0){prepareSyllables(games);for(Record r:next){if(generation!=buildGeneration)return;JSONObject old=saved.get(r.game.gameId);if(old!=null&&old.optString("signature").equals(r.signature))continue;
                StringBuilder py=new StringBuilder(),initial=new StringBuilder();for(String name:r.originalNames){String[] prepared=precomputed.get(name);String[] value=prepared==null?phonetics(name,syllables,phrases):new String[]{prepared[1],prepared[2]};appendPhonetics(py,initial,value[0],value[1]);}r.pinyin=py.toString();r.initials=initial.toString();
            }}
            if(changed>0)for(Record r:next)try{saved.put(r.game.gameId,new JSONObject().put("signature",r.signature).put("pinyin",r.pinyin).put("initials",r.initials).put("corpus",r.corpus).put("names",new JSONArray(r.names)));}catch(JSONException ignored){}
            if(generation!=buildGeneration||!sources.equals(sourceKey()))return;
            pinyinCount=0;for(Record r:next)if(!r.pinyin.isEmpty())pinyinCount++;phoneticReady=true;records=Collections.unmodifiableList(next);publishedKey=key;publishedSources=sources;complete=true;version++;
            PerfTrace.event("SEARCH_INDEX version="+version+" games="+next.size()+" recomputed="+changed+" pinyin="+pinyinCount+" sourceAliases="+aliasesCount+" elapsedMs="+(SystemClock.uptimeMillis()-start));main.post(done);
            long writeStarted=SystemClock.uptimeMillis();saveFast(key,next);if(changed>0)save();PerfTrace.event("SEARCH_CACHE_WRITE changed="+changed+" elapsedMs="+(SystemClock.uptimeMillis()-writeStarted));
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
        },version==0?0:40,TimeUnit.MILLISECONDS);
    }
    void query(String text,Set<String> scope,int sort,long generation,Result done){
        final long submitted=SystemClock.uptimeMillis();if(query!=null)query.cancel(false);query=queries.schedule(()->{
            long indexVersion=version,start=SystemClock.uptimeMillis();String q=fold(text);String[] words=q.split(" +");List<Record> matched=new ArrayList<>();Map<String,Integer> ranks=new HashMap<>();
            for(Record r:records){if(scope!=null&&!scope.contains(r.game.gameId))continue;int rank=rank(r,q,words);if(rank>=0){matched.add(r);ranks.put(r.game.gameId,rank);}}
            matched.sort((a,b)->{int rank=Integer.compare(ranks.get(a.game.gameId),ranks.get(b.game.gameId));if(rank!=0)return rank;return sort==1?Long.compare(b.game.romModifiedAt,a.game.romModifiedAt):a.game.bestTitle().compareToIgnoreCase(b.game.bestTitle());});
            List<GameEntry> result=new ArrayList<>();for(Record r:matched)result.add(r.game);List<GameEntry> immutable=Collections.unmodifiableList(result);
            PerfTrace.event("LOCAL_QUERY generation="+generation+" indexVersion="+indexVersion+" results="+result.size()+" mergeWaitMs="+(start-submitted)+" elapsedMs="+(SystemClock.uptimeMillis()-start));main.post(()->done.ready(generation,indexVersion,immutable));
        },100,TimeUnit.MILLISECONDS);
    }
    private void prepareSyllables(List<GameEntry> games){} // Character table is compiled on the development host.
    private String cacheKey(List<GameEntry> games,String sources){
        StringBuilder b=new StringBuilder("10.1:").append(sources);
        for(GameEntry g:games){for(String s:new String[]{g.gameId,g.fileName,g.displayTitle,g.internalTitle,g.bannerTitle,g.gameCode})appendKey(b,s);b.append('\n').append(g.bannerTitles.length);for(String s:g.bannerTitles)appendKey(b,s);}
        return MetadataManager.hash(b.toString());
    }
    private static void appendKey(StringBuilder b,String value){b.append('\n').append(value.length()).append(':').append(value);}
    private boolean loadFast(String key,List<GameEntry> games,long generation){
        if(fastCache.getBaseFile().length()>32L*1024*1024)return false;
        try(java.util.zip.CheckedInputStream checked=new java.util.zip.CheckedInputStream(new BufferedInputStream(fastCache.openRead()),new java.util.zip.CRC32());DataInputStream in=new DataInputStream(checked)){
            if(in.readInt()!=0x52475341||!readString(in).equals(key)||in.readInt()!=games.size())return false;
            List<Record> next=new ArrayList<>();for(GameEntry g:games){if(!readString(in).equals(g.gameId))return false;Record r=new Record();r.game=g;r.corpus=readString(in);r.pinyin=readString(in);r.initials=readString(in);int size=in.readInt();if(size<1||size>256)return false;r.names=new HashSet<>();for(int n=0;n<size;n++)r.names.add(readString(in));next.add(r);}
            long crc=checked.getChecksum().getValue();if(in.readLong()!=crc||in.read()!=-1||generation!=buildGeneration)return false;records=Collections.unmodifiableList(next);phoneticReady=true;version++;return true;
        }catch(Exception e){return false;}
    }
    private void saveFast(String key,List<Record> list){FileOutputStream stream=null;try{
        stream=fastCache.startWrite();java.util.zip.CheckedOutputStream checked=new java.util.zip.CheckedOutputStream(new BufferedOutputStream(stream),new java.util.zip.CRC32());DataOutputStream out=new DataOutputStream(checked);out.writeInt(0x52475341);writeString(out,key);out.writeInt(list.size());
        for(Record r:list){writeString(out,r.game.gameId);writeString(out,r.corpus);writeString(out,r.pinyin);writeString(out,r.initials);out.writeInt(r.names.size());for(String s:r.names)writeString(out,s);}out.writeLong(checked.getChecksum().getValue());out.flush();fastCache.finishWrite(stream);
    }catch(Exception e){if(stream!=null)fastCache.failWrite(stream);}}
    private static String readString(DataInputStream in)throws IOException{int size=in.readInt();if(size<0||size>256*1024)throw new IOException("Search string bound");byte[] bytes=new byte[size];in.readFully(bytes);return new String(bytes,StandardCharsets.UTF_8);}
    private static void writeString(DataOutputStream out,String value)throws IOException{byte[] bytes=value.getBytes(StandardCharsets.UTF_8);out.writeInt(bytes.length);out.write(bytes);}
    static void appendPhonetics(StringBuilder py,StringBuilder initial,String value,String initials){
        String normalized=normalize(value);if(!normalized.isEmpty()){py.append(normalized).append('\n');String joined=normalized.replace(" ","");if(!joined.equals(normalized))py.append(joined).append('\n');}
        String shortName=normalize(initials).replace(" ","");if(!shortName.isEmpty())initial.append(shortName).append('\n');
    }
    /** Same character/phrase table and normalization as the development-host compiler. */
    static String[] phonetics(String name,Map<Character,String> table,Map<String,String> phraseTable){
        name=Normalizer.normalize(name,Normalizer.Form.NFKC);
        // Kana indicates a Japanese name: keep its literal index, do not invent Mandarin.
        for(int n=0;n<name.length();n++)if(name.charAt(n)>='\u3040'&&name.charAt(n)<='\u30ff')return new String[]{"",""};
        StringBuilder value=new StringBuilder(),initial=new StringBuilder();
        for(int n=0;n<name.length();){
            String phrase=null;for(String key:phraseTable.keySet())if(name.startsWith(key,n)){phrase=key;break;}
            if(phrase!=null){for(String p:phraseTable.get(phrase).split(" +")){value.append(' ').append(p).append(' ');if(!p.isEmpty())initial.append(p.charAt(0));}n+=phrase.length();continue;}
            char c=name.charAt(n++);String syllable=table.get(c);
            if(syllable!=null){value.append(' ').append(syllable).append(' ');initial.append(syllable.charAt(0));}
            else{value.append(c);if(Character.isLetterOrDigit(c))initial.append(c);}
        }
        return new String[]{normalize(value.toString()).replace(" ",""),normalize(initial.toString()).replace(" ","")};
    }
    private static int rank(Record r,String q,String[] words){
        if(q.isEmpty())return 3;if(r.names.contains(q))return 0;for(String n:r.names)if(n.startsWith(q))return 1;
        boolean all=true;for(String w:words)if(!r.corpus.contains(w)){all=false;break;}if(all)return 2;
        // A segmented pronunciation is one query, not an unordered bag of syllables.
        // Newline-separated alias records prevent joining parts of different names.
        String joined=q.replace(" ","");return r.pinyin.contains(joined)||r.initials.contains(joined)?4:-1;
    }
    private void load(){if(loaded)return;loaded=true;
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(context.getResources().openRawResource(R.raw.nds_pinyin),StandardCharsets.UTF_8))){String row;while((row=reader.readLine())!=null){String[] pair=row.split("\t",2);if(pair.length==2){if(pair[0].length()==1)syllables.put(pair[0].charAt(0),pair[1]);else phrases.put(pair[0],pair[1]);}}}catch(Exception e){android.util.Log.w("TwinGridCatalog","PINYIN_TABLE_UNAVAILABLE");}
        try{if(cache.getBaseFile().length()>32L*1024*1024)throw new IOException("Local search cache safety bound");JSONObject root=new JSONObject(new String(cache.readFully(),StandardCharsets.UTF_8));Iterator<String> ids=root.keys();while(ids.hasNext()){String id=ids.next();if(!id.equals("_characters"))saved.put(id,root.getJSONObject(id));}}catch(Exception ignored){}}
    private void save(){FileOutputStream out=null;try{byte[] data=new JSONObject(saved).toString().getBytes(StandardCharsets.UTF_8);out=cache.startWrite();out.write(data);cache.finishWrite(out);}catch(Exception e){if(out!=null)cache.failWrite(out);}}
}
