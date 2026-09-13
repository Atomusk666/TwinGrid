package com.rgds.ultimate.shell;
import org.json.*;

/** Missing data is a versioned field result, independent of queue completion. */
final class MetadataFields {
    static final int VERSION=3;
    static final int COVER_MAPPING_VERSION=5;
    static JSONObject field(String state,String source,int normalization,String reason)throws JSONException {
        long now=System.currentTimeMillis();return new JSONObject().put("state",state).put("sourceVersion",source).put("normalizationVersion",normalization).put("reason",reason).put("checkedAt",now)
            .put("retryAt",state.equals("READY")?0:now+(state.equals("SOURCE_UNAVAILABLE")?300000L:(state.equals("INDEX_KEY_ABSENT")?86400000L:30L*86400000)));
    }
    static boolean needsCover(JSONObject info,boolean hasCover){
        return needsCover(info,hasCover,"");
    }
    static String identity(JSONObject info){
        JSONObject d=info.optJSONObject("resolveDecision");if(d==null)d=info;
        return d.optString("workId")+"|"+d.optString("releaseId",info.optString("externalId"))+"|"+d.optString("catalogVersion")+"|"+d.optString("resolverVersion")+"|"+d.optString("inputFingerprint");
    }
    static boolean needsCover(JSONObject info,boolean hasCover,String currentIndex){return needsCover(info,hasCover,currentIndex,identity(info));}
    static boolean needsCover(JSONObject info,boolean hasCover,String currentIndex,String identity){
        if(hasCover)return false;
        JSONObject fields=info.optJSONObject("fields"),cover=fields==null?null:fields.optJSONObject("cover");
        return (!currentIndex.isEmpty()&&!currentIndex.equals(info.optString("coverIndexVersion")))||!info.optString("coverQueryIdentity").equals(identity)||info.optString("coverIndexVersion").isEmpty()||cover==null||cover.optInt("normalizationVersion")!=COVER_MAPPING_VERSION||!(cover.optString("state").equals("MISSING_SOURCE")||cover.optString("state").equals("INDEX_KEY_ABSENT")||cover.optString("state").equals("NEEDS_CHOICE"))||cover.optLong("retryAt")<=System.currentTimeMillis();
    }
    static boolean needs(JSONObject info,boolean hasCover){
        JSONObject fields=info.optJSONObject("fields");if(fields==null||info.optInt("fieldSchema")!=VERSION)return true;
        for(String name:new String[]{"identity","cover","genre","description"}){
            JSONObject f=fields.optJSONObject(name);if(f==null)return true;
            if(f.optString("state").equals("SOURCE_UNAVAILABLE")&&f.optLong("retryAt")>System.currentTimeMillis())continue;
            if(name.equals("genre")&&f.optInt("normalizationVersion")!=GenreTaxonomy.VERSION)return true;
            if(name.equals("description")&&f.optInt("normalizationVersion")!=GameDescription.VERSION)return true;
            if((name.equals("genre")||name.equals("description"))&&!f.optString("sourceVersion").equals(OpenVgdbIndex.VERSION))return true;
            if(name.equals("cover")&&f.optString("state").equals("READY")&&!hasCover)return true;
            if(!f.optString("state").equals("READY")&&f.optLong("retryAt")<=System.currentTimeMillis())return true;
        }return false;
    }
}
