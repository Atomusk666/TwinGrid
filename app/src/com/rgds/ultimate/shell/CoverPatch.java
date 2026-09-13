package com.rgds.ultimate.shell;
import org.json.*;

/** Exact field ownership: a cover producer cannot replace prose, identity, overrides or unknown fields. */
final class CoverPatch {
    private static final String[] OWNED={"localCover","localBasis","localUri","localError","remoteCover","coverProvider","coverUrl","coverRegion","coverStatus","substituteRegion","coverFetchedAt","coverProviderKey","coverIndexVersion","coverQueriedRelease","coverQueryIdentity","coverAssociation"};
    static JSONObject apply(JSONObject old,JSONObject cover)throws JSONException{
        JSONObject out=new JSONObject(old.toString());
        for(String key:OWNED)if(cover.has(key))out.put(key,cover.get(key));
        JSONObject fields=old.optJSONObject("fields");fields=fields==null?new JSONObject():new JSONObject(fields.toString());
        JSONObject changed=cover.optJSONObject("fields");if(changed!=null&&changed.has("cover"))fields.put("cover",changed.get("cover"));
        if(fields.length()>0)out.put("fields",fields);return out;
    }
}
