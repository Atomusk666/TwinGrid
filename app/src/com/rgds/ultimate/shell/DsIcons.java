package com.rgds.ultimate.shell;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Original RGDS symbols on one 16x16 grid. Integer pixels, two ink levels, no Unicode icons. */
final class DsIcons extends Drawable {
    static final int GAME=0,LIBRARY=1,FAVORITE=2,RECENT=3,SETTINGS=4,FOLDER=5,GENRES=6,SEARCH=7,TASKS=8,BACK=9,CHECK=10,PAUSE=11,PLAY=12,ERROR=13,CLOSE=14,DETAILS=15;
    final int symbol;private final Paint p=new Paint();private boolean enabled=true;
    DsIcons(int symbol,int size){this.symbol=symbol;setBounds(0,0,size,size);}
    private void box(Canvas c,int l,int t,int r,int b,int color){p.setColor(color);c.drawRect(l,t,r,b,p);}
    private void ink(Canvas c,int l,int t,int r,int b){box(c,l,t,r,b,enabled?ClassicUi.INK:ClassicUi.MUTED);}
    private void light(Canvas c,int l,int t,int r,int b){box(c,l,t,r,b,Color.WHITE);}
    @Override public boolean isStateful(){return true;}
    @Override protected boolean onStateChange(int[] s){boolean e=false;for(int n:s)if(n==android.R.attr.state_enabled)e=true;if(e==enabled)return false;enabled=e;invalidateSelf();return true;}
    @Override public void draw(Canvas c){
        Rect b=getBounds();int scale=Math.max(1,Math.min(b.width(),b.height())/16);
        c.save();c.translate(b.centerX()-8*scale,b.centerY()-8*scale);c.scale(scale,scale);
        switch(symbol){
            case LIBRARY: ink(c,1,2,5,14);ink(c,6,2,10,14);ink(c,11,2,15,14);light(c,2,3,4,10);light(c,7,3,9,10);light(c,12,3,14,10);break;
            case FAVORITE: {int[][] a={{7,1,9,5},{5,5,11,7},{1,6,15,8},{3,8,13,10},{5,10,11,12},{3,12,7,15},{9,12,13,15}};for(int[] r:a)ink(c,r[0],r[1],r[2],r[3]);light(c,7,6,9,10);break;}
            case RECENT: ink(c,3,1,13,2);ink(c,1,3,2,13);ink(c,14,3,15,13);ink(c,3,14,13,15);ink(c,2,2,3,3);ink(c,13,2,14,3);ink(c,2,13,3,14);ink(c,13,13,14,14);ink(c,7,4,9,9);ink(c,8,8,12,10);break;
            case SETTINGS: for(int y=3;y<15;y+=4){ink(c,1,y,15,y+1);int x=y==7?10:4;ink(c,x,y-2,x+3,y+3);light(c,x+1,y-1,x+2,y+2);}break;
            case FOLDER: ink(c,1,3,7,5);ink(c,1,5,15,14);light(c,2,6,14,12);box(c,3,7,14,12,ClassicUi.SELECT);break;
            case GENRES: for(int y=2;y<13;y+=7)for(int x=2;x<13;x+=7){ink(c,x,y,x+5,y+5);light(c,x+1,y+1,x+4,y+3);}break;
            case SEARCH: ink(c,3,1,9,2);ink(c,1,3,2,9);ink(c,10,3,11,9);ink(c,3,10,9,11);ink(c,2,2,3,3);ink(c,9,2,10,3);ink(c,2,9,3,10);ink(c,9,9,12,12);ink(c,11,11,14,14);ink(c,13,13,15,15);break;
            case TASKS: ink(c,3,2,14,15);light(c,4,3,13,14);ink(c,6,1,11,4);for(int y=6;y<13;y+=3){ink(c,5,y,7,y+1);ink(c,8,y,12,y+1);}break;
            case BACK: for(int i=0;i<5;i++)ink(c,2+i,7-i,4+i,9+i);ink(c,6,7,14,9);break;
            case CHECK: ink(c,2,7,4,9);ink(c,4,9,6,11);for(int i=0;i<6;i++)ink(c,5+i,10-i,7+i,12-i);break;
            case PAUSE: ink(c,3,2,6,14);ink(c,10,2,13,14);break;
            case PLAY: for(int i=0;i<6;i++)ink(c,4+i,2+i,5+i,14-i);break;
            case ERROR: ink(c,7,1,9,10);ink(c,7,12,9,15);break;
            case CLOSE: for(int i=2;i<14;i++){ink(c,i,i,i+1,i+1);ink(c,15-i,i,16-i,i+1);}break;
            case DETAILS: ink(c,3,1,13,15);light(c,4,2,12,14);for(int y=4;y<13;y+=3)ink(c,5,y,11,y+1);break;
            case 16: for(int i=0;i<9;i++)ink(c,4+i,11-i,6+i,13-i);ink(c,2,9,8,11);ink(c,2,12,4,15);break;
            case 17: ink(c,1,3,5,14);ink(c,6,1,10,12);ink(c,11,3,15,14);light(c,2,4,4,12);light(c,7,2,9,10);light(c,12,4,14,12);break;
            case 18: ink(c,2,2,14,10);ink(c,4,10,12,12);ink(c,6,12,10,14);light(c,4,4,12,8);ink(c,7,3,9,11);break;
            case 19: ink(c,3,1,5,15);ink(c,5,2,14,8);light(c,6,3,10,5);ink(c,1,14,8,15);break;
            case 20: ink(c,3,2,13,4);ink(c,2,4,14,11);light(c,4,5,12,8);ink(c,2,11,5,14);ink(c,11,11,14,14);break;
            case 21: ink(c,4,1,12,3);ink(c,1,4,3,12);ink(c,13,4,15,12);ink(c,4,13,12,15);ink(c,6,6,10,10);ink(c,3,3,5,5);ink(c,11,3,13,5);ink(c,3,11,5,13);ink(c,11,11,13,13);break;
            case 22: ink(c,3,4,13,11);for(int x=3;x<13;x+=3)ink(c,x,2,x+2,4);ink(c,1,7,3,12);ink(c,4,11,11,15);light(c,4,5,6,9);break;
            case 23: ink(c,7,1,9,15);ink(c,1,7,15,9);ink(c,3,3,6,5);ink(c,10,3,13,5);ink(c,3,11,6,13);ink(c,10,11,13,13);break;
            case 24: ink(c,3,3,13,13);ink(c,6,1,10,4);ink(c,12,6,15,10);light(c,1,6,6,10);light(c,6,10,10,15);break;
            case 25: ink(c,6,2,14,5);ink(c,6,3,8,13);ink(c,12,3,14,11);ink(c,2,11,7,14);ink(c,9,9,13,12);break;
            case 26: ink(c,7,6,9,15);ink(c,2,3,7,7);ink(c,9,1,14,5);ink(c,4,13,12,15);light(c,3,4,6,5);light(c,10,2,13,3);break;
            case 27: ink(c,2,2,14,14);light(c,4,4,12,12);ink(c,7,4,9,12);ink(c,4,7,12,9);break;
            case 28: ink(c,4,1,12,3);ink(c,11,3,14,7);ink(c,8,6,12,9);ink(c,7,8,10,11);ink(c,7,13,10,15);break;
            case 29: for(int i=0;i<5;i++)ink(c,3+i,5+i,13-i,6+i);break;
            case 30: for(int i=0;i<5;i++){ink(c,5+i,3+i,7+i,5+i);ink(c,5+i,11-i,7+i,13-i);}break;
            default: ink(c,3,1,12,2);ink(c,2,2,14,15);light(c,4,3,12,10);box(c,5,4,11,9,ClassicUi.SELECT);for(int x=4;x<13;x+=2)light(c,x,12,x+1,15);
        }
        c.restore();
    }
    @Override public void setAlpha(int a){p.setAlpha(a);}@Override public void setColorFilter(ColorFilter f){p.setColorFilter(f);}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
