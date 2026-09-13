package com.rgds.ultimate.shell;

import android.app.Activity;
import android.os.SystemClock;
import android.view.*;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.widget.*;
import android.graphics.Color;
import android.text.TextUtils;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import static com.rgds.ultimate.shell.ClassicUi.*;

/** Fixed View hierarchy for the two physical 640 x 480 screens. Rebind only changed content. */
final class ClassicScreen extends FrameLayout implements MetadataManager.Listener,MetadataManager.TaskListener {
    private final Activity owner;
    private final boolean top;
    private final ShellStateRepository repo;
    private final ShellActionHandler actions;
    private final FrameLayout home,library,settings,details,filter,genres,tasks;
    private SetupScreen setupView;private FrameLayout preparation;
    private TaskScreen taskView;private TextView taskEntry;
    private FrameLayout editingGuide;private TextView editingHelp;
    private long boundLocale=-1;
    private final String[] rowBindings=new String[6];private long rowBindingUpdates,topContentUpdates;
    private boolean topContentDirty=true;private String topContentKey="";
    private final TextView[] settingDescriptions=new TextView[5],settingValues=new TextView[5];
    private TextView settingsPrevious,settingsNext;
    private final TextView heading,status;
    private BatteryIndicatorView batteryIndicator;
    private final View toolbar,divider;
    private TextView homeGame,homeCount,homeFavorites,homeRecent,homeInfo,homeDate;
    private final FrameLayout[] homePanels=new FrameLayout[5],rows=new FrameLayout[6],settingRows=new FrameLayout[5];
    private final TextView[] rowTitles=new TextView[6],rowMarks=new TextView[6],tabs=new TextView[5],settingLabels=new TextView[5],filterLabels=new TextView[6];
    private final Icon[] rowIcons=new Icon[6];
    private final TextView[] rowEditions=new TextView[6];
    private java.util.List<DirectoryIndex.Item> editionItems;private long editionLocale=-1;private java.util.Map<String,String> editionLabels=java.util.Collections.emptyMap();
    private Icon homeIcon,hero;
    private CoverView cover;
    private final MetadataManager metadata;
    private final TextView[] genreChoices=new TextView[13],genreExamples=new TextView[6];private TextView genreHelp,clearFilter;private boolean builtGenres;
    private TextView scopeLabel,detailGameTab,detailFileTab,detailCorrectButton,detailFooter,searchButton,searchStateLabel,descriptionLabel;private DescriptionView descriptionText;
    private EditText searchEdit;private final TextView[] footerButtons=new TextView[5];
    private boolean editingSearch,composingSearch,settingQuery,searchTextSyncPending,pendingBeginEditing;
    private Runnable pendingBeginEditingCallback;
    private String committedSearchText="";private int editingKey=-1;private long editingKeyDownTime;
    private int inputConnectionGeneration;private long inputConnectionAt,imeChangedAt;
    private int contentHeight=440,imeBottom,visibleRowCount=6;
    private static final int LIBRARY_ROW_TOP=UiMetrics.LIBRARY_CONTENT_TOP,LIBRARY_ROW_PITCH=UiMetrics.ROW_PITCH,LIBRARY_ROW_HEIGHT=UiMetrics.ROW_HEIGHT;
    private boolean topSingleColumn;
    private TextView legacyHeaderTitle,legacyHeaderGenres,legacyHeaderVersion;private String selectedHeaderGenres="",selectedHeaderVersion="",headerAnchorKey="";private int headerAnchorBody;
    private FrameLayout topReadingPanel;private View readingDivider,readingFooterLine;private TextView topStateText;
    private final android.graphics.Rect visibleWindow=new android.graphics.Rect();
    private final int[] screenLocation=new int[2];
    private ClockFace clock;
    private Month month;
    private TextView gameTitle,favorite,save,recent,gameHint,pageLabel,sortLabel,emptyLabel,scanLabel,startButton;private FrameLayout libraryStatus;private ScanBusyView scanBusy;private String drawnScan="";private ScanProgress boundScan=new ScanProgress("IDLE",0,0,0,0,0);
    private TextView detailText,settingInfo,filterPage;
    private final TextView[] genreCounts=new TextView[13];
    private ScrollView detailScroll;
    private SeekBar jump;
    private ShellStateRepository.Snapshot previous;
    private long lastDrawSequence;
    private int boundPage=-1;
    private long boundList=-1;
    private String selectedId="";
    private boolean firstReady;
    private boolean builtHome,builtLibrary,builtSettings,builtDetails,builtFilter;

    ClassicScreen(Activity a,boolean isTop,ShellActionHandler handler){
        super(a);owner=a;top=isTop;actions=handler;repo=ShellStateRepository.get(a);metadata=MetadataManager.get(a);
        DsGrid.register(this,top);setBackground(DsGrid.background(top,0,0));setFocusableInTouchMode(true);setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        toolbar=new View(a);toolbar.setTag("toolbar_band");toolbar.setBackground(new Band());place(this,toolbar,0,0,640,40);
        heading=new ToolbarTextView(a,"TwinGrid");heading.setTextColor(INK);heading.setTag("toolbar_title");
        status=new ToolbarTextView(a,"");status.setTextColor(MUTED);status.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);status.setTag("toolbar_status");
        place(this,heading,24,0,top?340:156,40);place(this,status,365,0,top?199:251,40);
        if(top){batteryIndicator=new BatteryIndicatorView(a);place(this,batteryIndicator,580,10,36,20);batteryIndicator.setOnModeChanged(this::layoutBattery);layoutBattery();}
        if(!top){taskEntry=toolbarButton(this,a,UiStrings.msg("ui_9a1d8c6a5082"),384,4,104,()->{finishSearchEditing();if(repo.snapshot().page==ShellStateRepository.Page.HOME)repo.selectHome(5);ShellCoordinator.get().openTasksActively();});taskEntry.setTag("toolbar_tasks");taskEntry.setSingleLine(true);taskEntry.setEllipsize(TextUtils.TruncateAt.END);taskEntry.setCompoundDrawables(new DsIcons(DsIcons.TASKS,16),null,null,null);taskEntry.setCompoundDrawablePadding(4);
            searchButton=toolbarButton(this,a,UiStrings.msg("ui_0c048f4df0b5"),496,4,120,()->openSearch());searchButton.setTag("toolbar_search");searchButton.setCompoundDrawables(new DsIcons(DsIcons.SEARCH,16),null,null,null);searchButton.setCompoundDrawablePadding(4);searchButton.setVisibility(GONE);}
        divider=new View(a);divider.setBackgroundColor(LINE);place(this,divider,24,39,592,1);
        preparation=page(a);place(this,preparation,0,40,640,440);preparation.setVisibility(GONE);
        home=page(a);library=page(a);settings=page(a);details=page(a);filter=page(a);genres=page(a);tasks=page(a);
        for(FrameLayout p:new FrameLayout[]{home,library,settings,details,filter,tasks})place(this,p,0,40,640,440);
        if(!top)bounds(home,0,0,640,480);
        if(top){editingGuide=page(a);editingGuide.setBackground(DsGrid.background(true,0,40));place(this,editingGuide,0,40,640,440);FrameLayout card=panel(a);place(editingGuide,card,24,16,592,352);editingHelp=text(a,"",20);editingHelp.setGravity(Gravity.TOP);editingHelp.setLineSpacing(6,1);place(card,editingHelp,24,20,544,312);editingGuide.setVisibility(GONE);}
        place(library,genres,0,top?8:LIBRARY_ROW_TOP,640,top?390:278);genres.setVisibility(GONE);
        getViewTreeObserver().addOnDrawListener(() -> {
            ShellStateRepository.Snapshot s=previous;
            if(s!=null&&s.inputTime>0&&s.inputSequence!=lastDrawSequence){
                lastDrawSequence=s.inputSequence;
                { if(PerfTrace.isEnabled()) android.util.Log.i("TwinGridPerf","DRAW display="+(top?2:0)+" sequence="+s.inputSequence+
                    " event="+s.inputTime+" uptime="+SystemClock.uptimeMillis()); }
            }
            if(s!=null&&s.page==ShellStateRepository.Page.PREPARING&&!top)SetupJourney.drawn(owner);
            if(s!=null&&s.page==ShellStateRepository.Page.LIBRARY){ScanProgress p=boundScan;String key=p.session+"|"+p.phase+"|"+(s.totalGameCount>0);if(!key.equals(drawnScan)){drawnScan=key;PerfTrace.event("SCAN_DRAW display="+(top?2:0)+" uptime="+SystemClock.uptimeMillis()+" phase="+p.phase+" count="+s.totalGameCount);}}
            if(!firstReady&&s!=null&&s.libraryReady&&s.stateReady&&s.storageAvailable){
                firstReady=true;
                { if(PerfTrace.isEnabled()) android.util.Log.i("TwinGridPerf","CONTENT_DRAW display="+(top?2:0)+" uptime="+SystemClock.uptimeMillis()+
                    " count="+s.totalGameCount+" controlsReady=true"); }
            }
        });
    }
    private void layoutBattery(){
        if(!top||batteryIndicator==null)return;
        boolean numbers=batteryIndicator.numbers();
        bounds(batteryIndicator,numbers?552:580,10,numbers?64:36,20);
        bounds(status,365,0,numbers?171:199,40);
    }
    private void homePlace(View view,int index){int[] r=HomeLayout.ENTRIES[index];place(home,view,r[0],r[1],r[2],r[3]);view.setTag("home_entry_"+index);}
    private TextView homeLabel(String value,boolean primary){
        TextView t=text(owner,value,20);DsTypography.home(t,primary);t.setGravity(Gravity.CENTER);lines(t,1);return t;
    }
    private String homeTitleLines(String value,android.text.TextPaint paint){
        if(paint.measureText(value)<=344)return value;
        int split=-1;float balance=Float.MAX_VALUE;
        for(int i=1;i<value.length()-1;i++)if(value.charAt(i)==' '){
            float left=paint.measureText(value.substring(0,i)),right=paint.measureText(value.substring(i+1));
            if(left<=344&&right<=344&&Math.abs(left-right)<balance){split=i;balance=Math.abs(left-right);}
        }
        // Presentation-only line break at an existing separator; never alter GameEntry data.
        return split<0?value:value.substring(0,split)+"\n"+value.substring(split+1);
    }
    private void buildHome(){
        FrameLayout slot=new HomeUi.Panel(owner,HomeUi.SLOT);homePanels[0]=slot;homePlace(slot,0);
        homeIcon=new Icon(owner,DsIcons.GAME);homeIcon.setTag("home_rom_banner");place(slot,homeIcon,24,24,64,64);
        homeGame=homeLabel("",true);homeGame.setTag("home_game_title");lines(homeGame,2);homeGame.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_BALANCED);homeGame.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE);place(slot,homeGame,124,12,344,88);
        slot.setOnClickListener(v -> {repo.selectHome(0);if(repo.snapshot().romTreeUri==null)ShellCoordinator.get().chooseRomDirectory("HOME_SLOT");else actions.onLaunchRequested();});
        FrameLayout all=homeTile(1,DsIcons.LIBRARY,UiStrings.msg("ui_b8eb5f068caf"),()->repo.setCategory(ShellStateRepository.Category.NDS));
        homeCount=homeLabel("",false);homeCount.setTextColor(MUTED);place(all,homeCount,80,60,140,36);
        FrameLayout favorites=homeTile(2,DsIcons.FAVORITE,UiStrings.msg("ui_9385bfefca58"),()->repo.setCategory(ShellStateRepository.Category.FAVORITES));
        homeFavorites=homeLabel("",false);homeFavorites.setTextColor(MUTED);place(favorites,homeFavorites,80,60,140,36);
        FrameLayout rec=homeTile(3,DsIcons.RECENT,UiStrings.msg("ui_dbd67e367a67"),()->repo.setCategory(ShellStateRepository.Category.RECENT));
        homeRecent=homeLabel("",false);homeRecent.setTextColor(MUTED);place(rec,homeRecent,124,60,344,32);
        homeTile(4,DsIcons.SETTINGS,UiStrings.msg("ui_df3d58c7d84b"),()->ShellCoordinator.get().openSettingsActively());
    }
    private FrameLayout homeTile(int index,int symbol,String name,Runnable action){
        FrameLayout p=new HomeUi.Panel(owner,index==4?HomeUi.SETTINGS:index==3?HomeUi.RECENT:HomeUi.PAIR);homePanels[index]=p;homePlace(p,index);
        p.setContentDescription(UiStrings.display(name));
        if(index!=4){
            HomeUi.Symbol icon=new HomeUi.Symbol(owner,symbol);icon.setTag("home_symbol_"+index);
            place(p,icon,24,index==3?24:54,index==3?64:48,index==3?64:48);
            TextView label=homeLabel(name,true);label.setTag("home_title_"+index);
            place(p,label,index==3?124:12,index==3?16:6,index==3?344:208,42);
        }
        p.setOnClickListener(v->{repo.selectHome(index);action.run();});return p;
    }
    private void topHomePlace(View v,int[] r){place(home,v,r[0],r[1]-40,r[2],r[3]);}
    private void buildTopHome(){
        clock=new ClockFace(owner);month=new Month(owner);
        clock.setTag("home_square_clock");month.setTag("home_calendar");topHomePlace(clock,HomeLayout.CLOCK);topHomePlace(month,HomeLayout.MONTH);
        homeDate=homeLabel("",false);homeDate.setBackgroundColor(0xFFFBFBFB);homeDate.setTag("home_month_title");topHomePlace(homeDate,HomeLayout.MONTH_TITLE);
        homeInfo=homeLabel("",false);homeInfo.setTag("home_context_notice");homeInfo.setBackgroundColor(0xFFFBFBFB);homeInfo.setPadding(12,8,12,8);lines(homeInfo,2);homeInfo.setLineSpacing(4,1);place(home,homeInfo,40,352+8,560,72);homeInfo.setVisibility(GONE);
    }
    private void layoutPageChrome(ShellStateRepository.Snapshot s){
        boolean atHome=s.page==ShellStateRepository.Page.HOME;
        DsGrid.Spec grid=atHome?(top?HomeLayout.TOP_GRID:HomeLayout.BOTTOM_GRID):(top?DsGrid.TOP:DsGrid.BOTTOM);
        DsGrid.register(this,grid);setBackground(new DsGrid.Background(grid,0,0));
        boolean show=top||!atHome;toolbar.setVisibility(show?VISIBLE:GONE);divider.setVisibility(show?VISIBLE:GONE);heading.setVisibility(show?VISIBLE:GONE);
    }
    private void buildLibrary(){
        ShellStateRepository.Category[] cs={ShellStateRepository.Category.NDS,ShellStateRepository.Category.TYPES,ShellStateRepository.Category.FOLDERS,ShellStateRepository.Category.FAVORITES,ShellStateRepository.Category.RECENT};
        int[] tabIcons={DsIcons.LIBRARY,DsIcons.GENRES,DsIcons.FOLDER,DsIcons.FAVORITE,DsIcons.RECENT};
        for(int i=0;i<5;i++){final ShellStateRepository.Category c=cs[i];tabs[i]=new LibraryTabView(owner,c.label,tabIcons[i],()->{finishSearchEditing();repo.setCategory(c);});tabs[i].setTag("library_tab_"+c.name());place(library,tabs[i],24+i*120,UiMetrics.LIBRARY_NAV_TOP,112,48);}
        searchEdit=new EditText(owner){
            @Override public boolean onKeyPreIme(int code,KeyEvent e){if(code==KeyEvent.KEYCODE_BACK&&editingSearch)return searchKey(e);return super.onKeyPreIme(code,e);}
            @Override public InputConnection onCreateInputConnection(EditorInfo info){
                InputConnection connection=super.onCreateInputConnection(info);if(connection==null)return null;
                inputConnectionGeneration++;inputConnectionAt=SystemClock.uptimeMillis();
                return new InputConnectionWrapper(connection,false){
                    @Override public boolean setComposingText(CharSequence text,int position){markSearchComposing(true);boolean result=super.setComposingText(text,position);syncSearchText();return result;}
                    @Override public boolean setComposingRegion(int start,int end){markSearchComposing(true);boolean result=super.setComposingRegion(start,end);syncSearchText();return result;}
                    @Override public boolean commitText(CharSequence text,int position){boolean result=super.commitText(text,position);syncSearchText();return result;}
                    @Override public boolean finishComposingText(){boolean result=super.finishComposingText();syncSearchText();return result;}
                    @Override public boolean setComposingText(CharSequence text,int position,android.view.inputmethod.TextAttribute attributes){markSearchComposing(true);boolean result=super.setComposingText(text,position,attributes);syncSearchText();return result;}
                    @Override public boolean setComposingRegion(int start,int end,android.view.inputmethod.TextAttribute attributes){markSearchComposing(true);boolean result=super.setComposingRegion(start,end,attributes);syncSearchText();return result;}
                    @Override public boolean commitText(CharSequence text,int position,android.view.inputmethod.TextAttribute attributes){boolean result=super.commitText(text,position,attributes);syncSearchText();return result;}
                };
            }
        };
        searchEdit.setSingleLine(true);DsTypography.body(searchEdit,20);searchEdit.setIncludeFontPadding(false);searchEdit.setGravity(Gravity.CENTER_VERTICAL);searchEdit.setMinHeight(0);searchEdit.setBackground(new InputEdge());searchEdit.setPadding(10,0,10,0);searchEdit.setTextColor(INK);searchEdit.setHintTextColor(MUTED);searchEdit.setHint(UiStrings.display(UiStrings.msg("ui_8f0167a6cfd7")));searchEdit.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH|android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI);searchEdit.setInputType(android.text.InputType.TYPE_CLASS_TEXT);DsTypography.body(searchEdit,20);searchEdit.setVisibility(GONE);place(library,searchEdit,24,46,368,40);
        searchEdit.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN)beginSearchEditing();return false;});
        searchEdit.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(160)});
        searchEdit.setOnFocusChangeListener((v,focus)->{if(focus&&!editingSearch)beginSearchEditing();});
        searchEdit.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence value,int start,int before,int count){}public void afterTextChanged(android.text.Editable e){scheduleSearchTextSync();}});
        searchEdit.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_SEARCH||action==EditorInfo.IME_ACTION_DONE){syncSearchText();if(!composingSearch)finishSearchEditing();else text(searchStateLabel,UiStrings.msg("ui_57b941db7f4b"));return true;}return false;});
        scopeLabel=label(owner,"",16,INK);scopeLabel.setSingleLine(true);scopeLabel.setEllipsize(TextUtils.TruncateAt.START);place(library,scopeLabel,24,48,368,32);
        sortLabel=button(library,owner,"",UiMetrics.LIBRARY_FILTER_X,UiMetrics.LIBRARY_SCOPE_TOP,UiMetrics.LIBRARY_FILTER_WIDTH,32,()->{if(repo.snapshot().searchActive)UiDialogs.searchScope(owner);else UiDialogs.genres(owner);});sortLabel.setSingleLine(true);sortLabel.setEllipsize(TextUtils.TruncateAt.END);
        clearFilter=button(library,owner,"",UiMetrics.LIBRARY_CLEAR_X,UiMetrics.LIBRARY_SCOPE_TOP,UiMetrics.LIBRARY_CLEAR_WIDTH,32,()->{if(repo.snapshot().searchActive){clearSearchText();}else repo.setGenre("all");});clearFilter.setCompoundDrawables(new DsIcons(DsIcons.CLOSE,16),null,null,null);clearFilter.setPadding(12,0,12,0);clearFilter.setContentDescription(UiStrings.display(UiStrings.msg("ui_b55da8e1078d")));
        bounds(searchEdit,24,UiMetrics.LIBRARY_SCOPE_TOP,368,32);bounds(sortLabel,UiMetrics.LIBRARY_FILTER_X,UiMetrics.LIBRARY_SCOPE_TOP,UiMetrics.LIBRARY_FILTER_WIDTH,32);bounds(scopeLabel,24,UiMetrics.LIBRARY_SCOPE_TOP,368,32);bounds(clearFilter,UiMetrics.LIBRARY_CLEAR_X,UiMetrics.LIBRARY_SCOPE_TOP,UiMetrics.LIBRARY_CLEAR_WIDTH,32);searchEdit.setTag("search_editor");sortLabel.setTag("library_scope_filter");clearFilter.setTag("search_clear");
        searchStateLabel=label(owner,"",15,ACCENT);searchStateLabel.setSingleLine(true);place(library,searchStateLabel,24,92,592,28);searchStateLabel.setVisibility(GONE);
        for(int i=0;i<6;i++){
            final int row=i;FrameLayout p=panel(owner);p.setBackground(new Edge(false,true));rows[i]=p;place(library,p,24,LIBRARY_ROW_TOP+i*LIBRARY_ROW_PITCH,592,LIBRARY_ROW_HEIGHT);
            rowIcons[i]=new Icon(owner,DsIcons.GAME);place(p,rowIcons[i],UiMetrics.ROW_ICON_X,UiMetrics.ROW_ICON_Y,32,32);
            p.setTag("library_row_"+i);rowTitles[i]=text(owner,"",18);DsTypography.title(rowTitles[i],18);rowTitles[i].setTag("game_title");lines(rowTitles[i],2);
            if(android.os.Build.VERSION.SDK_INT>=28){rowTitles[i].setFallbackLineSpacing(false);rowTitles[i].setLineHeight(20);}
            else rowTitles[i].setLineSpacing(0,1);
            rowTitles[i].setGravity(Gravity.CENTER_VERTICAL);place(p,rowTitles[i],UiMetrics.ROW_TITLE_X,UiMetrics.ROW_TEXT_Y,460,UiMetrics.ROW_TEXT_HEIGHT);
            rowEditions[i]=label(owner,"",15,MUTED);rowEditions[i].setTag("local_edition");if(android.os.Build.VERSION.SDK_INT>=28)rowEditions[i].setLineHeight(20);lines(rowEditions[i],2);place(p,rowEditions[i],450,UiMetrics.ROW_TEXT_Y,70,UiMetrics.ROW_TEXT_HEIGHT);
            rowMarks[i]=label(owner,"",14,ACCENT);rowMarks[i].setGravity(Gravity.CENTER);lines(rowMarks[i],2);if(android.os.Build.VERSION.SDK_INT>=28)rowMarks[i].setLineHeight(20);place(p,rowMarks[i],528,UiMetrics.ROW_TEXT_Y,52,UiMetrics.ROW_TEXT_HEIGHT);
            p.setOnClickListener(v->{ShellStateRepository.Snapshot s=repo.snapshot();if(ShellCoordinator.get().canInteract()&&!s.searchEditing&&!s.searchComposing&&!s.searchBusy)repo.selectIndex(boundPage*s.pageSize+row);});
        }
        emptyLabel=text(owner,"",20);emptyLabel.setGravity(Gravity.CENTER);emptyLabel.setPadding(24,16,24,16);emptyLabel.setLineSpacing(6,1);emptyLabel.setBackground(new Edge(false));emptyLabel.setOnClickListener(v->emptyStateAction());place(library,emptyLabel,24,LIBRARY_ROW_TOP,592,268);
        scanBusy=new ScanBusyView(owner);scanBusy.setTag("scan_busy");place(library,scanBusy,580,LIBRARY_ROW_TOP+16,20,20);scanBusy.setVisibility(GONE);
        libraryStatus=panel(owner);libraryStatus.setTag("library_status_panel");place(library,libraryStatus,24,360,592,UiMetrics.LIBRARY_STATUS_HEIGHT);
        pageLabel=label(owner,"",15,INK);pageLabel.setSingleLine(true);place(library,pageLabel,36,364,568,28);
        scanLabel=label(owner,"",14,MUTED);scanLabel.setSingleLine(true);scanLabel.setEllipsize(TextUtils.TruncateAt.END);scanLabel.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);place(library,scanLabel,220,364,384,28);
        scanLabel.setTag("scan_read_details");scanLabel.setOnClickListener(v->{if(!repo.snapshot().storageAvailable)DirectoryHealth.get(owner).show(owner,false);else if(boundScan.attention())emptyStateAction();});
        View actionBand=new View(owner);actionBand.setTag("library_action_band");actionBand.setBackground(new Band());place(library,actionBand,0,400,640,40);
        footerButtons[0]=button(library,owner,UiStrings.msg("ui_bec6ab1883ad"),24,384,96,44,actions::onBackRequested);
        footerButtons[1]=button(library,owner,UiStrings.msg("ui_a2fea32bc688"),128,384,96,44,actions::onDetailsRequested);
        footerButtons[2]=button(library,owner,UiStrings.msg("ui_b550716adad2"),232,384,108,44,repo::toggleFavorite);
        footerButtons[3]=button(library,owner,UiStrings.msg("ui_7ea101e4f250"),348,384,132,44,actions::onFilterRequested);
        startButton=button(library,owner,UiStrings.msg("ui_b57db9dbc9eb"),488,384,128,44,actions::onLaunchRequested);footerButtons[4]=startButton;
        final String[] pressed={null};startButton.setOnTouchListener((v,e)->{
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN)pressed[0]=repo.snapshot().actionContext();
            if(e.getActionMasked()==MotionEvent.ACTION_CANCEL)pressed[0]=null;
            if(e.getActionMasked()==MotionEvent.ACTION_UP){boolean stale=pressed[0]==null||!pressed[0].equals(repo.snapshot().actionContext())||ShellDialogBuilder.hasOpenDialog();pressed[0]=null;if(stale){v.setPressed(false);return true;}}
            return false;
        });
        for(int n=0;n<5;n++){footerButtons[n].setTag("library_action_"+n);footerButtons[n].setPadding(3,1,3,2);}layoutLibrary();
    }
    void openSearch(){
        if(top)return;if(repo.snapshot().searchActive){finishSearchEditing();repo.back();return;}
        if(repo.snapshot().page!=ShellStateRepository.Page.LIBRARY||!ShellCoordinator.get().canInteract()||ShellDialogBuilder.hasOpenDialog())return;
        repo.startSearch();pendingBeginEditing=true;markSearchEditingRequested();
    }
    private void markSearchEditingRequested(){
        if(!editingSearch||pendingBeginEditing){committedSearchText=repo.snapshot().searchQuery;editingSearch=true;editingKey=-1;}
        repo.setSearchEditing(true);
    }
    private void postBeginSearchEditing(){
        if(!pendingBeginEditing||searchEdit==null||!searchEdit.isShown())return;
        // State is delivered on a frame callback; focus only after that bind has shown the editor.
        pendingBeginEditing=false;
        pendingBeginEditingCallback=new Runnable(){public void run(){
            if(pendingBeginEditingCallback!=this)return;
            ShellStateRepository.Snapshot current=repo.snapshot();
            if(!editingSearch||current.page!=ShellStateRepository.Page.LIBRARY||!current.searchActive
                    ||!isAttachedToWindow()||!searchEdit.isShown()||owner.isFinishing()
                    ||!ShellCoordinator.get().canInteract()||ShellDialogBuilder.hasOpenDialog()){
                finishSearchEditing();return;
            }
            pendingBeginEditingCallback=null;beginSearchEditing();
        }};
        searchEdit.post(pendingBeginEditingCallback);
    }
    private void cancelPendingBeginEditing(){
        pendingBeginEditing=false;
        if(pendingBeginEditingCallback!=null&&searchEdit!=null)searchEdit.removeCallbacks(pendingBeginEditingCallback);
        pendingBeginEditingCallback=null;
    }
    void beginSearchEditing(){
        if(top||searchEdit==null||!searchEdit.isShown()||repo.snapshot().page!=ShellStateRepository.Page.LIBRARY||!repo.snapshot().searchActive)return;
        cancelPendingBeginEditing();markSearchEditingRequested();
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);searchEdit.requestFocus();
        ShellWindowPolicy.set(owner.getWindow(),ShellWindowPolicy.Mode.EDITING,"search");
        owner.getSystemService(android.view.inputmethod.InputMethodManager.class).showSoftInput(searchEdit,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        updateLibraryHeight();
    }
    boolean editingSearch(){return editingSearch||imeBottom>0;}
    /** Editor observations only: an IME can retain unpublished candidates in its own UI. */
    org.json.JSONObject editorDiagnostics(){
        org.json.JSONObject out=new org.json.JSONObject();
        try{
            android.text.Editable value=searchEdit==null?null:searchEdit.getText();
            android.graphics.Rect frame=new android.graphics.Rect();getWindowVisibleDisplayFrame(frame);
            int[] location=new int[2];getLocationOnScreen(location);WindowInsets insets=getRootWindowInsets();
            android.view.inputmethod.InputMethodManager input=owner.getSystemService(android.view.inputmethod.InputMethodManager.class);
            out.put("present",searchEdit!=null).put("shown",searchEdit!=null&&searchEdit.isShown())
                    .put("focus",searchEdit!=null&&searchEdit.hasFocus()).put("windowFocus",hasWindowFocus())
                    .put("pendingBeginEditing",pendingBeginEditing).put("beginEditingPosted",pendingBeginEditingCallback!=null)
                    .put("connectionGeneration",inputConnectionGeneration).put("connectionAt",inputConnectionAt)
                    .put("connectionActive",searchEdit!=null&&input!=null&&input.isActive(searchEdit))
                    .put("rootHeight",getHeight()).put("rootY",location[1]).put("imeBottom",imeBottom)
                    .put("imeChangedAt",imeChangedAt).put("contentHeight",contentHeight).put("visibleRowCount",visibleRowCount)
                    .put("windowBoundsBottom",android.os.Build.VERSION.SDK_INT>=30?owner.getWindowManager().getCurrentWindowMetrics().getBounds().bottom:getResources().getDisplayMetrics().heightPixels)
                    .put("visibleFrameTop",frame.top).put("visibleFrameBottom",frame.bottom)
                    .put("imeVisible",android.os.Build.VERSION.SDK_INT>=30&&insets!=null?insets.isVisible(WindowInsets.Type.ime()):org.json.JSONObject.NULL)
                    .put("actualText",value==null?"":value.toString()).put("committedText",committedSearchText)
                    .put("composingStart",value==null?-1:BaseInputConnection.getComposingSpanStart(value))
                    .put("composingEnd",value==null?-1:BaseInputConnection.getComposingSpanEnd(value))
                    .put("compositionObservation","editor_spans_only");
        }catch(org.json.JSONException ignored){}
        return out;
    }
    boolean searchKey(KeyEvent e){
        int code=e.getKeyCode();if(!editingSearch)return imeBottom>0&&(code==KeyEvent.KEYCODE_BUTTON_A||code==KeyEvent.KEYCODE_BUTTON_START||code==KeyEvent.KEYCODE_DPAD_CENTER||code==KeyEvent.KEYCODE_ENTER||code==KeyEvent.KEYCODE_BUTTON_B||code==KeyEvent.KEYCODE_BACK||code==KeyEvent.KEYCODE_BUTTON_X||code==KeyEvent.KEYCODE_BUTTON_Y||code==KeyEvent.KEYCODE_BUTTON_SELECT);
        if(code==KeyEvent.KEYCODE_ENTER&&composingSearch)return false; // Let the IME select/commit its current candidate.
        boolean exit=code==KeyEvent.KEYCODE_BUTTON_B||code==KeyEvent.KEYCODE_BACK;
        boolean submit=code==KeyEvent.KEYCODE_BUTTON_A||code==KeyEvent.KEYCODE_BUTTON_START||code==KeyEvent.KEYCODE_DPAD_CENTER||code==KeyEvent.KEYCODE_ENTER;
        if(exit||submit){
            if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getRepeatCount()==0){editingKey=code;editingKeyDownTime=e.getDownTime();}
            if(e.getAction()==KeyEvent.ACTION_UP&&editingKey==code&&editingKeyDownTime==e.getDownTime()){
                editingKey=-1;if(e.isCanceled())return true;syncSearchText();
                if(exit||!composingSearch)finishSearchEditing();else text(searchStateLabel,UiStrings.msg("ui_57b941db7f4b"));
            }
            return true;
        }
        return code==KeyEvent.KEYCODE_BUTTON_X||code==KeyEvent.KEYCODE_BUTTON_Y||code==KeyEvent.KEYCODE_BUTTON_L1||code==KeyEvent.KEYCODE_BUTTON_R1||code==KeyEvent.KEYCODE_BUTTON_SELECT||code==KeyEvent.KEYCODE_BUTTON_THUMBL;
    }
    void finishSearchEditing(){
        boolean pending=pendingBeginEditing||pendingBeginEditingCallback!=null;cancelPendingBeginEditing();
        if(!editingSearch)return;if(!pending)syncSearchText();editingSearch=false;editingKey=-1;setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        if(searchEdit!=null){
            if(composingSearch){settingQuery=true;BaseInputConnection.removeComposingSpans(searchEdit.getText());searchEdit.setText(committedSearchText);searchEdit.setSelection(searchEdit.length());settingQuery=false;}
            owner.getSystemService(android.view.inputmethod.InputMethodManager.class).hideSoftInputFromWindow(searchEdit.getWindowToken(),0);searchEdit.clearFocus();
        }
        composingSearch=false;repo.setSearchComposing(false);repo.setSearchEditing(imeBottom>0);
        requestFocus();requestApplyInsets();updateLibraryHeight();restoreBarsAfterIme();
    }
    private void markSearchComposing(boolean composing){if(!editingSearch)return;if(composingSearch!=composing){composingSearch=composing;repo.setSearchComposing(composing);}}
    private void scheduleSearchTextSync(){if(settingQuery||!editingSearch||searchTextSyncPending)return;searchTextSyncPending=true;post(()->{searchTextSyncPending=false;syncSearchText();});}
    private void syncSearchText(){
        if(settingQuery||pendingBeginEditing||pendingBeginEditingCallback!=null||!editingSearch||searchEdit==null||!repo.snapshot().searchActive)return;
        android.text.Editable value=searchEdit.getText();int start=BaseInputConnection.getComposingSpanStart(value),end=BaseInputConnection.getComposingSpanEnd(value);
        markSearchComposing(start>=0&&end>start);if(composingSearch)return;
        committedSearchText=value.toString();if(!committedSearchText.equals(repo.snapshot().searchQuery))repo.searchQuery(committedSearchText);
    }
    private void clearSearchText(){
        if(searchEdit==null)return;settingQuery=true;BaseInputConnection.removeComposingSpans(searchEdit.getText());searchEdit.setText("");settingQuery=false;
        committedSearchText="";markSearchComposing(false);repo.searchQuery("");
        if(editingSearch)owner.getSystemService(android.view.inputmethod.InputMethodManager.class).restartInput(searchEdit);
    }
    private void emptyStateAction(){
        ShellStateRepository.Snapshot s=repo.snapshot();if(!s.stateReady||s.searchEditing||s.searchBusy||s.searchComposing)return;
        ScanProgress p=boundScan;
        if(p.active()){if(DirectoryAccess.busy())DirectoryAccess.cancel(owner);else RomLibrary.get(owner).cancel();return;}
        if(p.phase.equals("PARTIAL")||p.phase.equals("FAILED")){RomLibrary.get(owner).showReadDiagnostics(owner);return;}
        if(p.attention()&&s.romTreeUri!=null){RomLibrary.get(owner).scanUncached();return;}
        if(s.romTreeUri==null){ShellCoordinator.get().chooseRomDirectory("LIBRARY_EMPTY");return;}
        if(!s.storageAvailable){DirectoryHealth.get(owner).show(owner,false);return;}
        if(s.searchActive){clearSearchText();return;}
        if(!s.genreFilter.equals("all")){repo.setGenre("all");return;}
        if(s.category==ShellStateRepository.Category.FOLDERS){repo.back();return;}
        if(s.category==ShellStateRepository.Category.FAVORITES||s.category==ShellStateRepository.Category.RECENT)repo.setCategory(ShellStateRepository.Category.NDS);
        else ShellCoordinator.get().chooseRomDirectory("LIBRARY_EMPTY");
    }
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);if(!top)updateLibraryHeight();}
    @Override public WindowInsets onApplyWindowInsets(WindowInsets insets){
        if(!top&&android.os.Build.VERSION.SDK_INT>=30){int bottom=insets.getInsets(WindowInsets.Type.ime()).bottom;if(bottom!=imeBottom){imeBottom=bottom;imeChangedAt=SystemClock.uptimeMillis();repo.setSearchEditing(editingSearch||imeBottom>0);updateLibraryHeight();if(bottom==0)restoreBarsAfterIme();PerfTrace.event("IME_GEOMETRY rootHeight="+getHeight()+" imeBottom="+imeBottom+" pageHeight="+contentHeight);}}
        return super.onApplyWindowInsets(insets);
    }
    private void restoreBarsAfterIme(){
        if(!top&&!editingSearch&&imeBottom==0&&!ShellDialogBuilder.hasOpenDialog()&&ShellCoordinator.get().canInteract())
            ShellWindowPolicy.set(owner.getWindow(),ShellWindowPolicy.Mode.BROWSING,"BottomHomeActivity");
    }
    private void updateLibraryHeight(){
        if(top||getHeight()<=0)return;int available=getHeight();
        if(imeBottom>0){
            getLocationOnScreen(screenLocation);getWindowVisibleDisplayFrame(visibleWindow);
            int windowBottom=android.os.Build.VERSION.SDK_INT>=30?owner.getWindowManager().getCurrentWindowMetrics().getBounds().bottom:getResources().getDisplayMetrics().heightPixels;
            available=Math.min(available,Math.max(0,windowBottom-imeBottom-screenLocation[1]));
            if(!visibleWindow.isEmpty())available=Math.min(available,Math.max(0,visibleWindow.bottom-screenLocation[1]));
        }
        contentHeight=Math.max(0,available-40);layoutLibrary();
    }
    private void layoutLibrary(){
        if(top||pageLabel==null)return;boolean input=editingSearch||imeBottom>0;int rowTop=input?120:LIBRARY_ROW_TOP;
        visibleRowCount=Math.max(0,Math.min(6,(contentHeight-rowTop-(input?24:64))/LIBRARY_ROW_PITCH));int count=Math.max(1,visibleRowCount);
        FrameLayout.LayoutParams page=(FrameLayout.LayoutParams)library.getLayoutParams();if(page!=null&&page.height!=contentHeight){page.height=contentHeight;library.setLayoutParams(page);}
        for(int i=0;i<rows.length;i++)geometry(rows[i],rowTop+i*LIBRARY_ROW_PITCH,LIBRARY_ROW_HEIGHT);
        geometry(emptyLabel,LIBRARY_ROW_TOP,Math.max(80,contentHeight-LIBRARY_ROW_TOP-UiMetrics.LIBRARY_STATUS_RESERVED-UiMetrics.EMPTY_TO_STATUS_GAP));
        geometry(libraryStatus,Math.max(0,contentHeight-UiMetrics.LIBRARY_STATUS_RESERVED),UiMetrics.LIBRARY_STATUS_HEIGHT);
        layoutLibraryStatus();
        searchStateLabel.setVisibility(input?VISIBLE:GONE);
        for(TextView v:footerButtons)if(v!=null)geometry(v,Math.max(0,contentHeight-40),40);
        repo.setPageSize(count);ShellStateRepository.Snapshot s=repo.snapshot();
        for(int i=0;i<rows.length;i++)rows[i].setVisibility(!s.typesOverview&&i<visibleRowCount&&(s.selectedIndex/s.pageSize)*s.pageSize+i<s.items.size()?VISIBLE:INVISIBLE);
        applyLibraryActions(s);
    }
    private void applyLibraryActions(ShellStateRepository.Snapshot s){
        if(top||startButton==null)return;boolean input=editingSearch||imeBottom>0;
        boolean game=s.canBrowseGame();boolean updating=s.searchActive&&(s.searchBusy||s.searchEditing||s.searchComposing);
        boolean status=!input&&!s.typesOverview;libraryStatus.setVisibility(status?VISIBLE:GONE);pageLabel.setVisibility(status?VISIBLE:GONE);scanLabel.setVisibility(status?VISIBLE:GONE);
        footerButtons[0].setVisibility(input?GONE:VISIBLE);
        for(int i=1;i<=2;i++){footerButtons[i].setVisibility(!input&&game?VISIBLE:GONE);footerButtons[i].setEnabled(game);}
        text(footerButtons[2],s.selectedGame!=null&&s.isFavorite(s.selectedGame)?UiStrings.msg("ui_cbd2484a0364"):UiStrings.msg("ui_b550716adad2"));
        footerButtons[3].setVisibility(!input&&!s.typesOverview?VISIBLE:GONE);footerButtons[3].setEnabled(!updating);
        text(footerButtons[3],s.searchActive?UiStrings.msg("ui_b60eb48519aa"):s.category==ShellStateRepository.Category.RECENT?UiStrings.msg("ui_f21e774869bf"):UiStrings.msg("ui_7ea101e4f250"));
        startButton.setVisibility(input?GONE:VISIBLE);startButton.setEnabled(!updating&&(s.typesOverview||s.selectedFolder!=null||game));
        text(startButton,s.typesOverview?UiStrings.msg("ui_4b6cbd00369e"):s.selectedFolder!=null?UiStrings.msg("ui_d107036c6b53"):!s.storageAvailable?SetupJourney.text("检查目录","Check folder"):s.items.isEmpty()?UiStrings.msg("ui_29d0fbe5e5fb"):UiStrings.msg("ui_bd7d47428640"));
        text(searchStateLabel,(s.searchComposing?UiStrings.msg("ui_57b941db7f4b"):editingSearch?UiStrings.msg("ui_f75607bd165e"):s.searchBusy?UiStrings.msg("ui_5addbef7de97"):"")+(boundScan.active()?" · "+boundScan.compact():""));
    }
    private void layoutLibraryStatus(){
        if(top||pageLabel==null||scanLabel==null)return;
        final int left=36,right=604,y=Math.max(0,contentHeight-UiMetrics.LIBRARY_STATUS_RESERVED+4);
        DsTypography.ensure(pageLabel);pageLabel.setContentDescription(pageLabel.getText());
        boolean input=editingSearch||imeBottom>0,auxiliary=scanLabel.length()>0;
        int needed=(int)Math.ceil(pageLabel.getPaint().measureText(pageLabel.getText().toString()))+pageLabel.getPaddingLeft()+pageLabel.getPaddingRight()+2;
        int width=auxiliary?Math.min(right-left,Math.max(1,needed)):right-left;
        bounds(pageLabel,left,y,width,28);
        int next=left+width+12;
        if(scanBusy!=null){
            scanBusy.setVisibility(boundScan.active()&&(input||next+18<=right)?VISIBLE:GONE);
            bounds(scanBusy,input?596:Math.min(right-18,next),input?98:y+6,18,18);
            scanBusy.bringToFront();
            if(!input&&boundScan.active())next+=26;
        }
        bounds(scanLabel,Math.min(right,next),y,Math.max(0,right-next),28);
    }
    private String rowText(GameEntry game){return GameDisplay.name(game,metadata);}
    private void buildTopLibrary(){
        // Unattached V11 rulers keep reading-card geometry independent of the new header.
        legacyHeaderTitle=text(owner,"",20);DsTypography.title(legacyHeaderTitle,20);lines(legacyHeaderTitle,2);
        legacyHeaderGenres=text(owner,"",20);lines(legacyHeaderGenres,1);
        legacyHeaderVersion=text(owner,"",20);lines(legacyHeaderVersion,1);
        for(TextView ruler:new TextView[]{legacyHeaderTitle,legacyHeaderGenres,legacyHeaderVersion})ruler.setLayoutParams(new FrameLayout.LayoutParams(592,FrameLayout.LayoutParams.WRAP_CONTENT));
        gameTitle=text(owner,"",26);DsTypography.title(gameTitle,26);gameTitle.setTag("selected_game_title");lines(gameTitle,2);gameTitle.setGravity(Gravity.TOP);gameTitle.setLineSpacing(0,1);place(library,gameTitle,24,8,592,64);
        gameHint=label(owner,"",16,MUTED);gameHint.setTag("selected_game_version");lines(gameHint,1);place(library,gameHint,24,68,592,28);
        favorite=text(owner,"",18);favorite.setTag("selected_game_genres");lines(favorite,1);place(library,favorite,24,96,592,28);favorite.setOnClickListener(v->showCurrentGenres());
        topReadingPanel=panel(owner);topReadingPanel.setBackground(new Edge(false,true));place(library,topReadingPanel,24,74,592,342);
        cover=new CoverView(owner);cover.setBackgroundColor(PAPER);place(library,cover,36,86,268,282);
        readingDivider=new View(owner);readingDivider.setBackgroundColor(LINE);place(library,readingDivider,316,86,1,282);
        descriptionLabel=label(owner,"",15,MUTED);place(library,descriptionLabel,328,86,276,20);
        descriptionText=new DescriptionView(owner);descriptionText.setOnClickListener(v->{if(repo.snapshot().canBrowseGame()&&!ShellDialogBuilder.hasOpenDialog())actions.onDetailsRequested();});descriptionLabel.setOnClickListener(v->{if(repo.snapshot().canBrowseGame()&&!ShellDialogBuilder.hasOpenDialog())actions.onDetailsRequested();});descriptionText.setBackgroundColor(PAPER);place(library,descriptionText,328,114,276,254);
        topStateText=text(owner,"",20);topStateText.setGravity(Gravity.TOP);topStateText.setLineSpacing(6,1);lines(topStateText,8);place(library,topStateText,36,114,568,228);topStateText.setVisibility(GONE);
        readingFooterLine=new View(owner);readingFooterLine.setBackgroundColor(LINE);place(library,readingFooterLine,36,380,568,1);
        save=label(owner,"",15,MUTED);lines(save,1);place(library,save,36,388,268,22);
        recent=label(owner,"",15,MUTED);lines(recent,1);place(library,recent,328,388,276,22);
    }
    private void buildGenres(){
        if(top){
            genreHelp=text(owner,"",23);place(genres,genreHelp,24,0,592,34);
            FrameLayout examples=panel(owner);examples.setTag("genre_examples_panel");place(genres,examples,24,50,592,260);
            for(int n=0;n<genreExamples.length;n++){TextView example=text(owner,"",21);example.setSingleLine(true);example.setEllipsize(TextUtils.TruncateAt.END);genreExamples[n]=example;place(genres,example,40,60+n*40,560,34);}
            TextView hint=label(owner,UiStrings.msg("ui_51600b82b280"),17,MUTED);place(genres,hint,24,354,592,28);
        }
        else for(int i=0;i<13;i++){
            final int n=i;int x=24+(i%UiMetrics.GENRE_COLUMNS)*200,y=(i/UiMetrics.GENRE_COLUMNS)*UiMetrics.GENRE_PITCH;
            genreChoices[i]=button(genres,owner,"",x,y,192,UiMetrics.GENRE_HEIGHT,()->{repo.chooseGenre(n);repo.enterGenre();});
            TextView button=genreChoices[i];button.setTag("genre_"+MetadataIndex.GENRES[i+1]);button.setGravity(Gravity.TOP|Gravity.LEFT);button.setPadding(36,2,8,20);button.setBackground(new Edge(false,true));button.setSingleLine(true);
            View symbol=new View(owner);symbol.setBackground(new DsIcons(16+i,16));symbol.setTag("genre_icon_"+i);place(genres,symbol,x+10,y+16,16,16);
            genreCounts[i]=text(owner,"",16);DsTypography.technical(genreCounts[i],16);genreCounts[i].setTag("genre_count_"+i);place(genres,genreCounts[i],x+36,y+24,144,28);
        }
    }
    private void bindGenres(ShellStateRepository.Snapshot s){
        if(top){String type=MetadataIndex.GENRES[s.genreIndex+1];text(genreHelp,GenreTaxonomy.label(type)+"  ·  "+metadata.genreCount(type)+TaskPresentation.msg(metadata.genreCount(type)==1?"1b":"1c"));int count=0;
            for(GameEntry game:repo.allGamesCopy())if(metadata.hasGenre(game.gameId,type)){text(genreExamples[count],GameDisplay.name(game,metadata));if(++count==genreExamples.length)break;}
            while(count<genreExamples.length)text(genreExamples[count++],"");
        }else for(int i=0;i<13;i++){String id=MetadataIndex.GENRES[i+1];text(genreChoices[i],id.equals("rpg")?UiStrings.msg("ui_d50000000001"):GenreTaxonomy.label(id));text(genreCounts[i],Integer.toString(metadata.genreCount(id)));genreChoices[i].setContentDescription(UiStrings.display(GenreTaxonomy.label(id))+", "+metadata.genreCount(id));focus(genreChoices[i],i==s.genreIndex);}
    }
    private void topGeometry(){
        if(!topSingleColumn){layoutSelectedOverview();return;}
        DsTypography.title(gameTitle,20);lines(gameTitle,2);
        gameTitle.setLineSpacing(0,1);gameTitle.setPadding(0,0,0,0);
        bounds(gameTitle,24,8,592,28);bounds(favorite,24,8,592,28);bounds(gameHint,24,8,592,28);
        String title=gameTitle.getText().toString();int height=gameTitle.getPaint().measureText(title)>592?56:28;
        geometry(gameTitle,8,height);int y=8+height+4;
        if(gameHint.length()>0){gameHint.setVisibility(VISIBLE);geometry(gameHint,y,28);y+=28;}else gameHint.setVisibility(GONE);
        if(favorite.length()>0){favorite.setVisibility(VISIBLE);geometry(favorite,y,28);y+=28;}else favorite.setVisibility(GONE);
        int body=DsGrid.TOP.snapY(y+8);
        save.setGravity(Gravity.CENTER_VERTICAL);recent.setGravity(Gravity.CENTER_VERTICAL);
        bounds(readingFooterLine,36,380,568,1);bounds(save,36,388,268,28);bounds(recent,328,388,276,28);
        boolean footer=!topSingleColumn||save.length()>0||recent.length()>0;
        int bottom=topSingleColumn?Math.min(416,body+280):416,footerTop=footer?bottom-36:bottom;
        geometry(topReadingPanel,body,bottom-body);cover.setVisibility(topSingleColumn?GONE:VISIBLE);readingDivider.setVisibility(topSingleColumn?GONE:VISIBLE);
        if(!topSingleColumn){geometry(cover,body+12,Math.max(1,footerTop-body-24));geometry(readingDivider,body+12,Math.max(1,footerTop-body-24));}
        int textX=topSingleColumn?24+UiMetrics.CARD_INSET:328,textWidth=topSingleColumn?592-2*UiMetrics.CARD_INSET:616-UiMetrics.CARD_INSET-328;
        boolean hasCaption=descriptionLabel.length()>0;descriptionLabel.setVisibility(hasCaption?VISIBLE:GONE);bounds(descriptionLabel,textX+(topSingleColumn?0:12),body+12,textWidth-(topSingleColumn?0:24),28);
        int descriptionTop=body+(!topSingleColumn||hasCaption?44:16);bounds(descriptionText,textX,descriptionTop,textWidth,Math.max(1,footerTop-descriptionTop-12));
        descriptionText.setVisibility(topSingleColumn?GONE:VISIBLE);topStateText.setVisibility(topSingleColumn?VISIBLE:GONE);
        if(topSingleColumn)bounds(topStateText,textX,descriptionTop,textWidth,Math.max(1,footerTop-descriptionTop-12));
        readingFooterLine.setVisibility(footer?VISIBLE:GONE);save.setVisibility(footer?VISIBLE:GONE);recent.setVisibility(footer?VISIBLE:GONE);
        if(footer){geometry(readingFooterLine,footerTop,1);geometry(save,footerTop+4,28);geometry(recent,footerTop+4,28);}
    }
    private void layoutSelectedOverview(){
        final int bottom=424,body=legacyOverviewBody();
        layoutSideHeader(body);
        bounds(topReadingPanel,24,body,592,bottom-body);
        final int footerLine=380,contentBottom=372;
        // Both equal-width columns stop above the shared full-width status footer.
        cover.setVisibility(VISIBLE);cover.setTopAligned(true);bounds(cover,40,body+16,268,Math.max(1,contentBottom-body-16));
        readingDivider.setVisibility(VISIBLE);bounds(readingDivider,320,body+8,1,Math.max(1,contentBottom-body-8));
        descriptionLabel.setVisibility(VISIBLE);bounds(descriptionLabel,344,body+12,252,28);
        descriptionText.setVisibility(VISIBLE);topStateText.setVisibility(GONE);
        descriptionText.setPadding(12,4,4,4);
        bounds(descriptionText,332,body+40,268,Math.max(1,contentBottom-(body+40)));
        readingFooterLine.setVisibility(VISIBLE);bounds(readingFooterLine,32,footerLine,576,1);
        save.setVisibility(VISIBLE);recent.setVisibility(VISIBLE);
        save.setBackground(null);recent.setBackground(null);save.setGravity(Gravity.CENTER_VERTICAL);recent.setGravity(Gravity.CENTER_VERTICAL);
        bounds(save,32,388,276,28);bounds(recent,332,388,276,28);

    }
    private int legacyOverviewBody(){
        GameEntry selected=repo.snapshot().selectedGame;
        String key=(selected==null?"":selected.gameId)+"|"+gameTitle.getText()+"|"+selectedHeaderVersion+"|"+LocaleSettings.revision();
        if(key.equals(headerAnchorKey))return headerAnchorBody;
        text(legacyHeaderTitle,gameTitle.getText().toString());text(legacyHeaderGenres,selectedHeaderGenres);text(legacyHeaderVersion,selectedHeaderVersion);
        measureHeader(legacyHeaderTitle,592,24);int nextInk=16+headerInk(legacyHeaderTitle).height()+6;
        for(TextView row:new TextView[]{legacyHeaderGenres,legacyHeaderVersion})if(row.length()>0){measureHeader(row,592,28);nextInk+=headerInk(row).height()+6;}
        headerAnchorKey=key;headerAnchorBody=((nextInk-6+12+3)/4)*4;return headerAnchorBody;
    }
    private void layoutSideHeader(int body){
        GameEntry selected=repo.snapshot().selectedGame;
        java.util.List<String> ids=selected==null?java.util.Collections.emptyList():metadata.genres(selected.gameId);
        boolean hasVersion=!selectedHeaderVersion.isEmpty();
        String full=selected==null?selectedHeaderGenres:TopHeaderGenres.format(ids,favorite.getPaint(),10000);
        int genresWidth=Math.min(hasVersion?392:592,Math.max(1,(int)Math.ceil(favorite.getPaint().measureText(full))+4));
        int versionWidth=Math.max(1,592-(full.isEmpty()?0:genresWidth+16));
        text(gameHint,hasVersion?android.text.TextUtils.ellipsize(selectedHeaderVersion,gameHint.getPaint(),versionWidth,android.text.TextUtils.TruncateAt.END).toString():"");
        gameHint.setVisibility(hasVersion?VISIBLE:GONE);if(hasVersion)measureHeader(gameHint,versionWidth,28);
        String genres=selected==null?full:TopHeaderGenres.format(ids,favorite.getPaint(),genresWidth-4);
        text(favorite,genres);favorite.setVisibility(genres.isEmpty()?GONE:VISIBLE);lines(favorite,1);measureHeader(favorite,genresWidth,28);
        android.graphics.Rect versionInk=hasVersion?paintedHeaderInk(gameHint):new android.graphics.Rect();
        android.graphics.Rect genreInk=genres.isEmpty()?new android.graphics.Rect():paintedHeaderInk(favorite);
        int metadataHeight=Math.max(versionInk.height(),genreInk.height()),gap=metadataHeight==0?0:12;
        DsTypography.selectedTitle(gameTitle);lines(gameTitle,2);measureHeader(gameTitle,592,34);
        android.graphics.Rect titleInk=paintedHeaderInk(gameTitle);
        if(titleInk.height()+gap+metadataHeight>body-8){lines(gameTitle,1);measureHeader(gameTitle,592,34);titleInk=paintedHeaderInk(gameTitle);}
        int height=titleInk.height()+gap+metadataHeight,topInk=(body-height)/2;
        bounds(gameTitle,24-paintedHeaderLineInk(gameTitle,0).left,topInk-titleInk.top,592,gameTitle.getMeasuredHeight());
        int metadataBottom=topInk+height;
        if(hasVersion)bounds(gameHint,24-paintedHeaderLineInk(gameHint,0).left,metadataBottom-versionInk.bottom,versionWidth,gameHint.getMeasuredHeight());
        if(!genres.isEmpty())bounds(favorite,616-genreInk.right,metadataBottom-genreInk.bottom,genresWidth,favorite.getMeasuredHeight());
    }
    private android.graphics.Rect paintedHeaderLineInk(TextView view,int line){
        android.text.Layout layout=view.getLayout();int start=layout.getLineStart(line),end=layout.getLineEnd(line);
        String visible=view.getText().subSequence(start,end).toString();int hidden=layout.getEllipsisCount(line),at=layout.getEllipsisStart(line);
        if(hidden>0)visible=visible.substring(0,at)+"…"+visible.substring(Math.min(visible.length(),at+hidden));
        visible=visible.replace("\n","");android.graphics.Rect ink=new android.graphics.Rect();view.getPaint().getTextBounds(visible,0,visible.length(),ink);
        ink.offset(view.getPaddingLeft()+(int)Math.floor(layout.getLineLeft(line)),view.getPaddingTop()+layout.getLineBaseline(line));return ink;
    }
    private android.graphics.Rect paintedHeaderInk(TextView view){
        android.graphics.Rect ink=new android.graphics.Rect();android.text.Layout layout=view.getLayout();
        for(int line=0;line<layout.getLineCount();line++)ink.union(paintedHeaderLineInk(view,line));return ink;
    }
    private android.graphics.Rect headerLineInk(TextView view,int line){
        android.text.Layout layout=view.getLayout();android.graphics.Rect ink=new android.graphics.Rect();
        String text=view.getText().subSequence(layout.getLineStart(line),layout.getLineEnd(line)).toString().replace("\n","");
        view.getPaint().getTextBounds(text,0,text.length(),ink);
        ink.offset(view.getPaddingLeft(),view.getPaddingTop()+layout.getLineBaseline(line));return ink;
    }
    private android.graphics.Rect headerInk(TextView view){
        android.graphics.Rect ink=new android.graphics.Rect();android.text.Layout layout=view.getLayout();
        if(layout!=null)for(int line=0;line<layout.getLineCount();line++)ink.union(headerLineInk(view,line));
        return ink;
    }
    private void measureHeader(TextView view,int width,int pitch){
        view.setGravity(Gravity.TOP|Gravity.LEFT);view.setPadding(0,0,0,0);
        android.graphics.Paint.FontMetricsInt metrics=view.getPaint().getFontMetricsInt();
        view.setLineSpacing(pitch-(metrics.descent-metrics.ascent),1);
        // A direct measurement must not reuse the preceding title's measured-height cache.
        view.forceLayout();view.measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
        android.text.Layout layout=view.getLayout();int extraPitch=0;
        for(int line=1;line<layout.getLineCount();line++)extraPitch=Math.max(extraPitch,4-(headerLineInk(view,line).top-headerLineInk(view,line-1).bottom));
        if(extraPitch>0){view.setLineSpacing(pitch+extraPitch-(metrics.descent-metrics.ascent),1);view.forceLayout();view.measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));}
        android.graphics.Rect ink=headerInk(view);int before=Math.max(0,-ink.top),after=Math.max(0,ink.bottom-view.getMeasuredHeight());
        if(before>0||after>0){view.setPadding(0,before,0,after);view.forceLayout();view.measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));}
    }
    private void geometry(View v,int y,int height){FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)v.getLayoutParams();bounds(v,p.leftMargin,y,p.width,height);}
    private void bindTaskEntry(){
        ShellStateRepository.Snapshot page=repo.snapshot();boolean atHome=page.page==ShellStateRepository.Page.HOME;
        CoverTask.Snapshot s=metadata.task();boolean active=!s.terminal()&&s.state!=CoverTask.State.IDLE;
        boolean unread=metadata.receipts!=null&&metadata.receipts.unread(TaskReceipts.key(s));
        taskEntry.setVisibility(page.page==ShellStateRepository.Page.TASKS||editingSearch||imeBottom>0?GONE:VISIBLE);
        text(taskEntry,atHome?"":UiStrings.msg("ui_5253040db864"));taskEntry.setTextColor(active||unread?ACCENT:INK);taskEntry.setActivated(active||unread);
        taskEntry.setContentDescription(UiStrings.display(UiStrings.msg("ui_085afb40867c"))+s.title());
        if(atHome){
            taskEntry.setBackgroundTintList(null);taskEntry.setAlpha(1f);
            if(!(taskEntry.getBackground() instanceof HomeUi.Surface)){taskEntry.setBackground(new HomeUi.Surface(HomeUi.TASKS));taskEntry.setCompoundDrawables(null,null,null,null);taskEntry.setPadding(0,0,0,0);}
            // Rebind the shared toolbar control to the left member of the fixed HOME pair.
            int[] r=HomeLayout.ENTRIES[5];bounds(taskEntry,r[0],r[1],r[2],r[3]);
        }else{
            if(!(taskEntry.getBackground() instanceof Edge)){taskEntry.setBackground(new Edge(false));taskEntry.setCompoundDrawables(new DsIcons(DsIcons.TASKS,16),null,null,null);taskEntry.setPadding(8,2,8,2);}
            bounds(taskEntry,page.page==ShellStateRepository.Page.LIBRARY?384:496,4,page.page==ShellStateRepository.Page.LIBRARY?104:120,32);
        }
        focus(taskEntry,atHome&&page.homeIndex==5);taskEntry.bringToFront();
    }
    private void bounds(View v,int x,int y,int width,int height){FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)v.getLayoutParams();if(v.getParent() instanceof FrameLayout&&(v.getBackground() instanceof Edge||v.getBackground() instanceof TabFace||v.getBackground() instanceof InputEdge)){int[] r=DsGrid.align((FrameLayout)v.getParent(),x,y,width,height);x=r[0];y=r[1];width=r[2];height=r[3];}if(p.leftMargin!=x||p.topMargin!=y||p.width!=width||p.height!=height){p.leftMargin=x;p.topMargin=y;p.width=width;p.height=height;v.setLayoutParams(p);}}
    private void topContentVisible(boolean show){for(View v:new View[]{gameTitle,gameHint,favorite,topReadingPanel,cover,readingDivider,readingFooterLine,descriptionLabel,descriptionText,topStateText,save,recent})if(v!=null)v.setVisibility(show&&!(topSingleColumn&&(v==cover||v==readingDivider||v==descriptionText))&&!(!topSingleColumn&&v==topStateText)?VISIBLE:GONE);}
    private void showCurrentGenres(){ShellStateRepository.Snapshot s=repo.snapshot();if(s.selectedGame!=null&&!s.searchEditing&&!s.searchComposing&&!s.searchBusy&&!s.typesOverview&&s.selectedFolder==null&&!ShellDialogBuilder.hasOpenDialog())UiDialogs.gameGenres(owner,s.selectedGame);}

    private TextView infoLine(FrameLayout parent,String s,int y){
        TextView t=text(owner,s,21);place(parent,t,40,y,560,35);
        View line=new View(owner);line.setBackgroundColor(Color.rgb(211,218,218));place(parent,line,40,y+39,560,1);return t;
    }
    private void buildSettings(){
        for(int i=0;i<UiMetrics.ROWS;i++){
            final int slot=i;FrameLayout row=panel(owner);settingRows[i]=row;place(settings,row,UiMetrics.LEFT,16+i*UiMetrics.ROW_STEP,UiMetrics.WIDTH,UiMetrics.ROW);
            row.setTag("setting_row_"+i);settingLabels[i]=text(owner,"",20);DsTypography.ui(settingLabels[i]);place(row,settingLabels[i],16,4,400,28);
            settingDescriptions[i]=label(owner,"",15,MUTED);lines(settingDescriptions[i],1);place(row,settingDescriptions[i],16,24,412,28);
            settingValues[i]=label(owner,"",17,ACCENT);settingValues[i].setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);lines(settingValues[i],1);place(row,settingValues[i],438,6,138,44);
            row.setOnClickListener(v->{ShellStateRepository.Snapshot s=repo.snapshot();int index=(s.settingsIndex/UiMetrics.ROWS)*UiMetrics.ROWS+slot;repo.selectSetting(index);ShellCoordinator.get().performSettingFromEitherScreen(index);});
        }
        settingInfo=label(owner,"",15,MUTED);lines(settingInfo,2);if(android.os.Build.VERSION.SDK_INT>=28)settingInfo.setLineHeight(20);place(settings,settingInfo,24,336,592,48);
        button(settings,owner,UiStrings.msg("ui_bec6ab1883ad"),24,384,128,44,actions::onBackRequested);
        settingsPrevious=button(settings,owner,UiStrings.msg("ui_c9b9ae7a6144"),160,384,88,44,()->repo.pageSetting(-1));
        settingsNext=button(settings,owner,UiStrings.msg("ui_8a8542f69648"),256,384,88,44,()->repo.pageSetting(1));
        button(settings,owner,UiStrings.msg("ui_498e1d59b4d7"),496,384,120,44,()->ShellCoordinator.get().exit(owner));
    }
    private void buildTopSettings(){
        FrameLayout p=panel(owner);place(settings,p,24,28,592,358);
        settingInfo=text(owner,"",20);settingInfo.setGravity(Gravity.TOP);settingInfo.setLineSpacing(5,1);
        // Native upper preview has a fixed height. Use font line height and
        // declared padding, never TextView's content-dependent total padding.
        int previewHeight=317;
        lines(settingInfo,Math.max(1,(int)((previewHeight+settingInfo.getLineSpacingExtra())/settingInfo.getLineHeight())));
        place(p,settingInfo,24,22,544,previewHeight);
        TextView hint=label(owner,UiStrings.msg("ui_58e382bd001f"),17,MUTED);hint.setGravity(Gravity.CENTER);place(settings,hint,24,398,592,28);
    }
    private void buildDetails(){
        detailGameTab=button(details,owner,UiStrings.msg("ui_e081321dee47"),24,8,192,44,()->repo.setDetailTab(0));
        detailFileTab=button(details,owner,UiStrings.msg("ui_800e19b42961"),224,8,192,44,()->repo.setDetailTab(1));
        detailCorrectButton=button(details,owner,UiStrings.msg("ui_a24d74d6915b"),424,8,192,44,()->{ShellStateRepository.Snapshot s=repo.snapshot();if(s.returningToSettings()){PerfTrace.setEnabled(owner,!PerfTrace.isEnabled());text(detailCorrectButton,PerfTrace.isEnabled()?UiStrings.msg("ui_b2793c3ef951"):UiStrings.msg("ui_353a09283917"));readingText(detailText,diagnostics(owner,s));}else UiDialogs.correct(owner,s.selectedGame);});
        detailScroll=new ScrollView(owner);detailScroll.setFillViewport(false);detailScroll.setBackground(new Edge(false));detailScroll.setPadding(2,2,2,2);detailScroll.setClipToPadding(true);place(details,detailScroll,24,60,592,312);
        detailText=text(owner,"",19);detailText.setGravity(Gravity.TOP);detailText.setPadding(18,16,18,18);detailText.setLineSpacing(5,1);detailScroll.addView(detailText,new ScrollView.LayoutParams(-1,-2));
        detailText.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->updateDetailLimit());
        detailScroll.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->updateDetailLimit());
        detailFooter=button(details,owner,UiStrings.msg("ui_190475c54441"),24,384,592,44,actions::onBackRequested);
    }

    private void updateDetailLimit(){
        if(top||detailText==null||detailScroll==null||repo.snapshot().page!=ShellStateRepository.Page.DETAILS)return;
        repo.setDetailLimit(Math.max(0,detailText.getHeight()+detailScroll.getPaddingTop()+detailScroll.getPaddingBottom()-detailScroll.getHeight()));
    }
    private void buildFilter(){
        FrameLayout panel=panel(owner);place(filter,panel,24,16,592,360);
        TextView title=text(owner,UiStrings.msg("ui_0940d41da308"),24);place(panel,title,20,8,280,36);button(panel,owner,UiStrings.msg("ui_21517c09a6e0"),340,8,216,36,()->UiDialogs.genres(owner));
        for(int i=0;i<6;i++){
            final int choice=i;
            filterLabels[i]=button(panel,owner,"",16,52+i*48,560,44,()->repo.activateFilterAt(choice));
        }
        filterPage=label(owner,"",16,MUTED);place(filter,filterPage,24,384,592,44);
        jump=new SeekBar(owner);jump.setMin(0);place(panel,jump,300,100,260,44);
        filterLabels[1].setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        filterLabels[1].setPadding(18,0,0,0);
        jump.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        jump.setThumb(new SeekThumb());jump.setProgressDrawable(ClassicUi.progress());
        jump.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int p,boolean user){if(user)repo.setJumpPage(p+1);}
            public void onStartTrackingTouch(SeekBar s){}
            public void onStopTrackingTouch(SeekBar s){}
        });
        jump.setVisibility(top?GONE:VISIBLE);
    }

    void bind(ShellStateRepository.Snapshot s){
        boundScan=RomLibrary.get(owner).progress();long begin=SystemClock.uptimeMillis();
        if(!top&&(s.page!=ShellStateRepository.Page.LIBRARY||!s.searchActive)
                &&(editingSearch||pendingBeginEditing||pendingBeginEditingCallback!=null))finishSearchEditing();
        // Each page is initialized once, on first use; subsequent binds reuse its exact views.
        if(s.page==ShellStateRepository.Page.HOME&&!builtHome){if(top)buildTopHome();else buildHome();builtHome=true;}
        if((s.page==ShellStateRepository.Page.LIBRARY||top&&s.page==ShellStateRepository.Page.DETAILS&&!s.returningToSettings())&&!builtLibrary){if(top)buildTopLibrary();else buildLibrary();builtLibrary=true;}
        if((s.page==ShellStateRepository.Page.SETTINGS||top&&s.page==ShellStateRepository.Page.DETAILS&&s.returningToSettings())&&!builtSettings){if(top)buildTopSettings();else buildSettings();builtSettings=true;}
        if(!top&&s.page==ShellStateRepository.Page.DETAILS&&!builtDetails){buildDetails();builtDetails=true;}
        if(s.typesOverview&&!builtGenres){buildGenres();builtGenres=true;}
        if(s.page==ShellStateRepository.Page.FILTER&&!builtFilter){buildFilter();builtFilter=true;}
        if(s.page==ShellStateRepository.Page.PREPARING&&setupView==null){setupView=new SetupScreen(owner,top);place(preparation,setupView,0,0,640,440);}
        if(s.page==ShellStateRepository.Page.TASKS&&taskView==null){taskView=new TaskScreen(owner,top);place(tasks,taskView,0,0,640,440);}
        ShellStateRepository.Snapshot old=previous;previous=s;
        // Scan-only publications cannot reload the unchanged selected game's upper-screen content.
        if(top&&old!=null&&old.page==s.page&&s.page==ShellStateRepository.Page.LIBRARY&&s.selectedGame!=null&&old.selectedGame==s.selectedGame&&old.listRevision==s.listRevision&&old.favoriteIds==s.favoriteIds&&old.storageAvailable==s.storageAvailable&&!old.scanState.equals(s.scanState))return;
        if(!top&&(old==null||old.page!=s.page))restoreBarsAfterIme();
        if(!top){boolean browsing=s.page==ShellStateRepository.Page.LIBRARY;searchButton.setVisibility(browsing?VISIBLE:GONE);status.setVisibility(GONE);taskEntry.setVisibility(editingSearch||imeBottom>0?GONE:VISIBLE);if(browsing)text(searchButton,s.searchActive?UiStrings.msg("ui_5ee87e65261b"):UiStrings.msg("ui_0c048f4df0b5"));}

        if(old==null||old.page!=s.page){
            layoutPageChrome(s);
            home.setVisibility(s.page==ShellStateRepository.Page.HOME?VISIBLE:GONE);
            library.setVisibility(s.page==ShellStateRepository.Page.LIBRARY||top&&s.page==ShellStateRepository.Page.DETAILS&&!s.returningToSettings()?VISIBLE:GONE);
            settings.setVisibility(s.page==ShellStateRepository.Page.SETTINGS||top&&s.page==ShellStateRepository.Page.DETAILS&&s.returningToSettings()?VISIBLE:GONE);
            details.setVisibility(!top&&s.page==ShellStateRepository.Page.DETAILS?VISIBLE:GONE);
            filter.setVisibility(s.page==ShellStateRepository.Page.FILTER?VISIBLE:GONE);
            tasks.setVisibility(s.page==ShellStateRepository.Page.TASKS?VISIBLE:GONE);
            preparation.setVisibility(s.page==ShellStateRepository.Page.PREPARING?VISIBLE:GONE);
            text(heading,s.page==ShellStateRepository.Page.HOME?"TwinGrid":
                    s.page==ShellStateRepository.Page.GENRES?UiStrings.msg("ui_1a9a02338a37"):s.page==ShellStateRepository.Page.LIBRARY?UiStrings.msg("ui_c71bdd4fb6c1"):
                    s.page==ShellStateRepository.Page.SETTINGS?UiStrings.msg("ui_ba778fd157de"):
                    s.page==ShellStateRepository.Page.DETAILS?UiStrings.msg("ui_e6f1802d5388"):UiStrings.msg("ui_cb3b0d25b019"));
        }
        if(!top)text(heading,s.page==ShellStateRepository.Page.HOME?"TwinGrid":s.page==ShellStateRepository.Page.LIBRARY?UiStrings.msg("ui_f4c9c9027065"):s.page==ShellStateRepository.Page.TASKS?UiStrings.msg("ui_00b514c36a6c"):s.page==ShellStateRepository.Page.SETTINGS?UiStrings.msg("ui_df3d58c7d84b"):s.page==ShellStateRepository.Page.DETAILS?UiStrings.msg("ui_7523035d2123"):UiStrings.msg("ui_0940d41da308"));
        if(s.page==ShellStateRepository.Page.PREPARING){text(heading,SetupJourney.text("首次准备","Preparation"));setupView.bind();}
        if(s.page==ShellStateRepository.Page.TASKS){if(top)text(heading,UiStrings.msg("ui_552ce5566a77"));taskView.bind();}
        if(!top)bindTaskEntry();
        genres.setVisibility(s.page==ShellStateRepository.Page.LIBRARY&&s.typesOverview?VISIBLE:GONE);
        if(s.page==ShellStateRepository.Page.HOME)bindHome(s);
        else if(s.page==ShellStateRepository.Page.LIBRARY){
            if(top)bindTopLibrary(s);else bindLibrary(s,old);
        }else if(s.page==ShellStateRepository.Page.SETTINGS)bindSettings(s);
        else if(s.page==ShellStateRepository.Page.DETAILS){
            if(top){if(s.returningToSettings())readingText(settingInfo,metadata.summary());else bindTopLibrary(s);return;}
            if(!s.returningToSettings()&&s.selectedGame!=null&&(old==null||old.page!=s.page||old.selectedGame!=s.selectedGame)){String id=s.selectedGame.gameId;metadata.prepareDetails(id,()->{ShellStateRepository.Snapshot current=repo.snapshot();if(current.page==ShellStateRepository.Page.DETAILS&&!current.returningToSettings()&&current.detailTab==0&&current.selectedGame!=null&&current.selectedGame.gameId.equals(id))readingText(detailText,UiDialogs.gameInfo(owner,current.selectedGame));});}
            focus(detailGameTab,s.detailTab==0);focus(detailFileTab,s.detailTab==1);
            detailGameTab.setVisibility(s.returningToSettings()?INVISIBLE:VISIBLE);detailFileTab.setVisibility(s.returningToSettings()?INVISIBLE:VISIBLE);detailCorrectButton.setVisibility(!s.returningToSettings()&&s.selectedGame==null?INVISIBLE:VISIBLE);text(detailCorrectButton,s.returningToSettings()?(PerfTrace.isEnabled()?UiStrings.msg("ui_b2793c3ef951"):UiStrings.msg("ui_353a09283917")):UiStrings.msg("ui_a24d74d6915b"));
            text(detailFooter,s.returningToSettings()?UiStrings.msg("ui_fc518cef07c1"):UiStrings.msg("ui_190475c54441"));
            String value=s.returningToSettings()?diagnostics(owner,s):s.detailTab==0?UiDialogs.gameInfo(owner,s.selectedGame):technical(owner,s.selectedGame,s);
            if(!value.contentEquals(detailText.getText())){readingText(detailText,value);detailScroll.scrollTo(0,0);}
            detailScroll.scrollTo(0,s.detailOffset);
            detailScroll.post(()->{
                if(previous==s&&s.page==ShellStateRepository.Page.DETAILS){
                    repo.setDetailLimit(Math.max(0,detailText.getHeight()+detailScroll.getPaddingTop()+detailScroll.getPaddingBottom()-detailScroll.getHeight()));
                    detailScroll.scrollTo(0,s.detailOffset);
                }
            });
        }else if(s.page==ShellStateRepository.Page.FILTER)bindFilter(s);
        if(s.inputTime>0&&(old==null||old.inputSequence!=s.inputSequence)){
            { if(PerfTrace.isEnabled()) android.util.Log.i("TwinGridPerf","SUBMIT display="+(top?2:0)+" sequence="+s.inputSequence+
                " event="+s.inputTime+" callback="+s.callbackTime+" state="+s.stateTime+
                " uptime="+SystemClock.uptimeMillis()+" bindMs="+(SystemClock.uptimeMillis()-begin)); }
        }
    }
    private void bindHome(ShellStateRepository.Snapshot s){
        GameEntry g=s.selectedGame;
        if(top){
            String notice="";
            if(s.homeIndex==0){
                if(!s.stateReady)notice=UiStrings.msg("ui_95717615adbe");
                else if(s.romTreeUri==null)notice=UiStrings.msg("ui_28297a18a5fa");
                else if(!s.storageAvailable)notice=DirectoryHealth.notice(s.romAccess);
                else if(g==null)notice=UiStrings.msg("ui_0d76a5b95647");
            }
            text(homeInfo,notice);homeInfo.setVisibility(notice.isEmpty()?GONE:VISIBLE);
        }else{
            boolean canShow=g!=null&&s.storageAvailable;
            homeIcon.bind(canShow?g:null);
            text(homeGame,s.stateReady&&s.romTreeUri==null?UiStrings.msg("ui_97a678c88209"):canShow?GameDisplay.name(g,metadata):s.stateReady&&!s.storageAvailable&&s.romTreeUri!=null?DirectoryHealth.label(s.romAccess):UiStrings.msg("ui_551e846a4ac6"));
            android.text.TextPaint titlePaint=new android.text.TextPaint(homeGame.getPaint());DsTypography.paint(owner,titlePaint,30);
            String title=homeGame.getText().toString(),displayTitle=homeTitleLines(title,titlePaint);
            android.text.StaticLayout titleLayout=android.text.StaticLayout.Builder.obtain(displayTitle,0,displayTitle.length(),titlePaint,344).setIncludePad(false).setBreakStrategy(android.text.Layout.BREAK_STRATEGY_BALANCED).setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE).build();
            boolean primary=titleLayout.getLineCount()<=2;
            if(!DsTypography.role(homeGame).equals(primary?DsTypography.HOME_PRIMARY:DsTypography.HOME_SECONDARY))DsTypography.home(homeGame,primary);
            if(!primary){DsTypography.paint(owner,titlePaint,20);displayTitle=homeTitleLines(title,titlePaint);}text(homeGame,displayTitle);
            text(homeCount,s.libraryReady?s.totalGameCount+UiStrings.msg("ui_64c026ac4dd6"):UiStrings.msg("ui_39a25feec6ca"));
            text(homeFavorites,s.favoriteCount+UiStrings.msg("ui_a6448f2038d4"));
            text(homeRecent,s.recentCount==0?UiStrings.msg("ui_41929be0bae2"):s.recentCount+UiStrings.msg("ui_64c026ac4dd6"));
            String[] names={homeGame.getText().toString(),UiStrings.display(UiStrings.msg("ui_b8eb5f068caf")),UiStrings.display(UiStrings.msg("ui_9385bfefca58")),UiStrings.display(UiStrings.msg("ui_dbd67e367a67")),UiStrings.display(UiStrings.msg("ui_df3d58c7d84b"))};
            for(int i=0;i<5;i++){homePanels[i].setContentDescription(names[i]);focus(homePanels[i],i==s.homeIndex);}
        }
    }
    private void bindLibrary(ShellStateRepository.Snapshot s,ShellStateRepository.Snapshot old){
        ShellStateRepository.Category[] navigation={ShellStateRepository.Category.NDS,ShellStateRepository.Category.TYPES,ShellStateRepository.Category.FOLDERS,ShellStateRepository.Category.FAVORITES,ShellStateRepository.Category.RECENT};
        for(int i=0;i<5;i++){text(tabs[i],navigation[i].label);tabs[i].setSelected(s.category==navigation[i]);tabs[i].setTextColor(s.category==navigation[i]?ACCENT:INK);}
        searchEdit.setVisibility(s.searchActive?VISIBLE:GONE);scopeLabel.setVisibility(s.searchActive?GONE:VISIBLE);sortLabel.setVisibility(s.typesOverview?INVISIBLE:VISIBLE);
        applyLibraryActions(s);
        scanBusy.setVisibility(boundScan.active()?VISIBLE:GONE);scanBusy.active(boundScan.active());
        text(scanLabel,boundScan.attention()?scanStatusText():"");
        scanLabel.setContentDescription(boundScan.attention()?boundScan.panel():"");
        if(s.typesOverview){
            text(pageLabel,"");
            scanBusy.setVisibility(GONE);
            for(View row:rows)row.setVisibility(INVISIBLE);emptyLabel.setVisibility(GONE);clearFilter.setVisibility(INVISIBLE);
            text(scopeLabel,UiStrings.msg("ui_2c15f0da9558")+s.totalGameCount+UiStrings.msg("ui_65fa1a09242d"));scopeLabel.setContentDescription(UiStrings.display(scopeLabel.getText()+" · "+UiStrings.msg("ui_9cb5cd37c956")));
            bindGenres(s);boundList=-1;return;
        }
        boolean empty=s.items.isEmpty();emptyLabel.setVisibility(empty&&!editingSearch&&imeBottom==0?VISIBLE:GONE);
        if(empty)text(emptyLabel,emptyStateText(s));
        int page=s.selectedIndex/s.pageSize;boolean rebind=boundLocale!=LocaleSettings.revision()||boundPage!=page||boundList!=s.listRevision||old==null||old.items!=s.items;
        if(editionItems!=s.items||editionLocale!=LocaleSettings.revision()){editionItems=s.items;editionLocale=LocaleSettings.revision();editionLabels=EditionLabels.forItems(s.items,metadata);}
        if(rebind){boundLocale=LocaleSettings.revision();boundPage=page;boundList=s.listRevision;for(int i=0;i<6;i++){
            int index=page*s.pageSize+i;boolean visible=i<s.pageSize&&i<visibleRowCount&&index<s.items.size();rows[i].setVisibility(visible?VISIBLE:INVISIBLE);
            if(!visible)rowBindings[i]=null;
            if(visible){DirectoryIndex.Item item=s.items.get(index);String signature=item.key()+"|"+(item.folder!=null?item.folder.name:rowText(item.game)+System.identityHashCode(item.game.iconData))+"|"+editionLabels.getOrDefault(item.key(),"")+"|"+LocaleSettings.revision();if(signature.equals(rowBindings[i]))continue;rowBindings[i]=signature;rowBindingUpdates++;if(item.folder!=null){text(rowTitles[i],item.folder.name);rowIcons[i].bindFolder(item.folder.id);}else{text(rowTitles[i],rowText(item.game));rowIcons[i].bind(item.game);}
                String edition=item.game==null?"":editionLabels.getOrDefault(item.game.gameId,"");text(rowEditions[i],edition);
                int badge=edition.isEmpty()?0:Math.min(126,Math.max(30,(int)Math.ceil(rowEditions[i].getPaint().measureText(edition))));
                rowEditions[i].setVisibility(edition.isEmpty()?GONE:VISIBLE);bounds(rowEditions[i],520-badge,UiMetrics.ROW_TEXT_Y,badge,UiMetrics.ROW_TEXT_HEIGHT);
                bounds(rowTitles[i],UiMetrics.ROW_TITLE_X,UiMetrics.ROW_TEXT_Y,460-(badge==0?0:badge+8),UiMetrics.ROW_TEXT_HEIGHT);
            }
        }}
        if(rebind||old==null||old.favoriteIds!=s.favoriteIds)for(int i=0;i<6&&page*s.pageSize+i<s.items.size();i++){
            DirectoryIndex.Item item=s.items.get(page*s.pageSize+i);text(rowMarks[i],item.folder!=null?(s.genreFilter.equals("all")?item.folder.totalGames+UiStrings.msg("ui_f2ee580869fe"):item.matchingGames+"/"+item.folder.totalGames):(item.game.hasSave?UiStrings.msg("ui_84fe0a63a434"):""));rowMarks[i].setCompoundDrawables(item.game!=null&&s.isFavorite(item.game)?new DsIcons(DsIcons.FAVORITE,16):null,null,null,null);
        }
        if(rebind||old==null){for(int i=0;i<6;i++)focus(rows[i],page*s.pageSize+i==s.selectedIndex&&!empty);}
        else if(old.selectedIndex!=s.selectedIndex){focus(rows[old.selectedIndex%s.pageSize],false);focus(rows[s.selectedIndex%s.pageSize],true);}
        searchEdit.setVisibility(s.searchActive?VISIBLE:GONE);scopeLabel.setVisibility(s.searchActive?GONE:VISIBLE);
        if(s.searchActive&&(!editingSearch||pendingBeginEditing)&&!s.searchQuery.contentEquals(searchEdit.getText())){settingQuery=true;searchEdit.setText(s.searchQuery);searchEdit.setSelection(searchEdit.length());settingQuery=false;}
        text(scopeLabel,s.category==ShellStateRepository.Category.FOLDERS?UiStrings.msg("ui_f76c1468225a")+s.folderPath+UiStrings.msg("ui_0b556c3b6d9d"):s.category==ShellStateRepository.Category.TYPES?UiStrings.msg("ui_dad6577a91dd")+GenreTaxonomy.label(s.genreFilter)+UiStrings.msg("ui_2c515b3e2e59"):s.category.label+UiStrings.msg("ui_2c515b3e2e59"));
        text(sortLabel,s.searchActive?(s.searchScoped?UiStrings.msg("ui_e04531121e86"):UiStrings.msg("ui_b7c94e4a08a3")):GenreTaxonomy.label(s.genreFilter)+"");sortLabel.setCompoundDrawables(null,null,new DsIcons(29,16),null);sortLabel.setContentDescription(UiStrings.display(UiStrings.msg("ui_45d50a18dc43")+GenreTaxonomy.label(s.genreFilter)));clearFilter.setVisibility(s.searchActive||!s.genreFilter.equals("all")?VISIBLE:INVISIBLE);
        text(pageLabel,(page+1)+" / "+Math.max(1,(s.items.size()+s.pageSize-1)/s.pageSize)+UiStrings.msg("ui_b7425c2ae9c0")+(s.category==ShellStateRepository.Category.FOLDERS&&!s.searchActive?s.items.size()+UiStrings.msg(s.items.size()==1?"ui_d100000001":"ui_d100000002"):s.games.size()+TaskPresentation.msg(s.games.size()==1?"1b":"1c")));
        text(scanLabel,boundScan.attention()?scanStatusText():s.searchActive?(s.searchBusy?UiStrings.msg("ui_d93765d316c0"):s.searchScopeLabel):s.romTreeUri==null?JourneyGuide.s(3):!s.storageAvailable?DirectoryHealth.label(s.romAccess):s.category==ShellStateRepository.Category.FOLDERS?(s.genreFilter.equals("all")?UiStrings.msg("ui_9c2b015d5b46"):UiStrings.msg("ui_f0bc66d16cda")):"");
        layoutLibraryStatus();
        if(rebind)PerfTrace.event("VISIBLE_ROWS updates="+rowBindingUpdates+" selected="+(s.selectedGame==null?"":s.selectedGame.gameId)+" scan="+boundScan.phase);
        if(s.selectedGame!=null&&(old==null||old.selectedGame!=s.selectedGame)){
            java.util.List<GameEntry> currentPage=new java.util.ArrayList<>();for(int i=page*s.pageSize;i<Math.min(s.items.size(),page*s.pageSize+s.pageSize);i++)if(s.items.get(i).game!=null)currentPage.add(s.items.get(i).game);
            metadata.prioritize(s.selectedGame,currentPage);
        }
        if(pendingBeginEditing){updateLibraryHeight();postBeginSearchEditing();}
    }
    private String scanStatusText(){
        return (boundScan.phase.equals("PARTIAL")||boundScan.phase.equals("FAILED")?SetupJourney.text("查看读取原因","Read details")+" · ":"")+boundScan.compact();
    }
    private String emptyStateText(ShellStateRepository.Snapshot s){
        if(!s.stateReady)return UiStrings.msg("ui_95717615adbe");
        ScanProgress progress=boundScan;
        if(progress.attention())return (s.totalGameCount>0&&progress.active()?JourneyGuide.s(60)+"\n\n":"")+progress.panel();
        if(s.romTreeUri==null)return JourneyGuide.s(26);
        if(!s.storageAvailable)return DirectoryHealth.notice(s.romAccess);
        if(s.searchActive)return s.searchBusy?UiStrings.msg("ui_bbe13eb92758"):s.searchScoped?UiStrings.msg("ui_aba835d41b80"):TaskPresentation.msg("19");
        if(!s.libraryReady)return s.scanState;
        if(!s.genreFilter.equals("all"))return UiStrings.msg("ui_e404ebe0a0a8");
        if(s.category==ShellStateRepository.Category.FOLDERS)return UiStrings.msg("ui_99c36dcbd065");
        if(s.category==ShellStateRepository.Category.FAVORITES)return UiStrings.msg("ui_763f295b200a");
        if(s.category==ShellStateRepository.Category.RECENT)return UiStrings.msg("ui_927ec36c05f6");
        return UiStrings.msg("ui_e6f70897f436");
    }
    private void bindTopLibrary(ShellStateRepository.Snapshot s){
        GameEntry selected=s.selectedGame;String next=selected==null?"":selected.gameId+"|"+System.identityHashCode(selected)+"|"+s.isFavorite(selected)+"|"+s.storageAvailable+"|"+(s.backupTreeUri!=null)+"|"+s.searchActive+"|"+s.searchEditing+"|"+s.searchComposing+"|"+s.searchBusy+"|"+LocaleSettings.revision();
        if(!next.isEmpty()&&!s.typesOverview&&s.selectedFolder==null&&!topContentDirty&&next.equals(topContentKey))return;topContentKey=next;topContentDirty=false;
        topContentUpdates++;
        if(s.typesOverview){topContentVisible(false);bindGenres(s);return;}
        boolean waiting=s.searchActive&&(s.searchEditing||s.searchComposing||s.searchBusy);
        GameEntry g=waiting?null:s.selectedGame;DirectoryIndex.Folder f=s.selectedFolder;
        topSingleColumn=g==null||f!=null;topContentVisible(true);
        cover.bind(g);cover.emptyState(f!=null?UiStrings.msg("ui_aa48fbe25242"):s.searchActive?(s.searchBusy?UiStrings.msg("ui_e3f404a27945"):UiStrings.msg("ui_e7031f38e27a")):UiStrings.msg("ui_01f197da56f2"));
        if(f!=null){text(gameTitle,f.name);DirectoryIndex d=RomLibrary.get(owner).directories();text(gameHint,d==null?s.folderPath:d.path(f.id));text(favorite,"");int children=d==null?0:d.children.getOrDefault(f.id,java.util.Collections.emptyList()).size();
            text(descriptionLabel,UiStrings.msg("ui_3d7e4d715f5c"));text(topStateText,UiStrings.msg("ui_62aaca039b03")+f.directGames+UiStrings.msg("ui_ff7e66657eff")+children+UiStrings.msg("ui_36ab0c896a02")+f.totalGames+UiStrings.msg("ui_907d6f843340")+(f.totalGames==0?UiStrings.msg("ui_1c803026148e"):UiStrings.msg("ui_8405530715ec")));
            text(save,s.genreFilter.equals("all")?UiStrings.msg("ui_9e9eca600584"):UiStrings.msg("ui_2674088ab88b")+s.items.get(s.selectedIndex).matchingGames+UiStrings.msg("ui_e683cc0e3347"));text(recent,UiStrings.msg("ui_2edd4da9d287"));descriptionText.bind("","");topGeometry();return;}
        if(g==null){
            String title,body,placeholder;
            if(boundScan.attention()){title=boundScan.title();body=boundScan.counts();placeholder=JourneyGuide.s(31);}
            else if(s.romTreeUri==null){title=UiStrings.msg("ui_044bdf657544");body=UiStrings.msg("ui_be38b4dc60cd");placeholder=UiStrings.msg("ui_2b82c9974f0f");}
            else if(!s.storageAvailable){title=DirectoryHealth.label(s.romAccess);body=DirectoryHealth.notice(s.romAccess);placeholder=UiStrings.msg("ui_a2e47200f639");}
            else if(waiting){title=s.searchComposing?UiStrings.msg("ui_0b8e2a71b899"):s.searchEditing?UiStrings.msg("ui_ad5213dcfce0"):UiStrings.msg("ui_c5d750690e03");body=s.searchComposing?UiStrings.msg("ui_25dedbd2e350"):s.searchEditing?UiStrings.msg("ui_72411c064185"):UiStrings.msg("ui_ab75a9a953f2");placeholder=UiStrings.msg("ui_0e3a7b6b00bf");}
            else if(s.searchActive){title=UiStrings.msg("ui_3c4761459801");body=s.searchScoped?UiStrings.msg("ui_ebf33e450d2a"):TaskPresentation.msg("1a");placeholder=UiStrings.msg("ui_e7031f38e27a");}
            else if(s.category==ShellStateRepository.Category.FOLDERS&&s.genreFilter.equals("all")){title=UiStrings.msg("ui_78dd4d296482");body=UiStrings.msg("ui_1dc644043af9");placeholder=UiStrings.msg("ui_8413b6cec2be");}
            else if(s.category==ShellStateRepository.Category.FAVORITES){title=UiStrings.msg("ui_45bd81dd675c");body=UiStrings.msg("ui_a9994e596ce6");placeholder=UiStrings.msg("ui_9385bfefca58");}
            else if(s.category==ShellStateRepository.Category.RECENT){title=UiStrings.msg("ui_41929be0bae2");body=UiStrings.msg("ui_f669cedcee98");placeholder=UiStrings.msg("ui_dbd67e367a67");}
            else {title=UiStrings.msg("ui_8fd443351725");body=UiStrings.msg("ui_7e56b00c9752");placeholder=UiStrings.msg("ui_01f197da56f2");}
            text(gameTitle,title);text(gameHint,s.searchActive?s.searchScopeLabel:s.category==ShellStateRepository.Category.FOLDERS?s.folderPath:"");text(favorite,"");text(save,"");text(recent,"");text(descriptionLabel,placeholder);text(topStateText,body);descriptionText.bind("","");topGeometry();return;
        }
        text(gameTitle,GameDisplay.name(g,metadata));
        MetadataManager.Info info=g==null?MetadataManager.EMPTY:metadata.info(g.gameId);
        String tags=g==null?"":GameDisplay.tags(g);text(gameHint,g==null?(s.searchActive?s.searchScopeLabel:s.folderPath):tags+(info.region.isEmpty()?"":(tags.isEmpty()?"":" · ")+UiDialogs.region(info.region)));
        selectedHeaderVersion=gameHint.getText().toString();selectedHeaderGenres=TopHeaderGenres.format(metadata.genres(g.gameId),favorite.getPaint(),568);text(favorite,selectedHeaderGenres);
        favorite.setContentDescription(UiStrings.display(GenreTaxonomy.labels(metadata.genres(g.gameId))));
        gameHint.setContentDescription(gameHint.getText());
        text(save,overviewSaveStatus(s,g));
        String description=g==null?"":metadata.descriptionShort(g.gameId);
        text(descriptionLabel,UiStrings.msg("ui_7700004d"));
        text(recent,g==null?"":!s.storageAvailable?UiStrings.msg("ui_4aa9da2993c3"):UiStrings.msg("ui_e6e811e5a738"));
        topGeometry();
        descriptionText.bind(g==null?"":g.gameId,g==null?UiStrings.msg("ui_be8185bbfb32"):description.isEmpty()?UiStrings.msg("ui_5ad78db20614"):description);

    }
    private String overviewSaveStatus(ShellStateRepository.Snapshot snapshot,GameEntry game){
        if(game==null)return "";
        String state;
        if(snapshot.backupTreeUri==null)state=SetupJourney.text("未选存档目录","No save folder");
        else switch(game.saveState){
            case FOUND:state=SetupJourney.text("已有存档","Save found");break;
            case NOT_FOUND:state=SetupJourney.text("未找到存档","No save");break;
            case AMBIGUOUS:state=SetupJourney.text("存档匹配有歧义","Save ambiguous");break;
            case SCANNING:state=SetupJourney.text("存档待核对","Save pending");break;
            default:state=SetupJourney.text("存档未授权/不可读","Save unreadable");break;
        }
        return (snapshot.isFavorite(game)?UiStrings.msg("ui_abec6d162f0a"):"")+state;
    }
    private void bindSettings(ShellStateRepository.Snapshot s){
        if(top){readingText(settingInfo,UiDialogs.settingHelp(s,metadata,RomLibrary.get(owner).isScanning()));return;}
        SettingsModel.Entry[] items=SettingsModel.entries(s.settingsGroup);int offset=(s.settingsIndex/UiMetrics.ROWS)*UiMetrics.ROWS;
        settingsPrevious.setEnabled(offset>0);settingsNext.setEnabled(offset+UiMetrics.ROWS<items.length);
        for(int i=0;i<UiMetrics.ROWS;i++){
            boolean visible=offset+i<items.length;settingRows[i].setVisibility(visible?VISIBLE:GONE);
            if(visible){SettingsModel.Entry item=items[offset+i];text(settingLabels[i],item.title);text(settingDescriptions[i],item.description(owner));text(settingValues[i],item.value(s,metadata,owner));settingValues[i].setCompoundDrawables(null,null,item.kind==SettingsModel.Kind.GROUP?new DsIcons(30,16):null,null);settingRows[i].setEnabled(item.enabled(metadata));settingRows[i].setAlpha(1f);focus(settingRows[i],offset+i==s.settingsIndex);}
        }
        text(settingInfo,(s.settingsGroup<0?UiStrings.msg("ui_daab6a438b4b"):SettingsModel.entries(-1)[s.settingsGroup].title)+" · "+(offset/5+1)+" / "+((items.length+4)/5)+UiStrings.msg("ui_85ca81cf6abd")+(metadata.operationNotice().isEmpty()?"":"\n"+metadata.operationNotice()));
    }

    private void bindFilter(ShellStateRepository.Snapshot s){
        int count=Math.max(1,(s.items.size()+s.pageSize-1)/s.pageSize);
        boolean fixedRecentOrder=s.category==ShellStateRepository.Category.RECENT&&!s.searchActive;
        String[] choices={fixedRecentOrder?UiStrings.msg("ui_d572a5f0de51"):UiStrings.msg("ui_de3c35c2a69a")+(s.sortMode==0?UiStrings.msg("ui_d44e9b3d3b31"):UiStrings.msg("ui_257bbcc44c83"))+UiStrings.msg("ui_952473714e9a"),
                UiStrings.msg("ui_514a19b8f839")+s.jumpPage+" / "+count,UiStrings.msg("ui_5b0bc83fbb10"),UiStrings.msg("ui_45a8c1cc2b22"),UiStrings.msg("ui_27a262b56810"),UiStrings.msg("ui_c7cd55e79b2a")};
        for(int i=0;i<6;i++){boolean enabled=i!=0||!fixedRecentOrder;text(filterLabels[i],choices[i]);filterLabels[i].setEnabled(enabled);focus(filterLabels[i],enabled&&s.filterIndex==i);}
        jump.setMax(count-1);if(jump.getProgress()!=s.jumpPage-1)jump.setProgress(s.jumpPage-1);
        text(filterPage,UiStrings.msg("ui_b24b994c733f"));
    }
    void tick(){
        long now=System.currentTimeMillis();
        text(status,new SimpleDateFormat("HH:mm",Locale.CHINA).format(new Date(now)));
        if(top)batteryIndicator.refreshDescription();
        if(top&&previous!=null&&previous.page==ShellStateRepository.Page.HOME){
            clock.refresh();month.refresh();text(homeDate,new SimpleDateFormat(LocaleSettings.ui().equals("zh")?"yyyy 年 M 月":"MMMM yyyy",Locale.forLanguageTag(LocaleSettings.ui())).format(new Date(now)));
        }
    }
    boolean isLibraryTouch(float y){int start=library.getTop()+LIBRARY_ROW_TOP;return !top&&previous!=null&&previous.page==ShellStateRepository.Page.LIBRARY&&!previous.typesOverview&&!previous.searchEditing&&!previous.searchComposing&&!previous.searchBusy&&y>=start&&y<start+visibleRowCount*LIBRARY_ROW_PITCH;}
    org.json.JSONObject scanDiagnostics(){org.json.JSONObject o=new org.json.JSONObject();try{o.put("rowBindingUpdates",rowBindingUpdates).put("topContentUpdates",topContentUpdates).put("busyShown",scanBusy!=null&&scanBusy.isShown()).put("scanStage",boundScan.phase).put("status",scanLabel==null?"":UiStrings.display(scanLabel.getText().toString())).put("boundPage",boundPage);}catch(Exception ignored){}return o;}
    void scrollTouchToState(){if(!top&&detailScroll!=null&&previous!=null&&previous.page==ShellStateRepository.Page.DETAILS)repo.setDetailOffset(detailScroll.getScrollY());}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();metadata.addListener(this);metadata.addTaskListener(this);restoreBarsAfterIme();}
    @Override protected void onDetachedFromWindow(){if(pendingBeginEditing||pendingBeginEditingCallback!=null)finishSearchEditing();metadata.removeListener(this);metadata.removeTaskListener(this);super.onDetachedFromWindow();}
    @Override public void taskUpdated(){
        if(!isAttachedToWindow())return;
        if(top&&editingGuide!=null){String help=ShellDialogBuilder.editorHelp();editingGuide.setVisibility(help.isEmpty()?GONE:VISIBLE);if(!help.isEmpty()){readingText(editingHelp,help);editingGuide.bringToFront();}}
        if(!top&&taskEntry!=null)bindTaskEntry();
        if(previous!=null&&previous.page==ShellStateRepository.Page.PREPARING&&setupView!=null)setupView.bind();
        if(previous!=null&&previous.page==ShellStateRepository.Page.TASKS&&taskView!=null)taskView.bind();
        if(previous!=null&&previous.page==ShellStateRepository.Page.SETTINGS)bindSettings(previous);
    }
    @Override public void metadataUpdated(String id){
        ShellStateRepository.Snapshot s=previous;if(s==null)return;
        if(s.page==ShellStateRepository.Page.PREPARING&&setupView!=null){setupView.bind();return;}
        if(top&&builtLibrary&&(s.page==ShellStateRepository.Page.LIBRARY||s.page==ShellStateRepository.Page.DETAILS&&!s.returningToSettings())){
            if(id.isEmpty()||s.selectedGame!=null&&s.selectedGame.gameId.equals(id)){topContentDirty=true;bindTopLibrary(s);}
        }else if(!top&&s.page==ShellStateRepository.Page.LIBRARY&&scanLabel!=null){if(s.typesOverview){bindLibrary(s,s);return;}
            for(int i=0;i<s.pageSize&&boundPage*s.pageSize+i<s.items.size();i++){GameEntry g=s.items.get(boundPage*s.pageSize+i).game;if(g!=null&&(id.isEmpty()||g.gameId.equals(id)))text(rowTitles[i],rowText(g));}
        }else if(s.page==ShellStateRepository.Page.SETTINGS)bindSettings(s);
        else if(s.page==ShellStateRepository.Page.GENRES)bind(repo.snapshot());
        else if(!top&&s.page==ShellStateRepository.Page.DETAILS&&s.detailTab==0&&!s.returningToSettings())readingText(detailText,UiDialogs.gameInfo(owner,s.selectedGame));
    }
    private static String time(long t){return t<=0?"—":new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.CHINA).format(new Date(t));}
    private static String bytes(long n){return n<1024?n+" B":n<1048576?String.format(Locale.US,"%.1f KiB",n/1024.0):String.format(Locale.US,"%.1f MiB",n/1048576.0);}
    static String technical(android.content.Context context,GameEntry g,ShellStateRepository.Snapshot s){
        if(g==null)return UiStrings.msg("ui_b90321fed934")+diagnostics(context,s);
        return UiStrings.msg("ui_52afba8ade75")+g.fileName+UiStrings.msg("ui_76fc98780855")+g.internalTitle+
            "\n\nGame Code  "+g.gameCode+"     Maker  "+g.makerCode+
            UiStrings.msg("ui_50c02b3b52dd")+bytes(g.romBytes)+UiStrings.msg("ui_ffd988675f80")+time(g.romModifiedAt)+
            UiStrings.msg("ui_b6afebd7143c")+(s.backupTreeUri==null?JourneyGuide.s(65):g.saveState.label)+
            (g.hasSave?UiStrings.msg("ui_43ebb962a8a6")+g.basename()+UiStrings.msg("ui_e4066ea46ec1")+bytes(g.saveBytes)+UiStrings.msg("ui_852f9c9992f9")+time(g.saveModifiedAt):"")+
            UiStrings.msg("ui_206e1adf56b8")+g.bannerTitle+UiStrings.msg("ui_243339b84365")+g.uriString()+
            UiStrings.msg("ui_9e7da55686df")+(s.backupTreeUri==null?UiStrings.msg("ui_94bc3d40defe"):s.backupTreeUri.toString())+
            UiStrings.msg("ui_3b9e0caad093")+(g.readWarning.isEmpty()?UiStrings.msg("ui_dfc133e3b1bf"):g.readWarning)+
            UiStrings.msg("ui_e1bf2210611d");
    }
    static String version(android.content.Context context){try{android.content.pm.PackageInfo p=context.getPackageManager().getPackageInfo(context.getPackageName(),0);return "TwinGrid "+p.versionName+" / code"+(android.os.Build.VERSION.SDK_INT>=28?p.getLongVersionCode():p.versionCode);}catch(Exception e){return UiStrings.msg("ui_4f14043eeba3");}}
    static String diagnostics(android.content.Context context,ShellStateRepository.Snapshot s){
        String version=UiStrings.msg("ui_4f14043eeba3");try{android.content.pm.PackageInfo p=context.getPackageManager().getPackageInfo(context.getPackageName(),0);version=p.versionName+" / "+(android.os.Build.VERSION.SDK_INT>=28?p.getLongVersionCode():p.versionCode);}catch(android.content.pm.PackageManager.NameNotFoundException ignored){}
        return "TwinGrid "+version+UiStrings.msg("ui_86bd26c5aad8")+(PerfTrace.isEnabled()?UiStrings.msg("ui_80f7cda74fe0"):UiStrings.msg("ui_6744b4c6a9aa"))+UiStrings.msg("ui_872ea7eee520")+s.totalGameCount+UiStrings.msg("ui_bf05f62ec00e")+s.favoriteCount+
            UiStrings.msg("ui_9344f704c8f3")+s.recentCount+"\n\n"+s.scanState+UiStrings.msg("ui_370b91fc1586")+
            (s.romTreeUri==null?UiStrings.msg("ui_94bc3d40defe"):s.romTreeUri.toString())+UiStrings.msg("ui_0d5583839a5a")+
            (s.backupTreeUri==null?UiStrings.msg("ui_94bc3d40defe"):s.backupTreeUri.toString())+
            UiStrings.msg("ui_a407b068a02a")+s.drasticState+UiStrings.msg("ui_eca3c4c02ad0")+s.topDisplayState+
            UiStrings.msg("ui_bd723cde2772");
    }
}
