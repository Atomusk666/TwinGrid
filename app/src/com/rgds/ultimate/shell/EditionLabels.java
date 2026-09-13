package com.rgds.ultimate.shell;

import java.util.*;
import java.util.regex.*;

/** One pass per result collection, never per selection. IDs and original names stay independent. */
final class EditionLabels {
    private static final Pattern VERSION=Pattern.compile("(?i)(?:\\bv\\d+(?:\\.\\d+)*|\\bRev[ ._-]*[A-Z0-9]+)");
    static String evidence(String filename){
        List<String> tags=new ArrayList<>();
        if(filename.matches(".*(?:繁体|繁體|[（(]繁[）)]).*"))tags.add("TC");
        else if(filename.matches(".*(?:简体|簡體|[（(]简[）)]).*"))tags.add("SC");
        else if(filename.matches(".*(?:汉化|漢化|[（(]汉[）)]).*"))tags.add("CN patch");
        if(filename.matches(".*(?:修正|修复|不死机|防黑屏|修復).*"))tags.add("fix");
        if(filename.matches(".*(?:改版|Remix).*"))tags.add("mod");
        Matcher m=VERSION.matcher(filename);if(m.find())tags.add(m.group());
        return String.join(" · ",tags);
    }
    static Map<String,String> forItems(List<DirectoryIndex.Item> items,MetadataManager metadata){
        Map<String,List<GameEntry>> groups=new LinkedHashMap<>();
        for(DirectoryIndex.Item item:items)if(item.game!=null){String name=GameDisplay.name(item.game,metadata);
            groups.computeIfAbsent(name,k->new ArrayList<>()).add(item.game);}
        Map<String,String> labels=new HashMap<>();
        for(List<GameEntry> group:groups.values())if(group.size()>1){
            Map<String,Integer> counts=new HashMap<>();for(GameEntry g:group)counts.merge(evidence(g.menuTitle()),1,Integer::sum);
            for(GameEntry g:group){String tag=evidence(g.menuTitle());
                // A local ID is an explicit file identity, not an inferred region or patch language.
                if(tag.isEmpty()||counts.get(tag)>1){int length=Math.min(6,g.gameId.length());while(length<g.gameId.length()){String prefix=g.gameId.substring(0,length);boolean collision=false;for(GameEntry other:group)if(other!=g&&other.gameId.startsWith(prefix)){collision=true;break;}if(!collision)break;length++;}tag+=(tag.isEmpty()?"file ":" · ")+"#"+g.gameId.substring(0,length);}
                labels.put(g.gameId,tag);}
        }
        return labels;
    }
}
