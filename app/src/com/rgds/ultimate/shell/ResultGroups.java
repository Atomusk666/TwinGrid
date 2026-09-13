package com.rgds.ultimate.shell;

import org.json.*;
import java.util.*;

/** A receipt's members are frozen once. Current metadata never reclassifies its history. */
final class ResultGroups {
    enum Id { ALL, CANDIDATE, IDENTITY, NO_IMAGE, RETRY, REMOVED, ADDED, REUSED, INTERRUPTED }
    static final class Snapshot {
        final String key;final long revision;final Map<Id,List<JSONObject>> members;
        final boolean legacyIdentity;
        Snapshot(String key,long revision,JSONArray input) throws JSONException {
            this.key=key;this.revision=revision;
            Map<Id,List<JSONObject>> groups=new EnumMap<>(Id.class);for(Id id:Id.values())groups.put(id,new ArrayList<>());
            Set<String> seen=new HashSet<>();boolean legacy=false;
            for(int n=0;n<input.length();n++){
                JSONObject row=new JSONObject(input.getJSONObject(n).toString());
                if(row.optString("id").isEmpty()||!seen.add(row.getString("id")))throw new JSONException("Receipt contains missing or duplicate game IDs");
                groups.get(Id.ALL).add(row);Id category;
                switch(row.optString("result")){
                    case "IDENTITY":legacy|=!row.has("candidateAtResult");category=row.optBoolean("candidateAtResult")?Id.CANDIDATE:Id.IDENTITY;break;
                    case "MISSING":category=Id.NO_IMAGE;break;
                    case "FAILED":case "RETRY":category=Id.RETRY;break;
                    case "REMOVED":category=Id.REMOVED;break;
                    case "ADDED":category=Id.ADDED;break;
                    case "REUSED":category=Id.REUSED;break;
                    default:category=Id.INTERRUPTED;
                }
                groups.get(category).add(row);
            }
            for(Id id:Id.values())groups.put(id,Collections.unmodifiableList(groups.get(id)));
            members=Collections.unmodifiableMap(groups);legacyIdentity=legacy;
        }
        int count(Id id){return members.get(id).size();}
        JSONObject json() throws JSONException {JSONObject out=new JSONObject().put("key",key).put("revision",revision).put("legacyIdentity",legacyIdentity);for(Id id:Id.values()){JSONArray ids=new JSONArray();for(JSONObject row:members.get(id))ids.put(row.getString("id"));out.put(id.name(),new JSONObject().put("count",ids.length()).put("ids",ids));}return out;}
    }
    static String label(Id id){switch(id){case ALL:return UiStrings.msg("ui_80a851b1df45");case CANDIDATE:return UiStrings.msg("ui_1d66da6d252d");case IDENTITY:return UiStrings.msg("ui_e35442d3a457");case NO_IMAGE:return UiStrings.msg("ui_5e7990d59972");case RETRY:return UiStrings.msg("ui_3d02e5d60310");case REMOVED:return UiStrings.msg("ui_364f5245d2d6");case ADDED:return UiStrings.msg("ui_9ef3543e96c1");case REUSED:return UiStrings.msg("ui_ee17c664a740");default:return UiStrings.msg("ui_c69291da629b");}}
}
