package com.rgds.ultimate.shell;

import android.app.Activity;
import android.view.*;
import android.widget.*;
import static com.rgds.ultimate.shell.ClassicUi.*;

/** One everyday journey over the existing durable local/catalog and cover workers. */
final class TaskScreen extends FrameLayout {
    private final Activity owner;private final boolean top;private final MetadataManager metadata;private final ShellStateRepository repo;
    private TextView title,scope,progressText,counts,waiting,notice;private ProgressBar progress;
    private final TextView[] actions=new TextView[4];private String pressedControl="";
    TaskScreen(Activity a,boolean upper){super(a);DsGrid.register(this,upper);owner=a;top=upper;metadata=MetadataManager.get(a);repo=ShellStateRepository.get(a);
        if(top){
            FrameLayout card=panel(a);place(this,card,24,16,592,356);
            title=text(a,"",20);title.setTag("task_state");place(card,title,16,16,560,32);
            scope=label(a,UxStrings.s(5),20,MUTED);lines(scope,2);place(card,scope,16,64,560,64);
            progress=new ProgressBar(a,null,android.R.attr.progressBarStyleHorizontal);progress.setProgressDrawable(ClassicUi.progress());place(card,progress,16,144,560,8);
            progressText=text(a,"",20);place(card,progressText,16,168,560,28);
            counts=text(a,"",20);counts.setTag("task_counts");lines(counts,3);place(card,counts,16,204,560,84);
            waiting=label(a,"",20,MUTED);lines(waiting,2);place(card,waiting,16,292,560,56);
            notice=label(a,UxStrings.s(84),20,MUTED);lines(notice,2);place(this,notice,24,384,592,56);
        }else{
            FrameLayout card=panel(a);place(this,card,24,16,592,164);
            title=text(a,"",20);title.setTag("task_state");lines(title,2);place(card,title,16,12,560,56);
            progressText=label(a,"",20,MUTED);place(card,progressText,16,72,560,28);
            actions[0]=button(card,a,"",16,108,560,48,()->activate(0));
            actions[0].setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN)pressedControl=metadata.taskControlKey();if(e.getActionMasked()==MotionEvent.ACTION_CANCEL)pressedControl="";return false;});
            actions[1]=button(this,a,"",24,196,592,60,()->activate(1));
            actions[2]=button(this,a,UxStrings.s(3),24,276,592,44,()->activate(2));
            notice=label(a,"",20,MUTED);lines(notice,2);place(this,notice,24,324,592,56);
            button(this,a,UxStrings.s(22),24,384,136,44,repo::back);
            actions[3]=button(this,a,UxStrings.s(4),176,384,440,44,()->activate(3));
            for(int n=0;n<actions.length;n++)actions[n].setTag("task_action_"+n);
        }
    }
    private void activate(int n){if(!ShellCoordinator.get().canInteract()||ShellDialogBuilder.hasOpenDialog())return;
        if(n==0&&!pressedControl.isEmpty()&&!pressedControl.equals(metadata.taskControlKey())){pressedControl="";return;}pressedControl="";repo.selectTask(n);perform(owner,n);}
    static void perform(Activity a,int n){MetadataManager m=MetadataManager.get(a);
        if(n==0)m.organizeAction();else if(n==1)GapUi.attention(a);else if(n==2)GapUi.history(a);
        else if(waitingForCover(m.task()))new ShellDialogBuilder(a).setTitle(UxStrings.s(64)).setMessage(UxStrings.s(65)).setPositiveButton(UxStrings.s(88),(d,w)->m.finishCoverWait()).setNegativeButton(UxStrings.s(74),null).show();
        else m.acknowledgeResult(TaskReceipts.key(m.task()),true);
    }
    static boolean waitingForCover(CoverTask.Snapshot s){return s.localDone&&(s.state==CoverTask.State.NETWORK||s.state==CoverTask.State.METERED||s.state==CoverTask.State.RETRY_WAIT);}
    void bind(){CoverTask.Snapshot s=metadata.task();boolean local=CoverTask.organization(s.trigger)&&!s.localDone;
        String state=s.state==CoverTask.State.IDLE?UxStrings.s(0):local&&!s.userPaused?UxStrings.s(7):TaskPresentation.title(s);
        text(title,state);String stage=local?UxStrings.s(6)+" "+s.localProcessed+" / "+(s.localTotal==0?"…":s.localTotal):s.planned?UxStrings.s(27)+" "+s.processed+" / "+s.total:UxStrings.s(5);
        text(progressText,TaskPresentation.kind(s.trigger)==TaskPresentation.Kind.INDEX?TaskPresentation.msg("07")+s.processed+" / "+s.total:stage);if(top)text(scope,s.scope);
        if(top){progress.setIndeterminate(local&&s.localTotal==0||!s.planned);progress.setMax(Math.max(1,local?s.localTotal:s.total));progress.setProgress(local?s.localProcessed:s.processed);
            text(counts,TaskPresentation.kind(s.trigger)==TaskPresentation.Kind.INDEX?TaskPresentation.msg("05")+s.indexRecords+"\n"+TaskPresentation.msg("07")+s.processed+" / "+s.total+"\n"+TaskPresentation.msg("0f")+s.downloads:TaskPresentation.extra(9)+s.added+" / "+s.reused+"\n"+TaskPresentation.extra(10)+s.missing+" / "+s.identity+"\n"+TaskPresentation.extra(11)+s.downloads+" / "+s.failed);
            text(waiting,waitingForCover(s)?UxStrings.s(11):s.userPaused?UxStrings.s(31):CoverTask.organization(s.trigger)&&s.localDone?UxStrings.s(81):UxStrings.s(84));
        }else{
            text(actions[0],s.terminal()?UxStrings.s(1):s.action==CoverTask.Action.PAUSE?UxStrings.s(104):s.action==CoverTask.Action.RESUME?UxStrings.s(105):s.actionText());actions[0].setEnabled(metadata.tasksReady()&&(s.terminal()||s.action!=CoverTask.Action.NONE));
            text(actions[1],TaskPresentation.extra(5)+" · "+(metadata.gapsReady()?mCount():"…"));actions[1].setEnabled(metadata.gapsReady());
            text(actions[3],waitingForCover(s)?UxStrings.s(64):UxStrings.s(4));actions[3].setEnabled(waitingForCover(s)||TaskReceipts.result(s)||!metadata.operationNotice().isEmpty());
            text(notice,repo.snapshot().romTreeUri!=null&&!repo.snapshot().storageAvailable?DirectoryHealth.notice(repo.snapshot().romAccess):metadata.operationNotice().isEmpty()?UxStrings.s(84):metadata.operationNotice());
            for(int n=0;n<actions.length;n++)focus(actions[n],n==repo.snapshot().taskIndex);
        }
    }
    private String mCount(){return Integer.toString(metadata.attention(0).size());}
}
