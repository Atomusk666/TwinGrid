package com.rgds.ultimate.shell;

import android.app.AlertDialog;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import java.lang.ref.WeakReference;
import org.json.*;

/** Main-thread directory transaction. A window is a capability, never a new request. */
final class DirectoryRequest {
    enum Phase { IDLE, OFFERED, PICKER, VALIDATING, COMMITTING, COMMITTED, CANCELED, FAILED, INTERRUPTED }
    private static DirectoryRequest instance;
    static DirectoryRequest get(Context c){if(instance==null)instance=new DirectoryRequest(c);return instance;}
    final Context app;
    private final SharedPreferences prefs;
    private WeakReference<BottomHomeActivity> owner=new WeakReference<>(null);
    private AlertDialog offer;
    private Phase phase=Phase.IDLE;
    private long id,epoch;
    private int code,launches,commits,scans,shown,maxVisible;
    private boolean saves,consumed,dispatched;
    private String source="",end="";
    private final java.util.ArrayDeque<JSONObject> events=new java.util.ArrayDeque<>();
    private DirectoryRequest(Context c){app=c.getApplicationContext();prefs=app.getSharedPreferences("directory_request_v116",0);
        try{JSONObject o=new JSONObject(prefs.getString("request","{}"));id=o.optLong("id");phase=Phase.valueOf(o.optString("phase","IDLE"));code=o.optInt("code");saves=o.optBoolean("saves");consumed=o.optBoolean("consumed");dispatched=o.optBoolean("dispatched");source=o.optString("source");launches=o.optInt("launches");commits=o.optInt("commits");scans=o.optInt("scans");shown=o.optInt("shown");maxVisible=o.optInt("maxVisible");end=o.optString("end");}catch(Exception ignored){}
    }
    boolean active(){return phase==Phase.OFFERED||phase==Phase.PICKER||phase==Phase.VALIDATING||phase==Phase.COMMITTING;}
    boolean picker(){return phase==Phase.PICKER;}
    long id(){return id;}
    long epoch(){return epoch;}
    int code(){return code;}
    Phase phase(){return phase;}
    boolean saves(){return saves;}
    void attach(BottomHomeActivity a,Bundle saved){
        BottomHomeActivity prior=owner.get();boolean hadOwner=prior!=null;
        ++epoch;DirectoryAccess.closeNotice();closeOffer("OWNER_TRANSFER");owner=new WeakReference<>(a);DirectoryTrace.watch(a.getWindow(),"activity");
        if(phase==Phase.PICKER&&prior!=null&&saved==null&&prior.getTaskId()!=a.getTaskId()){
            // ShellCoordinator retires the old task. Close only the child launched by that task;
            // its subsequent cancellation is stale and must not fabricate a user skip.
            prior.finishActivity(code);interrupt("PICKER_TASK_RETIRED");ShellCoordinator.get().pickerFinished();
        }
        if(phase==Phase.VALIDATING&&!DirectoryAccess.busy())interrupt("VALIDATOR_LOST");
        if(phase==Phase.COMMITTING&&!commitInFlight)interrupt("COMMIT_INTERRUPTED");
        if(phase==Phase.PICKER&&!hadOwner&&(saved==null||saved.getLong("directoryRequestId",-1)!=id))interrupt("RESULT_OWNER_LOST");
        // A restored Activity resumes its callback contract, never launches from lifecycle.
        if(phase==Phase.PICKER)ShellCoordinator.get().pickerStarted();
        event("OWNER_ATTACH",a,"saved="+(saved!=null));
    }
    void detach(BottomHomeActivity a){if(owner.get()!=a)return;++epoch;DirectoryAccess.closeNotice();closeOffer("OWNER_DESTROY");owner.clear();event("OWNER_DETACH",a,"");}
    void save(Bundle b){b.putLong("directoryRequestId",id);}
    void interrupt(String reason){phase=Phase.INTERRUPTED;end=reason;persist();JourneyGuide.directoryEnded(app,saves,"INTERRUPTED",reason);event("INTERRUPTED",owner.get(),reason);}
    void request(BottomHomeActivity a,boolean save,String origin,boolean automatic){
        event("ENTRY",a,origin+" type="+(save?"SAVE":"ROM"));
        if(owner.get()!=a||!ShellCoordinator.get().owns(a)||a.isFinishing()||!ShellStateRepository.get(a).snapshot().stateReady){event("REJECT_OWNER",a,origin);return;}
        if(active()){event(saves==save?"MERGE":"REJECT_BUSY_TYPE",a,origin);return;}
        if(!ShellCoordinator.get().canInteract())return;
        ShellStateRepository.Snapshot s=ShellStateRepository.get(a).snapshot();
        if(automatic&&(save?s.backupTreeUri:s.romTreeUri)!=null){event("REJECT_CONFIGURED",a,origin);return;}
        id=prefs.getLong("sequence",0)+1;
        // Never reuse an Android result code, even after process death or explicit retry.
        if(id>49150){event("REQUEST_SPACE_EXHAUSTED",a,origin);MetadataManager.get(a).savedNotice(JourneyGuide.s(45));return;}
        code=16384+(int)id;saves=save;source=origin;phase=Phase.OFFERED;consumed=false;dispatched=false;end="";
        launches=commits=scans=shown=maxVisible=0;++epoch;
        prefs.edit().putLong("sequence",id).commit();persist();if(saves&&origin.startsWith("AUTO_SAVE"))SetupJourney.saveRequest(a,id);JourneyGuide.directoryOffered(a,saves);
        event("CREATED",a,origin);showOffer(a);
    }
    void resumeOffer(BottomHomeActivity a){if(owner.get()==a&&phase==Phase.OFFERED&&offer==null)showOffer(a);}
    private void showOffer(BottomHomeActivity a){
        if(owner.get()!=a||phase!=Phase.OFFERED||offer!=null||a.isFinishing())return;
        final long token=id,windowEpoch=++epoch;
        ShellStateRepository.Snapshot s=ShellStateRepository.get(a).snapshot();boolean auto=source.startsWith("AUTO");
        String body=auto?JourneyGuide.s(saves?6:2):QuickStart.directoryText(a,saves);
        if(auto&&saves)body=SetupJourney.text("游戏资料正在整理。请选择 DraStic 实际使用的存档文件夹，以识别已有存档；没有存档或暂时不确定，可跳过并在设置中添加。选择目录不会复制存档，也不会为 DraStic 授权。","Game details are being prepared. Select the save folder used by DraStic to identify existing saves. You can skip and add it in Settings later. This neither copies saves nor grants DraStic access.");
        if(saves&&s.romTreeUri!=null)body=(LocaleSettings.ui().startsWith("zh")?"游戏目录已选择：":"Game folder selected: ")+DirectoryAccess.label(a,s.romTreeUri,false)+"\n\n"+body;
        ShellDialogBuilder builder=new ShellDialogBuilder(a);
        builder.setTitle(auto?JourneyGuide.s(saves?5:1):QuickStart.s(saves?22:21)).setView(QuickStart.body(a,body))
            .setPositiveButton(auto?JourneyGuide.s(saves?7:3):QuickStart.s(2),(d,w)->confirm(a,token,windowEpoch))
            .setNegativeButton(auto?JourneyGuide.s(saves?8:4):QuickStart.s(3),(d,w)->cancelOffer(a,token,windowEpoch))
            .setOnCancelListener(d->{if(!a.isChangingConfigurations()&&!a.isFinishing())cancelOffer(a,token,windowEpoch);});
        if(!auto)builder.setNeutralButton(QuickStart.s(0),null);
        // Reserve before create/show: synchronous UI publications can reenter the entry point.
        offer=builder.create();AlertDialog current=offer;
        ShellDialogBuilder.onDismissed(current,()->{
            if(offer==current&&ownsOffer(a,token,windowEpoch)){offer=null;++epoch;interrupt("OFFER_DISMISSED");ShellCoordinator.get().guideReady();}
        });
        current.getWindow().getDecorView().setTag("directory_"+(saves?"SAVE":"ROM")+"_"+token);
        current.show();DirectoryTrace.watch(current.getWindow(),"offer:"+token);ShellDialogBuilder.help(current,body);
        if(!auto)current.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{if(ownsOffer(a,token,windowEpoch))QuickStart.show(a,1);});
        ++shown;maxVisible=Math.max(maxVisible,visible());persist();event("OFFER_SHOWN",a,"windowEpoch="+windowEpoch);
    }
    private boolean ownsOffer(BottomHomeActivity a,long token,long windowEpoch){return id==token&&epoch==windowEpoch&&owner.get()==a&&ShellCoordinator.get().owns(a)&&phase==Phase.OFFERED&&!a.isFinishing()&&!a.isDestroyed();}
    void confirm(BottomHomeActivity a,long token,long windowEpoch){
        if(!ownsOffer(a,token,windowEpoch)){event("REJECT_OLD_CONFIRM",a,"token="+token);return;}
        phase=Phase.PICKER;dispatched=true;consumed=false;++epoch;persist();JourneyGuide.directoryPicker(a,saves,id);
        closeOffer("CONFIRM_HANDOFF");ActionKeyLatch.reset();ShellCoordinator.get().pickerStarted();
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).putExtra(Intent.EXTRA_LOCAL_ONLY,true)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        ShellStateRepository.Snapshot s=ShellStateRepository.get(a).snapshot();Uri uri=saves?s.backupTreeUri:s.romTreeUri;
        if(uri!=null)i.putExtra(DocumentsContract.EXTRA_INITIAL_URI,DocumentsContract.buildDocumentUriUsingTree(uri,DocumentsContract.getTreeDocumentId(uri)));
        try{++launches;persist();event("PICKER_LAUNCH",a,"");a.startActivityForResult(i,code);}
        catch(RuntimeException e){finish(Phase.FAILED,"PICKER_START_FAILED");ShellCoordinator.get().pickerFinished();MetadataManager.get(a).savedNotice(JourneyGuide.s(45));}
    }
    void cancelOffer(BottomHomeActivity a,long token,long windowEpoch){if(!ownsOffer(a,token,windowEpoch)){event("REJECT_OLD_CANCEL",a,"token="+token);return;}finish(Phase.CANCELED,"USER_CANCEL");}
    boolean result(BottomHomeActivity a,int request,int result,Intent data){
        if(request<16385||request>65534)return false;
        event("RESULT_RECEIVED",a,"code="+request+" result="+result);
        if(request!=code||phase!=Phase.PICKER||consumed||!dispatched||owner.get()!=a||!ShellCoordinator.get().owns(a)){event("REJECT_RESULT",a,"code="+request);return true;}
        consumed=true;phase=Phase.VALIDATING;++epoch;persist();closeOffer("RESULT_CONSUMED");
        if(result==android.app.Activity.RESULT_CANCELED){finish(Phase.CANCELED,"USER_CANCEL");ShellCoordinator.get().pickerFinished();return true;}
        if(result!=android.app.Activity.RESULT_OK||data==null||data.getData()==null){finish(Phase.FAILED,"EMPTY_RESULT");ShellCoordinator.get().pickerFinished();return true;}
        JourneyGuide.directoryValidating(app,saves);event("VALIDATION_START",a,"");
        DirectoryAccess.validate(app,data.getData(),saves,id,data.getFlags());ShellCoordinator.get().pickerFinished();return true;
    }
    boolean validating(long token,boolean save){return id==token&&saves==save&&phase==Phase.VALIDATING&&consumed;}
    private boolean commitInFlight;
    void commit(long token,boolean save,Uri uri,String label){
        if(!validating(token,save)){event("REJECT_LATE_COMMIT",owner.get(),"token="+token);return;}
        ShellStateRepository repo=ShellStateRepository.get(app);
        phase=Phase.COMMITTING;commitInFlight=true;++epoch;persist();closeOffer("COMMITTING");event("DURABLE_WRITE_START",owner.get(),"");
        repo.commitDirectory(uri,save,()->{
            if(id!=token||saves!=save||phase!=Phase.COMMITTING)return;
            commitInFlight=false;
            app.getSharedPreferences("directory_names_v93",0).edit().putString(save?"saveUri":"romUri",uri.toString()).putString(save?"saveName":"romName",label).commit();
            JourneyGuide.directoryAccepted(app,save,uri);
            if(!save)FirstRun.directoryAccepted(app);
            phase=Phase.COMMITTED;end="DURABLE_READBACK";++commits;persist();event("COMMITTED",owner.get(),"");
            if(save)RomLibrary.get(app).refreshSaves();
            else{++scans;persist();event("SCAN_REQUEST",owner.get(),"");RomLibrary.get(app).scan();}
            ShellCoordinator.get().guideReady();
        },()->{if(id==token&&saves==save&&phase==Phase.COMMITTING){commitInFlight=false;finish(Phase.FAILED,"DIRECTORY_WRITE_FAILED");if(!save)RomLibrary.get(app).validationFailed();MetadataManager.get(app).savedNotice(JourneyGuide.s(45));}});
    }
    void validationFailed(long token,boolean save){if(!validating(token,save)){event("REJECT_LATE_FAILURE",owner.get(),"token="+token);return;}finish(Phase.FAILED,"VALIDATION_FAILED");}
    void cancelValidation(long token,boolean save){if(validating(token,save))finish(Phase.CANCELED,"USER_CANCEL");else event("REJECT_LATE_CANCEL",owner.get(),"token="+token);}
    private void finish(Phase terminal,String reason){phase=terminal;end=reason;++epoch;persist();closeOffer(reason);JourneyGuide.directoryEnded(app,saves,terminal==Phase.CANCELED?"SKIPPED":terminal.name(),reason);event("ENDED",owner.get(),reason);ShellCoordinator.get().guideReady();}
    private void closeOffer(String reason){AlertDialog d=offer;offer=null;if(d!=null){d.dismiss();event("OFFER_CLOSED",owner.get(),reason);}}
    int visible(){int count=0;for(android.view.View window:ShellDialogBuilder.visibleWindows(0))if(String.valueOf(window.getTag()).startsWith("directory_"))++count;return count;}
    private JSONObject state(){JSONObject o=new JSONObject();try{o.put("id",id).put("type",saves?"SAVE":"ROM").put("saves",saves).put("phase",phase.name()).put("source",source).put("code",code).put("consumed",consumed).put("dispatched",dispatched).put("epoch",epoch).put("launches",launches).put("commits",commits).put("scans",scans).put("shown",shown).put("maxVisible",maxVisible).put("visible",visible()).put("end",end);}catch(Exception ignored){}return o;}
    private void persist(){prefs.edit().putString("request",state().toString()).commit();}
    void event(String action,BottomHomeActivity a,String detail){JSONObject o=state();try{o.put("event",action).put("uptime",android.os.SystemClock.uptimeMillis()).put("owner",a==null?"":Integer.toHexString(System.identityHashCode(a))).put("task",a==null?-1:a.getTaskId()).put("display",a==null?-1:a.getWindowManager().getDefaultDisplay().getDisplayId()).put("detail",detail).put("input",DirectoryTrace.lastInput);}catch(Exception ignored){}if(events.size()==256)events.removeFirst();events.addLast(o);android.util.Log.i("TwinGridDirectory",o.toString());}
    JSONObject snapshot(){JSONObject o=state();try{o.put("events",new JSONArray(events)).put("inputEvents",DirectoryTrace.snapshot());}catch(Exception ignored){}return o;}
}
