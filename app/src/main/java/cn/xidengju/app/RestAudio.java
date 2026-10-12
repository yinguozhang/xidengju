package cn.xidengju.app;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.*;

final class RestAudio {
    private MediaPlayer player;
    private AudioFocusRequest focus;
    private final AudioManager manager;
    private final Context context;
    RestAudio(Context context){this.context=context;manager=context.getSystemService(AudioManager.class);}
    boolean play(Runnable interrupted){
        return play(interrupted,0);
    }
    boolean play(Runnable interrupted,int positionMillis){
        stop();
        AudioAttributes attrs=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
        focus=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener(change->{if(change<0){stop();interrupted.run();}}).build();
        if(manager.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){stop();return false;}
        try(AssetFileDescriptor fd=context.getAssets().openFd("audio/moonlight.mp3")){
            player=new MediaPlayer();player.setAudioAttributes(attrs);player.setDataSource(fd.getFileDescriptor(),fd.getStartOffset(),fd.getLength());
            player.setVolume(.65f,.65f);player.setLooping(false);
            player.setOnErrorListener((p,what,extra)->{stop();interrupted.run();return true;});
            player.prepare();player.seekTo(Math.max(0,Math.min(positionMillis,player.getDuration()-1)));player.start();return true;
        }catch(Exception failure){stop();return false;}
    }
    void stop(){if(player!=null){player.release();player=null;}if(focus!=null){manager.abandonAudioFocusRequest(focus);focus=null;}}
}
