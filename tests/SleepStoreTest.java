package cn.xidengju.app;

import java.time.*;

/** Runs production diary storage using the existing in-memory Android doubles. */
public final class SleepStoreTest {
    private static int checks;
    private static void expect(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private static SleepRecord record(LocalDate morning,boolean midnight,int latency,int nap){
        return new SleepRecord(midnight?morning.atTime(1,0):morning.minusDays(1).atTime(23,30),latency,morning.atTime(7,10),morning.atTime(7,10),nap);
    }
    public static void main(String[] args){
        ReminderReceiverTest.Device device=new ReminderReceiverTest.Device();
        SleepStore store=new SleepStore(device);
        LocalDate last=LocalDate.now().minusDays(1);
        for(int i=0;i<7;i++){
            LocalDate date=last.minusDays(i);expect(store.read(date)==null,"missing day empty");
            SleepRecord original=record(date,i%2==0,25,2);
            expect(store.save(original),"historical day saved");
            expect(store.read(date).encode().equals(original.encode()),"all original values restored");
        }
        String neighbor=store.read(last.minusDays(1)).encode();
        SleepRecord edited=record(last,false,10,0);
        expect(store.save(edited),"edit saved");
        expect(store.read(last).encode().equals(edited.encode()),"edit replaces selected date");
        expect(store.read(last.minusDays(1)).encode().equals(neighbor),"other date untouched");
        expect(device.prefs.get("sleepDiary").data.size()==7,"edit does not duplicate record");
        expect(new SleepStore(device).read(last).encode().equals(edited.encode()),"store reopening retains edit");
        for(LocalDate date:new LocalDate[]{LocalDate.of(2025,1,1),LocalDate.of(2025,3,1),LocalDate.of(2024,2,29)}){
            for(boolean midnight:new boolean[]{false,true}){
                SleepRecord boundary=record(date,midnight,50,1);
                expect(store.save(boundary),"month year leap boundary accepted");
                expect(store.read(date).encode().equals(boundary.encode()),"boundary morning date retained");
            }
        }
        expect(!store.save(record(LocalDate.now().plusDays(1),false,25,2)),"future record rejected");
        expect(store.read(last).encode().equals(edited.encode()),"invalid save leaves valid history intact");
        System.out.println("PASS: "+checks+" historical diary fill, edit, date and isolation checks");
    }
}
