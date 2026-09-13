package com.rgds.ultimate.shell;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.TextView;
import org.json.*;

/** Shared toolbar labels/buttons: actual displayed ink is centered in the toolbar band. */
final class ToolbarTextView extends TextView {
    private final Rect ink=new Rect(),iconBox=new Rect(),referenceInk=new Rect();
    private String displayed="";private int x,baseline;
    ToolbarTextView(Context c,String text){super(c);DsTypography.ui(this);setText(text);setSingleLine(true);setIncludeFontPadding(false);setFocusable(false);setGravity(Gravity.LEFT);}
    @Override public void setText(CharSequence text,BufferType type){super.setText(UiStrings.bind(this,text),type);}
    private Drawable measureInk(){
        Drawable icon=getCompoundDrawables()[0];int iconWidth=icon==null?0:16,gap=icon==null?0:getCompoundDrawablePadding();
        int available=Math.max(1,getWidth()-getPaddingLeft()-getPaddingRight()-iconWidth-gap);
        displayed=TextUtils.ellipsize(getText(),getPaint(),available,TextUtils.TruncateAt.END).toString();
        getPaint().getTextBounds(displayed,0,displayed.length(),ink);int textWidth=(int)Math.ceil(getPaint().measureText(displayed));
        int group=iconWidth+gap+textWidth;int gravity=getGravity()&Gravity.HORIZONTAL_GRAVITY_MASK;
        int left=gravity==Gravity.RIGHT?getWidth()-getPaddingRight()-group:gravity==Gravity.CENTER_HORIZONTAL?(getWidth()-group)/2:getPaddingLeft();
        // Center the displayed glyphs, including localized labels and clock digits.
        // A fixed font reference leaves short digit ink visibly above this center.
        referenceInk.set(ink);
        x=left+iconWidth+gap;baseline=(getHeight()-referenceInk.top-referenceInk.bottom)/2;ink.offset(x,baseline);
        iconBox.set(left,(getHeight()-16)/2,left+16,(getHeight()+16)/2);return icon;
    }
    @Override protected void onDraw(Canvas canvas){
        // Single-line TextView may scroll its very-wide internal Layout for
        // center/right gravity. Our drawing uses the visible local view box.
        canvas.save();canvas.translate(getScrollX(),getScrollY());
        Drawable icon=measureInk();if(icon!=null){icon.setBounds(iconBox);icon.draw(canvas);}
        getPaint().setColor(getCurrentTextColor());canvas.drawText(displayed,x,baseline,getPaint());canvas.restore();
    }
    JSONObject geometry()throws JSONException{
        Drawable icon=measureInk();int[] at=new int[2];getLocationOnScreen(at);Rect text=new Rect(ink),image=new Rect(iconBox);text.offset(at[0],at[1]);image.offset(at[0],at[1]);
        return new JSONObject().put("inkBounds",VisualDiagnostics.rect(text)).put("iconBounds",icon==null?JSONObject.NULL:VisualDiagnostics.rect(image)).put("baseline",at[1]+baseline).put("localBaseline",baseline).put("referenceTop",at[1]+baseline+referenceInk.top).put("referenceBottom",at[1]+baseline+referenceInk.bottom).put("bandCenterY",at[1]+getHeight()/2.0).put("inkCenterY",(text.top+text.bottom)/2.0).put("displayedText",displayed).put("ellipsis",!displayed.equals(getText().toString())).put("scrollCompensationX",getScrollX()).put("scrollCompensationY",getScrollY()).put("layout","SHARED_TOOLBAR_ACTUAL_INK_CENTER");
    }
}
