package com.rgds.ultimate.shell;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.*;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import android.view.*;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Main interactive surface, display 0. Existing public launch and SAF contracts retained. */
public final class BottomHomeActivity extends Activity implements ShellStateRepository.Listener,ShellActionHandler {
    private static final int REQUEST_ROM_TREE=2001,REQUEST_BACKUP_TREE=2002,REQUEST_EXPORT_JSON=2003;
    private final Handler mainHandler=new Handler(Looper.getMainLooper());
    private ShellStateRepository repository;
    private ShellInputRouter inputRouter;
    private ClassicScreen screen;
    private PerfTrace perf;
    private boolean resumed;private long launchRequest;private String launchSource="SHARED_ACTION";
    private static final int REQUEST_COVER=2004;
    private String coverGameId="";
    private String launchExportSnapshot="";
    private float startX,startY;
    private boolean libraryGesture,swiped;
    private final Runnable tick=new Runnable(){
        public void run(){
            if(!resumed||!getSystemService(PowerManager.class).isInteractive())return;
            screen.tick();mainHandler.postDelayed(this,60000L-System.currentTimeMillis()%60000L);
        }
    };
    private final BroadcastReceiver powerEvents=new BroadcastReceiver(){
        public void onReceive(Context c,Intent i){
            if(Intent.ACTION_SCREEN_ON.equals(i.getAction())){ShellWindowPolicy.screenOn(getWindow());DirectoryHealth.get(c).recheck("SCREEN_ON");}
            MetadataManager.get(c).sleeping(Intent.ACTION_SCREEN_OFF.equals(i.getAction()));
            mainHandler.removeCallbacks(tick);
            if(Intent.ACTION_SCREEN_ON.equals(i.getAction())&&resumed){mainHandler.post(tick);ShellCoordinator.get().ensureTop();}
        }
    };
    @Override protected void onCreate(Bundle saved){
        super.onCreate(saved);UiStrings.init(this);FirstRun.init(this);JourneyGuide.restored(this,saved!=null);PerfTrace.init(this);PerfTrace.event("BOTTOM_CREATE uptime="+SystemClock.uptimeMillis());applyImmersiveMode();
        if(saved!=null)coverGameId=saved.getString("coverGameId","");
        if(saved!=null)launchExportSnapshot=saved.getString("launchExportSnapshot","");
        repository=ShellStateRepository.get(this);ShellCoordinator.get().attach(this);DirectoryRequest.get(this).attach(this,saved);
        inputRouter=new ShellInputRouter(repository,this);screen=new ClassicScreen(this,false,this);setContentView(screen);screen.requestFocus();
        perf=new PerfTrace(this,0);repository.addListener(this);
        IntentFilter f=new IntentFilter(Intent.ACTION_SCREEN_OFF);f.addAction(Intent.ACTION_SCREEN_ON);registerReceiver(powerEvents,f);
        RomLibrary.get(this).initialize();repository.setCurrentFocusDisplay(currentDisplayId());
        mainHandler.post(()->{ShellCoordinator.get().ensureTop();TaskNotifications.open(this,getIntent());});
    }
    private final Runnable firstRun=()->{JourneyGuide.drain(this);FirstRun.maybeShow(this);};
    void scheduleFirstRun(){if(!mainHandler.hasCallbacks(firstRun))mainHandler.post(firstRun);}
    @Override protected void onResume(){
        super.onResume();resumed=true;ShellWindowPolicy.resumed(getWindow(),true);applyImmersiveMode();mainHandler.removeCallbacks(tick);mainHandler.post(tick);
        HomeIntegration.resumed(this);DirectoryHealth.get(this).recheck("FOREGROUND");ShellCoordinator.get().bottomResumed();LaunchDiagnostic.returned(this);scheduleFirstRun();applyImmersiveMode();
        { if(PerfTrace.isEnabled()) android.util.Log.i("TwinGridPerf","BOTTOM_RESUME uptime="+SystemClock.uptimeMillis()+" controlsReady="+repository.snapshot().libraryReady); }
    }
    @Override protected void onPause(){
        cancelPendingLaunch("CANCELLED_OWNER_PAUSED");
        resumed=false;HomeIntegration.paused(this);ShellWindowPolicy.resumed(getWindow(),false);ActionKeyLatch.reset();mainHandler.removeCallbacks(tick);ShellCoordinator.get().bottomPaused();repository.flush();super.onPause();
    }
    @Override protected void onStop(){ShellCoordinator.get().bottomStopped(this);mainHandler.removeCallbacks(tick);repository.flush();super.onStop();}
    @Override protected void onDestroy(){
        DirectoryRequest.get(this).detach(this);ShellDialogBuilder.closeOwned(this);
        mainHandler.removeCallbacksAndMessages(null);unregisterReceiver(powerEvents);repository.removeListener(this);
        ShellWindowPolicy.forget(getWindow());if(perf!=null)perf.close();ShellCoordinator.get().detach(this);super.onDestroy();
    }
    @Override protected void onSaveInstanceState(Bundle b){b.putString("coverGameId",coverGameId);b.putString("launchExportSnapshot",launchExportSnapshot);DirectoryRequest.get(this).save(b);repository.flush();super.onSaveInstanceState(b);}
    @Override public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);LocaleSettings.configurationChanged(this);applyImmersiveMode();}
    void refreshLocale(){UiStrings.refresh(this);if(screen!=null)screen.bind(repository.snapshot());}
    @Override public void onWindowFocusChanged(boolean focus){
        super.onWindowFocusChanged(focus);if(!focus){ActionKeyLatch.reset();
            final long request=launchRequest;
            // Settle the paired-window focus callbacks, without retrying a request.
            mainHandler.post(()->{if(request==launchRequest&&!ShellDialogBuilder.hasOpenDialog()
                    &&!ShellCoordinator.get().launchWindowReady(this))cancelPendingLaunch("CANCELLED_WINDOW_FOCUS_LOST");});
        }if(focus&&repository!=null){JourneyGuide.regainedFocus(this);HomeIntegration.resumed(this);scheduleFirstRun();repository.setCurrentFocusDisplay(currentDisplayId());}
    }
    @Override public boolean dispatchKeyEvent(KeyEvent e){if(e.getAction()==KeyEvent.ACTION_DOWN)launchSource="KEY_"+KeyEvent.keyCodeToString(e.getKeyCode());if(screen!=null&&screen.editingSearch())return screen.searchKey(e)||super.dispatchKeyEvent(e);return inputRouter!=null&&inputRouter.dispatchKeyEvent(e)||super.dispatchKeyEvent(e);}
    @Override public boolean dispatchGenericMotionEvent(MotionEvent e){return inputRouter!=null&&inputRouter.dispatchGenericMotionEvent(e)||super.dispatchGenericMotionEvent(e);}
    @Override public boolean dispatchTouchEvent(MotionEvent e){
        if(screen==null)return super.dispatchTouchEvent(e);
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN){
            launchSource="TOUCH";
            startX=e.getX();startY=e.getY();swiped=false;
            libraryGesture=ShellCoordinator.get().canInteract()&&screen.isLibraryTouch(startY);
        }else if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&libraryGesture&&!swiped){
            float dx=e.getX()-startX,dy=e.getY()-startY;
            if(Math.max(Math.abs(dx),Math.abs(dy))>58){
                swiped=true;
                MotionEvent cancel=MotionEvent.obtain(e);cancel.setAction(MotionEvent.ACTION_CANCEL);
                super.dispatchTouchEvent(cancel);cancel.recycle();
            }
        }else if(e.getActionMasked()==MotionEvent.ACTION_UP){
            if(libraryGesture&&swiped){
                float dx=e.getX()-startX,dy=e.getY()-startY;
                repository.pageBy(Math.abs(dx)>Math.abs(dy)?(dx<0?1:-1):(dy<0?1:-1));
                libraryGesture=false;swiped=false;return true;
            }
            screen.scrollTouchToState();
        }else if(e.getActionMasked()==MotionEvent.ACTION_CANCEL){libraryGesture=false;swiped=false;}
        if(libraryGesture&&swiped)return true;
        return super.dispatchTouchEvent(e);
    }
    @Override public void onShellStateChanged(ShellStateRepository.Snapshot s){
        screen.bind(s);JourneyGuide.observe(this,s);if(s.page==ShellStateRepository.Page.HOME&&resumed&&s.inputTime==0)screen.tick();
    }
    @Override public void dump(String prefix,java.io.FileDescriptor fd,java.io.PrintWriter writer,String[] args){
        super.dump(prefix,fd,writer,args);RuntimeDiagnostics.dump(this,writer,args);
    }
    @Override public void onLaunchRequested(){ShellCoordinator.get().launchSelectedFromEitherScreen();}
    void launchSelectedGame(){
        ShellStateRepository.Snapshot s=repository.snapshot();
        if(!ShellCoordinator.get().canInteract()||ShellDialogBuilder.hasOpenDialog())return;
        if(screen.editingSearch()||s.searchEditing||s.searchComposing)return;
        if(s.searchActive&&s.searchBusy){toast(UiStrings.msg("ui_df95c2b8810c"));return;}
        if(s.page==ShellStateRepository.Page.LIBRARY&&s.typesOverview){repository.enterGenre();return;}
        if(s.page==ShellStateRepository.Page.DETAILS)return;
        if(s.page==ShellStateRepository.Page.FILTER){repository.activateFilter();return;}
        if(s.page==ShellStateRepository.Page.PREPARING){SetupScreen.perform(this,s.taskIndex);return;}
        if(s.page==ShellStateRepository.Page.TASKS){performTask();return;}
        if(s.page==ShellStateRepository.Page.SETTINGS){performSetting(s.settingsIndex);return;}
        if(s.page==ShellStateRepository.Page.HOME){
            if(s.homeIndex==0&&s.romTreeUri==null){repository.setCategory(ShellStateRepository.Category.NDS);return;}
            if(s.homeIndex==1){repository.setCategory(ShellStateRepository.Category.NDS);return;}
            if(s.homeIndex==2){repository.setCategory(ShellStateRepository.Category.FAVORITES);return;}
            if(s.homeIndex==3){repository.setCategory(ShellStateRepository.Category.RECENT);return;}
            if(s.homeIndex==4){ShellCoordinator.get().openSettingsActively();return;}
            if(s.homeIndex==5){ShellCoordinator.get().openTasksActively();return;}
            if(s.selectedGame==null){repository.setCategory(ShellStateRepository.Category.NDS);return;}
        }
        if(s.page==ShellStateRepository.Page.LIBRARY&&repository.enterFolder())return;
        if(s.canBrowseGame()&&!s.storageAvailable){DirectoryHealth.get(this).show(this,false);return;}
        if(!s.canActOnGame()){toast(UiStrings.msg("ui_4ded08c5e852"));return;}
        if(!s.storageAvailable){toast(UiStrings.msg("ui_f35fad4b266b"));return;}
        GameEntry game=s.selectedGame;
        final long request=++launchRequest;final long launchList=s.listRevision;final String launchQuery=s.actionQuery;final int launchGeneration=RomLibrary.get(this).generation();
        
        repository.beginLaunch(game);repository.flush();ShellCoordinator.get().launchStarted();
        RomLibrary.get(this).checkReadable(game,s.page.name()+"|"+s.category.name()+"|"+launchSource,currentDisplayId(),getTaskId(),plan->{
            if(isFinishing()||isDestroyed()||request!=launchRequest||!ShellCoordinator.get().owns(this)||!ShellCoordinator.get().launchPending())return;
            if(!resumed||!ShellCoordinator.get().launchWindowReady(this)||!getSystemService(PowerManager.class).isInteractive()
                    ||ShellDialogBuilder.hasOpenDialog()||!RomLibrary.get(this).generationIs(launchGeneration)||!repository.launchStillCurrent(game,launchList,launchQuery)){
                LaunchDiagnostic.outcome(this,plan.requestId,"CANCELLED_CONTEXT_CHANGED");repository.cancelLaunchRecord();ShellCoordinator.get().launchFailed();
                toast(UiStrings.msg("ui_9203bfc35aa6"));return;
            }
            Runnable cancel=()->{LaunchDiagnostic.outcome(this,plan.requestId,"CANCELLED");if(request==launchRequest&&ShellCoordinator.get().owns(this)){++launchRequest;RomLibrary.get(this).cancelLaunchCheck();ShellCoordinator.get().launchFailed();}};
            LaunchUi.authorize(this,plan,()->{
                if(!resumed||request!=launchRequest||!ShellCoordinator.get().owns(this)||!ShellCoordinator.get().launchPending()){cancel.run();return;}
                RomLibrary.get(this).verifyLaunch(plan,verified->{
                    if(!resumed||isFinishing()||isDestroyed()||request!=launchRequest||!ShellCoordinator.get().owns(this)||!ShellCoordinator.get().launchPending()
                        ||!ShellCoordinator.get().launchWindowReady(this)||!getSystemService(PowerManager.class).isInteractive()||ShellDialogBuilder.hasOpenDialog()||!RomLibrary.get(this).generationIs(launchGeneration)||!repository.launchStillCurrent(game,launchList,launchQuery)){cancel.run();return;}
                    try{ShellCoordinator.get().beginExternalHandoff();DrasticLauncher.dispatch(this,verified);repository.recordLaunchAccepted();ShellCoordinator.get().launchAccepted();repository.setDrasticState(JourneyGuide.s(43));}
                    catch(Exception error){launchFailed(error);}
                },error->{if(request==launchRequest&&ShellCoordinator.get().owns(this))launchFailed(error);});
            },cancel);
        },error->{if(request==launchRequest&&ShellCoordinator.get().owns(this)&&ShellCoordinator.get().launchPending()){if(!isFinishing()&&!isDestroyed()&&resumed)launchFailed(error);else{repository.cancelLaunchRecord();ShellCoordinator.get().launchFailed();}}});
    }
    void cancelPendingLaunch(String reason){
        if(!ShellCoordinator.get().owns(this)||!ShellCoordinator.get().launchPending())return;
        LaunchDiagnostic.outcome(this,LaunchDiagnostic.read(this).optString("requestId"),reason);
        ++launchRequest;RomLibrary.get(this).cancelLaunchCheck();repository.cancelLaunchRecord();ShellCoordinator.get().launchFailed();
    }
    private void launchFailed(Exception e){
        android.util.Log.e("TwinGrid","LAUNCH_FAILED",e);repository.cancelLaunchRecord();
        int reason=LaunchFailure.reason(e);
        LaunchDiagnostic.failure(this,LaunchDiagnostic.read(this).optString("requestId"),reason);
        String message=LaunchDiagnostic.help(this);
        if(reason==LaunchFailure.CANCELLED){repository.setDrasticState(message);ShellCoordinator.get().launchFailed();return;}
        repository.setDrasticState(message);ShellCoordinator.get().launchFailed();
        if(!isFinishing())new ShellDialogBuilder(this).setTitle(UiStrings.msg("ui_0a54d1d4eacd")).setMessage(message).setPositiveButton(JourneyGuide.s(35),(d,w)->launchHelp()).setNegativeButton(JourneyGuide.s(11),null).show();
    }
    void launchHelp(){new ShellDialogBuilder(this).setTitle(JourneyGuide.s(35)).setMessage(LaunchDiagnostic.help(this)+"\n\n"+LaunchUi.text("选择游戏会直接载入所选目标，通过重建模拟器任务避免恢复旧游戏。请先在游戏内存档；未保存的进度不会自动保留。返回 TwinGrid 只表示外部返回，不能确认游戏已经关闭。\n\n回测：用不同游戏 A→B→A，分别正常退出和按 HOME 返回后切换。请记录结果并导出诊断；发送请求不代表载入成功。Gamma Nano 内置运行器尚未接入。","Selecting a game directly loads that target by replacing the emulator task. Save in-game first; unsaved progress is not preserved automatically. Returning to TwinGrid does not confirm that the game closed.\n\nRetest different games A→B→A after normal exit and after HOME. Record results and export diagnostics. Dispatch does not confirm loading. The Gamma Nano native runner is not integrated."))
        .setPositiveButton(JourneyGuide.s(11),null).setNeutralButton(JourneyGuide.s(38),(d,w)->exportLaunchDiagnostic()).setNegativeButton(LaunchUi.text("记录结果","Record result"),(d,w)->LaunchUi.observations(this)).show();}
    void helpMenu(){new ShellDialogBuilder(this).setTitle(JourneyGuide.s(33)).setItems(UiStrings.array(new String[]{JourneyGuide.s(34),JourneyGuide.s(9)}),(d,n)->{if(n==0)QuickStart.show(this,0);else if(n==1)JourneyGuide.showTasks(this,false);}).show();}
    private void exportLaunchDiagnostic(){
        if(!launchExportSnapshot.isEmpty())return;
        launchExportSnapshot=LaunchDiagnostic.export(this).toString();
        ShellCoordinator.get().pickerStarted();Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE,"TwinGrid_launch_diagnostic.json");
        try{startActivityForResult(i,2005);}catch(Exception e){launchExportSnapshot="";ShellCoordinator.get().pickerFinished();}
    }
    @Override public void onBackPressed(){
        if(ShellCoordinator.get().launchPending()){cancelPendingLaunch("CANCELLED");return;}
        if(screen.editingSearch()){screen.finishSearchEditing();return;}
        if(repository.snapshot().searchActive){repository.back();return;}
        if(repository.page()!=ShellStateRepository.Page.HOME)repository.back();
        else ShellCoordinator.get().exit(this);
    }
    @Override public void onBackRequested(){onBackPressed();}
    @Override public void onDetailsRequested(){repository.toggleDetailPage();}
    @Override public void onFilterRequested(){if(repository.snapshot().searchActive)UiDialogs.searchOptions(this);else repository.openFilter();}
    @Override public void onLocalSearchRequested(){screen.openSearch();}
    boolean editingSearch(){return screen!=null&&screen.editingSearch();}
    org.json.JSONObject editorDiagnostics(){return screen==null?new org.json.JSONObject():screen.editorDiagnostics();}
    org.json.JSONObject scanDiagnostics(){return screen==null?new org.json.JSONObject():screen.scanDiagnostics();}
    void endTextEditing(){if(screen!=null)screen.finishSearchEditing();}
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);ShellCoordinator.get().homeIntent(i);TaskNotifications.open(this,i);}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==TaskNotifications.REQUEST)TaskNotifications.permissionResult(this,results);}
    void performTask(){
        MetadataManager m=MetadataManager.get(this);int index=repository.snapshot().taskIndex;
        TaskScreen.perform(this,index);
    }
    void performSetting(int position){
        if(!ShellCoordinator.get().canInteract()||!repository.snapshot().stateReady)return;
        ShellStateRepository.Snapshot s=repository.snapshot();MetadataManager m=MetadataManager.get(this);SettingsModel.Entry item=SettingsModel.entry(s.settingsGroup,position);
        if(!item.enabled(m))return;
        if(item.kind==SettingsModel.Kind.GROUP){repository.setSettingsGroup(SettingsModel.index(-1,item.id));return;}
        switch(item.id){
            case TASKS:repository.openTasks();break;
            case FILL:repository.openTasks();break;
            case MATCH:FirstRun.catalog(this);break;
            case COVERAGE:GapUi.coverage(this);break;
            case REVIEW:UiDialogs.review(this);break;
            case AUTO:m.setAuto(!m.enabled());break;
            case METERED:m.setMetered(!m.meteredAllowed());break;
            case NOTIFY:TaskNotifications.toggle(this);break;
            case ROM_DIR:chooseRomDirectory("SETTINGS_ROM");break;
            case SAVE_DIR:DirectoryRequest.get(this).request(this,true,"SETTINGS_SAVE",false);break;
            case SCAN:if(RomLibrary.get(this).isScanning())RomLibrary.get(this).cancel();else RomLibrary.get(this).scanUncached();break;
            case SORT:repository.cycleSortMode();break;
            case LANGUAGE:LocaleSettings.choose(this,false);break;
            case CONTENT_LANGUAGE:LocaleSettings.choose(this,true);break;
            case BATTERY_DISPLAY:BatterySettings.choose(this);break;
            case HOME_INTEGRATION:HomeIntegration.showHome(this);break;
            case BROWSE:repository.setCategory(s.category);break;
            case EXPORT:beginExport();break;
            case USAGE:UiDialogs.text(this,UiStrings.msg("ui_37ac171c052a"),m.cacheUsage());break;
            case CLEAR:new ShellDialogBuilder(this).setTitle(UiStrings.msg("ui_e1771fd27af7")).setMessage(m.cacheUsage()+UiStrings.msg("ui_cff0421ce0ad")).setPositiveButton(UiStrings.msg("ui_c194f4e29f23"),(d,w)->m.clearDownloads()).setNegativeButton(UiStrings.msg("ui_2cd0f3be8738"),null).show();break;
            case VERSION:UiDialogs.version(this,m);break;
            case SOURCES:UiDialogs.sources(this);break;
            case HELP:helpMenu();break;
            case DIRECTORY_HELP:chooseRomDirectory("SETTINGS_HELP");break;
            case DIAGNOSTICS:repository.toggleDetailPage();break;
            case LOGS:PerfTrace.setEnabled(this,!PerfTrace.isEnabled());m.notifyTaskUi();break;
            case PROBE:m.probeSources();break;
            case INDEX:m.updateSources();repository.openTasks();break;
            case AUDIT:m.exportAudit();break;
            default:break;
        }
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(HomeIntegration.result(this,request))return;
        if(DirectoryRequest.get(this).result(this,request,result,data))return;
        if(request==REQUEST_ROM_TREE||request==REQUEST_BACKUP_TREE)return; // Retired unbound code116 callbacks.
        ShellCoordinator.get().pickerFinished();
        ActionKeyLatch.reset();
        if(result!=RESULT_OK||data==null||data.getData()==null){if(request==2005)launchExportSnapshot="";return;}
        Uri uri=data.getData();
        if(request==2005){final String json=launchExportSnapshot;launchExportSnapshot="";if(json.isEmpty()){toast(JourneyGuide.s(64));return;}toast(JourneyGuide.s(62));new Thread(()->{try(java.io.OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new java.io.IOException("No output stream");out.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));out.flush();}catch(Exception e){runOnUiThread(()->toast(JourneyGuide.s(64)));return;}runOnUiThread(()->toast(JourneyGuide.s(63)));},"diagnostic-export").start();return;}
        if(request==REQUEST_COVER){
            if(!coverGameId.isEmpty())MetadataManager.get(this).importCover(coverGameId,uri);coverGameId="";return;
        }
        if(request==REQUEST_EXPORT_JSON)writeExport(uri);
    }
    void chooseRomDirectory(){chooseRomDirectory("MANUAL_"+repository.page());}
    void chooseRomDirectory(String source){DirectoryRequest.get(this).request(this,false,source,false);}
    void chooseSaveDirectoryDirect(){DirectoryRequest.get(this).request(this,true,"MANUAL_SAVE",false);}
    void openTasksActively(){JourneyGuide.activeTasks(this);}
    void chooseRomDirectoryDirect(){chooseRomDirectory("HELP_COMPLETE");}
    void chooseCover(String id){
        coverGameId=id;ShellCoordinator.get().pickerStarted();
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_LOCAL_ONLY,true).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{startActivityForResult(i,REQUEST_COVER);}catch(Exception e){ShellCoordinator.get().pickerFinished();toast(UiStrings.msg("ui_85bd3094bc9b"));}
    }
    private void beginExport(){
        ShellCoordinator.get().pickerStarted();
        String timestamp=new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date());
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).putExtra(Intent.EXTRA_LOCAL_ONLY,true);
        i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE,"TwinGrid_Data_"+timestamp+".json");
        i.putExtra(DocumentsContract.EXTRA_INITIAL_URI,DocumentsContract.buildDocumentUri("com.android.externalstorage.documents","primary:Documents"));
        try{startActivityForResult(i,REQUEST_EXPORT_JSON);}
        catch(RuntimeException e){ShellCoordinator.get().pickerFinished();toast(UiStrings.msg("ui_db5e6454eba0"));}
    }
    private void writeExport(Uri uri){
        try{
            if(!"com.android.externalstorage.documents".equals(uri.getAuthority())||!DocumentsContract.isDocumentUri(this,uri)){
                toast(UiStrings.msg("ui_57fdffffe362"));return;
            }
            String target=DocumentsContract.getDocumentId(uri).toLowerCase(Locale.ROOT);
            ShellStateRepository.Snapshot s=repository.snapshot();
            for(Uri tree:new Uri[]{s.romTreeUri,s.backupTreeUri}){
                if(tree!=null&&tree.getAuthority().equals(uri.getAuthority())){
                    String root=DocumentsContract.getTreeDocumentId(tree).toLowerCase(Locale.ROOT);
                    if(target.equals(root)||target.startsWith(root+"/")){toast(UiStrings.msg("ui_3a598e4a4a48"));return;}
                }
            }
        }catch(RuntimeException e){toast(UiStrings.msg("ui_628537a06225"));return;}
        RomLibrary.get(this).exportData(uri,this::toast);
    }
    private int currentDisplayId(){
        Display d=getWindow().getDecorView().getDisplay();return d==null?getWindowManager().getDefaultDisplay().getDisplayId():d.getDisplayId();
    }
    private void applyImmersiveMode(){ShellWindowPolicy.set(getWindow(),!ShellCoordinator.get().canInteract()?ShellWindowPolicy.Mode.EXTERNAL:screen!=null&&screen.editingSearch()?ShellWindowPolicy.Mode.EDITING:ShellWindowPolicy.Mode.BROWSING,getClass().getSimpleName());ShellWindowPolicy.refresh(getWindow(),"lifecycle");}
    private void toast(String s){android.widget.TextView label=ClassicUi.text(this,UiStrings.display(s),20);label.setPadding(16,10,16,10);label.setBackground(new ClassicUi.Edge(false));Toast notice=new Toast(this);notice.setView(label);notice.setDuration(Toast.LENGTH_SHORT);notice.show();}
}
