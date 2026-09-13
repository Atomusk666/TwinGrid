package com.rgds.ultimate.shell;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AtomicFile;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/**
 * Shared, immutable render snapshots. Navigation uses a cached list and O(1) selection.
 * JSON, preference construction and disk writes run outside the navigation lock.
 * The V1 preference file remains an untouched migration / rollback source.
 */
public final class ShellStateRepository {
    private static final String TAG="RGDSShellState";
    private static volatile ShellStateRepository instance;
    public enum Category {
        RECENT(UiStrings.msg("ui_997a5e6e513f")), NDS(UiStrings.msg("ui_5c55a67935af")), TYPES(UiStrings.msg("ui_ba40014ff496")), FOLDERS(UiStrings.msg("ui_7c7802d8adae")), FAVORITES(UiStrings.msg("ui_60a53514eb92")), SETTINGS(UiStrings.msg("ui_df3d58c7d84b"));
        public final String label;
        Category(String s){label=s;}
        static Category fromName(String s){try{return valueOf(s);}catch(Exception e){return NDS;}}
    }
    public enum Page { HOME, LIBRARY, DETAILS, SETTINGS, FILTER, GENRES, TASKS, PREPARING }
    public interface Listener { void onShellStateChanged(Snapshot s); }
    public static final class UsageRecord {
        public final String gameId,uri,title;
        // Legacy confirmed-play fields are preserved; never incremented by V0.3.
        public final long lastPlayedAt,lastSelectedAt,lastLaunchAt;
        public final int playCount,launchRequestCount;
        UsageRecord(String id,String u,String t,long played,int count,long selected,long launched,int requests){
            gameId=id;uri=u;title=t;lastPlayedAt=played;playCount=count;lastSelectedAt=selected;
            lastLaunchAt=launched;launchRequestCount=requests;
        }
    }
    public static final class RomMapping {
        public final String gameId,uri,title;
        RomMapping(String id,String u,String t){gameId=id;uri=u;title=t;}
    }
    public static final class Snapshot {
        public final Page page;
        public final Category category;
        public final List<GameEntry> games;
        public final List<DirectoryIndex.Item> items;
        public final DirectoryIndex.Folder selectedFolder;
        public final String folderId,folderPath,genreFilter,searchQuery,searchScopeLabel;
        public final boolean searchActive,searchScoped,searchBusy,typesOverview,searchEditing,searchComposing;public final int pageSize;
        public final int detailTab,settingsGroup,genreIndex,searchScopeMode,taskIndex;
        public final String queryDescriptor,actionQuery;
        public final GameEntry selectedGame;
        public final UsageRecord selectedUsage;
        public final Set<String> favoriteIds;
        public final int selectedIndex,scrollPosition,detailPage,sortMode,totalGameCount,favoriteCount,recentCount;
        public final int currentFocusDisplay,homeIndex,settingsIndex,filterIndex,jumpPage,detailOffset;
        public final String scanState,drasticState,topDisplayState;
        public final Uri romTreeUri,backupTreeUri;
        public final boolean stateReady,libraryReady,storageAvailable;
        public final DirectoryHealth.State romAccess,saveAccess;public final long romDirectoryEpoch,saveDirectoryEpoch;
        private final boolean settingsDetail;
        public final long revision,listRevision,inputSequence,inputTime,callbackTime,stateTime;
        Snapshot(ShellStateRepository r){
            taskIndex=r.taskIndex;page=r.page;category=r.category;typesOverview=r.typesOverview;games=r.visible;selectedIndex=r.selectedIndex;
            searchEditing=r.searchEditing;searchComposing=r.searchComposing;
            items=r.items;selectedFolder=r.selectedFolderLocked();folderId=r.folderId;
            folderPath=r.directoryIndex==null?UiStrings.msg("ui_bde0a2267d13"):r.directoryIndex.path(r.folderId);
            genreFilter=r.genreFilter;searchActive=r.searchActive;searchScoped=r.searchScoped;searchBusy=r.searchBusy;searchQuery=r.searchQuery;searchScopeLabel=r.searchScopeLabel;pageSize=r.pageSize;detailTab=r.detailTab;settingsGroup=r.settingsGroup;
            genreIndex=r.genreIndex;searchScopeMode=r.searchScopeMode;queryDescriptor=r.queryJsonLocked();actionQuery=r.actionQueryLocked();
            scrollPosition=r.scrollPosition;detailPage=page==Page.DETAILS?1:0;sortMode=r.sortMode;
            selectedGame=r.selectedGameLocked();selectedUsage=selectedGame==null?null:r.usageRecords.get(selectedGame.gameId);
            favoriteIds=r.favoriteIds;totalGameCount=r.allGames.size();favoriteCount=favoriteIds.size();
            recentCount=r.recentCount;scanState=r.scanState;drasticState=r.drasticState;topDisplayState=r.topDisplayState;
            currentFocusDisplay=r.currentFocusDisplay;homeIndex=r.homeIndex;settingsIndex=r.settingsIndex;
            filterIndex=r.filterIndex;jumpPage=r.jumpPage;detailOffset=r.detailOffset;
            romTreeUri=r.romTreeUri;backupTreeUri=r.backupTreeUri;
            stateReady=r.stateReady;libraryReady=r.libraryReady;storageAvailable=r.storageAvailable;romAccess=r.romAccess;saveAccess=r.saveAccess;romDirectoryEpoch=r.romDirectoryEpoch;saveDirectoryEpoch=r.saveDirectoryEpoch;
            settingsDetail=r.returnPage==Page.SETTINGS;
            revision=r.revision;listRevision=r.listRevision;inputSequence=r.inputSequence;
            inputTime=r.inputTime;callbackTime=r.callbackTime;stateTime=SystemClock.uptimeMillis();
        }
        public boolean isFavorite(GameEntry g){return g!=null&&favoriteIds.contains(g.gameId);}
        public boolean returningToSettings(){return settingsDetail;}
        public UsageRecord usageFor(GameEntry g){return g==selectedGame?selectedUsage:null;}
        public boolean canBrowseGame(){return allowsGame(true);}
        public boolean canActOnGame(){return allowsGame(storageAvailable);}
        private boolean allowsGame(boolean accessible){
            boolean member=page==Page.HOME || selectedIndex>=0 && selectedIndex<items.size()
                    && items.get(selectedIndex).game==selectedGame;
            return GameActionPolicy.allows(page.name(),homeIndex,stateReady&&libraryReady,accessible,
                    page==Page.LIBRARY&&typesOverview,page==Page.LIBRARY&&selectedFolder!=null,
                    selectedGame!=null,member,searchEditing,searchComposing,searchActive&&searchBusy,
                    ShellDialogBuilder.hasOpenDialog());
        }
        String actionContext(){if(canActOnGame())return page+"|"+category+"|"+homeIndex+"|"+currentFocusDisplay+"|"+romTreeUri+"|"+actionQuery+"|"+selectedGame.gameId+"|"+selectedGame.uriString();return page+"|"+category+"|"+homeIndex+"|"+settingsGroup+"|"+settingsIndex+"|"+filterIndex
                +"|"+folderId+"|"+genreFilter+"|"+genreIndex+"|"+typesOverview+"|"+listRevision+"|"+currentFocusDisplay
                +"|"+searchEditing+"|"+searchComposing+"|"+searchBusy+"|"+(selectedGame==null?"":selectedGame.gameId)
                +"|"+selectedIndex+"|"+(selectedFolder==null?"":selectedFolder.id)+"|"+jumpPage+"|"+detailTab;}
    }
    private final Context context;
    private SharedPreferences uiPrefs,userPrefs;
    private final AtomicFile mappingFile;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ScheduledExecutorService writer=Executors.newSingleThreadScheduledExecutor(r -> new Thread(()->{
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);r.run();
    },"shell-state-writer"));
    private ScheduledFuture<?> pendingWrite;
    private final Set<Listener> listeners=new CopyOnWriteArraySet<>();
    private final List<Runnable> readyCallbacks=new ArrayList<>();
    private List<GameEntry> allGames=Collections.emptyList(),visible=Collections.emptyList();
    private List<GameEntry> sortedTitles=Collections.emptyList(),sortedModified=Collections.emptyList();
    private List<DirectoryIndex.Item> items=Collections.emptyList();
    private DirectoryIndex directoryIndex;
    private String folderId="",genreFilter="all";
    private int detailTab,settingsGroup=-1,genreIndex,taskIndex;
    private final int[] settingPositions=new int[5];
    private Page taskReturn=Page.HOME;
    private final Map<String,String> bookmarks=new HashMap<>();
    private Map<String,GameEntry> byId=Collections.emptyMap();
    private final Map<String,List<GameEntry>> listCache=new HashMap<>();
    private Set<String> favoriteIds=Collections.emptySet();
    private final Map<String,UsageRecord> usageRecords=new LinkedHashMap<>();
    private final Map<String,RomMapping> romMappings=new LinkedHashMap<>();
    private Category category=Category.NDS;
    private Page page=Page.HOME,returnPage=Page.HOME,settingsReturn=Page.HOME;
    private String selectedGameId="",lastLaunchId="";
    private int selectedIndex,scrollPosition,sortMode,currentFocusDisplay,homeIndex,settingsIndex,filterIndex,jumpPage=1,detailOffset,recentCount;
    private Uri romTreeUri,backupTreeUri;
    private String scanState=UiStrings.msg("ui_95717615adbe"),drasticState="",topDisplayState="NOT_LAUNCHED";
    private boolean stateReady,libraryReady,storageAvailable,uiTouched,mappingReady;
    private DirectoryHealth.State romAccess=DirectoryHealth.State.NOT_CONFIGURED,saveAccess=DirectoryHealth.State.NOT_CONFIGURED;
    private long romDirectoryEpoch,saveDirectoryEpoch;
    private long revision,listRevision,stateVersion,userVersion,mappingVersion;
    private long committedState,committedUser,committedMappings;
    private long inputSequence,inputTime,callbackTime;
    private Snapshot currentSnapshot;
    private final EnumMap<Category,String> categorySelections=new EnumMap<>(Category.class);
    private GameEntry pendingLaunch;
    private boolean typesOverview,searchOriginTypesOverview;
    private JSONObject libraryPlaces=new JSONObject();
    private LocalSearch localSearch;private boolean searchActive,searchScoped,searchBusy,searchEditing,searchComposing;private String searchQuery="",searchScopeLabel=UiStrings.msg("ui_ea750e4cf350");
    private List<GameEntry> searchResults=Collections.emptyList();private long searchGeneration,metadataVersion;private int searchScopeMode;
    private JSONObject typeReturn;
    private List<String> restoredSearchIds=new ArrayList<>();
    private String searchOriginId="",searchOriginFolder="",searchOriginGenre="all";private Category searchOriginCategory=Category.NDS;private int searchOriginIndex,searchOriginSort,pageSize=UiMetrics.LIBRARY_ROWS;
    private volatile long libraryVersion;private long deliveredRevision;private boolean deliveryPosted;
    private final Runnable delivery=()->android.view.Choreographer.getInstance().postFrameCallback(frameTimeNanos->deliverPending());


    public static ShellStateRepository get(Context c){
        ShellStateRepository v=instance;
        if(v==null)synchronized(ShellStateRepository.class){
            v=instance;if(v==null)instance=v=new ShellStateRepository(c.getApplicationContext());
        }
        return v;
    }
    private ShellStateRepository(Context c){
        context=c;mappingFile=new AtomicFile(new File(c.getFilesDir(),"rom_uri_mappings_v3.json"));
        currentSnapshot=new Snapshot(this);
        { if(PerfTrace.isEnabled()) Log.i("TwinGridPerf","REPOSITORY_CONSTRUCT uptime="+SystemClock.uptimeMillis()+" diskRead=false"); }
        writer.execute(this::restore);
    }
    public synchronized Snapshot snapshot(){return currentSnapshot;}
    public synchronized void setSearchEditing(boolean editing){if(searchEditing!=editing){searchEditing=editing;ActionKeyLatch.reset();publishLocked();}}
    public synchronized void setSearchComposing(boolean composing){if(searchComposing!=composing){searchComposing=composing;publishLocked();}}
    public synchronized boolean launchStillCurrent(GameEntry game,long list,String descriptor){
        Snapshot s=currentSnapshot;return s.canActOnGame()&&s.selectedGame.gameId.equals(game.gameId)
                &&s.selectedGame.uriString().equals(game.uriString())&&s.actionQuery.equals(descriptor);
    }
    public synchronized Page page(){return page;}
    public synchronized int focusDisplay(){return currentFocusDisplay;}
    public synchronized int settingsIndex(){return settingsIndex;}
    public synchronized void selectSetting(int index){settingsIndex=Math.max(0,Math.min(SettingsModel.entries(settingsGroup).length-1,index));changedLocked();}
    public synchronized int homeIndex(){return homeIndex;}
    public synchronized void selectHome(int i){homeIndex=Math.max(0,Math.min(5,i));uiTouched=true;changedLocked();}
    private int detailLimit=3000;
    public synchronized void setDetailLimit(int y){detailLimit=Math.max(0,y);}
    public synchronized void setDetailOffset(int y){if(detailOffset!=y){detailOffset=Math.max(0,y);stateVersion++;scheduleWriteLocked(400);}}
    public synchronized List<GameEntry> allGamesCopy(){return allGames;} // immutable shared view
    public synchronized void whenReady(Runnable action){
        if(stateReady)main.post(action);else readyCallbacks.add(action);
    }
    public void addListener(Listener l){listeners.add(l);main.post(() -> {if(listeners.contains(l))l.onShellStateChanged(snapshot());});}
    public void removeListener(Listener l){listeners.remove(l);}

    private void restore(){
        long start=SystemClock.uptimeMillis();
        try{
            uiPrefs=context.getSharedPreferences("rgds_shell_ui_v3",0);
            UserReadCache.Records binaryUser=UserReadCache.loadRecords(context.getFilesDir());
            JSONObject fastUser=binaryUser==null?UserReadCache.load(context.getFilesDir()):null;
            if(fastUser==null&&binaryUser==null)userPrefs=context.getSharedPreferences("rgds_shell_user_v3",0);
            boolean migrated=uiPrefs.getInt("schema",0)==3;
            SharedPreferences legacy=null;
            if(!migrated||fastUser==null&&binaryUser==null&&!userPrefs.getBoolean("initialized",false)||!mappingFile.getBaseFile().exists())
                legacy=context.getSharedPreferences("rgds_shell_state_v1",0);
            SharedPreferences u=migrated?uiPrefs:legacy;
            final Category savedCategory=Category.fromName(u.getString("category","NDS"));
            final String savedId=u.getString("selected_game_id","");
            final int savedIndex=u.getInt("selected_index",0),savedSort=u.getInt("sort_mode",0);
            final Uri rom=parseUri(u.getString("rom_tree_uri","")),backup=parseUri(u.getString("backup_tree_uri",""));
            final String savedPage=migrated?u.getString("page","HOME"):"HOME";
            final String savedReturn=u.getString("return_page","HOME");
            final String savedSettingsReturn=u.getString("settings_return","HOME");
            final int savedDetail=u.getInt("detail_offset",0),savedFilter=u.getInt("filter_index",0),savedJump=u.getInt("jump_page",1);
            final int savedHome=u.getInt("home_index",0),savedSetting=u.getInt("settings_index",0);
            final String savedLaunch=u.getString("last_launch_id","");
            final String savedFolder=u.getString("folder_id",""),savedGenre=u.getString("genre_filter","all"),savedBookmarks=u.getString("bookmarks_v4","{}");
            final String savedPlaces=u.getString("library_places_v7","{}");final boolean savedOverview=u.getBoolean("types_overview_v7",savedPage.equals("GENRES"));
            final String savedSearch=u.getString("local_search_v5","{}"),savedTypeReturn=u.getString("type_return_v6","");
            final int savedGroup=u.getInt("settings_group",-1),savedDetailTab=u.getInt("detail_tab",0);
            final int[] settingRestore=SettingsModel.restore(u.getString("setting_id",""),savedGroup,savedSetting);
            final EnumMap<Category,String> selections=new EnumMap<>(Category.class);
            for(Category c:Category.values())selections.put(c,u.getString("selection_"+c.name(),""));
            final Set<String> favorites=new LinkedHashSet<>();
            final Map<String,UsageRecord> usage=new LinkedHashMap<>();
            final boolean userMigrated=binaryUser!=null||fastUser!=null||userPrefs.getBoolean("initialized",false);
            SharedPreferences data=userMigrated?userPrefs:legacy;
            JSONArray f=binaryUser!=null?new JSONArray(binaryUser.favorites):fastUser!=null?fastUser.getJSONArray("favorites"):new JSONArray(data.getString("favorite_ids","[]"));
            for(int i=0;i<f.length();i++)favorites.add(f.getString(i));
            JSONArray a=binaryUser!=null?new JSONArray():fastUser!=null?fastUser.getJSONArray("usage"):new JSONArray(data.getString("usage_records","[]"));
            for(int i=0;i<a.length();i++){
                JSONObject o=a.getJSONObject(i);String id=o.getString("gameId");
                usage.put(id,new UsageRecord(id,o.optString("uri"),o.optString("title"),o.optLong("lastPlayedAt"),
                        o.optInt("playCount"),o.optLong("lastSelectedAt"),o.optLong("lastLaunchAt"),o.optInt("launchRequestCount")));
            }
            if(binaryUser!=null)usage.putAll(binaryUser.usage);
            if(binaryUser==null&&userMigrated)UserReadCache.saveRecords(context.getFilesDir(),favorites,usage.values());
            if(binaryUser==null&&fastUser==null&&userMigrated)UserReadCache.save(context.getFilesDir(),f,a);
            { if(PerfTrace.isEnabled()) Log.i("TwinGridPerf","STATE_DATA_LOADED uptime="+SystemClock.uptimeMillis()+" elapsed="+(SystemClock.uptimeMillis()-start)+" verifiedUserCache="+(fastUser!=null||binaryUser!=null)+" binaryUserCache="+(binaryUser!=null)); }
            main.post(() -> {
                { if(PerfTrace.isEnabled()) Log.i("TwinGridPerf","STATE_APPLY_BEGIN uptime="+SystemClock.uptimeMillis()); }
                synchronized(this){
                    favoriteIds=Collections.unmodifiableSet(favorites);usageRecords.putAll(usage);
                    romTreeUri=rom;backupTreeUri=backup;romDirectoryEpoch++;saveDirectoryEpoch++;romAccess=rom==null?DirectoryHealth.State.NOT_CONFIGURED:DirectoryHealth.State.UNCHECKED;saveAccess=backup==null?DirectoryHealth.State.NOT_CONFIGURED:DirectoryHealth.State.UNCHECKED;lastLaunchId=savedLaunch;
                    selectedGameId=savedId;selectedIndex=savedIndex;sortMode=savedSort%2;
                    folderId=savedFolder;genreFilter=GenreTaxonomy.id(savedGenre);settingsGroup=settingRestore[0];detailTab=savedDetailTab;
                    try{JSONObject b=new JSONObject(savedBookmarks);Iterator<String> keys=b.keys();while(keys.hasNext()){String k=keys.next();bookmarks.put(k,b.getString(k));}for(String k:new ArrayList<>(bookmarks.keySet())){int split=k.lastIndexOf('|');if(split>=0){String old=k.substring(split+1);String id=GenreTaxonomy.id(old);bookmarks.putIfAbsent(k.substring(0,split+1)+id,bookmarks.get(k));}}}catch(Exception ignored){}
                    category=savedCategory==Category.SETTINGS?Category.NDS:savedCategory;restoreSearch(savedSearch);try{typeReturn=savedTypeReturn.isEmpty()?null:new JSONObject(savedTypeReturn);}catch(Exception ignored){}
                    categorySelections.putAll(selections);typesOverview=savedOverview;try{libraryPlaces=new JSONObject(savedPlaces);restoreGenreCursors(libraryPlaces);}catch(Exception ignored){}
                    genreIndex=readGenreCursor(libraryPlaces.optJSONObject(Category.TYPES.name()));
                    if(!uiTouched){
                        try{page=savedPage.equals("GENRES")?Page.LIBRARY:Page.valueOf(savedPage);returnPage=savedReturn.equals("GENRES")?Page.LIBRARY:Page.valueOf(savedReturn);}catch(Exception e){page=Page.HOME;}
                        try{settingsReturn=Page.valueOf(savedSettingsReturn);}catch(Exception e){settingsReturn=Page.HOME;}
                        detailOffset=savedDetail;filterIndex=savedFilter;jumpPage=savedJump;
                        homeIndex=Math.max(0,Math.min(5,savedHome));settingsIndex=settingRestore[1];
                    }
                    stateReady=true;
                    if(!migrated)stateVersion++;
                    if(!userMigrated)userVersion++;
                    scanState=rom==null?UiStrings.msg("ui_202126859f48"):UiStrings.msg("ui_e5918e6e2955");
                    currentSnapshot=new Snapshot(this);
                    for(Runnable r:readyCallbacks)r.run();readyCallbacks.clear();
                    publishLocked();scheduleWriteLocked(350);
                }
                { if(PerfTrace.isEnabled()) Log.i("TwinGridPerf","STATE_RESTORED uptime="+SystemClock.uptimeMillis()+" elapsed="+(SystemClock.uptimeMillis()-start)+" migrated="+migrated); }
            });
            // Historical URI mappings are not needed to navigate or launch cached games.
            final SharedPreferences old=legacy;
            writer.schedule(()->restoreMappings(old),1500,TimeUnit.MILLISECONDS);
        }catch(Exception e){
            Log.e(TAG,"STATE_RESTORE_FAILED original files retained; no writes",e);
            main.post(() -> {synchronized(this){scanState=UiStrings.msg("ui_6ac3d059b677");publishLocked();}});
        }
    }

    private void restoreMappings(SharedPreferences legacy){
        try{
            boolean exists=mappingFile.getBaseFile().exists();
            String json=exists?new String(mappingFile.readFully(),StandardCharsets.UTF_8):legacy.getString("rom_mappings","[]");
            JSONArray a=new JSONArray(json);Map<String,RomMapping> restored=new LinkedHashMap<>();
            for(int i=0;i<a.length();i++){
                JSONObject o=a.getJSONObject(i);String id=o.getString("gameId");
                restored.put(id,new RomMapping(id,o.optString("uri"),o.optString("title")));
            }
            synchronized(this){
                boolean changed=!exists;
                for(RomMapping current:romMappings.values()){
                    RomMapping old=restored.get(current.gameId);
                    if(old==null||!old.uri.equals(current.uri)||!old.title.equals(current.title)){changed=true;break;}
                }
                for(Map.Entry<String,RomMapping> e:restored.entrySet())if(!romMappings.containsKey(e.getKey()))romMappings.put(e.getKey(),e.getValue());
                mappingReady=true;if(!exists)mappingVersion++;
                if(!changed)committedMappings=mappingVersion;
                scheduleWriteLocked(400);
            }
            { if(PerfTrace.isEnabled()) Log.i("TwinGridPerf","MAPPINGS_RESTORED uptime="+SystemClock.uptimeMillis()+" count="+restored.size()+" navigationRequired=false"); }
        }catch(Exception e){Log.e(TAG,"MAPPING_RESTORE_FAILED retained original file; export deferred",e);}
    }

    /** Build/copy the low-frequency library off the UI thread, then swap references briefly. */
    public void setGames(List<GameEntry> games,String status){setGames(games,status,()->true);}
    public void setGames(List<GameEntry> games,String status,java.util.function.BooleanSupplier valid){
        setGames(games,status,valid,false);
    }
    private boolean scanOrderPending;private long libraryPublications,unchangedPublications;
    public void setGamesPartial(List<GameEntry> games,String status,java.util.function.BooleanSupplier valid){setGames(games,status,valid,true);}
    private void setGames(List<GameEntry> games,String status,java.util.function.BooleanSupplier valid,boolean incremental){
        final long preparedAt=android.os.SystemClock.uptimeMillis();
        Map<String,GameEntry> previousIndex;List<GameEntry> previousTitles,previousModified;boolean pendingOrder;
        synchronized(this){previousIndex=byId;previousTitles=sortedTitles;previousModified=sortedModified;pendingOrder=scanOrderPending;}
        boolean identical=games.size()==previousIndex.size();for(GameEntry game:games)if(previousIndex.get(game.gameId)!=game){identical=false;break;}
        if(identical&&(incremental||!pendingOrder)){main.post(()->{if(valid.getAsBoolean()){synchronized(this){unchangedPublications++;scanState=status;publishLocked();}PerfTrace.event("LIBRARY_UNCHANGED count="+games.size()+" skipped="+unchangedPublications);}});return;}
        List<GameEntry> immutable=Collections.unmodifiableList(new ArrayList<>(games));
        Map<String,GameEntry> index=new HashMap<>();for(GameEntry g:immutable)index.put(g.gameId,g);
        // NDS sorted indexes are independent of UI state and can be prepared on rom-reader.
        List<GameEntry> titles=new ArrayList<>(immutable),modified=new ArrayList<>(immutable);
        if(incremental){titles=stableScanOrder(previousTitles,titles,index);modified=stableScanOrder(previousModified,modified,index);}
        else {titles.sort(Comparator.comparing(GameEntry::bestTitle,String.CASE_INSENSITIVE_ORDER));modified.sort((a,b)->Long.compare(b.romModifiedAt,a.romModifiedAt));}
        final List<GameEntry> nextTitles=titles,nextModified=modified;
        main.post(() -> {
            if(!valid.getAsBoolean())return;
            MetadataManager.get(context).catalog.invalidateBindings();
            synchronized(this){
                allGames=immutable;byId=Collections.unmodifiableMap(index);listCache.clear();
                if(searchActive){List<GameEntry> savedResults=new ArrayList<>();if(!restoredSearchIds.isEmpty()){for(String id:restoredSearchIds){GameEntry g=byId.get(id);if(g!=null)savedResults.add(g);}restoredSearchIds.clear();}else for(GameEntry old:searchResults){GameEntry current=byId.get(old.gameId);if(current!=null)savedResults.add(current);}searchResults=Collections.unmodifiableList(savedResults);}
                sortedTitles=Collections.unmodifiableList(nextTitles);sortedModified=Collections.unmodifiableList(nextModified);scanOrderPending=incremental;libraryPublications++;
                listCache.put("NDS||all|0",sortedTitles);
                listCache.put("NDS||all|1",sortedModified);
                listRevision++;libraryVersion++;libraryReady=true;scanState=status;
                boolean mappingChanged=false;
                for(GameEntry g:immutable){
                    RomMapping old=romMappings.get(g.gameId);
                    if(old==null||!old.uri.equals(g.uriString())||!old.title.equals(g.bestTitle())){
                        romMappings.put(g.gameId,new RomMapping(g.gameId,g.uriString(),g.bestTitle()));mappingChanged=true;
                    }
                }
                if(mappingChanged)mappingVersion++;
                rebuildVisibleLocked();normalizeSelectionLocked();changedLocked();
                reindexSearch();
                { if(PerfTrace.isEnabled()) Log.i("TwinGridPerf","LIBRARY_READY uptime="+SystemClock.uptimeMillis()+" count="+allGames.size()+" listRevision="+listRevision+" publications="+libraryPublications+" incremental="+incremental+" elapsedMs="+(SystemClock.uptimeMillis()-preparedAt)+" selected="+selectedGameId); }
            }
            MetadataManager.get(context).gamesPublished();
        });
    }
    private static List<GameEntry> stableScanOrder(List<GameEntry> prior,List<GameEntry> incoming,Map<String,GameEntry> index){List<GameEntry> out=new ArrayList<>();Set<String> seen=new HashSet<>();for(GameEntry old:prior){GameEntry game=index.get(old.gameId);if(game!=null){out.add(game);seen.add(game.gameId);}}for(GameEntry game:incoming)if(seen.add(game.gameId))out.add(game);return out;}
    public void setStorageAvailable(boolean available,java.util.function.BooleanSupplier valid){main.post(()->{if(valid.getAsBoolean())setStorageAvailable(available);});}
    public synchronized void setStorageAvailable(boolean available){storageAvailable=available;romAccess=available?DirectoryHealth.State.AVAILABLE:DirectoryHealth.State.UNCHECKED;publishLocked();}
    synchronized void setDirectoryAccess(boolean saves,Uri tree,long epoch,DirectoryHealth.State state){
        if(!java.util.Objects.equals(tree,saves?backupTreeUri:romTreeUri)||epoch!=(saves?saveDirectoryEpoch:romDirectoryEpoch))return;
        if(saves){if(saveAccess==state)return;saveAccess=state;}else{if(romAccess==state)return;romAccess=state;storageAvailable=state==DirectoryHealth.State.AVAILABLE;}publishLocked();
    }
    public void setDirectoryIndex(DirectoryIndex d){setDirectoryIndex(d,()->true);}
    public void setDirectoryIndex(DirectoryIndex d,java.util.function.BooleanSupplier valid){main.post(()->{synchronized(this){if(!valid.getAsBoolean()||romTreeUri==null||!d.tree.equals(romTreeUri.toString()))return;directoryIndex=d;if(!d.folders.containsKey(folderId))folderId=d.rootId;libraryVersion++;listCache.clear();if(searchActive)runSearch();rebuildVisibleLocked();normalizeSelectionLocked();listRevision++;changedLocked();}MetadataManager.get(context).directoryReady(d);});}
    public synchronized void metadataChanged(){
        metadataVersion++;listCache.clear();reindexSearch();if(searchActive)runSearch();
        if(libraryReady&&!searchActive&&!genreFilter.equals("all")){
            List<GameEntry> prior=visible;rebuildVisibleLocked();
            normalizeSelectionLocked();listRevision++;
        }publishLocked();
    }
    public synchronized void invalidateMetadataCaches(){metadataChanged();} // No selection movement or full-screen publication during sync.
    private String scopeKey(){return category.name()+"|"+(category==Category.FOLDERS?folderId:"")+"|"+genreFilter;}
    private void remember(){if(searchActive)return;bookmarks.put(scopeKey(),selectedGameId);categorySelections.put(category,selectedGameId);}
    private void recall(){selectedGameId=bookmarks.getOrDefault(scopeKey(),"");selectedIndex=0;}
    public synchronized boolean enterFolder(){
        DirectoryIndex.Folder f=selectedFolderLocked();if(f==null)return false;
        remember();folderId=f.id;recall();rebuildVisibleLocked();normalizeSelectionLocked();listRevision++;changedLocked();return true;
    }
    public synchronized void setGenre(String genre){remember();genreFilter=GenreTaxonomy.id(genre);rebuildVisibleLocked();normalizeSelectionLocked();listRevision++;changedLocked();}
    public synchronized void chooseGenre(int index){genreIndex=Math.max(0,Math.min(MetadataIndex.GENRES.length-2,index));changedLocked();}
    public synchronized void enterGenre(){openGenre(MetadataIndex.GENRES[genreIndex+1],false);}
    public synchronized void openGenre(String genre,boolean fromTag){
        if(fromTag){try{typeReturn=new JSONObject().put("search",searchStateLocked().json()).put("id",selectedGameId).put("index",selectedIndex).put("category",category.name()).put("folder",folderId).put("genre",genreFilter).put("sort",sortMode).put("overview",typesOverview);}catch(Exception ignored){}}else typeReturn=null;
        if(searchActive)exitSearchLocked();category=Category.TYPES;genreFilter=GenreTaxonomy.id(genre);typesOverview=false;page=Page.LIBRARY;
        rebuildVisibleLocked();normalizeSelectionLocked();listRevision++;changedLocked();
    }
    private Set<String> recentIdsLocked(){Set<String> ids=new HashSet<>();for(UsageRecord u:usageRecords.values())if(u.lastLaunchAt>0)ids.add(u.gameId);return ids;}
    synchronized Set<String> recentIdsForAudit(){return recentIdsLocked();}
    private BrowseQuery queryLocked(){
        if(!searchActive)return new BrowseQuery(category.name(),folderId,false,genreFilter,"",sortMode,libraryVersion,metadataVersion,userVersion);
        String c=searchScopeMode==0?"NDS":searchScopeMode==2?"FOLDERS":searchOriginCategory.name();
        return new BrowseQuery(c,searchOriginFolder,true,searchScopeMode==1?searchOriginGenre:"all",searchQuery,sortMode,libraryVersion,metadataVersion,userVersion);
    }
    private String queryJsonLocked(){try{return queryLocked().json().toString();}catch(Exception e){return "{}";}}
    private String actionQueryLocked(){try{return queryLocked().intentJson().toString();}catch(Exception e){return "{}";}}
    public synchronized void setDetailTab(int tab){detailTab=tab;detailOffset=0;changedLocked();}
    public synchronized void setSettingsGroup(int group){if(settingsGroup>=0)settingPositions[settingsGroup]=settingsIndex;settingsGroup=group;settingsIndex=group<0?0:settingPositions[group];changedLocked();}
    private void rebuildVisibleLocked(){
        if(searchActive){visible=searchResults;rebuildItemsLocked();return;}
        String key=scopeKey()+"|"+sortMode;
        List<GameEntry> cached=listCache.get(key);
        if(cached==null){
            List<GameEntry> v=new ArrayList<>();
            List<GameEntry> base=sortMode==0?sortedTitles:sortedModified;
            BrowseQuery query=queryLocked();Set<String> recent=recentIdsLocked();MetadataManager metadata=MetadataManager.get(context);
            for(GameEntry g:base){
                if(!query.exclusion(g.gameId,metadata.genres(g.gameId),favoriteIds,recent,directoryIndex).isEmpty())continue;
                v.add(g);
            }
            if(category==Category.RECENT)v.sort((a,b)->Long.compare(usageRecords.get(b.gameId).lastLaunchAt,usageRecords.get(a.gameId).lastLaunchAt));
            cached=Collections.unmodifiableList(v);listCache.put(key,cached);
            { if(PerfTrace.isEnabled()) Log.i("TwinGridPerf","LIST_REBUILD category="+category+" count="+v.size()+" uptime="+SystemClock.uptimeMillis()); }
        }
        visible=cached;rebuildItemsLocked();
    }
    private void rebuildItemsLocked(){
        recentCount=0;List<DirectoryIndex.Item> rows=new ArrayList<>();
        if(!searchActive&&category==Category.FOLDERS&&directoryIndex!=null){
            Map<String,Integer> hits=new HashMap<>();for(GameEntry g:allGames)if(MetadataManager.get(context).hasGenre(g.gameId,genreFilter))for(String ancestor:directoryIndex.ancestors.getOrDefault(g.gameId,Collections.emptySet()))hits.put(ancestor,hits.getOrDefault(ancestor,0)+1);
            for(DirectoryIndex.Folder f:directoryIndex.childFolders(folderId))rows.add(new DirectoryIndex.Item(f,hits.getOrDefault(f.id,0)));
        }
        for(GameEntry g:visible)rows.add(new DirectoryIndex.Item(g));items=Collections.unmodifiableList(rows);
        for(UsageRecord u:usageRecords.values())if(u.lastLaunchAt>0&&byId.containsKey(u.gameId))recentCount++;
    }
    private void normalizeSelectionLocked(){
        if(items.isEmpty()){selectedIndex=0;scrollPosition=0;return;}
        int found=-1;
        for(int i=0;i<items.size();i++)if(items.get(i).key().equals(selectedGameId)){found=i;break;}
        selectedIndex=found>=0?found:Math.max(0,Math.min(selectedIndex,items.size()-1));
        selectedGameId=items.get(selectedIndex).key();scrollPosition=selectedIndex/pageSize*pageSize;
    }
    private GameEntry selectedGameLocked(){
        if(page==Page.HOME){
            GameEntry g=byId.get(selectedGameId);
            return g!=null?g:byId.get(lastLaunchId);
        }
        return items.isEmpty()?null:items.get(Math.max(0,Math.min(selectedIndex,items.size()-1))).game;
    }
    private DirectoryIndex.Folder selectedFolderLocked(){return searchActive||category!=Category.FOLDERS||items.isEmpty()?null:items.get(Math.max(0,Math.min(selectedIndex,items.size()-1))).folder;}
    public synchronized void setPageSize(int size){size=Math.max(1,Math.min(6,size));if(pageSize==size)return;pageSize=size;scrollPosition=selectedIndex/pageSize*pageSize;listRevision++;publishLocked();}
    public synchronized void startSearch(){
        if(searchActive||page!=Page.LIBRARY)return;remember();searchOriginId=selectedGameId;searchOriginIndex=selectedIndex;searchOriginCategory=category;searchOriginFolder=folderId;searchOriginGenre=genreFilter;searchOriginSort=sortMode;
        searchOriginTypesOverview=typesOverview;typesOverview=false;searchActive=true;searchScoped=false;searchScopeMode=0;searchQuery="";searchScopeLabel=UiStrings.msg("ui_ea750e4cf350");searchResults=allGames;
        selectedIndex=0;selectedGameId="";rebuildVisibleLocked();normalizeSelectionLocked();listRevision++;runSearch();changedLocked();
    }
    public synchronized void searchQuery(String query){if(!searchActive)return;searchQuery=query.substring(0,Math.min(160,query.length()));selectedGameId="";selectedIndex=0;runSearch();changedLocked();}
    public synchronized void searchScope(boolean scoped){searchScope(scoped?1:0);}
    public synchronized void searchScope(int mode){if(!searchActive)return;searchScopeMode=Math.max(0,Math.min(2,mode));searchScoped=searchScopeMode>0;updateScopeLabel();runSearch();changedLocked();}
    private void updateScopeLabel(){searchScopeLabel=searchScopeMode==0?UiStrings.msg("ui_ea750e4cf350"):searchScopeMode==2||searchOriginCategory==Category.FOLDERS?(directoryIndex==null?UiStrings.msg("ui_257f4442af73"):directoryIndex.path(searchOriginFolder))+UiStrings.msg("ui_c2544ca67689")+GenreTaxonomy.label(searchScopeMode==2?"all":searchOriginGenre):searchOriginCategory.label+" · "+GenreTaxonomy.label(searchOriginGenre);}
    private void exitSearchLocked(){
        typesOverview=searchOriginTypesOverview;searchGeneration++;searchActive=false;searchBusy=false;searchEditing=false;searchComposing=false;searchResults=Collections.emptyList();category=searchOriginCategory;folderId=searchOriginFolder;genreFilter=searchOriginGenre;sortMode=searchOriginSort;selectedGameId=searchOriginId;selectedIndex=searchOriginIndex;pageSize=UiMetrics.LIBRARY_ROWS;
        rebuildVisibleLocked();normalizeSelectionLocked();listRevision++;
    }
    private void reindexSearch(){
        if(!libraryReady)return;if(localSearch==null)localSearch=new LocalSearch(context);
        localSearch.rebuild(allGames,()->{synchronized(this){if(searchActive)runSearch();}});
    }
    public synchronized void searchInputsChanged(){reindexSearch();}
    private void runSearch(){
        if(!searchActive)return;searchBusy=true;if(localSearch==null)return;long generation=++searchGeneration,library=libraryVersion,submitted=SystemClock.uptimeMillis();
        updateScopeLabel();BrowseQuery descriptor=queryLocked();Set<String> scope=null;
        if(searchScoped){scope=new HashSet<>();Set<String> recent=recentIdsLocked();for(GameEntry g:allGames)if(descriptor.exclusion(g.gameId,MetadataManager.get(context).genres(g.gameId),favoriteIds,recent,directoryIndex).isEmpty())scope.add(g.gameId);}
        localSearch.query(searchQuery,scope,sortMode,generation,(received,indexVersion,result)->{synchronized(this){
            if(!searchActive||received!=searchGeneration||library!=libraryVersion||indexVersion!=localSearch.version())return;
            searchBusy=indexVersion==0||!localSearch.phoneticReady()||!localSearch.ready()||(searchScoped&&!MetadataManager.get(context).searchReady());PerfTrace.event("SEARCH_RESULTS_READY busy="+searchBusy+" results="+result.size()+" query="+searchQuery+" uptime="+SystemClock.uptimeMillis()+" generation="+received+" elapsedMs="+(SystemClock.uptimeMillis()-submitted));searchResults=result;rebuildVisibleLocked();normalizeSelectionLocked();listRevision++;changedLocked();
        }});
    }
    private static final class SearchState {
        boolean active,scoped,originOverview;String query,scopeLabel,originId,originCategory,originFolder,originGenre;int originIndex,originSort,mode;String descriptor;
        List<GameEntry> results;
        String json(){if(!active)return "{\"active\":false}";try{List<String> ids=new ArrayList<>();for(GameEntry g:results)ids.add(g.gameId);return new JSONObject().put("schema",6).put("descriptor",new JSONObject(descriptor)).put("scopeMode",mode).put("resultIds",new JSONArray(ids)).put("active",active).put("query",query).put("scoped",scoped).put("scopeLabel",scopeLabel).put("originId",originId).put("originIndex",originIndex).put("originCategory",originCategory).put("originFolder",originFolder).put("originGenre",originGenre).put("originSort",originSort).put("originOverview",originOverview).toString();}catch(JSONException e){return "{}";}}
    }
    private SearchState searchStateLocked(){SearchState s=new SearchState();s.originOverview=searchOriginTypesOverview;s.active=searchActive;s.scoped=searchScoped;s.query=searchQuery;s.scopeLabel=searchScopeLabel;s.originId=searchOriginId;s.originCategory=searchOriginCategory.name();s.originFolder=searchOriginFolder;s.originGenre=searchOriginGenre;s.originIndex=searchOriginIndex;s.originSort=searchOriginSort;s.results=searchResults;s.mode=searchScopeMode;s.descriptor=queryJsonLocked();return s;}
    private void restoreSearch(String data){try{JSONObject o=new JSONObject(data);searchActive=o.optBoolean("active");searchQuery=o.optString("query");searchScoped=o.optBoolean("scoped");searchScopeMode=o.optInt("scopeMode",searchScoped?1:0);searchScopeLabel=o.optString("scopeLabel",UiStrings.msg("ui_ea750e4cf350"));searchOriginId=o.optString("originId");searchOriginIndex=o.optInt("originIndex");searchOriginCategory=Category.fromName(o.optString("originCategory"));searchOriginFolder=o.optString("originFolder");searchOriginGenre=GenreTaxonomy.id(o.optString("originGenre","all"));searchOriginSort=o.optInt("originSort");searchOriginTypesOverview=o.optBoolean("originOverview");restoredSearchIds=new ArrayList<>();JSONArray results=o.optJSONArray("resultIds");if(results!=null)for(int i=0;i<results.length();i++)restoredSearchIds.add(results.optString(i));}catch(Exception e){searchActive=false;}}
    public void beginInput(long event,long callback){long wait=SystemClock.uptimeMillis();synchronized(this){PerfTrace.event("STATE_INPUT_LOCK waitMs="+(SystemClock.uptimeMillis()-wait));inputSequence++;inputTime=event;callbackTime=callback;}}
    public synchronized void endInput(){inputTime=0;callbackTime=0;} // published snapshot retains the event timestamps
    public void navigate(int direction){
        long wait=SystemClock.uptimeMillis();synchronized(this){PerfTrace.event("STATE_LOCK waitMs="+(SystemClock.uptimeMillis()-wait));navigateLocked(direction);}
    }
    private void navigateLocked(int direction){
        uiTouched=true;
        if(page==Page.HOME){
            int next=HomeLayout.navigate(homeIndex,direction);
            if(next!=homeIndex){homeIndex=next;changedLocked();}return;
        }
        if(page==Page.LIBRARY&&typesOverview){
            int next=genreIndex,columns=UiMetrics.GENRE_COLUMNS;
            if(direction==1)next=Math.max(0,genreIndex-columns);
            else if(direction==2)next=Math.min(MetadataIndex.GENRES.length-2,genreIndex+columns);
            else if(direction==3&&genreIndex%columns>0)next--;
            else if(direction==4&&genreIndex%columns<columns-1)next=Math.min(MetadataIndex.GENRES.length-2,genreIndex+1);
            chooseGenre(next);return;
        }
        if(page==Page.PREPARING){taskIndex=Math.max(0,Math.min(2,taskIndex+(direction==1||direction==3?-1:1)));publishLocked();return;}
        if(page==Page.TASKS){int[] order={0,1,2,3};int position=0;while(position<order.length-1&&order[position]!=taskIndex)position++;taskIndex=order[Math.max(0,Math.min(order.length-1,position+(direction==1||direction==3?-1:1)))];publishLocked();return;}
        if(page==Page.SETTINGS){if(direction==3||direction==4)pageSetting(direction==3?-1:1);else if(direction==1||direction==2)moveSetting(direction==1?-1:1);return;}
        if(page==Page.DETAILS){if(direction==3||direction==4){detailTab=1-detailTab;detailOffset=0;}else detailOffset=Math.max(0,Math.min(detailLimit,detailOffset+(direction==1?-96:96)));changedLocked();return;}
        if(page==Page.FILTER){
            if(direction==1||direction==2)filterIndex=Math.max(fixedRecentOrderLocked()?1:0,Math.min(5,filterIndex+(direction==1?-1:1)));
            else jumpPage=cyclePosition(jumpPage-1,direction==3?-1:1,pageCount())+1;
            publishLocked();return;
        }
        if(direction==1||direction==2)moveSelection(direction==1?-1:1);else pageBy(direction==3?-1:1);
    }
    public synchronized void pageSetting(int delta){
        if(settingsGroup<0){selectSetting(settingsIndex+delta);return;}
        int count=SettingsModel.entries(settingsGroup).length;
        int target=settingsIndex/UiMetrics.ROWS+delta;
        if(target<0||target>=(count+UiMetrics.ROWS-1)/UiMetrics.ROWS)return;
        selectSetting(Math.min(count-1,target*UiMetrics.ROWS+settingsIndex%UiMetrics.ROWS));
    }
    public synchronized void moveSetting(int delta){int first=settingsIndex/UiMetrics.ROWS*UiMetrics.ROWS;int last=Math.min(SettingsModel.entries(settingsGroup).length-1,first+UiMetrics.ROWS-1);settingsIndex=Math.max(first,Math.min(last,settingsIndex+delta));changedLocked();}
    public synchronized void selectIndex(int index){
        if(items.isEmpty())return;
        int next=Math.max(0,Math.min(index,items.size()-1));
        if(next==selectedIndex)return;
        selectedIndex=next;selectedGameId=items.get(next).key();scrollPosition=next/pageSize*pageSize;
        remember();if(items.get(next).game!=null)touchSelectedLocked(items.get(next).game,false);
        changedLocked();
    }
    private static int cyclePosition(int position,int delta,int count){
        if(count<=1)return 0;
        return (int)Math.floorMod((long)position+delta,(long)count);
    }
    public synchronized void moveSelection(int delta){
        if(items.isEmpty())return;
        selectIndex(cyclePosition(selectedIndex,delta,items.size()));
    }
    public synchronized int pageCount(){return Math.max(1,(items.size()+pageSize-1)/pageSize);}
    public synchronized void pageBy(int delta){
        if(items.isEmpty())return;
        int target=cyclePosition(selectedIndex/pageSize,delta,pageCount());
        selectIndex(target*pageSize+selectedIndex%pageSize);
    }
    /** Overview excludes GENRES[0] (all). Pre-code112 indexes used the frozen order below. */
    static final class GenreCursor {
        private static final String[] LEGACY={"action","adventure","rpg","strategy","racing","sports","fighting","shooter","puzzle","music","simulation","other","unclassified"};
        static String id(int index){return MetadataIndex.GENRES[Math.max(0,Math.min(MetadataIndex.GENRES.length-2,index))+1];}
        static int restore(String stableId,int legacyIndex){
            String wanted=stableId==null||stableId.isEmpty()?LEGACY[Math.max(0,Math.min(LEGACY.length-1,legacyIndex))]:stableId;
            for(int i=1;i<MetadataIndex.GENRES.length;i++)if(MetadataIndex.GENRES[i].equals(wanted))return i-1;
            return 0; // Missing/removed IDs fall back to the first visible overview tile.
        }
    }
    private static int readGenreCursor(JSONObject place){return place==null?0:GenreCursor.restore(place.optString("genreCursorId"),place.optInt("genreIndex"));}
    private static void restoreGenreCursors(JSONObject places)throws JSONException{
        Iterator<String> keys=places.keys();while(keys.hasNext()){
            JSONObject place=places.optJSONObject(keys.next());if(place!=null)place.put("genreCursorId",GenreCursor.id(readGenreCursor(place)));
        }
    }
    private void rememberPlace(){
        if(category==Category.SETTINGS||searchActive)return;
        try{libraryPlaces.put(category.name(),new JSONObject().put("id",selectedGameId).put("index",selectedIndex).put("folder",folderId).put("genre",genreFilter).put("sort",sortMode).put("overview",typesOverview).put("genreIndex",genreIndex).put("genreCursorId",GenreCursor.id(genreIndex)));}catch(JSONException ignored){}
    }
    public synchronized void setCategory(Category c){
        if(c==Category.SETTINGS){openSettings();return;}
        if(!stateReady)return;if(searchActive)exitSearchLocked();
        uiTouched=true;remember();rememberPlace();typeReturn=null;
        if(c!=category||page!=Page.LIBRARY){
            category=c;JSONObject place=libraryPlaces.optJSONObject(c.name());
            genreFilter=place==null?"all":GenreTaxonomy.id(place.optString("genre","all"));sortMode=place==null?0:place.optInt("sort");
            selectedGameId=place==null?categorySelections.getOrDefault(c,selectedGameId):place.optString("id");selectedIndex=place==null?0:place.optInt("index");
            if(c==Category.FOLDERS){folderId=place==null?folderId:place.optString("folder");if(directoryIndex!=null&&!directoryIndex.folders.containsKey(folderId))folderId=directoryIndex.rootId;}
            typesOverview=c==Category.TYPES&&(place==null||place.optBoolean("overview",true));if(c==Category.TYPES&&place!=null)genreIndex=readGenreCursor(place);
            rebuildVisibleLocked();normalizeSelectionLocked();listRevision++;
        }
        page=SetupJourney.held(context)?Page.PREPARING:Page.LIBRARY;changedLocked();
    }
    public synchronized void switchCategory(int delta){
        if(page==Page.FILTER){setJumpPage(cyclePosition(jumpPage-1,delta*10,pageCount())+1);return;}
        if(page!=Page.LIBRARY&&page!=Page.GENRES)return;
        Category[] cs={Category.NDS,Category.TYPES,Category.FOLDERS,Category.FAVORITES,Category.RECENT};
        int i=Arrays.asList(cs).indexOf(category);setCategory(cs[(i+delta+5)%5]);
    }
    public synchronized void openHome(){uiTouched=true;page=Page.HOME;changedLocked();}
    String setupControlKey(){return SetupJourney.controlKey(context,taskIndex);}
    String taskControlKey(){return MetadataManager.get(context).taskControlKey();}
    public synchronized void selectTask(int index){taskIndex=Math.max(0,Math.min(3,index));publishLocked();}
    public synchronized void openPreparation(){uiTouched=true;page=Page.PREPARING;taskIndex=0;changedLocked();}
    public synchronized void openTasks(){if(page==Page.TASKS)return;uiTouched=true;taskReturn=page;page=Page.TASKS;taskIndex=0;publishLocked();}
    public synchronized void openSettings(){uiTouched=true;settingsReturn=page==Page.LIBRARY||page==Page.PREPARING?page:Page.HOME;settingsGroup=-1;settingsIndex=0;page=Page.SETTINGS;changedLocked();}
    public synchronized void back(){
        if(page==Page.PREPARING){SetupJourney.leave(context,false);return;}
        if(page==Page.TASKS){page=taskReturn;publishLocked();return;}
        if(page==Page.LIBRARY&&searchActive){exitSearchLocked();changedLocked();return;}
        if(page==Page.DETAILS){page=returnPage;detailOffset=0;}
        else if(page==Page.SETTINGS){if(settingsGroup>=0){settingPositions[settingsGroup]=settingsIndex;settingsIndex=settingsGroup;settingsGroup=-1;}else page=settingsReturn;}
        else if(page==Page.FILTER)page=Page.LIBRARY;
        else if(page==Page.GENRES)page=Page.HOME; // Read compatibility only; no new GENRES page.
        else if(page==Page.LIBRARY){
            if(category==Category.TYPES){if(typesOverview){rememberPlace();page=Page.HOME;}else if(typeReturn!=null){JSONObject origin=typeReturn;typeReturn=null;typesOverview=origin.optBoolean("overview");category=Category.fromName(origin.optString("category"));folderId=origin.optString("folder");genreFilter=GenreTaxonomy.id(origin.optString("genre","all"));sortMode=origin.optInt("sort");selectedGameId=origin.optString("id");selectedIndex=origin.optInt("index");restoreSearch(origin.optString("search","{}"));if(searchActive){List<GameEntry> saved=new ArrayList<>();for(String id:restoredSearchIds){GameEntry game=byId.get(id);if(game!=null)saved.add(game);}restoredSearchIds.clear();searchResults=Collections.unmodifiableList(saved);}rebuildVisibleLocked();normalizeSelectionLocked();if(searchActive)runSearch();listRevision++;}else{typesOverview=true;rememberPlace();}}
            else if(category==Category.FOLDERS&&directoryIndex!=null&&!folderId.equals(directoryIndex.rootId)){
                DirectoryIndex.Folder f=directoryIndex.folders.get(folderId);remember();folderId=f==null?directoryIndex.rootId:f.parentId;
                recall();rebuildVisibleLocked();normalizeSelectionLocked();listRevision++;
            }else page=Page.HOME;
        }
        else return;
        changedLocked();
    }
    public synchronized void toggleDetailPage(){
        if(page==Page.DETAILS){back();return;}
        if(page!=Page.SETTINGS&&!currentSnapshot.canBrowseGame()||page==Page.HOME)return;
        returnPage=page;page=Page.DETAILS;detailOffset=0;changedLocked();
    }
    public synchronized void openFilter(){
        if(page!=Page.LIBRARY||typesOverview)return;page=Page.FILTER;filterIndex=fixedRecentOrderLocked()?1:0;jumpPage=selectedIndex/pageSize+1;changedLocked();
    }
    private boolean fixedRecentOrderLocked(){return category==Category.RECENT&&!searchActive;}
    public synchronized void setJumpPage(int p){jumpPage=Math.max(1,Math.min(pageCount(),p));publishLocked();}
    public synchronized void activateFilter(){
        if(page!=Page.FILTER)return;
        if(filterIndex==0){if(!fixedRecentOrderLocked())cycleSortMode();return;}
        if(filterIndex==1){setJumpPage(cyclePosition(jumpPage-1,1,pageCount())+1);return;}
        if(filterIndex==2){page=Page.LIBRARY;selectIndex((jumpPage-1)*pageSize);changedLocked();}
        else if(filterIndex==3){page=Page.LIBRARY;selectIndex(0);changedLocked();}
        else if(filterIndex==4){page=Page.LIBRARY;selectIndex((pageCount()-1)*pageSize);changedLocked();}
        else back();
    }
    public synchronized void activateFilterAt(int i){if(i==0&&fixedRecentOrderLocked())return;filterIndex=i;activateFilter();}
    public synchronized void toggleFavorite(){
        if(page!=Page.LIBRARY||!currentSnapshot.canBrowseGame())return;
        GameEntry g=selectedGameLocked();if(g==null)return;
        Set<String> f=new LinkedHashSet<>(favoriteIds);if(!f.remove(g.gameId))f.add(g.gameId);
        favoriteIds=Collections.unmodifiableSet(f);userVersion++;
        listCache.clear();if(searchActive)runSearch();
        if(category==Category.FAVORITES){rebuildVisibleLocked();normalizeSelectionLocked();listRevision++;}
        changedLocked();
    }
    public synchronized void cycleSortMode(){
        if(page==Page.FILTER&&fixedRecentOrderLocked())return;
        sortMode=(sortMode+1)%2;if(searchActive){runSearch();changedLocked();return;}rebuildVisibleLocked();normalizeSelectionLocked();listRevision++;changedLocked();
    }
    public synchronized void beginLaunch(GameEntry g){pendingLaunch=g;}
    public synchronized void cancelLaunchRecord(){pendingLaunch=null;}
    public synchronized void recordLaunchAccepted(){
        if(pendingLaunch==null)return;
        UsageRecord before=usageRecords.get(pendingLaunch.gameId);
        if(before==null||before.lastLaunchAt==0)recentCount++;
        touchSelectedLocked(pendingLaunch,true);lastLaunchId=pendingLaunch.gameId;
        { if(PerfTrace.isEnabled()) Log.i(TAG,"LAUNCH_REQUEST_RECORDED gameId="+pendingLaunch.gameId+" evidence=public_entry_accepted"); }
        pendingLaunch=null;listCache.clear();if(searchActive)runSearch();
        if(category==Category.RECENT){rebuildVisibleLocked();normalizeSelectionLocked();listRevision++;}
        changedLocked();
    }
    public synchronized void externalReturned(){pendingLaunch=null;publishLocked();}
    private void touchSelectedLocked(GameEntry g,boolean launched){
        UsageRecord old=usageRecords.get(g.gameId);long now=System.currentTimeMillis();
        usageRecords.put(g.gameId,new UsageRecord(g.gameId,g.uriString(),g.bestTitle(),
                old==null?0:old.lastPlayedAt,old==null?0:old.playCount,now,
                launched?now:old==null?0:old.lastLaunchAt,
                (old==null?0:old.launchRequestCount)+(launched?1:0)));
        userVersion++;
    }
    public synchronized void setRomTreeUri(Uri u){romTreeUri=u;romDirectoryEpoch++;romAccess=u==null?DirectoryHealth.State.NOT_CONFIGURED:DirectoryHealth.State.UNCHECKED;storageAvailable=false;changedLocked();}
    public synchronized void setBackupTreeUri(Uri u){backupTreeUri=u;saveDirectoryEpoch++;saveAccess=u==null?DirectoryHealth.State.NOT_CONFIGURED:DirectoryHealth.State.UNCHECKED;changedLocked();}
    /** Serialize the directory commit with ordinary state writes, then publish its readback. */
    void commitDirectory(Uri uri,boolean saves,Runnable accepted,Runnable failed){
        writer.execute(()->{
            boolean ok=false;
            synchronized(this){
                String key=saves?"backup_tree_uri":"rom_tree_uri";
                if(stateReady&&uiPrefs.edit().putString(key,uri.toString()).commit()
                        &&uri.toString().equals(uiPrefs.getString(key,""))){
                    if(saves){backupTreeUri=uri;saveDirectoryEpoch++;saveAccess=DirectoryHealth.State.UNCHECKED;}else{romTreeUri=uri;romDirectoryEpoch++;romAccess=DirectoryHealth.State.UNCHECKED;storageAvailable=false;}
                    stateVersion++;publishLocked();scheduleWriteLocked(400);ok=true;
                }
            }
            main.post(ok?()->{DirectoryHealth.get(context).recheck("DIRECTORY_COMMIT");accepted.run();}:failed);
        });
    }
    public synchronized void setScanState(String s){scanState=s;publishLocked();}
    public synchronized void markLibraryUnavailable(String message){
        // Restore has ended. Keep any cached selection and user data while showing an actionable empty/error state.
        scanState=message;libraryReady=true;storageAvailable=false;publishLocked();
    }
    public synchronized void setDrasticState(String s){drasticState=s;publishLocked();}
    public synchronized void setCurrentFocusDisplay(int d){
        if(d==currentFocusDisplay)return;currentFocusDisplay=d;publishLocked();
    }
    public synchronized void markTopActivityCreated(){topDisplayState="ACTIVE_ON_DISPLAY_2";publishLocked();}
    public synchronized void markTopActivityDestroyed(){topDisplayState="DESTROYED";publishLocked();}
    public synchronized void markTopLaunchFailed(String s){topDisplayState=s;publishLocked();}
    public synchronized boolean flush(){if(stateReady)scheduleWriteLocked(0);return true;}
    private void changedLocked(){stateVersion++;publishLocked();if(stateReady)scheduleWriteLocked(400);}
    private void publishLocked(){
        revision++;currentSnapshot=new Snapshot(this);
        if(!deliveryPosted){deliveryPosted=true;main.post(delivery);}
    }
    private void deliverPending(){
        if(Looper.myLooper()!=Looper.getMainLooper())return;
        Snapshot s;synchronized(this){deliveryPosted=false;s=currentSnapshot;}main.removeCallbacks(delivery);
        if(s.revision<=deliveredRevision)return;deliveredRevision=s.revision;deliver(s);
    }
    private void deliver(Snapshot s){
        for(Listener l:listeners)if(l instanceof BottomHomeActivity)l.onShellStateChanged(s);
        for(Listener l:listeners)if(!(l instanceof BottomHomeActivity))l.onShellStateChanged(s);
    }
    private void scheduleWriteLocked(long ms){
        if(pendingWrite!=null)pendingWrite.cancel(false);
        pendingWrite=writer.schedule(this::writeCurrent,ms,TimeUnit.MILLISECONDS);
    }
    private static final class Persisted {
        long state,user,mapping;String category,id,page,returnPage,settingsReturn,lastLaunch,rom,backup;
        int index,sort,home,setting,detail,filter,jump;
        Map<Category,String> selections;Set<String> favorites;List<UsageRecord> usage;List<RomMapping> mappings;
        String folder,genre,typeReturn,libraryPlaces;boolean typesOverview;SearchState search;Map<String,String> bookmarks;int group,detailTab;
    }
    private Persisted captureLocked(boolean export){
        Persisted p=new Persisted();p.state=stateVersion;p.user=userVersion;p.mapping=mappingVersion;
        rememberPlace();p.libraryPlaces=libraryPlaces.toString();p.typesOverview=typesOverview;p.category=category.name();p.id=selectedGameId;p.page=page.name();p.returnPage=returnPage.name();p.lastLaunch=lastLaunchId;
        if(page==Page.TASKS)p.page=taskReturn.name();
        p.settingsReturn=settingsReturn.name();p.detail=detailOffset;p.filter=filterIndex;p.jump=jumpPage;
        p.rom=uriString(romTreeUri);p.backup=uriString(backupTreeUri);p.index=selectedIndex;p.sort=sortMode;p.home=homeIndex;p.setting=settingsIndex;
        p.selections=new EnumMap<>(categorySelections);
        p.typeReturn=typeReturn==null?"":typeReturn.toString();p.search=searchStateLocked();p.folder=folderId;p.genre=genreFilter;p.bookmarks=new HashMap<>(bookmarks);p.group=settingsGroup;p.detailTab=detailTab;
        if(export||p.user>committedUser){p.favorites=favoriteIds;p.usage=new ArrayList<>(usageRecords.values());}
        if(mappingReady&&(export||p.mapping>committedMappings))p.mappings=new ArrayList<>(romMappings.values());
        return p;
    }
    private void writeCurrent(){
        Persisted p;long started=SystemClock.uptimeMillis();
        synchronized(this){if(!stateReady)return;p=captureLocked(false);}
        try{
            if(p.mappings!=null){
                byte[] bytes=mappingsJson(p.mappings).toString().getBytes(StandardCharsets.UTF_8);
                FileOutputStream out=null;
                try{out=mappingFile.startWrite();out.write(bytes);mappingFile.finishWrite(out);}
                catch(Exception e){if(out!=null)mappingFile.failWrite(out);throw e;}
                synchronized(this){committedMappings=p.mapping;}
            }
            if(p.usage!=null){
                JSONArray u=new JSONArray();for(UsageRecord r:p.usage)u.put(usageToJson(r));
                if(userPrefs==null)userPrefs=context.getSharedPreferences("rgds_shell_user_v3",0);
                JSONArray favorites=new JSONArray(p.favorites);
                if(!userPrefs.edit().putBoolean("initialized",true).putString("favorite_ids",favorites.toString())
                        .putString("usage_records",u.toString()).commit())throw new java.io.IOException("User preferences commit failed");
                UserReadCache.save(context.getFilesDir(),favorites,u);UserReadCache.saveRecords(context.getFilesDir(),p.favorites,p.usage);
                synchronized(this){committedUser=p.user;}
            }
            if(p.state>committedState){
                SharedPreferences.Editor e=uiPrefs.edit().putInt("schema",3).putString("category",p.category)
                    .putString("selected_game_id",p.id).putInt("selected_index",p.index).putInt("scroll_position",p.index/6*6)
                    .putInt("sort_mode",p.sort).putString("page",p.page).putString("return_page",p.returnPage)
                    .putString("settings_return",p.settingsReturn).putInt("detail_offset",p.detail)
                    .putInt("filter_index",p.filter).putInt("jump_page",p.jump)
                    .putString("setting_id",SettingsModel.entry(p.group,p.setting).id.name()).putInt("home_index",p.home).putInt("settings_index",p.setting).putString("last_launch_id",p.lastLaunch)
                    .putString("rom_tree_uri",p.rom).putString("backup_tree_uri",p.backup);
                e.putString("library_places_v7",p.libraryPlaces).putBoolean("types_overview_v7",p.typesOverview);
                e.putString("local_search_v5",p.search.json()).putString("type_return_v6",p.typeReturn);
                e.putInt("genre_schema",GenreTaxonomy.VERSION).putString("folder_id",p.folder).putString("genre_filter",p.genre).putString("bookmarks_v4",new JSONObject(p.bookmarks).toString()).putInt("settings_group",p.group).putInt("detail_tab",p.detailTab);
                for(Map.Entry<Category,String> x:p.selections.entrySet())e.putString("selection_"+x.getKey().name(),x.getValue());
                if(!e.commit())throw new java.io.IOException("UI preferences commit failed");
                synchronized(this){committedState=p.state;}
            }
            { if(PerfTrace.isEnabled()) Log.i("TwinGridPerf","PERSIST version="+p.state+" mappings="+(p.mappings==null?0:p.mappings.size())+
                    " elapsed="+(SystemClock.uptimeMillis()-started)+" lockEncoding=false"); }
        }catch(Exception e){Log.e(TAG,"PERSIST_FAILED retained previous atomic data",e);}
    }
    public String exportJson(){
        Persisted p;synchronized(this){if(!mappingReady)throw new IllegalStateException(UiStrings.msg("ui_8a6a3b0fdfd9"));p=captureLocked(true);}
        try{
            JSONObject root=new JSONObject().put("schemaVersion",3).put("applicationId","com.rgds.ultimate.shell")
                    .put("exportedAt",System.currentTimeMillis()).put("statistics","accepted launch requests; legacy confirmed records preserved separately");
            root.put("settings",new JSONObject().put("category",p.category).put("page",p.page).put("selectedGameId",p.id)
                .put("selectedIndex",p.index).put("scrollPosition",p.index/6*6).put("sortMode",p.sort)
                .put("romTreeUri",p.rom).put("backupTreeUri",p.backup));
            Map<String,RomMapping> mapping=new HashMap<>();for(RomMapping m:p.mappings)mapping.put(m.gameId,m);
            JSONArray f=new JSONArray();for(String id:p.favorites){RomMapping m=mapping.get(id);
                f.put(new JSONObject().put("gameId",id).put("uri",m==null?"":m.uri).put("title",m==null?"":m.title));}
            JSONArray u=new JSONArray();for(UsageRecord r:p.usage)u.put(usageToJson(r));
            return root.put("favorites",f).put("recentAndSelection",u).put("romUriMappings",mappingsJson(p.mappings)).toString(2);
        }catch(JSONException e){throw new IllegalStateException(e);}
    }
    private static JSONArray mappingsJson(List<RomMapping> list)throws JSONException{
        JSONArray a=new JSONArray();for(RomMapping r:list)a.put(new JSONObject().put("gameId",r.gameId).put("uri",r.uri).put("title",r.title));return a;
    }
    private static JSONObject usageToJson(UsageRecord r)throws JSONException{
        return new JSONObject().put("gameId",r.gameId).put("uri",r.uri).put("title",r.title)
            .put("lastPlayedAt",r.lastPlayedAt).put("playCount",r.playCount).put("lastSelectedAt",r.lastSelectedAt)
            .put("lastLaunchAt",r.lastLaunchAt).put("launchRequestCount",r.launchRequestCount);
    }
    private static Uri parseUri(String s){return s==null||s.isEmpty()?null:Uri.parse(s);}
    private static String uriString(Uri u){return u==null?"":u.toString();}
}
