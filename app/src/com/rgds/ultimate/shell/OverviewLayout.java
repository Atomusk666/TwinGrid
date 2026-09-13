package com.rgds.ultimate.shell;

import android.text.*;

/** Native layout of a useful excerpt. Full source text is never modified. */
final class OverviewLayout {
    static final int VERSION=2;
    static StaticLayout layout(String text,int width,TextPaint paint){
        return StaticLayout.Builder.obtain(text,0,text.length(),paint,Math.max(1,width))
                .setIncludePad(false).setLineSpacing(2,1).build();
    }
    static StaticLayout fit(String text,int width,int height,TextPaint paint){
        StaticLayout full=layout(text,width,paint);
        if(full.getHeight()<=height||text.isEmpty())return full;
        int lines=0;while(lines<full.getLineCount()&&full.getLineBottom(lines)<=height)lines++;
        // Actual screens reserve at least one readable line. Retain content even for a zero-height probe.
        if(lines==0)return layout("…",width,paint);
        int end=full.getLineVisibleEnd(lines-1);
        if(end<text.length()&&end>0&&Character.isHighSurrogate(text.charAt(end-1)))end--;
        String prefix=text.substring(0,end).trim();
        StaticLayout result=layout(prefix+"…",width,paint);
        while(!prefix.isEmpty()&&result.getHeight()>height){
            prefix=prefix.substring(0,prefix.offsetByCodePoints(prefix.length(),-1)).trim();
            result=layout(prefix+"…",width,paint);
        }
        // Avoid splitting a Latin word when the nearby word boundary still uses the last line.
        int space=prefix.lastIndexOf(' ');
        if(space>0&&prefix.length()-space<18&&end<text.length()&&Character.isLetter(text.charAt(end))){
            StaticLayout word=layout(prefix.substring(0,space).trim()+"…",width,paint);
            if(word.getLineCount()==result.getLineCount())result=word;
        }
        return result;
    }
}
