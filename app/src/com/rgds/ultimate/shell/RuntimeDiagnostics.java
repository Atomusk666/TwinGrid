package com.rgds.ultimate.shell;

import android.app.Activity;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.PrintWriter;

/** Read-only Activity dump, protected by Android's system DUMP permission. No command receiver. */
final class RuntimeDiagnostics {
    static void dump(Activity activity, PrintWriter writer, String[] args) {
        try {
            if(args!=null)for(String arg:args){
                if("--rgds-read-audit-start".equals(arg))RomReadAudit.get(activity).start();
                if("--rgds-read-audit-cancel".equals(arg))RomReadAudit.get(activity).cancel();
                if("--rgds-read-audit-result".equals(arg))writer.println("RGDS_READ_AUDIT="+RomReadAudit.get(activity).snapshot(true));
                if("--rgds-directory-recheck".equals(arg))DirectoryHealth.get(activity).recheck("MANUAL_DIAGNOSTIC");
                if("--rgds-scan-uncached".equals(arg))RomLibrary.get(activity).scanUncached();
            }
            ShellStateRepository.Snapshot s=ShellStateRepository.get(activity).snapshot();
            OfflineCatalog catalog=MetadataManager.get(activity).catalog;
            JSONObject o=new JSONObject();
            o.put("setupJourney",SetupJourney.snapshot(activity));
            o.put("directoryRequest",DirectoryRequest.get(activity).snapshot());
            o.put("directoryHealth",DirectoryHealth.get(activity).snapshot()).put("romAccess",s.romAccess.name()).put("saveAccess",s.saveAccess.name());
            o.put("firstRun",FirstRun.snapshot(activity)).put("launchDiagnostic",LaunchDiagnostic.read(activity)).put("journey",JourneyGuide.snapshot(activity)).put("scan",RomLibrary.get(activity).progress().json());
            o.put("uiLanguage",LocaleSettings.ui()).put("contentLanguage",LocaleSettings.content()).put("localeRevision",LocaleSettings.revision());
            if(activity instanceof BottomHomeActivity)o.put("scanUi",((BottomHomeActivity)activity).scanDiagnostics());
            o.put("scanReadEvidence",RomLibrary.get(activity).readEvidence());
            o.put("readAudit",RomReadAudit.get(activity).snapshot(false));
            if(GapUi.visibleResult!=null)o.put("resultGroups",GapUi.visibleResult.json());
            android.content.pm.PackageInfo info=activity.getPackageManager().getPackageInfo(activity.getPackageName(),0);
            o.put("topSurfacePresentation",activity instanceof TopDisplayActivity).put("observedWindowType",ShellWindowPolicy.window(activity).getAttributes().type);
            o.put("versionName",info.versionName).put("versionCode",android.os.Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode)
                    .put("uid",android.os.Process.myUid()).put("uptime",SystemClock.uptimeMillis())
                    .put("activity",activity.getClass().getSimpleName()).put("display",activity.getWindowManager().getDefaultDisplay().getDisplayId())
                    .put("windowFocus",ShellWindowPolicy.window(activity).getDecorView().hasWindowFocus()).put("focusDisplay",s.currentFocusDisplay)
                    .put("phase",ShellCoordinator.get().phaseName()).put("modal",ShellDialogBuilder.hasOpenDialog())
                    .put("page",s.page.name()).put("category",s.category.name()).put("homeIndex",s.homeIndex)
                    .put("settingsGroup",s.settingsGroup).put("settingsIndex",s.settingsIndex)
                    .put("filterIndex",s.filterIndex).put("detailTab",s.detailTab).put("detailOffset",s.detailOffset)
                    .put("activeEditorGameId",ShellDialogBuilder.editorObject(false)).put("activeEditorField",ShellDialogBuilder.editorObject(true))
                    .put("revision",s.revision).put("listRevision",s.listRevision).put("queryDescriptor",new JSONObject(s.queryDescriptor))
                    .put("typesOverview",s.typesOverview).put("genreIndex",s.genreIndex).put("genre",s.genreFilter)
                    .put("folder",s.folderId).put("folderPath",s.folderPath).put("selectedFolder",s.selectedFolder!=null)
                    .put("selectedFolderId",s.selectedFolder==null?"":s.selectedFolder.id)
                    .put("selectedIndex",s.selectedIndex).put("pageSize",s.pageSize).put("rows",s.items.size())
                    .put("selectedGameId",s.selectedGame==null?"":s.selectedGame.gameId)
                    .put("selectedUri",s.selectedGame==null?"":s.selectedGame.uriString())
                    .put("searchActive",s.searchActive).put("searchQuery",s.searchQuery).put("searchBusy",s.searchBusy)
                    .put("searchEditing",s.searchEditing).put("searchComposing",s.searchComposing)
                    .put("stateReady",s.stateReady).put("libraryReady",s.libraryReady).put("storageAvailable",s.storageAvailable)
                    .put("canActOnGame",s.canActOnGame());
            o.put("catalogPrepared",catalog.prepared()).put("catalogLibraryReady",catalog.ready())
                    .put("catalogBoundGames",catalog.boundGames()).put("catalogReadableGames",catalog.readableGames())
                    .put("catalogFingerprint",catalog.fingerprint()).put("catalogBindingGeneration",catalog.publishedBindingGeneration());
            if(android.os.Build.VERSION.SDK_INT>=30){
                o.put("windowPolicy",ShellWindowPolicy.diagnostics(ShellWindowPolicy.window(activity)));
                android.view.WindowInsets insets=ShellWindowPolicy.window(activity).getDecorView().getRootWindowInsets();
                if(insets!=null)o.put("systemBars",new JSONObject().put("statusVisible",insets.isVisible(android.view.WindowInsets.Type.statusBars()))
                        .put("navigationVisible",insets.isVisible(android.view.WindowInsets.Type.navigationBars()))
                        .put("statusTop",insets.getInsets(android.view.WindowInsets.Type.statusBars()).top));
            }
            if(activity instanceof BottomHomeActivity)o.put("editor",((BottomHomeActivity)activity).editorDiagnostics());
            o.put("repair",MetadataManager.get(activity).repairDiagnostics()).put("integration",HomeIntegration.snapshot(activity)).put("coverTask",MetadataManager.get(activity).task().json()).put("offlineMatch",catalog.match().json()).put("taskIndex",s.taskIndex).put("settingId",SettingsModel.entry(s.settingsGroup,s.settingsIndex).id.name()).put("notifications",TaskNotifications.status(activity));
            writer.println("RGDS_CONTEXT="+o.toString());
            if(args!=null)for(String arg:args)if("--rgds-geometry".equals(arg)){writer.println("RGDS_GEOMETRY="+VisualDiagnostics.capture(activity).toString());break;}
            if(args!=null)for(String arg:args)if("--rgds-labels".equals(arg)){writer.println("RGDS_LABELS="+LabelAudit.capture(activity).toString());break;}
            if(args!=null)for(String arg:args)if("--rgds-results".equals(arg)){
                JSONArray ids=new JSONArray();for(GameEntry g:s.games)ids.put(g.gameId);
                writer.println("RGDS_RESULTS="+ids.toString());break;
            }
        }catch(Exception e){writer.println("RGDS_CONTEXT_ERROR="+e.getClass().getSimpleName());}
    }
}
