package com.rgds.ultimate.shell;

import android.app.AlertDialog;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;

/** A release can confirm only the object that owned its initial press. */
final class ModalInput implements android.content.DialogInterface.OnKeyListener {
    private final AlertDialog dialog;
    private View pressed;
    private View pressedVisual;
    private ListAdapter adapter;
    private Object item;
    private String actionText;
    private long itemId;
    private int position=-1,keyDown=-1,cancelDeviceId=-1;
    ModalInput(AlertDialog dialog){this.dialog=dialog;}
    private void clear(){if(pressedVisual!=null)pressedVisual.setPressed(false);pressedVisual=null;pressed=null;adapter=null;item=null;actionText=null;position=-1;keyDown=-1;cancelDeviceId=-1;}
    private void capture(int key){
        clear();View focus=dialog.getCurrentFocus();if(focus==null||!focus.isEnabled())return;
        pressed=focus;keyDown=key;
        if(focus instanceof android.widget.TextView)actionText=((android.widget.TextView)focus).getText().toString();
        if(focus instanceof ListView){ListView list=(ListView)focus;position=list.getSelectedItemPosition();adapter=list.getAdapter();
            if(adapter==null||position<0||position>=adapter.getCount()||!adapter.isEnabled(position)){clear();return;}
            itemId=adapter.getItemId(position);item=adapter.getItem(position);
        }
        pressedVisual=focus instanceof ListView?((ListView)focus).getSelectedView():focus;if(pressedVisual!=null)pressedVisual.setPressed(true);
        PerfTrace.event("MODAL_DOWN key="+key+" kind="+focus.getClass().getSimpleName()+" position="+position+" itemId="+itemId);
    }
    @Override public boolean onKey(android.content.DialogInterface ignored,int key,KeyEvent event){
        if(key==KeyEvent.KEYCODE_BUTTON_B){
            if(event.getAction()==KeyEvent.ACTION_DOWN){if(event.getRepeatCount()==0){clear();if(dialog.isShowing()){keyDown=key;cancelDeviceId=event.getDeviceId();}}return true;}
            if(event.getAction()==KeyEvent.ACTION_UP){boolean valid=keyDown==key&&cancelDeviceId==event.getDeviceId()&&!event.isCanceled()&&dialog.isShowing();clear();if(valid)dialog.cancel();}
            return true;
        }
        if(key==KeyEvent.KEYCODE_BUTTON_START){clear();return true;}
        boolean confirm=key==KeyEvent.KEYCODE_BUTTON_A||key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER||key==KeyEvent.KEYCODE_NUMPAD_ENTER;
        if(!confirm){if(event.getAction()==KeyEvent.ACTION_DOWN)clear();return false;}
        // Preserve the IME's edit action while keeping gamepad A inside this modal.
        if(dialog.getCurrentFocus() instanceof EditText){clear();return key==KeyEvent.KEYCODE_BUTTON_A;}
        if(event.getAction()==KeyEvent.ACTION_DOWN){if(event.getRepeatCount()==0)capture(key);return true;}
        if(event.getAction()!=KeyEvent.ACTION_UP)return true;
        View target=pressed;ListAdapter oldAdapter=adapter;Object oldItem=item;long oldId=itemId;int oldPosition=position;String oldText=actionText;
        boolean valid=keyDown==key&&!event.isCanceled()&&dialog.isShowing()&&target!=null&&target==dialog.getCurrentFocus()&&target.isEnabled()&&target.isShown();
        if(valid&&oldText!=null)valid=oldText.equals(((android.widget.TextView)target).getText().toString());
        clear();
        if(valid&&target instanceof ListView){ListView list=(ListView)target;ListAdapter current=list.getAdapter();int p=list.getSelectedItemPosition();
            valid=current==oldAdapter&&p>=0&&p<current.getCount()&&current.isEnabled(p);
            if(valid)valid=current.hasStableIds()?current.getItemId(p)==oldId:p==oldPosition&&(oldItem==current.getItem(p)||(oldItem instanceof CharSequence&&oldItem.equals(current.getItem(p))));
            View row=list.getSelectedView();valid=valid&&row!=null&&row.isShown();
            PerfTrace.event("MODAL_UP commit="+valid+" position="+p+" itemId="+(valid?current.getItemId(p):oldId));
            if(valid)list.performItemClick(row,p,current.getItemId(p));
        }else{PerfTrace.event("MODAL_UP commit="+valid+" position=-1");if(valid)target.performClick();}
        return true;
    }
}
