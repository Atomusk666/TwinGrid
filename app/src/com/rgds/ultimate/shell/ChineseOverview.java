package com.rgds.ultimate.shell;
import java.util.*;
import java.util.regex.Pattern;
/** Extract complete source sentences; never infer missing mechanics from a game's name. */
final class ChineseOverview {
    static final int VERSION=6;
    private static final Pattern MECHANICS=Pattern.compile("操作|操控|触摸|觸摸|回合制|回合製|解谜|解謎|关卡|關卡|收集|战斗系统|戰鬥系統|对战模式|對戰模式|装备|裝備|技能|培养|培養|道具|地图|地圖|横版|橫版|跳跃|跳躍|迷宫|迷宮|合成|模拟经营|模擬經營|战略|戰略");
    private static final Pattern PLAYER=Pattern.compile("玩家.{0,30}(?:操作|控制|使用|探索|收集|战斗|戰鬥|解决|解決|扮演|驾驶|駕駛)");
    private static final Pattern INTERACTION=Pattern.compile("触控|觸控|触笔|觸筆|按键|按鍵|建造|建设|建設|经营|經營|驾驶|駕駛|治疗|治療|手术|手術|消除|钓鱼|釣魚|游戏模式|遊戲模式|多人对抗|多人對抗|照顾|照顧|照料|饲养|飼養|领养|領養|寄养|寄養|舞蹈|比赛|比賽|拍照|得分|失误|失誤|切换|切換|无线通信|無線通信|情绪|情緒|性格.{0,15}(选择|選擇)|系统.{0,25}(挑选|挑選|选择|選擇)");
    private static final Pattern STORY=Pattern.compile("故事|主角|剧情|劇情|舞台|传说|傳說|某一天|某日|主人公|帝国|帝國|统治|統治");
    private static final Pattern SPOILER=Pattern.compile("结局|結局|最终真相|最終真相|最后死|最後死|凶手是|幕后黑手是|幕後黑手是");
    private static int score(String sentence){int n=0;java.util.regex.Matcher m=MECHANICS.matcher(sentence);while(m.find())n++;m=INTERACTION.matcher(sentence);while(m.find())n+=2;if(PLAYER.matcher(sentence).find())n+=2;return n;}
    static int rank(String q){return q.equals("MIXED")||q.equals("GAMEPLAY")?4:q.equals("STORY_ONLY")?3:q.equals("OTHER_TEXT")?2:q.equals("RELEASE_ONLY")?1:0;}
    static String quality(String text){if(text==null||text.trim().isEmpty())return "EMPTY";boolean gameplay=false,story=STORY.matcher(text).find();for(String s:text.split("(?<=[。！？!?])|\\n+"))if(score(s)>=2)gameplay=true;return gameplay?(story?"MIXED":"GAMEPLAY"):story?"STORY_ONLY":text.matches("(?s).*(发售|發售|发行|發行|推出|开发|開發|発売|released|published).*")?"RELEASE_ONLY":"OTHER_TEXT";}
    static String shortText(String text){
        if(text==null||text.isEmpty())return "";List<String> sentences=new ArrayList<>();for(String s:text.split("(?<=[。！？!?])|\\n+")){s=s.trim();if(!s.isEmpty()&&!SPOILER.matcher(s).find())sentences.add(s);}
        List<String> chosen=new ArrayList<>();for(String s:sentences)if(score(s)>0||s.matches(".*(结束游戏|結束遊戲|游戏结束|遊戲結束).*"))chosen.add(s);
        if(chosen.isEmpty())chosen=sentences;StringBuilder out=new StringBuilder();
        // Prefer complete source sentences before considering an excerpt of an oversized one.
        for(String s:chosen){if(out.length()+s.length()<=120)out.append(s);if(out.length()>=80)break;}
        if(out.length()==0)for(String s:chosen){int depth=0,cut=-1;for(int i=0;i<Math.min(119,s.length());i++){char c=s.charAt(i);if("（(【[".indexOf(c)>=0)depth++;else if("）)】]".indexOf(c)>=0)depth=Math.max(0,depth-1);if(i>=25&&depth==0&&"，；".indexOf(c)>=0&&!s.substring(0,i).matches("(?s).*(时|時|的话|的話|但是|其中|如果|但)$"))cut=i;}if(cut>0){out.append(s.substring(0,cut)).append("…");break;}}
        return out.toString();
    }
}
