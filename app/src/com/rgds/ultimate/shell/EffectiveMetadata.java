package com.rgds.ultimate.shell;

import org.json.*;
import java.util.*;

/** Lightweight projection from loaded heads and overrides. Never reads a text file to count it. */
final class EffectiveMetadata {
    enum TextState { VALID, FOREIGN, HIDDEN, MISSING, LOADING }
    final TextState text;final boolean bundled,linked,gameplay,candidate,cover,typed,deferred,coverReview;
    final String textSource,kind,locale;final ResolveDecision.Kind decisionKind;
    EffectiveMetadata(OfflineCatalog.Head head,JSONObject user,MetadataManager.Info info,boolean prepared,boolean hasCover,List<String> genres){
        this(head,user,info,prepared,hasCover,genres,LocaleSettings.content());
    }
    EffectiveMetadata(OfflineCatalog.Head head,JSONObject user,MetadataManager.Info info,boolean prepared,boolean hasCover,List<String> genres,String language){
        this(head,user,info,prepared,hasCover,genres,language,null);
    }
    EffectiveMetadata(OfflineCatalog.Head head,JSONObject user,MetadataManager.Info info,boolean prepared,boolean hasCover,List<String> genres,String language,ResolveDecision decision){
        decisionKind=decision==null?null:decision.kind;locale=language;bundled=head!=null&&head.has(locale);linked=head!=null;candidate=decision!=null?decision.needsInput():info.status.equals("CANDIDATE")&&info.raw.optInt("candidateCount",info.raw.optJSONArray("candidates")==null?0:info.raw.optJSONArray("candidates").length())>0;
        coverReview=user!=null&&!user.optString("coverReviewReason").isEmpty()||(user==null||!user.has("cover"))&&info.raw.optJSONObject("coverAssociation")!=null&&info.raw.optJSONObject("coverAssociation").optBoolean("needsUserChoice");cover=hasCover;typed=!genres.isEmpty();deferred=user!=null&&user.optBoolean("deferReview");
        String value=null,source="",quality="";
        if(user!=null&&user.has(LocaleSettings.field("description",locale))){value=user.optString(LocaleSettings.field("description",locale));source="USER";quality=user.optString(LocaleSettings.field("descriptionKind",locale),"UNREVIEWED");}
        else if(user!=null&&user.has(LocaleSettings.field("confirmedText",locale))){value=user.optString(LocaleSettings.field("confirmedText",locale));source="CONFIRMED";quality=user.optString(LocaleSettings.field("confirmedKind",locale),"REFERENCE");}
        if(value!=null){text=value.trim().isEmpty()?TextState.HIDDEN:GameDescription.usable(value)&&GameDescription.language(value).equals(locale)?TextState.VALID:TextState.FOREIGN;textSource=source;kind=quality;}
        else{text=bundled?TextState.VALID:prepared?TextState.MISSING:TextState.LOADING;textSource="BUNDLE";kind=head==null?"MISSING":head.kind(locale);}
        gameplay=text==TextState.VALID&&(kind.equals("GAMEPLAY")||kind.equals("MIXED"));
    }
    boolean missingText(){return text==TextState.MISSING||text==TextState.FOREIGN;}
    boolean reviewedContent(){return text==TextState.VALID&&(gameplay||kind.equals("UTILITY")||kind.equals("READING"));}
    String reason(){List<String> r=new ArrayList<>();if(candidate)r.add(decisionKind==ResolveDecision.Kind.UNKNOWN_MAPPING?FirstRun.s(15):decisionKind==ResolveDecision.Kind.INSUFFICIENT_EVIDENCE?FirstRun.s(16):decisionKind==ResolveDecision.Kind.CONFLICT||decisionKind==ResolveDecision.Kind.USER_CONFLICT?FirstRun.s(17):UiStrings.msg("ui_6e4431fa826e"));if(!linked)r.add(UiStrings.msg("ui_66e747fdfdba"));if(missingText())r.add(text==TextState.FOREIGN?UiStrings.msg("ui_fbdc06f77669"):text==TextState.HIDDEN?UiStrings.msg("ui_d579f1912fe1"):UiStrings.msg("ui_4f359773470f"));if(text==TextState.LOADING)r.add(UiStrings.msg("ui_42f98ea02db3"));if(text==TextState.VALID&&!reviewedContent())r.add(UiStrings.msg("ui_066186b9f36f"));if(!typed)r.add(UiStrings.msg("ui_349cd074f6b4"));if(coverReview)r.add(UxStrings.s(95));if(!cover)r.add(UiStrings.msg("ui_3fdefb0b4da8"));return String.join(" / ",r);}
}
