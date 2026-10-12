package cn.xidengju.app;

public final class BlockRules {
    public static boolean validWindow(int start,int end){return start>=0&&start<1440&&end>=0&&end<1440&&start!=end;}
    public static boolean inWindow(int minute, int start, int end) {
        if(start==end) return false;
        return start<end ? minute>=start && minute<end : minute>=start || minute<end;
    }
    public static boolean shouldBlock(boolean enabled, boolean selected, boolean protectedApp,
            long now, long unlockUntil, long testUntil, int minute, int start, int end) {
        return enabled && selected && !protectedApp && now>=unlockUntil &&
            (now<testUntil || inWindow(minute,start,end));
    }
}
