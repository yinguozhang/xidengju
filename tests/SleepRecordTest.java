package cn.xidengju.app;
import java.time.*;

public final class SleepRecordTest {
    static int checks=0;
    static void expect(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static final LocalDate day=LocalDate.of(2026,9,22);
    static final LocalDateTime now=day.atTime(12,0);
    static SleepRecord record(LocalDateTime bed,int latency,int wake,int rise){return new SleepRecord(bed,latency,day.atTime(wake/60,wake%60),day.atTime(rise/60,rise%60),2);}
    public static void main(String[] args){
        SleepRecord normal=record(day.minusDays(1).atTime(23,30),25,450,465);
        expect(normal.validate(now)==null,"normal valid");
        expect(normal.sleepMinutes()==455,"23:55 to 07:30 =455");
        expect(normal.bedMinutes()==495,"23:30 to 07:45 =495");
        expect(normal.efficiency()==92,"rounded efficiency");
        expect(SleepRecord.decode(normal.encode()).encode().equals(normal.encode()),"persistence roundtrip");
        expect(SleepRecord.decode("bad record")==null,"corrupt record ignored");
        expect(record(day.atTime(0,0),0,300,305).validate(now)==null,"midnight");
        expect(record(day.atTime(4,0),50,300,305).validate(now)==null,"04:00 /50 /05:00 valid");
        expect(record(day.minusDays(1).atTime(21,0),0,655,660).validate(now)==null,"latest rise");
        expect(record(day.atTime(4,5),0,450,465).validate(now)!=null,"bed after04 invalid");
        expect(record(day.minusDays(1).atTime(20,55),0,450,465).validate(now)!=null,"bed before21 invalid");
        expect(record(day.minusDays(1).atTime(23,30),55,450,465).validate(now)!=null,"latency max50");
        expect(record(day.minusDays(1).atTime(23,30),-5,450,465).validate(now)!=null,"negative latency");
        expect(record(day.minusDays(1).atTime(23,30),25,430,430).validate(now)==null,"07:10 wake and rise equal valid");
        expect(record(day.atTime(4,0),50,300,300).validate(now)==null,"05:00 wake and rise valid");
        expect(record(day.minusDays(1).atTime(23,30),25,660,660).validate(now)==null,"11:00 wake and rise valid");
        expect(record(day.minusDays(1).atTime(23,30),25,450,445).validate(now)!=null,"earlier rise invalid");
        expect(record(day.atTime(23,30),25,450,465).validate(now)!=null,"incorrect bed date");
        expect(record(day.minusDays(2).atTime(23,30),25,450,465).validate(now)!=null,"two nights invalid");
        expect(normal.validate(day.atTime(7,40))!=null,"future rise invalid");
        expect(record(day.atTime(4,0),50,295,305).validate(now)!=null,"wake before05 invalid");
        expect(record(day.atTime(4,0),50,655,665).validate(now)!=null,"rise after11 invalid");
        expect(record(day.atTime(4,0),50,452,465).validate(now)!=null,"5-minute steps");
        for(int wake=300;wake<=660;wake+=5)for(int rise=wake;rise<=660;rise+=5){
            SleepRecord r=record(day.minusDays(1).atTime(23,30),25,wake,rise);
            expect(r.validate(now)==null && r.efficiency()>0 && r.efficiency()<=100,"valid wheel combination");
        }
        System.out.println("PASS: "+checks+" diary boundaries, date, efficiency and persistence checks");
    }
}
