package cn.xidengju.app;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

final class NightUi {
    static final int BG=Color.rgb(16,25,35), CARD=Color.rgb(25,36,47), TEXT=Color.rgb(229,236,235),
        MUTED=Color.rgb(166,182,189), GREEN=Color.rgb(184,215,189), BLUE=Color.rgb(139,174,190);
    private static final Map<String,Bitmap> images=new HashMap<>();
    static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    static GradientDrawable round(Context c,int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(c,radius));return g;}
    static GradientDrawable gradient(Context c,int top,int bottom,int radius){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{top,bottom});g.setCornerRadius(dp(c,radius));return g;}
    static TextView label(Context c,String value,int size){TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(size<=14?MUTED:TEXT);t.setLineSpacing(dp(c,3),1);return t;}
    static void styleButton(Button b,boolean primary){
        Context c=b.getContext();b.setAllCaps(false);b.setTextSize(15);b.setTextColor(primary?BG:TEXT);
        b.setBackgroundTintList(null);b.setBackground(round(c,primary?GREEN:CARD,17));b.setMinHeight(dp(c,54));
        b.setPadding(dp(c,14),dp(c,10),dp(c,14),dp(c,10));b.setStateListAnimator(null);
    }
    static ImageView art(Context c,String file,int height){
        ImageView view=new ImageView(c);view.setScaleType(ImageView.ScaleType.FIT_CENTER);view.setContentDescription("戴睡帽的小水獭");
        Bitmap bitmap=images.get(file);
        if(bitmap==null){try(InputStream input=c.getAssets().open("art/"+file)){
            BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=2;
            bitmap=BitmapFactory.decodeStream(input,null,options);if(bitmap!=null)images.put(file,bitmap);
        }catch(IOException ignored){}}
        if(bitmap!=null)view.setImageBitmap(bitmap);
        view.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(c,height)));return view;
    }
    static void field(EditText input){Context c=input.getContext();input.setTextColor(TEXT);input.setHintTextColor(MUTED);input.setTextSize(16);input.setBackground(round(c,CARD,16));input.setPadding(dp(c,16),dp(c,14),dp(c,16),dp(c,14));}
    static final class MoonDecor extends View {
        private final Paint p=new Paint(3);
        MoonDecor(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        @Override protected void onDraw(Canvas canvas){
            float d=getResources().getDisplayMetrics().density,w=getWidth(),h=getHeight();
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(d);p.setColor(Color.argb(42,188,220,216));
            canvas.drawArc(w*.35f,-h*.35f,w*1.27f,h*.87f,16,146,false,p);
            p.setColor(Color.argb(25,188,220,216));canvas.drawArc(w*.22f,-h*.53f,w*1.42f,h*1.01f,17,150,false,p);
            p.setStyle(Paint.Style.FILL);p.setColor(Color.argb(110,215,231,215));
            float[][] stars={{.73f,.14f,1.4f},{.87f,.25f,1.1f},{.52f,.23f,1.0f},{.91f,.53f,1.2f}};
            for(float[] s:stars)canvas.drawCircle(w*s[0],h*s[1],d*s[2],p);
            p.setColor(Color.argb(20,188,215,189));canvas.drawCircle(w*.76f,h*.54f,d*72,p);
        }
    }
}
