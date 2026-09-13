package com.rgds.ultimate.shell;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.*;
import android.net.*;
import android.os.*;
import android.util.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import android.os.Process;

/** App-owned metadata, durable per-item jobs, and single-request HTTPS worker. No ROM/save writes. */
final class MetadataManager {
    private static volatile MetadataManager instance;
    static MetadataManager get(Context c){MetadataManager m=instance;if(m==null)synchronized(MetadataManager.class){m=instance;if(m==null)instance=m=new MetadataManager(c.getApplicationContext());}return m;}
    static final String THUMB_TREE="https://api.github.com/repos/libretro-thumbnails/Nintendo_-_Nintendo_DS/git/trees/master?recursive=1";
    static final String THUMB_BASE="https://raw.githubusercontent.com/libretro-thumbnails/Nintendo_-_Nintendo_DS/master/";
    static final long DAY=86400000L,MAX_CACHE=160L*1024*1024;
    private static final java.util.regex.Pattern IMAGE_PATH=java.util.regex.Pattern.compile("(?:downloaded|local)/[a-f0-9]{64}\\.img");
    private static final java.util.regex.Pattern SHA256=java.util.regex.Pattern.compile("[a-f0-9]{64}");
    static final class Info {
        final JSONObject raw;
        final String status,title,genre,region,provider,id,basis,localCover,remoteCover,error,coverRegion,description,descriptionLanguage,descriptionShort;
        final List<String> categories;
        Info(JSONObject o){raw=o;status=o.optString("status","UNIDENTIFIED");title=o.optString("title");genre=o.optString("genre");region=o.optString("region");provider=o.optString("provider");id=o.optString("externalId");basis=o.optString("basis");localCover=o.optString("localCover");remoteCover=o.optString("remoteCover");error=o.optString("error");coverRegion=o.optString("coverRegion");description=o.optString("description");descriptionLanguage=o.optString("descriptionLanguage","und");descriptionShort=o.optString("descriptionShort");categories=automaticGenres(o);}
        String label(){return status.equals("WORK_IDENTIFIED")?UxStrings.s(107):status.equals("IDENTIFIED")?UiStrings.msg("ui_4d26bc13c4df"):status.equals("CANDIDATE")?UiStrings.msg("ui_1bc2a5e16dd2"):status.equals("UNAVAILABLE")?UiStrings.msg("ui_ae7a38b48f18"):UiStrings.msg("ui_5619f3a11913");}
    }
    static final Info EMPTY=new Info(new JSONObject());
    static List<String> automaticGenres(JSONObject o){
        JSONArray cached=o.optJSONArray("categoriesV7");if(cached!=null){List<String> result=new ArrayList<>();for(int n=0;n<cached.length();n++)result.add(cached.optString(n));return Collections.unmodifiableList(result);}
        List<String> base=GenreTaxonomy.parse(o.optString("genreProvider"),o.optString("genre")).categories;JSONObject z=o.optJSONObject("chinese");if(z==null||z.optInt("subjectId")==0||z.optBoolean("platformVariantReview"))return base;
        GenreTaxonomy.Result chinese=GenreTaxonomy.parse("Bangumi structured game type",z.optString("rawGenre"));
        return chinese.unmapped.isEmpty()&&chinese.categories.containsAll(base)?chinese.categories:base;
    }
    String chineseName(String id){OfflineCatalog.Head h=catalog.head(id);return h==null?"":h.title;}
    List<String> chineseAliases(String id){OfflineCatalog.Head h=catalog.head(id);return h==null?Collections.emptyList():h.aliases;}
    private String chineseText(String id,boolean full){OfflineCatalog.Text t=catalog.text(id);return t==null?"":full&&!t.details.isEmpty()?t.details:t.summary;}
    String descriptionQuality(String id){EffectiveMetadata e=effective(id);if(e.textSource.equals("USER"))return e.gameplay?UiStrings.msg("ui_b9d0546ab47e"):UiStrings.msg("ui_ab2a249cae2c");if(e.textSource.equals("CONFIRMED"))return UiStrings.msg("ui_4e71a378dc47");OfflineCatalog.Head h=catalog.head(id);return h==null||!h.has(LocaleSettings.content())?"":h.kind(LocaleSettings.content()).equals("STORY_ONLY")?UiStrings.msg("ui_7b4e8faebb7d"):h.kind(LocaleSettings.content()).equals("REFERENCE")?UiStrings.msg("ui_aaa46aa4ec01"):h.kind(LocaleSettings.content()).equals("READING")||h.kind(LocaleSettings.content()).equals("UTILITY")?UiStrings.msg("ui_4b99e0820e48"):UiStrings.msg("ui_26ac5cb501e8");}
    final OfflineCatalog catalog;
    boolean searchReady(){return ready&&catalog.ready();}
    boolean cacheInputsReady(){return !searchInputFingerprint.isEmpty()&&catalog.prepared();}
    String searchInputFingerprint(){return searchInputFingerprint;}
    private void catalogChanged(String id){
        taskSignal(true);
        if(id.equals("@match"))return;
        if(!id.isEmpty()){main.post(()->{for(Listener l:listeners)l.metadataUpdated(id);});return;}
        signal(id);
        if(id.isEmpty()){
            if(cacheInputsReady())main.post(()->ShellStateRepository.get(context).searchInputsChanged());
            if(ready&&ShellStateRepository.get(context).snapshot().libraryReady&&!catalog.ready())catalog.bindGames(ShellStateRepository.get(context).allGamesCopy(),this);
            else if(catalog.ready()){ShellStateRepository.get(context).metadataChanged();worker.execute(()->{effectiveRevision++;stats();completeEdits();maybeFirstOrganization();schedulePump(0);main.post(()->signal(""));});}
        }
    }
    interface Listener {void metadataUpdated(String gameId);}
    interface TaskListener {void taskUpdated();}
    private final Set<TaskListener> taskListeners=new CopyOnWriteArraySet<>();
    private final CoverTask coverTask=new CoverTask();
    private volatile boolean asleep,taskHydrated,indexRefresh;
    private volatile String operationNotice="";
    private final Map<String,Boolean> nextRequests=new ConcurrentHashMap<>();
    int nextRequestCount(){return nextRequests.size();}
    String taskActionText(){return task().terminal()&&!nextRequests.isEmpty()?UiStrings.msg("ui_fc21312490ab"):task().actionText();}
    private void saveNext(){prefs.edit().putString("cover_next",new JSONObject(nextRequests).toString()).apply();}
    private TaskNotifications taskNotifications;
    volatile TaskReceipts receipts;
    private volatile long effectiveRevision;
    private boolean taskNotifyPending;
    Context contextForUi(){return context;}
    void notifyTaskUi(){taskSignal(true);}
    CoverTask.Snapshot task(){return coverTask.snapshot();}
    boolean tasksReady(){return ready&&taskHydrated&&ShellStateRepository.get(context).snapshot().libraryReady;}
    void addTaskListener(TaskListener l){taskListeners.add(l);}
    void removeTaskListener(TaskListener l){taskListeners.remove(l);}
    String operationNotice(){return operationNotice;}
    void savedNotice(String message){operationNotice=message;taskSignal(true);signal("");}
    String taskControlKey(){CoverTask.Snapshot s=task();return s.id+":"+s.action+":"+nextRequests.size();}
    private void taskSignal(boolean immediate){
        main.post(()->{
            if(immediate){main.removeCallbacks(taskPublish);taskNotifyPending=false;taskPublish.run();}
            else if(!taskNotifyPending){taskNotifyPending=true;main.postDelayed(taskPublish,350);}
        });
    }
    private final Runnable taskPublish=()->{
        SetupJourney.task(contextForUi(),task());taskNotifyPending=false;for(TaskListener l:taskListeners)l.taskUpdated();
        if(taskNotifications!=null)taskNotifications.update(task());
        PerfTrace.event("COVER_TASK "+task().json());
    };
    private void persistTask(){
        CoverTask.Snapshot s=task();db.execSQL("INSERT OR REPLACE INTO cover_session VALUES(1,?)",new Object[]{s.json().toString()});
        if(receipts!=null&&TaskReceipts.result(s)&&receipts.find(TaskReceipts.key(s))==null)try{Map<String,GameEntry> games=new HashMap<>();for(GameEntry g:ShellStateRepository.get(context).allGamesCopy())games.put(g.gameId,g);receipts.capture(s,coverTask.items(),games,this);main.postDelayed(()->taskSignal(true),8500);}catch(JSONException e){throw new IllegalStateException(e);}
    }
    String taskBrief(){CoverTask.Snapshot s=task();if(!TaskReceipts.result(s))return s.state==CoverTask.State.IDLE?UiStrings.msg("ui_9a1d8c6a5082"):s.brief();String key=TaskReceipts.key(s);boolean unread=receipts!=null&&receipts.unread(key);return unread&&s.ended>0&&System.currentTimeMillis()-s.ended<8000?UiStrings.msg("ui_1e883999c246"):unread?UiStrings.msg("ui_5a574d4fee00"):UiStrings.msg("ui_9a1d8c6a5082");}
    void acknowledgeResult(String key,boolean dismiss){if(dismiss)operationNotice="";if(receipts!=null)receipts.acknowledge(key,dismiss);if(taskNotifications!=null)taskNotifications.cancel(key);taskSignal(true);}
    void receiptItems(String key,java.util.function.Consumer<JSONArray> callback){worker.execute(()->{JSONArray rows;try{rows=receipts==null?new JSONArray():receipts.items(key);}catch(Exception e){rows=new JSONArray();}final JSONArray result=rows;main.post(()->callback.accept(result));});}
    private EffectiveMetadata project(String id){return project(id,LocaleSettings.content());}
    private EffectiveMetadata project(String id,String locale){return new EffectiveMetadata(catalog.head(id),overrides.get(id),info(id),catalog.ready(),!coverKey(id).isEmpty(),genres(id),locale,catalog.publishedDecision(id));}
    private static final class GapSnapshot {
        final Map<String,EffectiveMetadata> values;final List<List<GameEntry>> groups;final String coverage,locale;
        GapSnapshot(Map<String,EffectiveMetadata> values,List<List<GameEntry>> groups,String coverage,String locale){this.values=Collections.unmodifiableMap(values);this.groups=groups;this.coverage=coverage;this.locale=locale;}
    }
    private final Map<String,EditResult> pendingEdits=new LinkedHashMap<>();
    private volatile long metadataSaveActions;
    private volatile GapSnapshot gapSnapshot;
    boolean identityConflict(String id){JSONObject o=overrides.get(id);if(o==null)return false;String work=o.optString("workId"),release=o.optString("externalId");return !release.isEmpty()&&(!catalog.hasRelease(release)||!work.isEmpty()&&!work.equals(catalog.releaseWork(release)));}
    JSONObject repairDiagnostics(){try{boolean complete=gapsReady();JSONObject o=new JSONObject().put("effectiveRevision",effectiveRevision).put("metadataSaveActions",metadataSaveActions).put("ready",complete);JSONArray counts=new JSONArray();for(int n=0;n<8;n++)counts.put(complete?gaps(n).size():JSONObject.NULL);return o.put("gapCounts",counts);}catch(JSONException e){throw new IllegalStateException(e);}}
    private void completeEdits(){if(!catalog.ready())return;stats();if(pendingEdits.isEmpty())return;Map<String,EditResult> done=new LinkedHashMap<>(pendingEdits);pendingEdits.clear();for(Map.Entry<String,EditResult> row:done.entrySet()){signal(row.getKey());main.post(()->{ShellStateRepository.get(context).metadataChanged();row.getValue().complete(true,UxStrings.s(9));taskSignal(true);});}}

    EffectiveMetadata effective(String id){GapSnapshot s=gapSnapshot;EffectiveMetadata e=s==null?null:s.values.get(id);return e==null||!e.locale.equals(LocaleSettings.content())?project(id):e;}
    boolean gapsReady(){GapSnapshot s=gapSnapshot;ShellStateRepository.Snapshot library=ShellStateRepository.get(context).snapshot();return ready&&catalog.ready()&&library.libraryReady&&s!=null&&s.values.size()==library.totalGameCount&&s.locale.equals(LocaleSettings.content());}
    List<GameEntry> gaps(int category){GapSnapshot s=gapSnapshot;return s==null||!s.locale.equals(LocaleSettings.content())?Collections.emptyList():s.groups.get(category);}
    private void publishGaps(List<GameEntry> games){
        Map<String,EffectiveMetadata> values=new HashMap<>();List<List<GameEntry>> groups=new ArrayList<>();for(int n=0;n<8;n++)groups.add(new ArrayList<>());
        String locale=LocaleSettings.content();int chinese=0,bundle=0,missing=0,loading=0;for(GameEntry g:games){String id=g.gameId;EffectiveMetadata e=project(id,locale);values.put(id,e);if(e.text==EffectiveMetadata.TextState.VALID)chinese++;if(e.bundled)bundle++;if(e.missingText())missing++;if(e.text==EffectiveMetadata.TextState.LOADING)loading++;
            JSONObject user=overrides.get(id);Info i=info(id);boolean conflict=identityConflict(id);boolean failure=i.status.equals("UNAVAILABLE")||!i.error.isEmpty();boolean[] flags={e.missingText(),e.text==EffectiveMetadata.TextState.VALID&&!e.reviewedContent(),!e.typed,!e.linked,e.candidate||conflict,!e.cover||e.coverReview,failure,!e.reason().isEmpty()||conflict||failure};for(int n=0;n<8;n++)if(flags[n])groups.get(n).add(g);
        }
        for(int n=0;n<8;n++)groups.set(n,Collections.unmodifiableList(groups.get(n)));
        if(!locale.equals(LocaleSettings.content()))return;
        gapSnapshot=new GapSnapshot(values,Collections.unmodifiableList(groups),locale+" · "+UiStrings.msg("ui_0da6ef405fa1")+games.size()+UiStrings.msg("ui_c23d37ec9507")+chinese+UiStrings.msg("ui_65129dd345ae")+bundle+UiStrings.msg("ui_f537df555a2a")+missing+UiStrings.msg("ui_d8e77191667b")+covered+UiStrings.msg("ui_ddd0392bcc1f")+unclassified+(loading>0?UiStrings.msg("ui_6390744e8a64")+loading:"")+UiStrings.msg("ui_91506b1c07d5"),locale);
    }
    List<GameEntry> attention(int group){
        List<GameEntry> out=new ArrayList<>();for(GameEntry g:gaps(7)){EffectiveMetadata e=effective(g.gameId);
            boolean manual=e.candidate||identityConflict(g.gameId)||e.coverReview;
            boolean automatic=!manual&&e.linked&&!e.cover;
            boolean content=e.missingText()||e.text==EffectiveMetadata.TextState.VALID&&!e.reviewedContent()||!e.typed||!e.linked&&!manual;
            if(group==0?manual:group==1?automatic:content)out.add(g);
        }return out;
    }
    long effectiveRevision(){return effectiveRevision;}
    String explicitWork(String id){JSONObject o=overrides.get(id);return o==null?"":o.optString("workId");}
    JSONObject overrideCopy(String id){try{return new JSONObject(overrides.getOrDefault(id,new JSONObject()).toString());}catch(JSONException e){throw new IllegalStateException(e);}}
    void inspectInfo(String id,java.util.function.Consumer<Info> callback){worker.execute(()->{Info detail=readFull(id);main.post(()->callback.accept(detail));});}

    private void persistItem(CoverTask.Item item){
        db.execSQL("UPDATE cover_plan SET result=?,reason=?,retry_at=?,attempts=? WHERE id=?",new Object[]{item.result.name(),item.reason,item.retryAt,item.attempts,item.id});
    }
    private void restoreTask()throws JSONException{
        receipts=new TaskReceipts(context,db);
        JSONObject next=new JSONObject(prefs.getString("cover_next","{}"));Iterator<String> keys=next.keys();while(keys.hasNext()){String id=keys.next();nextRequests.put(id,next.optBoolean(id));}
        db.execSQL("CREATE TABLE IF NOT EXISTS first_setup (id INTEGER PRIMARY KEY,session TEXT NOT NULL)");
        try(Cursor once=db.rawQuery("SELECT session FROM first_setup WHERE id=1",null)){if(once.moveToFirst())FirstRun.setupStarted(context,once.getString(0));}
        db.execSQL("CREATE TABLE IF NOT EXISTS cover_session (id INTEGER PRIMARY KEY,data TEXT NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS cover_plan (id TEXT PRIMARY KEY,ordinal INTEGER,alternative INTEGER,result TEXT,reason TEXT,retry_at INTEGER,attempts INTEGER)");
        try(Cursor c=db.rawQuery("SELECT data FROM cover_session WHERE id=1",null)){if(c.moveToFirst()){
            List<CoverTask.Item> items=new ArrayList<>();try(Cursor q=db.rawQuery("SELECT id,alternative,result,reason,retry_at,attempts FROM cover_plan ORDER BY ordinal",null)){while(q.moveToNext()){
                CoverTask.Item i=new CoverTask.Item(q.getString(0),q.getInt(1)!=0);i.result=CoverTask.Result.valueOf(q.getString(2));i.reason=q.getString(3);i.retryAt=q.getLong(4);i.attempts=q.getInt(5);items.add(i);
            }}coverTask.restore(new JSONObject(c.getString(0)),items,paused);persistTask();
        }}indexRefresh=task().trigger.equals("INDEX");for(CoverTask.Item item:coverTask.items())if(item.id.equals("@cover-index"))indexRefresh=true;taskHydrated=true;taskSignal(true);
    }
    String coverage(){GapSnapshot s=gapSnapshot;return !gapsReady()?UiStrings.msg("ui_d28efe456cb9"):s.coverage;}

    private volatile int covered;

    private final Context context;
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->new Thread(()->{Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT);r.run();},"metadata-worker"));
    private final CoverLoader coverLoader=new CoverLoader(this);
    private final ExecutorService detailReader=Executors.newSingleThreadExecutor(r->new Thread(r,"metadata-details"));
    private final ExecutorService connectionCloser=Executors.newSingleThreadExecutor(r->new Thread(r,"cover-cancel"));
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Map<String,Info> infos=new ConcurrentHashMap<>();
    // Invalidated transactionally by games-table triggers. This is a derived key cache,
    // not another metadata authority, and contains no descriptions or user history.
    private final Map<String,String> searchSignatures=new HashMap<>();
    private volatile String searchInputFingerprint="";
    private final Map<String,JSONObject> overrides=new ConcurrentHashMap<>();
    private final Map<String,File> coverFiles=new ConcurrentHashMap<>();
    private final Map<String,String> coverVersions=new ConcurrentHashMap<>();
    private final Set<Listener> listeners=new CopyOnWriteArraySet<>();
    private final LinkedHashSet<String> priority=new LinkedHashSet<>();
    private final Set<String> queued=ConcurrentHashMap.newKeySet();
    private final Map<String,List<String>> overrideGenres=new ConcurrentHashMap<>();
    private long statsAt,thumbnailScanAt;private boolean metadataPublishScheduled;
    private String identityVersion="libretro cached index";
    private final File directory,downloadDir,localDir,indexDir;
    private SharedPreferences prefs;
    private SQLiteDatabase db;
    private volatile MetadataIndex index;
    private OpenVgdbIndex supplemental;
    private volatile boolean ready,autoEnabled,allowMetered,paused,external,online,batchRequested;
    private BangumiSource bangumi;private final LinkedHashMap<String,List<GameEntry>> chineseQueue=new LinkedHashMap<>();
    private boolean chineseSample,chinesePumpScheduled,chineseOnline,chineseRefreshAfterLocal,seedLoaded;private volatile String chineseProgress=UiStrings.msg("ui_69d24e5f811a");
    private volatile int nativeChinese,chineseGameplay,chineseStory,chineseRelease,chineseMatched,chineseFault;
    private volatile Map<String,Integer> genreCounts=Collections.emptyMap();int genreCount(String genre){return genreCounts.getOrDefault(genre,0);}
    private volatile String taskState=UiStrings.msg("ui_6559ede452c6"),sourceError="";
    private volatile String jobDetails="";
    String jobReport(){return jobDetails;}
    Info fullForAudit(String id){JSONObject user=overrides.get(id);JSONObject release=user==null?null:user.optJSONObject("confirmedRelease");return release==null?readFull(id):new Info(release);}JSONObject overrideForAudit(String id){return overrides.getOrDefault(id,new JSONObject());}
    void exportAudit(){worker.execute(()->{try{LibraryAudit.export(context,this);taskState=UiStrings.msg("ui_3194c51b5407");operationNotice=taskState;taskSignal(true);}catch(Exception e){taskState=UiStrings.msg("ui_6fc81641283c")+e.getClass().getSimpleName();Log.e("TwinGridAudit","EXPORT_FAILED",e);}signal("");});}
    private volatile int pending,completed,total,identified,typed,candidates,localLinked,remoteLinked,failed,unclassified,described;
    private volatile long imageBytes,localBytes,thumbnailBytes,lastNotify,nextGlobalRequest;
    private volatile int imageCount;
    private volatile DirectoryIndex dirs;
    private final Map<String,Set<String>> picturesByParent=new HashMap<>(),artByName=new HashMap<>();
    private final Map<String,Integer> baseCounts=new HashMap<>(),parentBaseCounts=new HashMap<>();
    private volatile HttpURLConnection activeConnection;
    private final SourceHttp sourceHttp=new SourceHttp();
    private final CoverTransport coverTransport=new CoverTransport();
    private String currentGame="";
    private boolean pumpScheduled;
    private final ConnectivityManager network;
    private long sourceRetryAt;
    private long optionalRetryAt;
    private final Map<String,Long> hostBackoff=new HashMap<>();
    private MetadataManager(Context c){
        context=c;directory=new File(c.getFilesDir(),"metadata_v4");downloadDir=new File(directory,"downloaded");localDir=new File(directory,"local");indexDir=new File(directory,"indices");
        catalog=new OfflineCatalog(c,this::catalogChanged);
        taskNotifications=new TaskNotifications(c);
        network=c.getSystemService(ConnectivityManager.class);worker.execute(this::restore);
        try{network.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback(){
            @Override public void onAvailable(Network n){coverTransport.networkChanged();wake();}
            @Override public void onLost(Network n){wake();}
            @Override public void onCapabilitiesChanged(Network n,NetworkCapabilities cap){wake();}
        });}catch(Exception e){Log.w("TwinGridMetadata","NETWORK_CALLBACK "+e.getClass().getSimpleName());}
    }
    void addListener(Listener l){listeners.add(l);}void removeListener(Listener l){listeners.remove(l);}
    Info info(String id){JSONObject user=overrides.get(id);JSONObject release=user==null?null:user.optJSONObject("confirmedRelease");if(release==null)return infos.getOrDefault(id,EMPTY);try{JSONObject merged=new JSONObject(infos.getOrDefault(id,EMPTY).raw.toString());Iterator<String> keys=release.keys();while(keys.hasNext()){String key=keys.next();merged.put(key,release.get(key));}merged.put("candidateCount",0);return new Info(merged);}catch(JSONException error){return new Info(release);}}

    String title(GameEntry g){if(g==null)return UiStrings.msg("ui_551e846a4ac6");JSONObject o=overrides.get(g.gameId);return o!=null&&!o.optString("displayName").isEmpty()?o.optString("displayName"):g.menuTitle();}
    String rawGenre(String id){JSONObject o=overrides.get(id);return o!=null&&o.has("genre")?o.optString("genre"):info(id).genre;}
    List<String> genres(String id){List<String> user=overrideGenres.get(id);if(user!=null)return user;OfflineCatalog.Head h=catalog.head(id);return h!=null&&!h.genres.isEmpty()?h.genres:!explicitWork(id).isEmpty()||overrides.get(id)!=null&&overrides.get(id).has("confirmedRelease")?Collections.emptyList():info(id).categories;}
    private void cacheOverride(String id,JSONObject o){overrides.put(id,o);List<String> a=new ArrayList<>();if(o.has("categories")){JSONArray j=o.optJSONArray("categories");if(j!=null)for(int n=0;n<j.length();n++)a.add(j.optString(n));overrideGenres.put(id,GenreTaxonomy.validOverrides(a));}else if(o.has("genre"))overrideGenres.put(id,GenreTaxonomy.categories(o.optString("genre")));else overrideGenres.remove(id);}
    boolean hasGenre(String id,String filter){if(filter.equals("all"))return true;List<String> a=genres(id);return filter.equals("unclassified")&&a.isEmpty()||a.contains(filter);}
    String genreLabel(String id){List<String> a=genres(id);return a.isEmpty()?(!GenreTaxonomy.parse(info(id).raw.optString("genreProvider"),rawGenre(id)).unmapped.isEmpty()?UiStrings.msg("ui_0f2f210f449f"):UiStrings.msg("ui_54b89f901274")):GenreTaxonomy.labels(a);}
    boolean userEdited(String id){return overrides.containsKey(id);}
    boolean customName(String id){JSONObject o=overrides.get(id);return o!=null&&!o.optString("displayName").isEmpty();}
    String coverKey(String id){JSONObject o=overrides.get(id);if(o!=null&&o.has("cover"))return coverFiles.containsKey(o.optString("cover"))?o.optString("cover"):"";if(o!=null&&o.optBoolean("identityCoverHold"))return "";Info i=info(id);return coverFiles.containsKey(i.localCover)?i.localCover:coverFiles.containsKey(i.remoteCover)?i.remoteCover:"";}
    boolean enabled(){return autoEnabled;}boolean meteredAllowed(){return allowMetered;}boolean isPaused(){return paused;}
    String progress(){return taskBrief();}
    String cacheUsage(){return String.format(Locale.US,UiStrings.display(UiStrings.msg("ui_d2e7329fee33")),imageBytes/1048576.0,localBytes/1048576.0,thumbnailBytes/1048576.0);}
    String summary(){return chineseSummary()+UiStrings.msg("ui_4d9bb79886d9")+total+UiStrings.msg("ui_533f5177061b")+identified+UiStrings.msg("ui_35758b444ced")+candidates+UiStrings.msg("ui_b244ae59098a")+unclassified+UiStrings.msg("ui_c66fd79de46a")+localLinked+UiStrings.msg("ui_cb8ea1fa801f")+remoteLinked+UiStrings.msg("ui_9e06636c65de")+imageCount+" / "+String.format(Locale.US,UiStrings.display(UiStrings.msg("ui_ecb746332e17")),imageBytes/1048576.0)+UiStrings.msg("ui_e02b6bd74d88")+String.format(Locale.US,"%.1f MiB",localBytes/1048576.0)+UiStrings.msg("ui_aea00e89371c")+pending+UiStrings.msg("ui_0f02b0205454")+failed+"\n"+taskState+(sourceError.isEmpty()?"":"\n"+sourceError);}
    private void restore(){
        try{
            for(File f:new File[]{directory,downloadDir,localDir,indexDir})if(!f.isDirectory()&&!f.mkdirs())throw new IOException("Private directory unavailable");
            prefs=context.getSharedPreferences("metadata_v4",0);autoEnabled=prefs.getBoolean("auto",false);allowMetered=prefs.getBoolean("metered",false);paused=prefs.getBoolean("paused",false);
            batchRequested=prefs.getBoolean("batch",false);
            db=SQLiteDatabase.openOrCreateDatabase(new File(directory,"metadata.db"),null);db.enableWriteAheadLogging();
            db.execSQL("CREATE TABLE IF NOT EXISTS games (id TEXT PRIMARY KEY, data TEXT NOT NULL)");
            db.execSQL("CREATE TABLE IF NOT EXISTS cards (id TEXT PRIMARY KEY,data TEXT NOT NULL)");
            db.execSQL("CREATE TABLE IF NOT EXISTS cards_v7 (id TEXT PRIMARY KEY,data TEXT NOT NULL)");
            db.execSQL("CREATE TABLE IF NOT EXISTS search_inputs_v8 (id TEXT PRIMARY KEY,signature TEXT NOT NULL)");
            db.execSQL("CREATE TRIGGER IF NOT EXISTS invalidate_search8_update AFTER UPDATE ON games BEGIN DELETE FROM search_inputs_v8 WHERE id=OLD.id OR id=NEW.id; END");
            db.execSQL("CREATE TRIGGER IF NOT EXISTS invalidate_search8_insert AFTER INSERT ON games BEGIN DELETE FROM search_inputs_v8 WHERE id=NEW.id; END");
            db.execSQL("CREATE TRIGGER IF NOT EXISTS invalidate_search8_delete AFTER DELETE ON games BEGIN DELETE FROM search_inputs_v8 WHERE id=OLD.id; END");
            db.execSQL("CREATE TRIGGER IF NOT EXISTS invalidate_card7_update AFTER UPDATE ON games BEGIN DELETE FROM cards_v7 WHERE id=NEW.id; END");
            db.execSQL("CREATE TRIGGER IF NOT EXISTS invalidate_card7_insert AFTER INSERT ON games BEGIN DELETE FROM cards_v7 WHERE id=NEW.id; END");
            db.execSQL("CREATE TRIGGER IF NOT EXISTS invalidate_card7_delete AFTER DELETE ON games BEGIN DELETE FROM cards_v7 WHERE id=OLD.id; END");
            db.execSQL("CREATE TABLE IF NOT EXISTS overrides (id TEXT PRIMARY KEY, data TEXT NOT NULL)");
            db.execSQL("CREATE TABLE IF NOT EXISTS jobs (id TEXT PRIMARY KEY, state TEXT NOT NULL, attempts INTEGER DEFAULT 0, retry_at INTEGER DEFAULT 0, error TEXT DEFAULT '', force_cover INTEGER DEFAULT 0)");
            db.execSQL("CREATE TABLE IF NOT EXISTS images (key TEXT PRIMARY KEY, path TEXT NOT NULL, url TEXT, bytes INTEGER, width INTEGER, height INTEGER, sha256 TEXT, downloaded INTEGER, fetched_at INTEGER)");
            db.execSQL("CREATE TABLE IF NOT EXISTS failures (url TEXT PRIMARY KEY, status TEXT, retry_at INTEGER)");
            db.execSQL("CREATE TABLE IF NOT EXISTS chinese_works (work_id TEXT PRIMARY KEY,data TEXT NOT NULL)");
            // Historical source tables are retained, but no text producer is started.
            try(Cursor c=db.rawQuery("SELECT url,retry_at FROM failures WHERE status IN ('RATE_LIMIT','AUTH_OR_FORBIDDEN')",null)){while(c.moveToNext())try{String host=new URL(c.getString(0)).getHost();hostBackoff.put(host,Math.max(hostBackoff.getOrDefault(host,0L),c.getLong(1)));}catch(Exception ignored){}}
            try(Cursor c=db.rawQuery("SELECT id,data FROM overrides",null)){while(c.moveToNext())cacheOverride(c.getString(0),new JSONObject(c.getString(1)));}
            long inputsStarted=SystemClock.uptimeMillis();boolean inputsComplete=loadSearchInputs();
            if(inputsComplete)publishSearchInputs();
            PerfTrace.event("SEARCH_INPUTS_CACHE complete="+inputsComplete+" rows="+searchSignatures.size()+" elapsedMs="+(SystemClock.uptimeMillis()-inputsStarted));
            // Two directory inventories replace hundreds of canonical-path/stat system calls at startup.
            Set<String> present=new HashSet<>();for(File dir:new File[]{downloadDir,localDir}){String[] names=dir.list();if(names!=null)for(String name:names)present.add(dir.getName()+"/"+name);}
            try(Cursor c=db.rawQuery("SELECT key,path,sha256 FROM images",null)){while(c.moveToNext()){String path=c.getString(1);if(IMAGE_PATH.matcher(path).matches()&&present.contains(path)){coverFiles.put(c.getString(0),new File(directory,path));coverVersions.put(c.getString(0),c.getString(2));}}}
            long stage=SystemClock.uptimeMillis();int full=0,compact=0;try(Cursor c=db.rawQuery("SELECT (SELECT COUNT(*) FROM games),(SELECT COUNT(*) FROM cards)",null)){c.moveToFirst();full=c.getInt(0);compact=c.getInt(1);}
            String selected=context.getSharedPreferences("rgds_shell_ui_v3",0).getString("selected_game_id","");
            Map<String,String> migratedCards=new HashMap<>(),newSearchInputs=new HashMap<>();
            try(Cursor c=db.rawQuery("SELECT games.id,COALESCE(cards_v7.data,games.data),cards_v7.id FROM games LEFT JOIN cards_v7 USING(id) ORDER BY CASE WHEN games.id=? THEN 0 ELSE 1 END",new String[]{selected})){while(c.moveToNext()){String id=c.getString(0);JSONObject data=new JSONObject(c.getString(1));if(c.isNull(2)||!data.has("candidateCount")){data=card7(c.isNull(2)?data:readFull(id).raw);migratedCards.put(id,data.toString());}infos.put(id,new Info(data));if(!searchSignatures.containsKey(id))newSearchInputs.put(id,searchSignature(data));if(c.getPosition()==0)main.post(()->{for(Listener l:listeners)l.metadataUpdated("");});}}
            if(!migratedCards.isEmpty()){db.beginTransaction();try{for(Map.Entry<String,String> row:migratedCards.entrySet())db.execSQL("INSERT OR REPLACE INTO cards_v7 VALUES(?,?)",new Object[]{row.getKey(),row.getValue()});db.setTransactionSuccessful();}finally{db.endTransaction();}}
            if(!newSearchInputs.isEmpty()){db.beginTransaction();try{for(Map.Entry<String,String> row:newSearchInputs.entrySet())db.execSQL("INSERT OR REPLACE INTO search_inputs_v8 VALUES(?,?)",new Object[]{row.getKey(),row.getValue()});db.setTransactionSuccessful();}finally{db.endTransaction();}searchSignatures.putAll(newSearchInputs);}
            publishSearchInputs();
            PerfTrace.event("STAGE metadataCards="+(SystemClock.uptimeMillis()-stage)+" compact="+(full==compact)+" uptime="+SystemClock.uptimeMillis());
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            db.execSQL("UPDATE jobs SET state='PENDING' WHERE state='RUNNING'");
            restoreTask();
            ready=true;taskState=UiStrings.msg("ui_1fd972460257");signal("");
            if(dirs!=null)catalog.bindGames(ShellStateRepository.get(context).allGamesCopy(),this);
            main.post(()->ShellStateRepository.get(context).metadataChanged());stats();
            if(dirs!=null){preparePictures(dirs);enqueueLibrary();}schedulePump(500);
        }catch(Exception e){taskState=UiStrings.msg("ui_8f0af14424a4");sourceError=e.getClass().getSimpleName();Log.e("TwinGridMetadata","RESTORE_FAILED",e);signal("");}
    }
    void directoryReady(DirectoryIndex d){dirs=d;worker.execute(()->{if(ready){catalog.bindGames(ShellStateRepository.get(context).allGamesCopy(),this);preparePictures(d);enqueueLibrary();schedulePump(250);}});}
    /** Called only after the foreground repository has atomically published its actual game list. */
    void gamesPublished(){gapSnapshot=null;worker.execute(()->{if(ready)catalog.bindGames(ShellStateRepository.get(context).allGamesCopy(),this);});}
    private void preparePictures(DirectoryIndex d){
        picturesByParent.clear();artByName.clear();baseCounts.clear();parentBaseCounts.clear();
        for(DirectoryIndex.Picture p:d.pictures){String name=p.name.toLowerCase(Locale.ROOT);int dot=name.lastIndexOf('.');if(dot>=0)name=name.substring(0,dot);
            picturesByParent.computeIfAbsent(p.parentId+"|"+name,k->new LinkedHashSet<>()).add(p.uri);
            DirectoryIndex.Folder f=d.folders.get(p.parentId);if(f!=null&&f.name.toLowerCase(Locale.ROOT).matches("_?(images|covers|artwork|boxart)"))artByName.computeIfAbsent(name,k->new LinkedHashSet<>()).add(p.uri);
        }
        for(GameEntry g:ShellStateRepository.get(context).allGamesCopy()){String name=g.basename().toLowerCase(Locale.ROOT);baseCounts.put(name,baseCounts.getOrDefault(name,0)+1);String parent=d.gameParents.get(g.gameId)+"|"+name;parentBaseCounts.put(parent,parentBaseCounts.getOrDefault(parent,0)+1);}
    }
    void scanSettled(){worker.execute(()->{if(ready)maybeFirstOrganization();});}
    private void maybeFirstOrganization(){
        // Missing preference permits only this one-time task, never enables daily AUTO.
        if(paused||(!autoEnabled&&prefs.contains("auto"))||!FirstRun.setupPending(context)||!ready||!taskHydrated||!catalog.ready()||RomLibrary.get(context).isScanning()||!task().terminal())return;
        String phase=RomLibrary.get(context).progress().phase;
        if(phase.equals("CANCELED")||phase.equals("PARTIAL")||phase.equals("FAILED"))return;
        ShellStateRepository.Snapshot state=ShellStateRepository.get(context).snapshot();
        if(!state.libraryReady||!state.storageAvailable||state.romTreeUri==null||dirs==null||!dirs.tree.equals(state.romTreeUri.toString()))return;
        List<GameEntry> games=ShellStateRepository.get(context).allGamesCopy();
        if(games.isEmpty()||games.size()!=dirs.gameParents.size())return;
        try(Cursor once=db.rawQuery("SELECT session FROM first_setup WHERE id=1",null)){if(once.moveToFirst()){FirstRun.setupStarted(context,once.getString(0));return;}}
        // Same executor and network policy; preserve an explicit pause and existing preferences.
        startCover(games,false,"FIRST_SETUP",UxStrings.s(0));
    }
    private void enqueueLibrary(){
        associateNewIdentities();maybeFirstOrganization();
        // Automatic policy only considers new/missing covers. It never resumes a user-paused session.
        if(!autoEnabled||paused||!task().terminal())return;
        List<GameEntry> missing=new ArrayList<>();for(GameEntry g:ShellStateRepository.get(context).allGamesCopy())if((info(g.gameId).status.equals("IDENTIFIED")||info(g.gameId).status.equals("WORK_IDENTIFIED"))&&(MetadataFields.needsCover(info(g.gameId).raw,!coverKey(g.gameId).isEmpty(),index==null?"":index.thumbnailVersion,coverIdentity(g.gameId))||(!localPicture(g,true).isEmpty()&&coverKey(g.gameId).isEmpty())))missing.add(g);
        if(!missing.isEmpty())startCover(missing,false,"AUTO",UiStrings.msg("ui_050f56927a6e"));
    }
    private void associateNewIdentities(){
        List<GameEntry> fresh=new ArrayList<>();for(GameEntry g:ShellStateRepository.get(context).allGamesCopy())if(!infos.containsKey(g.gameId)||staleDecision(info(g.gameId).raw))fresh.add(g);
        if(fresh.isEmpty()||!catalog.prepared())return;
        MetadataIndex local=catalog.coverIdentity();if(local==null)return;
        for(GameEntry g:fresh)try{resolveAndSave(g,local);}catch(Exception e){Log.w("TwinGridMetadata","NEW_IDENTITY_DEFERRED",e);}
    }
    private boolean staleDecision(JSONObject data){JSONObject d=data.optJSONObject("resolveDecision");return d==null||!ResolveDecision.VERSION.equals(d.optString("resolverVersion"))||!catalog.version().equals(d.optString("catalogVersion"));}
    private boolean resolveAndSave(GameEntry g,MetadataIndex identity)throws JSONException{
        JSONObject user=overrideCopy(g.gameId);ResolveDecision d=ResolveDecision.resolve(g,identity,user);
        if(user.has("externalId")||user.has("workId"))return false;
        JSONObject previous=readFull(g.gameId).raw,decision=d.json(catalog.version());
        if(previous.optJSONObject("resolveDecision")!=null&&previous.optJSONObject("resolveDecision").toString().equals(decision.toString()))return false;
        JSONObject o=new JSONObject(previous.toString());MetadataIndex.Match match=d.match();
        o.put("status",match.status).put("basis",match.basis).put("resolveDecision",decision).put("workId",d.work);
        o.remove("externalId");o.remove("release");
        if(d.release!=null)o.put("externalId",d.release.id()).put("title",d.release.name).put("region",d.release.region).put("release",d.release.json());
        JSONArray choices=new JSONArray();for(MetadataIndex.Release r:d.candidates)choices.put(r.json());o.put("candidates",choices);
        db.execSQL("CREATE TABLE IF NOT EXISTS identity_revisions_v77 (id TEXT,changed INTEGER,before_data TEXT,after_data TEXT,PRIMARY KEY(id,changed))");
        db.execSQL("INSERT OR REPLACE INTO identity_revisions_v77 VALUES(?,?,?,?)",new Object[]{g.gameId,System.currentTimeMillis(),previous.toString(),o.toString()});
        saveInfo(g.gameId,o);return true;
    }
    private boolean done(String id){try(Cursor c=db.rawQuery("SELECT state FROM jobs WHERE id=?",new String[]{id})){return c.moveToFirst()&&c.getString(0).equals("DONE");}}
    private void enqueue(String id,boolean force){
        queued.add(id);
        db.execSQL("INSERT OR IGNORE INTO jobs(id,state) VALUES(?,'PENDING')",new Object[]{id});
        db.execSQL("UPDATE jobs SET state='PENDING' WHERE id=? AND state='NOT_IN_CURRENT_INDEX'",new Object[]{id});
        if(force)db.execSQL("UPDATE jobs SET state='PENDING',attempts=0,retry_at=0,force_cover=1 WHERE id=?",new Object[]{id});
    }
    void requestOne(GameEntry g,boolean alternative){
        if(g==null||!tasksReady())return;
        if(!task().terminal()){
            if(nextRequests.size()<64||nextRequests.containsKey(g.gameId)){nextRequests.put(g.gameId,alternative);saveNext();operationNotice=task().userPaused?UiStrings.msg("ui_814d955e5f34"):UiStrings.msg("ui_a85e9197b799");}else operationNotice=UiStrings.msg("ui_94da0ac260ab");taskSignal(true);return;
        }
        startCover(Collections.singletonList(g),alternative,"SINGLE",UiStrings.msg("ui_d9c49b140d84"));
        operationNotice=paused?UiStrings.msg("ui_ddc279c63cc6"):UiStrings.msg("ui_e96c7a8eb517");taskSignal(true);
    }
    void prioritize(GameEntry g,List<GameEntry> page){} // A frozen plan is not reordered by browsing.
    void organizeAction(){
        if(!tasksReady())return;CoverTask.Snapshot s=task();
        if(s.terminal()){paused=false;prefs.edit().putBoolean("paused",false).apply();startCover(null,false,"ORGANIZE",UxStrings.s(0));}
        else taskAction(s);
    }
    void finishCoverWait(){worker.execute(()->{if(task().terminal()||!task().localDone)return;cancelConnection();for(CoverTask.Item item:coverTask.items())if(item.result==CoverTask.Result.PENDING||item.result==CoverTask.Result.RETRY||item.result==CoverTask.Result.ACTIVE){coverTask.result(item,CoverTask.Result.MISSING,UxStrings.s(65),0);persistItem(item);}coverTask.command(task().id,task().revision,CoverTask.Action.RESUME);paused=false;prefs.edit().putBoolean("paused",false).apply();coverTask.finish();persistTask();taskSignal(true);});}
    private boolean localOrganization()throws Exception{
        CoverTask.Snapshot s=task();if(!CoverTask.organization(s.trigger)||s.localDone)return false;
        if(s.localTotal>0&&s.localProcessed==s.localTotal&&catalog.ready()){coverTask.localProgress(s.localProcessed,s.localTotal,s.localChanges,true);persistTask();return false;}
        if(s.localTotal>0&&s.localProcessed==s.localTotal&&catalog.match().busy())return true;
        if(!catalog.prepared())return true;MetadataIndex local=catalog.coverIdentity();if(local==null)return true;
        List<GameEntry> games=ShellStateRepository.get(context).allGamesCopy();int count=0,changes=s.localChanges;
        coverTask.stage(CoverTask.State.PREPARING,UxStrings.s(89));
        for(GameEntry g:games){if(paused||external||asleep){coverTask.localProgress(count,games.size(),changes,false);persistTask();if(paused)confirmPause();return true;}if(resolveAndSave(g,local))changes++;count++;if(count%32==0){coverTask.localProgress(count,games.size(),changes,false);taskSignal(false);}}
        coverTask.localProgress(count,games.size(),changes,false);persistTask();catalog.bindGames(games,this);taskSignal(true);return true;
    }
    void fillMissing(){if(tasksReady())startCover(null,false,"BATCH",UiStrings.msg("ui_15eb787febf8"));}
    private void startCover(List<GameEntry> selected,boolean alternative,String trigger,String scope){
        if(!coverTask.start(trigger,scope,paused))return;
        if(trigger.equals("FIRST_SETUP"))SetupJourney.taskStarted(context,task().id);
        operationNotice="";indexRefresh=false;taskSignal(true);
        worker.execute(()->{
            try{
                List<GameEntry> games=selected==null?ShellStateRepository.get(context).allGamesCopy():selected;
                List<CoverTask.Item> plan=new ArrayList<>();for(GameEntry g:games)plan.add(new CoverTask.Item(g.gameId,alternative));
                coverTask.plan(plan);db.beginTransaction();try{
                    db.execSQL("DELETE FROM cover_plan");int n=0;for(CoverTask.Item item:plan)db.execSQL("INSERT INTO cover_plan VALUES(?,?,?,?,?,?,?)",new Object[]{item.id,n++,item.alternative?1:0,item.result.name(),"",0,0});
                    persistTask();
                    if(trigger.equals("FIRST_SETUP"))db.execSQL("INSERT OR IGNORE INTO first_setup VALUES(1,?)",new Object[]{task().id});
                    db.setTransactionSuccessful();
                }finally{db.endTransaction();}
                if(trigger.equals("FIRST_SETUP"))FirstRun.setupStarted(context,task().id);
                if(trigger.equals("QUEUED")){for(GameEntry g:games)nextRequests.remove(g.gameId);saveNext();}
                if(paused)confirmPause();else {coverTask.finish();persistTask();taskSignal(true);schedulePump(0);}
            }catch(Exception e){coverTask.stage(CoverTask.State.ERROR,UiStrings.msg("ui_a3b57d62c6d2"));taskSignal(true);Log.e("TwinGridMetadata","PLAN_FAILED",e);}
        });
    }
    private List<String> gameIds(){List<String> a=new ArrayList<>();for(GameEntry g:ShellStateRepository.get(context).allGamesCopy())a.add(g.gameId);return a;}
    void setAuto(boolean value){autoEnabled=value;if(prefs!=null)prefs.edit().putBoolean("auto",value).apply();operationNotice=value?UiStrings.msg("ui_ff53a8ec0a7b"):UiStrings.msg("ui_0e451fa30bf2");taskSignal(true);}
    void setMetered(boolean value){allowMetered=value;if(prefs!=null)prefs.edit().putBoolean("metered",value).apply();taskSignal(true);worker.execute(()->schedulePump(0));}
    void taskAction(CoverTask.Snapshot expected){
        if(!tasksReady())return;
        if(expected.action==CoverTask.Action.START){if(task().id.equals(expected.id)&&task().action==expected.action){
            if(nextRequests.isEmpty())fillMissing();else {List<GameEntry> next=new ArrayList<>();for(GameEntry g:ShellStateRepository.get(context).allGamesCopy())if(nextRequests.containsKey(g.gameId))next.add(g);
                startCover(next,true,"QUEUED",UiStrings.msg("ui_4634c948a23a"));}
        }}
        else if(expected.action==CoverTask.Action.RETRY){if(task().id.equals(expected.id)&&task().action==expected.action)retryFailed();}
        else if(coverTask.command(expected.id,expected.revision,expected.action)){
            operationNotice="";
            paused=expected.action==CoverTask.Action.PAUSE;prefs.edit().putBoolean("paused",paused).apply();
            taskSignal(true);if(paused)cancelConnection();
            worker.execute(()->{if(paused)confirmPause();else {persistTask();schedulePump(0);}});
        }
    }
    private void confirmPause(){
        for(CoverTask.Item item:coverTask.items())if(item.result==CoverTask.Result.ACTIVE){coverTask.result(item,CoverTask.Result.PENDING,UiStrings.msg("ui_0a88d1fa5eb5"),0);persistItem(item);}
        coverTask.stage(CoverTask.State.PAUSED,UiStrings.msg("ui_d090743fb72a"));persistTask();taskSignal(true);
    }
    void suspend(boolean value){external=value;coverLoader.suspend(value);if(value)cancelConnection();worker.execute(()->{if(taskHydrated&&!task().terminal()){if(paused)confirmPause();else if(value){coverTask.stage(CoverTask.State.GAME,UiStrings.msg("ui_5f2b35a1c936"));persistTask();taskSignal(true);}else schedulePump(0);}});}
    void sleeping(boolean value){asleep=value;if(value)cancelConnection();worker.execute(()->{if(taskHydrated&&!task().terminal()){if(paused)confirmPause();else if(value){coverTask.stage(CoverTask.State.SLEEP,UiStrings.msg("ui_c1c38f7edf8c"));persistTask();taskSignal(true);}else schedulePump(0);}});}
    private void cancelConnection(){sourceHttp.cancel();coverTransport.cancel();HttpURLConnection c=activeConnection;if(c!=null)connectionCloser.execute(c::disconnect);}
    void wake(){worker.execute(()->{if(ready){schedulePump(0);}});}
    private boolean usableNetwork(){
        Network n=network.getActiveNetwork();NetworkCapabilities c=n==null?null:network.getNetworkCapabilities(n);
        online=c!=null&&c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)&&c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        return online&&(allowMetered||!network.isActiveNetworkMetered());
    }
    private boolean allowed(){PowerManager p=context.getSystemService(PowerManager.class);return !paused&&!external&&!asleep&&p.isInteractive();}
    String chineseProgress(){return catalog.matchStatus();}
    String chineseSummary(){return catalog.summary()+"\n\n"+coverage();}
    void updateChinese(){if(ready)catalog.requestMatch(ShellStateRepository.get(context).allGamesCopy(),this);}
    void sampleChinese(){updateChinese();}
    void updateChinese(boolean sample,boolean onlineMode){updateChinese();}
    private void scheduleChinese(long delay){}
    static String workKey(GameEntry g,JSONObject info){JSONObject w=info.optJSONObject("work");String key=w==null?"":w.optString("workId");return !key.isEmpty()?key:!info.optString("externalId").isEmpty()?"release:"+info.optString("externalId"):"local:"+g.gameId;}
    private boolean sourceProbeAllowed(long token){return sourceHttp.isCurrent(token)&&allowed();}
    void probeSources(){long token=sourceHttp.cancellationToken();operationNotice=UiStrings.msg("ui_72339e9a480a");taskSignal(true);worker.execute(()->{
        try{if(!sourceProbeAllowed(token)||!usableNetwork())throw new IOException(UiStrings.msg("ui_dc4ed99f8997"));ensureIndex();String path=new java.util.TreeSet<String>(index.thumbnails).first();CoverTransport.Result response=coverTransport.fetch(path,index.thumbnailRevision,index.thumbnailBlobs.get(path),SystemClock.elapsedRealtime()+CoverTransport.ITEM_MS,()->sourceProbeAllowed(token),this::recordCoverTransfer);operationNotice=sourceProbeAllowed(token)?UiStrings.msg("ui_c11200000002")+response.body.length/1024+" KiB · "+response.route:UiStrings.msg("ui_258bcab7dd4f");}
        catch(Exception e){operationNotice=UiStrings.msg("ui_852e8f24aacd")+e.getMessage();}taskSignal(true);
    });}
    private String bangumiRequest(String path,JSONObject post)throws Exception{
        checkCancelled();if(!usableNetwork())throw new IOException("Offline");long now=System.currentTimeMillis(),backoff=hostBackoff.getOrDefault("api.bgm.tv",0L);if(now<backoff)throw new HttpFailure("SOURCE_BACKOFF",backoff);
        long delay=nextGlobalRequest-now;if(delay>0){Thread.sleep(Math.min(delay,2000));checkCancelled();}
        if(!path.startsWith("/v0/"))throw new IOException("Unsupported Bangumi endpoint");
        SourceHttp.Result r=sourceHttp.request(path,post);checkCancelled();nextGlobalRequest=System.currentTimeMillis()+1100;
        { if(PerfTrace.isEnabled()) Log.i("TwinGridChinese","HTTP status="+r.status+" method="+(post==null?"GET":"POST")+" path="+path+" bytes="+r.body.length+" sha256="+hashBytes(r.body)+" uid="+Process.myUid()+" thread="+Thread.currentThread().getName()+" route="+r.route); }
        if(r.status!=200){long retry=System.currentTimeMillis()+(r.status==429||r.status==403?3600000:300000);try{retry=Math.max(retry,System.currentTimeMillis()+Long.parseLong(r.retryAfter)*1000);}catch(Exception ignored){}
            if(r.status==429||r.status==403){hostBackoff.put("api.bgm.tv",retry);db.execSQL("INSERT OR REPLACE INTO failures VALUES(?,?,?)",new Object[]{BangumiSource.API,r.status==429?"RATE_LIMIT":"AUTH_OR_FORBIDDEN",retry});}throw new HttpFailure("BANGUMI_HTTP_"+r.status,retry);}
        if(!r.type.toLowerCase(Locale.ROOT).contains("json"))throw new IOException("Non-JSON source response");prefs.edit().remove("chinese_online_error").putLong("chinese_online_success",System.currentTimeMillis()).commit();return new String(r.body,StandardCharsets.UTF_8);
    }
    private void schedulePump(long delay){
        CoverTask.Snapshot s=task();if(!ready||!taskHydrated||pumpScheduled||s.terminal()||s.state==CoverTask.State.INTERRUPTED||!s.planned)return;
        pumpScheduled=true;worker.schedule(()->{pumpScheduled=false;pump();},delay,TimeUnit.MILLISECONDS);
    }
    private void pump(){
        if(!ready||task().terminal()||task().state==CoverTask.State.INTERRUPTED)return;
        if(paused){confirmPause();return;}
        if(!allowed()){coverTask.stage(asleep||!context.getSystemService(PowerManager.class).isInteractive()?CoverTask.State.SLEEP:CoverTask.State.GAME,UiStrings.msg("ui_9ef14ec17d7b"));persistTask();taskSignal(true);return;}
        if(!ShellStateRepository.get(context).snapshot().libraryReady){coverTask.stage(CoverTask.State.PREPARING,UiStrings.msg("ui_a07c1ef4e882"));taskSignal(false);schedulePump(400);return;}
        try{if(localOrganization()){schedulePump(400);return;}}catch(Exception e){coverTask.stage(CoverTask.State.ERROR,e.getClass().getSimpleName());persistTask();taskSignal(true);return;}
        Map<String,GameEntry> games=new HashMap<>();for(GameEntry g:ShellStateRepository.get(context).allGamesCopy())games.put(g.gameId,g);
        int cached=0;boolean networkWork=false,waitingNetwork=false;
        for(CoverTask.Item item:coverTask.items()){
            if(item.result!=CoverTask.Result.PENDING&&item.result!=CoverTask.Result.RETRY)continue;

            if(paused){confirmPause();return;}if(!allowed()){schedulePump(0);return;}
            GameEntry game=games.get(item.id);CoverTask.Result result=null;String reason="";
            if(!indexRefresh&&game==null){result=CoverTask.Result.REMOVED;reason=UiStrings.msg("ui_7d2a4db21646");}
            else if(!indexRefresh)try{result=process(game,item.alternative,false);reason=result==CoverTask.Result.ADDED?TaskPresentation.extra(12):result==CoverTask.Result.IDENTITY?TaskPresentation.extra(13):result==CoverTask.Result.MISSING?UiStrings.msg("ui_e44943297d50"):UiStrings.msg("ui_5f0ca2e5f6fb");}
            catch(Cancelled e){schedulePump(0);return;}
            catch(Exception e){completeItem(item,CoverTask.Result.FAILED,"LOCAL_READ_"+e.getClass().getSimpleName(),0);continue;}
            if(result!=null){completeItem(item,result,reason,0);signal(item.id);if(++cached>=20){stats();taskSignal(false);schedulePump(0);return;}continue;}
            // Keep walking the frozen plan: a network-only item must not block
            // local artwork further down the same library.
            if(!usableNetwork()){waitingNetwork=true;continue;}
            if(item.retryAt>System.currentTimeMillis())continue;
            networkWork=true;coverTask.stage(CoverTask.State.RUNNING,indexRefresh?UiStrings.msg("ui_a3c113878b89"):UiStrings.msg("ui_705e41fefba2"));coverTask.current(item,indexRefresh?UiStrings.msg("ui_cbd1bb4a9a88"):title(game),"libretro-thumbnails");persistItem(item);persistTask();taskSignal(true);
            try{
                if(indexRefresh){refreshCoverIndex();result=CoverTask.Result.ADDED;reason=UiStrings.msg("ui_b1e810c4ba34");}
                else{ensureIndex();checkCancelled();result=process(game,item.alternative,true);reason=result==CoverTask.Result.ADDED?UiStrings.msg("ui_a9b68aff6627"):result==CoverTask.Result.REUSED?UiStrings.msg("ui_f0a3a5a18118"):result==CoverTask.Result.IDENTITY?UiStrings.msg("ui_a275fe4346b8"):UiStrings.msg("ui_e44943297d50");}
                completeItem(item,result,reason,0);
            }catch(Exception e){
                if(e instanceof Cancelled||!allowed()||!usableNetwork()){completeItem(item,CoverTask.Result.PENDING,UiStrings.msg("ui_8041e9a113b4"),0);}
                else{item.attempts++;long retry=e instanceof HttpFailure?((HttpFailure)e).retry:System.currentTimeMillis()+300000;
                    String kind=e instanceof HttpFailure?((HttpFailure)e).kind:e instanceof java.net.SocketTimeoutException?UiStrings.msg("ui_ae5ead4d8907"):e.getClass().getSimpleName();
                    boolean absent=e instanceof ImageAbsent;
                    completeItem(item,absent?CoverTask.Result.MISSING:item.attempts>=3?CoverTask.Result.FAILED:CoverTask.Result.RETRY,absent?UiStrings.msg("ui_9b59256c370f"):UiStrings.msg("ui_135b6570effd")+kind,retry);
                }
            }
            if(game!=null)signal(game.gameId);stats();break;
        }
        if(paused){confirmPause();return;}
        coverTask.finish();persistTask();taskSignal(task().terminal());
        if(task().terminal()){operationNotice="";stats();return;}
        if(task().remaining==0&&task().retrying>0){coverTask.stage(CoverTask.State.RETRY_WAIT,UiStrings.msg("ui_d335fafb7106")+new java.text.SimpleDateFormat("MM-dd HH:mm",Locale.CHINA).format(new Date(task().retryAt)));persistTask();taskSignal(true);schedulePump(Math.min(30000,Math.max(1000,task().retryAt-System.currentTimeMillis())));}
        else if(waitingNetwork&&!networkWork){coverTask.stage(online?CoverTask.State.METERED:CoverTask.State.NETWORK,online?UiStrings.msg("ui_2a106930e2af"):UiStrings.msg("ui_6e1677a53291"));persistTask();stats();taskSignal(true);}
        else schedulePump(networkWork?40:0);
    }
    private void completeItem(CoverTask.Item item,CoverTask.Result result,String reason,long retry){
        coverTask.result(item,result,reason,retry);db.beginTransaction();try{
            persistItem(item);persistTask();
            if(!indexRefresh)db.execSQL("INSERT OR REPLACE INTO jobs(id,state,attempts,retry_at,error,force_cover) VALUES(?,?,?,?,?,?)",new Object[]{item.id,result==CoverTask.Result.PENDING?"PENDING":result==CoverTask.Result.RETRY||result==CoverTask.Result.FAILED?"FAILED":"DONE",item.attempts,retry,reason,item.alternative?1:0});
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    private void ensureIndex()throws Exception{
        if(index!=null&&index.thumbnailsReady&&!index.thumbnailRevision.isEmpty())return;
        MetadataIndex next=catalog.coverIdentity();if(next==null)throw new IOException("Offline catalog is preparing");
        JSONObject manifest;byte[] bytes;
        try(InputStream in=context.getResources().openRawResource(R.raw.cover_index_manifest)){manifest=new JSONObject(new String(read(in,16384),StandardCharsets.UTF_8));}
        try(InputStream in=context.getResources().openRawResource(R.raw.cover_index)){bytes=read(in,2*1024*1024);}
        if(bytes.length!=manifest.getInt("bytes")||!hashBytes(bytes).equals(manifest.getString("sha256")))throw new IOException("Bundled cover index integrity failure");
        next.loadThumbnails(new String(bytes,StandardCharsets.UTF_8));
        if(!next.thumbnailRevision.matches("[a-f0-9]{40}"))throw new IOException("Unpinned image index");
        index=next;identityVersion="Catalog:"+catalog.version();sourceError="";sourceRetryAt=0;
    }
    private void recordCoverTransfer(JSONObject event){
        // Public resource diagnostics only: no gameId, ROM URI or account state.
        try{File f=new File(directory,"cover_transport_v112.jsonl");if(f.length()>2*1024*1024){File prior=new File(directory,"cover_transport_v112.previous.jsonl");if(prior.exists())prior.delete();f.renameTo(prior);}
            try(FileOutputStream out=new FileOutputStream(f,true)){out.write((event.toString()+"\n").getBytes(StandardCharsets.UTF_8));}
        }catch(IOException ignored){}
    }
    private void loadSupplemental()throws Exception{
        taskState=UiStrings.msg("ui_eb894f64f3a2");signal("");OpenVgdbIndex extra=new OpenVgdbIndex();try{extra.load(indexDir,this::download,this::checkCancelled);supplemental=extra;}catch(Exception e){extra.close();throw e;}
        { if(PerfTrace.isEnabled()) Log.i("TwinGridMetadata","OPTIONAL_INDEX_READY sha1Keys="+extra.bySha1.size()+" thread="+Thread.currentThread().getName()); }
    }
    private String indexFile(String name,String url,int limit)throws Exception{
        File f=new File(indexDir,name);if(f.isFile()&&f.length()>0)return new String(read(f,limit),StandardCharsets.UTF_8);
        byte[] b=download(url,limit);String text=new String(b,StandardCharsets.UTF_8);
        if(name.endsWith(".dat")&&!text.startsWith("clrmamepro"))throw new IOException("Invalid DAT");
        if(name.endsWith(".json")&&!new JSONObject(text).has("tree"))throw new IOException("Invalid image index");
        atomic(f,b);return text;
    }
    private static final class ImageAbsent extends Exception {}
    private byte[] imageBytes(String url)throws Exception{try{return download(url,4*1024*1024);}catch(HttpFailure e){if(e.kind.equals("HTTP_404"))throw new ImageAbsent();throw e;}}
    private CoverTask.Result process(GameEntry g,boolean forceCover,boolean http)throws Exception{
        JSONObject user=overrides.get(g.gameId);boolean corrected=user!=null&&user.has("confirmedRelease");
        Info old=corrected?info(g.gameId):readFull(g.gameId);
        boolean hadCover=!coverKey(g.gameId).isEmpty();
        // A user's selected picture is independent of release confirmation.
        if(user!=null&&user.has("cover")&&!forceCover)return hadCover?CoverTask.Result.REUSED:CoverTask.Result.MISSING;
        if(hadCover&&!forceCover)return CoverTask.Result.REUSED;
        JSONObject patch=new JSONObject();String local=old.localCover,remote=old.remoteCover;boolean transferred=false;
        ResolveDecision decision=catalog.publishedDecision(g.gameId);
        boolean recognized=decision!=null&&!decision.work.isEmpty()&&!decision.needsInput();
        if(!coverFiles.containsKey(local)){
            String uri=localPicture(g,recognized);
            if(!uri.isEmpty())try{checkCancelled();String key="local:"+hash(uri);if(!coverFiles.containsKey(key))try(InputStream in=context.getContentResolver().openInputStream(Uri.parse(uri))){if(in==null)throw new IOException("Local image unavailable");storeImage(key,read(in,4*1024*1024),uri,false);}
                local=key;patch.put("localCover",key).put("localBasis",UiStrings.msg("ui_5d43f5eaf480")).put("localUri",uri);
            }catch(Cancelled e){throw e;}catch(Exception e){patch.put("localError","LOCAL_IMAGE_UNAVAILABLE_OR_INVALID");}
        }
        if(coverFiles.containsKey(local)&&!forceCover){
            patch.put("coverAssociation",new JSONObject().put("reason","AUTHORIZED_LOCAL_IMAGE").put("needsUserChoice",false));
            saveCover(g,old,patch,corrected,true);return hadCover?CoverTask.Result.REUSED:CoverTask.Result.ADDED;
        }
        if(user!=null&&user.optBoolean("identityCoverHold"))return CoverTask.Result.IDENTITY;
        if(!recognized)return CoverTask.Result.IDENTITY;
        // Only negative HTTP results may be reused here; a newly supplied local
        // image above always gets its chance, including when offline.
        if(!forceCover&&!MetadataFields.needsCover(old.raw,hadCover,index==null?"":index.thumbnailVersion,coverIdentity(g.gameId)))return hadCover?CoverTask.Result.REUSED:CoverTask.Result.MISSING;
        if(!http)return null;
        checkCancelled();String identity=coverIdentity(g.gameId);long userRevision=overrideCopy(g.gameId).optLong("updatedAt");
        JSONArray candidates=CoverAssociation.candidates(decision,index);
        JSONObject association=CoverAssociation.choose(decision,index).json().put("candidates",candidates)
            .put("gameId",g.gameId).put("workId",decision.work).put("queryIdentity",identity).put("indexVersion",index.thumbnailVersion)
            .put("checkedAt",System.currentTimeMillis()).put("session",task().id);
        patch.put("coverAssociation",association).put("coverIndexVersion",index.thumbnailVersion).put("coverQueryIdentity",identity);
        if(candidates.length()==0&&association.optString("reason").equals("ARTWORK_WORK_CROSSWALK_MISSING")){
            // Lack of a reviewed work-to-artwork relationship is not evidence that a public source has no image.
            association.put("needsUserChoice",false);patch.put("coverStatus","ARTWORK_WORK_CROSSWALK_MISSING");saveCover(g,old,patch,corrected,hadCover||coverFiles.containsKey(local));return CoverTask.Result.MISSING;
        }
        Set<String> digests=new LinkedHashSet<>();JSONObject first=null;Exception failure=null;
        long deadline=SystemClock.elapsedRealtime()+CoverTransport.ITEM_MS;
        for(int n=0;n<candidates.length();n++){
            JSONObject candidate=candidates.getJSONObject(n);String path=candidate.getString("path"),url=THUMB_BASE+Uri.encode(path,"/"),key="net:"+hash(url);
            // A legacy URL key is retained only when its actual bytes match this pinned index.
            // Mismatching old files/overrides remain intact and receive a new versioned key.
            String expectedBlob=index.thumbnailBlobs.getOrDefault(path,"");
            if(coverFiles.containsKey(key)&&!expectedBlob.isEmpty()){
                boolean same=false;try{same=expectedBlob.equals(CoverTransport.blob(read(coverFiles.get(key),CoverTransport.LIMIT)));}catch(Exception ignored){}
                if(!same)key="netv112:"+hash(index.thumbnailRevision+":"+path);
            }
            candidate.put("url",url).put("key",key).put("checkedAt",System.currentTimeMillis()).put("resourceVersion",index.thumbnailRevision);
            try{
                checkCancelled();
                // The field cache owns 404 expiry/identity. Transport failures must
                // not conceal explicit retries or a changed source index.
                db.delete("failures","url=? AND status='HTTP_404'",new String[]{url});
                if(!coverFiles.containsKey(key)){
                    CoverTransport.Result image=coverTransport.fetch(path,index.thumbnailRevision,index.thumbnailBlobs.getOrDefault(path,""),deadline,this::allowed,this::recordCoverTransfer);
                    checkCancelled();url=image.url;candidate.put("url",url).put("route",image.route).put("provider","libretro-thumbnails").put("resourceVersion",index.thumbnailRevision);
                    storeImage(key,image.body,url,true);coverTask.downloaded();persistTask();transferred=true;
                }
                String digest=coverVersion(key);candidate.put("status","VERIFIED").put("sha256",digest).put("failure","");
                digests.add(digest);if(first==null)first=candidate;
            }catch(ImageAbsent absent){candidate.put("status","ABSENT_404").put("failure","HTTP_404");}
            catch(Cancelled cancelled){throw cancelled;}
            catch(CoverTransport.Failure error){
                if(error.kind.equals("CANCELLED"))throw new Cancelled();
                if(error.kind.equals("ALL_ROUTES_404")){candidate.put("status","ABSENT_404").put("failure",error.kind);continue;}
                candidate.put("status","FAILED").put("failure",error.kind);failure=new HttpFailure(error.kind,error.retryAt);
                if(SystemClock.elapsedRealtime()>=deadline)break;
            }
            catch(Exception error){candidate.put("status","FAILED").put("failure",error instanceof HttpFailure?((HttpFailure)error).kind:error.getClass().getSimpleName());failure=error;if(SystemClock.elapsedRealtime()>=deadline)break;}
        }
        checkCancelled();if(!identity.equals(coverIdentity(g.gameId))||userRevision!=overrideCopy(g.gameId).optLong("updatedAt"))throw new Cancelled();
        boolean explicitSelection=forceCover;
        association.put("needsUserChoice",digests.size()>1||explicitSelection&&first!=null)
            .put("reason",failure!=null?"SOURCE_UNAVAILABLE":first==null?"INDEX_KEY_ABSENT":digests.size()>1?"MULTIPLE_ARTWORK_RESOURCES":explicitSelection?"EXPLICIT_ALTERNATIVES":"WORK_ARTWORK_RELEASE_UNCONFIRMED");
        boolean retainedCover=hadCover||coverFiles.containsKey(local);
        // Partial bytes/candidates remain available. Transient failure is still retryable,
        // not a 30-day manual identity result; explicit previews may use verified rows now.
        if(failure!=null){patch.put("coverStatus","SOURCE_UNAVAILABLE");saveCover(g,old,patch,corrected,retainedCover);throw failure;}
        if(first==null){patch.put("coverStatus","INDEX_KEY_ABSENT");saveCover(g,old,patch,corrected,retainedCover);return CoverTask.Result.MISSING;}
        if(association.optBoolean("needsUserChoice")){patch.put("coverStatus","NEEDS_CHOICE");saveCover(g,old,patch,corrected,retainedCover);return CoverTask.Result.IDENTITY;}
        remote=first.getString("key");association.put("path",first.getString("path")).put("sourceReleaseId",first.getString("sourceReleaseId")).put("region",first.optString("region"));
        patch.put("remoteCover",remote).put("coverProviderKey",first.getString("path")).put("coverProvider","libretro-thumbnails").put("coverUrl",first.getString("url")).put("coverRegion",first.optString("region")).put("coverStatus","READY").put("substituteRegion",false);
        boolean has=coverFiles.containsKey(local)||coverFiles.containsKey(remote);saveCover(g,old,patch,corrected,has);
        return has?(transferred||!hadCover?CoverTask.Result.ADDED:CoverTask.Result.REUSED):CoverTask.Result.MISSING;
    }
    String coverIdentity(String id){ResolveDecision d=catalog.publishedDecision(id);try{return d==null?MetadataFields.identity(info(id).raw):MetadataFields.identity(d.json(catalog.version()));}catch(JSONException e){throw new IllegalStateException(e);}}
    JSONObject coverCandidates(String id){JSONObject a=info(id).raw.optJSONObject("coverAssociation");try{return a==null?new JSONObject():new JSONObject(a.toString());}catch(JSONException e){return new JSONObject();}}
    void selectCover(String id,String key,String identity,String indexVersion,long expected,EditResult callback){
        try{JSONObject choice=new JSONObject().put("key",key).put("queryIdentity",identity).put("indexVersion",indexVersion).put("confirmedAt",System.currentTimeMillis());
            editFieldsChecked(id,new JSONObject().put("cover",key).put("coverChoice",choice),Collections.emptyList(),expected,()->{
                JSONObject a=coverCandidates(id);if(!identity.equals(coverIdentity(id))||!identity.equals(a.optString("queryIdentity"))||!indexVersion.equals(a.optString("indexVersion"))||index==null||!indexVersion.equals(index.thumbnailVersion)||coverFile(key)==null)return false;
                JSONArray rows=a.optJSONArray("candidates");if(rows!=null)for(int n=0;n<rows.length();n++){JSONObject row=rows.optJSONObject(n);if(key.equals(row.optString("key"))&&row.optString("status").equals("VERIFIED")&&row.optString("sha256").equals(coverVersion(key)))return true;}return false;
            },callback);
        }catch(JSONException error){callback.complete(false,error.getMessage());}
    }
    private void saveCover(GameEntry g,Info old,JSONObject patch,boolean corrected,boolean has)throws Exception{
        patch.put("coverFetchedAt",System.currentTimeMillis()).put("fields",new JSONObject().put("cover",MetadataFields.field(has?"READY":patch.optString("coverStatus","INDEX_KEY_ABSENT"),index==null?"local":"libretro-thumbnails:"+index.thumbnailVersion,MetadataFields.COVER_MAPPING_VERSION,patch.optString("coverStatus",has?"LOCAL":"INDEX_KEY_ABSENT"))));
        checkCancelled();JSONObject result=CoverPatch.apply(old.raw,patch);
        if(corrected){JSONObject o=overrideCopy(g.gameId);o.put("confirmedRelease",result);db.execSQL("INSERT OR REPLACE INTO overrides VALUES(?,?)",new Object[]{g.gameId,o.toString()});cacheOverride(g.gameId,o);detailsCache.remove(g.gameId);}else saveInfo(g.gameId,result);
    }
    private String localPicture(GameEntry g,boolean recognized){
        DirectoryIndex d=dirs;if(d==null)return "";String parent=d.gameParents.get(g.gameId);Set<String> named=new LinkedHashSet<>();
        String full=g.fileName.toLowerCase(Locale.ROOT),base=g.basename().toLowerCase(Locale.ROOT),code=g.gameCode.toLowerCase(Locale.ROOT);
        named.addAll(picturesByParent.getOrDefault(parent+"|"+full,Collections.emptySet()));named.addAll(picturesByParent.getOrDefault(parent+"|"+base,Collections.emptySet()));
        if(named.size()==1&&parentBaseCounts.getOrDefault(parent+"|"+base,0)==1)return named.iterator().next();if(named.size()>1)return "";
        named.addAll(artByName.getOrDefault(full,Collections.emptySet()));named.addAll(artByName.getOrDefault(base,Collections.emptySet()));
        if(named.size()==1&&baseCounts.getOrDefault(base,0)==1)return named.iterator().next();if(named.size()>1)return "";
        Set<String> coded=artByName.getOrDefault(code,Collections.emptySet());return recognized&&code.matches("[a-z0-9]{4}")&&coded.size()==1?coded.iterator().next():"";
    }
    private static JSONObject card(JSONObject o)throws JSONException {
        JSONObject c=new JSONObject();
        for(String field:new String[]{"coverProviderKey","coverIndexVersion","coverQueriedRelease","coverQueryIdentity","coverAssociation","status","title","genre","region","externalId","localCover","remoteCover","coverRegion","descriptionShort","descriptionLanguage","aliases","fieldSchema","genreProvider","resolveDecision","workId","basis"})if(o.has(field))c.put(field,o.get(field));
        JSONObject source=o.optJSONObject("fields"),fields=new JSONObject();if(source!=null)for(String name:new String[]{"identity","cover","genre","description"}){JSONObject f=source.optJSONObject(name);if(f==null)continue;JSONObject compact=new JSONObject();for(String k:new String[]{"state","sourceVersion","normalizationVersion","retryAt"})if(f.has(k))compact.put(k,f.get(k));fields.put(name,compact);}
        JSONObject zh=o.optJSONObject("chinese");if(zh!=null){JSONObject small=new JSONObject();for(String k:new String[]{"state","nameCn","overview","quality","subjectId","url","summaryLanguage","rawGenre","aliases","summarySHA256","translation","delivery","sourceVersion","sourceFetchedAt","platformVariantReview"})if(zh.has(k))small.put(k,zh.get(k));c.put("chinese",small);}if(o.has("chineseLastAttempt"))c.put("chineseLastAttempt",o.get("chineseLastAttempt"));
        c.put("candidateCount",o.optJSONArray("candidates")==null?o.optInt("candidateCount"):o.optJSONArray("candidates").length()).put("error",o.optString("error")).put("fields",fields).put("description",c.optString("descriptionShort")).put("compact",true);return c;
    }
    private boolean loadSearchInputs(){
        searchSignatures.clear();boolean complete=true;
        try(Cursor c=db.rawQuery("SELECT games.id,search_inputs_v8.signature FROM games LEFT JOIN search_inputs_v8 USING(id) ORDER BY games.id",null)){
            while(c.moveToNext()){String signature=c.isNull(1)?"":c.getString(1);if(!SHA256.matcher(signature).matches())complete=false;else searchSignatures.put(c.getString(0),signature);}
        }
        return complete;
    }
    private static String searchSignature(JSONObject data){
        StringBuilder b=new StringBuilder("search-fields-v8");for(String field:new String[]{"status","externalId","title","aliases"})appendKey(b,data.optString(field));return hash(b.toString());
    }
    private static void appendKey(StringBuilder b,String value){b.append('\n').append(value.length()).append(':').append(value);}
    private void publishSearchInputs(){
        StringBuilder b=new StringBuilder("search-inputs-v8");for(Map.Entry<String,String> row:new TreeMap<>(searchSignatures).entrySet()){appendKey(b,row.getKey());appendKey(b,row.getValue());}
        for(Map.Entry<String,JSONObject> row:new TreeMap<>(overrides).entrySet()){String title=row.getValue().optString("displayName")+"\n"+row.getValue().optString("displayName_en");if(!title.isEmpty()||row.getValue().has("workId")||row.getValue().has("externalId")){appendKey(b,row.getKey());appendKey(b,title);appendKey(b,row.getValue().optString("workId"));appendKey(b,row.getValue().optString("externalId"));}}
        String next=hash(b.toString());if(next.equals(searchInputFingerprint))return;searchInputFingerprint=next;
        main.post(()->ShellStateRepository.get(context).searchInputsChanged());
    }
    private static JSONObject card7(JSONObject o)throws JSONException {
        JSONObject c=new JSONObject();for(String field:new String[]{"coverProviderKey","coverIndexVersion","coverQueriedRelease","coverQueryIdentity","coverAssociation","status","title","genre","region","externalId","localCover","remoteCover","coverRegion","aliases","fieldSchema","genreProvider","fields"})if(o.has(field))c.put(field,o.get(field));
        c.put("candidateCount",o.optInt("candidateCount",o.optJSONArray("candidates")==null?0:o.optJSONArray("candidates").length())).put("error",o.optString("error")).put("categoriesV7",new JSONArray(automaticGenres(o))).put("compact",true);return c;
    }
    private final LruCache<String,Info> detailsCache=new LruCache<>(12);
    Info detailInfo(String id){JSONObject user=overrides.get(id);if(user!=null&&user.has("confirmedRelease"))return info(id);Info detail=detailsCache.get(id);return detail==null?info(id):detail;}
    void prepareDetails(String id,Runnable callback){detailReader.execute(()->{Info full=readFull(id);detailsCache.put(id,full);main.post(callback);});}
    private Info readFull(String id){try(Cursor c=db.rawQuery("SELECT data FROM games WHERE id=?",new String[]{id})){if(c.moveToFirst())return new Info(new JSONObject(c.getString(0)));}catch(Exception e){Log.w("TwinGridMetadata","DETAIL_READ_FAILED");}return info(id);}
    private void saveInfo(String id,JSONObject o){
        try{
            JSONObject compact=card(o);String signature=searchSignature(compact);db.beginTransaction();try{
                db.execSQL("INSERT OR REPLACE INTO games VALUES(?,?)",new Object[]{id,o.toString()});
                db.execSQL("INSERT OR REPLACE INTO cards VALUES(?,?)",new Object[]{id,compact.toString()});
                db.execSQL("INSERT OR REPLACE INTO cards_v7 VALUES(?,?)",new Object[]{id,card7(compact).toString()});
                db.execSQL("INSERT OR REPLACE INTO search_inputs_v8 VALUES(?,?)",new Object[]{id,signature});
                db.setTransactionSuccessful();
            }finally{db.endTransaction();}
            Info next=new Info(compact),previous=infos.put(id,next);detailsCache.remove(id);
            String priorSignature=searchSignatures.put(id,signature);boolean searchChanged=!signature.equals(priorSignature);if(searchChanged){catalog.invalidateBindings();publishSearchInputs();}
            // Source timestamps / field retry dates do not change local search or type lists.
            // Rebuilding the complete library for these writes caused measured GC and frame stalls.
            if(searchChanged||previous==null||!previous.status.equals(next.status)||!previous.title.equals(next.title)
                ||!previous.genre.equals(next.genre)||!previous.id.equals(next.id)
                ||!previous.raw.optString("aliases").equals(next.raw.optString("aliases"))||!previous.raw.optString("chinese").equals(next.raw.optString("chinese")))derivedChanged();
        }catch(JSONException e){throw new IllegalStateException(e);}
    }
    private void derivedChanged(){
        if(metadataPublishScheduled)return;metadataPublishScheduled=true;
        worker.schedule(()->{metadataPublishScheduled=false;catalog.bindGames(ShellStateRepository.get(context).allGamesCopy(),this);main.post(()->ShellStateRepository.get(context).metadataChanged());},250,TimeUnit.MILLISECONDS);
    }
    String description(String id){return description(id,LocaleSettings.content());}
    String description(String id,String locale){JSONObject o=overrides.get(id);String user=LocaleSettings.field("description",locale),confirmed=LocaleSettings.field("confirmedText",locale);if(o!=null&&o.has(user))return o.optString(user);if(o!=null&&o.has(confirmed))return o.optString(confirmed);OfflineCatalog.Text t=catalog.text(id,locale);return t==null?"":t.details.isEmpty()?t.summary:t.details;}
    String descriptionLanguage(String id){JSONObject o=overrides.get(id);String user=LocaleSettings.field("description"),confirmed=LocaleSettings.field("confirmedText");if(o!=null&&(o.has(user)||o.has(confirmed)))return GameDescription.language(description(id));OfflineCatalog.Text t=catalog.text(id);return t==null?LocaleSettings.content():t.locale;}
    void localeChanged(){catalog.languageChanged();effectiveRevision++;worker.execute(()->{stats();main.post(()->{ShellStateRepository.get(context).metadataChanged();signal("");taskSignal(true);});});}
    String descriptionShort(String id){JSONObject o=overrides.get(id);return o!=null&&(o.has(LocaleSettings.field("description"))||o.has(LocaleSettings.field("confirmedText")))?description(id):chineseText(id,false);}
    String overviewDisplay(String id,String source){JSONObject o=overrides.get(id);
        if(o!=null&&(o.has(LocaleSettings.field("description"))||o.has(LocaleSettings.field("confirmedText"))))return source;
        return OverviewCopy.get(context,LocaleSettings.content(),source);
    }
    private static final class Cancelled extends IOException {}
    private static final class HttpFailure extends IOException {final String kind;final long retry;HttpFailure(String k,long t){super(k);kind=k;retry=t;}}
    private void checkCancelled()throws Cancelled{if(!allowed())throw new Cancelled();}
    private byte[] download(String url,int limit)throws Exception{
        checkCancelled();long now=System.currentTimeMillis();
        String sourceHost=new URL(url).getHost();long backoff=hostBackoff.getOrDefault(sourceHost,0L);if(now<backoff)throw new HttpFailure("SOURCE_BACKOFF",backoff);
        String requestKey=sourceHost.equals("release-assets.githubusercontent.com")?OpenVgdbIndex.URL:url;
        try(Cursor c=db.rawQuery("SELECT status,retry_at FROM failures WHERE url=?",new String[]{requestKey})){if(c.moveToFirst()&&c.getLong(1)>now)throw new HttpFailure(c.getString(0),c.getLong(1));}
        long delay=nextGlobalRequest-now;if(delay>0){Thread.sleep(Math.min(delay,2000));checkCancelled();}
        URL u=new URL(url);if(!u.getProtocol().equals("https")||!(u.getHost().equals("raw.githubusercontent.com")||u.getHost().equals("api.github.com")||u.getHost().equals("github.com")||u.getHost().equals("release-assets.githubusercontent.com")))throw new IOException("Unsupported source");
        HttpURLConnection c=(HttpURLConnection)u.openConnection();activeConnection=c;c.setConnectTimeout(12000);c.setReadTimeout(18000);c.setInstanceFollowRedirects(false);
        boolean archive=u.getHost().equals("release-assets.githubusercontent.com");
        File partial=new File(indexDir,"openvgdb_v29.part");long offset=archive&&partial.isFile()?partial.length():0;
        if(offset>limit)throw new IOException("Partial archive size limit");
        if(archive&&offset>0)c.setRequestProperty("Range","bytes="+offset+"-");
        c.setRequestProperty("User-Agent","TwinGrid/0.9.0 (personal library metadata)");c.setRequestProperty("Accept",url.contains("api.github.com")?"application/vnd.github+json":"*/*");
        try{
            int status=c.getResponseCode();nextGlobalRequest=System.currentTimeMillis()+850;
            if((status==302||status==301||status==307)&&u.getHost().equals("github.com")&&url.equals(OpenVgdbIndex.URL)){
                String location=c.getHeaderField("Location");URL redirected=new URL(location);if(!redirected.getHost().equals("release-assets.githubusercontent.com")||!redirected.getProtocol().equals("https"))throw new IOException("Unexpected asset redirect");
                c.disconnect();return download(location,limit);
            }
            { if(PerfTrace.isEnabled()) Log.i("TwinGridMetadata","HTTP status="+status+" host="+u.getHost()+" path="+u.getPath()+" uid="+Process.myUid()+" thread="+Thread.currentThread().getName()); }
            if(status!=200&&!(archive&&status==206)){
                String kind=RemotePolicy.kind(status);
                long retry=RemotePolicy.retryAt(status,c.getHeaderField("Retry-After"),System.currentTimeMillis());
                if(status==429||status==403)hostBackoff.put(u.getHost(),retry);
                db.execSQL("INSERT OR REPLACE INTO failures(url,status,retry_at) VALUES(?,?,?)",new Object[]{requestKey,kind,retry});throw new HttpFailure(kind,retry);
            }
            if(c.getContentLengthLong()>limit)throw new IOException("Response too large");
            if(archive){
                if(status==206){String range=c.getHeaderField("Content-Range");if(range==null||!range.startsWith("bytes "+offset+"-"))throw new IOException("Archive range mismatch");}
                else offset=0; // A source may ignore Range; restart this private partial only.
                { if(PerfTrace.isEnabled()) Log.i("TwinGridMetadata","ARCHIVE_TRANSFER resumedAt="+offset); }
                long count=offset,shown=offset,deadline=SystemClock.uptimeMillis()+600000;
                try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(partial,offset>0)){
                    byte[] buffer=new byte[32768];int n;while((n=in.read(buffer))!=-1){checkCancelled();if(SystemClock.uptimeMillis()>deadline)throw new java.net.SocketTimeoutException("Archive total timeout");count+=n;if(count>limit)throw new IOException("Archive limit");out.write(buffer,0,n);
                        if(count-shown>=512*1024){shown=count;taskState=String.format(Locale.US,UiStrings.display(UiStrings.msg("ui_af7a608e371f")),count/1048576.0);signal("");{ if(PerfTrace.isEnabled()) Log.i("TwinGridMetadata","ARCHIVE_PROGRESS bytes="+count); }}
                    }out.getFD().sync();
                }
                checkCancelled();return read(partial,limit);
            }
            try(InputStream in=c.getInputStream()){
                ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[16384];int n;long deadline=SystemClock.uptimeMillis()+120000;
                while((n=in.read(buffer))!=-1){checkCancelled();if(SystemClock.uptimeMillis()>deadline)throw new java.net.SocketTimeoutException("Response total timeout");if(out.size()+n>limit)throw new IOException("Response limit");out.write(buffer,0,n);}return out.toByteArray();
            }
        }finally{activeConnection=null;c.disconnect();}
    }
    private BitmapFactory.Options validateImage(byte[] data)throws Exception{
        if(data.length<16)throw new IOException("Empty image");
        boolean magic=RemotePolicy.imageMagic(data);
        if(!magic)throw new IOException("Not PNG/JPEG/WebP");
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,bounds);
        if(bounds.outWidth<32||bounds.outHeight<32||bounds.outWidth>4096||bounds.outHeight>4096||(long)bounds.outWidth*bounds.outHeight>12000000)throw new IOException("Image dimensions rejected");
        BitmapFactory.Options sample=sample(bounds.outWidth,bounds.outHeight);Bitmap proof=BitmapFactory.decodeByteArray(data,0,data.length,sample);if(proof==null)throw new IOException("Image cannot decode");proof.recycle();
        return bounds;
    }
    private void storeImage(String key,byte[] data,String source,boolean downloaded)throws Exception{
        BitmapFactory.Options bounds=validateImage(data);
        String basename=(downloaded?hashBytes(data):hash(key))+".img";File file=new File(downloaded?downloadDir:localDir,basename);if(!file.isFile())atomic(file,data);
        db.execSQL("INSERT OR REPLACE INTO images(key,path,url,bytes,width,height,sha256,downloaded,fetched_at) VALUES(?,?,?,?,?,?,?,?,?)",new Object[]{key,(downloaded?"downloaded/":"local/")+basename,source,data.length,bounds.outWidth,bounds.outHeight,hashBytes(data),downloaded?1:0,System.currentTimeMillis()});coverFiles.put(key,file);coverVersions.put(key,hashBytes(data));
        { if(PerfTrace.isEnabled()) Log.i("TwinGridMetadata","IMAGE_SAVED key="+key+" downloaded="+downloaded+" bytes="+data.length+" width="+bounds.outWidth+" height="+bounds.outHeight); }
        evict();
    }
    private Set<String> protectedPaths(){Set<String> paths=new HashSet<>();for(JSONObject o:overrides.values()){File f=coverFiles.get(o.optString("cover"));if(f!=null)paths.add(f.getAbsolutePath());}return paths;}
    private boolean deleteDownload(String relative,Set<String> protectedPaths){
        File f=new File(directory,relative);if(!f.getParentFile().equals(downloadDir)||protectedPaths.contains(f.getAbsolutePath()))return false;
        if(f.exists()&&!f.delete())return false;
        try(Cursor c=db.rawQuery("SELECT key FROM images WHERE path=? AND downloaded=1",new String[]{relative})){while(c.moveToNext()){coverFiles.remove(c.getString(0));coverLoader.evictAll();}}
        db.delete("images","path=? AND downloaded=1",new String[]{relative});return true;
    }
    private void evict(){
        try(Cursor count=db.rawQuery("SELECT COALESCE(SUM(bytes),0) FROM (SELECT path,MAX(bytes) AS bytes FROM images WHERE downloaded=1 GROUP BY path)",null)){count.moveToFirst();long bytes=count.getLong(0);if(bytes<=MAX_CACHE)return;
            Set<String> protectedPaths=protectedPaths();
            try(Cursor c=db.rawQuery("SELECT path,MAX(bytes) FROM images WHERE downloaded=1 GROUP BY path ORDER BY MIN(fetched_at)",null)){while(bytes>MAX_CACHE&&c.moveToNext())if(deleteDownload(c.getString(0),protectedPaths))bytes-=c.getLong(1);}
        }
    }
    private static BitmapFactory.Options sample(int w,int h){BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=1;while(w/o.inSampleSize>576||h/o.inSampleSize>576)o.inSampleSize*=2;o.inPreferredConfig=Bitmap.Config.RGB_565;return o;}
    Bitmap cached(String key){return coverLoader.cached(key,288);}
    File coverFile(String key){return coverFiles.get(key);}
    String coverVersion(String key){return coverVersions.getOrDefault(key,key);}
    boolean decodingAllowed(){return !external&&context.getSystemService(PowerManager.class).isInteractive();}
    CoverLoader covers(){return coverLoader;}
    void decode(String key,java.util.function.Consumer<Bitmap> callback){coverLoader.request(callback,key,288,callback);}
    interface EditResult {void complete(boolean success,String message);}
    void edit(String id,JSONObject changes){editFields(id,changes,Collections.emptyList(),(ok,message)->{operationNotice=message;taskSignal(true);});}
    void editFields(String id,JSONObject changes,List<String> remove,EditResult callback){editFieldsChecked(id,changes,remove,-1,callback);}
    void editFieldsChecked(String id,JSONObject changes,List<String> remove,long expectedRevision,EditResult callback){editFieldsChecked(id,changes,remove,expectedRevision,()->true,callback);}
    void editFieldsChecked(String id,JSONObject changes,List<String> remove,long expectedRevision,java.util.function.BooleanSupplier fresh,EditResult callback){worker.execute(()->{try{
        if(!fresh.getAsBoolean())throw new IllegalStateException(UiStrings.msg("ui_55ef88781a74"));
        if(expectedRevision>=0&&overrideCopy(id).optLong("updatedAt")!=expectedRevision)throw new IllegalStateException(UiStrings.msg("ui_55ef88781a74"));
        if(!gameIds().contains(id))throw new IllegalArgumentException(UiStrings.msg("ui_e2655567e176"));
        JSONObject o=overrideCopy(id);String oldWork=o.optString("workId"),oldRelease=o.optString("externalId");
        for(String key:remove)o.remove(key);
        Iterator<String> keys=changes.keys();while(keys.hasNext()){String key=keys.next();o.put(key,changes.get(key));}
        boolean identity=!oldWork.equals(o.optString("workId"))||!oldRelease.equals(o.optString("externalId"));
        if(identity){if(!o.has("externalId"))o.remove("confirmedRelease");}
        if(changes.has("cover")){o.remove("coverReviewReason");if(!changes.has("coverChoice"))o.remove("coverChoice");}if(remove.contains("cover"))o.remove("coverChoice");
        if(changes.has("externalId")&&!changes.optString("externalId").isEmpty()){
            MetadataIndex identityIndex=catalog.coverIdentity();MetadataIndex.Release release=identityIndex==null?null:identityIndex.ids.get(changes.getString("externalId"));if(release==null)throw new IllegalArgumentException(UiStrings.msg("ui_6d605b9cf7b3"));
            if(!o.optString("workId").isEmpty()&&!o.optString("workId").equals(release.work))throw new IllegalArgumentException(UxStrings.s(10));
            if(!release.work.isEmpty())o.put("workId",release.work);
            boolean changedWork=!catalog.releaseWork(info(id).id).equals(release.work);
            o.put("identityCoverHold",false);o.remove("identityConflict");
            if(changedWork&&!coverKey(id).isEmpty())o.put("coverReviewReason","WORK_CHANGED_CHECK_EXISTING_COVER");
            o.put("confirmedRelease",new JSONObject().put("externalId",release.id()).put("status","IDENTIFIED").put("title",release.name).put("region",release.region).put("release",release.json()).put("basis",UiStrings.msg("ui_418cbdedc5c6")).put("provider",UiStrings.msg("ui_d3e0d04ab38b")));
        }
        if(remove.contains("workId")&&!o.has("externalId")){o.remove("identityCoverHold");o.remove("identityConflict");}
        o.put("updatedAt",System.currentTimeMillis());String encoded=o.toString();
        db.beginTransaction();try{db.execSQL("INSERT OR REPLACE INTO overrides VALUES(?,?)",new Object[]{id,encoded});try(Cursor c=db.rawQuery("SELECT data FROM overrides WHERE id=?",new String[]{id})){if(!c.moveToFirst()||!encoded.equals(c.getString(0)))throw new IllegalStateException(UiStrings.msg("ui_2da6e861ba67"));}db.setTransactionSuccessful();}finally{db.endTransaction();}
        try(Cursor readback=db.rawQuery("SELECT data FROM overrides WHERE id=?",new String[]{id})){if(!readback.moveToFirst()||!encoded.equals(readback.getString(0)))throw new IllegalStateException("Committed readback mismatch");}
        cacheOverride(id,o);detailsCache.remove(id);metadataSaveActions++;effectiveRevision++;publishSearchInputs();
        if(identity){pendingEdits.put(id,callback);catalog.invalidateGame(id);catalog.bindGames(ShellStateRepository.get(context).allGamesCopy(),this);}
        else {stats();signal(id);main.post(()->{ShellStateRepository.get(context).metadataChanged();callback.complete(true,UxStrings.s(9));taskSignal(true);});}
    }catch(Exception e){Log.e("TwinGridMetadata","EDIT_FAILED",e);String message=e instanceof android.database.sqlite.SQLiteException?UiStrings.msg("ui_0e272086d7b0"):(UiStrings.msg("ui_05bd8e6f3e80")+e.getMessage());main.post(()->callback.complete(false,message));}});}
    void reset(String id){JSONObject o=overrideCopy(id);List<String> keys=new ArrayList<>();Iterator<String> it=o.keys();while(it.hasNext())keys.add(it.next());editFields(id,new JSONObject(),keys,(ok,message)->{operationNotice=message;taskSignal(true);});}
    void importCover(String id,Uri uri){worker.execute(()->{try(InputStream in=context.getContentResolver().openInputStream(uri)){
        if(in==null)throw new IOException("No image stream");byte[] b=read(in,4*1024*1024);String key="user:"+hashBytes(b);storeImage(key,b,UiStrings.msg("ui_9ab52bc4933e"),false);edit(id,new JSONObject().put("cover",key));
    }catch(Exception e){taskState=UiStrings.msg("ui_be7b9ed1920e")+e.getClass().getSimpleName();signal(id);}});}
    void retryFailed(){
        if(!tasksReady()||!task().terminal())return;
        List<CoverTask.Item> failedItems=new ArrayList<>();for(CoverTask.Item i:coverTask.items())if(i.result==CoverTask.Result.FAILED)failedItems.add(i);
        if(failedItems.isEmpty()){operationNotice=UiStrings.msg("ui_f651c9b6ba91");taskSignal(true);return;}
        if(!coverTask.start("RETRY",UiStrings.msg("ui_95e5ac19005c"),paused))return;
        taskSignal(true);worker.execute(()->{List<CoverTask.Item> plan=new ArrayList<>();for(CoverTask.Item old:failedItems){CoverTask.Item i=new CoverTask.Item(old.id,old.alternative);i.retryAt=old.retryAt;i.result=i.retryAt>System.currentTimeMillis()?CoverTask.Result.RETRY:CoverTask.Result.PENDING;plan.add(i);}
            coverTask.plan(plan);db.beginTransaction();try{db.execSQL("DELETE FROM cover_plan");int n=0;for(CoverTask.Item i:plan)db.execSQL("INSERT INTO cover_plan VALUES(?,?,?,?,?,?,?)",new Object[]{i.id,n++,i.alternative?1:0,i.result.name(),UiStrings.msg("ui_6b0f7d245946"),i.retryAt,0});persistTask();db.setTransactionSuccessful();}finally{db.endTransaction();}schedulePump(0);
        });
    }
    void updateSources(){
        if(!tasksReady()||!coverTask.start("INDEX",UiStrings.msg("ui_961efdb8130c"),paused)){operationNotice=UiStrings.msg("ui_78e520865295");taskSignal(true);return;}
        indexRefresh=true;taskSignal(true);worker.execute(()->{CoverTask.Item i=new CoverTask.Item("@cover-index",false);coverTask.plan(Collections.singletonList(i));db.execSQL("DELETE FROM cover_plan");db.execSQL("INSERT INTO cover_plan VALUES(?,0,0,'PENDING','',0,0)",new Object[]{i.id});persistTask();schedulePump(0);});
    }
    private void refreshCoverIndex()throws Exception{
        // Image mapping updates ship as a verified small text resource with the APK.
        // An explicit retry re-evaluates the bundled index without fetching GitHub tree.
        ensureIndex();checkCancelled();coverTask.indexUpdated(index.thumbnailVersion,index.thumbnails.size());sourceRetryAt=0;
    }
    void clearDownloads(){worker.execute(()->{
        Set<String> protectedPaths=protectedPaths();
        try(Cursor c=db.rawQuery("SELECT DISTINCT path FROM images WHERE downloaded=1",null)){while(c.moveToNext())deleteDownload(c.getString(0),protectedPaths);}
        for(Map.Entry<String,Info> e:new ArrayList<>(infos.entrySet()))if(!e.getValue().remoteCover.isEmpty()&&!coverFiles.containsKey(e.getValue().remoteCover))try{JSONObject o=new JSONObject(readFull(e.getKey()).raw.toString());o.remove("remoteCover");saveInfo(e.getKey(),o);}catch(Exception ignored){}
        stats();taskState=UiStrings.msg("ui_cf0eaaff9bb1");signal("");
    });}
    private void stats(){
        effectiveRevision++;statsAt=SystemClock.uptimeMillis();queued.clear();covered=0;
        Map<String,Integer> typeCounts=new HashMap<>();for(GameEntry g:ShellStateRepository.get(context).allGamesCopy()){List<String> types=genres(g.gameId);if(types.isEmpty())typeCounts.put("unclassified",typeCounts.getOrDefault("unclassified",0)+1);for(String type:types)typeCounts.put(type,typeCounts.getOrDefault(type,0)+1);}genreCounts=Collections.unmodifiableMap(typeCounts);
        List<GameEntry> games=ShellStateRepository.get(context).allGamesCopy();total=games.size();identified=typed=candidates=localLinked=remoteLinked=unclassified=described=0;nativeChinese=chineseGameplay=chineseStory=chineseRelease=chineseMatched=chineseFault=0;
        for(GameEntry g:games){if(!coverKey(g.gameId).isEmpty())covered++;Info i=info(g.gameId);JSONObject z=i.raw.optJSONObject("chinese");if(z!=null){if(z.optInt("subjectId")>0)chineseMatched++;if(z.optString("state").equals("NATIVE_ZH")){nativeChinese++;String q=z.optString("quality");if(!z.optBoolean("platformVariantReview")&&!z.optString("overview").isEmpty()&&(q.equals("GAMEPLAY")||q.equals("MIXED")))chineseGameplay++;else if(q.equals("STORY_ONLY"))chineseStory++;else if(q.equals("RELEASE_ONLY"))chineseRelease++;}}JSONObject failure=i.raw.optJSONObject("chineseLastAttempt");if(failure!=null&&failure.optString("state").equals("SOURCE_FAILURE"))chineseFault++;if(i.status.equals("IDENTIFIED"))identified++;if(i.status.equals("CANDIDATE"))candidates++;if(!i.genre.isEmpty())typed++;if(!i.descriptionShort.isEmpty())described++;if(genres(g.gameId).isEmpty())unclassified++;if(!i.localCover.isEmpty()&&coverFiles.containsKey(i.localCover))localLinked++;if(!i.remoteCover.isEmpty()&&coverFiles.containsKey(i.remoteCover))remoteLinked++;}
        // Source-text writes do not change thumbnails. Avoid hundreds of file stats per work.
        if(SystemClock.uptimeMillis()>=thumbnailScanAt){File[] thumbs=new File(directory,"thumbs").listFiles((d,n)->n.matches("[a-f0-9]{64}\\.png"));long thumbnailTotal=0;if(thumbs!=null)for(File thumb:thumbs)thumbnailTotal+=thumb.length();thumbnailBytes=thumbnailTotal;thumbnailScanAt=SystemClock.uptimeMillis()+10000;}
        Set<String> ids=new HashSet<>(gameIds());pending=completed=failed=0;
        try(Cursor c=db.rawQuery("SELECT id,state FROM jobs",null)){while(c.moveToNext())if(ids.contains(c.getString(0))){if(c.getString(1).equals("DONE"))completed++;else if(!c.getString(1).equals("NOT_IN_CURRENT_INDEX")){pending++;queued.add(c.getString(0));}if(c.getString(1).equals("FAILED"))failed++;}}
        try(Cursor c=db.rawQuery("SELECT COUNT(*),COALESCE(SUM(bytes),0) FROM (SELECT sha256,MAX(bytes) AS bytes FROM images WHERE downloaded=1 GROUP BY sha256)",null)){c.moveToFirst();imageCount=c.getInt(0);imageBytes=c.getLong(1);}
        try(Cursor c=db.rawQuery("SELECT COALESCE(SUM(bytes),0) FROM (SELECT path,MAX(bytes) AS bytes FROM images WHERE downloaded=0 GROUP BY path)",null)){c.moveToFirst();localBytes=c.getLong(0);}
        Map<String,GameEntry> map=new HashMap<>();for(GameEntry g:games)map.put(g.gameId,g);
        StringBuilder detail=new StringBuilder(UiStrings.msg("ui_15e608217008"));
        try(Cursor c=db.rawQuery("SELECT id,error,retry_at FROM jobs WHERE state='FAILED' LIMIT 100",null)){while(c.moveToNext()){
            GameEntry g=map.get(c.getString(0));if(g!=null)detail.append(title(g)).append(" · ").append(c.getString(1)).append(UiStrings.msg("ui_8321455db9bb")).append(new Date(c.getLong(2))).append('\n');
        }}jobDetails=detail.toString();publishGaps(games);
    }
    String reviewReason(String id){return effective(id).reason();}
    List<GameEntry> reviewItems(){List<GameEntry> a=new ArrayList<>();for(GameEntry g:ShellStateRepository.get(context).allGamesCopy())if(!reviewReason(g.gameId).isEmpty())a.add(g);return a;}
    List<MetadataIndex.Release> search(String query){
        MetadataIndex ix=index;if(ix==null)return Collections.emptyList();String q=MetadataIndex.normalize(query);if(q.isEmpty())return Collections.emptyList();
        List<MetadataIndex.Release> exact=ix.serials.get(query.trim().toUpperCase(Locale.ROOT)),result=new ArrayList<>();
        if(exact!=null)result.addAll(exact);
        else for(Map.Entry<String,List<MetadataIndex.Release>> e:ix.names.entrySet())if(e.getKey().contains(q)){for(MetadataIndex.Release r:e.getValue()){result.add(r);if(result.size()==60)break;}if(result.size()==60)break;}
        result.sort(Comparator.comparing(r->r.name));return result;
    }
    void prepareSearch(java.util.function.Consumer<Boolean> callback){
        if(index!=null){callback.accept(true);return;}
        worker.execute(()->{boolean success=false;try{
            // Corrections must remain usable after a cold offline restart. Read cached files only here.
            MetadataIndex cached=catalog.coverIdentity();if(cached==null)throw new IOException("Catalog preparing");index=cached;success=true;
        }catch(Exception e){Log.w("TwinGridMetadata","CACHED_SEARCH_INDEX_UNAVAILABLE");}final boolean ready=success;main.post(()->callback.accept(ready));});
    }
    private final Set<String> pendingContent=ConcurrentHashMap.newKeySet();
    private boolean notifyScheduled;
    private void signal(String id){
        pendingContent.add(id);main.post(()->{if(notifyScheduled)return;notifyScheduled=true;main.postDelayed(()->{
            notifyScheduled=false;List<String> changed=new ArrayList<>(pendingContent);pendingContent.removeAll(changed);
            if(changed.contains("")){for(Listener l:listeners)l.metadataUpdated("");}
            else for(String game:changed)for(Listener l:listeners)l.metadataUpdated(game);
        },350);});
    }
    static String sources(){return UiStrings.msg("ui_c11200000003");}
    private static byte[] read(File f,int limit)throws IOException{try(InputStream in=new FileInputStream(f)){return read(in,limit);}}
    private static byte[] read(InputStream in,int limit)throws IOException{ByteArrayOutputStream o=new ByteArrayOutputStream();byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1){if(o.size()+n>limit)throw new IOException("Size limit");o.write(b,0,n);}return o.toByteArray();}
    private static void atomic(File f,byte[] b)throws IOException{AtomicFile a=new AtomicFile(f);FileOutputStream o=null;try{o=a.startWrite();o.write(b);a.finishWrite(o);}catch(IOException e){if(o!=null)a.failWrite(o);throw e;}}
    static String hash(String s){return hashBytes(s.getBytes(StandardCharsets.UTF_8));}
    static String hashBytes(byte[] b){try{byte[] hash=MessageDigest.getInstance("SHA-256").digest(b);char[] out=new char[hash.length*2],hex="0123456789abcdef".toCharArray();for(int i=0;i<hash.length;i++){out[i*2]=hex[(hash[i]&255)>>>4];out[i*2+1]=hex[hash[i]&15];}return new String(out);}catch(Exception e){throw new IllegalStateException(e);}}
}
