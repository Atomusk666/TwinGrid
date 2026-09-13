package com.rgds.ultimate.shell;

import android.net.Uri;
import android.graphics.Bitmap;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

/** Immutable metadata read from one NDS document. ROM and save contents are never stored here. */
public final class GameEntry {
    public enum SaveState {
        FOUND("有存档"), NOT_FOUND("未找到存档"),
        UNAUTHORIZED_OR_UNREADABLE("存档未授权/不可读"), AMBIGUOUS("存档匹配有歧义"),
        SCANNING("存档待核对");
        public final String label;
        SaveState(String text) { label=text; }
    }
    public final String gameId;
    public final Uri uri;
    public final String fileName;
    public final String displayTitle;
    public final String internalTitle;
    public final String bannerTitle;
    public final String gameCode;
    public final String makerCode;
    public final long romBytes;
    public final long romModifiedAt;
    public final boolean hasSave;
    public final SaveState saveState;
    public final long saveBytes;
    public final long saveModifiedAt;
    public final byte[] iconData;
    public final String[] bannerTitles;
    public final String readWarning;
    private Bitmap iconBitmap;
    private boolean iconDecoded;
    private String menuTitle;
    private static final java.util.regex.Pattern INDEX_LETTER=java.util.regex.Pattern.compile("^[A-Z] (?=\\p{IsHan})");
    private static final java.util.regex.Pattern CAPACITY=java.util.regex.Pattern.compile("(?i)[（(][0-9]+(?:\\.[0-9]+)?\\s*[mg]b[）)]");
    // Derived presentation strings only; excluded from the stable on-disk ROM cache.
    volatile String v04MenuTitle,v04VersionTags;

    public GameEntry(
            String gameId,
            Uri uri,
            String fileName,
            String displayTitle,
            String internalTitle,
            String bannerTitle,
            String gameCode,
            String makerCode,
            long romBytes,
            long romModifiedAt,
            SaveState saveState,
            long saveBytes,
            long saveModifiedAt) {
        this(gameId,uri,fileName,displayTitle,internalTitle,bannerTitle,gameCode,makerCode,
                romBytes,romModifiedAt,saveState,saveBytes,saveModifiedAt,new byte[0],new String[0],"");
    }
    public GameEntry(String gameId,Uri uri,String fileName,String displayTitle,String internalTitle,
            String bannerTitle,String gameCode,String makerCode,long romBytes,long romModifiedAt,
            SaveState saveState,long saveBytes,long saveModifiedAt,byte[] icon,String[] titles,String warning) {
        this.gameId = clean(gameId);
        this.uri = uri;
        this.fileName = clean(fileName);
        this.displayTitle = clean(displayTitle);
        this.internalTitle = clean(internalTitle);
        this.bannerTitle = clean(bannerTitle);
        this.gameCode = clean(gameCode);
        this.makerCode = clean(makerCode);
        this.romBytes = romBytes;
        this.romModifiedAt = romModifiedAt;
        this.saveState = saveState;
        this.hasSave = saveState == SaveState.FOUND;
        this.saveBytes = saveBytes;
        this.saveModifiedAt = saveModifiedAt;
        iconData=icon;bannerTitles=titles;readWarning=warning;
    }
    public String basename() {return fileName.length()>4?fileName.substring(0,fileName.length()-4):fileName;}
    public GameEntry withSave(SaveState state,long size,long modified) {
        if(state==saveState&&size==saveBytes&&modified==saveModifiedAt)return this;
        return new GameEntry(gameId,uri,fileName,displayTitle,internalTitle,bannerTitle,gameCode,makerCode,
                romBytes,romModifiedAt,state,size,modified,iconData,bannerTitles,readWarning);
    }
    public synchronized Bitmap icon() {
        if(!iconDecoded) {
            int[] pixels=NdsHeaderParser.iconPixels(iconData);
            if(pixels!=null) iconBitmap=Bitmap.createBitmap(pixels,32,32,Bitmap.Config.ARGB_8888);
            iconDecoded=true;
        }
        return iconBitmap;
    }
    public JSONObject toJson() throws JSONException {
        JSONObject o=new JSONObject();
        o.put("gameId",gameId).put("uri",uriString()).put("fileName",fileName).put("title",displayTitle)
         .put("internalTitle",internalTitle).put("bannerTitle",bannerTitle).put("gameCode",gameCode).put("makerCode",makerCode)
         .put("romBytes",romBytes).put("romModifiedAt",romModifiedAt).put("saveState",saveState.name())
         .put("saveBytes",saveBytes).put("saveModifiedAt",saveModifiedAt).put("warning",readWarning)
         .put("icon",Base64.encodeToString(iconData,Base64.NO_WRAP)).put("bannerTitles",new JSONArray(bannerTitles));
        return o;
    }
    public static GameEntry fromJson(JSONObject o) throws JSONException {
        JSONArray a=o.optJSONArray("bannerTitles");String[] titles=new String[a==null?0:a.length()];
        for(int i=0;i<titles.length;i++) titles[i]=a.optString(i,"");
        return new GameEntry(o.getString("gameId"),Uri.parse(o.getString("uri")),o.getString("fileName"),
            o.optString("title"),o.optString("internalTitle"),o.optString("bannerTitle"),o.optString("gameCode"),o.optString("makerCode"),
            o.optLong("romBytes"),o.optLong("romModifiedAt"),SaveState.valueOf(o.optString("saveState","UNAUTHORIZED_OR_UNREADABLE")),
            o.optLong("saveBytes"),o.optLong("saveModifiedAt"),Base64.decode(o.optString("icon",""),Base64.NO_WRAP),titles,o.optString("warning",""));
    }

    public String bestTitle() {
        if (!displayTitle.isEmpty()) {
            return displayTitle;
        }
        if (!bannerTitle.isEmpty()) {
            return bannerTitle;
        }
        if (!internalTitle.isEmpty()) {
            return internalTitle;
        }
        return fileName.isEmpty() ? "Unknown NDS" : fileName;
    }

    /** Display only: remove indexing letter/capacity; preserve edition and translation text. */
    public String menuTitle() {
        if(menuTitle==null)menuTitle=CAPACITY.matcher(INDEX_LETTER.matcher(bestTitle()).replaceFirst("")).replaceAll("").trim();
        return menuTitle;
    }

    public String uriString() {
        return uri == null ? "" : uri.toString();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
