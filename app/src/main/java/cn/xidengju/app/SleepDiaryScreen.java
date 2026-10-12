package cn.xidengju.app;

import android.app.*;
import android.view.*;
import android.widget.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.IntConsumer;

final class SleepDiaryScreen {
    private final MainActivity activity;
    private final SleepStore store;
    private LocalDate morning=LocalDate.now();
    // Bed offset is relative to the previous evening: 21:00 through next day 04:00.
    private int bed=1410,latency=25,wake=450,rise=465,nap=2;
    private Button bedButton,latencyButton,wakeButton,riseButton,napButton;
    private TextView result;
    private static final String[] NAPS={"30 分钟以上（含 30 分钟）","30 分钟以下","白天没有补觉"};
    SleepDiaryScreen(MainActivity activity){this.activity=activity;store=new SleepStore(activity);load();}
    private void load(){SleepRecord saved=store.read(morning);if(saved!=null){bed=saved.bed.getHour()*60+saved.bed.getMinute()+(saved.bed.toLocalDate().equals(morning)?1440:0);latency=saved.latency;wake=saved.wake.getHour()*60+saved.wake.getMinute();rise=saved.rise.getHour()*60+saved.rise.getMinute();nap=saved.nap;}else{bed=1410;latency=25;wake=450;rise=465;nap=2;}}
    void show(){
        activity.screen("昨晚睡得怎么样？");activity.text("记下大概时间就好，不必分秒不差。",14);
        activity.button("记录日期 · "+date(morning),()->{
            List<String> days=Arrays.asList(date(LocalDate.now())+" · 今天",date(LocalDate.now().minusDays(1))+" · 昨天");
            wheel("选择起床日期",days,morning.equals(LocalDate.now())?0:1,index->{morning=LocalDate.now().minusDays(index);load();show();});
        });
        activity.text("01  ·  准备入睡",13);
        bedButton=activity.button("",()->{
            List<String> options=new ArrayList<>();for(int m=1260;m<=1680;m+=5)options.add(date(morning.minusDays(m<1440?1:0))+"  "+time(m%1440));
            wheel("准备闭眼睡觉",options,(bed-1260)/5,i->{bed=1260+i*5;refresh();});
        });
        latencyButton=activity.button("",()->{List<String> options=new ArrayList<>();for(int m=0;m<=50;m+=5)options.add(m==0?"很快睡着（0 分钟）":m+" 分钟");wheel("大约多久睡着",options,latency/5,i->{latency=i*5;refresh();});});
        activity.text("02  ·  早晨醒来",13);
        wakeButton=activity.button("",()->{List<String> options=new ArrayList<>();for(int m=300;m<=660;m+=5)options.add(time(m));wheel("早上最后一次醒来",options,(wake-300)/5,i->{wake=300+i*5;if(rise<wake)rise=wake;refresh();});});
        riseButton=activity.button("",()->{List<String> options=new ArrayList<>();for(int m=wake;m<=660;m+=5)options.add(time(m));wheel("最后起床时间",options,(rise-wake)/5,i->{rise=wake+i*5;refresh();});});
        activity.text("醒来与起床同为 "+date(morning)+"。起床可以与醒来同时，也可以更晚。",12);
        activity.text("03  ·  白天补觉",13);
        napButton=activity.button("",()->wheel("白天有没有补觉",Arrays.asList(NAPS),nap,i->{nap=i;refresh();}));
        activity.text("早晨先填写当前情况；白天补觉后可重新打开当天记录修改。",12);
        result=activity.text("",21);
        activity.text("按你填写的时间估算，未扣除夜间清醒时间。补觉单独记录，不计入夜间效率。",12);
        activity.button("保存睡眠日记",()->{
            SleepRecord record=record();String error=record.validate(LocalDateTime.now());
            if(error!=null){Toast.makeText(activity,error,Toast.LENGTH_LONG).show();return;}
            Runnable save=()->{if(store.save(record)){Toast.makeText(activity,"日记已保存",Toast.LENGTH_SHORT).show();activity.savedDiary();}else Toast.makeText(activity,"未能保存，请稍后再试",Toast.LENGTH_LONG).show();};
            if(store.read(morning)!=null)new AlertDialog.Builder(activity).setTitle("更新这一天的记录？").setMessage("只替换 "+date(morning)+" 的日记，其他日期保留。").setPositiveButton("更新",(d,w)->save.run()).setNegativeButton("取消",null).show();else save.run();
        });
        activity.button("返回睡眠",activity::sleepHub);refresh();
    }
    private SleepRecord record(){return new SleepRecord(morning.minusDays(bed<1440?1:0).atTime((bed%1440)/60,bed%60),latency,morning.atTime(wake/60,wake%60),morning.atTime(rise/60,rise%60),nap);}
    private void refresh(){
        bedButton.setText("准备闭眼  ·  "+date(record().bed.toLocalDate())+"  "+time(bed%1440));
        latencyButton.setText("大约 "+latency+" 分钟睡着");wakeButton.setText("最后醒来  ·  "+time(wake));riseButton.setText("最后起床  ·  "+time(rise));napButton.setText(NAPS[nap]);
        SleepRecord r=record();result.setText("估算睡眠效率  "+r.efficiency()+"%\n睡眠 "+duration(r.sleepMinutes())+" · 在床 "+duration(r.bedMinutes()));
    }
    void wheel(String title,List<String> values,int selected,IntConsumer confirmed){
        NumberPicker picker=new NumberPicker(activity);picker.setMinValue(0);picker.setMaxValue(values.size()-1);picker.setDisplayedValues(values.toArray(new String[0]));picker.setWrapSelectorWheel(false);picker.setValue(Math.max(0,Math.min(selected,values.size()-1)));
        picker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        LinearLayout wrap=new LinearLayout(activity);wrap.setGravity(Gravity.CENTER);wrap.setPadding(NightUi.dp(activity,12),0,NightUi.dp(activity,12),0);
        wrap.addView(picker,new LinearLayout.LayoutParams(-1,NightUi.dp(activity,160)));
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(title).setView(wrap).setNegativeButton("取消",null).setPositiveButton("确认",(d,w)->{picker.clearFocus();confirmed.accept(picker.getValue());}).create();dialog.show();
    }
    static String date(LocalDate d){return d.format(DateTimeFormatter.ofPattern("M月d日"));}
    static String time(int m){return String.format(Locale.CHINA,"%02d:%02d",m/60,m%60);}
    static String duration(long m){return m/60+"小时"+m%60+"分";}
}
