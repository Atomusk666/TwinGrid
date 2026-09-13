package com.rgds.ultimate.shell;

import android.app.*;
import android.content.Context;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Secondary actions; primary dual-screen browsing remains the existing DS Classic Views. */
final class UiDialogs {
    static String[] settingNames(ShellStateRepository.Snapshot s,MetadataManager m,boolean scanning){SettingsModel.Entry[] entries=SettingsModel.entries(s.settingsGroup);String[] names=new String[entries.length];for(int i=0;i<names.length;i++)names[i]=entries[i].title;return names;}
    static String settingHelp(ShellStateRepository.Snapshot s,MetadataManager m,boolean scanning){return SettingsModel.entry(s.settingsGroup,s.settingsIndex).help(s,m,m.contextForUi());}
    static String region(String s){return s.replace("Japan",UiStrings.msg("ui_cf2abf0c5be3")).replace("USA",UiStrings.msg("ui_58857e5318f3")).replace("Europe",UiStrings.msg("ui_8ef85828ff9c")).replace("Australia",UiStrings.msg("ui_0b1001a02c97")).replace("China",UiStrings.msg("ui_f0e9521611bb")).replace("Korea",UiStrings.msg("ui_963cd7300454"));}
    static String descriptionCaption(MetadataManager m,String id){EffectiveMetadata e=m.effective(id);if(e.text==EffectiveMetadata.TextState.HIDDEN)return UiStrings.msg("ui_d579f1912fe1");if(e.text==EffectiveMetadata.TextState.MISSING)return UiStrings.msg("ui_4f359773470f");String actual=m.descriptionLanguage(id);return (e.textSource.equals("USER")?UiStrings.msg("ui_fc0f28fd9a7f"):e.textSource.equals("CONFIRMED")?UiStrings.msg("ui_94289f0cf51f"):m.descriptionQuality(id))+(!actual.equals(LocaleSettings.content())?" · "+(actual.equals("zh")?UiStrings.msg("ui_9937e365aaf3"):"English")+UiStrings.msg("ui_77dbb04ae4f1"):"");}
    static void searchScope(Activity a){ShellStateRepository r=ShellStateRepository.get(a);new ShellDialogBuilder(a).setTitle(UiStrings.msg("ui_2fc80cadc873")).setSingleChoiceItems(new String[]{UiStrings.msg("ui_cb7b17064938"),UiStrings.msg("ui_f5fc4e86df84"),UiStrings.msg("ui_149e1c3e0c20")},r.snapshot().searchScopeMode,(d,w)->{r.searchScope(w);d.dismiss();}).setNegativeButton(UiStrings.msg("ui_572cf45ba436"),null).show();}
    static void searchOptions(Activity a){ShellStateRepository r=ShellStateRepository.get(a);new ShellDialogBuilder(a).setTitle(UiStrings.msg("ui_36489c77e39b")).setItems(new String[]{UiStrings.msg("ui_eba29ff35db1"),UiStrings.msg("ui_0c559649e30a")},(d,w)->{if(w==0)searchScope(a);else r.cycleSortMode();}).setNegativeButton(UiStrings.msg("ui_572cf45ba436"),null).show();}
    static void genres(Activity a){
        ShellStateRepository r=ShellStateRepository.get(a);String[] choices=MetadataIndex.GENRES;
        int selected=Arrays.asList(choices).indexOf(r.snapshot().genreFilter);
        new ShellDialogBuilder(a).setTitle(UiStrings.msg("ui_3507e11a268e")).setSingleChoiceItems(Arrays.stream(choices).map(GenreTaxonomy::label).toArray(String[]::new),selected,(d,w)->{r.setGenre(choices[w]);d.dismiss();}).setNegativeButton(UiStrings.msg("ui_572cf45ba436"),null).show();
    }
    static void gameGenres(Activity a,GameEntry game){if(game==null)return;MetadataManager m=MetadataManager.get(a);String[] types=m.genres(game.gameId).toArray(new String[0]);new ShellDialogBuilder(a).setTitle(UiStrings.msg("ui_44400dfc7c58")).setItems(Arrays.stream(types).map(GenreTaxonomy::label).toArray(String[]::new),(d,w)->ShellStateRepository.get(a).openGenre(types[w],true)).setNegativeButton(UiStrings.msg("ui_572cf45ba436"),null).show();}
    static void version(Activity a,MetadataManager m){
        String note=UiStrings.display(UiStrings.msg("ui_fa130001"));
        text(a,UiStrings.msg("ui_2da3906a24ee"),ClassicScreen.version(a)+"\n\n"+note+"\n\n"+m.chineseSummary(),note);
    }
    static void text(Activity a,String title,String body){text(a,title,body,"");}
    private static void text(Activity a,String title,String body,String quietNote){
        ScrollView scroll=new ScrollView(a);TextView t=ClassicUi.text(a,"",19);t.setPadding(20,16,20,20);t.setLineSpacing(5,1);ClassicUi.readingText(t,body);t.setTextIsSelectable(true);scroll.addView(t);
        if(!quietNote.isEmpty()){android.text.SpannableString spans=new android.text.SpannableString(t.getText());int start=spans.toString().indexOf(quietNote);if(start>=0)spans.setSpan(new android.text.style.ForegroundColorSpan(ClassicUi.MUTED),start,start+quietNote.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);t.setText("");t.setText(spans);}
        android.text.util.Linkify.addLinks(t,android.text.util.Linkify.WEB_URLS);
        AlertDialog.Builder dialog=new ShellDialogBuilder(a).setTitle(title).setView(scroll).setPositiveButton(UiStrings.msg("ui_572cf45ba436"),null);
        if(title.equals(UiStrings.msg("ui_c50f46fcd38f")))dialog.setNeutralButton(UiStrings.msg("ui_4c828fc6ac13"),(d,w)->runtimeLicenses(a));dialog.show();
    }
    private static void runtimeLicenses(Activity a){
        int id=a.getResources().getIdentifier("runtime_licenses","raw",a.getPackageName());
        if(id==0){text(a,UiStrings.msg("ui_4c828fc6ac13"),UiStrings.msg("ui_52ac486e9f66"));return;}
        try(java.io.InputStream input=a.getResources().openRawResource(id);java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];int size;while((size=input.read(buffer))>=0){if(output.size()+size>512000)throw new java.io.IOException("license size");output.write(buffer,0,size);}text(a,UiStrings.msg("ui_4c828fc6ac13"),output.toString("UTF-8"));
        }catch(java.io.IOException e){text(a,UiStrings.msg("ui_4c828fc6ac13"),UiStrings.msg("ui_52ac486e9f66"));}
    }
    static void sources(Activity a){GapUi.menu(a,UiStrings.msg("ui_c50f46fcd38f"),UiStrings.msg("ui_d5000000000c"),new String[]{UiStrings.msg("ui_70174055607c"),UiStrings.msg("ui_d5000000000c")},n->{if(n==0)text(a,UiStrings.msg("ui_c50f46fcd38f"),UiStrings.msg("ui_d411d99bc5f1"));else TypographySheet.show(a);});}
    static void tasks(Activity a){ShellStateRepository.get(a).openTasks();}
    static void review(Activity a){GapUi.coverage(a);}
    static String gameInfo(Context a,GameEntry g){
        if(g==null)return UiStrings.msg("ui_e4945e179ec5");MetadataManager m=MetadataManager.get(a);MetadataManager.Info i=m.detailInfo(g.gameId);JSONObject raw=i.raw.optJSONObject("release");
        String text=GameDisplay.name(g,m)+"\n\n"+i.label()+(m.userEdited(g.gameId)?UiStrings.msg("ui_96ed33a5b867"):"")+UiStrings.msg("ui_8b2e7df88917")+m.genreLabel(g.gameId);
        String description=m.description(g.gameId);boolean userDescription=m.effective(g.gameId).textSource.equals("USER");text+="\n\n"+descriptionCaption(m,g.gameId)+"\n"+description;
        if(!description.isEmpty())text+=userDescription?UiStrings.msg("ui_b8f1b4accf34"):UiStrings.msg("ui_1a6ec37b5032");
        JSONObject confirmed=m.overrideCopy(g.gameId).optJSONObject(LocaleSettings.field("confirmedTextSource"));if(confirmed!=null)text+=UiStrings.msg("ui_0205b71380a6")+confirmed.optString("name")+"\n"+confirmed.optString("url")+"\n"+confirmed.optString("platforms")+" · "+confirmed.optString("language")+"\n"+confirmed.optString("source")+UiStrings.msg("ui_de133b188c64")+confirmed.optString("textSHA256")+(userDescription?UiStrings.msg("ui_10b6dedef910"):"");
        OfflineCatalog.Text copy=m.catalog.text(g.gameId);
        if(copy!=null)try{JSONObject evidence=new JSONObject(copy.evidence);OfflineCatalog.Head h=m.catalog.head(g.gameId);String kind=h==null?"":h.kind(copy.locale);String quality=kind.equals("STORY_ONLY")?UiStrings.msg("ui_7b4e8faebb7d"):kind.equals("REFERENCE")?UiStrings.msg("ui_aaa46aa4ec01"):kind.equals("READING")||kind.equals("UTILITY")?UiStrings.msg("ui_4b99e0820e48"):kind.equals("MISSING")||kind.isEmpty()?UiStrings.msg("ui_8d45cbe9b1b7"):UiStrings.msg("ui_26ac5cb501e8");text+="\n\n"+(userDescription||confirmed!=null?UiStrings.msg("ui_d96afca50e37"):UiStrings.msg("ui_7cfd73ea03e8"))+" · "+m.catalog.version()+UiStrings.msg("ui_53578ef8c236")+quality+UiStrings.msg("ui_c12fd1b769b6")+"\n"+evidence.optString("qualityStatus");JSONArray refs=evidence.optJSONArray("sourceRefs");if(refs!=null)for(int n=0;n<refs.length();n++){JSONObject ref=refs.getJSONObject(n);text+="\n\n"+ref.optString("title")+"\n"+ref.optString("url");}text+=UiStrings.msg("ui_2c115e9a54ba")+evidence.optString("contentSHA256");}catch(JSONException ignored){}
        if(!m.rawGenre(g.gameId).isEmpty())text+=UiStrings.msg("ui_bcb2e8da229f")+m.rawGenre(g.gameId);
        if(!i.title.isEmpty())text+=UiStrings.msg("ui_4ccd84a1a719")+i.title;if(!i.region.isEmpty())text+=UiStrings.msg("ui_d6f8a049c8ea")+region(i.region);
        if(raw!=null){if(!raw.optString("developer").isEmpty())text+=UiStrings.msg("ui_3ce6801169a7")+raw.optString("developer");if(!raw.optString("publisher").isEmpty())text+=UiStrings.msg("ui_7ab6fbf16db4")+raw.optString("publisher");}
        JSONObject extra=i.raw.optJSONObject("supplemental");if(extra!=null){if(!extra.optString("developer").isEmpty())text+=UiStrings.msg("ui_3ce6801169a7")+extra.optString("developer");if(!extra.optString("publisher").isEmpty())text+=UiStrings.msg("ui_7ab6fbf16db4")+extra.optString("publisher");if(!extra.optString("date").isEmpty())text+=UiStrings.msg("ui_8ee942068fc6")+extra.optString("date");}
        if(!GameDisplay.tags(g).isEmpty())text+=UiStrings.msg("ui_b322b1e6ba12")+GameDisplay.tags(g);
        text+=UiStrings.msg("ui_7423e5b0e2aa")+i.provider+"\n"+i.basis;
        if(!i.id.isEmpty())text+=UiStrings.msg("ui_b80a27f373ae")+i.id;
        if(!i.raw.optString("genreBasis").isEmpty())text+="\n"+i.raw.optString("genreBasis");
        if(!i.raw.optString("genreProvider").isEmpty())text+=UiStrings.msg("ui_21de2b207e82")+i.raw.optString("genreProvider");
        if(extra!=null)text+=UiStrings.msg("ui_081ba82dbd99")+extra.optString("releaseId")+"\n"+extra.optString("basis")+UiStrings.msg("ui_81b65d29b5db")+extra.optString("sha1");
        if(i.raw.optLong("fetchedAt")>0)text+=UiStrings.msg("ui_e3eb2ff1b2a5")+new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.CHINA).format(new Date(i.raw.optLong("fetchedAt")));
        if(!i.localCover.isEmpty())text+=UiStrings.msg("ui_f0b82d28726a");if(!i.remoteCover.isEmpty())text+=UiStrings.msg("ui_40fdbd8d26bd")+i.raw.optString("coverUrl");
        if(!i.error.isEmpty())text+=UiStrings.msg("ui_835217271169")+i.error;
        return text+UiStrings.msg("ui_ef437fadcf66");
    }
    static void correct(Activity a,GameEntry g){GapUi.item(a,g,null);}
    static List<String> toList(JSONArray a){List<String> s=new ArrayList<>();for(int i=0;i<a.length();i++)s.add(a.optString(i));return s;}
    private static ScrollView editorViewport(Activity a,EditText editor){
        // AlertDialog keeps its title and action panel outside this resizable area.
        // A multiline editor can scroll when the IME leaves less than its minLines.
        ScrollView viewport=new ScrollView(a);viewport.setFillViewport(false);
        viewport.addView(editor,new ScrollView.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT,android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        return viewport;
    }
    static void identity(Activity a,GameEntry g){
        GapUi.lookup(a,g,false);
    }
    static void identityReady(Activity a,GameEntry g){GapUi.lookup(a,g,false);}
}
