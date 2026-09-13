package com.rgds.ultimate.shell;
import android.app.Activity;import android.widget.TextView;import android.view.View;import android.text.*;import org.json.*;import java.util.*;
/** Explicit DUMP-only audit of the frozen library. Not called by navigation or selection. */
final class LabelAudit {
    static JSONObject capture(Activity a)throws JSONException{
        MetadataManager m=MetadataManager.get(a);List<GameEntry> games=ShellStateRepository.get(a).allGamesCopy();List<DirectoryIndex.Item> items=new ArrayList<>();for(GameEntry g:games)items.add(new DirectoryIndex.Item(g));Map<String,String> editions=EditionLabels.forItems(items,m);
        TextView t=ClassicUi.text(a,"",20);t.setLayoutParams(new android.view.ViewGroup.LayoutParams(466,48));DsTypography.title(t,20);ClassicUi.lines(t,2);if(android.os.Build.VERSION.SDK_INT>=28)t.setLineHeight(20);t.setGravity(android.view.Gravity.CENTER_VERTICAL);
        JSONArray rows=new JSONArray();for(GameEntry g:games){String name=GameDisplay.name(g,m),badge=editions.getOrDefault(g.gameId,"");int badgeWidth=badge.isEmpty()?0:Math.min(126,Math.max(30,(int)Math.ceil(t.getPaint().measureText(badge))));int width=466-(badgeWidth==0?0:badgeWidth+8);
            t.getLayoutParams().width=width;t.forceLayout();t.setText(name);t.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(48,View.MeasureSpec.EXACTLY));t.layout(0,0,width,48);Layout l=t.getLayout();JSONArray lines=new JSONArray();for(int n=0;n<l.getLineCount();n++)lines.put(new JSONObject().put("baseline",t.getBaseline()+l.getLineBaseline(n)-l.getLineBaseline(0)).put("ellipsisCount",l.getEllipsisCount(n)).put("start",l.getLineStart(n)).put("end",l.getLineVisibleEnd(n)));
            rows.put(new JSONObject().put("gameId",g.gameId).put("gameCode",g.gameCode).put("fileName",g.fileName).put("menuTitle",g.menuTitle()).put("displayedName",name).put("editionLabel",badge).put("titleWidth",width).put("rowHeight",48).put("lineCount",l.getLineCount()).put("lines",lines).put("uncoveredByPixelFont",PixelCoverage.missing(name+badge)));
        }
        return new JSONObject().put("locale",LocaleSettings.ui()).put("libraryCount",games.size()).put("rows",rows).put("font",DsTypography.FACE).put("provenance","Read-only DUMP; actual frozen library, same name/edition components, native TextView measurement. Visible row draw identity is recorded separately in screen geometry.");
    }
}
