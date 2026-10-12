package cn.xidengju.app;

import java.time.*;
import java.time.temporal.ChronoUnit;

public final class SleepRecord {
    public final LocalDateTime bed,wake,rise;
    public final int latency,nap;
    public SleepRecord(LocalDateTime bed,int latency,LocalDateTime wake,LocalDateTime rise,int nap){this.bed=bed;this.latency=latency;this.wake=wake;this.rise=rise;this.nap=nap;}
    public String validate(LocalDateTime now){
        if(bed==null||wake==null||rise==null)return "请补全日期和时间";
        int b=bed.getHour()*60+bed.getMinute(), w=wake.getHour()*60+wake.getMinute(), r=rise.getHour()*60+rise.getMinute();
        if(b%5!=0||w%5!=0||r%5!=0)return "请选择 5 分钟间隔的时间";
        if(!(b>=1260||b<=240))return "闭眼时间需在晚上 21:00 至次日 04:00";
        if(latency<0||latency>50||latency%5!=0)return "入睡用时需在 0 至 50 分钟";
        if(w<300||w>660||r<300||r>660)return "醒来和起床时间需在早晨 05:00 至 11:00";
        if(!wake.toLocalDate().equals(rise.toLocalDate())||rise.isBefore(wake))return "起床不能早于最后醒来，且须为同一天";
        if(!wake.isAfter(bed.plusMinutes(latency)))return "醒来时间必须晚于估算入睡时间，请检查日期";
        if(!wake.toLocalDate().equals(bed.toLocalDate().plusDays(b>=1260?1:0)))return "闭眼与醒来日期不属于同一夜，请检查日期";
        if(rise.isAfter(now))return "还没到这个起床时间，请起床后再记录";
        if(nap<0||nap>2)return "请选择白天补觉情况";
        return null;
    }
    public long sleepMinutes(){return ChronoUnit.MINUTES.between(bed.plusMinutes(latency),wake);}
    public long bedMinutes(){return ChronoUnit.MINUTES.between(bed,rise);}
    public int efficiency(){return (int)Math.round(sleepMinutes()*100.0/bedMinutes());}
    public String encode(){return bed+"|"+latency+"|"+wake+"|"+rise+"|"+nap;}
    public static SleepRecord decode(String value){try{String[] p=value.split("\\|");return new SleepRecord(LocalDateTime.parse(p[0]),Integer.parseInt(p[1]),LocalDateTime.parse(p[2]),LocalDateTime.parse(p[3]),Integer.parseInt(p[4]));}catch(RuntimeException e){return null;}}
}
