package com.rgds.ultimate.shell;
/** Small DS-style activity indicator; animation says busy, not progress percentage. */
final class ScanBusyView extends android.view.View {
    private final android.graphics.Paint paint=new android.graphics.Paint();
    private boolean active;
    private final Runnable frame=()->{invalidate();schedule();};
    void active(boolean value){active=value;removeCallbacks(frame);if(value&&isShown())post(frame);else invalidate();}
    private void schedule(){removeCallbacks(frame);if(active&&isShown()&&getWindowVisibility()==VISIBLE)postDelayed(frame,180);}
    @Override protected void onVisibilityChanged(android.view.View v,int visibility){super.onVisibilityChanged(v,visibility);if(frame!=null)schedule();}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(frame!=null)schedule();}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();schedule();}
    @Override protected void onDetachedFromWindow(){removeCallbacks(frame);super.onDetachedFromWindow();}
    ScanBusyView(android.content.Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    @Override protected void onDraw(android.graphics.Canvas canvas){
        if(!isShown())return;
        int step=(int)(android.os.SystemClock.uptimeMillis()/180)%4;
        for(int n=0;n<4;n++){paint.setColor(n==step?ClassicUi.ACCENT:ClassicUi.LINE);int x=n%2*10,y=n/2*10;canvas.drawRect(x,y,x+6,y+6,paint);}
    }
}
