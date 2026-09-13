package com.rgds.ultimate.shell;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** Versioned exact-token vocabulary. Unknown source terms remain visible in details. */
final class GenreTaxonomy {
    static final int VERSION=6;
    private static final Map<String,String> TERMS=new HashMap<>();
    private static final Set<String> QUALIFIERS=new HashSet<>();
    private static final Map<String,List<String>> CACHE=new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<String,Result> PARSED=new java.util.concurrent.ConcurrentHashMap<>();
    private static final Pattern SPACE=Pattern.compile("[\\p{Z}\\s]+"), DASH=Pattern.compile("[\\p{Pd}_]+"), SPLIT=Pattern.compile("[,;|、，；\\n+&·]+");
    private static final Map<String,String> BANGUMI=new HashMap<>();
    static final String[] IDS={"action","adventure","rpg","strategy","racing","sports","fighting","shooter","puzzle","music","simulation","utility","other"};
    static final String[] LABELS={"动作","冒险","角色扮演","策略","竞速","体育","格斗","射击","益智","音乐","模拟/养成","工具","其他"};
    static {
        add("动作","action","platform","platformer","platforming","beat 'em up","beat em up","动作","平台动作");
        add("冒险","adventure","冒险");add("动作|冒险","action adventure","动作冒险");
        add("角色扮演","role playing","role playing game","roleplaying","rpg","console style rpg","角色扮演","角色扮演游戏");
        add("动作|角色扮演","action rpg","action role playing","动作角色扮演");
        add("策略|角色扮演","tactical rpg","strategy rpg");
        add("策略","strategy","real time strategy","turn based strategy","tactical","wargame","策略","战略");
        add("竞速","racing","driving","kart","horse racing","竞速","赛车");
        add("体育","sports","sport","soccer","baseball","football","golf","tennis","olympic sports","basketball","boxing","skating","bowling","skiing","ice hockey","体育","运动");
        add("格斗","fighting","格斗");add("射击","shooter","shooting","first person shooter","third person shooter","light gun","射击");
        add("益智","puzzle","board","board game","board games","card","cards","card game","card battle","logic","matching","hidden object","pinball","益智","解谜","桌游");
        add("音乐","music","rhythm","music maker","dancing","音乐","节奏");
        add("模拟/养成","simulation","sim","virtual life","breeding","breeding/constructing","tycoon","city building","management","模拟","养成","模拟/养成");
        add("其他","miscellaneous","other","edutainment","compilation","其他","教育");
        add("工具","utility","utilities","translation tool","cooking guide","lifestyle tracker","工具","翻译工具","翻譯工具");
        add("动作|角色扮演","人類進化アクションRPG");
        add("益智","パズル");
        Collections.addAll(QUALIFIERS,"general","2d","3d","traditional","modern","fantasy","sci fi","arcade","first person","third person","turn based","real time","historic","scrolling","static","alternative","horror");
        add("角色扮演","role playing (rpg)","角色扮演遊戲");
        add("动作","動作","動作遊戲");add("冒险","冒險","冒險遊戲");
        add("动作|角色扮演","action (rpg)","動作角色扮演");
        add("策略","戰略","戰略遊戲");add("射击","射擊","射擊遊戲");add("竞速","競速","賽車");
        add("音乐","音樂","音樂遊戲");add("模拟/养成","模擬","養成","模擬/養成");
        add("益智","解謎","益智遊戲","trivia / game show","trivia","game show","gambling");
        add("体育","fishing","snowboarding");add("模拟/养成","flight");
        add("其他","party");
        Collections.addAll(QUALIFIERS,"text","nature","stacking","parlor","modern jet","civilian plane","rally","offroad","gt","street");
        String[][] bg={{"ACT","动作"},{"ARPG","动作|角色扮演"},{"ADV","冒险"},{"AVG","冒险"},{"A-AVG","动作|冒险"},{"A-ADV","动作|冒险"},{"RCG","竞速"},{"ETC","其他"},{"AAVG","动作|冒险"},{"RPG","角色扮演"},{"SRPG","策略|角色扮演"},{"SLG","策略"},{"RTS","策略"},{"FTG","格斗"},{"STG","射击"},{"FPS","射击"},{"TPS","射击"},{"PUZ","益智"},{"PZG","益智"},{"RAC","竞速"},{"SPG","体育"},{"SIM","模拟/养成"},{"MUG","音乐"}};
        for(String[] p:bg)BANGUMI.put(normalize(p[0]),p[1]);
        add("体育","billiards","exercise","fitness","exercise / fitness");
        String[][] words={{"文字冒险","冒险"},{"冒险类","冒险"},{"アドベンチャー","冒险"},{"アドベンチャーゲーム","冒险"},{"恋爱AVG","冒险"},{"恐怖AVG","冒险"},{"恋愛AVG","冒险"},{"女性向AVG","冒险"},{"动作游戏","动作"},{"动作类","动作"},{"平台跳跃","动作"},{"アクション","动作"},{"アクションRPG","动作|角色扮演"},{"角色扮演类","角色扮演"},{"动作类角色扮演","动作|角色扮演"},{"模拟类","模拟/养成"},{"经营","模拟/养成"},{"模拟经营","模拟/养成"},{"模拟经营SIM","模拟/养成"},{"养成","模拟/养成"},{"育成","模拟/养成"},{"恋爱养成","模拟/养成"},{"恋爱育成","模拟/养成"},{"对战型卡片游戏","益智"},{"卡片类","益智"},{"卡牌","益智"},{"纸牌游戏","益智"},{"棋牌","益智"},{"益智类","益智"},{"益智游戏","益智"},{"パズル","益智"},{"パズルゲーム","益智"},{"第一人称射击","射击"},{"节奏游戏","音乐"},{"回合策略","策略"},{"Tower defense","策略"},{"Turn-based tactics","策略"},{"TBG","益智"}};
        for(String[] p:words)BANGUMI.put(normalize(p[0]),p[1]);
    }
    private static void add(String category,String... words){for(String word:words)TERMS.put(normalize(word),category);}
    static String normalize(String s){return SPACE.matcher(DASH.matcher(Normalizer.normalize(s==null?"":s,Normalizer.Form.NFKC).replace('’','\'').replace('‘','\'').toLowerCase(Locale.ROOT)).replaceAll(" ")).replaceAll(" ").trim();}
    private static List<String> tokens(String raw){List<String> out=new ArrayList<>();for(String t:SPLIT.split(raw==null?"":raw)){String n=normalize(t);if(!TERMS.containsKey(n)&&n.contains("/")){for(String part:n.split("/"))out.add(normalize(part));}else out.add(n);}return out;}
    static List<String> categories(String raw){
        String key=raw==null?"":raw;List<String> cached=CACHE.get(key);if(cached!=null)return cached;
        LinkedHashSet<String> result=new LinkedHashSet<>();
        for(String token:tokens(raw)){
            String normalized=normalize(token),mapped=TERMS.get(normalized);
            if(mapped==null&&normalized.contains(" / ")){for(String part:normalized.split(" / ")){String m=TERMS.get(part);if(m!=null)Collections.addAll(result,m.split("\\|"));}}
            else if(mapped!=null)Collections.addAll(result,mapped.split("\\|"));
        }
        // Miscellaneous is an upstream parent, not an extra type beside a known subtype.
        if(result.size()>1&&tokens(raw).contains("miscellaneous")&&!tokens(raw).contains("other"))result.remove("其他");
        List<String> value=validOverrides(result);if(CACHE.size()<1024)CACHE.put(key,value);return value;
    }
    static String signature(String raw){
        TreeSet<String> values=new TreeSet<>();for(String t:tokens(raw)){
            String n=normalize(t);if(n.isEmpty()||QUALIFIERS.contains(n))continue;
            String mapped=TERMS.get(n);if(mapped!=null)Collections.addAll(values,mapped.split("\\|"));else values.add("source:"+n);
        }return String.join("|",values);
    }
    static final class Result {
        final String source,raw,state;final List<String> categories,ids,unmapped,qualifiers;
        Result(String s,String r,Set<String> c,List<String> u,List<String> q){source=s;raw=r;categories=validOverrides(c);unmapped=Collections.unmodifiableList(u);qualifiers=Collections.unmodifiableList(q);List<String> keys=new ArrayList<>();for(String v:c){int n=Arrays.asList(LABELS).indexOf(v);if(n>=0)keys.add(IDS[n]);}ids=Collections.unmodifiableList(keys);state=normalize(r).isEmpty()?"NO_SOURCE":!u.isEmpty()?(c.isEmpty()?"UNMAPPED":"PARTIAL"):c.isEmpty()?"QUALIFIERS_ONLY":c.size()==1&&c.contains("其他")?"EXPLICIT_OTHER":"MAPPED";}
    }
    static Result parse(String source,String raw){
        String key=(source==null?"":source)+'\u0000'+(raw==null?"":raw);Result cached=PARSED.get(key);if(cached!=null)return cached;
        Result result=parseUncached(source,raw);if(PARSED.size()<1024)PARSED.put(key,result);return result;
    }
    private static Result parseUncached(String source,String raw){
        String r=raw==null?"":raw;LinkedHashSet<String> c=new LinkedHashSet<>();List<String> u=new ArrayList<>(),q=new ArrayList<>();
        boolean bg=source!=null&&source.toLowerCase(Locale.ROOT).contains("bangumi");
        for(String t:tokens(r)){if(t.isEmpty())continue;String m=bg&&BANGUMI.containsKey(t)?BANGUMI.get(t):TERMS.get(t);
            if(m!=null)Collections.addAll(c,m.split("\\|"));else if(QUALIFIERS.contains(t))q.add(t);else u.add(t);}
        if(c.size()>1&&tokens(r).contains("miscellaneous")&&!tokens(r).contains("other")){c.remove("其他");q.add("miscellaneous (source parent)");}
        if(source!=null&&source.toLowerCase(Locale.ROOT).contains("openvgdb")){
            List<String> hierarchy=tokens(r);
            if(c.size()>1&&hierarchy.contains("other")&&!hierarchy.get(0).equals("other")){c.remove("其他");q.add("other (subtype qualifier)");}
            if(!hierarchy.isEmpty()&&hierarchy.get(0).equals("action")&&hierarchy.contains("shooter")&&hierarchy.contains("tactical")){c.remove("策略");q.add("tactical shooter qualifier");}
        }
        return new Result(source==null?"":source,r,c,u,q);
    }
    static List<String> validOverrides(Collection<String> values){LinkedHashSet<String> result=new LinkedHashSet<>();for(String value:values){int n=Arrays.asList(IDS).indexOf(value);if(n>=0)result.add(IDS[n]);else if(Arrays.asList(LABELS).contains(value))result.add(IDS[Arrays.asList(LABELS).indexOf(value)]);}return Collections.unmodifiableList(new ArrayList<>(result));}
    // Exact read compatibility for schema 4 labels. New preferences and edits store IDs.
    static String id(String value){if(value.equals("全部类型")||value.equals("all"))return "all";if(value.equals("未分类")||value.equals("unclassified"))return "unclassified";int n=Arrays.asList(LABELS).indexOf(value);return n>=0?IDS[n]:value;}
    static String label(String id){int n=Arrays.asList(IDS).indexOf(id(id));return n>=0?DISPLAY[n]:id.equals("all")?UiStrings.msg("ui_267c4a7a86dd"):id.equals("unclassified")?UiStrings.msg("ui_54b89f901274"):id;}
    static final String[] DISPLAY={UiStrings.msg("ui_be37d84119d6"),UiStrings.msg("ui_c79e8435b942"),UiStrings.msg("ui_a94c85829dc2"),UiStrings.msg("ui_9c8eb75c7e58"),UiStrings.msg("ui_d2ff05cd7a92"),UiStrings.msg("ui_ef7be5be3c13"),UiStrings.msg("ui_6efa526f558b"),UiStrings.msg("ui_fbe878295afd"),UiStrings.msg("ui_646b073df633"),UiStrings.msg("ui_db9514212493"),UiStrings.msg("ui_b09717236dd1"),UiStrings.msg("ui_c11200000001"),UiStrings.msg("ui_d2909f1647e7")};
    static String labels(Collection<String> ids){List<String> result=new ArrayList<>();for(String id:ids)result.add(label(id));return String.join(" · ",result);}
    /** Only a proven same-work subtype superset refines an existing classification. */
    static boolean compatible(Collection<String> a,Collection<String> b){return a.isEmpty()||b.isEmpty()||a.containsAll(b)||b.containsAll(a);}
}
