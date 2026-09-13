package com.rgds.ultimate.shell;

/** Stable action identities; position is presentation only. */
final class SettingsModel {
    enum Id { COVER_DATA,DIRECTORIES,DISPLAY,STORAGE,ABOUT,TASKS,FILL,MATCH,COVERAGE,REVIEW,AUTO,METERED,NOTIFY,ROM_DIR,SAVE_DIR,SCAN,SORT,BROWSE,EXPORT,USAGE,CLEAR,VERSION,SOURCES,DIAGNOSTICS,PROBE,INDEX,AUDIT,LOGS,LANGUAGE,CONTENT_LANGUAGE,HOME_INTEGRATION,HELP,DIRECTORY_HELP,BATTERY_DISPLAY }
    enum Kind { GROUP,ACTION,TOGGLE,INFO }
    static final class Entry {
        final Id id;final Kind kind;final String title,description;
        Entry(Id id,Kind kind,String title,String description){this.id=id;this.kind=kind;this.title=title;this.description=description;}
        String description(android.content.Context c){if(id!=Id.HOME_INTEGRATION)return description;org.json.JSONObject home=HomeIntegration.snapshot(c);return home.optBoolean("isDefaultHome")?UxStrings.s(109):home.optBoolean("roleHeld")?UxStrings.s(111):UxStrings.s(110);}
        String value(ShellStateRepository.Snapshot s,MetadataManager m,android.content.Context c){switch(id){
            case BATTERY_DISPLAY:return BatterySettings.label(c);
            case AUTO:return m.enabled()?UiStrings.msg("ui_8da97ddda990"):UiStrings.msg("ui_3fd47edce45b");case METERED:return m.meteredAllowed()?UiStrings.msg("ui_ce7ef28b670a"):UiStrings.msg("ui_4a044484f863");case NOTIFY:return TaskNotifications.status(c);case SORT:return s.sortMode==0?UiStrings.msg("ui_d44e9b3d3b31"):UiStrings.msg("ui_257bbcc44c83");case SCAN:return RomLibrary.get(c).isScanning()?UiStrings.msg("ui_323d825eab5d"):UiStrings.msg("ui_743497b67cd2");case ROM_DIR:return DirectoryHealth.label(s.romAccess);case SAVE_DIR:return DirectoryHealth.label(s.saveAccess);case MATCH:return UiStrings.msg("ui_db8db0530432");case LOGS:return PerfTrace.isEnabled()?UiStrings.msg("ui_8da97ddda990"):UiStrings.msg("ui_3fd47edce45b");default:return kind==Kind.GROUP?"":kind==Kind.INFO?UiStrings.msg("ui_db8db0530432"):UiStrings.msg("ui_f88182fcd575");
        }}
        boolean enabled(MetadataManager m){return true;}
        String help(ShellStateRepository.Snapshot s,MetadataManager m,android.content.Context c){
            String detail=description;
            switch(id){case TASKS:case FILL:detail=UiStrings.msg("ui_5d4a2e74561e");break;
                case MATCH:detail=m.catalog.version()+"\n"+m.chineseProgress()+UiStrings.msg("ui_5bfd872d077b");break;
                case COVERAGE:detail=m.coverage();break;
                case AUTO:detail=UiStrings.msg("ui_791618e0bf0a");break;
                case METERED:detail=UiStrings.msg("ui_05c2afbc1ba3");break;
                case NOTIFY:detail=UiStrings.msg("ui_11f1f4bcbdc9");break;
                case DIRECTORY_HELP:detail=QuickStart.s(12);break;
                case ROM_DIR:detail=QuickStart.s(12)+"\n\n"+QuickStart.s(14)+DirectoryAccess.label(c,s.romTreeUri,false)+"\n"+UiStrings.msg("ui_3c492cc0378e")+DirectoryHealth.label(s.romAccess);break;
                case SAVE_DIR:detail=QuickStart.s(13)+"\n\n"+QuickStart.s(14)+DirectoryAccess.label(c,s.backupTreeUri,true)+"\n"+UiStrings.msg("ui_f7eb11036c9e")+DirectoryHealth.label(s.saveAccess);break;
                case SCAN:detail=s.scanState+UiStrings.msg("ui_9df45f19ede6");break;
                case SORT:detail=UiStrings.msg("ui_6601885a6e27");break;
                case CLEAR:case USAGE:detail=m.cacheUsage()+UiStrings.msg("ui_ddb086a646ac");break;
                case INDEX:detail=UiStrings.msg("ui_1158671ac122");break;
                case VERSION:detail=ClassicScreen.version(c)+UiStrings.msg("ui_29db79aadf48")+m.catalog.version();break;
            }
            return title+"\n\n"+detail;
        }
    }
    private static Entry e(Id id,Kind kind,String title,String description){return new Entry(id,kind,title,description);}
    private static final Entry[] ROOT={e(Id.COVER_DATA,Kind.GROUP,UiStrings.msg("ui_308be5e9bc37"),UiStrings.msg("ui_e99481375acf")),e(Id.DIRECTORIES,Kind.GROUP,UiStrings.msg("ui_a03119e61efc"),UiStrings.msg("ui_e2c975763d9d")),e(Id.DISPLAY,Kind.GROUP,FirstRun.s(9),UiStrings.msg("ui_1319dcff652f")),e(Id.STORAGE,Kind.GROUP,UiStrings.msg("ui_2100fbd17556"),UiStrings.msg("ui_6114c7a84492")),e(Id.ABOUT,Kind.GROUP,UiStrings.msg("ui_0a114f362a5f"),UiStrings.msg("ui_cd178652fde7"))};
    private static final Entry[][] GROUPS={
        {e(Id.TASKS,Kind.ACTION,UiStrings.msg("ui_00b514c36a6c"),UiStrings.msg("ui_9ebc9b65d3fd")),e(Id.MATCH,Kind.ACTION,FirstRun.s(6),FirstRun.s(14)),e(Id.COVERAGE,Kind.ACTION,UiStrings.msg("ui_bbfca5de3962"),UiStrings.msg("ui_eaca0caa328b")),e(Id.REVIEW,Kind.ACTION,UiStrings.msg("ui_2285ca6e0448"),UiStrings.msg("ui_c6c2933ea3ed")),e(Id.AUTO,Kind.TOGGLE,UiStrings.msg("ui_b38296844b70"),UiStrings.msg("ui_2185225aef79")),e(Id.METERED,Kind.TOGGLE,UiStrings.msg("ui_1828b630a3f0"),UiStrings.msg("ui_f7904cccd849")),e(Id.NOTIFY,Kind.TOGGLE,UiStrings.msg("ui_3be8decba2c5"),UiStrings.msg("ui_a16a3489b2cb"))},
        {e(Id.DIRECTORY_HELP,Kind.ACTION,QuickStart.s(11),QuickStart.s(12)),e(Id.ROM_DIR,Kind.ACTION,UiStrings.msg("ui_53f5ff328e34"),UiStrings.msg("ui_396a05bdeb74")),e(Id.SAVE_DIR,Kind.ACTION,UiStrings.msg("ui_6ac797305105"),UiStrings.msg("ui_ad2f33dd71de")),e(Id.SCAN,Kind.ACTION,UiStrings.msg("ui_e32545d75592"),UiStrings.msg("ui_7e09b942dad8"))},
        {e(Id.HOME_INTEGRATION,Kind.ACTION,UxStrings.s(40),UxStrings.s(41)),e(Id.LANGUAGE,Kind.ACTION,UiStrings.msg("ui_127527c89c51"),UiStrings.msg("ui_01ea8c5a2c99")),e(Id.CONTENT_LANGUAGE,Kind.ACTION,UiStrings.msg("ui_98a28c9b4bbe"),UiStrings.msg("ui_3fa405d23dda")),e(Id.SORT,Kind.TOGGLE,UiStrings.msg("ui_a29375e863f0"),UiStrings.msg("ui_d0c49b7d7b36")),e(Id.BATTERY_DISPLAY,Kind.ACTION,UiStrings.msg("ui_ba77000001"),UiStrings.msg("ui_ba77000002")),e(Id.BROWSE,Kind.ACTION,UiStrings.msg("ui_596a53ed74d5"),UiStrings.msg("ui_4e9dc0b51bd1"))},
        {e(Id.EXPORT,Kind.ACTION,UiStrings.msg("ui_ced3e1c3dc55"),UiStrings.msg("ui_b3692b013a24")),e(Id.USAGE,Kind.INFO,UiStrings.msg("ui_37ac171c052a"),UiStrings.msg("ui_56738959f3bf")),e(Id.CLEAR,Kind.ACTION,UiStrings.msg("ui_c194f4e29f23"),UiStrings.msg("ui_d3a48e753c9e"))},
        {e(Id.HELP,Kind.ACTION,QuickStart.s(0),QuickStart.s(1)),e(Id.VERSION,Kind.INFO,UiStrings.msg("ui_2da3906a24ee"),UiStrings.msg("ui_974deaa7005f")),e(Id.SOURCES,Kind.INFO,UiStrings.msg("ui_70174055607c"),UiStrings.msg("ui_2bca4380acb8")),e(Id.DIAGNOSTICS,Kind.ACTION,UiStrings.msg("ui_4097bbc80765"),UiStrings.msg("ui_0d414dcf6206")),e(Id.LOGS,Kind.TOGGLE,UiStrings.msg("ui_bb1d19b85634"),UiStrings.msg("ui_6e9546160e58")),e(Id.PROBE,Kind.ACTION,UiStrings.msg("ui_4605735ff535"),UiStrings.msg("ui_9bedeeb99ebf")),e(Id.INDEX,Kind.ACTION,UiStrings.msg("ui_0ec1aca58598"),UiStrings.msg("ui_0af420cb2691")),e(Id.AUDIT,Kind.ACTION,UiStrings.msg("ui_218b2741bf4c"),UiStrings.msg("ui_dbb7393b6619"))}
    };
    static String homePath(){int g=group(Id.HOME_INTEGRATION);return UiStrings.msg("ui_df3d58c7d84b")+" → "+ROOT[g].title+" → "+entry(g,index(g,Id.HOME_INTEGRATION)).title;}
    static Entry[] entries(int group){return group<0||group>=GROUPS.length?ROOT:GROUPS[group];}
    static Entry entry(int group,int index){Entry[] list=entries(group);return list[Math.max(0,Math.min(list.length-1,index))];}
    static int group(Id id){for(int g=0;g<5;g++)for(Entry e:GROUPS[g])if(e.id==id)return g;return -1;}
    static int index(int group,Id id){Entry[] entries=entries(group);for(int i=0;i<entries.length;i++)if(entries[i].id==id)return i;return 0;}
    static int[] restore(String stable,int oldGroup,int oldIndex){
        Id id=null;try{id="RG_KEY".equals(stable)?Id.HELP:Id.valueOf(stable);}catch(Exception ignored){}
        if(id==null){Id[][] old={{Id.ROM_DIR,Id.SAVE_DIR,Id.SCAN,Id.DIRECTORIES},{Id.AUTO,Id.METERED,Id.FILL,Id.TASKS,Id.TASKS,Id.INDEX,Id.CLEAR,Id.PROBE,Id.AUDIT},{Id.SORT,Id.BROWSE,Id.BROWSE,Id.DISPLAY},{Id.EXPORT,Id.ABOUT,Id.DIAGNOSTICS,Id.ABOUT},{Id.DIRECTORIES,Id.COVER_DATA,Id.DISPLAY,Id.STORAGE}};
            Id[] list=oldGroup<0?new Id[]{Id.MATCH,Id.TASKS,Id.COVERAGE,Id.REVIEW,Id.COVER_DATA}:old[Math.min(4,oldGroup)];id=list[Math.max(0,Math.min(list.length-1,oldIndex))];}
        if(id==Id.FILL)id=Id.TASKS;
        int group=group(id);return new int[]{group,index(group,id)};
    }
}
