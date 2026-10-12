package cn.xidengju.app;

import android.content.*;
import java.time.*;

final class NightFlow {
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("plan",0);}
    static long bed(Context c){SharedPreferences p=prefs(c);return NightFlowRules.bedtime(ZonedDateTime.now(),p.getInt("hour",23),p.getInt("minute",30),p.getInt("endMinute",420));}
    static long end(Context c,long bed){return NightFlowRules.end(bed,prefs(c).getInt("endMinute",420),ZoneId.systemDefault());}
    static String fingerprint(Context c){SharedPreferences p=prefs(c);return p.getInt("hour",23)+":"+p.getInt("minute",30)+"@"+ZoneId.systemDefault();}
    static long pause(Context c){SharedPreferences p=prefs(c);return NightFlowRules.blockPause(p.getLong("unlockUntil",0),p.getLong("delayUntil",0),p.getLong("nightExitUntil",0));}
    static boolean pending(Context c){SharedPreferences p=prefs(c);return p.getBoolean("enabled",false)&&p.getLong("flowBed",0)==bed(c)&&fingerprint(c).equals(p.getString("flowPlan",""))&&NightFlowRules.pending(System.currentTimeMillis(),p.getLong("promptUntil",0),pause(c));}
    static boolean finished(Context c,long bed){SharedPreferences p=prefs(c);return bed>0&&(p.getLong("completedBed",0)==bed||p.getLong("exitedBed",0)==bed);}
    static void present(Context c,String kind,long bed,boolean again){
        if("test".equals(kind)){prefs(c).edit().putLong("testPromptUntil",System.currentTimeMillis()+120000).apply();return;}
        if(finished(c,bed))return;
        prefs(c).edit().putLong("flowBed",bed).putString("flowPlan",fingerprint(c)).putLong("promptUntil",end(c,bed)).putString("promptKind",kind).putBoolean("promptRepeat",again).apply();
    }
    static boolean repeat(Context c,String task,int minutes){
        long now=System.currentTimeMillis(),bed=bed(c);if(!prefs(c).getBoolean("enabled",false)||task.trim().isEmpty()||now>=bed)return false;
        long due=NightFlowRules.repeat(now,minutes,bed);
        return saveRepeat(c,task,due,bed);
    }
    static boolean repeatAt(Context c,String task,long due,long originalBed){
        long now=System.currentTimeMillis();
        if(!prefs(c).getBoolean("enabled",false)||originalBed!=bed(c)||finished(c,originalBed)||task.trim().isEmpty()||!NightFlowRules.validReminderTime(now,due,originalBed,ZoneId.systemDefault()))return false;
        return saveRepeat(c,task,due,originalBed);
    }
    private static boolean saveRepeat(Context c,String task,long due,long bed){
        // The unchanged daily bedtime alarm covers a repeat clipped to bedtime.
        if(due<bed&&!ReminderReceiver.followup(c,due,bed,"winddown"))return false;
        if(due>=bed)ReminderReceiver.cancel(c,"followup");
        prefs(c).edit().putString("winddownTask",task.trim()).putLong("taskBed",bed).putLong("promptUntil",0).apply();
        ReminderLog.add(c,"已确认收尾任务；下次提醒 "+ReminderLog.time(due)+"（不记录任务内容）");return true;
    }
    static boolean delay(Context c){
        long now=System.currentTimeMillis(),bed=bed(c),end=end(c,bed);SharedPreferences p=prefs(c);
        if(!p.getBoolean("enabled",false)||!NightFlowRules.mayDelay(now,bed,end,p.getLong("delayedBed",0)))return false;
        long due=Math.min(now+900000,end);
        if(!ReminderReceiver.followup(c,due,bed,"bedtime"))return false;
        p.edit().putLong("delayedBed",bed).putLong("delayUntil",due).putLong("flowBed",bed).putString("flowPlan",fingerprint(c)).putLong("promptUntil",end).putString("promptKind","bedtime").apply();
        ReminderLog.add(c,"用户使用当晚唯一一次15分钟延迟，至 "+ReminderLog.time(due));return true;
    }
    static boolean emergency(Context c,String reason,boolean finish){
        long now=System.currentTimeMillis(),bed=bed(c),end=end(c,bed);SharedPreferences p=prefs(c);
        if(!finish&&p.getBoolean("enabled",false)){long due=Math.min(now+900000,end);if(!ReminderReceiver.followup(c,due,bed,due<bed?"winddown":"bedtime"))return false;}
        else ReminderReceiver.cancel(c,"followup");
        SharedPreferences.Editor e=p.edit().putLong("lastEmergency",now).putString("emergencyReason",reason).putLong("flowBed",bed).putString("flowPlan",fingerprint(c)).putLong("promptUntil",finish?0:end).putString("promptKind",now+900000<bed?"winddown":"bedtime");
        if(finish)e.putLong("exitedBed",bed).putLong("nightExitUntil",end);else e.putLong("unlockUntil",Math.min(now+900000,end));
        e.apply();ReminderLog.add(c,finish?"用户明确结束当晚计划；不改变日常开关":"用户紧急解锁15分钟；已安排恢复提醒");return true;
    }
    static void complete(Context c,long bed){
        long now=System.currentTimeMillis();SharedPreferences.Editor e=prefs(c).edit().putLong("lastRitual",now);
        if(now>=bed-1800000&&now<end(c,bed)){
            e.putLong("completedBed",bed).putLong("promptUntil",0).putLong("delayUntil",0).putLong("unlockUntil",0);
            ReminderReceiver.cancel(c,"followup");
        }
        e.apply();ReminderLog.add(c,"睡前仪式完成；娱乐应用门禁保留（日间试用不取消晚间提醒）");
    }
    static void clear(Context c){ReminderReceiver.cancel(c,"followup");prefs(c).edit().putLong("promptUntil",0).putLong("delayUntil",0).putLong("nightExitUntil",0).putLong("unlockUntil",0).remove("flowBed").remove("flowPlan").remove("completedBed").remove("exitedBed").remove("delayedBed").apply();}
}
