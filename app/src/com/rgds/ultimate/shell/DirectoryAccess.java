package com.rgds.ultimate.shell;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.IOException;
import java.util.concurrent.Executors;
/** Validates a replacement before changing the user's active tree; no file writes. */
final class DirectoryAccess {
    private static final java.util.concurrent.ExecutorService WORKER=Executors.newSingleThreadExecutor(r->new Thread(r,"folder-validation"));
    private static volatile boolean validating;
    private static volatile boolean activeSaves;
    private static java.lang.ref.WeakReference<android.app.AlertDialog> notice=new java.lang.ref.WeakReference<>(null);
    private static volatile int revision;
    private static long requestId,noticeEpoch;
    private static android.os.CancellationSignal signal=new android.os.CancellationSignal();
    static boolean busy(){return validating;}
    static void closeNotice(){++noticeEpoch;android.app.AlertDialog d=notice.get();notice.clear();if(d!=null&&d.isShowing())d.dismiss();}
    static void showSaveNotice(BottomHomeActivity a){if(!validating||!activeSaves)return;android.app.AlertDialog old=notice.get();if(old!=null&&old.isShowing())return;
        final int noticeRevision=revision; final long noticeRequest=requestId,windowEpoch=++noticeEpoch;
        android.app.AlertDialog d=new ShellDialogBuilder(a).setTitle(JourneyGuide.s(47)).setMessage(JourneyGuide.s(48)).setNegativeButton(JourneyGuide.s(8),(x,w)->{if(windowEpoch==noticeEpoch&&ShellCoordinator.get().owns(a)&&!a.isFinishing()&&!a.isDestroyed()&&noticeRevision==revision&&noticeRequest==requestId)cancel(a);}).setOnCancelListener(x->{if(windowEpoch==noticeEpoch&&ShellCoordinator.get().owns(a)&&!a.isChangingConfigurations()&&!a.isFinishing()&&!a.isDestroyed()&&noticeRevision==revision&&noticeRequest==requestId)cancel(a);}).show();notice=new java.lang.ref.WeakReference<>(d);}
    static void cancel(Context c){if(!validating)return;boolean saves=activeSaves;++revision;signal.cancel();validating=false;closeNotice();DirectoryRequest.get(c).cancelValidation(requestId,saves);if(!saves)RomLibrary.get(c).cancel();}
    static void validate(Context owner,Uri uri,boolean saves,long request,int grantedFlags){
        if(validating||!DirectoryRequest.get(owner).validating(request,saves))return;requestId=request;activeSaves=saves;validating=true;final int token=++revision;signal=new android.os.CancellationSignal();final android.os.CancellationSignal cancellation=signal;
        Context a=owner.getApplicationContext();ShellStateRepository repo=ShellStateRepository.get(a);
        if(!saves)RomLibrary.get(a).validating();else repo.setScanState(repo.snapshot().scanState);
        PerfTrace.event("DIRECTORY_RESULT uptime="+android.os.SystemClock.uptimeMillis()+" saves="+saves);
        WORKER.execute(()->{
            try {
                if(!DocumentsContract.isTreeUri(uri))throw new IOException("Not a tree");
                String id=DocumentsContract.getTreeDocumentId(uri);
                if("com.android.externalstorage.documents".equals(uri.getAuthority())&&id.endsWith(":"))throw new IOException("Storage root");
                if((grantedFlags&Intent.FLAG_GRANT_READ_URI_PERMISSION)==0||(grantedFlags&Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)==0)throw new IOException("Provider did not offer persistable READ");
                a.getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
                boolean persisted=false;for(UriPermission g:a.getContentResolver().getPersistedUriPermissions())if(g.getUri().equals(uri)&&g.isReadPermission())persisted=true;
                if(!persisted)throw new IOException("No persisted read permission");
                String name;Uri doc=DocumentsContract.buildDocumentUriUsingTree(uri,id);
                try(Cursor c=a.getContentResolver().query(doc,new String[]{DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null,cancellation)){
                    if(c==null||!c.moveToFirst()||!DocumentsContract.Document.MIME_TYPE_DIR.equals(c.getString(0)))throw new IOException("Not a directory");name=c.getString(1);
                }
                try(Cursor c=a.getContentResolver().query(DocumentsContract.buildChildDocumentsUriUsingTree(uri,id),new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID},null,null,null,cancellation)){
                    if(c==null)throw new IOException("Cannot enumerate");c.getCount();
                }
                final String display=name;
                new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{
                    if(token!=revision||!DirectoryRequest.get(a).validating(request,saves))return;
                    validating=false;closeNotice();
                    DirectoryRequest.get(a).commit(request,saves,uri,display);
                    ShellCoordinator.get().guideReady();
                });
            }catch(Exception e){android.util.Log.w("TwinGrid","FOLDER_VALIDATION_FAILED "+e.getClass().getSimpleName());new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{if(token!=revision||!DirectoryRequest.get(a).validating(request,saves))return;validating=false;closeNotice();DirectoryRequest.get(a).validationFailed(request,saves);if(!saves)RomLibrary.get(a).validationFailed();MetadataManager.get(a).savedNotice(JourneyGuide.s(saves?29:45));});}
        });
    }
    static String label(Context c,Uri uri,boolean saves){
        if(uri==null)return QuickStart.s(15);SharedPreferences p=c.getSharedPreferences("directory_names_v93",0);
        if(uri.toString().equals(p.getString(saves?"saveUri":"romUri","")))return p.getString(saves?"saveName":"romName","")+(saves&&"INVALID_ACCESS".equals(JourneyGuide.snapshot(c).optString("save"))?" · "+JourneyGuide.s(61):"");
        try {String id=DocumentsContract.getTreeDocumentId(uri);return id.replace(':','/')+(saves&&"INVALID_ACCESS".equals(JourneyGuide.snapshot(c).optString("save"))?" · "+JourneyGuide.s(61):"");}catch(Exception e){return QuickStart.s(15);}
    }
}
