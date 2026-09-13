package com.rgds.ultimate.shell;
import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.util.*;
/** Explicit diagnostic export from real in-memory metadata, using the production scope predicate. */
final class LibraryAudit {
    static void export(Context context,MetadataManager m)throws Exception{
        if(!m.searchReady()||!m.gapsReady())throw new IOException(UiStrings.msg("ui_0c165cf04230"));
        long bindingGeneration=m.catalog.publishedBindingGeneration();
        long effectiveRevision=m.effectiveRevision();
        if(bindingGeneration<0)throw new IOException(UiStrings.msg("ui_0c165cf04230"));
        ShellStateRepository repo=ShellStateRepository.get(context);ShellStateRepository.Snapshot state=repo.snapshot();
        JSONObject query=new JSONObject(state.queryDescriptor);long libraryVersion=query.getLong("libraryVersion");
        List<GameEntry> games=repo.allGamesCopy();DirectoryIndex dirs=RomLibrary.get(context).directories();if(dirs==null)throw new IOException("Directory index not ready");
        JSONObject json=ProductIdentity.diagnostics(context).put("capturedAt",System.currentTimeMillis()).put("uid",android.os.Process.myUid()).put("evidence","running APK metadata snapshot + production BrowseQuery; independent reference evaluation on host")
            .put("uiLanguage",LocaleSettings.ui()).put("contentLanguage",LocaleSettings.content()).put("localeRevision",LocaleSettings.revision())
            .put("taxonomyVersion",GenreTaxonomy.VERSION).put("query",query).put("directory",dirs.json()).put("catalogBindingGeneration",bindingGeneration).put("effectiveRevision",effectiveRevision).put("effectiveCoverage",m.coverage());
        Set<String> favorites=new HashSet<>(state.favoriteIds),recent=repo.recentIdsForAudit();Map<String,List<String>> types=new HashMap<>();JSONArray rows=new JSONArray();
        for(GameEntry g:games){MetadataManager.Info i=m.fullForAudit(g.gameId);List<String> effective=m.genres(g.gameId);types.put(g.gameId,effective);
            OfflineCatalog.Head catalog=m.catalog.head(g.gameId);
            EffectiveMetadata e=m.effective(g.gameId);
            GenreTaxonomy.Result original=GenreTaxonomy.parse(i.raw.optString("genreProvider"),i.genre);
            JSONObject zh=i.raw.optJSONObject("chinese");GenreTaxonomy.Result chinese=GenreTaxonomy.parse("Bangumi",zh==null?"":zh.optString("rawGenre"));
            rows.put(new JSONObject().put("gameId",g.gameId).put("fileName",g.fileName).put("gameCode",g.gameCode).put("title",GameDisplay.name(g,m)).put("parent",dirs.gameParents.get(g.gameId))
                .put("genres",new JSONArray(effective)).put("label",m.genreLabel(g.gameId)).put("rawState",original.state).put("unmapped",new JSONArray(original.unmapped))
                .put("chineseUnmapped",new JSONArray(chinese.unmapped)).put("chineseGenreConflict",!GenreTaxonomy.compatible(original.categories,chinese.categories)).put("userOverride",m.overrideForAudit(g.gameId))
                .put("metadata",i.raw).put("workKey",MetadataManager.workKey(g,i.raw))
                .put("effectiveTextState",e.text.name()).put("effectiveTextSource",e.textSource).put("effectiveKind",e.kind).put("effectiveReason",e.reason()).put("effectiveLinked",e.linked).put("effectiveCandidate",e.candidate).put("effectiveCovered",e.cover).put("effectiveReviewedContent",e.reviewedContent()).put("effectiveDeferred",e.deferred)
                .put("catalogWorkId",catalog==null?"":catalog.workId).put("catalogKind",catalog==null?"MISSING":catalog.kind).put("catalogHasSummary",catalog!=null&&catalog.hasSummary).put("catalogHasEnglish",catalog!=null&&catalog.hasEnglish).put("catalogEnglishKind",catalog==null?"MISSING":catalog.englishKind).put("catalogEnglishName",catalog==null?"":catalog.englishName).put("catalogEnglishNameKind",catalog==null?"":catalog.englishNameKind).put("catalogFingerprint",m.catalog.fingerprint()));
        }
        json.put("games",rows).put("favorites",new JSONArray(favorites)).put("recent",new JSONArray(recent));
        JSONArray gapGroups=new JSONArray();for(int category=0;category<8;category++){JSONArray ids=new JSONArray();for(GameEntry g:m.gaps(category))ids.put(g.gameId);gapGroups.put(new JSONObject().put("category",category).put("ids",ids));}json.put("gapGroups",gapGroups);
        JSONArray queries=new JSONArray();
        for(String genre:MetadataIndex.GENRES){
            for(String category:new String[]{"NDS","TYPES","FAVORITES","RECENT"})queries.put(evaluate(new BrowseQuery(category,"",false,genre,"",0,0,0,0),games,types,favorites,recent,dirs));
            for(String folder:dirs.folders.keySet())for(boolean subtree:new boolean[]{false,true})queries.put(evaluate(new BrowseQuery("FOLDERS",folder,subtree,genre,"",0,0,0,0),games,types,favorites,recent,dirs));
        }
        json.put("queries",queries).put("finishedAt",System.currentTimeMillis());
        Set<String> capturedIds=new HashSet<>(),currentIds=new HashSet<>();for(GameEntry g:games)capturedIds.add(g.gameId);
        for(GameEntry g:repo.allGamesCopy())currentIds.add(g.gameId);
        long currentLibraryVersion=new JSONObject(repo.snapshot().queryDescriptor).getLong("libraryVersion");
        if(!m.searchReady()||effectiveRevision!=m.effectiveRevision()||dirs!=RomLibrary.get(context).directories()||!auditSnapshotUnchanged(bindingGeneration,m.catalog.publishedBindingGeneration(),libraryVersion,currentLibraryVersion,capturedIds,currentIds))throw new IOException(UiStrings.msg("ui_8546c6a8eec3"));
        AtomicFile file=new AtomicFile(new File(context.getFilesDir(),"v06_audit_current.json"));FileOutputStream out=null;
        try{out=file.startWrite();out.write(json.toString().getBytes("UTF-8"));file.finishWrite(out);}catch(Exception e){if(out!=null)file.failWrite(out);throw e;}
    }
    static boolean auditSnapshotUnchanged(long beforeBinding,long afterBinding,long beforeLibrary,long afterLibrary,Set<String> beforeIds,Set<String> afterIds){
        return beforeBinding>=0&&beforeBinding==afterBinding&&beforeLibrary==afterLibrary&&beforeIds.equals(afterIds);
    }
    private static JSONObject evaluate(BrowseQuery q,List<GameEntry> games,Map<String,List<String>> types,Set<String> favorites,Set<String> recent,DirectoryIndex dirs)throws Exception{
        List<GameEntry> result=new ArrayList<>();for(GameEntry g:games)if(q.exclusion(g.gameId,types.get(g.gameId),favorites,recent,dirs).isEmpty())result.add(g);
        result.sort(Comparator.comparing(GameEntry::bestTitle,String.CASE_INSENSITIVE_ORDER));JSONArray ids=new JSONArray(),pages=new JSONArray();JSONArray page=new JSONArray();
        for(GameEntry g:result){ids.put(g.gameId);page.put(g.gameId);if(page.length()==6){pages.put(page);page=new JSONArray();}}if(page.length()>0)pages.put(page);
        return new JSONObject().put("descriptor",q.json()).put("count",result.size()).put("ids",ids).put("gameChunksOf6",pages).put("foregroundPageTrace",false);
    }
}
