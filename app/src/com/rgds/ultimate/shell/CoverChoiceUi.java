package com.rgds.ultimate.shell;
import android.app.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Uses the existing queue, image loader and checked field transaction. */
final class CoverChoiceUi {
    static String text(String zh,String en){return LocaleSettings.ui().equals("en")?en:zh;}
    static void show(Activity a,GameEntry g,boolean query){
        MetadataManager m=MetadataManager.get(a);LinearLayout box=new LinearLayout(a);box.setOrientation(1);
        TextView status=GapUi.caption(a,"");box.addView(status);
        ListView list=new ListView(a);box.addView(list,new LinearLayout.LayoutParams(-1,224));
        AlertDialog d=new ShellDialogBuilder(a).setTitle(text("选择封面","Choose cover")).setView(box)
            .setPositiveButton(text("获取备选封面","Find alternatives"),null)
            .setNeutralButton(text("暂停","Pause"),null)
            .setNegativeButton(text("返回","Back"),null).create();
        List<JSONObject> rows=new ArrayList<>();List<String> labels=new ArrayList<>();String[] token={""};
        Map<String,Long> stableIds=new HashMap<>();long[] nextId={1};
        BaseAdapter adapter=new BaseAdapter(){
            public int getCount(){return rows.size();}public Object getItem(int n){return rows.get(n);}
            public boolean hasStableIds(){return true;}public long getItemId(int n){return stableIds.get(rows.get(n).optString("key"));}
            public View getView(int n,View reuse,ViewGroup parent){
                LinearLayout line,copy;ImageView image;
                if(reuse instanceof LinearLayout){line=(LinearLayout)reuse;image=(ImageView)line.getChildAt(0);copy=(LinearLayout)line.getChildAt(1);m.covers().release(image);image.setImageDrawable(null);}
                else{line=new LinearLayout(a);line.setGravity(Gravity.CENTER_VERTICAL);line.setBackground(new ClassicUi.Edge(false));line.setPadding(6,4,8,4);line.setLayoutParams(new AbsListView.LayoutParams(-1,68));image=new ImageView(a);image.setScaleType(ImageView.ScaleType.FIT_CENTER);line.addView(image,new LinearLayout.LayoutParams(48,48));copy=new LinearLayout(a);copy.setOrientation(1);copy.setPadding(8,0,0,0);for(int i=0;i<2;i++){TextView v=new TextView(a);DsTypography.body(v,18);v.setTextColor(ClassicUi.INK);v.setSingleLine(true);v.setEllipsize(android.text.TextUtils.TruncateAt.END);copy.addView(v,new LinearLayout.LayoutParams(-1,28));}line.addView(copy,new LinearLayout.LayoutParams(0,56,1));}
                JSONObject row=rows.get(n);((TextView)copy.getChildAt(0)).setText(row.optString("title"));((TextView)copy.getChildAt(1)).setText(labels.get(n));if(row.optString("status").equals("VERIFIED")){final ImageView target=image;m.covers().request(target,row.optString("key"),64,b->{if(d.isShowing())target.setImageBitmap(b);});}return line;
            }
        };
        list.setAdapter(adapter);
        Runnable refresh=()->{
            if(!d.isShowing()||a.isFinishing())return;JSONObject record=m.coverCandidates(g.gameId);
            String next=record.toString()+m.coverKey(g.gameId)+m.task().revision+LocaleSettings.ui();if(next.equals(token[0]))return;token[0]=next;
            int selected=list.getSelectedItemPosition();String selectedKey=selected>=0&&selected<rows.size()?rows.get(selected).optString("key"):"";
            View selectedView=list.getSelectedView();int selectedTop=selectedView==null?0:selectedView.getTop()-list.getPaddingTop();
            rows.clear();labels.clear();JSONArray candidates=record.optJSONArray("candidates");
            boolean fresh=record.optString("queryIdentity").equals(m.coverIdentity(g.gameId));
            Set<String> seen=new HashSet<>();if(fresh&&candidates!=null)for(int n=0;n<candidates.length();n++){JSONObject r=candidates.optJSONObject(n);if(r!=null&&!r.optString("key").isEmpty()&&seen.add(r.optString("key")))rows.add(r);}
            int restored=-1;for(int n=0;n<rows.size();n++){JSONObject r=rows.get(n);String state=r.optString("status"),key=r.optString("key");
                if(!stableIds.containsKey(key))stableIds.put(key,nextId[0]++);if(key.equals(selectedKey))restored=n;
                String label=state.equals("VERIFIED")?(r.optString("key").equals(m.coverKey(g.gameId))?text("当前选图","Selected"):text("可预览","Preview")):state.equals("ABSENT_404")?text("来源无图","No source image"):state.equals("FAILED")?text("请求失败","Request failed"):text("尚未验证","Not checked");
                labels.add(r.optString("region")+" · libretro · "+label);
            }
            adapter.notifyDataSetChanged();
            if(!rows.isEmpty()){if(restored>=0&&selectedView!=null)list.setSelectionFromTop(restored,selectedTop);else list.setSelection(restored>=0?restored:Math.min(Math.max(0,selected),rows.size()-1));}
            status.setText(text("确认前保留原图。","Current cover stays until confirmation.")+"\n"+(rows.isEmpty()?text("暂无候选；可获取备选。","No candidates; find alternatives."):rows.size()+text(" 条候选"," candidates"))+" · "+UiStrings.display(TaskPresentation.title(m.task())));
            d.getButton(-3).setEnabled(!m.task().terminal());d.getButton(-3).setText(m.task().terminal()?text("暂停","Pause"):UiStrings.display(m.taskActionText()));
            ShellDialogBuilder.gamePreview(d,g,text("选择图片只改变封面。","Selecting an image changes only the cover."));
        };
        list.setOnItemClickListener((parent,v,pos,id)->{if(pos<0||pos>=rows.size()||adapter.getItemId(pos)!=id)return;JSONObject r=rows.get(pos);if(!r.optString("status").equals("VERIFIED")||m.coverFile(r.optString("key"))==null){UiDialogs.text(a,text("封面未就绪","Cover unavailable"),r.optString("path")+"\n"+r.optString("failure")+"\n"+text("请获取备选后重试。","Find alternatives and retry."));return;}preview(a,g,r,m.coverCandidates(g.gameId),d);});
        MetadataManager.Listener listener=id->refresh.run();m.addListener(listener);MetadataManager.TaskListener taskListener=()->refresh.run();m.addTaskListener(taskListener);
        d.setOnDismissListener(x->{m.removeListener(listener);m.removeTaskListener(taskListener);m.notifyTaskUi();});d.show();
        d.getButton(-1).setOnClickListener(v->{m.requestOne(g,true);token[0]="";refresh.run();});
        d.getButton(-3).setOnClickListener(v->{CoverTask.Snapshot task=m.task();if(!task.terminal())m.taskAction(task);token[0]="";refresh.run();});
        refresh.run();if(query)m.requestOne(g,true);
    }
    private static void preview(Activity a,GameEntry g,JSONObject row,JSONObject record,AlertDialog parent){
        MetadataManager m=MetadataManager.get(a);String key=row.optString("key");long expected=m.overrideCopy(g.gameId).optLong("updatedAt");
        LinearLayout box=new LinearLayout(a);box.setOrientation(1);ImageView image=new ImageView(a);image.setScaleType(ImageView.ScaleType.FIT_CENTER);box.addView(image,new LinearLayout.LayoutParams(-1,208));
        TextView caption=GapUi.caption(a,row.optString("title")+"\n"+row.optString("region")+" · libretro-thumbnails");box.addView(caption);
        AlertDialog d=new ShellDialogBuilder(a).setTitle(text("预览封面","Cover preview")).setView(box).setPositiveButton(text("使用这张封面","Use this cover"),null).setNegativeButton(text("取消","Cancel"),null).create();
        d.setOnDismissListener(x->{m.covers().release(image);m.notifyTaskUi();});d.show();ShellDialogBuilder.gamePreview(d,g,row.optString("path"));d.getButton(-1).setEnabled(false);
        m.covers().request(image,key,288,b->{if(d.isShowing()&&!a.isFinishing()){image.setImageBitmap(b);d.getButton(-1).setEnabled(b!=null);if(b==null)caption.setText(text("图片已失效，请重新获取。","Image unavailable; fetch again."));}});
        d.getButton(-1).setOnClickListener(v->{d.getButton(-1).setEnabled(false);m.selectCover(g.gameId,key,record.optString("queryIdentity"),record.optString("indexVersion"),expected,(ok,message)->{if(a.isFinishing())return;if(ok){d.dismiss();parent.dismiss();m.savedNotice(message);}else{caption.setText(message);d.getButton(-1).setEnabled(true);}});});
    }
}
