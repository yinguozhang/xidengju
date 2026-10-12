package cn.xidengju.app;

import android.content.*;
import android.app.*;
import java.util.*;
import java.time.*;

/** Executes production Receiver/Restore/Log against in-memory Android API doubles.
 * Does not simulate OEM scheduling or prove delivery on a phone. */
public class ReminderReceiverTest {
    static int checks;
    static void expect(boolean result,String message){checks++;if(!result)throw new AssertionError(message);}
    static class Memory implements SharedPreferences {
        Map<String,Object> data=new HashMap<>();
        public long getLong(String k,long d){return (long)data.getOrDefault(k,d);}
        public int getInt(String k,int d){return (int)data.getOrDefault(k,d);}
        public boolean getBoolean(String k,boolean d){return (boolean)data.getOrDefault(k,d);}
        public String getString(String k,String d){return (String)data.getOrDefault(k,d);}
        public Editor edit(){return new Editor(){Map<String,Object> update=new HashMap<>();
            public Editor putLong(String k,long v){update.put(k,v);return this;}public Editor putInt(String k,int v){update.put(k,v);return this;}
            public Editor putBoolean(String k,boolean v){update.put(k,v);return this;}public Editor putString(String k,String v){update.put(k,v);return this;}
            public Editor remove(String k){update.put(k,null);return this;}public boolean commit(){update.forEach((k,v)->{if(v==null)data.remove(k);else data.put(k,v);});return true;}public void apply(){commit();}};
        }
    }
    static class Device extends Context {
        Map<String,Memory> prefs=new HashMap<>();AlarmManager alarms=new AlarmManager();NotificationManager notifications=new NotificationManager();int permission=0;
        public SharedPreferences getSharedPreferences(String name,int mode){return prefs.computeIfAbsent(name,k->new Memory());}
        public <T>T getSystemService(Class<T> type){return type.cast(type==AlarmManager.class?alarms:notifications);}
        public int checkSelfPermission(String name){return permission;}
    }
    static Intent event(Device c,String kind,long due){return new Intent(c,ReminderReceiver.class).setAction(kind).putExtra("due",due);}
    static Device device(){return flowDevice(30);}
    static Device flowDevice(int offsetMinutes){
        Device c=new Device();LocalTime bed=LocalTime.now().plusMinutes(offsetMinutes),end=bed.plusHours(2);
        c.getSharedPreferences("plan",0).edit().putBoolean("enabled",true).putBoolean("blocking",true).putBoolean("strongReminders",true)
            .putInt("hour",bed.getHour()).putInt("minute",bed.getMinute()).putInt("endMinute",end.getHour()*60+end.getMinute()).commit();
        ReminderReceiver.schedule(c);return c;
    }
    public static void main(String[] args){
        Device c=device();SharedPreferences s=ReminderReceiver.state(c),p=c.getSharedPreferences("plan",0);
        expect(c.alarms.alarms.size()==2,"two alarms initially scheduled");
        long old=s.getLong("due_winddown",0),bed=s.getLong("due_bedtime",0);
        ReminderReceiver.schedule(c);expect(s.getLong("due_winddown",0)==old,"resume retains future occurrence");expect(c.alarms.cancels==0,"resume never cancels active daily alarms");
        long overdue=bed-1800000;s.edit().putLong("due_winddown",overdue).commit();
        ReminderReceiver.schedule(c);expect(s.getLong("due_winddown",0)==overdue,"overdue retained on resume");
        ReminderReceiver receiver=new ReminderReceiver();Intent delivery=event(c,"winddown",overdue);
        receiver.onReceive(c,delivery);expect(c.notifications.posts==1,"overdue notification posted");expect(s.getLong("due_winddown",0)>System.currentTimeMillis(),"self recurrence");
        Intent opened=c.notifications.last.open.intent;
        expect(opened.getLongExtra("notificationBed",0)==overdue+1800000,"notification carries original night identity");
        expect(NightFlow.fingerprint(c).equals(opened.getStringExtra("notificationPlan")),"notification carries original plan fingerprint");
        expect(opened.getAction().endsWith("_"+(overdue+1800000)),"pending intent action unique to night");
        expect(s.getLong("due_bedtime",0)==bed,"other occurrence untouched by receiver");
        receiver.onReceive(c,delivery);expect(c.notifications.posts==1,"duplicate suppressed");
        receiver.onReceive(c,event(c,"winddown",overdue-1));expect(c.notifications.posts==1,"stale suppressed");
        expect(ReminderReceiver.test(c),"test registration");long test=s.getLong("due_test",0);
        expect(test>System.currentTimeMillis()+50000,"test is future, not direct notification");expect(c.notifications.posts==1,"test not sent immediately");
        expect(s.getLong("due_bedtime",0)==bed,"test does not alter daily alarm");
        long testDue=System.currentTimeMillis()-1000;s.edit().putLong("due_test",testDue).commit();receiver.onReceive(c,event(c,"test",testDue));
        expect(c.notifications.posts==2,"test delivered by receiver");expect(s.getLong("due_test",0)==0,"test not recurring");expect(s.getLong("due_bedtime",0)==bed,"test completion preserves plan");
        // State survives a process replacement; system alarm registry may need restoring.
        Device replacement=new Device();replacement.prefs=c.prefs;
        new RestoreReceiver().onReceive(replacement,new Intent(replacement,RestoreReceiver.class).setAction("android.intent.action.BOOT_COMPLETED"));
        expect(replacement.alarms.alarms.size()==2,"reboot rebuilds registration from persistent prefs");expect(ReminderReceiver.state(replacement).getLong("due_bedtime",0)==bed,"reboot retains occurrence");
        Device denied=device();denied.permission=-1;SharedPreferences d=ReminderReceiver.state(denied);d.edit().putLong("due_winddown",overdue).commit();receiver.onReceive(denied,event(denied,"winddown",overdue));
        expect(denied.notifications.posts==0,"notification permission denial respected");expect(d.getLong("due_winddown",0)>System.currentTimeMillis(),"denied notification still renews alarm");expect(ReminderLog.read(denied).contains("未授予"),"denial logged");
        Device channelBlocked=device();channelBlocked.notifications.channels.get("reminders").importance=0;ReminderReceiver.state(channelBlocked).edit().putLong("due_winddown",overdue).commit();receiver.onReceive(channelBlocked,event(channelBlocked,"winddown",overdue));
        expect(channelBlocked.notifications.posts==0,"channel block respected");expect(ReminderLog.read(channelBlocked).contains("分类已关闭"),"channel failure logged");
        Device throwing=device();throwing.notifications.fail=true;ReminderReceiver.state(throwing).edit().putLong("due_winddown",overdue).commit();receiver.onReceive(throwing,event(throwing,"winddown",overdue));expect(ReminderReceiver.state(throwing).getLong("due_winddown",0)>System.currentTimeMillis(),"notification exception still renews");
        Device inexact=device();inexact.alarms.exact=false;ReminderReceiver.schedule(inexact);expect(inexact.alarms.fallbacks==2,"missing exact permission uses fallback");expect(ReminderReceiver.state(inexact).getString("mode_winddown","").contains("非精确"),"fallback surfaced");
        Device race=device();race.alarms.denyExact=true;ReminderReceiver.schedule(race);expect(race.alarms.fallbacks==2,"permission race fallback");
        Device failure=device();failure.alarms.fail=true;ReminderReceiver.schedule(failure);expect(!ReminderReceiver.state(failure).getString("error_winddown","").isEmpty(),"schedule error surfaced");failure.alarms.fail=false;ReminderReceiver.schedule(failure);expect(ReminderReceiver.state(failure).getString("error_winddown","").isEmpty(),"schedule error cleared after recovery");
        Device expired=device();long stale=System.currentTimeMillis()-ReminderPlan.GRACE-1000;ReminderReceiver.state(expired).edit().putLong("due_winddown",stale).commit();receiver.onReceive(expired,event(expired,"winddown",stale));expect(expired.notifications.posts==0,"wrong night identity skipped");expect(ReminderReceiver.state(expired).getLong("due_winddown",0)==NightFlow.bed(expired)-1800000,"recover actual current occurrence after invalid delivery");
        Device lateWinddown=flowDevice(18);long lateBed=NightFlow.bed(lateWinddown),lateDue=lateBed-1800000;
        ReminderReceiver.state(lateWinddown).edit().putLong("due_winddown",lateDue).remove("handled_winddown").commit();ReminderReceiver.schedule(lateWinddown);
        expect(ReminderReceiver.state(lateWinddown).getLong("due_winddown",0)==lateDue,"12 minute late winddown restored");
        receiver.onReceive(lateWinddown,event(lateWinddown,"winddown",lateDue));expect(lateWinddown.notifications.posts==1,"12 minute late winddown delivered");
        Device lateBedtime=flowDevice(-20);long bedLate=NightFlow.bed(lateBedtime);
        ReminderReceiver.state(lateBedtime).edit().putLong("due_bedtime",bedLate).remove("handled_bedtime").commit();receiver.onReceive(lateBedtime,event(lateBedtime,"bedtime",bedLate));
        expect(lateBedtime.notifications.posts==1&&NightFlow.pending(lateBedtime),"20 minute late bedtime recovers active night");
        Device winding=flowDevice(30);SharedPreferences windingPrefs=winding.getSharedPreferences("plan",0),windingState=ReminderReceiver.state(winding);
        long windingBed=NightFlow.bed(winding);expect(windingBed>System.currentTimeMillis()+28*60000,"upcoming bedtime identified");
        long absolute=NightFlowRules.reminderTimes(System.currentTimeMillis(),windingBed,ZoneId.systemDefault())[0];
        expect(NightFlow.repeatAt(winding,"保存进度",absolute,windingBed),"absolute future reminder registered");
        expect(windingState.getLong("due_followup",0)==absolute,"selected absolute time not offset from delivery");
        expect(!NightFlow.repeatAt(winding,"保存进度",windingBed+300000,windingBed),"absolute repeat after bedtime rejected");
        expect(!NightFlow.repeatAt(winding,"保存进度",absolute,windingBed-86400000),"stale session cannot create repeat");
        expect(!NightFlow.repeat(winding,"  ",20),"empty winddown task cannot acknowledge reminder");
        expect(NightFlow.repeat(winding,"最后一局",20),"confirmed task schedules follow-up");
        long firstFollowup=windingState.getLong("due_followup",0);
        expect(firstFollowup>System.currentTimeMillis()+19*60000&&firstFollowup<windingBed,"follow-up before bedtime");
        expect(winding.alarms.alarms.containsKey("followup"),"system follow-up alarm registered");
        expect(windingPrefs.getLong("promptUntil",-1)==0,"confirmed task removes overlay until repeat");
        Device rebootFlow=new Device();rebootFlow.prefs=winding.prefs;new RestoreReceiver().onReceive(rebootFlow,new Intent(rebootFlow,RestoreReceiver.class).setAction("android.intent.action.BOOT_COMPLETED"));
        expect(rebootFlow.alarms.alarms.containsKey("followup"),"follow-up restored after reboot");
        long dueAgain=System.currentTimeMillis()-1000;windingState.edit().putLong("due_followup",dueAgain).commit();receiver.onReceive(winding,event(winding,"followup",dueAgain));
        expect(windingPrefs.getLong("promptUntil",0)>System.currentTimeMillis()+30*60000,"follow-up prompt lasts through night, not two minutes");
        expect("winddown".equals(windingPrefs.getString("promptKind","")),"follow-up stays in winddown phase");
        expect(windingPrefs.getBoolean("promptRepeat",false),"follow-up has distinct copy state");
        expect(windingState.getLong("due_followup",0)==0,"one-shot follow-up cleared after delivery");
        expect(!ReminderLog.read(winding).contains("最后一局"),"task content absent from diagnostic log");
        NightFlow.present(winding,"test",0,false);expect("winddown".equals(windingPrefs.getString("promptKind","")),"test leaves daily stage intact");
        windingPrefs.edit().putInt("minute",(windingPrefs.getInt("minute",0)+1)%60).commit();ReminderReceiver.schedule(winding);
        expect(windingState.getLong("due_followup",0)==0,"plan change cancels old follow-up");
        expect(!NightFlow.pending(winding),"old prompt cannot attach to edited plan");
        Device stillUseful=flowDevice(30);long usefulBed=NightFlow.bed(stillUseful),usefulDue=System.currentTimeMillis()-11*60000;
        expect(ReminderReceiver.followup(stillUseful,usefulDue,usefulBed,"winddown"),"late follow-up registered for recovery test");
        Device recoveredUseful=new Device();recoveredUseful.prefs=stillUseful.prefs;new RestoreReceiver().onReceive(recoveredUseful,new Intent(recoveredUseful,RestoreReceiver.class).setAction("android.intent.action.BOOT_COMPLETED"));
        expect(recoveredUseful.alarms.alarms.containsKey("followup"),"late but useful follow-up restored before bedtime");
        receiver.onReceive(stillUseful,event(stillUseful,"followup",usefulDue));
        expect(stillUseful.notifications.posts==1,"late follow-up still arrives when bedtime is ahead");
        Device delayed=flowDevice(-1);SharedPreferences delayPrefs=delayed.getSharedPreferences("plan",0),delayState=ReminderReceiver.state(delayed);long delayBed=NightFlow.bed(delayed);
        expect(delayBed<System.currentTimeMillis(),"current night identified after bedtime");
        expect(NightFlow.delay(delayed),"first 15-minute delay accepted");
        expect(!NightFlow.delay(delayed),"second 15-minute delay same night refused");
        expect(!NightFlow.pending(delayed),"delay suppresses reminder until due");
        expect(NightFlow.pause(delayed)>=System.currentTimeMillis()+14*60000,"delay also pauses entertainment gate");
        long delayedDue=System.currentTimeMillis()-1000;delayState.edit().putLong("due_followup",delayedDue).commit();delayPrefs.edit().putLong("delayUntil",delayedDue).commit();receiver.onReceive(delayed,event(delayed,"followup",delayedDue));
        expect(NightFlow.pending(delayed),"bedtime prompt returns after delay");
        expect("bedtime".equals(delayPrefs.getString("promptKind","")),"delay returns to bedtime phase");
        NightFlow.complete(delayed,delayBed);expect(delayPrefs.getLong("completedBed",0)==delayBed,"ritual marks the correct night complete");
        expect(!NightFlow.pending(delayed),"completion clears reminder");
        expect(delayPrefs.getBoolean("blocking",false),"completion does not disable entertainment gate");
        int postsAfterCompletion=delayed.notifications.posts;delayState.edit().putLong("due_bedtime",delayBed).remove("handled_bedtime").commit();receiver.onReceive(delayed,event(delayed,"bedtime",delayBed));
        expect(delayed.notifications.posts==postsAfterCompletion,"completed night cannot be reminded again");
        Device earlyFinish=flowDevice(-1);long earlyBed=NightFlow.bed(earlyFinish);
        expect(NightFlow.delay(earlyFinish),"delay before ritual can be active");NightFlow.complete(earlyFinish,earlyBed);
        expect(NightFlow.pause(earlyFinish)<System.currentTimeMillis(),"ritual ends the unused grace and restores entertainment gate");
        Device escaped=flowDevice(-1);SharedPreferences escapePrefs=escaped.getSharedPreferences("plan",0);long escapeBed=NightFlow.bed(escaped);
        expect(NightFlow.emergency(escaped,"联系家人",true),"night exit accepted");
        expect(escapePrefs.getLong("exitedBed",0)==escapeBed,"exit records this night only");
        expect(escapePrefs.getBoolean("enabled",false)&&escapePrefs.getBoolean("blocking",false),"exit preserves daily switches");
        expect(NightFlow.pause(escaped)>System.currentTimeMillis(),"exit pauses tonight's entertainment gate");
        expect(!ReminderLog.read(escaped).contains("联系家人"),"reason not copied into diagnostics");
        Device temporary=flowDevice(-1);SharedPreferences tempPrefs=temporary.getSharedPreferences("plan",0);
        expect(NightFlow.emergency(temporary,"学习",false),"temporary emergency schedules recovery");
        expect(ReminderReceiver.state(temporary).getLong("due_followup",0)>System.currentTimeMillis(),"temporary emergency has a persisted follow-up");
        expect(!NightFlow.pending(temporary),"temporary emergency pauses overlay");
        expect(tempPrefs.getLong("delayedBed",0)==0,"emergency does not spend the one ordinary delay");
        p.edit().putBoolean("enabled",false).commit();ReminderReceiver.schedule(c);expect(!c.alarms.alarms.containsKey("winddown")&&!c.alarms.alarms.containsKey("bedtime"),"explicit disable cancels");
        for(int i=0;i<100;i++)ReminderLog.add(c,"entry "+i);expect(ReminderLog.read(c).split("\n").length==80,"log bounded");
        System.out.println("PASS: "+checks+" production receiver integration checks (Android API doubles, not a device)");
    }
}
