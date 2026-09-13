package com.rgds.ultimate.shell;

import android.content.Context;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.OperationCanceledException;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.util.AtomicFile;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Read-only SAF index, bounded metadata cache, and serialized cancellable scans. */
final class RomLibrary {
    private static RomLibrary instance;
    private final Context context;
    private final ShellStateRepository repository;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r -> new Thread(()->{
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);r.run();
    },"rom-reader"));
    private final AtomicFile cache;
    private final RomCache binaryCache;
    private final AtomicFile directoriesFile;
    private volatile DirectoryIndex directoryIndex;
    private boolean initialized;
    private volatile boolean scanning;
    private final java.util.concurrent.ThreadPoolExecutor launchWorker=new java.util.concurrent.ThreadPoolExecutor(1,1,0L,java.util.concurrent.TimeUnit.MILLISECONDS,new java.util.concurrent.ArrayBlockingQueue<>(1),r->new Thread(r,"rom-launch-check"));
    private volatile ScanProgress progress=new ScanProgress("IDLE",0,0,0,0,0);
    private volatile ScanReadEvidence readEvidence;
    JSONObject readEvidence(){try{return new JSONObject().put("current",readEvidence==null?new JSONObject():readEvidence.snapshot())
        .put("last",ScanReadEvidence.saved(context,"scan_read_last_v123.json")).put("lastFailure",ScanReadEvidence.saved(context,"scan_read_failure_v123.json"))
        .put("generation",generation).put("scanning",scanning).put("scanSignalCancelled",cancellation.isCanceled())
        .put("launchActiveWorkers",launchWorker.getActiveCount()).put("launchQueue",launchWorker.getQueue().size());}catch(Exception e){return new JSONObject();}}
    ScanProgress progress(){return progress;}
    void validating(){status("VALIDATING",generation,0,0,repository.allGamesCopy().size(),0);}
    void validationFailed(){status("FAILED",generation,0,0,repository.allGamesCopy().size(),1);}
    private void status(String phase,int token,int folders,int found,int available,int errors){
        if(!current(token))return;
        ScanReadEvidence e=readEvidence;boolean own=e!=null&&e.snapshot().optInt("session")==token;
        if(own)e.progress(folders,found,available);
        progress=new ScanProgress(phase,token,folders,found,available,errors,own?e.openedCount():0,own?e.parsedCount():0,own?e.cachedCount():0);
        SetupJourney.scan(context,progress);
        repository.setScanState(progress.title());
        PerfTrace.event("SCAN_PROGRESS "+progress.json());
    }
    boolean generationIs(int token){return current(token);}
    int generation(){return generation;}

    private volatile int generation;
    private volatile boolean censusComplete;
    private volatile boolean saveRefreshPending;
    private volatile Uri indexedTree;
    private CancellationSignal cancellation=new CancellationSignal();
    private RomLibrary(Context c) {
        context=c.getApplicationContext(); repository=ShellStateRepository.get(c);
        cache=new AtomicFile(new File(context.getFilesDir(),"rom_metadata_v1.json"));
        binaryCache=new RomCache(context.getFilesDir());
        directoriesFile=new AtomicFile(new File(context.getFilesDir(),"directory_index_v4.json"));
    }
    static synchronized RomLibrary get(Context c) { if(instance==null) instance=new RomLibrary(c);return instance; }
    synchronized void initialize() {
        if(initialized)return; initialized=true;
        DirectoryHealth.get(context).initialize();
        repository.whenReady(() -> {
            ShellStateRepository.Snapshot state=repository.snapshot();
            final int token=++generation;
            worker.execute(() -> {
                if(!current(token))return;
                boolean loaded=loadCache(state.romTreeUri,token);
                if(!current(token))return;
                if(loaded) {
                    // All/recent/favorites can launch a cached URI once the authorized root is verified.
                    // Folder mode still waits for its actual parent/child index before enabling controls.
                    DirectoryHealth.get(context).recheck("CACHE_RESTORED");
                    try {
                        DirectoryIndex d=DirectoryIndex.parse(new JSONObject(new String(directoriesFile.readFully(),StandardCharsets.UTF_8)));
                        if(!d.tree.equals(state.romTreeUri.toString()))throw new java.io.IOException("Different root");
                        directoryIndex=d;repository.setDirectoryIndex(d,()->current(token));
                    } catch(Exception e) {
                        { if(PerfTrace.isEnabled()) Log.i("TwinGrid","DIRECTORY_MIGRATION bounded=true reuseROMHeaders=true"); }
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(this::scan);
                    }
                    // Publish cached metadata first; root/save validation follows on this worker.
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> refreshSaves());
                } else {
                    if(state.romTreeUri==null)repository.markLibraryUnavailable(UiStrings.msg("ui_202126859f48"));
                    else new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{if(current(token))scan();});
                }
            });
        });
    }
    boolean isScanning() { return scanning; }
    DirectoryIndex directories(){return directoryIndex;}
    private LaunchCheck launchCheck;
    private LaunchCheck launchCheck(){if(launchCheck==null)launchCheck=new LaunchCheck(context,launchWorker);return launchCheck;}
    void cancelLaunchCheck(){if(launchCheck!=null)launchCheck.cancel();}
    void checkReadable(GameEntry game,String source,int display,int task,java.util.function.Consumer<DrasticLauncher.Plan> success,java.util.function.Consumer<Exception> failure) {
        cancelLaunchCheck();String id=LaunchDiagnostic.begin(context,game,source,display,task);checkLaunch(game,id,null,success,failure);
    }
    void verifyLaunch(DrasticLauncher.Plan plan,java.util.function.Consumer<DrasticLauncher.Plan> success,java.util.function.Consumer<Exception> failure){checkLaunch(plan.game,plan.requestId,plan,success,failure);}
    private void checkLaunch(GameEntry game,String id,DrasticLauncher.Plan existing,java.util.function.Consumer<DrasticLauncher.Plan> success,java.util.function.Consumer<Exception> failure){
        launchCheck().start(id,cancel->{
            LaunchFile file=LaunchFile.read(context,game,cancel);cancel.throwIfCanceled();
            DrasticLauncher.Plan result;
            if(existing==null){result=DrasticLauncher.prepare(context,game,id);result.game=game;result.file=file;}
            else {
                if(!existing.file.same(file))throw new LaunchFailure(LaunchFailure.FILE_CHANGED);
                try{if(!existing.profile.key.equals(ReceiverProfile.inspect(context).key))throw new LaunchFailure(LaunchFailure.ENVIRONMENT_CHANGED);}
                catch(Exception e){throw new LaunchFailure(LaunchFailure.ENVIRONMENT_CHANGED);}
                result=existing;
            }
            cancel.throwIfCanceled();return result;
        },plan->{
            if(!LaunchDiagnostic.active(context,id))return;
            try{org.json.JSONObject d=LaunchDiagnostic.read(context);d.put("fileIdentity",plan.file.json()).put("fileRecheckedBeforeDispatch",existing!=null);LaunchDiagnostic.save(context,d);}
            catch(Exception ignored){}
            success.accept(plan);
        },failure);
    }
    void exportData(Uri uri,java.util.function.Consumer<String> done) {
        worker.execute(() -> {
            String message;
            try(java.io.OutputStream out=context.getContentResolver().openOutputStream(uri,"wt")) {
                if(out==null)throw new java.io.IOException("Null output stream");
                out.write(repository.exportJson().getBytes(StandardCharsets.UTF_8));out.flush();
                message=UiStrings.msg("ui_110531ee537b");
                { if(PerfTrace.isEnabled()) Log.i("TwinGrid","EXPORT_SUCCESS uri="+uri+" thread="+Thread.currentThread().getName()); }
            } catch(Exception e){message=UiStrings.msg("ui_c55e9d64e5b5")+e.getClass().getSimpleName();Log.e("TwinGrid","EXPORT_FAILED",e);}
            final String result=message;
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> done.accept(result));
        });
    }
    synchronized void cancel() {
        ++generation; cancellation.cancel(); scanning=false;
        ScanProgress old=progress;
        // Invalidate callbacks with generation, but keep the cancelled session and its observed counters.
        progress=new ScanProgress("CANCELED",old.session,old.folders,old.discovered,old.available,old.errors,old.opened,old.parsed,old.cached);
        SetupJourney.scan(context,progress);repository.setScanState(progress.title());PerfTrace.event("SCAN_PROGRESS "+progress.json());
        { if(PerfTrace.isEnabled()) Log.i("TwinGrid","SCAN_CANCEL"); }
    }
    synchronized void scan() {
        scan(false);
    }
    synchronized void scanUncached(){scan(true);}
    private synchronized void scan(boolean uncached) {
        if(scanning&&repository.snapshot().romTreeUri!=null&&repository.snapshot().romTreeUri.equals(indexedTree))return;
        cancellation.cancel(); cancellation=new CancellationSignal();
        final CancellationSignal signal=cancellation;
        final int token=++generation;
        ShellStateRepository.Snapshot s=repository.snapshot();
        if(s.romTreeUri==null) {scanning=false;repository.setScanState(UiStrings.msg("ui_a730a8f0d387"));return;}
        if(!s.romTreeUri.equals(indexedTree)){directoryIndex=null;repository.setGames(new ArrayList<>(),JourneyGuide.s(14),()->current(token));}
        indexedTree=s.romTreeUri;censusComplete=false;
        scanning=true;
        status("QUEUED",token,0,0,s.romTreeUri.equals(indexedTree)?repository.allGamesCopy().size():0,0);
        worker.execute(() -> read(token,signal,s.romTreeUri,s.backupTreeUri,uncached));
    }
    private boolean current(int token) { return generation==token; }
    private void check(int token) { if(!current(token))throw new OperationCanceledException(); }
    private void read(int token,CancellationSignal signal,Uri romTree,Uri ignoredSaveTree,boolean uncached) {
        ScanReadEvidence evidence=new ScanReadEvidence(context,token,romTree);readEvidence=evidence;
        String readStage="ROOT_GRANT",terminal="FAILED";
        int errors=0,folders=0,found=0;long lastBatch=0;
        List<GameEntry> previous=new ArrayList<>();
        // Only keep entries from this actual tree, never an asynchronously cleared old root.
        for(GameEntry g:repository.allGamesCopy())if(g.uri.toString().startsWith(romTree.toString()+"/document/"))previous.add(g);
        Map<String,GameEntry> prior=new HashMap<>(),merged=new LinkedHashMap<>();
        for(GameEntry g:previous){prior.put(g.uriString(),g);merged.put(g.uriString(),g);}
        List<GameEntry> result=new ArrayList<>();boolean complete=true;
        try {
            check(token);status("DISCOVERING",token,0,0,merged.size(),0);requireReadableRoot(romTree,signal);check(token);DirectoryHealth.get(context).recheck("SCAN_ROOT_VERIFIED");
            String rootDoc=DocumentsContract.getTreeDocumentId(romTree),rootName="NDS";
            readStage="ROOT_QUERY";
            try(Cursor c=context.getContentResolver().query(DocumentsContract.buildDocumentUriUsingTree(romTree,rootDoc),new String[]{"_display_name"},null,null,null,signal)){if(c!=null&&c.moveToFirst())rootName=c.getString(0);}
            DirectoryIndex dirs=new DirectoryIndex(romTree.toString(),rootDoc,rootName);
            ArrayDeque<String> queue=new ArrayDeque<>();Set<String> visited=new HashSet<>();queue.add(rootDoc);
            while(!queue.isEmpty()) {
                check(token);String id=queue.removeFirst();if(!visited.add(id))continue;
                if(visited.size()>20000)throw new java.io.IOException("Directory limit");
                List<Document> children;
                try{readStage="CHILDREN_QUERY";children=children(romTree,id,signal);folders++;}
                catch(OperationCanceledException e){throw e;}
                catch(Exception e){evidence.failed("CHILDREN_QUERY",DocumentsContract.buildDocumentUriUsingTree(romTree,id),-1,-1,e,signal.isCanceled());complete=false;errors++;continue;}
                for(Document d:children) {
                    check(token);String parent=DirectoryIndex.key(romTree.toString(),id);
                    if(d.directory){dirs.add(d.id,parent,d.name);queue.add(d.id);continue;}
                    if(!d.name.toLowerCase(Locale.ROOT).endsWith(".nds")){
                        if(dirs.pictures.size()<20000&&d.name.toLowerCase(Locale.ROOT).matches(".*\\.(png|jpg|jpeg|webp)$"))dirs.pictures.add(new DirectoryIndex.Picture(parent,d.name,d.uri.toString()));continue;
                    }
                    found++;if(found==1)status("READING",token,folders,found,merged.size(),errors);
                    GameEntry old=prior.get(d.uri.toString()),game;
                    if(!uncached&&old!=null&&d.size>0&&d.modified>0&&old.romBytes==d.size&&old.romModifiedAt==d.modified){game=old;evidence.cached();}
                    else {
                        try {
                            readStage="FILE_OPEN";
                            ParcelFileDescriptor fd=context.getContentResolver().openFileDescriptor(d.uri,"r",signal);
                            if(fd==null)throw new java.io.IOException("No file descriptor");
                            evidence.opened();readStage="HEADER_PARSE";
                            NdsHeaderParser.Metadata m;RomReadFile identity;
                            try(ParcelFileDescriptor.AutoCloseInputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){
                                identity=RomReadFile.inspect(context,d.uri,d.size,d.modified,fd,in.getChannel(),signal);
                                m=NdsHeaderParser.parse(in.getChannel(),identity.size,()->{check(token);signal.throwIfCanceled();});
                            }finally{evidence.closed();}
                            check(token);signal.throwIfCanceled();
                            readStage="ENTRY_METADATA";
                            game=entry(d,m,identity);
                            evidence.parsed();
                        }catch(OperationCanceledException e){throw e;}catch(Exception e){evidence.progress(folders,found,merged.size());evidence.failed(readStage,d.uri,d.size,d.modified,e,signal.isCanceled());errors++;complete=false;continue;}
                    }
                    dirs.gameParents.put(game.gameId,parent);result.add(game);merged.put(game.uriString(),game);
                    long now=android.os.SystemClock.uptimeMillis();
                    if(result.size()==1||now-lastBatch>=300){
                        status("READING",token,folders,found,merged.size(),errors);
                        repository.setGamesPartial(new ArrayList<>(merged.values()),progress.title(),()->current(token));lastBatch=now;
                    }
                }
                if(android.os.SystemClock.uptimeMillis()-lastBatch>=300){status("DISCOVERING",token,folders,found,merged.size(),errors);lastBatch=android.os.SystemClock.uptimeMillis();}
            }
            check(token);censusComplete=complete;
            List<GameEntry> published=complete?result:new ArrayList<>(merged.values());
            status("SAVES",token,folders,found,published.size(),errors);
            repository.setGames(published,progress.title(),()->current(token));
            // Save lookup follows visible ROM batches and uses the newest selected save folder.
            readStage="SAVE_LOOKUP";Uri saveTree=repository.snapshot().backupTreeUri;SaveIndex saves=readSaves(saveTree,signal);
            Map<String,Integer> counts=new HashMap<>();for(GameEntry g:published)counts.put(base(g.fileName),counts.getOrDefault(base(g.fileName),0)+1);
            List<GameEntry> withSaves=new ArrayList<>();for(GameEntry g:published)withSaves.add(matchSave(g,saves,counts,complete));
            check(token);
            final Uri expectedSave=saveTree;
            repository.setGames(withSaves,JourneyGuide.s(complete?17:18),()->current(token)&&java.util.Objects.equals(expectedSave,repository.snapshot().backupTreeUri));
            readStage="DERIVED_CACHE";if(complete){saveCache(romTree,withSaves);dirs.finish();
                FileOutputStream out=null;
                try{out=directoriesFile.startWrite();out.write(dirs.json().toString().getBytes(StandardCharsets.UTF_8));directoriesFile.finishWrite(out);}
                catch(Exception e){if(out!=null)directoriesFile.failWrite(out);throw e;}
                check(token);directoryIndex=dirs;repository.setDirectoryIndex(dirs,()->current(token));
            }
            terminal=complete?"COMPLETE":"PARTIAL";status(terminal,token,folders,found,published.size(),errors);
        }catch(OperationCanceledException e){terminal="CANCELED";PerfTrace.event("SCAN_CANCELED_WORKER session="+token);}
        catch(Exception e){evidence.failed(readStage,romTree,-1,-1,e,signal.isCanceled());if(current(token)){DirectoryHealth.get(context).recheck("SCAN_FAILED");status("FAILED",token,folders,found,merged.size(),errors+1);}android.util.Log.w("TwinGrid","SCAN_FAILED "+e.getClass().getSimpleName());}
        finally {evidence.finish(context,terminal,signal.isCanceled());if(current(token)){scanning=false;MetadataManager.get(context).scanSettled();if(saveRefreshPending){saveRefreshPending=false;refreshSaves();}}}
    }
    static GameEntry entry(Document d,NdsHeaderParser.Metadata m,RomReadFile identity){
        return new GameEntry(UUID.nameUUIDFromBytes(d.uri.toString().getBytes(StandardCharsets.UTF_8)).toString(),d.uri,d.name,d.name.substring(0,d.name.length()-4),m.internalTitle,m.bannerTitle,m.gameCode,m.makerCode,identity.size,identity.modified,GameEntry.SaveState.SCANNING,0,0,m.icon,m.titles,m.warning);
    }
    void showReadDiagnostics(android.app.Activity owner){
        if(owner.isFinishing()||owner.isDestroyed()||!owner.hasWindowFocus()||ShellDialogBuilder.hasOpenDialog())return;
        boolean zh=LocaleSettings.ui().equals("zh");JSONObject current=readEvidence==null?new JSONObject():readEvidence.snapshot();
        JSONObject historical=ScanReadEvidence.saved(context,"scan_read_failure_v123.json");
        StringBuilder text=new StringBuilder(progress.panel());
        JSONObject source=current.optInt("failures")>0?current:historical;
        if(source.length()>0){text.append(zh?"\n\n故障记录：":"\n\nFailure record: ").append(source==current?(zh?"当前":"current"):(zh?"历史，非本次结果":"historical, separate from current results"))
            .append(" · ").append(source.optString("version")).append(" · #").append(source.optInt("session"));
            JSONArray rows=source.optJSONArray("samples");if(rows!=null)for(int i=0;i<Math.min(3,rows.length());i++){JSONObject row=rows.optJSONObject(i);if(row!=null)text.append("\n").append(row.optString("stage")).append(" / ").append(row.optString("code")).append(": ").append(ScanReadEvidence.description(row.optString("code"),row.optString("errnoName"),zh));}
        }else text.append(zh?"\n\n尚无可用的具体失败记录。":"\n\nNo specific failure evidence is available.");
        new ShellDialogBuilder(owner).setTitle(zh?"读取详情":"Read details").setMessage(text.toString())
            .setPositiveButton(zh?"重新实际读取":"Read files again",(d,w)->scanUncached())
            .setNegativeButton(zh?"返回":"Back",null).show();
    }
    private SaveIndex readSaves(Uri tree,CancellationSignal signal) {
        SaveIndex index=new SaveIndex();
        if(tree==null)return index;
        try {
            requireReadGrant(tree);
            for(Document d:children(tree,DocumentsContract.getTreeDocumentId(tree),signal)) {
                if(d.directory||!d.name.toLowerCase(Locale.ROOT).endsWith(".dsv"))continue;
                String name=d.name.substring(0,d.name.length()-4).toLowerCase(Locale.ROOT);
                if(!index.entries.containsKey(name))index.entries.put(name,new ArrayList<>());
                index.entries.get(name).add(d);
            }
            index.readable=true;
        } catch(OperationCanceledException e){throw e;}
        catch(Exception e){Log.w("TwinGrid","SAVE_DIRECTORY_UNREADABLE "+e);}
        JourneyGuide.saveAccess(context,tree,index.readable);
        return index;
    }
    private GameEntry matchSave(GameEntry game,SaveIndex saves,Map<String,Integer> counts,boolean complete) {
        if(!saves.readable)return game;
        if(!complete)return game.withSave(GameEntry.SaveState.SCANNING,0,0);
        String basename=base(game.fileName);List<Document> matches=saves.entries.get(basename);
        if((counts.containsKey(basename)&&counts.get(basename)>1)||(matches!=null&&matches.size()>1))
            return game.withSave(GameEntry.SaveState.AMBIGUOUS,0,0);
        if(matches==null||matches.isEmpty())return game.withSave(GameEntry.SaveState.NOT_FOUND,0,0);
        Document save=matches.get(0);
        return game.withSave(GameEntry.SaveState.FOUND,save.size,save.modified);
    }
    void refreshSaves() {
        if(scanning){saveRefreshPending=true;return;}
        final int token=generation;
        worker.execute(() -> refreshSavesOnWorker(token));
    }
    private void refreshSavesOnWorker(int token) {
        if(!current(token))return;
        ShellStateRepository.Snapshot snapshot=repository.snapshot();
        List<GameEntry> games=repository.allGamesCopy();
        try {
            DirectoryHealth.get(context).recheck("SAVE_REFRESH");
            SaveIndex saves=readSaves(snapshot.backupTreeUri,new CancellationSignal());
            if(!saves.readable)return; // Keep prior save evidence while access is uncertain.
            Map<String,Integer> counts=new HashMap<>();
            for(GameEntry g:games)counts.put(base(g.fileName),counts.containsKey(base(g.fileName))?counts.get(base(g.fileName))+1:1);
            List<GameEntry> updated=new ArrayList<>();
            for(GameEntry g:games)updated.add(matchSave(g,saves,counts,censusComplete));
            if(!current(token)||!java.util.Objects.equals(snapshot.backupTreeUri,repository.snapshot().backupTreeUri))return;
            repository.setGames(updated,UiStrings.msg("ui_32c7a9c13309")+games.size()+UiStrings.msg("ui_e8fb2f295e3b")+found(updated)+
                    (saves.readable?UiStrings.msg("ui_da215a7b74f2"):UiStrings.msg("ui_d6f0778c2741")),()->current(token)&&java.util.Objects.equals(snapshot.backupTreeUri,repository.snapshot().backupTreeUri));
            { if(PerfTrace.isEnabled()) Log.i("TwinGrid","CACHE_RESTORE_OR_SAVE_REFRESH games="+games.size()+" saveFound="+found(updated)+" fullRomScan=false"); }
        } catch(Exception e) {
            DirectoryHealth.get(context).recheck("SAVE_REFRESH_FAILED");
            Log.w("TwinGrid","SAVE_REFRESH_FAILED "+e.getClass().getSimpleName());
        }
    }
    private void requireReadableRoot(Uri tree)throws java.io.IOException {
        requireReadableRoot(tree,new CancellationSignal());
    }
    private void requireReadableRoot(Uri tree,CancellationSignal signal)throws java.io.IOException {
        signal.throwIfCanceled();
        requireReadGrant(tree);Uri root=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
        try(Cursor c=context.getContentResolver().query(root,new String[]{"document_id"},null,null,null,signal)){if(c==null||!c.moveToFirst())throw new java.io.IOException("ROM root unavailable");signal.throwIfCanceled();}
    }
    private boolean loadCache(Uri tree,int token) {
        if(tree==null)return false;
        long started=android.os.SystemClock.uptimeMillis();
        if(binaryCache.exists()){
            try{
                List<GameEntry> games=binaryCache.read(tree);
                if(current(token)){
                    indexedTree=tree;censusComplete=true;
                    repository.setGames(games,UiStrings.msg("ui_32c7a9c13309")+games.size()+UiStrings.msg("ui_48a5e7b07125"),()->current(token));
                }
                { if(PerfTrace.isEnabled()) Log.i("TwinGrid","CACHE_LOADED count="+games.size()+" format=binary elapsedMs="+(android.os.SystemClock.uptimeMillis()-started)+" fullRomScan=false"); }
                return true;
            }catch(Exception e){Log.w("TwinGrid","BINARY_CACHE_INVALID retained V1 fallback",e);}
        }
        if(!cache.getBaseFile().exists())return false;
        try {
            if(cache.getBaseFile().length()>16*1024*1024)throw new java.io.IOException("Metadata cache too large");
            JSONObject root=new JSONObject(new String(cache.readFully(),StandardCharsets.UTF_8));
            if(root.optInt("schema")!=1||!tree.toString().equals(root.optString("tree")))return false;
            JSONArray rows=root.getJSONArray("games");List<GameEntry> games=new ArrayList<>();
            for(int i=0;i<rows.length();i++)games.add(GameEntry.fromJson(rows.getJSONObject(i)));
            if(current(token)) {
                indexedTree=tree;censusComplete=true;
                repository.setGames(games,UiStrings.msg("ui_32c7a9c13309")+games.size()+UiStrings.msg("ui_48a5e7b07125"),()->current(token));
            }
            { if(PerfTrace.isEnabled()) Log.i("TwinGrid","CACHE_LOADED count="+games.size()+" thread="+Thread.currentThread().getName()); }
            saveCache(tree,games);
            return true;
        } catch(Exception e){Log.w("TwinGrid","CACHE_INVALID_RESCAN_ALLOWED "+e);return false;}
    }
    private void saveCache(Uri tree,List<GameEntry> games) {
        try {
            binaryCache.write(tree,games);
            { if(PerfTrace.isEnabled()) Log.i("TwinGrid","CACHE_WRITE count="+games.size()+" format=binary derivedOnly=true"); }
        } catch(Exception e){Log.e("TwinGrid","CACHE_WRITE_FAILED",e);}
    }
    void requireReadGrant(Uri tree) throws java.io.IOException {
        if(DirectoryHealth.hasGrant(context,tree))return;
        throw new java.io.IOException("Persisted read grant missing");
    }
    List<Document> children(Uri tree,String id,CancellationSignal signal) throws java.io.IOException {
        signal.throwIfCanceled();
        Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,id);
        String[] projection={"document_id","_display_name","mime_type","_size","last_modified"};
        List<Document> result=new ArrayList<>();
        try(Cursor c=context.getContentResolver().query(children,projection,null,null,null,signal)) {
            if(c==null)throw new java.io.IOException("Null document cursor");
            while(c.moveToNext()){
                signal.throwIfCanceled();
                if(result.size()>=100000)throw RomReadFile.failure("CHILDREN_QUERY","DIRECTORY_ENTRY_LIMIT",100000,result.size()+1);
                result.add(new Document(id,c.getString(0),
                DocumentsContract.buildDocumentUriUsingTree(tree,c.getString(0)),c.getString(1),
                DocumentsContract.Document.MIME_TYPE_DIR.equals(c.getString(2)),c.isNull(3)?-1:c.getLong(3),c.isNull(4)?0:c.getLong(4)));
            }
        }
        signal.throwIfCanceled();
        return result;
    }
    private static String base(String name) {return name.substring(0,name.length()-4).toLowerCase(Locale.ROOT);}
    private static int found(List<GameEntry> games) {int n=0;for(GameEntry g:games)if(g.saveState==GameEntry.SaveState.FOUND)n++;return n;}
    private static class SaveIndex {boolean readable;final Map<String,List<Document>> entries=new HashMap<>();}
    static class Document {
        final String parent,id;final Uri uri;final String name;final boolean directory;final long size,modified;
        Document(String p,String i,Uri u,String n,boolean d,long s,long m){parent=p;id=i;uri=u;name=n;directory=d;size=s;modified=m;}
    }
}
