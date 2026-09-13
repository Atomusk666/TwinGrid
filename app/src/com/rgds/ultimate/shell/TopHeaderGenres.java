package com.rgds.ultimate.shell;

import android.graphics.Paint;
import java.util.*;

/** Display projection only: never mutates stored genres or classifier results. */
final class TopHeaderGenres {
    static String format(List<String> ids,Paint paint,int width){
        // The taxonomy's published order is stable across source ordering and UI languages.
        Set<String> unique=new HashSet<>(ids);List<String> labels=new ArrayList<>();
        for(String id:GenreTaxonomy.IDS)if(unique.remove(id))labels.add("rpg".equals(id)&&!LocaleSettings.ui().startsWith("zh")?"RPG":UiStrings.display(GenreTaxonomy.label(id)));
        List<String> rest=new ArrayList<>(unique);Collections.sort(rest);
        for(String id:rest)labels.add(UiStrings.display(GenreTaxonomy.label(id)));
        if(labels.isEmpty())return "";
        String full=String.join(" · ",labels);if(paint.measureText(full)<=width)return full;
        String shown=full;while(!shown.isEmpty()&&paint.measureText(shown+"…")>width)shown=shown.substring(0,shown.offsetByCodePoints(shown.length(),-1));
        return shown.isEmpty()?"…":shown.trim()+"…";
    }
}
