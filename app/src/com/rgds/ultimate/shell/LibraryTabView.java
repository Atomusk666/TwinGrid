package com.rgds.ultimate.shell;

import android.content.Context;
import android.graphics.*;
import android.widget.TextView;
import org.json.*;

/** A 48px tab lays out actual pixel ink separately from the font's 28px line box. */
final class LibraryTabView extends TextView {
    private final DsIcons icon;
    private final Rect ink=new Rect(),iconBox=new Rect();
    private int baseline,textX;
    LibraryTabView(Context c,String label,int symbol,Runnable action){
        super(c);icon=new DsIcons(symbol,16);setText(label);DsTypography.ui(this);
        setSingleLine(true);setPadding(0,0,0,0);setBackground(new ClassicUi.TabFace());
        setOnClickListener(v->action.run());
    }
    @Override public void setText(CharSequence text,BufferType type){super.setText(UiStrings.bind(this,text),type);}
    private void measureInk(){
        String value=getText().toString();getPaint().getTextBounds(value,0,value.length(),ink);
        baseline=getHeight()-8-ink.bottom;
        textX=2*Math.round((getWidth()-getPaint().measureText(value))/4f);
        ink.offset(textX,baseline);
        iconBox.set(getWidth()/2-8,4,getWidth()/2+8,20);
    }
    @Override protected void onDraw(Canvas canvas){
        measureInk();icon.setBounds(iconBox);icon.draw(canvas);
        getPaint().setColor(isEnabled()?ClassicUi.INK:ClassicUi.MUTED);
        canvas.drawText(getText().toString(),textX,baseline,getPaint());
    }
    JSONObject geometry()throws JSONException{
        measureInk();int[] at=new int[2];getLocationOnScreen(at);
        Rect text=new Rect(ink),image=new Rect(iconBox);text.offset(at[0],at[1]);image.offset(at[0],at[1]);
        int border=getHeight()-2*DsGrid.EDGE;
        return new JSONObject().put("inkBounds",VisualDiagnostics.rect(text)).put("iconBounds",VisualDiagnostics.rect(image))
            .put("baseline",at[1]+baseline).put("borderTop",at[1]+border).put("inkToBorder",border-ink.bottom)
            .put("iconToInk",ink.top-iconBox.bottom).put("leftSpace",ink.left).put("rightSpace",getWidth()-ink.right)
            .put("layout","EXPLICIT_ICON_AND_PIXEL_INK").put("compoundDrawables",false);
    }
}
