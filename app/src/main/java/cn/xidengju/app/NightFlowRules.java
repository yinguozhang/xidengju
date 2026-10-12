package cn.xidengju.app;

import java.time.*;

/** Pure rules for a night identified by its original bedtime, not the calendar day. */
public final class NightFlowRules {
    public static boolean validSession(long now,long originalBed,long currentBed,long end,boolean samePlan){return samePlan&&originalBed>0&&originalBed==currentBed&&now>=originalBed-1800000&&now<end;}
    public static long[] reminderTimes(long now,long bed,ZoneId zone){
        java.util.ArrayList<Long> values=new java.util.ArrayList<>();
        ZonedDateTime tick=Instant.ofEpochMilli(now).atZone(zone).withSecond(0).withNano(0).plusMinutes(1);
        while(tick.getMinute()%5!=0)tick=tick.plusMinutes(1);
        for(long time=tick.toInstant().toEpochMilli();time<bed;time+=300000)values.add(time);
        if(bed>now)values.add(bed);
        long[] result=new long[values.size()];for(int i=0;i<result.length;i++)result[i]=values.get(i);return result;
    }
    public static boolean validReminderTime(long now,long due,long bed,ZoneId zone){
        if(due<=now||due>bed)return false;if(due==bed)return true;
        ZonedDateTime time=Instant.ofEpochMilli(due).atZone(zone);return time.getMinute()%5==0&&time.getSecond()==0&&time.getNano()==0;
    }
    public static long end(long bed,int endMinute,ZoneId zone){
        ZonedDateTime bedtime=Instant.ofEpochMilli(bed).atZone(zone);
        ZonedDateTime end=bedtime.toLocalDate().atTime(endMinute/60,endMinute%60).atZone(zone);
        if(!end.isAfter(bedtime))end=bedtime.toLocalDate().plusDays(1).atTime(endMinute/60,endMinute%60).atZone(zone);
        return end.toInstant().toEpochMilli();
    }
    public static long bedtime(ZonedDateTime now,int hour,int minute,int endMinute){
        long time=now.toInstant().toEpochMilli();
        long previous=now.toLocalDate().minusDays(1).atTime(hour,minute).atZone(now.getZone()).toInstant().toEpochMilli();
        if(time<end(previous,endMinute,now.getZone()))return previous;
        long today=now.toLocalDate().atTime(hour,minute).atZone(now.getZone()).toInstant().toEpochMilli();
        if(time<end(today,endMinute,now.getZone()))return today;
        return now.toLocalDate().plusDays(1).atTime(hour,minute).atZone(now.getZone()).toInstant().toEpochMilli();
    }
    public static long repeat(long now,int minutes,long bed){
        if(minutes!=15&&minutes!=20&&minutes!=25)throw new IllegalArgumentException("Use 15/20/25 minutes");
        return Math.min(now+minutes*60000L,bed);
    }
    public static boolean mayDelay(long now,long bed,long end,long usedBed){return now>=bed&&now+900000<=end&&usedBed!=bed;}
    public static boolean pending(long now,long until,long pauseUntil){return now<until&&now>=pauseUntil;}
    public static long blockPause(long emergency,long delay,long nightExit){return Math.max(emergency,Math.max(delay,nightExit));}
}
