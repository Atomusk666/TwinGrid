package com.rgds.ultimate.shell;

import org.json.*;
import java.util.*;

/** One bounded operation. Control methods never perform I/O or wait for the network worker. */
final class CoverTask {
    enum State { IDLE, PREPARING, RUNNING, PAUSING, PAUSED, NETWORK, METERED, RETRY_WAIT, GAME, SLEEP, COMPLETE, PARTIAL, EMPTY, ERROR, INTERRUPTED }
    enum Action { START, PAUSE, RESUME, RETRY, NONE }
    enum Result { PENDING, ACTIVE, RETRY, ADDED, REUSED, MISSING, IDENTITY, FAILED, REMOVED }
    static final class Item {
        final String id; final boolean alternative; Result result=Result.PENDING; long retryAt; int attempts; String reason="";
        Item(String id,boolean alternative){this.id=id;this.alternative=alternative;}
    }
    static final class Snapshot {
        final String id,trigger,scope,current,source,detail,indexVersion;final int indexRecords; final long revision,started,changed,ended,retryAt,resultRevision;
        final State state; final Action action; final boolean userPaused,planned;
        final int total,processed,active,remaining,retrying,added,reused,missing,identity,failed,removed,downloads,localProcessed,localTotal,localChanges;final boolean localDone;
        Snapshot(CoverTask t){
            localProcessed=t.localProcessed;localTotal=t.localTotal;localChanges=t.localChanges;localDone=t.localDone;indexVersion=t.indexVersion;indexRecords=t.indexRecords;id=t.id;revision=t.revision;resultRevision=t.resultRevision;trigger=t.trigger;scope=t.scope;current=t.current;source=t.source;detail=t.detail;
            started=t.started;changed=t.changed;ended=t.ended;state=t.state;userPaused=t.userPaused;planned=t.planned;downloads=t.downloads;
            int a=0,u=0,m=0,i=0,f=0,x=0,r=0,p=0,v=0;long retry=0;
            for(Item item:t.items.values())switch(item.result){case ADDED:a++;break;case REUSED:u++;break;case MISSING:m++;break;case IDENTITY:i++;break;case FAILED:f++;break;case REMOVED:x++;break;case RETRY:r++;if(retry==0||item.retryAt<retry)retry=item.retryAt;break;case ACTIVE:v++;break;default:p++;}
            total=t.items.size();added=a;reused=u;missing=m;identity=i;failed=f;removed=x;retrying=r;active=v;remaining=p;processed=a+u+m+i+f+x;retryAt=retry;
            action=state==State.PAUSING||state==State.ERROR?Action.NONE:state==State.PAUSED||state==State.INTERRUPTED?Action.RESUME:
                terminal(state)?(f>0?Action.RETRY:Action.START):Action.PAUSE;
        }
        boolean terminal(){return terminal(state);}
        static boolean terminal(State s){return s==State.IDLE||s==State.COMPLETE||s==State.PARTIAL||s==State.EMPTY||s==State.ERROR;}
        String actionText(){switch(action){case START:return UiStrings.msg("ui_5f62d65013a2");case PAUSE:return UiStrings.msg("ui_75f853d938c7");case RESUME:return UiStrings.msg("ui_7730fe6c7fbe");case RETRY:return UiStrings.msg("ui_70b1aaf12ba1");default:return state==State.PAUSING?UiStrings.msg("ui_f12249e193c2"):UiStrings.msg("ui_4fd026d051ac");}}
        String title(){switch(state){case IDLE:return UiStrings.msg("ui_5ed4dd2d1738");case PREPARING:return UiStrings.msg("ui_65724f2085ff");case RUNNING:return UiStrings.msg("ui_c6329a5ac882");case PAUSING:return UiStrings.msg("ui_f12249e193c2");case PAUSED:return UiStrings.msg("ui_0b6197d7c036");case NETWORK:return UiStrings.msg("ui_84322925c212");case METERED:return UiStrings.msg("ui_9dbf36b68701");case RETRY_WAIT:return UiStrings.msg("ui_85fd10dfa05b");case GAME:return UiStrings.msg("ui_56d1aee96e19");case SLEEP:return UiStrings.msg("ui_e9b92e7af99a");case COMPLETE:return UiStrings.msg("ui_f22047630838");case PARTIAL:return UiStrings.msg("ui_34b4f1b79ed0");case EMPTY:return UiStrings.msg("ui_e3a62a01c589");case ERROR:return UiStrings.msg("ui_ae7f1d2afa20");default:return UiStrings.msg("ui_b9c14cc8a16f");}}
        String progress(){return planned&&total>0?UiStrings.msg("ui_1b5cc7f39f38")+processed+" / "+total:UiStrings.msg("ui_53a32e161089");}
        String brief(){return UiStrings.msg("ui_69ab2f9c68be")+title()+(planned&&total>0?" "+processed+"/"+total:"")+"";}
        String counts(){if(trigger.equals("INDEX"))return TaskPresentation.index(json());return UiStrings.msg("ui_e15546316dd9")+added+UiStrings.msg("ui_878eb42b5b3e")+reused+UiStrings.msg("ui_db23e83f5d5d")+missing+UiStrings.msg("ui_1b4cbfef18ff")+identity+UiStrings.msg("ui_1267d615d5c3")+failed+UiStrings.msg("ui_ace2cb819f0b")+removed+UiStrings.msg("ui_b98d2034ea90")+downloads+UiStrings.msg("ui_e9e0fdc242b4");}
        String waiting(){return UiStrings.msg("ui_f6fedd26989d")+remaining+UiStrings.msg("ui_cdbb372d8d32")+active+UiStrings.msg("ui_31e09a81adb8")+retrying;}
        JSONObject json(){try{return new JSONObject().put("localProcessed",localProcessed).put("localTotal",localTotal).put("localChanges",localChanges).put("localDone",localDone).put("session",id).put("revision",revision).put("resultRevision",resultRevision).put("trigger",trigger).put("scope",scope).put("state",state.name()).put("action",action.name()).put("planned",planned).put("userPaused",userPaused).put("total",total).put("processed",processed).put("active",active).put("remaining",remaining).put("retrying",retrying).put("added",added).put("reused",reused).put("missing",missing).put("identity",identity).put("failed",failed).put("removed",removed).put("downloads",downloads).put("current",current).put("source",source).put("detail",detail).put("started",started).put("changed",changed).put("ended",ended).put("retryAt",retryAt).put("indexVersion",indexVersion).put("indexRecords",indexRecords);}catch(JSONException e){throw new IllegalStateException(e);}}
    }
    private String id="",trigger="",scope="",current="",source="",detail="";
    private long revision,started,changed,ended,resultRevision; private int downloads;private String indexVersion="";private int indexRecords=-1;
    private int localProcessed,localTotal,localChanges;private boolean localDone=true;
    static boolean organization(String trigger){return trigger.equals("ORGANIZE")||trigger.equals("FIRST_SETUP");}
    synchronized void localProgress(int processed,int total,int changes,boolean done){localProcessed=processed;localTotal=total;localChanges=changes;localDone=done;publish();}
    private State state=State.IDLE; private boolean userPaused,planned;
    private final LinkedHashMap<String,Item> items=new LinkedHashMap<>();
    private volatile Snapshot snapshot=new Snapshot(this);
    Snapshot snapshot(){return snapshot;}
    private void publish(){revision++;changed=System.currentTimeMillis();snapshot=new Snapshot(this);}
    synchronized boolean start(String trigger,String scope,boolean paused){
        if(!snapshot.terminal())return false;
        localProcessed=localTotal=localChanges=0;localDone=!organization(trigger);id=UUID.randomUUID().toString();this.trigger=trigger;this.scope=scope;userPaused=paused;planned=false;items.clear();downloads=0;
        current=source=detail="";indexVersion="";indexRecords=-1;started=System.currentTimeMillis();ended=0;resultRevision=0;state=State.PREPARING;publish();return true;
    }
    synchronized void plan(List<Item> plan){if(planned)return;for(Item i:plan)items.putIfAbsent(i.id,i);planned=true;publish();}
    synchronized boolean command(String session,long revision,Action expected){
        if(!id.equals(session)||revision>this.revision||snapshot.action!=expected)return false;
        if(expected==Action.PAUSE){userPaused=true;state=State.PAUSING;detail=UiStrings.msg("ui_ba12515d27c4");}
        else if(expected==Action.RESUME){userPaused=false;state=State.PREPARING;detail=UiStrings.msg("ui_1a793081db29");}
        else return false;
        publish();return true;
    }
    synchronized void stage(State next,String detail){if(state==State.INTERRUPTED&&next!=State.ERROR)return;if(state==State.PAUSING&&next!=State.PAUSED&&next!=State.ERROR)return;if(userPaused&&next!=State.PAUSED&&next!=State.ERROR)return;if(state==next&&this.detail.equals(detail))return;state=next;this.detail=detail;if(next==State.ERROR&&resultRevision==0){resultRevision=revision+1;ended=System.currentTimeMillis();}publish();}
    synchronized void current(Item item,String name,String source){if(items.get(item.id)!=item)return;item.result=Result.ACTIVE;current=name;this.source=source;publish();}
    synchronized void result(Item item,Result result,String reason,long retryAt){if(items.get(item.id)!=item)return;item.result=result;item.reason=reason;item.retryAt=retryAt;current=source="";publish();}
    synchronized void indexUpdated(String version,int records){indexVersion=version;indexRecords=records;publish();}
    synchronized void downloaded(){downloads++;publish();}
    synchronized void finish(){if(snapshot.terminal())return;if(userPaused||state==State.PAUSING)return;Snapshot s=new Snapshot(this);if(s.remaining+s.active+s.retrying>0)return;state=s.total==0?State.EMPTY:s.missing+s.identity+s.failed+s.removed>0?State.PARTIAL:State.COMPLETE;ended=System.currentTimeMillis();resultRevision=revision+1;detail=UiStrings.msg("ui_f7127024f49f");current=source="";publish();}
    synchronized List<Item> items(){return new ArrayList<>(items.values());}
    synchronized void restore(JSONObject o,List<Item> saved,boolean paused){
        localProcessed=o.optInt("localProcessed");localTotal=o.optInt("localTotal");localChanges=o.optInt("localChanges");localDone=o.optBoolean("localDone",!organization(o.optString("trigger")));indexVersion=o.optString("indexVersion");indexRecords=o.optInt("indexRecords",-1);id=o.optString("session");revision=o.optLong("revision");resultRevision=o.optLong("resultRevision");trigger=o.optString("trigger");scope=o.optString("scope");detail=o.optString("detail");planned=o.optBoolean("planned");started=o.optLong("started");ended=o.optLong("ended");downloads=o.optInt("downloads");userPaused=paused;
        try{state=State.valueOf(o.getString("state"));}catch(Exception e){state=State.ERROR;}
        for(Item i:saved){if(i.result==Result.ACTIVE)i.result=Result.PENDING;items.put(i.id,i);}
        if(!Snapshot.terminal(state)){state=paused?State.PAUSED:State.INTERRUPTED;detail=UiStrings.msg("ui_9709f5bfcae3");}
        if(resultRevision==0&&(Snapshot.terminal(state)||state==State.INTERRUPTED))resultRevision=revision;
        publish();
    }
}
