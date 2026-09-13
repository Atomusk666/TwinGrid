package com.rgds.ultimate.shell;

import android.content.Context;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Main-thread owner for both preflight stages. One bounded worker, no waiting on it. */
final class LaunchCheck {
    interface Work { DrasticLauncher.Plan run(CancellationSignal cancel)throws Exception; }
    private final Context context;
    private final ThreadPoolExecutor worker;
    private final Handler main=new Handler(Looper.getMainLooper());
    private Operation active;
    private final class Operation {
        final String id;final CancellationSignal cancel=new CancellationSignal();
        boolean finished;Future<?> future;Runnable timeout;
        Operation(String id){this.id=id;}
        boolean finish(){if(finished)return false;finished=true;main.removeCallbacks(timeout);if(active==this)active=null;return true;}
        void stop(){if(future!=null)future.cancel(true);worker.purge();cancel.cancel();}
    }
    LaunchCheck(Context c,ThreadPoolExecutor worker){context=c;this.worker=worker;}
    private void owner(){if(Looper.myLooper()!=main.getLooper())throw new IllegalStateException("Launch check owner must be main thread");}
    void cancel(){owner();Operation op=active;if(op!=null&&op.finish()){LaunchDiagnostic.outcome(context,op.id,"CANCELLED");op.stop();}}
    void start(String id,Work work,Consumer<DrasticLauncher.Plan> success,Consumer<Exception> failure){
        owner();cancel();Operation op=new Operation(id);active=op;
        op.timeout=()->{if(op.finish()){LaunchDiagnostic.failure(context,id,LaunchFailure.TIMEOUT);op.stop();failure.accept(new LaunchFailure(LaunchFailure.TIMEOUT));}};
        main.postDelayed(op.timeout,12000);
        op.future=worker.submit(()->{
            DrasticLauncher.Plan result=null;Exception error=null;
            try{op.cancel.throwIfCanceled();result=work.run(op.cancel);op.cancel.throwIfCanceled();}catch(Exception e){error=e;}
            final DrasticLauncher.Plan plan=result;final Exception problem=error;
            main.post(()->{if(!op.finish())return;
                if(problem==null)success.accept(plan);
                else {LaunchDiagnostic.failure(context,id,LaunchFailure.reason(problem));failure.accept(problem);}
            });
        });
    }
}
