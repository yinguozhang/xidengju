package cn.xidengju.app;
import android.content.*;
public class RestoreReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        ReminderLog.add(c,"恢复排程："+i.getAction());
        ReminderReceiver.schedule(c);
    }
}
