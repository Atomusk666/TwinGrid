package com.rgds.ultimate.shell;

import android.app.Activity;
import android.content.*;
import android.content.res.Configuration;
import android.os.*;
import android.view.*;

/** Read-mostly top display, sharing the same snapshot and input routing as the bottom. */
public final class TopDisplayActivity extends Activity implements ShellStateRepository.Listener,ShellActionHandler {
    private final Handler main=new Handler(Looper.getMainLooper());
    private ShellStateRepository repository;
    private ShellInputRouter router;
    private ClassicScreen screen;
    private PerfTrace perf;
    private boolean registered,resumed;
    private android.app.Presentation surface;
    Window surfaceWindow(){return surface==null?getWindow():surface.getWindow();}
    boolean surfaceHasWindowFocus(){return surface!=null&&surface.isShowing()&&surfaceWindow().getDecorView().hasWindowFocus();}
    void closeSurface(){if(surface!=null&&surface.isShowing())surface.dismiss();}
    private void surfaceFocus(boolean focus){
        if(!focus)ActionKeyLatch.reset();
        if(focus&&repository!=null){ShellCoordinator.get().endTextEditing();repository.setCurrentFocusDisplay(currentDisplayId());}
    }
    private void showSurface(){
        if(surface==null||!resumed||!ShellCoordinator.get().canInteract()||isFinishing())return;
        if(!surface.isShowing())surface.show();
        surfaceWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT);
        ShellWindowPolicy.resumed(surfaceWindow(),true);applyImmersiveMode();screen.requestFocus();
    }
    private final Runnable tick=new Runnable(){
        public void run(){
            if(!resumed||screen==null||!getSystemService(PowerManager.class).isInteractive())return;
            screen.tick();main.postDelayed(this,60000L-System.currentTimeMillis()%60000L);
        }
    };
    private final BroadcastReceiver powerEvents=new BroadcastReceiver(){
        public void onReceive(Context c,Intent i){
            if(Intent.ACTION_SCREEN_ON.equals(i.getAction()))ShellWindowPolicy.screenOn(surfaceWindow());
            main.removeCallbacks(tick);if(Intent.ACTION_SCREEN_ON.equals(i.getAction())&&resumed)main.post(tick);
        }
    };
    @Override protected void onCreate(Bundle saved){
        super.onCreate(saved);UiStrings.init(this);repository=ShellStateRepository.get(this);router=new ShellInputRouter(repository,this);
        if(currentDisplayId()!=2){repository.markTopLaunchFailed("Incorrect top display");finish();return;}
        ShellCoordinator.get().attach(this);ShellCoordinator.get().topReady();applyImmersiveMode();
        // Keep the Activity task/lifecycle, while the existing view owns a normal
        // Android secondary-display Presentation. Some receivers leave their
        // Presentation alive on HOME; an application window is below that layer.
        setContentView(new android.widget.FrameLayout(this));
        surface=new android.app.Presentation(this,getWindowManager().getDefaultDisplay(),R.style.AppTheme){
            @Override public boolean dispatchKeyEvent(KeyEvent e){return router!=null&&router.dispatchKeyEvent(e)||super.dispatchKeyEvent(e);}
            @Override public boolean dispatchGenericMotionEvent(MotionEvent e){return router!=null&&router.dispatchGenericMotionEvent(e)||super.dispatchGenericMotionEvent(e);}
            @Override public boolean dispatchTouchEvent(MotionEvent e){if(screen!=null&&e.getActionMasked()==MotionEvent.ACTION_UP)screen.scrollTouchToState();return super.dispatchTouchEvent(e);}
            @Override public void onBackPressed(){TopDisplayActivity.this.onBackPressed();}
            @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);surfaceFocus(focus);}
            @Override public void onDisplayRemoved(){super.onDisplayRemoved();TopDisplayActivity.this.finish();}
        };
        surface.setCanceledOnTouchOutside(false);
        screen=new ClassicScreen(this,true,this);surface.setContentView(screen);perf=new PerfTrace(this,2);
        repository.markTopActivityCreated();repository.addListener(this);
        IntentFilter f=new IntentFilter(Intent.ACTION_SCREEN_OFF);f.addAction(Intent.ACTION_SCREEN_ON);registerReceiver(powerEvents,f);registered=true;
    }
    @Override protected void onResume(){super.onResume();resumed=true;showSurface();main.removeCallbacks(tick);main.post(tick);}
    @Override protected void onPause(){resumed=false;ShellWindowPolicy.resumed(surfaceWindow(),false);closeSurface();ActionKeyLatch.reset();main.removeCallbacks(tick);repository.flush();super.onPause();}
    @Override protected void onStop(){closeSurface();main.removeCallbacks(tick);repository.flush();super.onStop();}
    @Override protected void onDestroy(){
        ShellDialogBuilder.closeOwned(this);
        main.removeCallbacksAndMessages(null);
        if(registered){unregisterReceiver(powerEvents);repository.removeListener(this);}
        closeSurface();ShellWindowPolicy.forget(surfaceWindow());ShellWindowPolicy.forget(getWindow());if(perf!=null)perf.close();repository.markTopActivityDestroyed();ShellCoordinator.get().detach(this);super.onDestroy();
    }
    @Override protected void onSaveInstanceState(Bundle b){repository.flush();super.onSaveInstanceState(b);}
    @Override public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);LocaleSettings.configurationChanged(this);applyImmersiveMode();}
    void refreshLocale(){UiStrings.refresh(this);if(screen!=null)screen.bind(repository.snapshot());}
    @Override public void onWindowFocusChanged(boolean focus){
        super.onWindowFocusChanged(focus);if(surface==null)surfaceFocus(focus);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent e){return router!=null&&router.dispatchKeyEvent(e)||super.dispatchKeyEvent(e);}
    @Override public boolean dispatchGenericMotionEvent(MotionEvent e){return router!=null&&router.dispatchGenericMotionEvent(e)||super.dispatchGenericMotionEvent(e);}
    @Override public boolean dispatchTouchEvent(MotionEvent e){
        if(screen!=null&&e.getActionMasked()==MotionEvent.ACTION_UP)screen.scrollTouchToState();return super.dispatchTouchEvent(e);
    }
    @Override public void onShellStateChanged(ShellStateRepository.Snapshot s){
        if(screen!=null){screen.bind(s);if(s.page==ShellStateRepository.Page.HOME&&resumed&&s.inputTime==0)screen.tick();}
    }
    @Override public void onLaunchRequested(){ShellCoordinator.get().launchSelectedFromEitherScreen();}
    @Override public void onBackPressed(){
        ShellCoordinator.get().backFromEitherScreen();
    }
    @Override public void dump(String prefix,java.io.FileDescriptor fd,java.io.PrintWriter writer,String[] args){
        super.dump(prefix,fd,writer,args);RuntimeDiagnostics.dump(this,writer,args);
    }
    @Override public void onBackRequested(){onBackPressed();}
    @Override public void onDetailsRequested(){repository.toggleDetailPage();}
    @Override public void onFilterRequested(){if(repository.snapshot().searchActive)UiDialogs.searchOptions(this);else repository.openFilter();}
    @Override public void onLocalSearchRequested(){ShellCoordinator.get().openSearch();}
    private int currentDisplayId(){
        Display d=getWindow().getDecorView().getDisplay();return d==null?getWindowManager().getDefaultDisplay().getDisplayId():d.getDisplayId();
    }
    private void applyImmersiveMode(){ShellWindowPolicy.set(surfaceWindow(),!ShellCoordinator.get().canInteract()?ShellWindowPolicy.Mode.EXTERNAL:screen!=null&&screen.editingSearch()?ShellWindowPolicy.Mode.EDITING:ShellWindowPolicy.Mode.BROWSING,getClass().getSimpleName());ShellWindowPolicy.refresh(surfaceWindow(),"lifecycle");}
}
