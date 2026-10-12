package cn.xidengju.app;

import android.content.*;
import java.time.*;
import java.time.format.DateTimeFormatter;

final class ReminderLog {
    static synchronized void add(Context c,String event){
        SharedPreferences p=c.getSharedPreferences("reminderLog",0);
        String old=p.getString("lines","");String line=LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss"))+"  "+event.replace('\n',' ');
        String[] lines=(line+"\n"+old).split("\n");StringBuilder result=new StringBuilder();
        for(int i=0;i<Math.min(80,lines.length);i++)result.append(lines[i]).append('\n');
        p.edit().putString("lines",result.toString()).commit();
    }
    static String read(Context c){return c.getSharedPreferences("reminderLog",0).getString("lines","暂无记录。请先安排一次后台测试。");}
    static String time(long value){return value<=0?"未安排":Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M月d日 HH:mm:ss"));}
}
