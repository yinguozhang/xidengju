package cn.xidengju.app;

import java.time.*;

/** Pure date/decision logic shared with the device scheduler and desktop tests. */
public final class ReminderPlan {
    public static final long GRACE=10*60*1000L;
    public static long next(ZonedDateTime now,int hour,int minute,boolean winddown){
        // Calculate local wall clock afresh for each date (including DST transitions).
        for(int day=0;day<4;day++){
            ZonedDateTime candidate=now.toLocalDate().plusDays(day).atTime(hour,minute).atZone(now.getZone());
            if(winddown)candidate=candidate.minusMinutes(30);
            if(!candidate.isBefore(now))return candidate.toInstant().toEpochMilli();
        }
        throw new IllegalStateException("No next reminder");
    }
    public static long reconcile(ZonedDateTime now,int hour,int minute,boolean winddown,
            boolean samePlan,long scheduled,long handled){
        return reconcile(now,hour,minute,winddown,samePlan,scheduled,handled,420);
    }
    public static long reconcile(ZonedDateTime now,int hour,int minute,boolean winddown,
            boolean samePlan,long scheduled,long handled,int endMinute){
        long time=now.toInstant().toEpochMilli();
        if(samePlan&&scheduled>0){
            if(scheduled!=handled&&scheduled>=time)return scheduled;
            long bed=NightFlowRules.bedtime(now,hour,minute,endMinute);
            long current=winddown?bed-1800000:bed;
            if(current<=time&&time<NightFlowRules.end(bed,endMinute,now.getZone())&&scheduled<=current&&handled!=current)return current;
        }
        long candidate=next(now,hour,minute,winddown);
        if(candidate==handled)return next(Instant.ofEpochMilli(handled+1).atZone(now.getZone()),hour,minute,winddown);
        return candidate;
    }
    public static boolean accepts(long expected,long scheduled,long handled,long now){
        return expected>0 && expected==scheduled && expected!=handled && expected<=now;
    }
}
