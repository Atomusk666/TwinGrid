package com.rgds.ultimate.shell;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.BatteryManager;
import android.view.View;

/** Live system battery state in a compact, integer-pixel DS-style case. */
final class BatteryIndicatorView extends View {
    private final Paint paint=new Paint();
    private final Paint numberPaint=new Paint();
    private final android.graphics.Rect numberBounds=new android.graphics.Rect();
    private int percent=-1;
    private boolean charging,registered,numberMode;
    private Runnable modeChanged;
    private final Runnable preferenceChanged=()->{
        boolean next=BatterySettings.numbers(getContext());if(next==numberMode)return;
        numberMode=next;if(modeChanged!=null)modeChanged.run();invalidate();
    };
    private final BroadcastReceiver receiver=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){bindBattery(intent);}
    };

    BatteryIndicatorView(Context context){
        super(context);setTag("toolbar_battery");setFocusable(false);
        numberMode=BatterySettings.numbers(context);DsTypography.paint(context,numberPaint,20);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);refreshDescription();
    }
    boolean numbers(){return numberMode;}
    void setOnModeChanged(Runnable action){modeChanged=action;}
    @Override protected void onAttachedToWindow(){
        super.onAttachedToWindow();
        BatterySettings.addListener(preferenceChanged);preferenceChanged.run();
        try{
            Intent current=getContext().registerReceiver(receiver,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            registered=true;bindBattery(current);
        }catch(RuntimeException unavailable){bindBattery(null);}
    }
    @Override protected void onDetachedFromWindow(){
        BatterySettings.removeListener(preferenceChanged);
        if(registered){try{getContext().unregisterReceiver(receiver);}catch(IllegalArgumentException ignored){}registered=false;}
        super.onDetachedFromWindow();
    }
    private void bindBattery(Intent intent){
        int next=-1;boolean nextCharging=false;
        if(intent!=null&&intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT,true)){
            int level=intent.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=intent.getIntExtra(BatteryManager.EXTRA_SCALE,-1);
            if(scale>0&&level>=0&&level<=scale)next=(int)((level*100L+scale/2)/scale);
            nextCharging=intent.getIntExtra(BatteryManager.EXTRA_STATUS,BatteryManager.BATTERY_STATUS_UNKNOWN)==BatteryManager.BATTERY_STATUS_CHARGING;
        }
        boolean changed=percent!=next||charging!=nextCharging;percent=next;charging=nextCharging;
        refreshDescription();if(changed)invalidate();
    }
    void refreshDescription(){
        boolean zh="zh".equals(LocaleSettings.ui());
        String value=percent<0?(zh?"电量未知":"Battery level unknown"):(zh?"电量 ":"Battery ")+percent+"%";
        if(charging)value+=zh?"，充电中":", charging";
        if(!value.contentEquals(getContentDescription()==null?"":getContentDescription()))setContentDescription(value);
    }
    private void block(Canvas canvas,int color,int left,int top,int right,int bottom){
        paint.setColor(color);canvas.drawRect(left,top,right,bottom,paint);
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        if(numberMode){
            String value=percent<0?"--%":percent+"%";
            numberPaint.setColor(percent>=0&&percent<=20?0xFFD94C35:0xFF202020);
            numberPaint.getTextBounds(value,0,value.length(),numberBounds);
            int y=(getHeight()-numberBounds.height())/2-numberBounds.top;
            canvas.drawText(value,Math.max(0,getWidth()-numberPaint.measureText(value)),y,numberPaint);return;
        }
        final int ink=0xFF202020;
        block(canvas,ink,32,6,36,14);block(canvas,0xFFB8B8B8,32,8,34,12);
        block(canvas,ink,0,2,32,18);block(canvas,0xFFB8B8B8,2,4,30,16);
        block(canvas,0xFFFFFFFF,2,4,28,6);block(canvas,0xFF787878,2,14,30,16);
        block(canvas,0xFFF4F4F4,4,6,28,14);
        if(percent<0){
            // A pixel question mark distinguishes unavailable capacity from an empty battery.
            block(canvas,ink,12,6,18,8);block(canvas,ink,16,8,20,10);
            block(canvas,ink,14,10,18,12);block(canvas,ink,14,13,16,15);
        }else{
            int cells=(percent+24)/25;boolean low=percent<=20;
            for(int i=0;i<cells;i++){
                int x=4+i*6;
                block(canvas,low?0xFFD94C35:0xFF30C956,x,6,x+5,14);
                block(canvas,low?0xFFFFAA73:0xFF8AF19A,x,6,x+5,8);
                block(canvas,low?0xFF913521:0xFF178C38,x,12,x+5,14);
            }
        }
        if(charging){
            // A fixed bolt reports charging; the fill still follows the measured capacity.
            block(canvas,ink,16,2,20,6);block(canvas,ink,14,6,18,10);
            block(canvas,ink,12,8,20,12);block(canvas,ink,14,12,18,16);
            block(canvas,ink,12,16,16,18);
            block(canvas,0xFFFFFFA4,16,4,18,8);block(canvas,0xFFFFFFA4,14,8,18,10);
            block(canvas,0xFFFFFFA4,16,10,18,12);block(canvas,0xFFFFFFA4,14,12,16,16);
        }
    }
}
