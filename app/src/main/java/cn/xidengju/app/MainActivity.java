package cn.xidengju.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.time.*;

public class MainActivity extends Activity {
    private LinearLayout page;
    private LinearLayout root;
    private android.content.SharedPreferences prefs;
    private CountDownTimer timer;
    private RestAudio audio;
    private SleepDiaryScreen diaryDraft;
    private boolean interruptedRest;
    private String currentScreen="home";
    private String selectedTab="home";
    private long ritualBed;
    private long activeBed, reminderDue;
    private String activePlan="";
    private TextView remainingText;
    private Button reminderTimeButton;
    private final ArrayList<Button> earlyChoices=new ArrayList<>();
    private final Handler flowHandler=new Handler(Looper.getMainLooper());
    private boolean foregroundReminderShown;
    private boolean foregroundReminderBedtime;
    private AlertDialog foregroundReminderDialog;
    private final Runnable flowTick=new Runnable(){public void run(){
        boolean pending=NightFlow.pending(MainActivity.this);
        boolean bedtime=System.currentTimeMillis()>=NightFlow.bed(MainActivity.this);
        if(bedtime!=foregroundReminderBedtime){foregroundReminderShown=false;foregroundReminderBedtime=bedtime;}
        if(!pending)foregroundReminderShown=false;
        if("winddown".equals(currentScreen)){if(!validFlow())expiredReminder();else if(System.currentTimeMillis()>=activeBed||NightFlow.finished(MainActivity.this,activeBed))flow();else updateWinddown();}
        else if("flow".equals(currentScreen)&&!validFlow())expiredReminder();
        else if(pending&&!foregroundReminderShown&&!Arrays.asList("winddown","flow","ritual","rest","emergency").contains(currentScreen)){
            foregroundReminderShown=true;
            if(foregroundReminderDialog!=null)foregroundReminderDialog.dismiss();
            foregroundReminderDialog=new AlertDialog.Builder(MainActivity.this).setTitle(bedtime?"到下线时间了":"该收尾了")
                .setMessage("可以保留当前页面，或查看提醒。正在填写的睡眠日记草稿会在本次打开应用期间保留。")
                .setNegativeButton("保留当前页面",null).setPositiveButton("查看提醒",(d,w)->{activeBed=0;flow();}).show();
        }
        flowHandler.postDelayed(this,1000);
    }};
    private int dp(int n) { return (int)(n*getResources().getDisplayMetrics().density); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs=getSharedPreferences("plan",0);
        audio=new RestAudio(this);
        getWindow().setStatusBarColor(Color.rgb(16,25,35));
        getWindow().setNavigationBarColor(Color.rgb(16,25,35));
        routeIntent(getIntent());
    }
    private void routeIntent(Intent intent){
        interruptedRest=false;
        activeBed=0;activePlan="";
        if(intent.getBooleanExtra("flow",false)&&intent.getAction()!=null&&intent.getAction().startsWith("open_")){
            activeBed=intent.getLongExtra("notificationBed",0);activePlan=intent.getStringExtra("notificationPlan");
            if(!validFlow()){expiredReminder();return;}
        }
        if(intent.getBooleanExtra("emergency",false))emergency();
        else if(intent.getBooleanExtra("blocking",false))blockingSettings();
        else if(intent.getBooleanExtra("winddown",false))winddown();
        else if(intent.getBooleanExtra("flow",false))flow();
        else if(intent.getBooleanExtra("ritual",false))ritual();
        else if(intent.getBooleanExtra("diagnostics",false))diagnostics();
        else if(NightFlow.pending(this))flow();else home();
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);routeIntent(intent);}
    void screen(String title) {
        if(timer!=null){timer.cancel();timer=null;}
        if(audio!=null)audio.stop();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams window=getWindow().getAttributes();window.screenBrightness=-1;getWindow().setAttributes(window);
        currentScreen="other";
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(NightUi.BG);
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(NightUi.BG);scroll.setClipToPadding(false);
        page=new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(dp(24),dp(18),dp(24),dp(30));
        scroll.addView(page);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets)->{root.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());return insets;});
        TextView brand=text("熄灯局  ·  月光夜航",12);brand.setLetterSpacing(.08f);brand.setPadding(0,dp(4),0,dp(16));
        TextView heading=text(title,28);heading.setTextColor(NightUi.TEXT);heading.setPadding(0,0,0,dp(18));
    }
    TextView text(String value,int size) {
        TextView t=NightUi.label(this,value,size);t.setPadding(0,dp(10),0,dp(10));page.addView(t);return t;
    }
    Button button(String label,Runnable action) {
        Button b=new Button(this); b.setText(label); b.setAllCaps(false); b.setOnClickListener(v->action.run());
        NightUi.styleButton(b,false);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.topMargin=dp(10); page.addView(b,p);return b;
    }
    private TextView small(String value,int size,int color){TextView v=NightUi.label(this,value,size);v.setTextColor(color);return v;}
    private void section(String title){TextView t=small(title,14,NightUi.MUTED);t.setPadding(0,dp(22),0,dp(8));page.addView(t);}
    private void row(String icon,String title,String detail,Runnable action){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.HORIZONTAL);box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(16),dp(15),dp(14),dp(15));box.setBackground(NightUi.round(this,NightUi.CARD,18));
        TextView symbol=small(icon,22,NightUi.GREEN);symbol.setGravity(Gravity.CENTER);box.addView(symbol,new LinearLayout.LayoutParams(dp(36),dp(44)));
        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(dp(10),0,0,0);
        copy.addView(small(title,16,NightUi.TEXT));if(detail!=null&&!detail.isEmpty()){TextView sub=small(detail,12,NightUi.MUTED);sub.setPadding(0,dp(4),0,0);copy.addView(sub);}
        box.addView(copy,new LinearLayout.LayoutParams(0,-2,1));TextView arrow=small("›",25,NightUi.MUTED);arrow.setGravity(Gravity.CENTER);box.addView(arrow,new LinearLayout.LayoutParams(dp(20),-2));
        box.setOnClickListener(v->action.run());box.setFocusable(true);box.setContentDescription(title+(detail==null?"":", "+detail));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(10);page.addView(box,lp);
    }
    private void addBottomNav(String tab){
        selectedTab=tab;LinearLayout nav=new LinearLayout(this);nav.setOrientation(LinearLayout.HORIZONTAL);nav.setPadding(dp(10),dp(7),dp(10),dp(5));
        nav.setBackground(NightUi.gradient(this,Color.rgb(23,34,45),Color.rgb(18,28,38),0));
        navItem(nav,"☾","今晚","home".equals(tab),this::home);
        navItem(nav,"▥","睡眠","sleep".equals(tab),this::sleepHub);
        navItem(nav,"⚙","设置","settings".equals(tab),this::settingsHub);
        root.addView(nav,new LinearLayout.LayoutParams(-1,dp(64)));
    }
    private void navItem(LinearLayout nav,String glyph,String label,boolean active,Runnable action){
        LinearLayout item=new LinearLayout(this);item.setOrientation(LinearLayout.VERTICAL);item.setGravity(Gravity.CENTER);
        TextView mark=small(glyph,23,active?NightUi.GREEN:NightUi.MUTED);mark.setGravity(Gravity.CENTER);item.addView(mark);
        TextView name=small(label,11,active?NightUi.GREEN:NightUi.MUTED);name.setGravity(Gravity.CENTER);item.addView(name);
        item.setOnClickListener(v->action.run());item.setContentDescription(label);nav.addView(item,new LinearLayout.LayoutParams(0,-1,1));
    }
    private String clockTime(){return String.format(Locale.CHINA,"%02d:%02d",prefs.getInt("hour",23),prefs.getInt("minute",30));}
    private void changeTime(){new TimePickerDialog(this,(v,h,m)->{
        if(!BlockRules.validWindow(h*60+m,prefs.getInt("endMinute",420))){Toast.makeText(this,"下线时间不能与解除时间相同",Toast.LENGTH_LONG).show();return;}
        NightFlow.clear(this);
        prefs.edit().putInt("hour",h).putInt("minute",m).putBoolean("enabled",true).commit();
        ReminderLog.add(this,"用户保存下线时间并开启每日提醒");requestNotificationPermission();ReminderReceiver.schedule(this);planSettings();
    },prefs.getInt("hour",23),prefs.getInt("minute",30),true).show();}
    void home() {
        activeBed=0;activePlan="";
        screen("今晚，也按时收机");
        currentScreen="home";
        FrameLayout hero=new FrameLayout(this);hero.setBackground(NightUi.gradient(this,Color.rgb(35,59,65),Color.rgb(28,42,53),26));hero.setClipToOutline(true);
        hero.addView(new NightUi.MoonDecor(this),new FrameLayout.LayoutParams(-1,-1));
        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(dp(22),dp(22),0,0);
        copy.addView(small("今 晚 下 线",13,NightUi.GREEN));TextView time=small(clockTime(),46,NightUi.TEXT);time.setPadding(0,dp(6),0,0);copy.addView(time);
        TextView cadence=small("提前 30 分钟收尾",12,NightUi.MUTED);cadence.setPadding(0,dp(10),0,0);copy.addView(cadence);
        hero.addView(copy,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP|Gravity.START));
        ImageView otter=NightUi.art(this,"otter-companion.png",165);FrameLayout.LayoutParams artLp=new FrameLayout.LayoutParams(dp(151),dp(165),Gravity.BOTTOM|Gravity.RIGHT);artLp.rightMargin=dp(3);artLp.bottomMargin=dp(36);hero.addView(otter,artLp);otter.setOnClickListener(v->room());otter.setContentDescription("进入水獭小屋");
        TextView foot=small("今晚还有时间，慢慢收个尾。",12,NightUi.TEXT);foot.setPadding(dp(20),dp(9),0,dp(14));foot.setBackgroundColor(Color.argb(32,7,18,26));hero.addView(foot,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        LinearLayout.LayoutParams heroLp=new LinearLayout.LayoutParams(-1,dp(236));page.addView(hero,heroLp);
        int hour=LocalTime.now().getHour();boolean morning=hour>=5&&hour<12;boolean evening=hour>=21||hour<5;
        NightUi.styleButton(button(morning?"记录昨夜睡眠":evening?"开始睡前仪式":"查看今晚计划",morning?this::diary:evening?this::ritual:this::planSettings),true);
        section("今晚运行状态");
        String problem=ReminderReceiver.notificationsProblem(this);boolean enabled=prefs.getBoolean("enabled",false),blocking=prefs.getBoolean("blocking",false);
        int selected=prefs.getStringSet("apps",Collections.emptySet()).size();
        if(!enabled)row("!","每日提醒未开启","设置下线时间并打开提醒",this::planSettings);
        else if(!problem.isEmpty()||!ReminderReceiver.exact(this))row("!","提醒可能无法准时到达","检查通知与系统权限",this::safeguards);
        else if(!BlockingService.connected||!prefs.getBoolean("strongReminders",false))row("!","全屏收尾流程尚未开启","普通通知容易忽略，请检查授权与弹窗开关",this::permissions);
        else if(!blocking||selected==0)row("!","应用拦截尚未就绪",selected==0?"先选择要暂停的娱乐应用":"检查拦截开关与授权",selected==0?this::apps:this::safeguards);
        else row("✓","今晚已准备好","提醒与 "+selected+" 个应用的拦截已开启",this::planSettings);
        TextView hint=text("点一下水獭，可以去看看它的小屋。",12);hint.setGravity(Gravity.CENTER);hint.setPadding(0,dp(20),0,0);
        addBottomNav("home");
    }
    private void requestNotificationPermission(){if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},1);}
    private String reminderSummary(){
        if(!prefs.getBoolean("enabled",false))return "仅设置时间或打开拦截不会自动提醒，请开启每日提醒。";
        android.content.SharedPreferences state=ReminderReceiver.state(this);StringBuilder result=new StringBuilder();
        for(String kind:ReminderReceiver.DAILY){String error=state.getString("error_"+kind,"");result.append(ReminderReceiver.label(kind)).append("：").append(error.isEmpty()?ReminderLog.time(state.getLong("due_"+kind,0)):error).append('\n');}
        String problem=ReminderReceiver.notificationsProblem(this);if(!problem.isEmpty())result.append("⚠ ").append(problem).append('\n');
        if(!ReminderReceiver.exact(this))result.append("⚠ 未允许准时提醒，可能延迟\n");
        return result.toString().trim();
    }
    void sleepHub(){
        screen("把睡眠慢慢记下来");currentScreen="sleep";
        LocalDate today=LocalDate.now();SleepStore store=new SleepStore(this);int nights=0;
        for(int i=0;i<7;i++)if(store.read(today.minusDays(i))!=null)nights++;
        LinearLayout summary=new LinearLayout(this);summary.setOrientation(LinearLayout.VERTICAL);summary.setPadding(dp(20),dp(17),dp(20),dp(17));
        summary.setBackground(NightUi.gradient(this,Color.rgb(31,52,59),Color.rgb(29,43,53),20));
        summary.addView(small("近 7 天",13,NightUi.GREEN));TextView score=small(nights+" / 7 晚",30,NightUi.TEXT);score.setPadding(0,dp(5),0,dp(5));summary.addView(score);
        summary.addView(small("记录越完整，越容易看清自己的作息。",13,NightUi.MUTED));page.addView(summary);
        section("睡眠记录");row("✎","填写睡眠日记","第二天早上花一分钟记下昨夜",this::diary);
        row("▥","查看每周记录","睡眠时长与效率趋势",this::report);
        addBottomNav("sleep");
    }
    void settingsHub(){
        screen("把今晚安排好");currentScreen="settings";
        section("今晚的计划");row("◷","下线时间与每日提醒",clockTime()+" 下线 · "+(prefs.getBoolean("enabled",false)?"提醒已开启":"提醒未开启"),this::planSettings);
        int selected=prefs.getStringSet("apps",Collections.emptySet()).size();
        row("▦","睡前应用名单",selected+" 个娱乐应用 · 随时增减",this::apps);
        section("运行保障");
        row("◇","权限与后台保障",safeguardSummary(),this::safeguards);
        section("关于");TextView about=text("熄灯局 0.8.2 · 睡眠记录保存在本机\n覆盖安装会保留数据；卸载应用会清除本机记录。\n睡前音乐：Vaporware · The Cynic Project（CC0）",12);about.setPadding(0,dp(4),0,dp(4));
        row("◇","隐私与数据","权限用途、本机记录和删除方式",this::privacy);
        row("♪","睡前音乐来源","Calm Piano 1 · OpenGameArt",()->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://opengameart.org/content/calm-piano-1-vaporware"))));
        addBottomNav("settings");
    }
    private String safeguardSummary(){
        if(!ReminderReceiver.notificationsProblem(this).isEmpty())return "通知需要检查";
        if(!ReminderReceiver.exact(this))return "准时提醒权限需要检查";
        if(!BlockingService.connected&&prefs.getBoolean("blocking",false))return "拦截服务未连接";
        return "提醒、拦截与诊断集中在这里";
    }
    void privacy(){
        screen("隐私与本机数据");selectedTab="settings";
        text("下线计划、娱乐应用名单、明日计划、睡眠日记、紧急退出原因和最近80条日志保存在本机应用私有空间。当前版本没有联网权限，不上传这些数据。",14);
        text("无障碍服务只识别窗口事件中的前台应用包名，显示用户同意的提醒和门禁；不读取页面文字、聊天、密码或截图。通知和准时提醒权限用于计划到点提醒。",14);
        text("主动复制诊断日志会包含提醒时间、系统时区、权限和运行状态，不包含日记正文、任务正文或紧急原因。是否分享由你决定。",14);
        text("系统设置中清除本应用数据或卸载，可删除全部本机记录。覆盖安装保留记录。当前没有数据导出和云端恢复。音乐来源页面由外部浏览器打开。",14);
        button("返回设置",this::settingsHub);
    }
    void planSettings(){
        screen("今晚的下线计划");currentScreen="plan";selectedTab="settings";
        section("约定时间");row("◷","下线时间",clockTime()+" · 收尾提醒提前 30 分钟",this::changeTime);
        Switch reminder=new Switch(this);reminder.setText("每天提醒我收尾");reminder.setTextSize(16);reminder.setTextColor(NightUi.TEXT);reminder.setChecked(prefs.getBoolean("enabled",false));
        reminder.setPadding(dp(16),dp(10),dp(16),dp(10));reminder.setMinHeight(dp(58));reminder.setBackground(NightUi.round(this,NightUi.CARD,18));
        LinearLayout.LayoutParams switchLp=new LinearLayout.LayoutParams(-1,-2);switchLp.topMargin=dp(10);page.addView(reminder,switchLp);
        reminder.setOnCheckedChangeListener((v,on)->{if(!on)NightFlow.clear(this);prefs.edit().putBoolean("enabled",on).commit();ReminderLog.add(this,on?"用户开启每日提醒":"用户关闭每日提醒");if(on)requestNotificationPermission();ReminderReceiver.schedule(this);});
        section("娱乐应用");row("▦","调整拦截名单",prefs.getStringSet("apps",Collections.emptySet()).size()+" 个应用已选择",this::apps);
        row("◇","检查运行保障","通知、准时提醒、拦截授权",this::safeguards);
        section("已安排的提醒");text(reminderSummary(),13);
        text("提前30分钟确认收尾任务，再次提醒不会越过下线时刻。到点可做睡前仪式，每晚可延迟15分钟一次。紧急情况可明确结束当晚计划，不会关闭明天的提醒。",12);
        button("返回设置",this::settingsHub);
    }
    void safeguards(){
        screen("权限与后台保障");currentScreen="safeguards";selectedTab="settings";
        text("这些设置决定提醒能否准时送达、应用门禁能否出现。需要时可直接跳到对应的系统页面。",14);
        section("提醒");row("♧","通知与准时提醒",ReminderReceiver.notificationsProblem(this).isEmpty()?(ReminderReceiver.exact(this)?"通知已开启 · 准时提醒已允许":"准时提醒未允许"):"通知需要检查",this::permissions);
        section("应用门禁");row("◈","应用拦截与弹窗",BlockingService.connected?(prefs.getBoolean("blocking",false)?"服务已连接 · 拦截已开启":"服务已连接 · 拦截未开启"):"服务未连接",this::blockingSettings);
        section("排查");row("⌁","后台测试与日志","安排一分钟测试、检查记录",this::diagnostics);
        button("返回设置",this::settingsHub);
    }
    void permissions() {
        screen("让提醒准时到达");
        currentScreen="permissions";selectedTab="settings";ReminderReceiver.channel(this);
        boolean exact=Build.VERSION.SDK_INT<31 || getSystemService(AlarmManager.class).canScheduleExactAlarms();
        boolean notifications=getSystemService(NotificationManager.class).areNotificationsEnabled();
        text("通知："+(notifications?"已开启":"未开启")+"\n准时提醒："+(exact?"已允许":"未允许，到达时间可能延后"),16);
        NotificationChannel channel=getSystemService(NotificationManager.class).getNotificationChannel(ReminderReceiver.CHANNEL);
        text("睡前提醒分类："+(channel.getImportance()==0?"已关闭":channel.getImportance()<NotificationManager.IMPORTANCE_HIGH?"已开启，但未设为高重要性，可能不弹横幅":"已开启，高重要性")+"\n提示音："+(channel.getSound()==null?"静音":"由系统控制"),14);
        text("省电限制："+(getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName())?"已豁免系统电池优化":"未豁免；还需留意手机自启动/后台限制")+"\n免打扰："+(getSystemService(NotificationManager.class).getCurrentInterruptionFilter()==NotificationManager.INTERRUPTION_FILTER_ALL?"未开启":"可能影响提示音和横幅"),13);
        button("打开通知设置",()->openSettings(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName())));
        if(Build.VERSION.SDK_INT>=31) button("允许准时提醒",()->openSettings(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName()))));
        button("睡前提醒分类与横幅设置",()->openSettings(new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()).putExtra(Settings.EXTRA_CHANNEL_ID,ReminderReceiver.CHANNEL)));
        button("查看系统电池优化设置",()->openSettings(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)));
        button("应用详情：电池 / 自启动",()->openSettings(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))));
        button(prefs.getBoolean("strongReminders",false)?"关闭全屏收尾流程":"开启全屏收尾流程",()->{
            if(prefs.getBoolean("strongReminders",false)){prefs.edit().putBoolean("strongReminders",false).putLong("testPromptUntil",0).apply();permissions();return;}
            if(!BlockingService.connected){new AlertDialog.Builder(this).setMessage("收尾弹窗需要先连接已授权的应用拦截服务。它不会遮挡锁屏和已保护的必要应用；这些场景保留通知。")
                .setPositiveButton("前往授权",(d,w)->blockingSettings()).setNegativeButton("取消",null).show();return;}
            prefs.edit().putBoolean("strongReminders",true).apply();permissions();
        });
        button("刷新状态",this::permissions); button("返回运行保障",this::safeguards);
        button("一分钟后台提醒测试",this::startReminderTest);
    }
    void startReminderTest(){
        String problem=ReminderReceiver.notificationsProblem(this);
        if(!problem.isEmpty()){new AlertDialog.Builder(this).setMessage(problem+"。请先开启通知，再测试后台到达。")
            .setPositiveButton("检查权限",(d,w)->permissions()).setNegativeButton("取消",null).show();return;}
        boolean saved=ReminderReceiver.test(this);diagnostics();
        new AlertDialog.Builder(this).setTitle(saved?"后台测试已安排":"测试安排失败").setMessage(saved?"现在返回桌面或划掉最近任务卡片，等待一分钟。不要点击系统“强行停止”。未授权准时提醒时可能延迟。回来后可查看日志，测试不会改变每日计划。":"请查看下方诊断日志。")
            .setPositiveButton("知道了",null).show();
    }
    void diagnostics(){
        screen("后台提醒诊断");currentScreen="diagnostics";selectedTab="settings";
        text(reminderSummary(),14);
        text(diagnosticState(),12);
        long test=ReminderReceiver.state(this).getLong("due_test",0);text("后台测试："+ReminderLog.time(test),14);
        text("这里显示最近提交给系统的计划，不保证系统一定送达。日志可区分排程、收到广播、通知被关闭和通知提交；提交成功不代表你已看到。",12);
        button("安排一分钟后台测试",this::startReminderTest);
        button("取消后台测试",()->{ReminderReceiver.cancel(this,"test");prefs.edit().putLong("testPromptUntil",0).apply();ReminderLog.add(this,"用户取消后台测试");diagnostics();});
        button("检查提醒权限",this::permissions);
        button("刷新诊断",()->{ReminderReceiver.schedule(this);diagnostics();});
        button("复制诊断文字",()->{
            String report="熄灯局0.8.2\n"+reminderSummary()+"\n"+diagnosticState()+"\n"+ReminderLog.read(this);
            getSystemService(android.content.ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("熄灯局诊断",report));Toast.makeText(this,"诊断已复制，不包含日记和应用名单",Toast.LENGTH_LONG).show();
        });
        TextView logs=text(ReminderLog.read(this),12);logs.setTextIsSelectable(true);
        text("划掉卡片不等于强行停止。强行停止后需重新打开；手机重启、更新安装包后会尝试恢复排程。当前不发送短信。",12);
        button("返回运行保障",this::safeguards);
    }
    private String diagnosticState(){
        NotificationManager nm=getSystemService(NotificationManager.class);NotificationChannel ch=nm.getNotificationChannel(ReminderReceiver.CHANNEL);
        String problem=ReminderReceiver.notificationsProblem(this);
        return "系统时区："+ZoneId.systemDefault()+"\n准时提醒："+(ReminderReceiver.exact(this)?"允许":"未允许，可能延迟")
            +"\n通知检查："+(problem.isEmpty()?"开关已打开（不代表实际可见）":problem)
            +"\n通知分类重要性："+(ch==null?"未创建":ch.getImportance()+"（0关闭 / 4高）")
            +"\n分类提示音："+(ch==null||ch.getSound()==null?"静音":"系统设置")
            +"\n系统省电豁免："+getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(getPackageName())
            +"\n免打扰过滤状态："+nm.getCurrentInterruptionFilter()+"（1为全部允许）"
            +"\n拦截服务连接："+BlockingService.connected+"\n收尾弹窗开关："+prefs.getBoolean("strongReminders",false);
    }
    void openSettings(Intent intent) {
        try {startActivity(intent);} catch(ActivityNotFoundException ex) {startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}
    }
    void apps() {
        screen("选择睡前应用");selectedTab="settings";
        button("管理手动保护名单",this::manageProtectedApps);
        text("选择睡前要挡住的娱乐应用。系统与已识别的必要应用自动保护；长按其他应用可加入保护名单。保护识别可能不全，请补充你的校园码等必要应用。",14);
        Set<String> draft=new HashSet<>(prefs.getStringSet("apps",Collections.emptySet()));
        EditText search=new EditText(this); search.setSingleLine(true); search.setHint("搜索已安装应用");NightUi.field(search); page.addView(search);
        button("保存名单",()->{prefs.edit().putStringSet("apps",new HashSet<>(draft)).apply();settingsHub();});
        button("取消",this::settingsHub);
        PackageManager pm=getPackageManager();
        List<ResolveInfo> installed=pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0);
        installed.sort(Comparator.comparing(a->a.loadLabel(pm).toString(),String.CASE_INSENSITIVE_ORDER));
        Set<String> seen=new HashSet<>(); List<CheckBox> rows=new ArrayList<>();
        Set<String> safe=AppProtection.packages(this);
        for(ResolveInfo info:installed) {
            String pkg=info.activityInfo.packageName;
            if(pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
            CheckBox row=new CheckBox(this); row.setText(info.loadLabel(pm)); row.setTextColor(Color.rgb(229,236,235)); row.setMinHeight(dp(52)); row.setChecked(draft.contains(pkg));
            if(AppProtection.isProtected(this,pkg,safe)){draft.remove(pkg);row.setChecked(false);row.setEnabled(false);row.setText(info.loadLabel(pm)+" · 已保护");}
            row.setOnLongClickListener(v->{
                new AlertDialog.Builder(this).setTitle("始终保护这个应用？").setMessage("加入后不会被拦截。适合校园码、支付、出行和紧急联系应用。")
                    .setPositiveButton("加入保护",(d,w)->{Set<String> protectedApps=new HashSet<>(prefs.getStringSet("protectedApps",Collections.emptySet()));protectedApps.add(pkg);prefs.edit().putStringSet("protectedApps",protectedApps).apply();draft.remove(pkg);row.setChecked(false);row.setEnabled(false);row.setText(info.loadLabel(pm)+" · 已保护");})
                    .setNegativeButton("取消",null).show();return true;
            });
            row.setOnCheckedChangeListener((b,on)->{if(on)draft.add(pkg);else draft.remove(pkg);}); page.addView(row); rows.add(row);
        }
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){String q=s.toString().toLowerCase(Locale.ROOT);for(CheckBox row:rows)row.setVisibility(row.getText().toString().toLowerCase(Locale.ROOT).contains(q)?View.VISIBLE:View.GONE);}
            public void afterTextChanged(Editable e){}
        });
    }
    void manageProtectedApps(){
        screen("手动保护名单");
        text("移除手动保护不会自动加入拦截名单。系统和自动识别的必要应用仍受保护。",14);
        Set<String> manual=new HashSet<>(prefs.getStringSet("protectedApps",Collections.emptySet()));
        if(manual.isEmpty())text("还没有手动添加的保护应用。",16);
        for(String pkg:manual){String name=pkg;try{name=getPackageManager().getApplicationInfo(pkg,0).loadLabel(getPackageManager()).toString();}catch(PackageManager.NameNotFoundException ignored){}
            final String label=name;
            button("移除手动保护 · "+label,()->new AlertDialog.Builder(this).setTitle("移除手动保护？").setMessage(label+" 的系统保护规则仍然有效。")
                .setPositiveButton("移除",(d,w)->{Set<String> updated=new HashSet<>(prefs.getStringSet("protectedApps",Collections.emptySet()));updated.remove(pkg);prefs.edit().putStringSet("protectedApps",updated).apply();manageProtectedApps();}).setNegativeButton("取消",null).show());
        }
        button("回到应用名单",this::apps);
    }
    void blockingSettings(){
        screen("睡前应用拦截");currentScreen="blocking";selectedTab="settings";
        int end=prefs.getInt("endMinute",420);
        text("服务连接："+(BlockingService.connected?"已连接":"未连接，请授权后刷新")+"\n拦截开关："+(prefs.getBoolean("blocking",false)?"开启":"关闭")+String.format(Locale.CHINA,"\n每天从下线时间到 %02d:%02d",end/60,end%60),16);
        text(prefs.getString("blockError","先选择一个娱乐应用，开启服务与拦截，再进行两分钟测试。"),14);
        button("授权应用拦截服务",()->new AlertDialog.Builder(this).setTitle("授权前请了解")
            .setMessage("熄灯局需要无障碍服务识别前台应用包名，并在约定时段遮挡你选择的应用。不读取页面文字、聊天、密码或截图，不上传数据。你可以随时紧急解锁、关闭拦截或撤销服务。")
            .setPositiveButton("同意，前往系统设置",(d,w)->openSettings(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
            .setNegativeButton("暂不开启",null).show());
        button(prefs.getBoolean("blocking",false)?"关闭拦截":"开启拦截",()->{
            if(!prefs.getBoolean("blocking",false) && !BlockingService.connected){Toast.makeText(this,"请先授权服务并返回刷新",Toast.LENGTH_LONG).show();return;}
            prefs.edit().putBoolean("blocking",!prefs.getBoolean("blocking",false)).putLong("testUntil",0).remove("blockError").apply();blockingSettings();
        });
        if(end==prefs.getInt("hour",23)*60+prefs.getInt("minute",30))text("下线和解除时间相同，请修改其中一个时间，应用门禁才能生效。",14);
        button("设置早晨解除时间",()->new TimePickerDialog(this,(v,h,m)->{
            if(!BlockRules.validWindow(prefs.getInt("hour",23)*60+prefs.getInt("minute",30),h*60+m)){Toast.makeText(this,"解除时间不能与下线时间相同",Toast.LENGTH_LONG).show();return;}
            NightFlow.clear(this);prefs.edit().putInt("endMinute",h*60+m).commit();ReminderReceiver.schedule(this);blockingSettings();
        },end/60,end%60,true).show());
        button("开始两分钟拦截测试",()->{
            if(!BlockingService.connected || !prefs.getBoolean("blocking",false) || prefs.getStringSet("apps",Collections.emptySet()).isEmpty()){
                Toast.makeText(this,"请先选应用、授权服务并开启拦截",Toast.LENGTH_LONG).show();return;}
            prefs.edit().putLong("testUntil",System.currentTimeMillis()+120000).putLong("unlockUntil",0).apply();
            new AlertDialog.Builder(this).setMessage("现在返回桌面，打开名单中的娱乐应用。两分钟后恢复正常计划；门禁上随时可以停止测试。") .setPositiveButton("知道了",null).show();
        });
        button("紧急解锁 / 结束当晚计划",this::emergency);
        button("刷新连接状态",this::blockingSettings);button("管理应用名单",this::apps);button("返回运行保障",this::safeguards);
    }
    private void bindFlow(){if(activeBed==0){activeBed=NightFlow.bed(this);activePlan=NightFlow.fingerprint(this);}}
    private boolean validFlow(){return prefs.getBoolean("enabled",false)&&NightFlowRules.validSession(System.currentTimeMillis(),activeBed,NightFlow.bed(this),activeBed>0?NightFlow.end(this,activeBed):0,NightFlow.fingerprint(this).equals(activePlan));}
    private void expiredReminder(){screen("这条提醒已经过期");currentScreen="expired";text("这是之前一晚的提醒，或计划已修改。不会重新开启今晚的流程。",14);button("查看当前计划",this::home);}
    private String timeLabel(long time){return java.time.format.DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()));}
    private void updateWinddown(){
        long now=System.currentTimeMillis();boolean late=activeBed-now<=900000;
        remainingText.setText("距离 "+timeLabel(activeBed)+" 下线还有 "+Math.max(1,(activeBed-now+59999)/60000)+" 分钟。"+(late?"只收尾已有任务，不再开始新的一局。":"选好收尾任务和下次提醒时刻。"));
        for(Button choice:earlyChoices)choice.setVisibility(late?View.GONE:View.VISIBLE);
        if(reminderDue<=now||reminderDue>activeBed){long[] options=NightFlowRules.reminderTimes(now,activeBed,ZoneId.systemDefault());if(options.length>0)reminderDue=options[0];}
        reminderTimeButton.setText("下次提醒 · "+timeLabel(reminderDue)+(reminderDue==activeBed?"（下线时刻）":""));
    }
    void winddown(){
        bindFlow();if(!validFlow()){expiredReminder();return;}long bed=activeBed;if(System.currentTimeMillis()>=bed||NightFlow.finished(this,bed)){flow();return;}
        screen("给今晚留个句号");currentScreen="winddown";selectedTab="home";
        remainingText=text("",14);earlyChoices.clear();
        EditText task=new EditText(this);task.setHint("例如：看完收藏的视频，不再打开新的");NightUi.field(task);
        if(prefs.getLong("taskBed",0)==bed)task.setText(prefs.getString("winddownTask",""));page.addView(task);
        earlyChoices.add(button("再打一局",()->task.setText("结束最后一局，不再开新对局")));
        earlyChoices.add(button("再刷一会",()->task.setText("结束短视频浏览，不再打开新的")));
        earlyChoices.add(button("正在学习",()->task.setText("结束当前学习任务，保存进度")));
        if(bed-System.currentTimeMillis()<=900000&&task.getText().toString().trim().isEmpty())task.setText("结束当前任务，保存进度");
        reminderDue=0;Button interval=button("",()->{});reminderTimeButton=interval;
        interval.setOnClickListener(v->{
            long[] options=NightFlowRules.reminderTimes(System.currentTimeMillis(),bed,ZoneId.systemDefault());if(options.length==0){flow();return;}
            String[] labels=new String[options.length];int selected=0;for(int i=0;i<options.length;i++){labels[i]=timeLabel(options[i])+(options[i]==bed?" · 下线":"");if(options[i]==reminderDue)selected=i;}
            NumberPicker picker=new NumberPicker(this);picker.setMinValue(0);picker.setMaxValue(options.length-1);picker.setDisplayedValues(labels);picker.setValue(selected);picker.setWrapSelectorWheel(false);picker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
            LinearLayout wrap=new LinearLayout(this);wrap.setGravity(Gravity.CENTER);wrap.addView(picker,new LinearLayout.LayoutParams(-1,dp(160)));
            new AlertDialog.Builder(this).setTitle("选择下次提醒时刻").setView(wrap).setNegativeButton("取消",null).setPositiveButton("确认",(d,w)->{reminderDue=options[picker.getValue()];updateWinddown();}).show();
        });
        text("只显示未来的5分钟刻度和下线时刻；不会推迟你的下线计划。已有任务可以直接保留。",12);updateWinddown();
        NightUi.styleButton(button("确认收尾计划，继续当前任务",()->{
            if(bed-System.currentTimeMillis()<=900000&&task.getText().toString().trim().isEmpty())task.setText("结束当前任务，保存进度");
            if(task.getText().toString().trim().isEmpty()){Toast.makeText(this,"先选择或写下一个收尾任务",Toast.LENGTH_LONG).show();return;}
            if(System.currentTimeMillis()>=bed){flow();return;}
            if(!validFlow()){expiredReminder();return;}updateWinddown();
            if(NightFlow.repeatAt(this,task.getText().toString(),reminderDue,bed)){Toast.makeText(this,"已安排 "+timeLabel(reminderDue)+" 提醒",Toast.LENGTH_SHORT).show();home();moveTaskToBack(true);}
            else Toast.makeText(this,"提醒未安排成功，请检查每日提醒和系统权限",Toast.LENGTH_LONG).show();
        }),true);
        button("现在下线，开始睡前仪式",this::ritual);button("紧急情况",this::emergency);
    }
    void flow(){
        bindFlow();if(!validFlow()){expiredReminder();return;}long now=System.currentTimeMillis(),bed=activeBed;if(now<bed&&!NightFlow.finished(this,bed)){winddown();return;}
        screen(NightFlow.finished(this,bed)?"今晚已经安顿好了":"到点了，结束今天吧");currentScreen="flow";selectedTab="home";
        page.addView(NightUi.art(this,"otter-ready.png",150));
        text("约定下线时间 "+clockTime()+"。完成明日计划和90秒休息后，这一晚的提醒停止，娱乐应用门禁仍然保留。",14);
        if(!NightFlow.finished(this,bed)){
            NightUi.styleButton(button("写明日计划，开始睡前仪式",this::ritual),true);
            if(NightFlowRules.mayDelay(now,bed,NightFlow.end(this,bed),prefs.getLong("delayedBed",0)))button("延迟15分钟 · 今晚仅一次",()->{
                if(NightFlow.delay(this)){Toast.makeText(this,"15分钟后恢复下线提醒和门禁",Toast.LENGTH_LONG).show();home();moveTaskToBack(true);}else Toast.makeText(this,"未能安排延迟，或今晚已使用过",Toast.LENGTH_LONG).show();
            });else text("今晚的普通延迟已用完。特殊情况可走紧急入口。",12);
        }
        button("紧急情况 / 结束当晚计划",this::emergency);
        if(NightFlow.finished(this,bed))button("返回首页",this::home);
    }
    void emergency(){
        screen("特殊情况，留一个出口");currentScreen="emergency";selectedTab="home";
        text("电话和已保护的必要应用不受限制。这里用于临时继续使用娱乐应用或暂停当晚计划；原因只保存在本机。",14);
        RadioGroup reasons=new RadioGroup(this);String[] options={"学习","社团或工作","出行","联系家人","情绪不适","其他"};
        for(String reason:options){RadioButton option=new RadioButton(this);option.setId(View.generateViewId());option.setText(reason);option.setTextColor(NightUi.TEXT);option.setMinHeight(dp(44));reasons.addView(option);}page.addView(reasons);
        java.util.function.Consumer<Boolean> confirm=finish->{
            RadioButton selected=reasons.findViewById(reasons.getCheckedRadioButtonId());if(selected==null){Toast.makeText(this,"请选择本次原因",Toast.LENGTH_SHORT).show();return;}
            new AlertDialog.Builder(this).setTitle(finish?"只结束这一晚？":"临时解锁15分钟？").setMessage(finish?"当晚不再提醒或拦截；早晨解除时间后恢复日常计划。不会关闭明天的开关。":"到期后恢复门禁，并再次提醒。不会消耗普通延迟次数。")
                .setNegativeButton("返回",null).setPositiveButton("确认",(d,w)->{if(NightFlow.emergency(this,selected.getText().toString(),finish)){home();moveTaskToBack(true);}else Toast.makeText(this,"恢复提醒安排失败，请检查系统权限",Toast.LENGTH_LONG).show();}).show();
        };
        button("紧急解锁15分钟",()->confirm.accept(false));button("结束当晚计划",()->confirm.accept(true));button("返回下线流程",this::flow);
    }
    void ritual() {
        interruptedRest=false;ritualBed=activeBed>0?activeBed:NightFlow.bed(this);screen("没做完的，\n明天再继续。");currentScreen="ritual";selectedTab="home";page.addView(NightUi.art(this,"otter-ready.png",180));text("写下一件明天想做的事",16);
        EditText plan=new EditText(this); plan.setText(prefs.getString("tomorrow","")); plan.setHint("明天的计划");NightUi.field(plan); page.addView(plan);
        text("点击开始后播放 90 秒舒缓音乐。正常呼吸即可，音量由手机媒体音量控制。",14);
        NightUi.styleButton(button("计划好了，闭眼休息",()->{if(plan.getText().toString().trim().isEmpty()){Toast.makeText(this,"写一件明天想做的事，也可以写“好好休息”",Toast.LENGTH_LONG).show();return;}prefs.edit().putString("tomorrow",plan.getText().toString().trim()).apply();rest();}),true);
        button("返回下线选择",this::flow);button("紧急情况",this::emergency);
    }
    void rest() {
        screen("闭上眼睛，感受自己的呼吸一小会");
        currentScreen="rest";
        text("保持正常呼吸就好。音乐结束，这一小段休息也就完成了。",16);
        text("切换应用、锁屏或来电时，本次休息会暂停并停止音乐。",12);
        TextView remaining=text("01:30",40);
        TextView musicState=text("",13);
        final long startedAt=SystemClock.elapsedRealtime();
        Runnable audioInterrupted=()->{if(timer!=null){timer.cancel();timer=null;}interruptedRest=true;ritual();Toast.makeText(this,"音乐已暂停，可重新开始休息",Toast.LENGTH_LONG).show();};
        final boolean[] playing={audio.play(audioInterrupted)};
        musicState.setText(playing[0]?"♪ 舒缓钢琴 · 播放中":"音乐暂不可用，当前为静音计时");
        Button musicToggle=button(playing[0]?"静音休息":"打开音乐",()->{});
        musicToggle.setOnClickListener(v->{
            long elapsed=SystemClock.elapsedRealtime()-startedAt;
            if(timer==null||elapsed>=90000)return;
            if(playing[0]){audio.stop();playing[0]=false;musicState.setText("静音休息中");musicToggle.setText("打开音乐");}
            else{
                playing[0]=audio.play(audioInterrupted,(int)elapsed);
                musicState.setText(playing[0]?"♪ 舒缓钢琴 · 播放中":"音乐暂不可用，当前为静音计时");
                musicToggle.setText(playing[0]?"静音休息":"打开音乐");
                if(!playing[0])Toast.makeText(this,"暂时无法播放音乐，可以稍后再试",Toast.LENGTH_SHORT).show();
            }
        });
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams dim=getWindow().getAttributes();dim.screenBrightness=.05f;getWindow().setAttributes(dim);
        final long deadline=startedAt+90000;
        timer=new CountDownTimer(90000,1000){
            public void onTick(long ms){long seconds=Math.max(0,(deadline-SystemClock.elapsedRealtime()+999)/1000);remaining.setText(String.format(Locale.CHINA,"%02d:%02d",seconds/60,seconds%60));}
            public void onFinish(){timer=null;audio.stop();NightFlow.complete(MainActivity.this,ritualBed);screen("今晚已下线，晚安。");page.addView(NightUi.art(MainActivity.this,"otter-asleep.png",220));text("明天最重要的事",13);text(prefs.getString("tomorrow",""),18);text("已完成这一小段休息；娱乐应用仍保持原来的门禁。",13);button("回到首页",()->home());}
        }.start();
        button("暂停并返回",()->{timer.cancel();timer=null;ritual();});
    }
    void diary(){selectedTab="sleep";if(diaryDraft==null||diaryDraft.fromReport())diaryDraft=new SleepDiaryScreen(this);diaryDraft.show();currentScreen="diary";}
    void diary(LocalDate morning){selectedTab="sleep";if(diaryDraft==null||!diaryDraft.fromReport()||!diaryDraft.isForDate(morning))diaryDraft=new SleepDiaryScreen(this,morning);diaryDraft.show();currentScreen="diary";}
    void savedDiary(){diaryDraft=null;report();}
    void room(){screen("把休息留给自己");selectedTab="home";page.addView(NightUi.art(this,"otter-room.png",300));page.addView(NightUi.art(this,"otter-companion.png",100));text("今晚的小屋，已经安静下来了。",18);text("积分兑换和装饰玩法还在准备中。",14);button("回到首页",this::home);}
    void report(){
        screen("每周睡眠记录");selectedTab="sleep";
        LocalDate first=LocalDate.now().minusDays(6);SleepStore store=new SleepStore(this);SleepRecord[] records=new SleepRecord[7];int count=0;long total=0;int efficiencies=0;
        for(int i=0;i<7;i++){records[i]=store.read(first.plusDays(i));if(records[i]!=null){count++;total+=records[i].sleepMinutes();efficiencies+=records[i].efficiency();}}
        text(SleepDiaryScreen.date(first)+" — "+SleepDiaryScreen.date(first.plusDays(6)),14);
        if(count==0){page.addView(NightUi.art(this,"otter-asleep.png",170));text("这周还没有记录。",22);text("从一次晨间记录开始，让自己的作息慢慢清晰。",14);}
        else{text("已记录 "+count+" / 7 晚",22);text("平均睡眠 "+SleepDiaryScreen.duration(Math.round(total/(double)count))+"\n平均效率 "+Math.round(efficiencies/(double)count)+"%",18);}
        text("雾蓝柱形 · 睡眠小时    绿色折线 · 效率%",12);
        page.addView(new WeeklyChart(this,records,first),new LinearLayout.LayoutParams(-1,dp(230)));
        text("未记录日期留空；平均值仅计算已记录的夜晚。点击日期卡片可补填或修改。",12);
        for(int i=6;i>=0;i--){SleepRecord r=records[i];String line=SleepDiaryScreen.date(first.plusDays(i))+"  ·  ";
            if(r==null)line+="未记录";else line+=SleepDiaryScreen.duration(r.sleepMinutes())+"  /  "+r.efficiency()+"%\n闭眼 "+SleepDiaryScreen.time(r.bed.getHour()*60+r.bed.getMinute())+" · 醒来 "+SleepDiaryScreen.time(r.wake.getHour()*60+r.wake.getMinute())+" · 起床 "+SleepDiaryScreen.time(r.rise.getHour()*60+r.rise.getMinute())+"\n"+(r.nap==2?"白天没有补觉":r.nap==0?"补觉 30 分钟以上（含 30 分钟）":"补觉 30 分钟以下");
            final LocalDate recordDate=first.plusDays(i);
            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setBackground(NightUi.round(this,NightUi.CARD,14));card.setPadding(dp(16),dp(16),dp(16),dp(16));
            TextView row=NightUi.label(this,line,14);card.addView(row);
            String action=r==null?"补填日记  ›":"修改日记  ›";
            TextView entry=NightUi.label(this,action,14);entry.setTextColor(NightUi.GREEN);entry.setPadding(0,dp(12),0,0);card.addView(entry);
            card.setContentDescription(SleepDiaryScreen.date(recordDate)+"，"+(r==null?"未记录，补填日记":"已记录，修改日记"));card.setOnClickListener(v->diary(recordDate));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(8);page.addView(card,lp);
        }
        button("填写或修改日记",this::diary);button("返回睡眠",this::sleepHub);
    }
    @Override protected void onPause(){super.onPause();flowHandler.removeCallbacks(flowTick);if(timer!=null){timer.cancel();timer=null;audio.stop();interruptedRest=true;getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}}
    @Override protected void onDestroy(){flowHandler.removeCallbacks(flowTick);if(timer!=null)timer.cancel();if(audio!=null)audio.stop();super.onDestroy();}
    @Override protected void onResume(){super.onResume();ReminderReceiver.schedule(this);flowHandler.removeCallbacks(flowTick);flowHandler.post(flowTick);if(interruptedRest){interruptedRest=false;ritual();Toast.makeText(this,"上次休息已暂停，音乐已停止",Toast.LENGTH_SHORT).show();}else if("home".equals(currentScreen)){if(NightFlow.pending(this))flow();else home();}else if("flow".equals(currentScreen))flow();else if("sleep".equals(currentScreen))sleepHub();else if("settings".equals(currentScreen))settingsHub();else if("plan".equals(currentScreen))planSettings();else if("safeguards".equals(currentScreen))safeguards();else if("permissions".equals(currentScreen))permissions();else if("diagnostics".equals(currentScreen))diagnostics();else if("blocking".equals(currentScreen))blockingSettings();}
    @Override public void onBackPressed(){if("diary".equals(currentScreen)&&diaryDraft!=null&&diaryDraft.fromReport())report();else if("home".equals(currentScreen))super.onBackPressed();else if("sleep".equals(currentScreen)||"settings".equals(currentScreen))home();else if("sleep".equals(selectedTab))sleepHub();else if("settings".equals(selectedTab))settingsHub();else home();}
}
