package cn.xidengju.app;

import java.time.*;

public class NightFlowRulesTest {
    static int checks;
    static void expect(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static ZonedDateTime at(String value){return ZonedDateTime.parse(value+"+08:00[Asia/Shanghai]");}
    public static void main(String[] args){
        long before=NightFlowRules.bedtime(at("2026-09-29T23:00:00"),23,30,420);
        long afterMidnight=NightFlowRules.bedtime(at("2026-09-30T00:30:00"),23,30,420);
        long nextEvening=NightFlowRules.bedtime(at("2026-09-30T08:00:00"),23,30,420);
        expect(before==afterMidnight,"same night survives midnight");
        expect(nextEvening-before==86400000L,"morning past end advances to next night");
        expect(NightFlowRules.end(before,420,ZoneId.of("Asia/Shanghai"))==at("2026-09-30T07:00:00").toInstant().toEpochMilli(),"morning end date");
        long afterBed=NightFlowRules.bedtime(at("2026-09-29T23:45:00"),23,30,420);
        expect(afterBed==before,"night remains current after bedtime");
        long midnight=NightFlowRules.bedtime(at("2026-09-29T23:50:00"),0,15,420);
        expect(midnight==at("2026-09-30T00:15:00").toInstant().toEpochMilli(),"bedtime after midnight");
        expect(NightFlowRules.repeat(before-300000L,15,before)==before,"repeat never moves bedtime");
        expect(NightFlowRules.repeat(before-1800000L,20,before)==before-600000L,"repeat interval is respected");
        for(int minutes:new int[]{15,20,25})expect(NightFlowRules.repeat(before-1800000L,minutes,before)<=before,"repeat option bounded: "+minutes);
        boolean rejected=false;try{NightFlowRules.repeat(0,5,before);}catch(IllegalArgumentException expected){rejected=true;}expect(rejected,"unapproved reminder interval rejected");
        long end=NightFlowRules.end(before,420,ZoneId.of("Asia/Shanghai"));
        ZoneId zone=ZoneId.of("Asia/Shanghai");
        long[] late=NightFlowRules.reminderTimes(before-600000,before,zone);
        expect(late.length==2&&late[0]==before-300000&&late[1]==before,"23:20 offers 23:25 and 23:30 only");
        expect(NightFlowRules.reminderTimes(before-180000,before,zone).length==1,"23:27 offers bedtime only");
        expect(NightFlowRules.reminderTimes(before,before,zone).length==0,"bedtime has no followup choices");
        expect(!NightFlowRules.validReminderTime(before-600000,before+300000,before,zone),"cannot push bedtime later");
        expect(!NightFlowRules.validSession(before+86400000,before,before+86400000,end,true),"stale notice cannot start next night");
        expect(!NightFlowRules.validSession(before,before,before,end,false),"changed plan expires notice");
        expect(NightFlowRules.validSession(before-600000,before,before,end,true),"late same night notice valid");
        for(int remaining=1;remaining<=1800000;remaining+=1000){long now=before-remaining;for(long due:NightFlowRules.reminderTimes(now,before,zone))expect(NightFlowRules.validReminderTime(now,due,before,zone),"all live choices future and bounded");}
        expect(NightFlowRules.mayDelay(before,before,end,0),"one delay allowed at bedtime");
        expect(!NightFlowRules.mayDelay(before,before,end,before),"second delay same night denied");
        expect(!NightFlowRules.mayDelay(end,before,end,0),"no delay after night ended");
        expect(!NightFlowRules.mayDelay(end-300000,before,end,0),"no partial ordinary delay near morning end");
        expect(!NightFlowRules.pending(before+1000,end,before+900000),"pause hides prompt");
        expect(NightFlowRules.pending(before+900001,end,before+900000),"prompt returns after pause");
        expect(NightFlowRules.blockPause(100,200,300)==300,"longest pause wins");
        System.out.println("PASS: "+checks+" night flow boundary checks");
    }
}
