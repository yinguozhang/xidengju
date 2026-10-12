package cn.xidengju.app;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityEvent;
import android.widget.*;
import java.time.*;
import java.util.*;

public class BlockingService extends AccessibilityService {
    static volatile boolean connected=false;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private String foreground="";
    private View overlay;
    private boolean reminderOverlay;
    private String overlayStage="";
    private long retryAfter;
    private Set<String> safe;
    private SharedPreferences prefs;
    private final Runnable tick=new Runnable(){public void run(){evaluate();handler.postDelayed(this,1000);}};
    @Override protected void onServiceConnected(){
        connected=true;prefs=getSharedPreferences("plan",0);safe=AppProtection.packages(this);
        ReminderLog.add(this,"拦截服务已连接");ReminderReceiver.schedule(this);handler.post(tick);
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        if(event.getEventType()!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.getPackageName()==null)return;
        String pkg=event.getPackageName().toString();
        // Ignore events emitted by our own overlay; the underlying app stays selected.
        if(pkg.equals(getPackageName()) && overlay!=null && !"cn.xidengju.app.MainActivity".contentEquals(event.getClassName()==null?"":event.getClassName()))return;
        foreground=pkg;evaluate();
    }
    private void evaluate(){
        if(prefs==null)return;
        long now=System.currentTimeMillis(); LocalTime time=LocalTime.now();
        boolean locked=getSystemService(KeyguardManager.class).isKeyguardLocked() || !getSystemService(PowerManager.class).isInteractive();
        boolean block=BlockRules.shouldBlock(prefs.getBoolean("blocking",false),prefs.getStringSet("apps",Collections.emptySet()).contains(foreground),
            AppProtection.isProtected(this,foreground,safe),now,NightFlow.pause(this),prefs.getLong("testUntil",0),time.getHour()*60+time.getMinute(),
            prefs.getInt("hour",23)*60+prefs.getInt("minute",30),prefs.getInt("endMinute",420));
        boolean prompt=prefs.getBoolean("strongReminders",false)&&(NightFlow.pending(this)||now<prefs.getLong("testPromptUntil",0))
            &&!AppProtection.isProtected(this,foreground,safe);
        if(!locked && now>=retryAfter && (block||prompt))showGate(!block);else removeGate();
    }
    private void showGate(boolean reminder){
        long now=System.currentTimeMillis(),bed=NightFlow.bed(this);
        String kind=NightFlow.pending(this)?(now>=bed?"bedtime":"winddown"):"test";
        boolean completed=NightFlow.finished(this,bed);
        boolean finalMinutes=bed-now<=900000;
        String stage=reminder?kind+(finalMinutes?"Final":"")+(prefs.getBoolean("promptRepeat",false)?"Again":"")+(now/60000):completed?"completed":"gate";
        if(overlay!=null && reminderOverlay==reminder&&stage.equals(overlayStage))return;
        removeGate();reminderOverlay=reminder;
        overlayStage=stage;
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setGravity(Gravity.CENTER);
        int pad=(int)(24*getResources().getDisplayMetrics().density);layout.setPadding(pad,pad,pad,pad);layout.setBackgroundColor(Color.rgb(16,25,35));
        layout.addView(NightUi.art(this,"otter-ready.png",130));
        boolean test=reminder&&"test".equals(kind),winddown=reminder&&"winddown".equals(kind);
        boolean again=prefs.getBoolean("promptRepeat",false);
        String heading=test?"后台提醒测试\n已成功触发。":winddown?(again?"再次提醒：\n该完成收尾任务了。":"最后这段时间，\n准备怎么收尾？"):completed?"今晚已下线。\n明天仍然可以继续。":"到下线时间了。\n把今天结束掉。";
        TextView title=new TextView(this);title.setText(heading);title.setTextSize(28);title.setTextColor(Color.WHITE);title.setGravity(Gravity.CENTER);layout.addView(title);
        String task=prefs.getLong("taskBed",0)==bed?prefs.getString("winddownTask",""):"";
        TextView caption=new TextView(this);caption.setText(test?"这是测试，不改变今晚的日常计划。":winddown?(again&&!task.isEmpty()?"你刚才决定："+task+"。\n":"")+"距离下线还有 "+Math.max(1,(bed-now+59999)/60000)+" 分钟。选好下次提醒，再回去继续。":completed?"仪式已完成，娱乐应用仍保持门禁。\n电话、必要应用和系统导航仍可使用。":"先写明日计划，再闭眼休息90秒。\n紧急情况有退出入口，必要应用不受影响。");caption.setTextColor(Color.LTGRAY);caption.setPadding(0,pad,0,pad);layout.addView(caption);
        if(test)addButton(layout,"结束这次测试",()->{prefs.edit().putLong("testPromptUntil",0).apply();removeGate();});
        else if(winddown){
            addButton(layout,finalMinutes?"只剩最后几分钟 · 选择收尾时刻":"确认收尾任务、选择提醒时刻",()->openApp("winddown"));
            addButton(layout,"现在下线，开始睡前仪式",()->openApp("ritual"));
        }else if(!completed){
            addButton(layout,"开始睡前仪式",()->openApp("ritual"));
            if(NightFlowRules.mayDelay(now,bed,NightFlow.end(this,bed),prefs.getLong("delayedBed",0)))addButton(layout,"今晚延迟 15 分钟 · 仅一次",()->{if(NightFlow.delay(this))removeGate();else Toast.makeText(this,"延迟未安排成功，请查看权限与日志",Toast.LENGTH_LONG).show();});
        }
        if(!test)addButton(layout,"紧急情况 / 结束当晚计划",()->openApp("emergency"));
        if(!reminder)addButton(layout,"回到桌面",()->{performGlobalAction(GLOBAL_ACTION_HOME);foreground="";removeGate();});
        if(!reminder){
            if(prefs.getLong("testUntil",0)>System.currentTimeMillis())addButton(layout,"停止本次测试，保留日常计划",()->{prefs.edit().putLong("testUntil",0).apply();performGlobalAction(GLOBAL_ACTION_HOME);foreground="";removeGate();ReminderLog.add(this,"用户停止拦截测试；保留日常拦截开关");});
            addButton(layout,"管理拦截设置",()->openApp("blocking"));
        }
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(layout);
        WindowManager.LayoutParams params=new WindowManager.LayoutParams(-1,-1,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.OPAQUE);
        try{getSystemService(WindowManager.class).addView(scroll,params);overlay=scroll;if(reminder)ReminderLog.add(this,"收尾覆盖页已显示");prefs.edit().remove("blockError").apply();}
        catch(RuntimeException failure){retryAfter=System.currentTimeMillis()+30000;prefs.edit().putString("blockError","覆盖页显示失败，30秒后重试；日常开关未关闭").apply();ReminderLog.add(this,"覆盖显示失败："+failure.getClass().getSimpleName()+"；保留设置，稍后重试");}
    }
    private void confirmTask(String task,int minutes){
        if(NightFlow.repeat(this,task,minutes)){removeGate();Toast.makeText(this,"已安排再次提醒；不会越过下线时刻",Toast.LENGTH_LONG).show();}
        else{removeGate();Toast.makeText(this,"已到下线时间，或排程失败；请检查计划与权限",Toast.LENGTH_LONG).show();}
    }
    private void openApp(String destination){
        foreground="";removeGate();startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra(destination,true));
    }
    private void addButton(LinearLayout layout,String label,Runnable action){Button b=new Button(this);b.setText(label);NightUi.styleButton(b,label.equals("开始睡前仪式"));b.setOnClickListener(v->action.run());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=NightUi.dp(this,10);layout.addView(b,lp);}
    private void removeGate(){if(overlay!=null){try{getSystemService(WindowManager.class).removeView(overlay);}catch(IllegalArgumentException ignored){}overlay=null;}}
    @Override public void onInterrupt(){removeGate();}
    @Override public void onDestroy(){connected=false;handler.removeCallbacksAndMessages(null);removeGate();ReminderLog.add(this,"拦截服务已断开");super.onDestroy();}
}
