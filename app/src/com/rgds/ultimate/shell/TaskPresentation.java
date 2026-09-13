package com.rgds.ultimate.shell;

import org.json.JSONObject;

/** Presentation of frozen results. Does not change accounting, restart tasks or infer old facts. */
final class TaskPresentation {
    enum Kind { INDEX, COVER, OFFLINE_MATCH, UNKNOWN }
    static Kind kind(String trigger){
        if("INDEX".equals(trigger))return Kind.INDEX;
        if("OFFLINE_MATCH".equals(trigger))return Kind.OFFLINE_MATCH;
        if("AUTO".equals(trigger)||CoverTask.organization(trigger)||"BATCH".equals(trigger)||"SINGLE".equals(trigger)||"RETRY".equals(trigger))return Kind.COVER;
        return Kind.UNKNOWN;
    }
    static String msg(String suffix){return UiStrings.msg("ui_d670000000"+suffix);}
    static String extra(int n){return UiStrings.msg(String.format(java.util.Locale.ROOT,"ui_1100%04x",n));}
    static String title(CoverTask.Snapshot s){
        if(kind(s.trigger)==Kind.INDEX)return s.state==CoverTask.State.COMPLETE?msg("01"):msg("02")+" · "+s.title();
        if(s.state==CoverTask.State.COMPLETE||s.state==CoverTask.State.PARTIAL){
            int n=s.trigger.equals("SINGLE")?0:s.trigger.equals("OFFLINE_MATCH")?2:CoverTask.organization(s.trigger)?3:1;
            return extra(n)+(s.state==CoverTask.State.PARTIAL?extra(4):"");
        }return s.title();
    }
    static String index(JSONObject s){
        String version=s.optString("indexVersion");int records=s.optInt("indexRecords",-1);
        return msg("03")+(version.isEmpty()?msg("04"):version.substring(0,Math.min(12,version.length())))+"\n"
                +msg("05")+(records<0?msg("04"):Integer.toString(records))+"\n"+(s.optInt("downloads")==0?msg("06"):msg("0f")+s.optInt("downloads"))+"\n"
                +msg("07")+s.optInt("processed")+" / "+s.optInt("total");
    }
    static String summary(CoverTask.Snapshot s){
        if(kind(s.trigger)==Kind.INDEX)return index(s.json());
        if(kind(s.trigger)==Kind.UNKNOWN)return msg("08")+"\n"+msg("07")+s.processed+" / "+s.total;
        return msg("09")+s.added+" · "+msg("0a")+s.reused+"\n"+msg("0b")+s.missing+" · "+msg("0c")+s.identity+"\n"
                +msg("0d")+s.failed+" · "+msg("0e")+s.removed+"\n"+msg("0f")+s.downloads;
    }
    static String explanation(CoverTask.Snapshot s){return kind(s.trigger)==Kind.INDEX?msg("10"):kind(s.trigger)==Kind.UNKNOWN?msg("08"):UiStrings.msg("ui_d50000000004");}
}
