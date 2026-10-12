package cn.xidengju.app;

import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import java.util.*;

final class AppProtection {
    static Set<String> packages(Context c) {
        Set<String> result=new HashSet<>(); result.add(c.getPackageName());
        result.add("com.android.systemui"); result.add("com.android.settings");
        for(Intent intent:new Intent[]{new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                new Intent(Intent.ACTION_DIAL,Uri.parse("tel:")),new Intent(Intent.ACTION_SENDTO,Uri.parse("smsto:")),
                new Intent(android.provider.Settings.ACTION_SETTINGS)}) {
            for(ResolveInfo i:c.getPackageManager().queryIntentActivities(intent,PackageManager.MATCH_DEFAULT_ONLY)) result.add(i.activityInfo.packageName);
        }
        return result;
    }
    static boolean isProtected(Context c,String pkg,Set<String> safe) {
        if(safe.contains(pkg))return true;
        try {
            ApplicationInfo info=c.getPackageManager().getApplicationInfo(pkg,0);
            if((info.flags & (ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0)return true;
            String name=(pkg+" "+info.loadLabel(c.getPackageManager())).toLowerCase(Locale.ROOT);
            for(String word:new String[]{"支付","银行","校园","地图","打车","出行","电话","短信","急救","钱包","alipay","tencent.mm","bank","wallet","maps","didi","uber","dialer","messaging"})if(name.contains(word))return true;
            return c.getSharedPreferences("plan",0).getStringSet("protectedApps",Collections.emptySet()).contains(pkg);
        } catch(PackageManager.NameNotFoundException e){return true;}
    }
}
