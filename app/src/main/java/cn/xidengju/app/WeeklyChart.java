package cn.xidengju.app;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.time.*;

final class WeeklyChart extends View {
    private final SleepRecord[] records;
    private final LocalDate first;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    WeeklyChart(Context c,SleepRecord[] records,LocalDate first){super(c);this.records=records;this.first=first;setContentDescription("最近七天：雾蓝柱形为睡眠小时，绿色折线为效率百分比；未记录日期留空。下方提供逐日文字记录。");setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float density=getResources().getDisplayMetrics().density;
        float left=30*density,right=getWidth()-34*density,top=30*density,bottom=getHeight()-36*density,height=bottom-top;
        paint.setTextSize(10*getResources().getDisplayMetrics().scaledDensity);paint.setStrokeWidth(density);
        for(int i=0;i<=4;i++){
            float y=bottom-height*i/4;paint.setColor(Color.rgb(43,57,69));canvas.drawLine(left,y,right,y,paint);
            paint.setColor(NightUi.MUTED);paint.setTextAlign(Paint.Align.RIGHT);canvas.drawText(String.valueOf(i*4),left-6*density,y+3*density,paint);
            paint.setTextAlign(Paint.Align.LEFT);canvas.drawText(String.valueOf(i*25),right+5*density,y+3*density,paint);
        }
        paint.setTextAlign(Paint.Align.LEFT);canvas.drawText("小时",0,14*density,paint);paint.setTextAlign(Paint.Align.RIGHT);canvas.drawText("效率%",getWidth(),14*density,paint);
        float step=(right-left)/7,previousX=0,previousY=0;boolean connected=false;
        for(int i=0;i<7;i++){
            float x=left+step*(i+.5f);LocalDate day=first.plusDays(i);paint.setTextAlign(Paint.Align.CENTER);paint.setColor(NightUi.MUTED);
            canvas.drawText(day.getMonthValue()+"/"+day.getDayOfMonth(),x,bottom+22*density,paint);
            SleepRecord r=records[i];if(r==null){connected=false;continue;}
            float barHeight=Math.min(1,r.sleepMinutes()/960f)*height;
            paint.setColor(NightUi.BLUE);paint.setAlpha(170);canvas.drawRoundRect(x-step*.23f,bottom-barHeight,x+step*.23f,bottom,4*density,4*density,paint);paint.setAlpha(255);
            float y=bottom-height*r.efficiency()/100f;paint.setColor(NightUi.GREEN);paint.setStrokeWidth(2*density);
            if(connected)canvas.drawLine(previousX,previousY,x,y,paint);canvas.drawCircle(x,y,3*density,paint);previousX=x;previousY=y;connected=true;
        }
    }
}
