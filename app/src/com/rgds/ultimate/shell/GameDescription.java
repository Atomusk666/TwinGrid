package com.rgds.ultimate.shell;
import java.util.regex.Pattern;

/** Treat source markup as text; never create a WebView or follow embedded links. */
final class GameDescription {
    static final int VERSION=3;
    private static final Pattern RELEASE_ONLY=Pattern.compile("(?is)^.{1,260}\\bis an? .{1,80} game,? developed.{0,260}(?:published|released).{0,100}$");
    private static final Pattern ENGLISH=Pattern.compile("(?i)\\b(the|you|your|and|with|game|in|to|is|are|was|players?|can|from|of|this)\\b");
    private static final Pattern UNSAFE=Pattern.compile("(?is)<(script|style)\\b[^>]*>.*?</\\1\\s*>"),CONTROLS=Pattern.compile("[\\p{Cc}\\p{Cf}&&[^\\n\\t]]"),SPACE=Pattern.compile("[\\p{Z}\\s]+");
    static String clean(String source){
        if(source==null)return "";String bounded=source.substring(0,Math.min(20000,source.length()));
        String text=android.text.Html.fromHtml(UNSAFE.matcher(bounded).replaceAll(""),android.text.Html.FROM_HTML_MODE_LEGACY).toString();
        return SPACE.matcher(CONTROLS.matcher(text).replaceAll("")).replaceAll(" ").trim();
    }
    static String language(String text){
        int han=0,kana=0,hangul=0;for(int i=0;i<text.length();i++){Character.UnicodeScript script=Character.UnicodeScript.of(text.charAt(i));if(script==Character.UnicodeScript.HAN)han++;else if(script==Character.UnicodeScript.HIRAGANA||script==Character.UnicodeScript.KATAKANA)kana++;else if(script==Character.UnicodeScript.HANGUL)hangul++;}
        if(han>15&&han>kana*3&&han>hangul*3)return "zh";if(kana>0)return "ja";if(hangul>0)return "ko";
        java.util.Set<String> words=new java.util.HashSet<>();java.util.regex.Matcher m=ENGLISH.matcher(text);while(m.find())words.add(m.group().toLowerCase(java.util.Locale.ROOT));return words.size()>=2?"en":"und";
    }
    static String cleanParagraphs(String source){
        if(source==null)return "";String bounded=source.substring(0,Math.min(20000,source.length()));
        String text=android.text.Html.fromHtml(UNSAFE.matcher(bounded).replaceAll("").replace("\n","<br>"),android.text.Html.FROM_HTML_MODE_LEGACY).toString();
        return CONTROLS.matcher(text).replaceAll("").replaceAll("[^\\S\\n]+"," ").replaceAll("\\n{3,}","\n\n").trim();
    }
    static boolean usable(String text){
        String value=clean(text);if(value.isEmpty()||RELEASE_ONLY.matcher(value).matches())return false;
        // Reject a title or a few placeholder words without treating length as factual review.
        int ideographs=0;for(int n=0;n<value.length();n++){Character.UnicodeScript s=Character.UnicodeScript.of(value.charAt(n));if(s==Character.UnicodeScript.HAN||s==Character.UnicodeScript.HIRAGANA||s==Character.UnicodeScript.KATAKANA||s==Character.UnicodeScript.HANGUL)ideographs++;}
        if(ideographs>=16)return true;
        java.util.regex.Matcher words=Pattern.compile("[A-Za-z]+(?:['’-][A-Za-z]+)*").matcher(value);int count=0;while(words.find())if(++count>=12)return true;return false;
    }
    static String shortText(String text,String language){
        int limit=language.equals("zh")?100:330;String result=text.substring(0,Math.min(limit,text.length()));
        int sentences=0,end=-1;for(int i=0;i<result.length();i++)if("。！？".indexOf(result.charAt(i))>=0||(result.charAt(i)=='.'&&i+1<result.length()&&result.charAt(i+1)==' ')){if(++sentences==2){end=i+1;break;}}
        if(end>0)result=result.substring(0,end);else if(text.length()>limit)result=result.trim()+"…";return result;
    }
}
