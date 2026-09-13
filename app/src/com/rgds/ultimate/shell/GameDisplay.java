package com.rgds.ultimate.shell;

final class GameDisplay {
    private static final java.util.regex.Pattern PARTS=java.util.regex.Pattern.compile("[（(]([^()（）]*)[）)]");
    private static final java.util.regex.Pattern ANNOTATION=java.util.regex.Pattern.compile("(?i)汉|简|繁|英|日|韩|美|汉化|JP|CN|EN|JPN|USA");
    private static final java.util.regex.Pattern SPACE=java.util.regex.Pattern.compile("\\s+");
    private static final java.util.regex.Pattern VERSION=java.util.regex.Pattern.compile("(?i)\\bV\\d+(?:\\.\\d+)*");
    private static final java.util.regex.Pattern LANGUAGE_MARKER=java.util.regex.Pattern.compile("(?i)[（(](?:汉|简|繁|英|日|韩|美|汉化|JP|CN|EN|JPN|USA|DE|KS)[）)]");
    private static final java.util.regex.Pattern TAIL=java.util.regex.Pattern.compile("[（(]([^()（）]*)$");
    private static final java.util.regex.Pattern CAPACITY_TAIL=java.util.regex.Pattern.compile("(?i)(?:1|2|5|12|25|64|128|256|512|1024|2048)(?:[mg]b?)?|(?:J|JP|JPN|E|EN|U|USA|CN|汉|简|繁|英|日|韩|美)");
    private static final java.util.regex.Pattern MEANINGFUL_VERSION=java.util.regex.Pattern.compile("(?i)修正|修复|防黑屏|不死机|补丁|補丁|改版|汉化版|文本|菜单|剧情|动画|CG版|V[0-9]|[0-9]+\\.[0-9]+");
    private static final java.util.regex.Pattern CHINESE_ANNOTATION=java.util.regex.Pattern.compile("汉化|中文|简体|繁体|修正版|修复版|汉化版|最终版|完全版");
    static String name(GameEntry g,MetadataManager m){
        return name(g,m,LocaleSettings.ui());
    }
    static String name(GameEntry g,MetadataManager m,String locale){
        String s=m.title(g);if(g==null)return s;
        if(locale.equals("en")){org.json.JSONObject o=m.overrideCopy(g.gameId);if(o.has("displayName_en"))return o.optString("displayName_en");OfflineCatalog.Head head=m.catalog.head(g.gameId);if(head!=null&&!head.englishName.isEmpty())return head.englishName;MetadataManager.Info info=m.info(g.gameId);return info.title.isEmpty()?g.menuTitle():info.title;}
        if(m.customName(g.gameId))return s;
        String local=localName(g,s);
        // A source title never owns an existing local Chinese name. User overrides already won above.
        if(hasMeaningfulChinese(local))return local;
        String chinese=m.chineseName(g.gameId);return chinese.isEmpty()?local:chinese;
    }
    static boolean hasMeaningfulChinese(String name){
        String content=CHINESE_ANNOTATION.matcher(PARTS.matcher(name).replaceAll("")).replaceAll("");
        int count=0;for(int n=0;n<content.length();n++)if(Character.UnicodeScript.of(content.charAt(n))==Character.UnicodeScript.HAN)count++;
        return count>=2;
    }
    private static String localName(GameEntry g,String s){
        if(g.v04MenuTitle!=null)return g.v04MenuTitle;
        java.util.regex.Matcher tail=TAIL.matcher(s);
        if(tail.find()){
            String part=tail.group(1);
            if(MEANINGFUL_VERSION.matcher(part).find())s=s+(s.charAt(tail.start())=='（'?"）":")");
            else if(part.isEmpty()||CAPACITY_TAIL.matcher(part).matches()||LANGUAGE_MARKER.matcher(s.substring(0,tail.start())).find())s=s.substring(0,tail.start());
        }
        java.util.regex.Matcher parts=PARTS.matcher(s);
        StringBuffer out=new StringBuffer();
        while(parts.find()){
            String part=parts.group(1);
            boolean annotation=ANNOTATION.matcher(part).matches()||part.contains("汉化组")||part.contains("汉化工作室")||part.contains("配音小组")||part.contains("DSi修复");
            parts.appendReplacement(out,java.util.regex.Matcher.quoteReplacement(annotation?"":parts.group()));
        }
        parts.appendTail(out);
        String cleaned=out.toString().replaceAll("[（(][^()（）]*(?:汉化组|配音小组|DSi修复)[^()（）]*$","").replaceAll("[（(](?:汉|简|繁|英|日|韩|美)$","");
        g.v04MenuTitle=SPACE.matcher(cleaned).replaceAll(" ").trim();return g.v04MenuTitle;
    }
    static String rowName(GameEntry g,MetadataManager m){return name(g,m);}
    static String tags(GameEntry g){
        if(g==null)return "";if(g.v04VersionTags!=null)return g.v04VersionTags;String s=g.menuTitle();java.util.List<String> tags=new java.util.ArrayList<>();
        if(s.contains("繁"))tags.add(UiStrings.msg("ui_7336e6535d26"));else if(s.contains("简"))tags.add(UiStrings.msg("ui_5279d84d4d35"));else if(s.contains("汉")||s.contains("中文"))tags.add(UiStrings.msg("ui_6822a7f3dd71"));
        if(s.contains("配音"))tags.add(UiStrings.msg("ui_c2cb144c8158"));
        if(s.contains("修正")||s.contains("修复")||s.contains("不死机"))tags.add(UiStrings.msg("ui_336f5df2f102"));
        if(s.contains("改版")||s.contains("Remix"))tags.add(UiStrings.msg("ui_04542ccc30cc"));
        java.util.regex.Matcher v=VERSION.matcher(s);if(v.find())tags.add(v.group());
        if(tags.isEmpty()&&s.contains("JP"))tags.add(UiStrings.msg("ui_5eb1b36eee24"));g.v04VersionTags=String.join(" · ",tags);return g.v04VersionTags;
    }
}
