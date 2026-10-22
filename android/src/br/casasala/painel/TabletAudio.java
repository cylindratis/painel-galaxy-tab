package br.casasala.painel;
import android.media.AudioManager;
import android.media.MediaPlayer;
import org.json.JSONArray;
import org.json.JSONObject;

/** Froyo MediaPlayer audio queue. Network preparation is asynchronous. */
public final class TabletAudio {
    public interface Listener {void changed();void error(String message);}
    private final Listener listener;
    private MediaPlayer player;
    private JellyfinClient client;
    private JSONArray queue=new JSONArray();
    private int index,offset,generation;
    private boolean prepared,paused=true,loading,playWhenPrepared;
    public TabletAudio(Listener l){listener=l;}
    public void play(JellyfinClient c,JSONArray items){play(c,items,0);}
    public void play(JellyfinClient c,JSONArray items,int start){client=c;queue=items;index=Math.max(0,Math.min(start,items.length()-1));open(0);}
    public JSONArray queue(){return queue;}
    public int index(){return index;}
    public void jump(int to){if(to>=0&&to<queue.length()){index=to;open(0);}}
    private void open(int start){
        open(start,true);
    }
    private void open(int start,boolean autoplay){
        releasePlayer();final int stamp=++generation;offset=start;prepared=false;loading=true;playWhenPrepared=autoplay;paused=!autoplay;
        try{
            final JSONObject item=queue.getJSONObject(index);player=new MediaPlayer();player.setAudioStreamType(AudioManager.STREAM_MUSIC);player.setDataSource(client.audioUrl(item.getString("Id"),start));
            player.setOnPreparedListener(new MediaPlayer.OnPreparedListener(){public void onPrepared(MediaPlayer p){if(stamp!=generation)return;prepared=true;loading=false;if(playWhenPrepared){p.start();paused=false;}else paused=true;listener.changed();}});
            player.setOnCompletionListener(new MediaPlayer.OnCompletionListener(){public void onCompletion(MediaPlayer p){if(stamp!=generation)return;if(index+1<queue.length()){index++;open(0);}else{paused=true;listener.changed();}}});
            player.setOnErrorListener(new MediaPlayer.OnErrorListener(){public boolean onError(MediaPlayer p,int what,int extra){if(stamp==generation){loading=false;paused=true;prepared=false;listener.error("O tablet não conseguiu abrir o áudio. Confira a conexão e a transcodificação MP3 no Jellyfin.");listener.changed();}return true;}});
            player.prepareAsync();listener.changed();
        }catch(Exception e){loading=false;paused=true;listener.error("Não foi possível abrir esta música no tablet.");listener.changed();}
    }
    public void command(String cmd){try{
        if(cmd.equals("Pause")){if(prepared)player.pause();paused=true;playWhenPrepared=false;}
        if(cmd.equals("Unpause")){if(prepared)player.start();paused=false;playWhenPrepared=true;}
        if(cmd.equals("Stop")){stop();return;}
        if(cmd.equals("NextTrack")&&index+1<queue.length()){index++;open(0);return;}
        if(cmd.equals("PreviousTrack")&&queue.length()>0){index=Math.max(0,index-1);open(0);return;}
        listener.changed();
    }catch(Exception e){listener.error("A reprodução local foi interrompida. Escolha a música novamente.");}}
    public void seek(long ticks){if(queue.length()>0)open((int)Math.min(Integer.MAX_VALUE,ticks/10000),!paused);}
    public long position(){try{return offset*10000L+(prepared?player.getCurrentPosition()*10000L:0);}catch(Exception e){return offset*10000L;}}
    public JSONObject snapshot(){JSONObject s=new JSONObject();try{s.put("Id","__tablet__").put("DeviceId","__tablet__").put("DeviceName","Este tablet").put("Client","Painel").put("PlayState",new JSONObject().put("IsPaused",paused).put("CanSeek",prepared).put("PositionTicks",position()).put("Loading",loading));if(queue.length()>0)s.put("NowPlayingItem",queue.getJSONObject(index));}catch(Exception ignored){}return s;}
    public void stop(){generation++;releasePlayer();queue=new JSONArray();paused=true;loading=false;listener.changed();}
    private void releasePlayer(){prepared=false;if(player!=null){player.reset();player.release();player=null;}}
    public void release(){generation++;releasePlayer();}
}
