package com.rgds.ultimate.shell;

import android.app.Activity;
import android.widget.*;
import static com.rgds.ultimate.shell.ClassicUi.*;

/** Stable non-modal preparation surface; the directory child owns any dialog. */
final class SetupScreen extends FrameLayout {
    private final Activity owner;private final boolean top;private final TextView title,scan,local,cover,hint;
    private final String[] pressed=new String[3];
    private final TextView[] buttons=new TextView[3];
    SetupScreen(Activity a,boolean upper){super(a);owner=a;top=upper;DsGrid.register(this,upper);
        FrameLayout card=panel(a);place(this,card,24,12,592,top?356:278);
        title=text(a,"",20);title.setTag("setup_title");place(card,title,16,10,560,32);
        scan=label(a,"",20,INK);lines(scan,2);place(card,scan,16,50,560,60);
        local=label(a,"",20,MUTED);lines(local,2);place(card,local,16,116,560,60);
        cover=label(a,"",20,MUTED);lines(cover,3);place(card,cover,16,182,560,84);
        hint=label(a,"",20,MUTED);lines(hint,2);place(this,hint,24,top?376:296,592,56);
        if(!top){buttons[0]=button(this,a,"",24,356,592,44,()->activate(0));buttons[1]=button(this,a,"",24,404,288,32,()->activate(1));buttons[2]=button(this,a,"",328,404,288,32,()->activate(2));for(int i=0;i<3;i++){final int n=i;buttons[i].setTag("setup_action_"+i);buttons[i].setOnTouchListener((v,e)->{if(e.getActionMasked()==android.view.MotionEvent.ACTION_DOWN)pressed[n]=SetupJourney.controlKey(owner,n);if(e.getActionMasked()==android.view.MotionEvent.ACTION_CANCEL)pressed[n]=null;return false;});}}
    }
    private void activate(int n){String key=pressed[n];pressed[n]=null;if(key!=null&&!key.equals(SetupJourney.controlKey(owner,n)))return;perform(owner,n);}
    static void perform(Activity a,int n){if(!ShellCoordinator.get().canInteract()||ShellDialogBuilder.hasOpenDialog())return;
        if(n==0){SetupJourney.Model m=new SetupJourney.Model(a);if(m.resumable)SetupJourney.resume(a);else SetupJourney.leave(a,m.result);}
        else if(n==1)SetupJourney.leave(a,false);else ShellCoordinator.get().openSettingsActively();
    }
    void bind(){SetupJourney.Model m=new SetupJourney.Model(owner);text(title,m.title);text(scan,ShellStateRepository.get(owner).snapshot().romTreeUri!=null&&!ShellStateRepository.get(owner).snapshot().storageAvailable?DirectoryHealth.notice(ShellStateRepository.get(owner).snapshot().romAccess):m.scan);text(local,m.local);text(cover,m.cover);
        text(hint,SetupJourney.text("存档可选；离开此页不会取消任务。\n准备结果由你确认后进入游戏库。","Saves are optional. Leaving keeps tasks running.\nEnter the library when you are ready."));
        if(!top){text(buttons[0],m.action);text(buttons[1],SetupJourney.text("先浏览 / B 返回","Browse now / B back"));text(buttons[2],SetupJourney.text("设置","Settings"));for(int i=0;i<3;i++)focus(buttons[i],ShellStateRepository.get(owner).snapshot().taskIndex==i);}
    }
}
