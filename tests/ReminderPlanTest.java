package cn.xidengju.app;

import java.time.*;

public final class ReminderPlanTest {
    static int checks;
    static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
    static ZonedDateTime at(String value){return LocalDateTime.parse(value).atZone(ZONE);}
    static long epoch(String value){return at(value).toInstant().toEpochMilli();}
    static void expect(boolean result,String message){checks++;if(!result)throw new AssertionError(message);}
    static void equals(long actual,String date,String message){expect(actual==epoch(date),message+" actual="+Instant.ofEpochMilli(actual));}
    public static void main(String[] args){
        long due=epoch("2026-09-23T23:00:00");
        equals(ReminderPlan.next(at("2026-09-23T20:00:00"),23,30,true),"2026-09-23T23:00:00","30 minute reminder");
        equals(ReminderPlan.next(at("2026-09-23T20:00:00"),23,30,false),"2026-09-23T23:30:00","bedtime");
        equals(ReminderPlan.next(at("2026-09-23T23:00:00"),23,30,true),"2026-09-23T23:00:00","exact boundary included");
        equals(ReminderPlan.next(at("2026-09-23T23:00:01"),23,30,true),"2026-09-24T23:00:00","new plan after boundary");
        equals(ReminderPlan.reconcile(at("2026-09-23T23:00:01"),23,30,true,true,due,0),"2026-09-23T23:00:00","resume must preserve unhandled due");
        equals(ReminderPlan.reconcile(at("2026-09-23T23:10:00"),23,30,true,true,due,0),"2026-09-23T23:00:00","10 minute grace");
        equals(ReminderPlan.reconcile(at("2026-09-23T23:12:00"),23,30,true,true,due,0),"2026-09-23T23:00:00","late reminder recovers current night");
        equals(ReminderPlan.reconcile(at("2026-09-24T00:12:00"),23,30,true,true,due,0),"2026-09-23T23:00:00","after midnight still current night");
        equals(ReminderPlan.reconcile(at("2026-09-24T07:00:00"),23,30,true,true,due,0),"2026-09-24T23:00:00","ended night does not recover");
        equals(ReminderPlan.reconcile(at("2026-09-23T23:50:00"),23,30,false,true,epoch("2026-09-23T23:30:00"),0),"2026-09-23T23:30:00","late bedtime still actionable");
        equals(ReminderPlan.reconcile(at("2026-09-23T23:00:00"),23,30,true,true,due,due),"2026-09-24T23:00:00","handled exact boundary never repeats");
        equals(ReminderPlan.reconcile(at("2026-09-23T23:00:01"),23,45,true,false,due,0),"2026-09-23T23:15:00","changed plan replaces old due");
        equals(ReminderPlan.next(at("2026-09-23T23:40:00"),0,15,true),"2026-09-23T23:45:00","cross midnight winddown");
        equals(ReminderPlan.next(at("2026-09-23T23:40:00"),0,15,false),"2026-09-24T00:15:00","cross midnight bedtime");
        expect(ReminderPlan.accepts(due,due,0,due),"alarm delivered");
        expect(!ReminderPlan.accepts(due,due,due,due+1),"duplicate ignored");
        expect(!ReminderPlan.accepts(due,due+60000,0,due),"stale alarm ignored");
        expect(!ReminderPlan.accepts(0,due,0,due),"legacy intent not accepted");
        expect(!ReminderPlan.accepts(due,due,0,due-1),"early alarm ignored");
        // Both kinds coexist: a delayed winddown cannot advance the pending bedtime.
        long bedtime=epoch("2026-09-23T23:30:00");
        equals(ReminderPlan.reconcile(at("2026-09-23T23:31:00"),23,30,false,true,bedtime,0),"2026-09-23T23:30:00","pending bedtime retained");
        // Reboot/permission recovery retain the same date; missing persisted state rebuilds.
        equals(ReminderPlan.reconcile(at("2026-09-23T22:50:00"),23,30,true,true,due,0),"2026-09-23T23:00:00","reboot restore");
        equals(ReminderPlan.reconcile(at("2026-09-23T22:50:00"),23,30,true,false,0,0),"2026-09-23T23:00:00","upgrade restore");
        equals(ReminderPlan.reconcile(at("2026-09-26T12:00:00"),23,30,true,true,due,0),"2026-09-26T23:00:00","recover after multiple missed days");
        // Daily recurrence without foreground activity, including month rollover.
        for(boolean winddown:new boolean[]{true,false}){
            ZonedDateTime now=at("2026-09-23T12:00:00");long scheduled=ReminderPlan.next(now,23,30,winddown);
            for(int day=0;day<40;day++){
                ZonedDateTime fire=Instant.ofEpochMilli(scheduled).atZone(ZONE);
                expect(fire.toLocalDate().equals(LocalDate.of(2026,9,23).plusDays(day)),"recurrence day "+day);
                expect(ReminderPlan.accepts(scheduled,scheduled,0,scheduled),"dispatch day "+day);
                long following=ReminderPlan.reconcile(fire,23,30,winddown,true,scheduled,scheduled);
                expect(following>scheduled,"advance day "+day);scheduled=following;
            }
        }
        // Exhaustive local minute coverage for the user's 23:30 plan.
        for(int minute=0;minute<1440;minute++){
            ZonedDateTime now=LocalDate.of(2026,9,23).atStartOfDay(ZONE).plusMinutes(minute);
            for(boolean winddown:new boolean[]{true,false}){
                long next=ReminderPlan.next(now,23,30,winddown);
                ZonedDateTime date=Instant.ofEpochMilli(next).atZone(ZONE);
                expect(next>=now.toInstant().toEpochMilli(),"future or now");
                expect(date.getHour()==23 && date.getMinute()==(winddown?0:30),"correct wall clock");
            }
        }
        ZoneId ny=ZoneId.of("America/New_York");
        for(String date:new String[]{"2026-03-08T00:00:00","2026-11-01T00:00:00"}){
            ZonedDateTime now=LocalDateTime.parse(date).atZone(ny);long next=ReminderPlan.next(now,23,30,true);
            expect(Instant.ofEpochMilli(next).atZone(ny).getHour()==23,"DST uses local wall clock");
        }
        System.out.println("PASS: "+checks+" reminder boundary, recovery, recurrence and stale-delivery checks");
    }
}
