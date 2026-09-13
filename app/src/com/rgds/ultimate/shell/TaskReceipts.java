package com.rgds.ultimate.shell;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.util.*;

/** Frozen results are independent of current metadata and of the execution controller. */
final class TaskReceipts {
    static final int LIMIT=20;
    static final class Receipt {
        final String key,session;final JSONObject summary;final long created;
        Receipt(String key,String session,String json,long created)throws JSONException{this.key=key;this.session=session;summary=new JSONObject(json);this.created=created;}
        String label(){return new java.text.SimpleDateFormat("MM-dd HH:mm",Locale.CHINA).format(new Date(created))+" · "+summary.optString("scope")+" · "+stateLabel(summary.optString("state"));}
    }
    private static String stateLabel(String state){switch(state){case "COMPLETE":return UiStrings.msg("ui_f28461bb49c8");case "PARTIAL":return UiStrings.msg("ui_dd52d161c87d");case "EMPTY":return UiStrings.msg("ui_6401bce93bfc");case "ERROR":return UiStrings.msg("ui_28384d7afd2e");case "INTERRUPTED":return UiStrings.msg("ui_22a39a9a9f0a");default:return state;}}
    private final SQLiteDatabase db;private final SharedPreferences state;
    private volatile List<Receipt> recent=Collections.emptyList();
    TaskReceipts(Context c,SQLiteDatabase d)throws JSONException{db=d;state=c.getSharedPreferences("task_receipts_v1",0);db.execSQL("CREATE TABLE IF NOT EXISTS task_receipts_v1 (receipt_key TEXT PRIMARY KEY,session TEXT NOT NULL,summary TEXT NOT NULL,items TEXT NOT NULL,created INTEGER NOT NULL)");reload();}
    static boolean result(CoverTask.Snapshot s){return !s.id.isEmpty()&&(s.terminal()&&s.state!=CoverTask.State.IDLE||s.state==CoverTask.State.INTERRUPTED);}
    static String key(CoverTask.Snapshot s){return s.trigger+":"+s.id+":"+s.resultRevision;}
    List<Receipt> list(){return recent;}
    Receipt find(String key){for(Receipt r:recent)if(r.key.equals(key))return r;return null;}
    Receipt session(String id){for(Receipt r:recent)if(r.session.equals(id))return r;return null;}
    boolean unread(String key){return state.getLong("seen:"+key,0)==0&&state.getLong("dismissed:"+key,0)==0;}
    void acknowledge(String key,boolean dismiss){if(find(key)==null)return;state.edit().putLong((dismiss?"dismissed:":"seen:")+key,System.currentTimeMillis()).apply();}
    boolean notified(String key){return state.getBoolean("notified:"+key,false)||!unread(key);}
    void notifiedNow(String key){state.edit().putBoolean("notified:"+key,true).apply();}
    void capture(CoverTask.Snapshot s,List<CoverTask.Item> items,Map<String,GameEntry> games,MetadataManager metadata)throws JSONException{
        if(!result(s)||find(key(s))!=null)return;
        JSONArray frozen=new JSONArray();for(CoverTask.Item item:items){GameEntry g=games.get(item.id);frozen.put(new JSONObject().put("id",item.id).put("name",g==null?item.id:GameDisplay.name(g,metadata)).put("code",g==null?"":g.gameCode).put("result",item.result.name()).put("candidateAtResult",metadata!=null&&metadata.info(item.id).status.equals("CANDIDATE")).put("reason",item.reason).put("retryAt",item.retryAt).put("alternative",item.alternative));}
        db.execSQL("INSERT OR IGNORE INTO task_receipts_v1 VALUES(?,?,?,?,?)",new Object[]{key(s),s.id,s.json().toString(),frozen.toString(),s.ended>0?s.ended:s.changed});
        // Keep all frozen rows. The recent view is bounded; new work must not erase earlier evidence.
        reload();
    }
    JSONArray items(String key)throws JSONException{try(Cursor c=db.rawQuery("SELECT items FROM task_receipts_v1 WHERE receipt_key=?",new String[]{key})){return c.moveToFirst()?new JSONArray(c.getString(0)):new JSONArray();}}
    private void reload()throws JSONException{List<Receipt> next=new ArrayList<>();try(Cursor c=db.rawQuery("SELECT receipt_key,session,summary,created FROM task_receipts_v1 ORDER BY created DESC LIMIT 20",null)){while(c.moveToNext())next.add(new Receipt(c.getString(0),c.getString(1),c.getString(2),c.getLong(3)));}recent=Collections.unmodifiableList(next);}
}
