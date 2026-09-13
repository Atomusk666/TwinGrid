package com.rgds.ultimate.shell;

import android.app.Activity;
import android.graphics.*;
import android.text.Layout;
import android.view.*;
import android.widget.TextView;
import org.json.*;

/** On-demand observation behind Android DUMP. No command/action API and no persistent logging. */
final class VisualDiagnostics {
    static JSONArray rect(Rect r){return new JSONArray().put(r.left).put(r.top).put(r.right).put(r.bottom);}
    static JSONObject capture(Activity a)throws JSONException{
        JSONObject out=new JSONObject();JSONArray nodes=new JSONArray();int display=a.getWindowManager().getDefaultDisplay().getDisplayId();
        out.put("available",true).put("display",display).put("uptime",android.os.SystemClock.uptimeMillis()).put("fontLoadMillis",DsTypography.loadMillis)
                .put("coordinateSpace","physical pixels; global bounds; no screenshot cropping").put("nodes",nodes);
        walk(ShellWindowPolicy.window(a).getDecorView(),"activity",0,nodes);
        int n=0;for(View root:ShellDialogBuilder.visibleWindows(display))walk(root,"modal_"+(n++),1,nodes);
        return out;
    }
    private static void walk(View v,String path,int layer,JSONArray out)throws JSONException{
        if(v.getVisibility()!=View.VISIBLE||!v.isShown()||v.getWidth()==0||v.getHeight()==0)return;
        int[] xy=new int[2];v.getLocationOnScreen(xy);Rect bounds=new Rect(xy[0],xy[1],xy[0]+v.getWidth(),xy[1]+v.getHeight()),clip=new Rect();boolean visible=v.getGlobalVisibleRect(clip);
        // getGlobalVisibleRect uses the window root; dialog windows have a screen offset.
        int[] inWindow=new int[2];v.getLocationInWindow(inWindow);clip.offset(xy[0]-inWindow[0],xy[1]-inWindow[1]);
        String id=path+"/"+(v.getTag()==null?v.getClass().getSimpleName():v.getTag().toString());
        JSONObject o=new JSONObject().put("id",id).put("class",v.getClass().getSimpleName()).put("layer",layer)
                .put("bounds",rect(bounds)).put("visibleClip",rect(clip)).put("visible",visible)
                .put("clickable",v.isClickable()).put("focusable",v.isFocusable()).put("focused",v.hasFocus())
                .put("selected",v.isSelected()).put("pressed",v.isPressed()).put("enabled",v.isEnabled());
        if(v.getBackground() instanceof HomeUi.Surface){
            int kind=((HomeUi.Surface)v.getBackground()).kind;Rect draw=new Rect(bounds);draw.inset(2,2);
            o.put("surfaceStyle",new JSONObject().put("recipe","HOME_LOCAL_V2").put("kind",kind).put("gradient",false).put("outerBorderPx",2).put("textFill","#FBFBFB").put("iconWellFill","#E3E3E3").put("textRule","#EDEDED / 1px every 4px, slot and recent only"));
            o.put("focusDrawing",new JSONObject().put("active",v.isSelected()).put("bounds",rect(draw)).put("mode",v instanceof HomeUi.Panel?"AFTER_CHILDREN_INTERNAL":"TOOL_SURFACE_INTERNAL").put("whiteWidth",8).put("accentWidth",4).put("cornerLength",26));
            if(kind>=HomeUi.SETTINGS)o.put("toolIconBounds",rect(new Rect(bounds.centerX()-16,bounds.centerY()-16,bounds.centerX()+16,bounds.centerY()+16)));
        }
        if(v.getBackground() instanceof ClassicUi.Edge){
            ClassicUi.Edge edge=(ClassicUi.Edge)v.getBackground();
            o.put("surfaceStyle",new JSONObject().put("recipe","DS_APPROVED_SETTINGS_SHARED_V96").put("gradient",new JSONArray().put("#F3F3F3").put("#E3E3E3").put("#CBCBCB")).put("gradientStop",0.55).put("outerBorderPx",DsGrid.EDGE).put("innerLightDarkPx",DsGrid.EDGE).put("shadow","integer-pixel inset bottom/right").put("rounded",false).put("insetEditor",edge instanceof ClassicUi.InputEdge).put("cachedUntilBoundsChange",true));
            boolean active=v.isEnabled()&&(edge.selected||v.isFocused());Rect draw=new Rect(bounds);draw.inset(2,2);
            JSONArray chain=new JSONArray();android.view.ViewParent parent=v.getParent();while(parent instanceof ViewGroup){ViewGroup p=(ViewGroup)parent;chain.put(new JSONObject().put("class",p.getClass().getSimpleName()).put("clipChildren",p.getClipChildren()).put("clipToPadding",p.getClipToPadding()));parent=p.getParent();}
            o.put("focusDrawing",new JSONObject().put("active",active).put("owner",id).put("mode","INTERNAL_SAFE_AREA_BACKGROUND").put("bounds",rect(draw)).put("outset",-2).put("clipChain",chain));
        }
        if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;o.put("clipChildren",group.getClipChildren()).put("clipToPadding",group.getClipToPadding()).put("padding",new JSONArray().put(v.getPaddingLeft()).put(v.getPaddingTop()).put(v.getPaddingRight()).put(v.getPaddingBottom()));}
        if(v instanceof android.widget.ScrollView){Rect viewport=new Rect(bounds);if(((ViewGroup)v).getClipToPadding()){viewport.left+=v.getPaddingLeft();viewport.top+=v.getPaddingTop();viewport.right-=v.getPaddingRight();viewport.bottom-=v.getPaddingBottom();}o.put("scrollViewportInScreen",rect(viewport)).put("scrollOffsetY",v.getScrollY()).put("visibleClipLimit","getGlobalVisibleRect alone does not model clipToPadding; use this native scroll viewport for child clipping.");}
        DsGrid.Spec grid=DsGrid.of(v);
        if(v instanceof ClassicScreen&&grid!=null)o.put("grid",grid.json());
        if(grid!=null&&(v.getBackground() instanceof ClassicUi.Edge||v.getBackground() instanceof ClassicUi.TabFace||v.getBackground() instanceof ClassicUi.InputEdge))o.put("gridAlignment",new JSONObject().put("originX",grid.originX).put("originY",grid.originY).put("subdivision",grid.unit).put("leftOffset",Math.floorMod(bounds.left-grid.originX,grid.unit)).put("topOffset",Math.floorMod(bounds.top-grid.originY,grid.unit)).put("rightOffset",Math.floorMod(bounds.right-grid.originX,grid.unit)).put("bottomOffset",Math.floorMod(bounds.bottom-grid.originY,grid.unit)));
        if(v instanceof TextView){
            TextView t=(TextView)v;Layout l=t.getLayout();JSONArray lines=new JSONArray();
            Rect textBox=new Rect(bounds.left+t.getCompoundPaddingLeft(),bounds.top+t.getCompoundPaddingTop(),bounds.right-t.getCompoundPaddingRight(),bounds.bottom-t.getCompoundPaddingBottom());
            o.put("text",t.getText().toString()).put("textRole",DsTypography.role(t)).put("typeface",DsTypography.face(t)).put("fontPx",t.getTextSize()).put("fontPadding",t.getIncludeFontPadding())
                    .put("uncoveredByPixelFont",PixelCoverage.missing(t.getText())).put("textAntiAlias",t.getPaint().isAntiAlias()).put("textScaleX",t.getTextScaleX()).put("subpixelText",t.getPaint().isSubpixelText())
                    .put("textMeasureBox",rect(textBox)).put("firstBaseline",xy[1]+t.getBaseline()).put("maxLines",t.getMaxLines())
                    .put("lineCount",t.getLineCount()).put("lineHeight",t.getLineHeight()).put("fontAscent",t.getPaint().ascent()).put("fontDescent",t.getPaint().descent()).put("lines",lines);
            boolean clipInk=false;
            if(l!=null){int base=xy[1]+t.getBaseline()-l.getLineBaseline(0)-t.getScrollY();
                for(int i=0;i<l.getLineCount();i++){int from=l.getLineStart(i),to=l.getLineVisibleEnd(i);Rect ink=new Rect();String s=t.getText().toString().substring(Math.min(from,t.length()),Math.min(to,t.length()));t.getPaint().getTextBounds(s,0,s.length(),ink);int x=xy[0]+t.getCompoundPaddingLeft()+Math.round(l.getLineLeft(i))-t.getScrollX();ink.offset(x,base+l.getLineBaseline(i));boolean clipped=ink.top<bounds.top||ink.bottom>bounds.bottom||ink.left<bounds.left||ink.right>bounds.right;clipInk|=clipped;
                    lines.put(new JSONObject().put("start",from).put("end",to).put("baseline",base+l.getLineBaseline(i)).put("advance",l.getLineWidth(i)).put("ellipsisCount",l.getEllipsisCount(i)).put("inkBoundsPrimaryPaint",rect(ink)).put("exceedsView",clipped));
                }
            }
            o.put("primaryPaintInkExceedsView",clipInk).put("inkMeasurementLimit","Primary paint bounds; spans and platform fallback may differ. Scroll clipping and ellipsis are reported, not automatically failures.");
            if(id.contains("font_sample")){JSONObject support=new JSONObject();String value=t.getText().toString();for(int at=0;at<value.length();){int cp=value.codePointAt(at);at+=Character.charCount(cp);if(!Character.isWhitespace(cp))support.put("U+"+Integer.toHexString(cp).toUpperCase(java.util.Locale.ROOT),t.getPaint().hasGlyph(new String(Character.toChars(cp))));}o.put("glyphSupportIncludingFallback",support);}
            JSONArray icons=new JSONArray();for(android.graphics.drawable.Drawable d:t.getCompoundDrawables())if(d instanceof DsIcons)icons.put(new JSONObject().put("symbol",((DsIcons)d).symbol).put("drawableLocalBounds",rect(d.getBounds())));o.put("icons",icons);
        }
        if(v instanceof ClassicUi.Icon){ClassicUi.Icon icon=(ClassicUi.Icon)v;int size=icon.bitmap==null?16*Math.max(1,Math.min(v.getWidth(),v.getHeight())/16):32*Math.max(1,Math.min(v.getWidth()/32,v.getHeight()/32));o.put("iconRendering",icon.identity()).put("romIcon",icon.bitmap!=null).put("iconContentBounds",rect(new Rect(bounds.centerX()-size/2,bounds.centerY()-size/2,bounds.centerX()+size/2,bounds.centerY()+size/2)));}
        if(v instanceof LibraryTabView)o.put("navigationInk",((LibraryTabView)v).geometry());
        if(v instanceof ToolbarTextView)o.put("toolbarInk",((ToolbarTextView)v).geometry());
        if(v instanceof DescriptionView)o.put("descriptionLayout",((DescriptionView)v).geometry());
        out.put(o);
        if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int i=0;i<group.getChildCount();i++)walk(group.getChildAt(i),id+"/"+i,layer,out);}
    }
}
