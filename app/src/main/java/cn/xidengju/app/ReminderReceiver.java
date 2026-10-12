package cn.xidengju.app;

import android.app.*;
import android.content.*;
import android.os.Build;
import android.Manifest;
import android.content.pm.PackageManager;
import java.time.*;

public class ReminderReceiver extends BroadcastReceiver {
    static final String CHANNEL="reminders";
    static final String[] DAILY={"winddown","bedtime"};
    static int id(String kind){return "winddown".equals(kind)?1:"bedtime".equals(kind)?2:"followup".equals(kind)?4:3;}
    static SharedPreferences state(Context c){return c.getSharedPreferences("reminderState",0);}
    static void channel(Context c){c.getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL,"睡前提醒",NotificationManager.IMPORTANCE_HIGH));}
    static boolean exact(Context c){return Build.VERSION.SDK_INT<31 || c.getSystemService(AlarmManager.class).canScheduleExactAlarms();}
    static String notificationsProblem(Context c){
        NotificationManager nm=c.getSystemService(NotificationManager.class);
        if(Build.VERSION.SDK_INT>=33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return "通知权限未授予";
        if(!nm.areNotificationsEnabled())return "通知总开关已关闭";
        NotificationChannel ch=nm.getNotificationChannel(CHANNEL);
        if(ch!=null && ch.getImportance()==NotificationManager.IMPORTANCE_NONE)return "睡前提醒分类已关闭";
        return "";
    }
    static PendingIntent alarm(Context c,String kind,long due){
        Intent i=new Intent(c,ReminderReceiver.class).setAction(kind).putExtra("due",due);
        return PendingIntent.getBroadcast(c,id(kind),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    static void cancel(Context c,String kind){
        c.getSystemService(AlarmManager.class).cancel(alarm(c,kind,0));
        state(c).edit().remove("due_"+kind).remove("mode_"+kind).remove("error_"+kind).commit();
    }
    static synchronized void schedule(Context c){
        channel(c);
        for(String kind:DAILY)scheduleKind(c,kind);
        SharedPreferences s=state(c);long test=s.getLong("due_test",0),now=System.currentTimeMillis();
        long followup=s.getLong("due_followup",0),bed=s.getLong("followupBed",0);
        if(followup>0&&followup!=s.getLong("handled_followup",0)){
            boolean stillUseful=bed==NightFlow.bed(c)&&now<NightFlow.end(c,bed);
            if(NightFlow.prefs(c).getBoolean("enabled",false)&&NightFlow.fingerprint(c).equals(s.getString("followupPlan",""))&&!NightFlow.finished(c,bed)&&now<NightFlow.end(c,bed)&&stillUseful)register(c,"followup",followup);
            else{ReminderLog.add(c,"再次提醒已过期或计划变化，取消旧排程");cancel(c,"followup");}
        }
        if(test>0 && test!=s.getLong("handled_test",0)){
            if(now-test<=ReminderPlan.GRACE)register(c,"test",test);
            else{ReminderLog.add(c,"后台测试过期，未收到触发；请检查强行停止、省电限制及权限");cancel(c,"test");}
        }
    }
    static synchronized void scheduleKind(Context c,String kind){
        SharedPreferences p=c.getSharedPreferences("plan",0),s=state(c);
        if(!p.getBoolean("enabled",false)){
            if(s.getLong("due_"+kind,0)>0)ReminderLog.add(c,label(kind)+"已取消：每日提醒关闭");
            cancel(c,kind);return;
        }
        ZonedDateTime now=ZonedDateTime.now();int h=p.getInt("hour",23),m=p.getInt("minute",30);
        String fingerprint=h+":"+m+"@"+now.getZone();
        boolean same=fingerprint.equals(s.getString("plan_"+kind,""));
        long old=s.getLong("due_"+kind,0),handled=s.getLong("handled_"+kind,0);
        long due=ReminderPlan.reconcile(now,h,m,"winddown".equals(kind),same,old,handled,p.getInt("endMinute",420));
        if(due<now.toInstant().toEpochMilli()&&due!=handled)ReminderLog.add(c,label(kind)+"恢复当晚未完成提醒："+ReminderLog.time(due));
        if(!s.edit().putLong("due_"+kind,due).putString("plan_"+kind,fingerprint).commit()){
            ReminderLog.add(c,label(kind)+"保存排程失败");return;
        }
        register(c,kind,due);
    }
    private static void register(Context c,String kind,long due){
        SharedPreferences s=state(c);long now=System.currentTimeMillis();
        // Replace the SAME occurrence, never blindly cancel and advance to tomorrow.
        long trigger=Math.max(now+1000,due);String mode="精确";
        try{
            AlarmManager am=c.getSystemService(AlarmManager.class);PendingIntent pi=alarm(c,kind,due);
            try{
                if(exact(c))am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,trigger,pi);
                else{mode="非精确（可能延迟）";am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,trigger,pi);}
            }catch(SecurityException changed){mode="非精确（权限变化）";am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,trigger,pi);}
            if(due!=s.getLong("registered_"+kind,0)||!mode.equals(s.getString("mode_"+kind,""))||!s.getString("error_"+kind,"").isEmpty())
                ReminderLog.add(c,label(kind)+"已提交系统："+ReminderLog.time(due)+" · "+mode);
            s.edit().putLong("registered_"+kind,due).putString("mode_"+kind,mode).remove("error_"+kind).commit();
        }catch(RuntimeException error){
            String message="提交系统失败："+error.getClass().getSimpleName();s.edit().putString("error_"+kind,message).commit();ReminderLog.add(c,label(kind)+message);
        }
    }
    static synchronized boolean test(Context c){
        channel(c);SharedPreferences s=state(c);long due=System.currentTimeMillis()+60000;
        if(!s.edit().putLong("due_test",due).remove("handled_test").commit())return false;
        ReminderLog.add(c,"用户安排一分钟后台测试");register(c,"test",due);
        return s.getString("error_test","").isEmpty();
    }
    static synchronized boolean followup(Context c,long due,long bed,String kind){
        SharedPreferences s=state(c);
        if(!s.edit().putLong("due_followup",due).putLong("followupBed",bed).putString("followupKind",kind).putString("followupPlan",NightFlow.fingerprint(c)).remove("handled_followup").commit())return false;
        register(c,"followup",due);boolean ok=s.getString("error_followup","").isEmpty();if(!ok)cancel(c,"followup");return ok;
    }
    static String label(String kind){return "winddown".equals(kind)?"收尾提醒":"bedtime".equals(kind)?"下线提醒":"followup".equals(kind)?"再次提醒":"后台测试";}
    @Override public void onReceive(Context c,Intent intent){
        String kind=intent.getAction();if(!"winddown".equals(kind)&&!"bedtime".equals(kind)&&!"test".equals(kind)&&!"followup".equals(kind))return;
        synchronized(ReminderReceiver.class){
            SharedPreferences s=state(c);long expected=intent.getLongExtra("due",0),now=System.currentTimeMillis();
            ReminderLog.add(c,label(kind)+"收到系统广播；原计划 "+ReminderLog.time(expected));
            if(expected==0){schedule(c);return;} // Old APK alarm, migrate without treating it as a new occurrence.
            if(!ReminderPlan.accepts(expected,s.getLong("due_"+kind,0),s.getLong("handled_"+kind,0),now)){
                ReminderLog.add(c,label(kind)+"忽略：已处理、计划已更改或尚未到点");return;
            }
            if(!s.edit().putLong("handled_"+kind,expected).commit())return;
            try{
                if(!"test".equals(kind)&&!c.getSharedPreferences("plan",0).getBoolean("enabled",false))return;
                long bed="followup".equals(kind)?s.getLong("followupBed",0):"winddown".equals(kind)?expected+1800000:expected;
                if("test".equals(kind)?now-expected>ReminderPlan.GRACE:!NightFlowRules.validSession(now,bed,NightFlow.bed(c),NightFlow.end(c,bed),true)){
                    ReminderLog.add(c,label(kind)+"已过期或不是当前一晚，跳过通知");return;
                }
                if("followup".equals(kind)&&!NightFlow.fingerprint(c).equals(s.getString("followupPlan",""))){ReminderLog.add(c,"忽略旧计划的再次提醒");return;}
                String stage="followup".equals(kind)?s.getString("followupKind","winddown"):kind;
                if("winddown".equals(stage)&&now>=bed)stage="bedtime";
                post(c,stage,now-expected,bed,"followup".equals(kind));
            }finally{
                if("test".equals(kind)||"followup".equals(kind))cancel(c,kind);else scheduleKind(c,kind);
            }
        }
    }
    static void post(Context c,String kind,long late,long bed,boolean again){
        if(!"test".equals(kind)&&(NightFlow.finished(c,bed)||System.currentTimeMillis()<NightFlow.pause(c)||System.currentTimeMillis()>=NightFlow.end(c,bed))){ReminderLog.add(c,label(kind)+"跳过：当晚已完成、已结束或正在宽限");return;}
        NightFlow.present(c,kind,bed,again);
        channel(c);String problem=notificationsProblem(c);
        if(!problem.isEmpty())ReminderLog.add(c,label(kind)+"未显示通知："+problem);
        else try{
            boolean bedtime="bedtime".equals(kind),test="test".equals(kind);
            PendingIntent open=PendingIntent.getActivity(c,id(kind),new Intent(c,MainActivity.class).setAction("open_"+kind+"_"+bed).putExtra("notificationBed",bed).putExtra("notificationPlan",NightFlow.fingerprint(c)).putExtra("flow",!test).putExtra("diagnostics",test),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            long left=Math.max(0,(bed-System.currentTimeMillis()+59999)/60000);
            String title=test?"后台定时测试已触发":bedtime?(again?"再次提醒：现在该下线了":"今晚已经到站"):again?"再次提醒：距离下线还有"+left+"分钟":"还有"+left+"分钟，准备收尾";
            if(late>=60000 && !test)title="补发："+title;
            c.getSystemService(NotificationManager.class).notify(id(kind),new Notification.Builder(c,CHANNEL).setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title).setContentText(test?"点击查看触发时间与诊断日志":bedtime?"写下明日计划，闭眼休息一小会":again?"刚才约定的收尾任务，现在该完成了":"确认还要做什么，选择下次提醒时间")
                .setContentIntent(open).setAutoCancel(true).setCategory(Notification.CATEGORY_REMINDER).build());
            ReminderLog.add(c,label(kind)+"通知已提交（不代表用户已看到），延迟 "+late/1000+" 秒");
        }catch(RuntimeException e){ReminderLog.add(c,label(kind)+"通知提交异常："+e.getClass().getSimpleName());}
        SharedPreferences p=c.getSharedPreferences("plan",0);
        if(p.getBoolean("strongReminders",false)){
            ReminderLog.add(c,BlockingService.connected?"已请求分阶段覆盖页（安全窗口/锁屏时不覆盖）":"收尾覆盖未连接，已回退通知");
        }
    }
}
