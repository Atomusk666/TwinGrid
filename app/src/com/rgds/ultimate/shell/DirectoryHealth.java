package com.rgds.ultimate.shell;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.os.storage.*;
import android.provider.DocumentsContract;
import org.json.*;
import java.util.Objects;
import java.util.concurrent.*;

/** Lightweight accessibility checks. Never scans, regrants, launches, or alters user data. */
final class DirectoryHealth {
    enum State { NOT_CONFIGURED, UNCHECKED, CHECKING, AVAILABLE, WAITING_STORAGE, GRANT_MISSING, DIRECTORY_MISSING, RETRYABLE_ERROR }
    private static DirectoryHealth instance;
    private final Context context;
    private final ShellStateRepository repository;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Slot rom=new Slot(false),save=new Slot(true);
    private final long created=SystemClock.elapsedRealtime();
    private final JSONArray events=new JSONArray();
    private boolean registered;
    private final class Slot {
        final boolean saves;
        final ExecutorService worker;
        Uri tree;long epoch,sequence,lastStarted;int attempt;boolean running,pending,timedOut;
        CancellationSignal signal;JSONObject last=new JSONObject();
        Slot(boolean s){saves=s;worker=Executors.newSingleThreadExecutor(r->new Thread(r,s?"save-access":"rom-access"));}
    }
    private DirectoryHealth(Context c){context=c.getApplicationContext();repository=ShellStateRepository.get(c);}
    static synchronized DirectoryHealth get(Context c){if(instance==null)instance=new DirectoryHealth(c);return instance;}
    void initialize(){main.post(()->{if(registered)return;registered=true;
        IntentFilter f=new IntentFilter();for(String a:new String[]{Intent.ACTION_MEDIA_MOUNTED,Intent.ACTION_MEDIA_UNMOUNTED,Intent.ACTION_MEDIA_REMOVED,Intent.ACTION_MEDIA_BAD_REMOVAL,Intent.ACTION_MEDIA_CHECKING})f.addAction(a);f.addDataScheme("file");
        context.registerReceiver(new BroadcastReceiver(){public void onReceive(Context c,Intent i){recheck("STORAGE_EVENT");}},f);
        if(Build.VERSION.SDK_INT>=30)context.getSystemService(StorageManager.class).registerStorageVolumeCallback(context.getMainExecutor(),new StorageManager.StorageVolumeCallback(){public void onStateChanged(StorageVolume v){recheck("VOLUME_STATE");}});
        repository.whenReady(()->recheck("CONFIG_RESTORED"));
    });}
    void recheck(String trigger){main.post(()->{request(rom,trigger,false);request(save,trigger,false);});}
    void retry(boolean saves){main.post(()->request(saves?save:rom,"USER_RETRY",true));}
    private void request(Slot slot,String trigger,boolean user){
        ShellStateRepository.Snapshot s=repository.snapshot();if(!s.stateReady)return;
        Uri tree=slot.saves?s.backupTreeUri:s.romTreeUri;long epoch=slot.saves?s.saveDirectoryEpoch:s.romDirectoryEpoch;
        boolean changed=!Objects.equals(tree,slot.tree)||epoch!=slot.epoch;
        if(changed){if(slot.signal!=null)slot.signal.cancel();slot.sequence++;slot.tree=tree;slot.epoch=epoch;slot.attempt=0;slot.pending=true;}
        if(tree==null){publish(slot,State.NOT_CONFIGURED);return;}
        if(slot.running){slot.pending|=changed;return;}
        long now=SystemClock.elapsedRealtime();
        if(!changed&&!user&&now-slot.lastStarted<2000)return;
        if(!"BOUNDED_RETRY".equals(trigger))slot.attempt=0;
        slot.running=true;slot.pending=false;slot.timedOut=false;slot.lastStarted=now;slot.attempt++;
        long sequence=++slot.sequence;CancellationSignal signal=new CancellationSignal();slot.signal=signal;
        State previous=slot.saves?s.saveAccess:s.romAccess;
        if(changed||previous!=State.AVAILABLE)publish(slot,State.CHECKING);
        JSONObject start=new JSONObject();try{start.put("trigger",trigger).put("treeHash",ScanReadEvidence.hash(tree)).put("epoch",epoch).put("sequence",sequence).put("attempt",slot.attempt).put("startedElapsed",now).put("pid",android.os.Process.myPid()).put("uid",android.os.Process.myUid());}catch(Exception ignored){}
        slot.last=start;event(slot,State.CHECKING,start);
        main.postDelayed(()->{if(slot.running&&valid(slot,tree,epoch,sequence)){slot.timedOut=true;signal.cancel();try{start.put("stage","TIMEOUT").put("timeoutMs",3000);}catch(Exception ignored){}publish(slot,State.RETRYABLE_ERROR);event(slot,State.RETRYABLE_ERROR,start);}},3000);
        slot.worker.execute(()->{
            JSONObject detail=new JSONObject();State result;
            try{result=probe(context,tree,signal,detail);
                if(result==State.AVAILABLE&&!slot.saves){
                    for(GameEntry g:repository.allGamesCopy())if(sameIdentity(tree,g.uri)){
                        detail.put("stage","ROM_HEADER_READ");
                        try(ParcelFileDescriptor fd=context.getContentResolver().openFileDescriptor(g.uri,"r",signal)){
                            if(fd==null)throw new java.io.IOException("NO_DESCRIPTOR");
                            // Own a duplicate so stream/channel and PFD have distinct lifetimes.
                            try(java.io.InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(fd.getFileDescriptor()))){
                                byte[] header=new byte[512];int count=0;while(count<header.length){signal.throwIfCanceled();int n=in.read(header,count,header.length-count);if(n<=0)throw new java.io.IOException("SHORT_READ");count+=n;}
                                detail.put("sampleHeaderBytes",count).put("sampleDocumentHash",ScanReadEvidence.hash(g.uri)).put("sampleHeaderSHA256",LaunchDiagnostic.hash(header));
                            }
                        }
                        break;
                    }
                }
            }catch(Exception e){result=State.RETRYABLE_ERROR;try{detail.put("error",ScanReadEvidence.reason(detail.optString("stage","PROBE"),e,signal.isCanceled()));}catch(Exception ignored){}}
            final State outcome=result;
            main.post(()->{slot.running=false;
                if(valid(slot,tree,epoch,sequence)&&!slot.timedOut){
                    try{java.util.Iterator<String> keys=detail.keys();while(keys.hasNext()){String k=keys.next();start.put(k,detail.get(k));}start.put("endedElapsed",SystemClock.elapsedRealtime());}catch(Exception ignored){}
                    publish(slot,outcome);event(slot,outcome,start);
                }
                if(slot.pending){request(slot,"CONFIG_CHANGED",true);return;}
                State state=slot.saves?repository.snapshot().saveAccess:repository.snapshot().romAccess;
                if(valid(slot,tree,epoch,sequence)&&retryable(state)&&slot.attempt<3){
                    long delay=slot.attempt==1?2500:6000;
                    main.postDelayed(()->{if(valid(slot,tree,epoch,sequence))request(slot,"BOUNDED_RETRY",false);},delay);
                }
            });
        });
    }
    private boolean valid(Slot slot,Uri tree,long epoch,long sequence){ShellStateRepository.Snapshot s=repository.snapshot();return slot.sequence==sequence&&Objects.equals(tree,slot.saves?s.backupTreeUri:s.romTreeUri)&&epoch==(slot.saves?s.saveDirectoryEpoch:s.romDirectoryEpoch);}
    private void publish(Slot slot,State state){repository.setDirectoryAccess(slot.saves,slot.tree,slot.epoch,state);}
    static boolean retryable(State s){return s==State.RETRYABLE_ERROR||s==State.WAITING_STORAGE;}
    private synchronized void event(Slot slot,State state,JSONObject info){try{
        JSONObject e=new JSONObject(info.toString()).put("saves",slot.saves).put("state",state.name());
        if(events.length()>=24)events.remove(0);events.put(e);slot.last=e;
        // Persist bounded, path-redacted first/current diagnostics, never transient availability as configuration.
        ScanReadEvidence.persist(context,"directory_access_last.json",snapshot().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }catch(Exception ignored){}}
    synchronized JSONObject snapshot(){try{return new JSONObject().put("processCreatedElapsed",created).put("rom",new JSONObject(rom.last.toString())).put("save",new JSONObject(save.last.toString())).put("events",new JSONArray(events.toString())).put("readOnly",true).put("automaticScans",0);}catch(Exception e){return new JSONObject();}}
    static boolean sameIdentity(Uri a,Uri b){try{return a!=null&&b!=null&&Objects.equals(a.getAuthority(),b.getAuthority())&&DocumentsContract.getTreeDocumentId(a).equals(DocumentsContract.getTreeDocumentId(b));}catch(Exception e){return Objects.equals(a,b);}}
    static boolean hasGrant(Context c,Uri tree){
        if(tree==null)return false;
        for(UriPermission p:c.getContentResolver().getPersistedUriPermissions())if(p.isReadPermission()&&sameIdentity(p.getUri(),tree))return true;
        // A broader persisted tree may cover this document. Ask the provider for ancestry, never guess a path prefix.
        for(UriPermission p:c.getContentResolver().getPersistedUriPermissions())if(p.isReadPermission()&&Objects.equals(p.getUri().getAuthority(),tree.getAuthority()))try{
            Uri parent=DocumentsContract.buildDocumentUriUsingTree(p.getUri(),DocumentsContract.getTreeDocumentId(p.getUri()));
            Uri child=DocumentsContract.buildDocumentUriUsingTree(p.getUri(),DocumentsContract.getTreeDocumentId(tree));
            if(DocumentsContract.isChildDocument(c.getContentResolver(),parent,child))return true;
        }catch(Exception ignored){}
        return false;
    }
    static State probe(Context c,Uri tree,CancellationSignal signal,JSONObject d){
        String stage="CONFIG";try{
            if(tree==null)return State.NOT_CONFIGURED;signal.throwIfCanceled();
            UserManager user=c.getSystemService(UserManager.class);boolean unlocked=user==null||user.isUserUnlocked();d.put("userUnlocked",unlocked);
            if(!unlocked)return State.WAITING_STORAGE;
            stage="PERSISTED_READ";boolean grant=hasGrant(c,tree);d.put("persistedRead",grant);if(!grant)return State.GRANT_MISSING;
            String volume="UNKNOWN_PROVIDER";
            if("com.android.externalstorage.documents".equals(tree.getAuthority())){
                String id=DocumentsContract.getTreeDocumentId(tree),key=id.contains(":")?id.substring(0,id.indexOf(':')):"";volume="ABSENT";
                for(StorageVolume v:c.getSystemService(StorageManager.class).getStorageVolumes())if(("primary".equals(key)&&v.isPrimary())||(v.getUuid()!=null&&v.getUuid().equalsIgnoreCase(key))){volume=v.getState();break;}
                d.put("volumeState",volume);if(!Environment.MEDIA_MOUNTED.equals(volume)&&!Environment.MEDIA_MOUNTED_READ_ONLY.equals(volume))return State.WAITING_STORAGE;
            }else d.put("volumeState",volume);
            stage="ROOT_QUERY";Uri root=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
            try(Cursor cursor=c.getContentResolver().query(root,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_MIME_TYPE},null,null,null,signal)){
                if(cursor==null){d.put("root","NULL_CURSOR");return State.RETRYABLE_ERROR;}
                if(!cursor.moveToFirst()){
                    // Empty results alone can be provider warm-up. Existence is confirmed separately.
                    d.put("root","EMPTY_CURSOR");return State.RETRYABLE_ERROR;
                }
                if(!DocumentsContract.getTreeDocumentId(tree).equals(cursor.getString(0))||!DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(1)))return State.DIRECTORY_MISSING;
                d.put("root","READABLE");
            }
            stage="CHILDREN_QUERY";
            try(Cursor cursor=c.getContentResolver().query(DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree)),new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID},null,null,null,signal)){
                if(cursor==null)return State.RETRYABLE_ERROR;
                int sample=0;while(sample<3&&cursor.moveToNext()){signal.throwIfCanceled();sample++;}d.put("sampleChildren",sample);
            }
            signal.throwIfCanceled();d.put("stage","ROOT_AND_CHILDREN_VERIFIED");return State.AVAILABLE;
        }catch(SecurityException e){try{d.put("stage",stage).put("error",ScanReadEvidence.reason(stage,e,false));}catch(Exception ignored){}return d.optBoolean("persistedRead")?State.RETRYABLE_ERROR:State.GRANT_MISSING;}
        catch(Exception e){try{d.put("stage",stage).put("error",ScanReadEvidence.reason(stage,e,signal.isCanceled()));}catch(Exception ignored){}return State.RETRYABLE_ERROR;}
    }
    static String label(State s){boolean zh=LocaleSettings.ui().equals("zh");switch(s){
        case NOT_CONFIGURED:return zh?"未配置":"Not configured";
        case UNCHECKED:case CHECKING:return zh?"正在检查":"Checking";
        case AVAILABLE:return zh?"可读取":"Readable";
        case WAITING_STORAGE:return zh?"等待存储":"Waiting for storage";
        case GRANT_MISSING:return zh?"需要重新授权":"Permission required";
        case DIRECTORY_MISSING:return zh?"目录已变更":"Directory changed";
        default:return zh?"读取暂时失败":"Read temporarily failed";
    }}
    static String notice(State s){boolean zh=LocaleSettings.ui().equals("zh");switch(s){
        case NOT_CONFIGURED:return zh?"请选择游戏目录":"Choose a game folder";
        case UNCHECKED:case CHECKING:return zh?"正在连接游戏目录\n已显示上次的游戏列表":"Connecting to the game folder\nShowing the previous game list";
        case WAITING_STORAGE:return zh?"存储卡暂不可用\n可浏览缓存资料，请重新检查":"Storage is not ready\nBrowse cached details; check again";
        case GRANT_MISSING:return zh?"游戏目录授权已失效\n缓存仍保留，请重新授权":"Game folder permission is missing\nCached details remain; grant access";
        case DIRECTORY_MISSING:return zh?"原游戏目录不存在或已变更\n缓存仍保留，请检查目录":"The original folder is missing or changed\nCached details remain; check the folder";
        case RETRYABLE_ERROR:return zh?"游戏目录暂时读取失败\n可浏览缓存资料，请重新检查":"The game folder cannot be read yet\nBrowse cached details; check again";
        default:return "";
    }}
    void show(android.app.Activity owner,boolean saves){if(!(owner instanceof BottomHomeActivity))return;BottomHomeActivity a=(BottomHomeActivity)owner;ShellStateRepository.Snapshot s=repository.snapshot();State state=saves?s.saveAccess:s.romAccess;
        String title=SetupJourney.text(saves?"存档目录":"游戏目录",saves?"Save folder":"Game folder");
        new ShellDialogBuilder(a).setTitle(title).setMessage((saves?label(state):notice(state)))
            .setPositiveButton(SetupJourney.text("重新检查","Check again"),(d,w)->retry(saves))
            .setNeutralButton(SetupJourney.text("选择目录","Choose folder"),(d,w)->{if(saves)a.chooseSaveDirectoryDirect();else ShellCoordinator.get().chooseRomDirectory("ACCESS_STATUS");})
            .setNegativeButton(SetupJourney.text("返回","Back"),null).show();
    }
}
