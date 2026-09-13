package com.rgds.ultimate.shell;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AtomicFile;
import android.util.Log;
import android.util.LruCache;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** Published content is immutable. All disk access is on this private executor, never a View bind. */
final class OfflineCatalog {
    interface Listener { void updated(String gameId); }
    enum MatchPhase { PREPARING, MATCHING, COMPLETE, UNCHANGED, ERROR }
    static final class MatchSnapshot {
        final long revision,started,ended;final MatchPhase phase;final int total,matched,readable;final String version,message;
        MatchSnapshot(long r,long start,long end,MatchPhase p,int total,int matched,int readable,String version,String message){revision=r;started=start;ended=end;phase=p;this.total=total;this.matched=matched;this.readable=readable;this.version=version;this.message=message;}
        boolean busy(){return phase==MatchPhase.PREPARING||phase==MatchPhase.MATCHING;}
        JSONObject json(){try{return new JSONObject().put("revision",revision).put("phase",phase.name()).put("started",started).put("ended",ended).put("total",total).put("matched",matched).put("readable",readable).put("version",version).put("message",message);}catch(JSONException e){throw new IllegalStateException(e);}}
    }
    private volatile MatchSnapshot match=new MatchSnapshot(0,0,0,MatchPhase.PREPARING,0,0,0,"",UiStrings.msg("ui_567f03d91413"));
    MatchSnapshot match(){return match;}
    String matchStatus(){return match.message;}
    synchronized void requestMatch(List<GameEntry> games,MetadataManager metadata){if(match.busy())return;bindGames(games,metadata);}
    private synchronized void matchState(long token,MatchPhase phase,int total,String message){
        if(token!=generation)return;long now=System.currentTimeMillis();match=new MatchSnapshot(match.revision+1,phase==MatchPhase.MATCHING?now:match.started,phase==MatchPhase.MATCHING?0:now,phase,total,matchedCount,readableCount,version,message);
        main.post(()->listener.updated("@match"));
    }

    static final class Head {
        final String workId,title,original,kind,hash;
        final List<String> aliases,genres;final boolean hasSummary;boolean hasEnglish;String englishKind="MISSING",englishName="",englishNameKind="ORIGINAL_LANGUAGE_FALLBACK";
        boolean has(String locale){return locale.equals("en")?hasEnglish:hasSummary;}
        String kind(String locale){return locale.equals("en")?englishKind:kind;}
        Head(Cursor c)throws JSONException {
            workId=c.getString(0);original=c.getString(1);title=c.getString(2);
            aliases=strings(c.getString(3));genres=GenreTaxonomy.validOverrides(strings(c.getString(4)));kind=c.getString(5);hasSummary=c.getInt(6)!=0;hash=c.getString(7);
        }
    }
    static final class Text {
        final String summary,details,evidence,locale;final boolean fallback;
        Text(String s,String d,String e,String locale,boolean fallback){summary=s;details=d;evidence=e;this.locale=locale;this.fallback=fallback;}
    }
    private final Context context;private final Listener listener;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->new Thread(()->{android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_DEFAULT);r.run();},"offline-catalog"));
    private final Handler main=new Handler(Looper.getMainLooper());
    private final LruCache<String,Text> textCache=new LruCache<>(48);
    private final Set<String> requested=ConcurrentHashMap.newKeySet();
    private volatile Map<String,Head> bindings=Collections.emptyMap();
    private final Map<String,Head> headCache=new HashMap<>();
    private boolean localizedContent;
    private volatile boolean ready,libraryReady;private volatile String version="",error="";
    private volatile SQLiteDatabase db;private final File dir;private JSONObject saved=new JSONObject();
    private volatile long generation;private long publishedGeneration=-1;private String bundleHash="",lastBindingKey="";private int releaseCount,editedCount,englishCount,matchedCount,readableCount;
    OfflineCatalog(Context c,Listener l){context=c;listener=l;dir=new File(c.getFilesDir(),"catalog_v7");worker.execute(this::prepare);}
    Head head(String id){return bindings.get(id);}
    synchronized boolean ready(){return ready&&libraryReady&&publishedGeneration==generation;}
    boolean prepared(){return ready;}
    int boundGames(){return bindings.size();}
    synchronized int readableGames(){return readableCount;}
    /** A completed binding token, or -1 while its library has been invalidated. */
    synchronized long publishedBindingGeneration(){return ready&&libraryReady&&publishedGeneration==generation?publishedGeneration:-1;}
    synchronized void invalidateBindings(){libraryReady=false;generation++;}
    synchronized void invalidateGame(String id){Map<String,Head> next=new HashMap<>(bindings);next.remove(id);bindings=Collections.unmodifiableMap(next);libraryReady=false;generation++;}
    /** Only these short monitor sections may publish binding completion. No I/O holds this lock. */
    private synchronized boolean publishBindings(long request,Map<String,Head> next,String key,int matched,int readable){
        if(request!=generation)return false;
        bindings=next;lastBindingKey=key;matchedCount=matched;readableCount=readable;
        publishedGeneration=request;libraryReady=true;return true;
    }
    private synchronized boolean publishUnchanged(long request,String key){
        if(request!=generation||!lastBindingKey.equals(key))return false;
        publishedGeneration=request;libraryReady=true;return true;
    }
    private synchronized boolean publishBindingFailure(long request,String message){
        if(request!=generation)return false;
        // The prior map belongs to a different input snapshot and must not become
        // the successful-looking result of this failed generation. Files stay intact.
        lastBindingKey="";
        error=message;publishedGeneration=-1;libraryReady=false;return true;
    }
    String version(){return version;}
    static final class Choice {
        final String work,release,name,code,region,title,genres,summary,evidence,kind;String english="";
        Choice(Cursor c){work=c.getString(0);release=c.getString(1);name=c.getString(2);code=c.getString(3);region=c.getString(4);title=c.getString(5);genres=c.getString(6);summary=c.getString(7);evidence=c.getString(8);kind=c.getString(9);}
        String label(){return (LocaleSettings.ui().equals("en")||title.isEmpty()?name:title)+(code.isEmpty()?"":" · "+code+" · "+region)+(name.contains("(Rev ")?" · "+name.substring(name.indexOf("(Rev ")):"");}
        String genreLabels(){try{JSONArray a=new JSONArray(genres);List<String> out=new ArrayList<>();for(int n=0;n<a.length();n++)out.add(GenreTaxonomy.label(a.optString(n)));return String.join(" / ",out);}catch(Exception e){return genres;}}
        String preview(){return label()+((LocaleSettings.ui().equals("en")||title.isEmpty()||title.equals(name))?"":"\n"+TaskPresentation.msg("18")+name)+UiStrings.msg("ui_9166fc79df48")+genreLabels()+"\n\n"+UiStrings.msg("ui_5dc9f4687208")+(summary.isEmpty()?UiStrings.msg("ui_65364e6fd8c7"):UiStrings.msg("ui_4d99c976beb8"))+UiStrings.msg("ui_66d17059821e")+(english.isEmpty()?UiStrings.msg("ui_65364e6fd8c7"):UiStrings.msg("ui_4d99c976beb8"))+"\n\n"+(LocaleSettings.content().equals("en")?(english.isEmpty()?UiStrings.msg("ui_0b9701c7e80e"):english):(summary.isEmpty()?UiStrings.msg("ui_4578dccccd49"):summary))+UiStrings.msg("ui_9defcc6b60d3")+work+"\n"+(release.isEmpty()?UiStrings.msg("ui_56b148e28bac"):UiStrings.msg("ui_0d4d02d59fb1")+release)+UiStrings.msg("ui_9be9a769f5b8");}
    }
    void choices(String query,boolean workOnly,int offset,java.util.function.Consumer<List<Choice>> callback){worker.execute(()->{
        List<Choice> out=new ArrayList<>();String q="%"+query.trim().replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";
        String fields=workOnly?"w.work_id,'',w.original_title,'','',w.preferred_title_zh,w.genres,c.summary_zh,c.evidence,w.kind":"COALESCE(w.work_id,''),r.release_id,r.name,r.serial,r.region,COALESCE(w.preferred_title_zh,''),COALESCE(w.genres,'[]'),COALESCE(c.summary_zh,''),COALESCE(c.evidence,'{}'),COALESCE(w.kind,'MISSING')";
        String from=workOnly?"works w JOIN content c ON c.work_id=w.work_id":"releases r LEFT JOIN works w ON w.work_id=r.work_id LEFT JOIN content c ON c.work_id=w.work_id";
        // Materialize each matching ID set once. A correlated alias scan per
        // release made one local lookup inspect tens of millions of rows.
        String textMatches="SELECT work_id FROM works WHERE original_title LIKE ? ESCAPE '\\' OR preferred_title_zh LIKE ? ESCAPE '\\' UNION SELECT work_id FROM aliases WHERE text LIKE ? ESCAPE '\\'";
        String extra=workOnly?"w.work_id IN (SELECT work_id FROM releases WHERE serial=? OR name LIKE ? ESCAPE '\\')":"r.serial=? OR r.name LIKE ? ESCAPE '\\'";
        String sql="SELECT DISTINCT "+fields+" FROM "+from+" WHERE w.work_id IN ("+textMatches+") OR "+extra+" ORDER BY "+(workOnly?"w.original_title":"r.name")+" LIMIT 61 OFFSET "+Math.max(0,offset);
        if(db!=null&&!query.trim().isEmpty())try(Cursor c=db.rawQuery(sql,new String[]{q,q,q,query.trim().toUpperCase(Locale.ROOT),q})){while(c.moveToNext())out.add(new Choice(c));}catch(Exception e){Log.w("TwinGridCatalog","CHOICE_SEARCH_FAILED",e);}
        if(localizedContent)for(Choice choice:out)if(!choice.work.isEmpty())try(Cursor c=db.rawQuery("SELECT summary FROM localized_content WHERE work_id=? AND locale='en'",new String[]{choice.work})){if(c.moveToFirst())choice.english=c.getString(0);}
        main.post(()->callback.accept(out));
    });}
    String fingerprint(){return bundleHash;}
    private void prepare(){
        long started=SystemClock.uptimeMillis();boolean copied=false;
        try {
            if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Catalog directory unavailable");
            JSONObject expected=new JSONObject(new String(read(context.getResources().openRawResource(R.raw.nds_catalog_manifest),8192),StandardCharsets.UTF_8));
            bundleHash=expected.getString("sha256");if(!bundleHash.matches("[a-f0-9]{64}"))throw new IOException("Invalid catalog hash");
            File target=new File(dir,"nds-"+bundleHash+".sqlite");long expectedBytes=expected.getLong("bytes");
            if(expectedBytes<4096||expectedBytes>128L*1024*1024)throw new IOException("Catalog outside measured safety bounds");
            long integrityStarted=SystemClock.uptimeMillis();boolean verifiedExisting=false;
            if(target.isFile()){
                try{CatalogIntegrity.verify(target,bundleHash,expectedBytes);verifiedExisting=true;}
                catch(IOException invalid){
                    File rejected=new File(dir,"rejected-"+bundleHash+"-"+SystemClock.uptimeMillis()+".sqlite");
                    if(!target.renameTo(rejected))throw new IOException("Cannot preserve invalid catalog",invalid);
                    Log.w("TwinGridCatalog","INVALID_DERIVED_CATALOG_PRESERVED; rebuilding bundled copy");
                }
            }
            if(!target.isFile()){
                FileOutputStream out=null;
                File staged=new File(dir,"prepare-"+bundleHash+".sqlite");
                try(InputStream in=context.getResources().openRawResource(R.raw.nds_catalog)){
                    out=new FileOutputStream(staged);MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[32768];int n;long size=0;
                    while((n=in.read(buffer))!=-1){size+=n;if(size>expectedBytes)throw new IOException("Expanded catalog too large");out.write(buffer,0,n);digest.update(buffer,0,n);}
                    if(size!=expectedBytes||!hex(digest.digest()).equals(bundleHash))throw new IOException("Catalog integrity mismatch");out.getFD().sync();out.close();out=null;
                }finally{if(out!=null){out.close();out=null;}}
                SQLiteDatabase candidate=open(staged);
                try{
                    try(Cursor c=candidate.rawQuery("PRAGMA quick_check",null)){if(!c.moveToFirst()||!"ok".equals(c.getString(0)))throw new IOException("Catalog SQLite check failed");}
                    if(!Arrays.asList("7","8").contains(value(candidate,"schema"))||!value(candidate,"content_sha256").equals(expected.getString("contentSHA256")))throw new IOException("Staged catalog version mismatch");
                }finally{candidate.close();}
                // Same-directory rename publishes the verified, fsynced bytes atomically.
                // No second full-file copy and no active pointer change before validation.
                if(!staged.renameTo(target))throw new IOException("Catalog atomic publication failed");copied=true;
            }
            if(!verifiedExisting)CatalogIntegrity.verify(target,bundleHash,expectedBytes);
            db=open(target);checkDatabase(db);if(!Arrays.asList("7","8").contains(value("schema"))||!value("content_sha256").equals(expected.getString("contentSHA256")))throw new IOException("Catalog version mismatch");
            PerfTrace.event("CATALOG_INTEGRITY bytes="+expectedBytes+" elapsedMs="+(SystemClock.uptimeMillis()-integrityStarted));
            localizedContent=value("schema").equals("8");version=value("version");releaseCount=Integer.parseInt(value("universe_count"));editedCount=Integer.parseInt(value("edited_count"));englishCount=Integer.parseInt(value("english_count"));
            AtomicFile active=new AtomicFile(new File(dir,"active.json"));
            JSONObject old=null;try{old=new JSONObject(new String(active.readFully(),StandardCharsets.UTF_8));}catch(Exception ignored){}
            if(old==null||!old.optString("hash").equals(bundleHash))write(active,new JSONObject().put("hash",bundleHash).put("previous",old==null?"":old.optString("hash")).toString());
            try{saved=new JSONObject(new String(new AtomicFile(new File(dir,"bindings.json")).readFully(),StandardCharsets.UTF_8));}catch(Exception ignored){}
            if(!saved.optString("version").equals(bundleHash))saved=new JSONObject().put("version",bundleHash).put("games",new JSONObject());
            ready=true;PerfTrace.event("CATALOG_READY version="+version+" copied="+copied+" bytes="+target.length()+" elapsedMs="+(SystemClock.uptimeMillis()-started));
        }catch(Exception failure){
            error=UiStrings.msg("ui_29bcf77f48ce");Log.e("TwinGridCatalog","PREPARE_FAILED",failure);
            if(db!=null){db.close();db=null;}
            try{
                JSONObject active=new JSONObject(new String(new AtomicFile(new File(dir,"active.json")).readFully(),StandardCharsets.UTF_8));String old=active.getString("hash");
                if(old.equals(bundleHash))old=active.optString("previous");
                if(!old.matches("[a-f0-9]{64}"))throw new IOException("Invalid prior catalog");File prior=new File(dir,"nds-"+old+".sqlite");CatalogIntegrity.verify(prior,old,-1);db=open(prior);checkDatabase(db);
                if(!Arrays.asList("7","8").contains(value("schema")))throw new IOException("Unsupported prior catalog");bundleHash=old;localizedContent=value("schema").equals("8");version=value("version");releaseCount=Integer.parseInt(value("universe_count"));editedCount=Integer.parseInt(value("edited_count"));englishCount=Integer.parseInt(value("english_count"));saved=new JSONObject().put("version",old).put("games",new JSONObject());ready=true;
                PerfTrace.event("CATALOG_FALLBACK verified=true version="+version+" fingerprint="+bundleHash+" bytes="+prior.length());
            }catch(Exception ignored){if(db!=null){db.close();db=null;}error=UiStrings.msg("ui_eaf098989e8a");bundleHash="unavailable";ready=true;}
        }
        main.post(()->listener.updated(""));
    }
    private SQLiteDatabase open(File f){return SQLiteDatabase.openDatabase(f.toString(),null,SQLiteDatabase.OPEN_READONLY|SQLiteDatabase.NO_LOCALIZED_COLLATORS);}
    private static void checkDatabase(SQLiteDatabase database)throws IOException{try(Cursor c=database.rawQuery("PRAGMA quick_check",null)){if(!c.moveToFirst()||!"ok".equals(c.getString(0)))throw new IOException("Catalog SQLite check failed");}}
    private String value(String key){return value(db,key);}
    private static String value(SQLiteDatabase database,String key){try(Cursor c=database.rawQuery("SELECT value FROM info WHERE key=?",new String[]{key})){return c.moveToFirst()?c.getString(0):"";}}
    static boolean cachedBindingMatches(String work,String release,String gameCode,String verifiedWork,String verifiedSerial,String status,String identifiedId){return !work.isEmpty()&&work.equals(verifiedWork)&&gameCode.equals(verifiedSerial)&&status.equals("IDENTIFIED")&&release.equals(identifiedId);}
    private volatile Map<String,ResolveDecision> publishedDecisions=Collections.emptyMap();
    ResolveDecision publishedDecision(String id){return publishedDecisions.get(id);}
    boolean hasRelease(String id){MetadataIndex i=identityIndex;return i!=null&&i.ids.containsKey(id);}
    synchronized void bindGames(List<GameEntry> games,MetadataManager metadata){
        libraryReady=false;final long request=++generation;List<GameEntry> input=new ArrayList<>(games);
        matchState(request,MatchPhase.MATCHING,input.size(),UxStrings.s(7));
        worker.execute(()->{
            if(request!=generation)return;
            if(db==null){if(publishBindingFailure(request,"Catalog unavailable"))matchState(request,MatchPhase.ERROR,input.size(),error);return;}
            long started=SystemClock.uptimeMillis();
            try{
                MetadataIndex identity=coverIdentity();Map<String,Head> next=new LinkedHashMap<>();Map<String,ResolveDecision> decisions=new LinkedHashMap<>();JSONObject cache=new JSONObject();
                for(GameEntry g:input){
                    if(request!=generation)return;
                    ResolveDecision decision=ResolveDecision.resolve(g,identity,metadata.overrideCopy(g.gameId));
                    cache.put(g.gameId,decision.json(version));decisions.put(g.gameId,decision);
                    if(decision.work.isEmpty()||decision.kind==ResolveDecision.Kind.USER_CONFLICT)continue;
                    Head head=headCache.get(decision.work);
                    if(head==null){
                        String sql="SELECT w.work_id,w.original_title,w.preferred_title_zh,w.aliases,w.genres,w.kind,w.has_summary,w.content_hash"+(localizedContent?",COALESCE(e.kind,'MISSING'),length(trim(COALESCE(e.summary,''))),COALESCE(n.name,''),COALESCE(n.kind,'ORIGINAL_LANGUAGE_FALLBACK')":"")+" FROM works w "+(localizedContent?"LEFT JOIN localized_content e ON e.work_id=w.work_id AND e.locale='en' LEFT JOIN localized_names n ON n.work_id=w.work_id AND n.locale='en' ":"")+"WHERE w.work_id=?";
                        try(Cursor c=db.rawQuery(sql,new String[]{decision.work})){if(c.moveToFirst()){head=new Head(c);if(localizedContent){head.englishKind=c.getString(8);head.hasEnglish=c.getInt(9)>0;head.englishName=c.getString(10);head.englishNameKind=c.getString(11);}headCache.put(head.workId,head);}}
                    }
                    if(head!=null)next.put(g.gameId,head);
                }
                int readable=0;for(Head h:next.values())if(h.has(LocaleSettings.content()))readable++;
                JSONObject savedNext=new JSONObject().put("version",bundleHash).put("resolverVersion",ResolveDecision.VERSION).put("games",cache);
                write(new AtomicFile(new File(dir,"bindings.json")),savedNext.toString());saved=savedNext;
                if(request!=generation)return;publishedDecisions=Collections.unmodifiableMap(decisions);
                if(!publishBindings(request,Collections.unmodifiableMap(next),bundleHash,next.size(),readable))return;
                matchState(request,MatchPhase.COMPLETE,input.size(),UxStrings.s(8)+next.size()+" / "+input.size());
                PerfTrace.event("CATALOG_BIND games="+input.size()+" matched="+matchedCount+" readable="+readableCount+" elapsedMs="+(SystemClock.uptimeMillis()-started));
                main.post(()->listener.updated(""));
            }catch(Exception e){if(publishBindingFailure(request,e.getClass().getSimpleName()))matchState(request,MatchPhase.ERROR,input.size(),error);Log.e("TwinGridCatalog","BIND_FAILED",e);}
        });
    }
    Text text(String gameId){return text(gameId,LocaleSettings.content());}
    Text text(String gameId,String locale){
        Head h=head(gameId);if(h==null)return null;String key=h.workId+":"+locale;Text current=textCache.get(key);if(current!=null)return current;
        if(requested.size()<12&&requested.add(key))worker.execute(()->{try{textCache.put(key,readText(h.workId,locale,true));}catch(Exception e){Log.w("TwinGridCatalog","TEXT_READ_FAILED",e);}finally{requested.remove(key);main.post(()->listener.updated(gameId));}});return null;
    }
    private Text readText(String work,String locale,boolean fallback)throws IOException{
        if(locale.equals("en")&&localizedContent)try(Cursor c=db.rawQuery("SELECT summary,details,evidence FROM localized_content WHERE work_id=? AND locale='en'",new String[]{work})){if(c.moveToFirst()&&!c.getString(0).trim().isEmpty())return new Text(c.getString(0),c.getString(1),c.getString(2),"en",false);}
        if(locale.equals("en")&&!fallback)return new Text("","","{}","en",false);
        try(Cursor c=db.rawQuery("SELECT summary_zh,details_zh,evidence FROM content WHERE work_id=?",new String[]{work})){if(c.moveToFirst())return new Text(c.getString(0),c.getString(1),c.getString(2),"zh",locale.equals("en"));}throw new IOException("Content record missing");
    }
    void editableText(String gameId,java.util.function.BiConsumer<String,String> callback){editableText(gameId,LocaleSettings.content(),callback);}
    void editableText(String gameId,String locale,java.util.function.BiConsumer<String,String> callback){
        Head requestedHead=head(gameId);if(!ready()){main.post(()->callback.accept(null,UiStrings.msg("ui_679745227ba1")));return;}
        if(requestedHead==null||!requestedHead.has(locale)){main.post(()->callback.accept("",""));return;}
        worker.execute(()->{String value=null,problem="";try{Text text=readText(requestedHead.workId,locale,false);textCache.put(requestedHead.workId+":"+locale,text);value=text.details.isEmpty()?text.summary:text.details;}catch(Exception e){problem=UiStrings.msg("ui_a6331a4547d6")+e.getClass().getSimpleName();}
            final String result=value,error=problem;main.post(()->{Head current=head(gameId);if(!ready()||current==null||!current.workId.equals(requestedHead.workId))callback.accept(null,UiStrings.msg("ui_d8ea5b864bba"));else callback.accept(result,error);});});
    }
    synchronized void languageChanged(){int count=0;for(Head h:bindings.values())if(h.has(LocaleSettings.content()))count++;readableCount=count;match=new MatchSnapshot(match.revision+1,match.started,match.ended,match.phase,match.total,match.matched,count,version,UiStrings.msg("ui_160743299abe")+count+" / "+match.total);main.post(()->listener.updated(""));}
    String brief(){return version+"\n"+FirstRun.s(10)+editedCount+" · "+FirstRun.s(11)+englishCount+"\n"+FirstRun.s(12)+matchedCount+" · "+FirstRun.s(13)+readableCount;}
    String summary(){return UiStrings.msg("ui_e2faba8465e6")+version+UiStrings.msg("ui_3ff2762c7023")+releaseCount+UiStrings.msg("ui_20f2b2fbe836")+editedCount+UiStrings.msg("ui_0d10e0e2da62")+matchedCount+UiStrings.msg("ui_36fcc2e1f963")+readableCount+UiStrings.msg("ui_28a5c369ca52")+(error.isEmpty()?"":"\n"+error);}
    /** Called on LocalSearch's background executor; no raw source parsing or transliterator setup. */
    Map<String,String[]> searchNames(Set<String> wanted){
        Map<String,String[]> result=new HashMap<>();SQLiteDatabase database=db;if(database==null)return result;
        List<String> names=new ArrayList<>(wanted);long startTime=SystemClock.uptimeMillis();int batches=0;
        for(int start=0;start<names.size();start+=200){List<String> batch=names.subList(start,Math.min(names.size(),start+200));String placeholders=String.join(",",Collections.nCopies(batch.size(),"?"));
            try(Cursor c=database.rawQuery("SELECT name,folded,pinyin,initials FROM search_names WHERE name IN ("+placeholders+")",batch.toArray(new String[0]))){while(c.moveToNext())result.put(c.getString(0),new String[]{c.getString(1),c.getString(2),c.getString(3)});}batches++;
        }
        PerfTrace.event("CATALOG_SEARCH_NAMES wanted="+wanted.size()+" found="+result.size()+" batches="+batches+" elapsedMs="+(SystemClock.uptimeMillis()-startTime));
        return result;
    }
    private volatile MetadataIndex identityIndex;
    private final Object identityLoadLock=new Object();
    MetadataIndex coverIdentity(){
        // prepare() is the only DB replacement path; ready is published after it
        // completes validation/fallback, so no loader can publish from a staged DB.
        if(!ready)return null;
        if(identityIndex!=null)return identityIndex;
        // SQL and normalization may be slow. Do not hold the readiness/publication
        // monitor used by the UI; retain one loader and volatile complete publication.
        synchronized(identityLoadLock){
        if(identityIndex!=null)return identityIndex;
        MetadataIndex result=new MetadataIndex();if(db==null)return null;
        try(Cursor c=db.rawQuery("SELECT release_id,name,serial,sha1,crc,region,bytes,work_id FROM releases",null)){while(c.moveToNext()){
            MetadataIndex.Release r=new MetadataIndex.Release();r.catalogId=c.getString(0);r.name=c.getString(1);r.serial=c.getString(2);r.sha1=c.getString(3);r.crc=c.getString(4);r.region=c.getString(5);r.size=c.getLong(6);r.work=c.getString(7);
            result.ids.put(r.id(),r);result.serials.computeIfAbsent(r.serial,k->new ArrayList<>()).add(r);result.names.computeIfAbsent(MetadataIndex.normalize(r.name),k->new ArrayList<>()).add(r);
        }}
        try(Cursor c=db.rawQuery("SELECT work_id,text FROM aliases UNION SELECT work_id,original_title FROM works",null)){while(c.moveToNext())result.workAliases.computeIfAbsent(c.getString(0),k->new HashSet<>()).add(ResolveDecision.name(c.getString(1)));}
        try(Cursor c=db.rawQuery("SELECT serial,internal_title,banner_title,release_id FROM fingerprints",null)){while(c.moveToNext())result.fingerprints.computeIfAbsent(ResolveDecision.fingerprint(c.getString(0),c.getString(1),c.getString(2)),k->new HashSet<>()).add(c.getString(3));}
        identityIndex=result;return result;
        }
    }
    ResolveDecision decision(GameEntry game,JSONObject user){MetadataIndex i=identityIndex;return i==null?null:ResolveDecision.resolve(game,i,user);}
    String releaseWork(String id){MetadataIndex i=identityIndex;MetadataIndex.Release r=i==null?null:i.ids.get(id);return r==null?"":r.work;}
    private static List<String> strings(String raw)throws JSONException{JSONArray a=new JSONArray(raw);List<String> result=new ArrayList<>();for(int n=0;n<a.length();n++)result.add(a.getString(n));return Collections.unmodifiableList(result);}
    private static byte[] read(InputStream in,int limit)throws IOException{try(InputStream source=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=source.read(b))!=-1){if(out.size()+n>limit)throw new IOException("Bounded read");out.write(b,0,n);}return out.toByteArray();}}
    private static void write(AtomicFile f,String text)throws IOException{FileOutputStream out=null;try{out=f.startWrite();out.write(text.getBytes(StandardCharsets.UTF_8));f.finishWrite(out);}catch(IOException e){if(out!=null)f.failWrite(out);throw e;}}
    private static String hex(byte[] bytes){StringBuilder b=new StringBuilder();for(byte v:bytes)b.append(String.format(Locale.ROOT,"%02x",v&255));return b.toString();}
}
