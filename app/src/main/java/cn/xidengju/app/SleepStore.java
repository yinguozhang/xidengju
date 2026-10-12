package cn.xidengju.app;
import android.content.*;
import java.time.*;

final class SleepStore {
    private final SharedPreferences prefs;
    SleepStore(Context c){prefs=c.getSharedPreferences("sleepDiary",0);}
    SleepRecord read(LocalDate date){SleepRecord r=SleepRecord.decode(prefs.getString(date.toString(),""));return r!=null && r.validate(LocalDateTime.now())==null && r.wake.toLocalDate().equals(date)?r:null;}
    boolean save(SleepRecord record){
        if(record.validate(LocalDateTime.now())!=null)return false;
        return prefs.edit().putString(record.wake.toLocalDate().toString(),record.encode()).commit();
    }
}
