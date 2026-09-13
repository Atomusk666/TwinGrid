package com.rgds.ultimate.shell;
import android.app.Activity;

final class LaunchUi {
    static String text(String zh,String en){return LocaleSettings.ui().equals("en")?en:zh;}
    /** The selected-game action already authorizes loading it. Only a new trial needs consent. */
    static void authorize(Activity a,DrasticLauncher.Plan p,Runnable proceed,Runnable cancel){
        if(!LaunchDiagnostic.active(a,p.requestId)||a.isFinishing()||a.isDestroyed()){cancel.run();return;}
        if(!p.profile.trial||p.profile.trialAccepted(a)){
            LaunchDiagnostic.manual(a,"DIRECT_SELECTED_GAME");proceed.run();return;
        }
        final boolean[] decided={false},accepted={false};
        android.app.AlertDialog dialog=new ShellDialogBuilder(a).setTitle(text("启用兼容回测","Enable compatibility trial"))
            .setMessage(text("此模拟器构建尚未实测。仅在当前接收端与系统环境启用兼容回测；更新模拟器或系统后需重新确认。\n\n选择游戏会载入新目标并结束模拟器旧任务，未保存的进度可能丢失。请先在游戏内存档。", "This emulator build is untested. Enable the compatibility trial for this receiver and system environment; updates require renewed consent.\n\nSelecting a game loads that target and replaces the emulator task. Unsaved progress may be lost. Save in-game first."))
            .setPositiveButton(text("启用并载入","Enable and load"),(d,w)->{
                if(decided[0])return;decided[0]=true;
                if(!LaunchDiagnostic.active(a,p.requestId)||a.isFinishing()||a.isDestroyed()){cancel.run();return;}
                p.profile.acceptTrial(a);accepted[0]=true;LaunchDiagnostic.manual(a,"USER_ACCEPTED_COMPATIBILITY_TRIAL");
            })
            .setNegativeButton(text("取消","Cancel"),(d,w)->{if(!decided[0]){decided[0]=true;cancel.run();}})
            .setOnCancelListener(d->{if(!decided[0]){decided[0]=true;cancel.run();}}).create();
        // Only the modal branch waits for dismissal. The direct path has no UI/post dependency.
        ShellDialogBuilder.onDismissed(dialog,()->{
            if(accepted[0])a.getWindow().getDecorView().post(proceed);
            else if(!decided[0]){decided[0]=true;cancel.run();}
        });
        dialog.show();
    }
    static void observations(Activity a){String id=LaunchDiagnostic.read(a).optString("requestId");
        new ShellDialogBuilder(a).setTitle(text("记录最近一次启动结果","Record last launch result"))
            .setItems(new String[]{text("进入了所选游戏","Selected game loaded"),text("仍是旧游戏","Previous game remained"),text("仅打开模拟器菜单","Only emulator menu opened"),text("已在模拟器正常退出游戏","I exited the game normally")},(d,n)->LaunchDiagnostic.userObservation(a,id,new String[]{"SELECTED_GAME","OLD_GAME","MENU_ONLY","NORMAL_EXIT_REPORTED"}[n]))
            .setNegativeButton(text("取消","Cancel"),null).show();
    }
}
