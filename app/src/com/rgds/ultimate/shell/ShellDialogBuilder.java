package com.rgds.ultimate.shell;

import android.app.AlertDialog;
import android.content.Context;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;

/** Tracks modal windows across both displays without replacing their dismissal listeners. */
final class ShellDialogBuilder extends AlertDialog.Builder {
    private static final ArrayList<WeakReference<AlertDialog>> dialogs = new ArrayList<>();
    private static final java.util.WeakHashMap<AlertDialog,Boolean> styled=new java.util.WeakHashMap<>();
    private static final java.util.WeakHashMap<AlertDialog,String> editors=new java.util.WeakHashMap<>();
    private static final java.util.WeakHashMap<AlertDialog,Runnable> dismissed=new java.util.WeakHashMap<>();
    static void onDismissed(AlertDialog dialog,Runnable action){dismissed.put(dialog,action);}
    private static final java.util.WeakHashMap<AlertDialog,String> editorGames=new java.util.WeakHashMap<>();
    private static final java.util.WeakHashMap<AlertDialog,String> editorFields=new java.util.WeakHashMap<>();
    static void gameEditor(AlertDialog d,GameEntry game,String field,String operation){
        editorGames.put(d,game.gameId);editorFields.put(d,field);
        help(d,GameDisplay.name(game,MetadataManager.get(d.getContext()))+"\n\n"+operation+"\n"+TaskPresentation.msg("13")+LocaleSettings.content()+"\n\n"+TaskPresentation.msg("14"));
    }
    /** Candidate bodies scroll below; the fixed upper panel keeps bounded context. */
    static void gamePreview(AlertDialog d,GameEntry game,String operation){
        help(d,GameDisplay.name(game,MetadataManager.get(d.getContext()))+"\n\n"+operation+"\n"+TaskPresentation.msg("13")+LocaleSettings.content()+"\n\n"+TaskPresentation.msg("15"));
    }
    static String editorObject(boolean field){for(int n=dialogs.size()-1;n>=0;n--){AlertDialog d=dialogs.get(n).get();if(d!=null&&d.isShowing()&&editorGames.containsKey(d))return (field?editorFields:editorGames).get(d);}return "";}
    static String editorHelp(){for(int n=dialogs.size()-1;n>=0;n--){AlertDialog d=dialogs.get(n).get();if(d!=null&&d.isShowing()&&editors.containsKey(d))return editors.get(d);}return "";}
    static void help(AlertDialog d,String text){editors.put(d,text);MetadataManager.get(d.getContext()).notifyTaskUi();}
    ShellDialogBuilder(Context context) { super(context); }
    private static android.app.Activity owner(AlertDialog d){Context c=d.getContext();while(c instanceof android.content.ContextWrapper){if(c instanceof android.app.Activity)return (android.app.Activity)c;Context base=((android.content.ContextWrapper)c).getBaseContext();if(base==c)break;c=base;}return null;}
    static void closeOwned(android.app.Activity activity){for(WeakReference<AlertDialog> ref:new ArrayList<>(dialogs)){AlertDialog d=ref.get();if(d!=null&&owner(d)==activity)d.dismiss();}}
    static void inputError(android.widget.EditText input,String message){String text=UiStrings.display(message);input.setHintTextColor(ClassicUi.ACCENT);input.setHint(text);input.announceForAccessibility(text);}
    static void refreshLocale(){for(WeakReference<AlertDialog> ref:dialogs){AlertDialog d=ref.get();if(d!=null&&d.isShowing()){android.widget.ListView list=d.getListView();if(list!=null&&list.getAdapter() instanceof LocaleAdapter)((LocaleAdapter)list.getAdapter()).notifyDataSetChanged();UiStrings.refreshTree(d.getWindow().getDecorView());}}}
    /** Resolve every recycled row, including rows first exposed after scrolling. */
    private static final class LocaleAdapter extends android.widget.BaseAdapter {
        final android.widget.ListAdapter source;
        LocaleAdapter(android.widget.ListAdapter source){this.source=source;source.registerDataSetObserver(new android.database.DataSetObserver(){public void onChanged(){notifyDataSetChanged();}public void onInvalidated(){notifyDataSetInvalidated();}});}
        public int getCount(){return source.getCount();}public Object getItem(int n){return source.getItem(n);}public long getItemId(int n){return source.getItemId(n);}
        public boolean hasStableIds(){return source.hasStableIds();}public int getViewTypeCount(){return source.getViewTypeCount();}public int getItemViewType(int n){return source.getItemViewType(n);}
        public boolean areAllItemsEnabled(){return source.areAllItemsEnabled();}public boolean isEnabled(int n){return source.isEnabled(n);}
        public android.view.View getView(int n,android.view.View reuse,android.view.ViewGroup parent){
            UiStrings.beginRowBind(reuse);
            android.view.View v=source.getView(n,reuse,parent);if(v instanceof android.widget.TextView&&!(v.getBackground() instanceof ClassicUi.Edge))bindRow(v);UiStrings.refreshTree(v);
            if(parent instanceof android.widget.ListView){ClassicUi.Edge edge=v.getBackground() instanceof ClassicUi.Edge?(ClassicUi.Edge)v.getBackground():new ClassicUi.Edge(false);edge.cursorOwner((android.widget.ListView)parent);v.setBackground(edge);}
            v.setEnabled(source.isEnabled(n));
            // Keep an explicitly sized product row (including four-row menus).
            return v;
        }
        private void bindRow(android.view.View v){if(v instanceof android.widget.TextView){android.widget.TextView t=(android.widget.TextView)v;if(t.getText().toString().indexOf('\uE100')>=0)t.setText(UiStrings.bind(t,t.getText()));DsTypography.body(t,19);if(t instanceof android.widget.CheckedTextView)((android.widget.CheckedTextView)t).setCheckMarkDrawable(new ClassicUi.CheckFace());t.setTextColor(ClassicUi.INK);t.setMinHeight(48);t.setBackground(new ClassicUi.Edge(false));t.setPadding(16,8,16,8);}if(v instanceof android.view.ViewGroup){android.view.ViewGroup g=(android.view.ViewGroup)v;for(int n=0;n<g.getChildCount();n++)bindRow(g.getChildAt(n));}}
    }
    @Override public AlertDialog create() {
        AlertDialog dialog = super.create();
        android.app.Activity activity=owner(dialog);
        if(activity instanceof TopDisplayActivity){
            // Same public Presentation window type as its owning upper surface.
            // Keep top-owned dialogs above that surface and close them with owner.
            dialog.getWindow().setType(((TopDisplayActivity)activity).surfaceWindow().getAttributes().type);
        }
        dialogs.add(new WeakReference<>(dialog));
        ActionKeyLatch.reset();
        dialog.setOnShowListener(shown->style(dialog));
        dialog.setOnDismissListener(d->{Runnable action=dismissed.remove(dialog);if(action!=null)action.run();editors.remove(dialog);MetadataManager.get(getContext()).notifyTaskUi();ShellCoordinator.get().guideReady();});
        return dialog;
    }
    @Override public AlertDialog show(){AlertDialog dialog=create();dialog.show();style(dialog);return dialog;}
    private static void style(AlertDialog dialog){
        android.view.Window window=dialog.getWindow();if(window==null)return;
        if(styled.containsKey(dialog))return;styled.put(dialog,true);
        int available=dialog.getContext().getResources().getDisplayMetrics().widthPixels;
        window.setLayout(Math.max(240,Math.min(592,available-32)),android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        window.setBackgroundDrawable(new ClassicUi.Edge(false));
        // AlertDialog's message/custom panels otherwise paint over our 2+2px
        // frame. Reserve that frame once for every modal child surface.
        window.getDecorView().setPadding(4,4,4,4);
        window.setDimAmount(.48f);
        boolean editing=styleEditors(window.getDecorView());
        ShellWindowPolicy.set(window,editing?ShellWindowPolicy.Mode.EDITING:ShellWindowPolicy.Mode.MODAL,"dialog");
        if(!editing){
            // One layout adjustment, guarded by the dialog's own lifetime. No system-bar callback.
            window.getDecorView().post(()->{if(dialog.isShowing()){int limit=Math.max(240,dialog.getContext().getResources().getDisplayMetrics().heightPixels-32);if(window.getDecorView().getHeight()>limit)window.setLayout(Math.max(240,Math.min(592,available-32)),limit);}});
        }
        android.widget.TextView title=dialog.findViewById(dialog.getContext().getResources().getIdentifier("alertTitle","id","android"));
        if(title!=null){DsTypography.ui(title);title.setTextColor(ClassicUi.INK);title.setMaxLines(2);title.setTag("modal_title");if(title.getParent() instanceof android.view.View){android.view.View strip=(android.view.View)title.getParent();strip.setBackground(new ClassicUi.Band());strip.setPadding(12,4,12,6);}}
        if(!editors.containsKey(dialog))help(dialog,(title==null?UiStrings.msg("ui_907451385fe4"):title.getText().toString())+(editing?UiStrings.msg("ui_67202af2d97b"):UiStrings.msg("ui_e27f88063ce1")));
        dialog.setOnKeyListener(new ModalInput(dialog));
        android.widget.TextView message=dialog.findViewById(android.R.id.message);
        if(message!=null){DsTypography.body(message,19);message.setTextColor(ClassicUi.INK);message.setLineSpacing(5,1);message.setTag("modal_message");message.setPadding(12,10,12,12);message.setBackgroundColor(ClassicUi.PAPER);}
        for(int which:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL}){
            android.widget.Button button=dialog.getButton(which);if(button!=null){DsTypography.ui(button);button.setTextColor(ClassicUi.INK);button.setMinHeight(44);button.setMinimumHeight(44);button.setAllCaps(false);button.setBackground(new ClassicUi.Edge(false));button.setPadding(14,2,14,4);button.setTag("modal_action_"+which);android.view.ViewGroup.LayoutParams raw=button.getLayoutParams();if(raw instanceof android.view.ViewGroup.MarginLayoutParams){android.view.ViewGroup.MarginLayoutParams lp=(android.view.ViewGroup.MarginLayoutParams)raw;lp.height=44;lp.setMargins(4,4,4,4);if(lp instanceof android.widget.LinearLayout.LayoutParams)((android.widget.LinearLayout.LayoutParams)lp).gravity=android.view.Gravity.CENTER_VERTICAL;button.setLayoutParams(lp);}}
        }
        // The framework button bar retains baseline alignment from its themed
        // buttons. With our pixel font that can position a child above the bar's
        // padded viewport, cutting off the complete top border. Lay out each
        // 44px button by its box, reserving the same 4px gap on all sides.
        for(int which:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL}){
            android.widget.Button button=dialog.getButton(which);
            if(button!=null&&button.getParent() instanceof android.widget.LinearLayout){
                android.widget.LinearLayout bar=(android.widget.LinearLayout)button.getParent();
                bar.setBaselineAligned(false);
                bar.setGravity((bar.getGravity()&android.view.Gravity.RELATIVE_HORIZONTAL_GRAVITY_MASK)|android.view.Gravity.CENTER_VERTICAL);
                bar.setPadding(12,4,12,4);bar.setMinimumHeight(60);bar.requestLayout();
            }
        }
        UiStrings.refreshTree(window.getDecorView());
        styleLists(window.getDecorView());
        // Custom panels and callers that replace an adapter after show use the
        // same binding. No timer and no replacement of application listeners.
        window.getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(()->{if(dialog.isShowing())styleLists(window.getDecorView());});
        window.getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus,newFocus)->{if(dialog.isShowing())invalidateLists(window.getDecorView());});
    }
    private static void styleLists(android.view.View view){
        if(view instanceof android.widget.ListView){android.widget.ListView list=(android.widget.ListView)view;
        if(list.getAdapter()!=null&&!(list.getAdapter() instanceof LocaleAdapter)){
            int position=list.getSelectedItemPosition();android.util.SparseBooleanArray checked=list.getCheckedItemPositions();checked=checked==null?new android.util.SparseBooleanArray():checked.clone();
            list.setSelector(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
            list.setAdapter(new LocaleAdapter(list.getAdapter()));for(int n=0;n<checked.size();n++)list.setItemChecked(checked.keyAt(n),checked.valueAt(n));if(position>=0)list.setSelection(position);
        }return;}
        if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int n=0;n<group.getChildCount();n++)styleLists(group.getChildAt(n));}
    }
    private static void invalidateLists(android.view.View view){
        if(view instanceof android.widget.ListView){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int n=0;n<group.getChildCount();n++)group.getChildAt(n).invalidate();return;}
        if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int n=0;n<group.getChildCount();n++)invalidateLists(group.getChildAt(n));}
    }
    private static boolean styleEditors(android.view.View view){
        boolean editing=view instanceof android.widget.EditText;
        if(editing){android.widget.EditText editor=(android.widget.EditText)view;DsTypography.body(editor,20);editor.setTextColor(ClassicUi.INK);editor.setBackground(new ClassicUi.InputEdge());editor.setPadding(12,8,12,8);editor.setMinimumHeight(44);editor.setImeOptions(editor.getImeOptions()|android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI|android.view.inputmethod.EditorInfo.IME_FLAG_NO_FULLSCREEN);}
        if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++)editing=styleEditors(group.getChildAt(i))||editing;}
        return editing;
    }
    static boolean hasOpenDialog() {
        boolean open = false;
        for (Iterator<WeakReference<AlertDialog>> it=dialogs.iterator();it.hasNext();) {
            AlertDialog dialog=it.next().get();
            if(dialog==null)it.remove();else {android.app.Activity a=owner(dialog);if(a!=null&&a.isDestroyed())it.remove();else if(dialog.isShowing())open=true;}
        }
        return open;
    }
    static java.util.List<android.view.View> visibleWindows(int display){
        java.util.List<android.view.View> out=new java.util.ArrayList<>();
        for(WeakReference<AlertDialog> ref:dialogs){AlertDialog d=ref.get();if(d!=null&&d.isShowing()&&d.getWindow()!=null&&d.getWindow().getWindowManager().getDefaultDisplay().getDisplayId()==display)out.add(d.getWindow().getDecorView());}
        return out;
    }
}
