package br.casasala.painel;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.widget.HorizontalScrollView;
import java.io.File;
import java.io.InputStream;
import java.util.HashSet;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import java.util.LinkedHashMap;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.SeekBar;
import android.content.res.Configuration;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.media.AudioManager;
import android.os.SystemClock;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

/** Deliberately uses only the original Android 2.2 framework. */
public class MainActivity extends Activity {
    private final Handler handler = new Handler();
    private final ExecutorService worker = Executors.newFixedThreadPool(4);
    private final ExecutorService artWorker=Executors.newFixedThreadPool(2);
    private final LinkedHashMap<String,Bitmap> thumbnails=new LinkedHashMap<String,Bitmap>();
    private JSONArray libraryItems=new JSONArray(),remoteQueue=new JSONArray();
    private int remoteIndex;private String queueDevice="";private boolean remoteSeen,remoteWasPlaying,remotePending;private long remotePosition,remoteDuration,remoteAt;
    private String playerSignature="";
    private TextView quoteView;private JSONObject quote=new JSONObject();private boolean quoteBusy;private long quoteAttempt;
    private final Demo demoData = new Demo();
    private SharedPreferences prefs;
    private DeviceStore store;
    private VisualTheme theme;
    private LinearLayout content, deviceGrid, rows, player, manager;
    private TextView clock, date, weatherTemp, weatherInfo, connection, pageLabel;
    private LinearLayout shortcutRow;
    private AnalogClockView analog;
    private EditText search;
    private JSONArray devices = new JSONArray(), sessions = new JSONArray();
    private JSONObject weather = new JSONObject(), selectedSession;
    private String screen = "clock", kind = "Audio", parent = "", playlist = "", artist = "", query = "";
    private String base = "", token = "", jellyBase = "", jellyToken = "", jellyUser = "", deviceId = "", tuyaMode = "local";
    private String selectedDevice = "", sessionId = "", artId = "", jellyError = "";
    private boolean demo = true, active, pollBusy, sessionBusy, listBusy, commandBusy;
    private boolean analogHome, playerHidden,chooseAfterRefresh;
    private String lightTitle="",lightReturn="home";
    private JSONArray lightMembers=new JSONArray(),pendingLightMembers;
    private JSONObject lightValues=new JSONObject(),pendingLightAction;
    private TextView pendingLightLabel;
    private int page, total, generation, libraryGeneration, quickGeneration;
    private long lastWeather;
    private Bitmap coverBitmap;
    private TabletAudio tablet;
    private TextView progressText, lyricText;
    private SeekBar progressBar;
    private boolean seeking;
    private long sessionObserved;
    private String lyricId="", lyricMessage="";
    private JSONArray lyrics=new JSONArray();
    private int lyricLine=-2;
    private WeatherPanel weatherPanel;
    private final ArrayList<String[]> browseStack = new ArrayList<String[]>();
    private SamsungRemote samsung;private boolean tvBusy;private String tvMessage="Toque em Conexão e autorize o Painel na TV.";
    private VoiceCapture voice;private Button voiceIndicator;private TextView voiceLabel,tvLabel;private String voiceMessage="Microfone desligado";private android.media.MediaPlayer answerPlayer;private int answerGeneration;private String speechError="";

    interface Result { void done(JSONObject value); }
    interface Job { JSONObject run() throws Exception; }

    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences("casa-sala", MODE_PRIVATE);
        store = new DeviceStore(prefs);
        loadSettings();
        tablet=new TabletAudio(new TabletAudio.Listener(){
            public void changed(){if(isTablet()){selectedSession=tablet.snapshot();sessionId="__tablet__";drawPlayer();}}
            public void error(String message){toast(message);}
        });
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        voice=new VoiceCapture(new VoiceCapture.Listener(){public void state(final String state){handler.post(new Runnable(){public void run(){setVoiceMessage(state.equals("listening")?"Ouvindo seu pedido…":state.equals("processing")?"Entendendo…":"Microfone ligado · diga Alexa");}});}public void text(final String value){handler.post(new Runnable(){public void run(){if(active)executeVoice(value);}});}public void error(final String error){handler.post(new Runnable(){public void run(){setVoiceMessage(error);toast(error);}});}});
        try{quote=new JSONObject(prefs.getString("daily_quote","{}"));}catch(Exception ignored){}
        render();
    }

    private void loadSettings() {
        base = prefs.getString("base", ""); token = prefs.getString("token", "");
        jellyBase = prefs.getString("jelly_url", ""); jellyToken = prefs.getString("jelly_token", "");
        jellyUser = prefs.getString("jelly_user", "");
        deviceId = prefs.getString("jelly_device", "");
        if (deviceId.length() == 0) { deviceId = "casasala-" + UUID.randomUUID().toString(); prefs.edit().putString("jelly_device", deviceId).commit(); }
        tuyaMode = prefs.getString("tuya_mode", base.length() > 0 ? "bridge" : "local");
        demo = prefs.getBoolean("demo", true);
        selectedDevice = demo ? "demo-tv" : prefs.getString("device", "");
        theme = VisualTheme.get(prefs.getString("theme", "dark"));
        analogHome = prefs.getBoolean("analog_home", false);
        playerHidden = prefs.getBoolean("player_hidden", false);
        try { if (weather.length() == 0) weather = new JSONObject(prefs.getString("weather_cache", "{}")); } catch (Exception ignored) { }
    }

    protected void onResume() { super.onResume(); active = true; handler.removeCallbacks(ticker); handler.post(ticker); poll(); refreshQuote(); }
    protected void onPause() { active = false; handler.removeCallbacks(ticker);if(voice!=null)voice.stop();setVoiceMessage("Microfone desligado");answerGeneration++;if(answerPlayer!=null){answerPlayer.release();answerPlayer=null;}super.onPause(); }
    protected void onDestroy() { quickGeneration++;if(samsung!=null)samsung.close(); if(tablet!=null)tablet.release(); worker.shutdownNow();artWorker.shutdownNow(); super.onDestroy(); }
    public void onConfigurationChanged(Configuration config){super.onConfigurationChanged(config);render();if(screen.equals("music"))loadLibrary();}
    public boolean onSearchRequested(){navigate("music");handler.postDelayed(new Runnable(){public void run(){if(search!=null){search.requestFocus();((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(search,InputMethodManager.SHOW_IMPLICIT);}}},180);return true;}
    public boolean onKeyDown(int key,KeyEvent event){if(key==KeyEvent.KEYCODE_SEARCH){if(event.getRepeatCount()==0)onSearchRequested();return true;}return super.onKeyDown(key,event);}
    private boolean portrait(){return getResources().getDisplayMetrics().heightPixels>getResources().getDisplayMetrics().widthPixels;}
    private boolean isTablet(){return "__tablet__".equals(selectedDevice);}
    private AnalogClockView dial(){AnalogClockView view=new AnalogClockView(this,theme);view.setStyle(prefs.getString("clock_style","bauhaus"));view.setShowSeconds(prefs.getBoolean("clock_seconds",true));view.setFrameRate(prefs.getInt("clock_fps",60));return view;}

    private final Runnable ticker = new Runnable() {
        int count;
        public void run() {
            if (!active) return;
            updateClock();updateProgress();
            if (++count % (remoteQueue.length()>0?2:5) == 0 && !screen.equals("settings"))refreshSessions(false);
            if (count % 15 == 0) poll();
            handler.postDelayed(this, 1000);
        }
    };

    private void updateClock() {
        if (clock != null) clock.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
        if (date != null) date.setText(new SimpleDateFormat("EEEE, d 'de' MMMM", new Locale("pt", "BR")).format(new Date()));
        if (analog != null) analog.tick();
    }

    private void async(final Job job, final Result result, final boolean silent) {
        final int stamp = generation;
        worker.submit(new Runnable() {
            public void run() {
                try {
                    final JSONObject data = job.run();
                    handler.post(new Runnable() { public void run() { if (!isFinishing() && stamp == generation) result.done(data); } });
                } catch (final Exception failure) {
                    handler.post(new Runnable() { public void run() {
                        if (isFinishing() || stamp != generation) return;
                        if (!silent) toast(message(failure));
                        result.done(null);
                    } });
                }
            }
        });
    }

    private void request(final String path, final JSONObject body, final Result result, final boolean silent) {
        final boolean preview = demo;
        final String requestBase = base, requestToken = token, jb = jellyBase, jt = jellyToken, ju = jellyUser, did = deviceId;
        final String mode = tuyaMode;
        final JSONArray local = store.local();
        async(new Job() { public JSONObject run() throws Exception {
            if (preview) return demoData.get(path, body);
            if (path.equals("/v1/devices") && mode.equals("local")) return obj("devices", TuyaLan.list(local));
            if (path.startsWith("/v1/devices/") && mode.equals("local")) {
                String id = Uri.decode(path.substring("/v1/devices/".length()));
                for (int i = 0; i < local.length(); i++) {
                    JSONObject item = local.getJSONObject(i);
                    if (id.equals(item.optString("id"))) {
                        try{return TuyaLan.setValues(item,LightingProfile.values(item,body));}
                        catch(Exception error){return obj("error",message(error)).put("confirmed",false);}
                    }
                }
                throw new Exception("Dispositivo local não cadastrado.");
            }
            if (path.equals("/v1/weather")) {
                String lat = prefs.getString("weather_lat", ""), lon = prefs.getString("weather_lon", "");
                if (lat.length() > 0 && lon.length() > 0) return WeatherClient.fetch(prefs.getString("weather_city", "Sua cidade"), Double.parseDouble(lat), Double.parseDouble(lon));
                if (requestBase.length() > 0 && requestToken.length() > 0) return Api.json(requestBase, requestToken, path, body);
                return obj("configured", Boolean.FALSE);
            }
            if (!path.startsWith("/v1/devices")) {
                if (jb.length() > 0 && jt.length() > 0) return new JellyfinClient(jb, jt, ju, did).request(path, body);
                if (requestBase.length() == 0 || requestToken.length() == 0) throw new Exception("Conecte o Jellyfin em Ajustes > Quick Connect.");
            }
            if (requestBase.length() == 0 || requestToken.length() == 0) throw new Exception("Configure a ponte opcional ou selecione Tuya local.");
            return Api.json(requestBase, requestToken, path, body);
        } }, result, silent);
    }

    private void poll() {
        if (!active || screen.equals("settings")) return;
        if (!pollBusy) {
            pollBusy = true;
            request("/v1/devices", null, new Result() { public void done(JSONObject data) {
                pollBusy = false;
                if (data != null) devices = data.optJSONArray("devices");
                if (devices == null) devices = new JSONArray();
                if (data == null) for (int i = 0; i < devices.length(); i++) { try { devices.getJSONObject(i).put("online", false).put("on", JSONObject.NULL); } catch (Exception ignored) { } }
                drawDevices(); drawManager(); updateConnection();
            } }, true);
        }
        refreshSessions(false);
        if (System.currentTimeMillis() - lastWeather > 900000) {
            lastWeather = System.currentTimeMillis();
            request("/v1/weather", null, new Result() { public void done(JSONObject data) {
                if (data != null) {
                    weather = data;
                    if (!demo) prefs.edit().putString("weather_cache", data.toString()).putLong("weather_time", System.currentTimeMillis()).commit();
                }
                drawWeather();if(weatherPanel!=null)weatherPanel.update(weather,demo);
            } }, true);
        }
    }

    private void updateConnection() {
        if (connection == null) return;
        connection.setText(demo ? "DEMO" : tuyaMode.equals("local") ? "LOCAL" : "PONTE");
    }

    private void refreshSessions(final boolean choose) {
        if(isTablet()){selectedSession=tablet.snapshot();sessionId="__tablet__";drawPlayer();if(choose)chooseSession();return;}
        if(choose)chooseAfterRefresh=true;
        if (sessionBusy || seeking) return;
        sessionBusy = true;
        request("/v1/sessions", null, new Result() { public void done(JSONObject data) {
            sessionBusy = false; selectedSession = null; sessionId = "";
            sessions = data == null ? new JSONArray() : data.optJSONArray("sessions");
            if (sessions == null) sessions = new JSONArray();
            jellyError = data == null ? "Conecte em Ajustes > Jellyfin" : sessions.length() == 0 ? "Abra o Jellyfin na TV" : "Selecione a TV";
            for (int i = 0; i < sessions.length(); i++) {
                JSONObject item = sessions.optJSONObject(i);
                if (item != null && selectedDevice.length() > 0 && selectedDevice.equals(item.optString("DeviceId"))) {
                    selectedSession = item; sessionId = item.optString("Id"); break;
                }
            }
            sessionObserved=SystemClock.elapsedRealtime();
            if(isTablet()){selectedSession=tablet.snapshot();sessionId="__tablet__";}
            if(data!=null)observeRemoteQueue();
            drawPlayer();
            if (chooseAfterRefresh){chooseAfterRefresh=false;chooseSession();}
        } }, !choose);
    }

    private void chooseSession() {
        final JSONArray options=sessions;
        String[] labels=new String[options.length()+1];labels[0]="Este tablet · música";
        for(int i=0;i<options.length();i++){JSONObject s=options.optJSONObject(i);labels[i+1]=s.optString("DeviceName","TV")+" · "+s.optString("Client");}
        new AlertDialog.Builder(this).setTitle("Saída de reprodução").setItems(labels,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int p){
            if(p==0){selectedDevice="__tablet__";selectedSession=tablet.snapshot();sessionId="__tablet__";}
            else {if(tablet!=null)tablet.command("Pause");selectedSession=options.optJSONObject(p-1);selectedDevice=selectedSession.optString("DeviceId");sessionId=selectedSession.optString("Id");}
            if(!demo)prefs.edit().putString("device",selectedDevice).commit();sessionObserved=SystemClock.elapsedRealtime();drawPlayer();
        }}).setNeutralButton("Atualizar TVs",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int p){
            final boolean local=isTablet(); if(local)selectedDevice="";
            refreshSessions(true);if(local)selectedDevice="__tablet__";
        }}).setNegativeButton("Fechar",null).show();
    }

    private void navigate(String target) {
        screen = target; render();
        if (target.equals("music")) loadLibrary();
        if (!target.equals("settings")) poll();
    }

    private void render() {
        voiceLabel=null;tvLabel=null;
        playerSignature="";shortcutRow=null;quoteView=null;clock = null; date = null; analog = null; weatherTemp = null; weatherInfo = null; weatherPanel=null;progressBar=null;progressText=null;lyricText=null;
        deviceGrid = null; rows = null; player = null; manager = null; pageLabel = null; search = null;
        LinearLayout root = column(); root.setBackgroundDrawable(theme.pageBackground()); setContentView(root);
        LinearLayout nav = row(); nav.setGravity(Gravity.CENTER_VERTICAL); nav.setPadding(dp(10), dp(3), dp(10), dp(3)); nav.setBackgroundColor(theme.nav);
        String[] titles = {"Relógio", "Painel", "Mídia", "Tempo"}; final String[] targets = {"clock", "home", "music", "weather"};
        for(int i=0;i<titles.length;i++){final String target=targets[i];Button tab=button(titles[i],screen.equals(target));tab.setTextSize(14);tab.setSingleLine(true);tab.setCompoundDrawables(new PanelIcon(target.equals("home")?"home":target.equals("music")?"music":target.equals("weather")?"weather":"clock",theme.controlInk(screen.equals(target)),dp(18)),null,null,null);tab.setCompoundDrawablePadding(dp(5));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(42),1);lp.setMargins(i==0?0:dp(5),0,0,0);nav.addView(tab,lp);tab.setOnClickListener(new View.OnClickListener(){public void onClick(View v){navigate(target);}});}
        connection=null;
        voiceIndicator=null;if(voice!=null&&voice.running()){voiceIndicator=new IconButton(this,"Microfone ligado. Toque para desligar.","mic",theme,true);LinearLayout.LayoutParams micLayout=new LinearLayout.LayoutParams(dp(42),dp(42));micLayout.leftMargin=dp(5);nav.addView(voiceIndicator,micLayout);voiceIndicator.setOnClickListener(new View.OnClickListener(){public void onClick(View v){voice.stop();setVoiceMessage("Microfone desligado");render();}});}
        root.addView(nav, new LinearLayout.LayoutParams(-1, dp(48)));
        content = column();if(theme.id.equals("lcars")){content.setBackgroundDrawable(new LcarsFrame(getResources().getDisplayMetrics().density));content.setPadding(dp(34),dp(19),dp(12),dp(18));}else content.setPadding(dp(14), dp(12), dp(14), dp(12)); root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        if (screen.equals("settings")) renderSettings();
        else if(screen.equals("tv"))renderTv();
        else if(screen.equals("voice"))renderVoice();
        else if (screen.equals("music")) renderMusic();
        else if (screen.equals("clock")) renderClock();
        else if (screen.equals("lights")) renderLights();
        else if (screen.equals("now")) renderNow();
        else if (screen.equals("weather")){weatherPanel=new WeatherPanel(this,theme);content.addView(weatherPanel,new LinearLayout.LayoutParams(-1,-1));weatherPanel.update(weather,demo);}
        else if (screen.equals("devices")) renderManager();
        else renderHome();
        updateConnection(); updateClock();
    }

    private void chooseTheme() {
        final String[] ids = {"dark", "light", "bauhaus", "skeuo", "home", "lcars"};
        new AlertDialog.Builder(this).setTitle("Estilo do painel").setItems(new String[]{"Escuro original", "Branco", "Bauhaus · creme e formas", "Skeumorfismo · tátil", "Casa · inspirado no Apple Home", "LCARS · The Next Generation"}, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int p) { prefs.edit().putString("theme", ids[p]).commit(); theme = VisualTheme.get(ids[p]); render(); if (screen.equals("music")) loadLibrary(); }
        }).show();
    }

    private void renderHome(){
        LinearLayout heading=row();heading.setGravity(Gravity.CENTER_VERTICAL);heading.addView(text("Dispositivos",24,theme.ink),new LinearLayout.LayoutParams(0,-2,1));Button groups=button("Grupos",false),edit=button("Editar",false);heading.addView(groups,new LinearLayout.LayoutParams(dp(76),dp(38)));hgap(heading,6);heading.addView(edit,new LinearLayout.LayoutParams(dp(68),dp(38)));hgap(heading,6);Button television=new IconButton(this,"TV","tv",theme,false);heading.addView(television,new LinearLayout.LayoutParams(dp(42),dp(38)));television.setContentDescription("Controle da TV");television.setOnClickListener(new View.OnClickListener(){public void onClick(View v){navigate("tv");}});content.addView(heading);groups.setOnClickListener(new View.OnClickListener(){public void onClick(View v){chooseLightGroup();}});edit.setOnClickListener(new View.OnClickListener(){public void onClick(View v){navigate("devices");}});gap(content,8);
        deviceGrid=portrait()?column():row();content.addView(deviceGrid,new LinearLayout.LayoutParams(-1,0,1));deviceGrid.post(new Runnable(){public void run(){drawDevices();}});
    }
    private void renderClock(){
        LinearLayout main=portrait()?column():row();main.setGravity(Gravity.CENTER_VERTICAL);content.addView(main,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout info=column();info.addView(new GreetingView(this,theme),new LinearLayout.LayoutParams(-1,dp(38)));date=text("",14,theme.muted);info.addView(date);gap(info,8);
        if(portrait())main.addView(info,new LinearLayout.LayoutParams(-1,-2));
        ClockFrame dialWrap=new ClockFrame(this);analog=dial();dialWrap.addView(analog);main.addView(dialWrap,portrait()?new LinearLayout.LayoutParams(-1,0,1):new LinearLayout.LayoutParams(0,-1,1));
        LinearLayout details=portrait()?column():info;weatherTemp=text("--°",32,theme.ink);details.addView(weatherTemp);weatherInfo=text("",14,theme.muted);weatherInfo.setMaxLines(2);weatherInfo.setEllipsize(TextUtils.TruncateAt.END);details.addView(weatherInfo);gap(details,10);
        shortcutRow=row();details.addView(shortcutRow,new LinearLayout.LayoutParams(-1,dp(70)));drawShortcuts();gap(details,8);quoteView=text("",12,theme.muted);quoteView.setMaxLines(1);quoteView.setEllipsize(TextUtils.TruncateAt.END);details.addView(quoteView);quoteView.setOnClickListener(new View.OnClickListener(){public void onClick(View v){showQuote();}});drawQuote();
        if(portrait())main.addView(details,new LinearLayout.LayoutParams(-1,-2));else{hgap(main,18);main.addView(info,new LinearLayout.LayoutParams(0,-2,1));}drawWeather();
    }
    private JSONObject groupById(String id){JSONArray list=groups();for(int i=0;i<list.length();i++)if(id.equals(list.optJSONObject(i).optString("id")))return list.optJSONObject(i);return null;}
    private JSONObject managedById(String id){JSONArray all=allManaged();for(int i=0;i<all.length();i++){JSONObject d=all.optJSONObject(i);if(id.equals(d.optString("id")))return d;}return null;}
    private void drawShortcuts(){if(shortcutRow==null)return;shortcutRow.removeAllViews();
        for(int i=0;i<2;i++){final int slot=i;String id=prefs.getString("clock_shortcut_"+i,"");final JSONObject group=id.startsWith("group:")?groupById(id.substring(6)):null;final JSONObject chosen=group==null?managedById(id):null;boolean enabled=false;String label="Escolher atalho "+(i+1);
            if(group!=null){JSONArray ids=group.optJSONArray("members");int on=0,online=0;for(int k=0;ids!=null&&k<ids.length();k++){JSONObject d=managedById(ids.optString(k));if(d!=null&&d.optBoolean("online")){online++;if(d.optBoolean("on"))on++;}}enabled=on>0;label=group.optString("name")+"\n"+(ids==null||ids.length()==0?"Escolha os membros":online==0?"Sem resposta":on+" de "+ids.length()+" ligadas");}
            else if(chosen!=null){enabled=chosen.optBoolean("online")&&chosen.optBoolean("on");label=store.name(chosen)+"\n"+(chosen.optBoolean("online")?(chosen.optBoolean("on")?"Ligada":"Desligada"):"Tentar conexão");}
            Button tile=button(label,enabled);tile.setTextSize(14);tile.setMaxLines(3);tile.setEllipsize(TextUtils.TruncateAt.END);tile.setContentDescription("Atalho "+(i+1)+". Segure para personalizar");LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1);lp.setMargins(i==0?0:dp(8),0,0,0);shortcutRow.addView(tile,lp);
            tile.setOnClickListener(new View.OnClickListener(){public void onClick(View v){if(group!=null){JSONArray ids=group.optJSONArray("members");if(ids==null||ids.length()==0){editGroups();return;}boolean anyOn=false;for(int k=0;k<ids.length();k++){JSONObject d=managedById(ids.optString(k));if(d!=null&&d.optBoolean("on")&&d.optBoolean("online"))anyOn=true;}final TextView state=text("",14,theme.ink);applyLights(ids,obj("on",Boolean.valueOf(!anyOn)),state);toast("Atualizando "+group.optString("name"));}else if(chosen!=null)toggleDevice(chosen);else chooseShortcut(slot);}});tile.setOnLongClickListener(new View.OnLongClickListener(){public boolean onLongClick(View v){chooseShortcut(slot);return true;}});
        }
    }
    private void chooseShortcut(final int slot){final JSONArray all=allManaged(),sets=groups();String[] names=new String[1+sets.length()+all.length()];names[0]="Sem atalho";for(int i=0;i<sets.length();i++)names[i+1]="Grupo · "+sets.optJSONObject(i).optString("name");for(int i=0;i<all.length();i++)names[1+sets.length()+i]=store.name(all.optJSONObject(i))+" · "+store.room(all.optJSONObject(i));new AlertDialog.Builder(this).setTitle("Escolher atalho "+(slot+1)).setItems(names,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){String value=which==0?"":which<=sets.length()?"group:"+sets.optJSONObject(which-1).optString("id"):all.optJSONObject(which-1-sets.length()).optString("id");prefs.edit().putString("clock_shortcut_"+slot,value).commit();drawShortcuts();}}).setNegativeButton("Cancelar",null).show();}

    private void clockOptions(){new AlertDialog.Builder(this).setTitle("Relógio").setItems(new String[]{"Bauhaus · movimento suave","Quadrado · StandBy","Movimento a até 60 quadros/s","Movimento a até 30 quadros/s"},new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int p){
        if(p<2)prefs.edit().putString("clock_style",p==0?"bauhaus":"square").putBoolean("clock_seconds",true).commit();else prefs.edit().putInt("clock_fps",p==2?60:30).commit();render();
    }}).show();}

    private void drawWeather() {
        if (weatherTemp == null) return;
        if (!weather.optBoolean("configured")) { weatherTemp.setText("--°"); weatherInfo.setText("Escolha sua cidade em Ajustes.\nClima direto · Open-Meteo"); return; }
        int code = weather.optInt("code");
        String condition = code == 0 ? "Céu limpo" : code <= 3 ? "Entre nuvens" : code <= 48 ? "Neblina" : code <= 67 ? "Chuva" : code <= 77 ? "Neve" : code <= 86 ? "Pancadas" : "Trovoadas";
        weatherTemp.setText(Math.round(weather.optDouble("temperature")) + "°");
        boolean old = !demo && System.currentTimeMillis() - prefs.getLong("weather_time", 0) > 1800000;
        if(screen.equals("clock")){weatherInfo.setText(weather.optString("city")+" · "+WeatherAdvice.rain(weather));return;}
        weatherInfo.setText(weather.optString("city") + " · " + condition + "\n" + Math.round(weather.optDouble("low")) + "° / " + Math.round(weather.optDouble("high")) +
            "° · Chuva " + weather.optInt("rain") + "%" + (screen.equals("home") ? "" : "\n" + (demo ? "Dados de exemplo" : old ? "Última previsão salva · Open-Meteo" : "Open-Meteo · atualizado")));
    }

    private void drawDevices(){
        drawShortcuts();if(deviceGrid==null)return;deviceGrid.removeAllViews();JSONArray shown=store.ordered(devices,false);if(shown.length()==0){deviceGrid.addView(text("Importe seu arquivo em Menu > Importar lâmpadas\nou adicione um dispositivo em Editar.",18,theme.muted));return;}
        LinkedHashMap<String,ArrayList<JSONObject>> rooms=new LinkedHashMap<String,ArrayList<JSONObject>>();for(int i=0;i<shown.length();i++){JSONObject d=shown.optJSONObject(i);String room=store.room(d);if(!rooms.containsKey(room))rooms.put(room,new ArrayList<JSONObject>());rooms.get(room).add(d);}
        int h=deviceGrid.getHeight()>0?deviceGrid.getHeight():getResources().getDisplayMetrics().heightPixels-dp(112),roomIndex=0,totalRows=0;for(ArrayList<JSONObject> list:rooms.values())totalRows+=(list.size()+1)/2;
        int portraitHeight=Math.min(dp(176),Math.max(dp(80),(h-dp(rooms.size()*30+(rooms.size()-1)*8)-dp(totalRows*6))/Math.max(1,totalRows)));
        for(String name:rooms.keySet()){ArrayList<JSONObject> items=rooms.get(name);int cols=portrait()?Math.min(2,items.size()):items.size()>=3?2:1,lines=(items.size()+cols-1)/cols;LinearLayout room=column();LinearLayout.LayoutParams rp=portrait()?new LinearLayout.LayoutParams(-1,-2):new LinearLayout.LayoutParams(0,-1,cols);rp.setMargins(portrait()?0:roomIndex==0?0:dp(10),portrait()&&roomIndex>0?dp(8):0,0,0);deviceGrid.addView(room,rp);roomIndex++;
            LinearLayout label=row();label.setGravity(Gravity.CENTER_VERTICAL);label.addView(text(name,18,theme.ink),new LinearLayout.LayoutParams(0,-2,1));label.addView(text(String.valueOf(items.size()),12,theme.muted));room.addView(label,new LinearLayout.LayoutParams(-1,dp(28)));int tileHeight=portrait()?portraitHeight:Math.max(dp(88),(h-dp(32)-dp(lines*8))/Math.max(1,lines));LinearLayout line=null;
            for(int i=0;i<items.size();i++){if(i%cols==0){line=row();room.addView(line,new LinearLayout.LayoutParams(-1,tileHeight));if(i>0)((LinearLayout.LayoutParams)line.getLayoutParams()).topMargin=dp(6);}LinearLayout tile=deviceTile(items.get(i),tileHeight,false);LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-1,1);tp.setMargins(0,0,i%cols==cols-1?0:dp(7),0);line.addView(tile,tp);}if(items.size()%cols!=0)line.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        }
    }
    private LinearLayout deviceTile(final JSONObject device,int height,boolean featured){
        boolean on=device.optBoolean("on"),online=device.optBoolean("online");LinearLayout tile=column();tile.setBackgroundDrawable(theme.background(this,on&&online?theme.onPanel:theme.panel,on&&online));tile.setPadding(dp(10),dp(7),dp(10),dp(7));LinearLayout status=row();status.setGravity(Gravity.CENTER_VERTICAL);ImageView icon=new ImageView(this);icon.setImageDrawable(new PanelIcon("bulb",on&&online?theme.accent:theme.muted,dp(18)));status.addView(icon,new LinearLayout.LayoutParams(dp(18),dp(18)));hgap(status,6);status.addView(text(online?(on?"Ligada":"Desligada"):"Sem resposta",12,theme.muted));tile.addView(status);gap(tile,3);TextView name=text(store.name(device),16,theme.ink);name.setMaxLines(1);name.setEllipsize(TextUtils.TruncateAt.END);tile.addView(name);tile.addView(new View(this),new LinearLayout.LayoutParams(1,0,1));Button adjust=button(online?"Cor / brilho":"Tentar conexão",false);adjust.setTextSize(13);adjust.setContentDescription("Cor e brilho de "+store.name(device));tile.addView(adjust,new LinearLayout.LayoutParams(-1,dp(32)));adjust.setOnClickListener(new View.OnClickListener(){public void onClick(View v){if(!device.optBoolean("online")){retryDevice(device);return;}lightDialog(store.name(device),new JSONArray().put(device.optString("id")));}});tile.setOnClickListener(new View.OnClickListener(){public void onClick(View v){toggleDevice(device);}});tile.setOnLongClickListener(new View.OnLongClickListener(){public boolean onLongClick(View v){deviceActions(device);return true;}});return tile;
    }
    private void retryDevice(final JSONObject device){final JSONObject config=store.findLocal(device.optString("id"));if(config==null){pollBusy=false;poll();toast("Atualizando os dispositivos…");return;}toast("Consultando "+store.name(device)+"…");async(new Job(){public JSONObject run(){return TuyaLan.status(config);}},new Result(){public void done(JSONObject state){if(state==null)return;for(int i=0;i<devices.length();i++)if(config.optString("id").equals(devices.optJSONObject(i).optString("id")))try{devices.put(i,state);}catch(Exception ignored){}drawDevices();drawManager();toast(state.optBoolean("online")?store.name(device)+" conectada. Toque para controlar.":store.name(device)+": "+state.optString("error","Confira a alimentação e o Wi-Fi da lâmpada."));}},false);}
    private void toggleDevice(final JSONObject device){if(!device.optBoolean("online")){retryDevice(device);return;}if(commandBusy)return;commandBusy=true;request("/v1/devices/"+Uri.encode(device.optString("id")),obj("on",Boolean.valueOf(!device.optBoolean("on"))),new Result(){public void done(JSONObject result){commandBusy=false;if(result==null)toast("Sem confirmação da lâmpada.");else if(result.has("error"))toast(result.optString("error"));else if(!result.optBoolean("confirmed"))toast("Aguardando confirmação do estado.");pollBusy=false;poll();}},false);}

    private void renderMusic() {
        LinearLayout bar = row(); bar.setGravity(Gravity.CENTER_VERTICAL);
        Button category = button(kindLabel(), true); bar.addView(category, new LinearLayout.LayoutParams(dp(100), dp(40)));
        category.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { chooseLibrary(); } });
        hgap(bar, 7);
        search = edit("Buscar na biblioteca", query, false); bar.addView(search, new LinearLayout.LayoutParams(0, dp(40), 1));
        Button find = new IconButton(this,"Buscar","search",theme,false); bar.addView(find, new LinearLayout.LayoutParams(dp(44), dp(40)));
        find.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { query = search.getText().toString().trim(); page = 0; loadLibrary(); } });
        Button toggle = new IconButton(this,prefs.getBoolean("library_grid",false)?"Lista":"Grade",prefs.getBoolean("library_grid",false)?"list":"grid",theme,false); bar.addView(toggle, new LinearLayout.LayoutParams(dp(44), dp(40)));
        toggle.setOnClickListener(new View.OnClickListener(){public void onClick(View v){prefs.edit().putBoolean("library_grid",!prefs.getBoolean("library_grid",false)).commit();render();loadLibrary();}});
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);search.setOnEditorActionListener(new TextView.OnEditorActionListener(){public boolean onEditorAction(TextView v,int action,KeyEvent event){if(action==EditorInfo.IME_ACTION_SEARCH || (event!=null&&event.getKeyCode()==KeyEvent.KEYCODE_ENTER)){query=search.getText().toString().trim();page=0;loadLibrary();return true;}return false;}});
        content.addView(bar); gap(content, 9);
        LinearLayout body = row(); content.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout library = column();
        if (!browseStack.isEmpty()) {
            Button back = button("< Voltar à lista anterior", false); library.addView(back, new LinearLayout.LayoutParams(-1, dp(28)));
            back.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { browseBack(); } }); gap(library, 5);
        }
        ScrollView scroll = new ScrollView(this); rows = column(); scroll.addView(rows); library.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout paging = row(); paging.setGravity(Gravity.CENTER_VERTICAL);
        Button previous = button("<", false), next = button(">", false);
        previous.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if (!listBusy && page > 0) { page--; loadLibrary(); } } });
        next.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if (!listBusy && (page + 1) * 40 < total) { page++; loadLibrary(); } } });
        paging.addView(previous, new LinearLayout.LayoutParams(dp(44), dp(36))); pageLabel = text("", 10, theme.muted); pageLabel.setGravity(Gravity.CENTER);
        paging.addView(pageLabel, new LinearLayout.LayoutParams(0, -2, 1)); paging.addView(next, new LinearLayout.LayoutParams(dp(44), dp(36))); gap(library, 5); library.addView(paging);
        body.addView(library, new LinearLayout.LayoutParams(0, -1, portrait()||playerHidden ? 1f : .60f));
        if(portrait()){gap(content,8);player=card();content.addView(player,new LinearLayout.LayoutParams(-1,dp(76)));drawPlayer();}
        if (!playerHidden && !portrait()) {
            hgap(body, 11); ScrollView playerScroll = new ScrollView(this); player = card(); playerScroll.addView(player);
            body.addView(playerScroll, new LinearLayout.LayoutParams(0, -1, .40f)); drawPlayer();
        }
    }

    private String kindLabel() {
        if (kind.equals("MusicAlbum")) return "Álbuns"; if (kind.equals("Playlist")) return "Playlists";
        if (kind.equals("MusicArtist")) return "Artistas"; if (kind.equals("Movie")) return "Filmes";
        if (kind.equals("Series")) return "Séries"; if (kind.equals("Season")) return "Temporadas"; if (kind.equals("Episode")) return "Episódios";
        return "Músicas";
    }

    private void chooseLibrary() {
        final String[] ids = {"Audio", "MusicAlbum", "Playlist", "MusicArtist", "Movie", "Series"};
        new AlertDialog.Builder(this).setTitle("Biblioteca Jellyfin").setItems(new String[]{"Músicas", "Álbuns", "Playlists", "Artistas", "Filmes", "Séries"}, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int p) { kind = ids[p]; parent = ""; artist = ""; playlist = ""; query = ""; page = 0; browseStack.clear(); render(); loadLibrary(); }
        }).show();
    }

    private void loadLibrary() {
        if (rows == null) return;
        final LinearLayout target = rows; final int stamp = ++libraryGeneration; listBusy = true;
        target.removeAllViews(); target.addView(text("Carregando biblioteca...", 13, theme.muted));
        String path = "/v1/library?kind=" + kind + "&start=" + page * 40 + "&q=" + Uri.encode(query) + "&parent=" + Uri.encode(parent) + "&playlist=" + Uri.encode(playlist) + "&artist=" + Uri.encode(artist);
        request(path, null, new Result() { public void done(JSONObject data) {
            if (stamp != libraryGeneration || rows != target) return; listBusy = false; target.removeAllViews();
            if (data == null) { target.addView(text("Conecte o Jellyfin em Ajustes.\nDepois toque em Buscar.", 14, theme.muted)); return; }
            JSONArray items = data.optJSONArray("items"); total = data.optInt("total");
            if (pageLabel != null) pageLabel.setText("Página " + (page + 1) + " · " + total + " itens");
            if (items == null || items.length() == 0) { target.addView(text("Nenhum item encontrado.", 14, theme.muted)); return; }
            libraryItems=items;final JSONArray loaded=items;target.post(new Runnable(){public void run(){if(rows==target&&stamp==libraryGeneration)drawLibrary(target,loaded);}});

        } }, false);
    }

    private void itemTapped(final JSONObject item) {
        String type = item.optString("Type");
        if (type.equals("Audio") || type.equals("Movie") || type.equals("Episode")) { play(item); return; }
        if (type.equals("Series") || type.equals("Season")) { enterItem(item); return; }
        new AlertDialog.Builder(this).setTitle(item.optString("Name")).setItems(new String[]{isTablet()?"Tocar agora no tablet":"Tocar agora na TV", "Ver faixas"}, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int p) { if (p == 0) play(item); else enterItem(item); }
        }).show();
    }

    private void enterItem(JSONObject item) {
        browseStack.add(new String[]{kind, parent, playlist, artist, query, String.valueOf(page)});
        String type = item.optString("Type");
        parent = (type.equals("MusicAlbum") || type.equals("Series") || type.equals("Season")) ? item.optString("Id") : "";
        playlist = type.equals("Playlist") ? item.optString("Id") : ""; artist = type.equals("MusicArtist") ? item.optString("Id") : "";
        kind = type.equals("Series") ? "Season" : type.equals("Season") ? "Episode" : "Audio"; page = 0; query = ""; render(); loadLibrary();
    }

    private void browseBack() {
        if (browseStack.isEmpty()) return;
        String[] b = browseStack.remove(browseStack.size() - 1); kind = b[0]; parent = b[1]; playlist = b[2]; artist = b[3]; query = b[4]; page = Integer.parseInt(b[5]); render(); loadLibrary();
    }

    private void renderNow(){
        LinearLayout top=row();top.setGravity(Gravity.CENTER_VERTICAL);top.addView(text("Reproduzindo",22,theme.ink),new LinearLayout.LayoutParams(0,-2,1));
        Button back=button("Biblioteca",false);top.addView(back,new LinearLayout.LayoutParams(dp(110),dp(40)));back.setOnClickListener(new View.OnClickListener(){public void onClick(View v){navigate("music");}});content.addView(top);gap(content,10);
        ScrollView scroll=new ScrollView(this);player=column();scroll.addView(player);content.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));drawPlayer();
    }

    private void drawPlayer(){
        if(player==null)return;
        if(isTablet()&&tablet!=null){selectedSession=tablet.snapshot();sessionId="__tablet__";}
        JSONObject playing=selectedSession==null?null:selectedSession.optJSONObject("NowPlayingItem"),state=selectedSession==null?null:selectedSession.optJSONObject("PlayState");
        boolean paused=state==null || state.optBoolean("IsPaused",true);
        String title=playing==null?"Escolha o que tocar":playing.optString("Name"),subtitle=playing==null?"Jellyfin · música, filmes e séries":join(playing.optJSONArray("Artists"));
        if(subtitle.length()==0&&playing!=null)subtitle=playing.optString("SeriesName","Jellyfin");
        String signature=screen+":"+selectedDevice+":"+title+":"+subtitle+":"+paused+":"+(state==null?"":state.optBoolean("CanSeek")+":"+state.optBoolean("Loading"))+":"+(coverBitmap==null?0:coverBitmap.hashCode())+":"+(playing==null?"":playing.optString("Id"))+":"+(isTablet()?tablet.index():remoteIndex)+":"+(isTablet()?tablet.queue().length():remoteQueue.length());
        if(signature.equals(playerSignature)){updateProgress();return;}playerSignature=signature;player.removeAllViews();progressBar=null;progressText=null;lyricText=null;
        boolean small=screen.equals("home")||(screen.equals("music")&&portrait()),full=screen.equals("now");
        LinearLayout info=column();TextView name=text(title,small?16:26,theme.ink);name.setMaxLines(small?1:2);name.setEllipsize(TextUtils.TruncateAt.END);info.addView(name);
        TextView artistLabel=text(subtitle,12,theme.muted);artistLabel.setMaxLines(1);artistLabel.setEllipsize(TextUtils.TruncateAt.END);info.addView(artistLabel);
        Button output=button(isTablet()?"Tablet · trocar":selectedSession==null?"Saída: escolher":"TV · trocar",false);output.setTextSize(14);output.setOnClickListener(new View.OnClickListener(){public void onClick(View v){refreshSessions(true);}});
        if(small){
            LinearLayout line=row();line.setGravity(Gravity.CENTER_VERTICAL);line.addView(info,new LinearLayout.LayoutParams(0,-2,1));line.addView(control(paused?"Tocar":"Pausar",paused?"Unpause":"Pause",true),new LinearLayout.LayoutParams(dp(44),dp(44)));hgap(line,6);
            Button open=button("Player",false);line.addView(open,new LinearLayout.LayoutParams(dp(70),dp(44)));open.setOnClickListener(new View.OnClickListener(){public void onClick(View v){navigate("now");}});player.addView(line);return;
        }
        if(!full){drawSidePlayer(playing,state,title,subtitle,paused);return;}
        LinearLayout body=full&&!portrait()?row():column();player.addView(body);
        String imageId=playing==null?"":imageId(playing);
        boolean video=playing!=null&&("Movie".equals(playing.optString("Type"))||"Episode".equals(playing.optString("Type")));
        float ratio=video?2f/3f:1f;
        MediaArtView art=new MediaArtView(this,ratio,theme);art.setContentDescription(video?"Cartaz":"Capa do álbum");
        if(coverBitmap!=null&&imageId.equals(artId))art.setImageBitmap(coverBitmap);else if(!demo&&imageId.length()>0&&!imageId.equals(artId))loadArt(imageId);
        int width=(int)(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density);
        int side=full?(portrait()?Math.min(width-56,video?160:210):Math.min((int)(width*.35f),compact()?180:290)):(compact()?80:130);
        LinearLayout artHolder=column();artHolder.setGravity(Gravity.CENTER);artHolder.addView(art,new LinearLayout.LayoutParams(dp(video?(int)(side*.75f):side),-2));
        if(full&&!portrait()){body.addView(artHolder,new LinearLayout.LayoutParams(0,-2,.4f));hgap(body,20);}else{body.addView(artHolder,new LinearLayout.LayoutParams(-1,-2));gap(body,14);}
        LinearLayout controls=column();body.addView(controls,full&&!portrait()?new LinearLayout.LayoutParams(0,-2,.6f):new LinearLayout.LayoutParams(-1,-2));
        controls.addView(info);gap(controls,8);controls.addView(output,new LinearLayout.LayoutParams(-1,dp(40)));
        if(selectedSession!=null&&!full){controls.addView(text(selectedSession.optString("DeviceName")+(demo?" · exemplo":""),11,theme.muted));}
        if(state!=null&&state.optBoolean("Loading"))controls.addView(text("Preparando áudio…",12,theme.muted));
        progressText=text("0:00 / 0:00",12,theme.muted);gap(controls,8);controls.addView(progressText);
        progressBar=new ThinSeekBar(this,theme);progressBar.setContentDescription("Andamento da reprodução");progressBar.setMax(1000);progressBar.setEnabled(playing!=null&&state!=null&&state.optBoolean("CanSeek"));controls.addView(progressBar,new LinearLayout.LayoutParams(-1,dp(44)));
        progressBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){seeking=true;}public void onProgressChanged(SeekBar b,int p,boolean user){}public void onStopTrackingTouch(SeekBar b){seeking=false;JSONObject item=selectedSession==null?null:selectedSession.optJSONObject("NowPlayingItem");if(item!=null)seek(item.optLong("RunTimeTicks")*b.getProgress()/1000);}});
        LinearLayout commands=row();commands.addView(control("Anterior","PreviousTrack",false),new LinearLayout.LayoutParams(0,dp(54),1));hgap(commands,8);commands.addView(control(paused?"Tocar":"Pausar",paused?"Unpause":"Pause",true),new LinearLayout.LayoutParams(0,dp(60),1));hgap(commands,8);commands.addView(control("Próxima","NextTrack",false),new LinearLayout.LayoutParams(0,dp(54),1));controls.addView(commands);gap(controls,6);
        LinearLayout volume=row();Button less=control("Vol −","VolumeDown",false),more=control("Vol +","VolumeUp",false);JSONArray supported=selectedSession==null?null:selectedSession.optJSONArray("SupportedCommands");less.setEnabled(isTablet()||contains(supported,"VolumeDown"));more.setEnabled(isTablet()||contains(supported,"VolumeUp"));volume.addView(less,new LinearLayout.LayoutParams(full?dp(42):0,dp(40),full?0:1));hgap(volume,4);volume.addView(more,new LinearLayout.LayoutParams(full?dp(42):0,dp(40),full?0:1));hgap(volume,4);volume.addView(control("Parar","Stop",false),new LinearLayout.LayoutParams(full?dp(42):0,dp(40),full?0:1));controls.addView(volume);
        final JSONArray currentQueue=isTablet()?tablet.queue():remoteQueue;int at=isTablet()?tablet.index():remoteIndex;
        if(currentQueue.length()>0){Button queueButton=button("Fila · "+(at+1)+" de "+currentQueue.length()+" faixas",false);queueButton.setContentDescription("Abrir fila de reprodução");if(full){hgap(volume,6);volume.addView(queueButton,new LinearLayout.LayoutParams(0,dp(40),1));}else{gap(controls,8);controls.addView(queueButton,new LinearLayout.LayoutParams(-1,dp(40)));}queueButton.setOnClickListener(new View.OnClickListener(){public void onClick(View v){showQueue();}});}

        if(!full){gap(controls,8);Button expand=button("Capa, progresso e letra",false);controls.addView(expand,new LinearLayout.LayoutParams(-1,dp(40)));expand.setOnClickListener(new View.OnClickListener(){public void onClick(View v){navigate("now");}});}
        else{gap(player,18);player.addView(text(video?"Sobre este vídeo":"Letra",17,theme.ink));gap(player,8);lyricText=text("",16,theme.muted);lyricText.setLineSpacing(dp(7),1);player.addView(lyricText);
            if(video)lyricText.setText(playing.optString("Overview","Sem sinopse disponível."));else{String id=playing==null?"":playing.optString("Id");if(!lyricId.equals(id))loadLyrics(id);drawLyrics();}}
        updateProgress();
    }

    private void drawSidePlayer(JSONObject item,JSONObject state,String title,String subtitle,boolean paused){
        LinearLayout top=row();top.setGravity(Gravity.CENTER_VERTICAL);boolean video=item!=null&&poster(item);int h=compact()?76:112;float ratio=video?2f/3f:1f;MediaArtView art=item==null?new MediaArtView(this,ratio,theme):thumbnail(item,ratio);top.addView(art,new LinearLayout.LayoutParams(dp(Math.round(h*ratio)),-2));hgap(top,12);LinearLayout labels=column();TextView heading=text(title,18,theme.ink);heading.setMaxLines(2);heading.setEllipsize(TextUtils.TruncateAt.END);labels.addView(heading);gap(labels,5);TextView sub=text(subtitle,13,theme.muted);sub.setMaxLines(2);sub.setEllipsize(TextUtils.TruncateAt.END);labels.addView(sub);top.addView(labels,new LinearLayout.LayoutParams(0,-2,1));player.addView(top);gap(player,6);
        Button output=button(isTablet()?"Tablet · trocar":selectedSession==null?"Escolher saída":"TV · trocar",false);player.addView(output,new LinearLayout.LayoutParams(-1,dp(34)));output.setOnClickListener(new View.OnClickListener(){public void onClick(View v){refreshSessions(true);}});gap(player,4);
        progressText=text("",12,theme.muted);player.addView(progressText);progressBar=new ThinSeekBar(this,theme);progressBar.setMax(1000);progressBar.setContentDescription("Andamento da reprodução");progressBar.setEnabled(item!=null&&state!=null&&state.optBoolean("CanSeek"));player.addView(progressBar,new LinearLayout.LayoutParams(-1,dp(32)));progressBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){seeking=true;}public void onProgressChanged(SeekBar b,int n,boolean user){}public void onStopTrackingTouch(SeekBar b){seeking=false;JSONObject now=selectedSession==null?null:selectedSession.optJSONObject("NowPlayingItem");if(now!=null)seek(now.optLong("RunTimeTicks")*b.getProgress()/1000);}});
        LinearLayout commands=row();commands.addView(control("Anterior","PreviousTrack",false),new LinearLayout.LayoutParams(0,dp(44),1));hgap(commands,6);commands.addView(control(paused?"Tocar":"Pausar",paused?"Unpause":"Pause",true),new LinearLayout.LayoutParams(0,dp(44),1));hgap(commands,6);commands.addView(control("Próxima","NextTrack",false),new LinearLayout.LayoutParams(0,dp(44),1));player.addView(commands);gap(player,6);
        LinearLayout actions=row();Button queue=button("Fila",false),expand=button("Ampliar",false);queue.setEnabled((isTablet()?tablet.queue():remoteQueue).length()>0);actions.addView(queue,new LinearLayout.LayoutParams(0,dp(36),1));hgap(actions,6);actions.addView(expand,new LinearLayout.LayoutParams(0,dp(36),1));queue.setOnClickListener(new View.OnClickListener(){public void onClick(View v){showQueue();}});expand.setOnClickListener(new View.OnClickListener(){public void onClick(View v){navigate("now");}});player.addView(actions);updateProgress();
    }

    private long position(){
        if(isTablet())return tablet.position();
        JSONObject state=selectedSession==null?null:selectedSession.optJSONObject("PlayState");if(state==null)return 0;
        long result=state.optLong("PositionTicks");if(!state.optBoolean("IsPaused",true)&&sessionObserved>0)result+=Math.min(15000,SystemClock.elapsedRealtime()-sessionObserved)*10000;return result;
    }
    private void updateProgress(){if(progressText==null||selectedSession==null)return;JSONObject item=selectedSession.optJSONObject("NowPlayingItem");long total=item==null?0:item.optLong("RunTimeTicks"),pos=Math.min(total,position());progressText.setText(duration(pos)+" / "+duration(total));if(progressBar!=null&&!seeking)progressBar.setProgress(total==0?0:(int)(pos*1000/total));if(screen.equals("now"))drawLyrics();}
    private void seek(long ticks){if(isTablet()){tablet.seek(ticks);return;}JSONObject body=obj("session_id",sessionId);put(body,"command","Seek");put(body,"position_ticks",Long.valueOf(ticks));request("/v1/command",body,new Result(){public void done(JSONObject d){refreshSessions(false);}},false);}
    private void loadLyrics(final String id){lyricId=id;lyrics=new JSONArray();lyricLine=-2;lyricMessage=id.length()==0?"Escolha uma música para ver a letra.":"Carregando letra…";
        if(id.length()==0||demo){lyricMessage=demo?"Demonstração sem letra. As letras vêm da sua biblioteca Jellyfin.":lyricMessage;return;}
        request("/v1/lyrics/"+Uri.encode(id),null,new Result(){public void done(JSONObject data){if(!lyricId.equals(id))return;lyrics=data==null?new JSONArray():data.optJSONArray("Lyrics");if(lyrics==null)lyrics=new JSONArray();lyricMessage=data==null?"Não foi possível consultar a letra. Verifique a conexão e o suporte a letras do servidor.":lyrics.length()==0?"Esta faixa não tem letra no Jellyfin. Adicione um arquivo .lrc ou .txt junto à música.":"";drawLyrics();}},true);
    }
    private void drawLyrics(){if(lyricText==null)return;JSONObject playing=selectedSession==null?null:selectedSession.optJSONObject("NowPlayingItem");if(playing!=null&&!"Audio".equals(playing.optString("Type")))return;
        if(lyrics.length()==0){lyricText.setText(lyricMessage);return;}
        long ticks=position();int current=-1;boolean timed=false;for(int i=0;i<lyrics.length();i++){JSONObject line=lyrics.optJSONObject(i);if(line!=null&&line.has("Start")&&!line.isNull("Start")){timed=true;if(line.optLong("Start")<=ticks)current=i;}}
        if(lyricLine==current&&lyricText.length()>0)return;lyricLine=current;android.text.SpannableStringBuilder text=new android.text.SpannableStringBuilder();
        for(int i=0;i<lyrics.length();i++){JSONObject line=lyrics.optJSONObject(i);if(line==null)continue;int begin=text.length();text.append(line.optString("Text")).append("\n");if(timed&&i==current){text.setSpan(new android.text.style.ForegroundColorSpan(theme.ink),begin,text.length(),0);text.setSpan(new android.text.style.StyleSpan(Typeface.BOLD),begin,text.length(),0);}}
        lyricText.setText(text);
    }

    private void loadArt(final String id) {
        artId = id; coverBitmap = null; final int stamp = generation;
        final String jb = jellyBase, jt = jellyToken, ju = jellyUser, did = deviceId, rb = base, rt = token;
        worker.submit(new Runnable() { public void run() {
            try {
                byte[] bytes = jb.length() > 0 && jt.length() > 0 ? new JellyfinClient(jb, jt, ju, did).bytes("/v1/art/" + id) : Api.bytes(rb, rt, "/v1/art/" + Uri.encode(id), null);
                BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
                if(bounds.outWidth<=0||bounds.outHeight<=0)throw new Exception("Imagem inválida.");
                BitmapFactory.Options decode=new BitmapFactory.Options();decode.inPreferredConfig=Bitmap.Config.RGB_565;decode.inSampleSize=1;
                while(bounds.outWidth/decode.inSampleSize>420||bounds.outHeight/decode.inSampleSize>420)decode.inSampleSize*=2;
                final Bitmap image = BitmapFactory.decodeByteArray(bytes, 0, bytes.length,decode);
                handler.post(new Runnable() { public void run() { if (generation == stamp && artId.equals(id)) { coverBitmap = image; drawPlayer(); } } });
            } catch (Exception ignored) { }
        } });
    }

    private Button control(String title, final String command, boolean accent) {
        Button button = (command.equals("VolumeUp")||command.equals("VolumeDown")||command.equals("Stop"))?new IconButton(this,title,command.equals("Stop")?"stop":command.equals("VolumeUp")?"volumeUp":"volumeDown",theme,accent):(command.equals("Pause")||command.equals("Unpause")||command.equals("NextTrack")||command.equals("PreviousTrack"))?new TransportButton(this,title,command,theme,accent):button(title,accent); button.setEnabled(sessionId.length() > 0);
        button.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            if(isTablet()){
                if(command.equals("VolumeUp")||command.equals("VolumeDown")){((AudioManager)getSystemService(AUDIO_SERVICE)).adjustStreamVolume(AudioManager.STREAM_MUSIC,command.equals("VolumeUp")?AudioManager.ADJUST_RAISE:AudioManager.ADJUST_LOWER,AudioManager.FLAG_SHOW_UI);return;}
                tablet.command(command);return;
            }
            if(remoteQueue.length()>0&&queueDevice.equals(selectedDevice)){
                if(command.equals("NextTrack")||command.equals("PreviousTrack")){sendRemoteTrack(remoteIndex+(command.equals("NextTrack")?1:-1));return;}
                if(command.equals("Stop"))clearRemoteQueue();
            }
            if (commandBusy || sessionId.length() == 0) return; commandBusy = true;
            JSONObject body = obj("session_id", sessionId); put(body, "command", command);
            request("/v1/command", body, new Result() { public void done(JSONObject data) { commandBusy = false; handler.postDelayed(new Runnable() { public void run() { if (active) refreshSessions(false); } }, 1000); } }, false);
        } }); return button;
    }

    private void play(final JSONObject item){
        if(commandBusy)return;
        final String type=item.optString("Type");boolean audio=type.equals("Audio")||type.equals("MusicAlbum")||type.equals("Playlist")||type.equals("MusicArtist")||type.equals("MusicGenre");
        if(isTablet()&&(!audio||demo)){toast(demo?"Conecte o Jellyfin para ouvir no tablet.":"Selecione a TV para assistir a vídeos.");return;}
        if(!isTablet()&&sessionId.length()==0){toast("Escolha Tablet ou TV no player primeiro.");refreshSessions(true);return;}
        if(!audio||demo){clearRemoteQueue();JSONObject body=obj("session_id",sessionId);put(body,"item_id",item.optString("Id"));commandBusy=true;request("/v1/play",body,new Result(){public void done(JSONObject d){commandBusy=false;if(d!=null){navigate("now");handler.postDelayed(new Runnable(){public void run(){refreshSessions(false);}},600);}}},false);return;}
        JSONObject selection=item;
        if(type.equals("Audio")){
            if(playlist.length()>0)selection=obj("Id",playlist);else if(artist.length()>0)selection=obj("Id",artist);else if(parent.length()>0)selection=obj("Id",parent);else if(item.optString("AlbumId").length()>0)selection=obj("Id",item.optString("AlbumId"));
            if(selection!=item)put(selection,"Type",playlist.length()>0?"Playlist":artist.length()>0?"MusicArtist":"MusicAlbum");
        }
        final JSONObject container=selection;final String jb=jellyBase,jt=jellyToken,ju=jellyUser,did=deviceId,target=selectedDevice;commandBusy=true;
        async(new Job(){public JSONObject run()throws Exception{return obj("queue",new JellyfinClient(jb,jt,ju,did).audioQueue(container));}},new Result(){public void done(JSONObject result){commandBusy=false;if(result==null||!target.equals(selectedDevice))return;JSONArray tracks=result.optJSONArray("queue");if(tracks==null||tracks.length()==0)return;int start=0;for(int i=0;i<tracks.length();i++)if(item.optString("Id").equals(tracks.optJSONObject(i).optString("Id")))start=i;
            if(isTablet()){try{tablet.play(new JellyfinClient(jb,jt,ju,did),tracks,start);navigate("now");}catch(Exception e){toast(message(e));}}
            else{remoteQueue=tracks;queueDevice=target;sendRemoteTrack(start);navigate("now");}
        }},false);
    }

    private void clearRemoteQueue(){remoteQueue=new JSONArray();remoteSeen=false;remoteWasPlaying=false;remotePending=false;queueDevice="";}
    private void sendRemoteTrack(final int index){
        if(index<0||index>=remoteQueue.length()||remotePending||commandBusy||!queueDevice.equals(selectedDevice))return;
        final JSONObject track=remoteQueue.optJSONObject(index);final String device=queueDevice;remotePending=true;commandBusy=true;
        JSONObject body=obj("session_id",sessionId);put(body,"item_id",track.optString("Id"));
        request("/v1/play",body,new Result(){public void done(JSONObject data){remotePending=false;commandBusy=false;if(!device.equals(queueDevice))return;if(data==null){toast("Não foi possível iniciar a próxima faixa. Abra a fila e tente novamente.");return;}remoteIndex=index;remoteSeen=false;remoteWasPlaying=false;remotePosition=0;remoteDuration=track.optLong("RunTimeTicks");remoteAt=SystemClock.elapsedRealtime();drawPlayer();handler.postDelayed(new Runnable(){public void run(){if(active)refreshSessions(false);}},500);}},false);
    }
    private void observeRemoteQueue(){
        if(remoteQueue.length()==0||remotePending||!queueDevice.equals(selectedDevice)||selectedSession==null)return;
        JSONObject now=selectedSession.optJSONObject("NowPlayingItem"),state=selectedSession.optJSONObject("PlayState");long time=SystemClock.elapsedRealtime();
        if(now!=null){String expected=remoteQueue.optJSONObject(remoteIndex).optString("Id");if(!expected.equals(now.optString("Id"))){if(remoteSeen)clearRemoteQueue();return;}
            remoteSeen=true;remotePosition=state==null?0:state.optLong("PositionTicks");remoteDuration=now.optLong("RunTimeTicks");remoteWasPlaying=state!=null&&!state.optBoolean("IsPaused",true);remoteAt=time;
        }else if(remoteSeen&&remoteWasPlaying&&remoteDuration>0&&remotePosition+(time-remoteAt)*10000>=remoteDuration-15000000L){
            remoteSeen=false;remoteWasPlaying=false;if(remoteIndex+1<remoteQueue.length())sendRemoteTrack(remoteIndex+1);
        }else if(remoteSeen){clearRemoteQueue();}
    }
    private void showQueue(){final boolean local=isTablet();final JSONArray q=local?tablet.queue():remoteQueue;int index=local?tablet.index():remoteIndex;String[] labels=new String[q.length()];for(int i=0;i<labels.length;i++)labels[i]=(i==index?"Tocando · ":(i+1)+". ")+q.optJSONObject(i).optString("Name");new AlertDialog.Builder(this).setTitle("Fila de reprodução · "+q.length()+" faixas").setItems(labels,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){if(local)tablet.jump(which);else sendRemoteTrack(which);}}).setNegativeButton("Fechar",null).show();}

    public boolean onCreateOptionsMenu(Menu menu){menu.add(0,1,0,"Ajustes");menu.add(0,2,1,"Tema");menu.add(0,3,2,"Personalizar dispositivos");menu.add(0,4,3,"Grupos de luzes");menu.add(0,5,4,"Player");menu.add(0,6,5,"Importar lâmpadas");menu.add(0,7,6,"Controle da TV");menu.add(0,8,7,"Voz · Alexa");return true;}
    public boolean onOptionsItemSelected(MenuItem item){switch(item.getItemId()){case 1:navigate("settings");return true;case 2:chooseTheme();return true;case 3:navigate("devices");return true;case 4:chooseLightGroup();return true;case 5:navigate("now");return true;case 6:chooseImport();return true;case 7:navigate("tv");return true;case 8:navigate("voice");return true;}return super.onOptionsItemSelected(item);}

    private String imageId(JSONObject item){if(item.optString("Type").equals("Episode")&&item.optString("SeriesId").length()>0)return item.optString("SeriesId");JSONObject tags=item.optJSONObject("ImageTags");if(tags!=null&&tags.has("Primary"))return item.optString("Id");String id=item.optString("AlbumId");if(id.length()==0)id=item.optString("SeriesId");return id.length()>0?id:item.optString("Id");}
    private boolean poster(JSONObject item){String t=item.optString("Type");return t.equals("Movie")||t.equals("Series")||t.equals("Season")||t.equals("Episode");}
    private MediaArtView thumbnail(JSONObject item,float ratio){final MediaArtView view=new MediaArtView(this,ratio,theme);final String id=imageId(item);view.setContentDescription(item.optString("Name")+" · "+(ratio<1?"cartaz":"capa"));Bitmap cached=thumbnails.get(id);if(cached!=null){view.setImageBitmap(cached);return view;}if(demo||jellyBase.length()==0||jellyToken.length()==0)return view;
        final int stamp=generation;final String jb=jellyBase,jt=jellyToken,ju=jellyUser,did=deviceId;
        artWorker.submit(new Runnable(){public void run(){try{byte[] bytes=new JellyfinClient(jb,jt,ju,did).art(id);BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);BitmapFactory.Options opts=new BitmapFactory.Options();opts.inPreferredConfig=Bitmap.Config.RGB_565;opts.inSampleSize=1;while(bounds.outWidth/opts.inSampleSize>180||bounds.outHeight/opts.inSampleSize>270)opts.inSampleSize*=2;final Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,opts);handler.post(new Runnable(){public void run(){if(stamp!=generation||bitmap==null)return;thumbnails.put(id,bitmap);while(thumbnails.size()>24)thumbnails.remove(thumbnails.keySet().iterator().next());view.setImageBitmap(bitmap);}});}catch(Exception ignored){}}});return view;
    }
    private void drawLibrary(LinearLayout target,JSONArray items){
        target.removeAllViews();boolean grid=prefs.getBoolean("library_grid",false);float density=getResources().getDisplayMetrics().density;int width=(int)(target.getWidth()/density);int columns=Math.max(2,Math.min(4,width/135));LinearLayout line=null;
        for(int i=0;i<items.length();i++){final JSONObject item=items.optJSONObject(i);if(item==null)continue;boolean video=poster(item);LinearLayout entry=grid?column():row();entry.setGravity(grid?Gravity.TOP:Gravity.CENTER_VERTICAL);entry.setPadding(dp(grid?0:9),dp(grid?0:8),dp(grid?0:9),dp(8));if(!grid)entry.setBackgroundDrawable(theme.background(this,theme.panel,false));
            MediaArtView image=thumbnail(item,video?2f/3f:1f);if(grid)entry.addView(image,new LinearLayout.LayoutParams(-1,-2));else{entry.addView(image,new LinearLayout.LayoutParams(dp(video?40:54),-2));hgap(entry,10);}
            LinearLayout detail=column();TextView name=text(item.optString("Name"),grid?15:16,theme.ink);name.setMaxLines(grid?2:1);name.setEllipsize(TextUtils.TruncateAt.END);detail.addView(name);String by=join(item.optJSONArray("Artists"));if(by.length()==0)by=item.optString("SeriesName",kindLabel());TextView meta=text(by,13,theme.muted);meta.setSingleLine(true);meta.setEllipsize(TextUtils.TruncateAt.END);detail.addView(meta);if(grid){gap(entry,7);entry.addView(detail);}else{entry.addView(detail,new LinearLayout.LayoutParams(0,-2,1));entry.addView(text(duration(item.optLong("RunTimeTicks")),12,theme.muted));}
            entry.setContentDescription(item.optString("Name"));entry.setOnClickListener(new View.OnClickListener(){public void onClick(View v){itemTapped(item);}});
            if(grid){if(i%columns==0){line=row();target.addView(line);}LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(0,0,i%columns==columns-1?0:dp(12),dp(15));line.addView(entry,lp);}else{LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,dp(6));target.addView(entry,lp);}
        }
        if(grid&&line!=null&&items.length()%columns!=0)for(int j=items.length()%columns;j<columns;j++)line.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
    }
    private String today(){return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());}
    private void refreshQuote(){if(quoteBusy||today().equals(prefs.getString("quote_day",""))||System.currentTimeMillis()-quoteAttempt<3600000)return;quoteBusy=true;quoteAttempt=System.currentTimeMillis();async(new Job(){public JSONObject run()throws Exception{return QuoteClient.fetch();}},new Result(){public void done(JSONObject data){quoteBusy=false;if(data!=null){quote=data;prefs.edit().putString("daily_quote",data.toString()).putString("quote_day",today()).commit();}drawQuote();}},true);}
    private void drawQuote(){if(quoteView==null)return;String phrase=quote.optString("text");quoteView.setText(phrase.length()>0?phrase+" — "+quote.optString("author"):"Frase do dia · toque para consultar");}
    private void showQuote(){if(quote.optString("text").length()==0){quoteAttempt=0;refreshQuote();toast("Consultando frase do dia…");return;}String detail=quote.optString("text")+"\n\n"+quote.optString("author")+"\nQuotable · "+prefs.getString("quote_day","")+(quote.optBoolean("translated")?"\nTradução automática; original:\n"+quote.optString("original"):"\nTexto original em inglês");new AlertDialog.Builder(this).setTitle("Frase do dia").setMessage(detail).setPositiveButton("Fechar",null).show();}

    private void chooseImport(){new AlertDialog.Builder(this).setTitle("Importar lâmpadas").setItems(new String[]{"Escolher arquivo JSON","Arquivos no tablet"},new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){if(which==1){browseImport(android.os.Environment.getExternalStorageDirectory());return;}Intent intent=new Intent(Intent.ACTION_GET_CONTENT);intent.setType("*/*");intent.addCategory(Intent.CATEGORY_OPENABLE);try{startActivityForResult(intent,51);}catch(android.content.ActivityNotFoundException noPicker){browseImport(android.os.Environment.getExternalStorageDirectory());}}}).setNegativeButton("Cancelar",null).show();}
    protected void onActivityResult(int code,int result,Intent data){super.onActivityResult(code,result,data);if(code==51&&result==RESULT_OK&&data!=null&&data.getData()!=null)importUri(data.getData());}
    private void browseImport(final File directory){
        final File root=android.os.Environment.getExternalStorageDirectory();try{if(!directory.getCanonicalPath().startsWith(root.getCanonicalPath()+"/")&&!directory.getCanonicalPath().equals(root.getCanonicalPath()))throw new Exception();}catch(Exception error){toast("Escolha um arquivo no armazenamento do tablet.");return;}
        File[] files=directory.listFiles();if(files==null){toast("Armazenamento indisponível. Confira o cartão SD e a conexão USB.");return;}java.util.Arrays.sort(files,new java.util.Comparator<File>(){public int compare(File a,File b){if(a.isDirectory()!=b.isDirectory())return a.isDirectory()?-1:1;return a.getName().compareToIgnoreCase(b.getName());}});final ArrayList<File> options=new ArrayList<File>();ArrayList<String> names=new ArrayList<String>();if(!directory.equals(root)){options.add(directory.getParentFile());names.add(".. Voltar");}for(int i=0;i<files.length&&options.size()<200;i++)if(files[i].isDirectory()||files[i].getName().toLowerCase(Locale.US).endsWith(".json")){options.add(files[i]);names.add(files[i].isDirectory()?files[i].getName()+"/":files[i].getName());}if(options.size()==0){toast("Copie lampadas.json para a pasta Download do tablet.");return;}
        new AlertDialog.Builder(this).setTitle("Arquivo · "+directory.getName()).setItems(names.toArray(new String[names.size()]),new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){File chosen=options.get(which);if(chosen.isDirectory())browseImport(chosen);else importUri(Uri.fromFile(chosen));}}).setNegativeButton("Cancelar",null).show();
    }
    private void importUri(final Uri uri){final JSONArray existing=store.local();async(new Job(){public JSONObject run()throws Exception{InputStream input=getContentResolver().openInputStream(uri);if(input==null)throw new Exception("Não foi possível abrir o arquivo.");try{java.io.ByteArrayOutputStream data=new java.io.ByteArrayOutputStream();byte[] bytes=new byte[4096];int n;while((n=input.read(bytes))!=-1){if(data.size()+n>1048576)throw new Exception("Arquivo maior que 1 MB.");data.write(bytes,0,n);}String raw=new String(data.toByteArray(),"UTF-8");if(raw.startsWith("\ufeff"))raw=raw.substring(1);Object top=new org.json.JSONTokener(raw).nextValue();if(top instanceof JSONObject&&((JSONObject)top).optString("schema").equals("painel-voz")){JSONObject config=(JSONObject)top;java.net.URL url=new java.net.URL(config.getString("url"));String secret=config.getString("token");if(!url.getProtocol().equals("http")||url.getUserInfo()!=null||url.getQuery()!=null||url.getRef()!=null||secret.length()<24||secret.indexOf('\n')>=0||secret.indexOf('\r')>=0)throw new Exception("Configuração de voz inválida.");return config;}return DeviceImport.parse(raw,existing);}finally{input.close();}}},new Result(){public void done(final JSONObject result){if(result==null)return;if(result.optString("schema").equals("painel-voz")){new AlertDialog.Builder(MainActivity.this).setTitle("Conectar ponte de voz?").setMessage("Endereço: "+result.optString("url")+"\nA chave fica privada no tablet. O microfone continuará desligado até você ativá-lo.").setPositiveButton("Importar voz",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){voice.stop();prefs.edit().putString("voice_url",result.optString("url")).putString("voice_token",result.optString("token")).commit();navigate("voice");}}).setNegativeButton("Cancelar",null).show();return;}JSONArray all=result.optJSONArray("devices");String message=result.optInt("count")+" lâmpada(s) no arquivo.\n"+result.optInt("added")+" nova(s), "+result.optInt("updated")+" atualização(ões).\n\n";for(int i=0;i<all.length()&&i<30;i++){JSONObject d=all.optJSONObject(i);message+=d.optString("name")+" · "+d.optString("room")+"\n";}message+="\nAs chaves ficam nas preferências privadas do app. A importação não aciona as lâmpadas.";new AlertDialog.Builder(MainActivity.this).setTitle("Conferir importação").setMessage(message).setPositiveButton("Importar",new DialogInterface.OnClickListener(){public void onClick(DialogInterface dialog,int which){prefs.edit().putString("local_devices",result.optJSONArray("devices").toString()).putBoolean("demo",false).putString("tuya_mode","local").commit();resetConnections();navigate("home");toast("Lâmpadas importadas. Os estados serão consultados.");}}).setNegativeButton("Cancelar",null).show();}},false);}

    private void renderManager() {
        LinearLayout top = row(); top.setGravity(Gravity.CENTER_VERTICAL); top.addView(text("Seus dispositivos", 20, theme.ink), new LinearLayout.LayoutParams(0, -2, 1));
        Button groupEdit=button("Grupos",false);top.addView(groupEdit,new LinearLayout.LayoutParams(dp(70),dp(34)));groupEdit.setOnClickListener(new View.OnClickListener(){public void onClick(View v){editGroups();}});
        Button add = button("Adicionar Tuya", true); top.addView(add, new LinearLayout.LayoutParams(dp(128), dp(34))); add.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { editDevice(null); } }); content.addView(top);
        gap(content, 7); content.addView(text("Mude a ordem, o nome e o ambiente. Segure um cartão no painel para abrir estas opções.", 11, theme.muted)); gap(content, 10);
        ScrollView scroll = new ScrollView(this); manager = column(); scroll.addView(manager); content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1)); drawManager();
    }

    private JSONArray allManaged() {
        JSONArray all = new JSONArray(); ArrayList<String> ids = new ArrayList<String>();
        for (int i = 0; i < devices.length(); i++) { JSONObject d = devices.optJSONObject(i); if (d != null) { all.put(d); ids.add(d.optString("id")); } }
        JSONArray local = store.local();
        for (int i = 0; i < local.length(); i++) { JSONObject d = local.optJSONObject(i); if (d != null && !ids.contains(d.optString("id"))) all.put(d); }
        return all;
    }

    private void drawManager() {
        if (manager == null) return; manager.removeAllViews(); JSONArray all = store.ordered(allManaged(), true);
        if (all.length() == 0) manager.addView(text("Nenhum dispositivo cadastrado.\nUse ID, IP e chave local obtidos na sua conta Tuya.", 14, theme.muted));
        for (int i = 0; i < all.length(); i++) {
            final JSONObject d = all.optJSONObject(i); final String id = d.optString("id");
            LinearLayout line = card(); LinearLayout top = row(); top.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout labels = column(); labels.addView(text(store.name(d), 15, theme.ink)); labels.addView(text(store.room(d) + (store.hidden(id) ? " · oculto" : " · visível"), 10, theme.muted)); top.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
            Button up = button("Subir", false), down = button("Descer", false), options = button("Editar", true);
            top.addView(up, new LinearLayout.LayoutParams(dp(65), dp(32))); hgap(top, 4); top.addView(down, new LinearLayout.LayoutParams(dp(65), dp(32))); hgap(top, 4); top.addView(options, new LinearLayout.LayoutParams(dp(65), dp(32)));
            up.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { store.move(allManaged(), id, -1); drawManager(); } });
            down.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { store.move(allManaged(), id, 1); drawManager(); } });
            options.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { deviceActions(d); } });
            line.addView(top); manager.addView(line); gap(manager, 7);
        }
    }

    private void deviceActions(final JSONObject d) {
        final String id = d.optString("id");
        new AlertDialog.Builder(this).setTitle(store.name(d)).setItems(new String[]{"Renomear", "Alterar ambiente", "Mover antes", "Mover depois", store.hidden(id) ? "Mostrar no painel" : "Ocultar do painel", "Editar conexão local", "Perfil RGBW / consultar DPs"}, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int position) {
                if (position == 0 || position == 1) renameDevice(d, position == 0);
                if (position == 2 || position == 3) { store.move(allManaged(), id, position == 2 ? -1 : 1); drawDevices(); drawManager(); }
                if (position == 4) { store.putMap("device_hidden", id, Boolean.valueOf(!store.hidden(id))); drawDevices(); drawManager(); }
                if(position==6){JSONObject local=store.findLocal(id);if(local==null)toast("Cadastre uma lâmpada local primeiro.");else profileDialog(local);}
                if (position == 5) { JSONObject local = store.findLocal(id); if (local == null) toast("Controle vindo da demonstração ou ponte. Adicione um Tuya local para editar a conexão."); else editDevice(local); }
            }
        }).show();
    }

    private void renameDevice(final JSONObject d, final boolean name) {
        final EditText field = edit(name ? "Nome" : "Ambiente", name ? store.name(d) : store.room(d), false);
        new AlertDialog.Builder(this).setTitle(name ? "Nome no painel" : "Ambiente").setView(field).setPositiveButton("Salvar", new DialogInterface.OnClickListener() { public void onClick(DialogInterface dialog, int which) {
            String value = field.getText().toString().trim(); if (value.length() == 0) return;
            store.putMap(name ? "device_names" : "device_rooms", d.optString("id"), value); drawDevices(); drawManager();
        } }).setNegativeButton("Cancelar", null).show();
    }

    private void editDevice(final JSONObject old) {
        final LinearLayout form = column(); form.setPadding(dp(14), dp(12), dp(14), dp(12)); form.setBackgroundColor(theme.bg);
        final EditText name = labeled(form, "Nome", old == null ? "" : store.name(old), false);
        final EditText room = labeled(form, "Ambiente", old == null ? "Sala" : store.room(old), false);
        final EditText id = labeled(form, "ID do dispositivo Tuya", old == null ? "" : old.optString("device_id"), false);
        final EditText ip = labeled(form, "IP local (reserve no roteador)", old == null ? "" : old.optString("ip"), false);
        final EditText key = labeled(form, "Local key (16 bytes)", old == null ? "" : old.optString("local_key"), true);
        final EditText version = labeled(form, "Protocolo real: 3.1, 3.2, 3.3, 3.4 ou 3.5", old == null ? "3.3" : old.optString("version"), false);
        final EditText dpField = labeled(form, "DP booleano do canal (ex.: 1 ou 20)", old == null ? "" : old.optString("switch_dp"), false);
        form.addView(text("A conta Tuya de desenvolvedor serve para obter a local key. Esta conexão usa a rede local, sem login na nuvem por comando.", 11, theme.muted));
        ScrollView scroll = new ScrollView(this); scroll.addView(form);
        final AlertDialog dialog = new AlertDialog.Builder(this).setTitle(old == null ? "Adicionar Tuya Wi-Fi" : "Conexão Tuya local").setView(scroll).setPositiveButton("Salvar", null).setNegativeButton("Cancelar", null).create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() { public void onShow(DialogInterface ignored) {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
                try {
                    String n = name.getText().toString().trim(), did = id.getText().toString().trim(), address = ip.getText().toString().trim(), k = key.getText().toString();
                    double protocol = Double.parseDouble(version.getText().toString().trim()); int channel = Integer.parseInt(dpField.getText().toString().trim());
                    if (n.length() == 0 || did.length() == 0 || !address.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}") || k.getBytes("UTF-8").length != 16 || channel < 1 ||
                        !TuyaLan.supported(protocol)) throw new Exception("Confira os dados da conexão.");
                    JSONObject device = old==null?obj("id","local-"+UUID.randomUUID().toString()):new JSONObject(old.toString());
                    put(device, "name", n); put(device, "room", room.getText().toString().trim()); put(device, "device_id", did); put(device, "ip", address); put(device, "local_key", k); put(device, "version", Double.valueOf(protocol)); put(device, "switch_dp", Integer.valueOf(channel));
                    TuyaLan.validate(device);
                    store.saveLocal(device); store.putMap("device_names", device.optString("id"), n); store.putMap("device_rooms", device.optString("id"), room.getText().toString().trim());
                    prefs.edit().putString("tuya_mode", "local").putBoolean("demo", false).commit(); resetConnections(); dialog.dismiss(); navigate("devices");
                } catch (Exception error) { toast(error.getMessage() == null ? "Confira nome, ID, IP, chave de 16 bytes, protocolo e DP numérico." : message(error)); }
            } });
        } });
        if (old != null) dialog.setButton(AlertDialog.BUTTON_NEUTRAL, "Excluir", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
            new AlertDialog.Builder(MainActivity.this).setTitle("Remover este controle?").setMessage("Isso remove apenas o cadastro no painel.").setPositiveButton("Remover", new DialogInterface.OnClickListener() { public void onClick(DialogInterface dd, int ww) {
                store.remove(old.optString("id")); devices = new JSONArray(); pollBusy = false; poll(); drawManager();
            } }).setNegativeButton("Cancelar", null).show();
        } });
        dialog.show();
    }

    private String groupPreference(){return demo?"light_groups_demo":"light_groups";}
    private JSONArray groups() {
        try {
            if(prefs.contains(groupPreference())){JSONArray saved=new JSONArray(prefs.getString(groupPreference(),"[]"));boolean changed=false;for(int j=0;j<saved.length();j++)if(!saved.optJSONObject(j).has("id")){saved.optJSONObject(j).put("id","legacy-group-"+j);changed=true;}if(changed)prefs.edit().putString(groupPreference(),saved.toString()).commit();return saved;}
            JSONArray out=new JSONArray();String[] names={"Teto da sala","Sala inteira","Quarto 1","Quarto 2"};
            for(int i=0;i<4;i++){JSONArray ids=new JSONArray();if(demo){int start=i<2?0:i==2?4:6,end=i==0?3:i==1?4:i==2?6:7;for(int j=start;j<end;j++)ids.put("demo"+j);}if(!demo){JSONArray all=allManaged();for(int k=0;k<all.length();k++){JSONObject d=all.optJSONObject(k);String room=store.room(d),n=store.name(d).toLowerCase(Locale.US);if((i<2&&room.equalsIgnoreCase("Sala")&&(i==1||!(n.indexOf("abajur")>=0||n.indexOf("vela")>=0)))||(i==2&&room.equalsIgnoreCase("Quarto 1"))||(i==3&&room.equalsIgnoreCase("Quarto 2")))ids.put(d.optString("id"));}}out.put(obj("name",names[i]).put("id","default-group-"+i).put("members",ids));}return out;
        }catch(Exception e){return new JSONArray();}
    }
    private void chooseLightGroup(){
        final JSONArray configured=groups();final String[] names=new String[configured.length()+2];names[0]="Todas as lâmpadas";
        for(int i=0;i<configured.length();i++){JSONObject g=configured.optJSONObject(i);JSONArray ids=g.optJSONArray("members");names[i+1]=g.optString("name")+" · "+(ids==null?0:ids.length());}names[names.length-1]="Editar grupos…";
        new AlertDialog.Builder(this).setTitle("Controlar em conjunto").setItems(names,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int p){
            if(p==names.length-1){editGroups();return;}JSONArray ids=new JSONArray();String title="Todas as lâmpadas";
            if(p==0){JSONArray all=store.ordered(allManaged(),false);for(int i=0;i<all.length();i++)ids.put(all.optJSONObject(i).optString("id"));}
            else{JSONObject g=configured.optJSONObject(p-1);ids=g.optJSONArray("members");title=g.optString("name");}
            if(ids==null||ids.length()==0){toast("Escolha as lâmpadas deste grupo em Editar grupos.");editGroups();return;}lightDialog(title,ids);
        }}).show();
    }
    private void editGroups(){
        final JSONArray list=groups();String[] labels=new String[list.length()+1];for(int i=0;i<list.length();i++)labels[i]=list.optJSONObject(i).optString("name");labels[list.length()]="Criar grupo…";
        new AlertDialog.Builder(this).setTitle("Grupos de lâmpadas").setItems(labels,new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int p){
            if(p==list.length()){
                final EditText name=edit("Nome do grupo","",false);new AlertDialog.Builder(MainActivity.this).setTitle("Novo grupo").setView(name).setPositiveButton("Criar",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){String n=name.getText().toString().trim();if(n.length()==0)return;JSONObject g=obj("name",n);put(g,"id",UUID.randomUUID().toString());put(g,"members",new JSONArray());list.put(g);prefs.edit().putString(groupPreference(),list.toString()).commit();editGroups();}}).setNegativeButton("Cancelar",null).show();return;
            }
            groupMembers(list,p);
        }}).show();
    }
    private void groupMembers(final JSONArray groups,final int index){
        final JSONObject group=groups.optJSONObject(index);final JSONArray all=allManaged();String[] names=new String[all.length()];final boolean[] checked=new boolean[all.length()];
        for(int i=0;i<all.length();i++){JSONObject device=all.optJSONObject(i);names[i]=store.name(device)+" · "+store.room(device);checked[i]=contains(group.optJSONArray("members"),device.optString("id"));}
        if(all.length()==0){toast("Adicione as lâmpadas primeiro.");return;}
        new AlertDialog.Builder(this).setTitle(group.optString("name")).setMultiChoiceItems(names,checked,new DialogInterface.OnMultiChoiceClickListener(){public void onClick(DialogInterface d,int p,boolean on){checked[p]=on;}})
            .setPositiveButton("Salvar grupo",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int p){JSONArray ids=new JSONArray();for(int i=0;i<checked.length;i++)if(checked[i])ids.put(all.optJSONObject(i).optString("id"));put(group,"members",ids);prefs.edit().putString(groupPreference(),groups.toString()).commit();toast("Grupo salvo.");}})
            .setNeutralButton("Excluir grupo",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int p){JSONArray keep=new JSONArray();for(int i=0;i<groups.length();i++)if(i!=index)keep.put(groups.opt(i));prefs.edit().putString(groupPreference(),keep.toString()).commit();}}).setNegativeButton("Cancelar",null).show();
    }
    private void lightDialog(String title,JSONArray members){
        if(members==null||members.length()==0){editGroups();return;}lightTitle=title;lightMembers=members;lightReturn=screen.equals("lights")?lightReturn:screen;JSONObject current=managedById(members.optString(0));lightValues=new JSONObject();put(lightValues,"mode",current==null?"white":current.optString("mode","white"));put(lightValues,"hue",current==null?35:current.optDouble("hue",35));put(lightValues,"saturation",current==null?100:current.optDouble("saturation",100));put(lightValues,"temperature",current==null?50:current.optInt("temperature",50));put(lightValues,"brightness",current==null?75:current.optInt("brightness",75));navigate("lights");
    }
    private void renderLights(){
        LinearLayout title=row();title.setGravity(Gravity.CENTER_VERTICAL);TextView name=text(lightTitle,24,theme.ink);name.setSingleLine(true);name.setEllipsize(TextUtils.TruncateAt.END);title.addView(name,new LinearLayout.LayoutParams(0,-2,1));Button close=button("Voltar",false);title.addView(close,new LinearLayout.LayoutParams(dp(78),dp(38)));close.setOnClickListener(new View.OnClickListener(){public void onClick(View v){navigate(lightReturn);}});content.addView(title);gap(content,8);
        LinearLayout body=portrait()?column():row();body.setGravity(Gravity.CENTER_VERTICAL);content.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout pickers=row();pickers.setGravity(Gravity.CENTER_VERTICAL);final ColorWheel wheel=new ColorWheel(this);wheel.setColor((float)lightValues.optDouble("hue"),(float)lightValues.optDouble("saturation",100));ClockFrame fit=new ClockFrame(this);fit.addView(wheel);pickers.addView(fit,new LinearLayout.LayoutParams(0,-1,1));hgap(pickers,8);LinearLayout white=column();white.setGravity(Gravity.CENTER);white.addView(text("Quente",12,theme.muted));final TemperatureBar temperature=new TemperatureBar(this);temperature.setValue(lightValues.optInt("temperature"));white.addView(temperature,new LinearLayout.LayoutParams(-1,0,1));white.addView(text("Frio",12,theme.muted));pickers.addView(white,new LinearLayout.LayoutParams(dp(60),-1));body.addView(pickers,portrait()?new LinearLayout.LayoutParams(-1,0,1):new LinearLayout.LayoutParams(0,-1,.53f));
        if(portrait())gap(body,10);else hgap(body,18);LinearLayout controls=column();body.addView(controls,portrait()?new LinearLayout.LayoutParams(-1,-2):new LinearLayout.LayoutParams(0,-2,.47f));LinearLayout power=row();Button on=button("Ligar",true),off=button("Desligar",false);power.addView(on,new LinearLayout.LayoutParams(0,dp(40),1));hgap(power,8);power.addView(off,new LinearLayout.LayoutParams(0,dp(40),1));controls.addView(power);gap(controls,8);
        final TextView mode=text("",14,theme.ink),brightnessText=text("",14,theme.ink),status=text("Solte o dedo para enviar o ajuste.",13,theme.muted);mode.setMaxLines(2);status.setMaxLines(3);status.setEllipsize(TextUtils.TruncateAt.END);controls.addView(mode);gap(controls,6);controls.addView(brightnessText);final SeekBar brightness=new ThinSeekBar(this,theme);brightness.setMax(100);brightness.setProgress(lightValues.optInt("brightness",75));brightness.setContentDescription("Brilho");controls.addView(brightness,new LinearLayout.LayoutParams(-1,dp(42)));final TextView preview=text("Prévia",13,theme.ink);preview.setGravity(Gravity.CENTER);controls.addView(preview,new LinearLayout.LayoutParams(-1,dp(26)));gap(controls,8);controls.addView(status,new LinearLayout.LayoutParams(-1,dp(48)));
        final Runnable update=new Runnable(){public void run(){boolean color="colour".equals(lightValues.optString("mode"));mode.setText(color?"Cor · "+Math.round(lightValues.optDouble("hue"))+"° · "+Math.round(lightValues.optDouble("saturation"))+"%":"Branco · "+lightValues.optInt("temperature")+"% de quente para frio");brightnessText.setText("Brilho · "+lightValues.optInt("brightness")+"%");float v=lightValues.optInt("brightness")/100f,t=lightValues.optInt("temperature")/100f;int c=color?android.graphics.Color.HSVToColor(new float[]{(float)lightValues.optDouble("hue"),(float)lightValues.optDouble("saturation")/100f,v}):android.graphics.Color.rgb((int)((255-40*t)*v),(int)((185+60*t)*v),(int)((110+145*t)*v));preview.setBackgroundDrawable(theme.background(MainActivity.this,c,false));preview.setTextColor((android.graphics.Color.red(c)*.299+android.graphics.Color.green(c)*.587+android.graphics.Color.blue(c)*.114)>150?0xff202020:0xffffffff);}};
        wheel.setListener(new ColorWheel.Listener(){public void changed(float hue,float sat){put(lightValues,"mode","colour");put(lightValues,"hue",Float.valueOf(hue));put(lightValues,"saturation",Float.valueOf(sat));update.run();}public void released(){sendLightEdit(status);}});temperature.setListener(new TemperatureBar.Listener(){public void changed(int value){put(lightValues,"mode","white");put(lightValues,"temperature",Integer.valueOf(value));update.run();}public void released(){sendLightEdit(status);}});brightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int value,boolean user){put(lightValues,"brightness",Integer.valueOf(value));update.run();}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){sendLightEdit(status);}});on.setOnClickListener(new View.OnClickListener(){public void onClick(View v){queueLightEdit(lightMembers,obj("on",Boolean.TRUE),status);}});off.setOnClickListener(new View.OnClickListener(){public void onClick(View v){queueLightEdit(lightMembers,obj("on",Boolean.FALSE),status);}});update.run();
    }
    private void sendLightEdit(TextView status){try{queueLightEdit(lightMembers,new JSONObject(lightValues.toString()),status);}catch(Exception ignored){}}
    private void queueLightEdit(JSONArray members,JSONObject action,TextView label){if(commandBusy){pendingLightMembers=members;pendingLightAction=action;pendingLightLabel=label;label.setText("Aguardando para enviar o último ajuste…");return;}applyLights(members,action,label);}
    private void drainLightEdit(){if(pendingLightAction==null)return;JSONObject action=pendingLightAction;JSONArray members=pendingLightMembers;TextView label=pendingLightLabel;pendingLightAction=null;pendingLightMembers=null;pendingLightLabel=null;applyLights(members,action,label);}
    private SeekBar lightSlider(LinearLayout form,final String title,int max,int value,final String unit){
        final TextView label=text(title+" · "+value+unit,14,theme.ink);gap(form,8);form.addView(label);SeekBar bar=new ThinSeekBar(this,theme);bar.setTag(label);bar.setContentDescription(title);bar.setMax(max);bar.setProgress(value);form.addView(bar,new LinearLayout.LayoutParams(-1,dp(44)));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int n,boolean user){label.setText(title+" · "+n+unit);}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});return bar;
    }
    private void applyLights(final JSONArray members,final JSONObject action,final TextView label){
        if(!demo&&!tuyaMode.equals("local")&&!action.has("on")){label.setText("Para cor e brilho, selecione Tuya local em Ajustes e cadastre o perfil RGBW.");return;}
        if(commandBusy){toast("Aguarde a confirmação do comando anterior.");return;}commandBusy=true;label.setText("Enviando para "+members.length()+" lâmpada(s)…");
        final int[] remaining={members.length()},confirmed={0};final StringBuilder failures=new StringBuilder();
        if(remaining[0]==0){commandBusy=false;return;}
        for(int i=0;i<members.length();i++){
            final String id=members.optString(i);JSONObject config=store.findLocal(id);final String deviceName=config==null?id:store.name(config);JSONObject adjusted=action;if(action.optBoolean("preserve_mode")){JSONObject state=managedById(id);adjusted=obj("brightness",Integer.valueOf(action.optInt("brightness",50)));if(action.has("delta")){if(state==null||!state.has("brightness")){remaining[0]--;failures.append(deviceName).append(": consulte o brilho primeiro\n");if(remaining[0]==0){commandBusy=false;label.setText(failures.toString());}continue;}put(adjusted,"brightness",Integer.valueOf(Math.max(1,Math.min(100,state.optInt("brightness")+action.optInt("delta")))));}put(adjusted,"mode",state==null?"white":state.optString("mode","white"));if(state!=null){put(adjusted,"temperature",Integer.valueOf(state.optInt("temperature",50)));put(adjusted,"hue",Double.valueOf(state.optDouble("hue",0)));put(adjusted,"saturation",Double.valueOf(state.optDouble("saturation",100)));}}request("/v1/devices/"+Uri.encode(id),adjusted,new Result(){public void done(JSONObject result){
                if(result!=null&&result.optBoolean("confirmed"))confirmed[0]++;else failures.append(deviceName).append(": ").append(result==null?"sem resposta":result.optString("error","ajuste não confirmado na leitura")).append("\n");
                remaining[0]--;label.setText(confirmed[0]+" / "+members.length()+" confirmada(s)"+(remaining[0]>0?" · aguardando "+remaining[0]:"\n"+failures.toString()));
                if(remaining[0]==0){if(label.getWindowToken()==null)toast(label.getText().toString());commandBusy=false;drainLightEdit();pollBusy=false;poll();}
            }},true);
        }
    }
    private void profileDialog(final JSONObject config){
        new AlertDialog.Builder(this).setTitle("Perfil RGBW · "+store.name(config)).setItems(new String[]{"Consultar DPs (somente leitura)","Configurar RGBW · DPs 20–24","Configurar RGBW · DPs 1–5","Editar DPs e intervalos"},new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int p){
            if(p==0){async(new Job(){public JSONObject run(){return TuyaLan.status(config);}},new Result(){public void done(JSONObject state){new AlertDialog.Builder(MainActivity.this).setTitle("Estado local / DPs").setMessage(state==null?"Sem resposta.":state.optJSONObject("dps")==null?state.optString("error","Sem DPs."):state.optJSONObject("dps").toString()).setPositiveButton("Fechar",null).show();}},false);return;}
            try{profileFields(p==1?LightingProfile.apply(config,"B"):p==2?LightingProfile.apply(config,"A"):config);}catch(Exception e){toast(message(e));}
        }}).show();
    }
    private void profileFields(final JSONObject config){
        LinearLayout form=column();form.setPadding(dp(14),dp(12),dp(14),dp(12));form.setBackgroundColor(theme.bg);form.addView(text("Confira os DPs consultados ou o esquema da sua lâmpada. Marca e potência não determinam o perfil.",13,theme.ink));
        final String[] keys={"switch_dp","mode_dp","brightness_dp","temperature_dp","color_dp","brightness_min","brightness_max","color_format"};String[] names={"Ligar / desligar","Modo white / colour","Brilho branco","Temperatura do branco","Cor","Brilho mínimo","Brilho máximo / escala do branco","Formato: hsv16 ou rgb8"};final EditText[] fields=new EditText[keys.length];
        for(int i=0;i<keys.length;i++)fields[i]=labeled(form,names[i],config.optString(keys[i],""),false);
        ScrollView scroll=new ScrollView(this);scroll.addView(form);final AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Mapeamento RGBW").setView(scroll).setPositiveButton("Salvar perfil",null).setNegativeButton("Cancelar",null).create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener(){public void onShow(DialogInterface d){dialog.getButton(-1).setOnClickListener(new View.OnClickListener(){public void onClick(View v){try{JSONObject device=new JSONObject(config.toString());for(int i=0;i<keys.length;i++)device.put(keys[i],fields[i].getText().toString().trim());LightingProfile.validate(device);TuyaLan.validate(device);store.saveLocal(device);dialog.dismiss();toast("Perfil salvo. Teste uma lâmpada antes de aplicar ao grupo.");poll();}catch(Exception e){toast(message(e));}}});}});dialog.show();
    }

    private SamsungRemote tvRemote()throws Exception{if(samsung==null)samsung=new SamsungRemote(prefs.getString("tv_ip",""),prefs.getString("tv_mac",""),prefs.getInt("tv_port",8001),prefs.getString("tv_token",""));return samsung;}
    private void tvCommand(final String key,final int repeats){if(tvBusy){toast("Aguarde a TV responder.");return;}tvBusy=true;tvMessage="Enviando…";if(tvLabel!=null)tvLabel.setText(tvMessage);async(new Job(){public JSONObject run(){try{if(demo)return obj("message","DEMO · comando simulado.");SamsungRemote tv=tvRemote();if(key.equals("wake"))tv.wake();else if(key.equals("pair"))tv.pair();else if(key.startsWith("channel:"))tv.channel(Integer.parseInt(key.substring(8)));else if(key.startsWith("mute:")){boolean desired=key.endsWith("on");if(tv.mute(desired)!=desired)throw new Exception("A TV não confirmou o estado do som.");return obj("message",desired?"TV silenciada · confirmado":"Som da TV ativado · confirmado");}else if(key.startsWith("volume:")){int desired=Integer.parseInt(key.substring(7)),actual=tv.volume(desired);if(actual!=desired)throw new Exception("Volume informado pela TV: "+actual);return obj("message","Volume da TV: "+actual+" · confirmado");}else tv.key(key,repeats);prefs.edit().putString("tv_token",tv.token()).commit();return obj("message",key.equals("wake")?"Sinal para ligar enviado. A TV precisa aceitar Wake-on-LAN em espera.":key.equals("pair")?"Controle autorizado pela TV.":"Comando enviado à TV.");}catch(Exception e){return obj("error",message(e));}}},new Result(){public void done(JSONObject result){tvBusy=false;tvMessage=result==null?"A TV não respondeu.":result.optString("error",result.optString("message"));if(tvLabel!=null)tvLabel.setText(tvMessage);if(result==null||result.has("error"))toast(tvMessage);}},true);}
    private Button tvButton(String title,final String key,boolean selected){Button b=button(title,selected);b.setSingleLine(true);String icon=key.equals("wake")||key.equals("KEY_POWEROFF")?"power":key.equals("KEY_VOLUP")?"volumeUp":key.equals("KEY_VOLDOWN")?"volumeDown":"";if(icon.length()>0){b.setCompoundDrawables(null,new PanelIcon(icon,theme.controlInk(false),dp(18)),null,null);b.setCompoundDrawablePadding(dp(2));}b.setOnClickListener(new View.OnClickListener(){public void onClick(View v){tvCommand(key,1);}});return b;}
    private void remoteRow(LinearLayout parent,String[] titles,String[] keys){LinearLayout line=row();for(int i=0;i<titles.length;i++){if(i>0)hgap(line,6);line.addView(tvButton(titles[i],keys[i],keys[i].equals("KEY_ENTER")),new LinearLayout.LayoutParams(0,-1,1));}LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,0,1);lp.topMargin=dp(6);parent.addView(line,lp);}
    private void renderTv(){LinearLayout head=row();head.setGravity(Gravity.CENTER_VERTICAL);TextView name=text("TV da sala",24,theme.ink);head.addView(name,new LinearLayout.LayoutParams(0,-2,1));Button setup=button("Conexão",false);head.addView(setup,new LinearLayout.LayoutParams(dp(90),dp(38)));setup.setOnClickListener(new View.OnClickListener(){public void onClick(View v){configureTv();}});content.addView(head);gap(content,6);tvLabel=text(tvMessage,13,theme.muted);tvLabel.setMaxLines(2);content.addView(tvLabel,new LinearLayout.LayoutParams(-1,dp(36)));gap(content,4);LinearLayout main=portrait()?column():row();content.addView(main,new LinearLayout.LayoutParams(-1,0,1));LinearLayout controls=column(),navigation=column();main.addView(controls,portrait()?new LinearLayout.LayoutParams(-1,0,1):new LinearLayout.LayoutParams(0,-1,1));if(portrait())gap(main,8);else hgap(main,16);main.addView(navigation,portrait()?new LinearLayout.LayoutParams(-1,0,1):new LinearLayout.LayoutParams(0,-1,1));
        remoteRow(controls,new String[]{"Ligar","Desligar"},new String[]{"wake","KEY_POWEROFF"});remoteRow(controls,new String[]{"Volume −","Mudo","Volume +"},new String[]{"KEY_VOLDOWN","KEY_MUTE","KEY_VOLUP"});remoteRow(controls,new String[]{"Entradas","HDMI 1","HDMI 2"},new String[]{"KEY_SOURCE","KEY_HDMI1","KEY_HDMI2"});remoteRow(controls,new String[]{"TV / antena","Canal −","Canal +"},new String[]{"KEY_TV","KEY_CHDOWN","KEY_CHUP"});
        remoteRow(navigation,new String[]{"Menu","Acima","Início"},new String[]{"KEY_MENU","KEY_UP","KEY_HOME"});remoteRow(navigation,new String[]{"Esquerda","OK","Direita"},new String[]{"KEY_LEFT","KEY_ENTER","KEY_RIGHT"});remoteRow(navigation,new String[]{"Voltar","Abaixo","Sair"},new String[]{"KEY_RETURN","KEY_DOWN","KEY_EXIT"});remoteRow(navigation,new String[]{"Reproduzir","Pausar","Parar"},new String[]{"KEY_PLAY","KEY_PAUSE","KEY_STOP"});}
    private void configureTv(){ScrollView scroll=new ScrollView(this);LinearLayout form=card();scroll.addView(form);final EditText ip=labeled(form,"IP da TV",prefs.getString("tv_ip",""),false),mac=labeled(form,"MAC para ligar por rede",prefs.getString("tv_mac",""),false),port=labeled(form,"Porta do controle (8001 ou 8002)",String.valueOf(prefs.getInt("tv_port",8001)),false);form.addView(text("Na primeira conexão, permita o Painel na TV. Algumas entradas diretas variam com o firmware; use Entradas e as setas se necessário. Ligar por Wi-Fi depende da TV em espera.",14,theme.muted));final AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Conexão Samsung").setView(scroll).setPositiveButton("Salvar e conectar",null).setNegativeButton("Cancelar",null).create();dialog.setOnShowListener(new DialogInterface.OnShowListener(){public void onShow(DialogInterface d){dialog.getButton(-1).setOnClickListener(new View.OnClickListener(){public void onClick(View v){try{String address=ip.getText().toString().trim(),hardware=mac.getText().toString().trim();int remotePort=Integer.parseInt(port.getText().toString().trim());new SamsungRemote(address,hardware,remotePort,"");SamsungRemote.magicPacket(hardware);if(samsung!=null)samsung.close();samsung=null;prefs.edit().putString("tv_ip",address).putString("tv_mac",hardware).putInt("tv_port",remotePort).remove("tv_token").commit();dialog.dismiss();tvCommand("pair",1);}catch(Exception e){toast(message(e));}}});}});dialog.show();}
    private void setVoiceMessage(String value){voiceMessage=value;if(voiceLabel!=null)voiceLabel.setText(value);if(voiceIndicator!=null&&voice!=null&&!voice.running())voiceIndicator.setVisibility(View.GONE);}
    private void renderVoice(){ScrollView scroll=new ScrollView(this);LinearLayout panel=column();scroll.addView(panel);content.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));panel.addView(text("É só dizer Alexa",26,theme.ink));gap(panel,8);voiceLabel=text(voiceMessage,16,theme.accent);panel.addView(voiceLabel);gap(panel,10);Button mic=button(voice.running()?"Desligar microfone":"Ativar escuta por Alexa",true);mic.setCompoundDrawables(new PanelIcon("mic",theme.accentInk,dp(22)),null,null,null);mic.setCompoundDrawablePadding(dp(10));panel.addView(mic,new LinearLayout.LayoutParams(-1,dp(48)));mic.setOnClickListener(new View.OnClickListener(){public void onClick(View v){if(voice.running()){voice.stop();setVoiceMessage("Microfone desligado");render();return;}try{voice.start(prefs.getString("voice_url",""),prefs.getString("voice_token",""));setVoiceMessage("Microfone ligado · diga Alexa");render();}catch(Exception e){toast(message(e));}}});gap(panel,8);panel.addView(text("O tablet envia o microfone à ponte local enquanto esta opção está ligada e o Painel está aberto. Ao sair do app, a escuta desliga. Os controles por toque funcionam sem a ponte.",14,theme.muted));gap(panel,16);
        LinearLayout config=card();config.addView(text("Ponte de voz",19,theme.ink));final EditText address=labeled(config,"Endereço HTTP do computador",prefs.getString("voice_url","http://"),false),secret=labeled(config,"Chave privada da ponte",prefs.getString("voice_token",""),true);Button save=button("Salvar e testar ponte",false);config.addView(save);save.setOnClickListener(new View.OnClickListener(){public void onClick(View v){final String url=address.getText().toString().trim(),key=secret.getText().toString().trim();voice.stop();prefs.edit().putString("voice_url",url).putString("voice_token",key).commit();async(new Job(){public JSONObject run()throws Exception{return new JSONObject(new String(VoiceBridge.call(url,key,"/health",null),"UTF-8"));}},new Result(){public void done(JSONObject data){setVoiceMessage(data==null?"Ponte não respondeu.":data.optBoolean("ready")?"Ponte pronta · ative o microfone":data.optBoolean("error")?"Falha ao carregar modelos. Consulte a ponte.":"Ponte conectada · modelos ainda carregando");}},false);}});panel.addView(config);gap(panel,16);
        Button voiceImport=button("Importar arquivo de configuração de voz",false);panel.addView(voiceImport);voiceImport.setOnClickListener(new View.OnClickListener(){public void onClick(View v){browseImport(android.os.Environment.getExternalStorageDirectory());}});gap(panel,12);final EditText typed=edit("Ex.: Alexa, liga as luzes da sala","",false);panel.addView(typed);Button test=button("Executar frase digitada",false);panel.addView(test);test.setOnClickListener(new View.OnClickListener(){public void onClick(View v){executeVoice(typed.getText().toString());}});gap(panel,14);panel.addView(text("Experimente\nAlexa, toca Artista de teste\nAlexa, toca música samba\nAlexa, toque no tablet / na TV\nAlexa, pausa / continue / próxima música\nAlexa, vai chover amanhã?\nAlexa, liga as luzes da sala\nAlexa, coloca a sala em 50 por cento\nAlexa, deixa a sala azul\nAlexa, aumenta o volume da TV\nAlexa, coloca a TV no HDMI 1\nAlexa, que horas são?",15,theme.ink));}
    private void voiceReply(final String reply){setVoiceMessage(reply);toast(reply);final String url=prefs.getString("voice_url",""),secret=prefs.getString("voice_token","");if(url.length()<8||secret.length()<24)return;final int stamp=++answerGeneration;speechError="";if(voice!=null)voice.hush(20000);async(new Job(){public JSONObject run(){try{byte[] wav=VoiceBridge.call(url,secret,"/v1/speak",obj("text",reply));File file=new File(getCacheDir(),"voice-answer-"+stamp+".wav");java.io.FileOutputStream out=new java.io.FileOutputStream(file);try{out.write(wav);}finally{out.close();}return obj("path",file.getAbsolutePath());}catch(Exception e){return obj("error",message(e));}}},new Result(){public void done(JSONObject result){if(result==null||!result.has("path")){speechError=result==null?"Sem resposta de áudio.":result.optString("error");setVoiceMessage(reply+" · resposta falada indisponível");voice.hush(600);return;}final File file=new File(result.optString("path"));if(!active||stamp!=answerGeneration){file.delete();return;}try{if(answerPlayer!=null)answerPlayer.release();answerPlayer=new android.media.MediaPlayer();answerPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);java.io.FileInputStream speechInput=new java.io.FileInputStream(file);try{answerPlayer.setDataSource(speechInput.getFD());}finally{speechInput.close();}answerPlayer.setOnPreparedListener(new android.media.MediaPlayer.OnPreparedListener(){public void onPrepared(android.media.MediaPlayer p){if(stamp==answerGeneration&&active)p.start();else p.release();}});answerPlayer.setOnCompletionListener(new android.media.MediaPlayer.OnCompletionListener(){public void onCompletion(android.media.MediaPlayer p){p.release();if(answerPlayer==p)answerPlayer=null;file.delete();voice.hush(800);}});answerPlayer.setOnErrorListener(new android.media.MediaPlayer.OnErrorListener(){public boolean onError(android.media.MediaPlayer p,int what,int extra){speechError="MediaPlayer "+what+"/"+extra;setVoiceMessage(reply+" · resposta falada indisponível");p.release();if(answerPlayer==p)answerPlayer=null;file.delete();voice.hush(800);return true;}});answerPlayer.prepareAsync();}catch(Exception e){speechError=message(e);if(answerPlayer!=null){answerPlayer.release();answerPlayer=null;}file.delete();voice.hush(800);}}},true);}
    private JSONArray voiceTargets(String target)throws Exception{JSONArray all=allManaged(),found=new JSONArray(),sets=groups();String wanted=VoiceIntent.fold(target);if(wanted.equals("todas")||wanted.equals("tudo")||wanted.equals("casa")){for(int i=0;i<all.length();i++)found.put(all.optJSONObject(i).optString("id"));return found;}for(int i=0;i<all.length();i++){JSONObject d=all.optJSONObject(i);if(VoiceIntent.fold(store.room(d)).equals(wanted))found.put(d.optString("id"));}if(found.length()>0)return found;JSONObject exact=null;for(int i=0;i<sets.length();i++){JSONObject g=sets.optJSONObject(i);if(VoiceIntent.fold(g.optString("name")).equals(wanted)){if(exact!=null)throw new Exception("Há grupos com nomes iguais. Renomeie em Grupos.");exact=g;}}if(exact!=null)return exact.optJSONArray("members");for(int i=0;i<all.length();i++){JSONObject d=all.optJSONObject(i);if(VoiceIntent.fold(store.name(d)).equals(wanted))found.put(d.optString("id"));}if(found.length()>1)throw new Exception("Há lâmpadas com esse nome. Dê nomes diferentes para usar a voz.");return found;}
    private void executeVoice(String text){try{final JSONObject intent=VoiceIntent.parse(text);String type=intent.optString("type");setVoiceMessage("Você pediu: "+text);if(type.equals("tv")){tvCommand(intent.has("channel")?"channel:"+intent.optInt("channel"):intent.optString("command"),intent.optInt("repeats",1));return;}
        if(type.equals("lights")){JSONArray ids=voiceTargets(intent.optString("target"));if(ids==null||ids.length()==0){voiceReply("Não encontrei esse ambiente, grupo ou lâmpada. Use o nome exibido no Painel.");return;}if(commandBusy){voiceReply("Aguarde o comando anterior terminar.");return;}TextView result=new TextView(this);applyLights(ids,intent.optJSONObject("action"),result);setVoiceMessage("Enviando para "+ids.length()+" lâmpadas. Aguardando confirmação.");return;}
        if(type.equals("time")||type.equals("date")){voiceReply(new SimpleDateFormat(type.equals("time")?"'São' HH:mm":"EEEE, d 'de' MMMM",new Locale("pt","BR")).format(new Date()));return;}
        if(type.equals("weather")){final boolean tomorrow=intent.optBoolean("tomorrow");request("/v1/weather",null,new Result(){public void done(JSONObject w){if(w==null||!w.optBoolean("configured")){voiceReply("Escolha sua cidade em Ajustes para consultar a previsão.");return;}weather=w;String prefix=demo?"Dados de exemplo. ":"";JSONArray days=w.optJSONArray("days");JSONObject day=days==null?null:days.optJSONObject(tomorrow?1:0);if(day==null||day.isNull("precipitation_probability_max")||!day.has("precipitation_probability_max")){voiceReply(prefix+"A previsão não informou esse dia.");return;}String when=tomorrow?"amanhã":"hoje";voiceReply(prefix+"Para "+when+", em "+w.optString("city")+", há "+day.optInt("precipitation_probability_max")+" por cento de chance de chuva. "+(day.optInt("precipitation_probability_max")>=40?"Leve um guarda chuva. ":"")+"Temperaturas entre "+Math.round(day.optDouble("temperature_2m_min"))+" e "+Math.round(day.optDouble("temperature_2m_max"))+" graus.");}},false);return;}
        if(type.equals("output")){voiceOutput(intent.optString("target"));return;}if(type.equals("media")){if(sessionId.length()==0){voiceReply("Escolha Tablet ou TV no player primeiro.");return;}control("",intent.optString("command"),false).performClick();return;}
        if(type.equals("music")){if(demo||jellyToken.length()==0){voiceReply("Conecte o Jellyfin em Ajustes para escolher músicas por voz.");return;}if(sessionId.length()==0){voiceReply("Primeiro diga toque no tablet ou selecione a TV no player.");return;}async(new Job(){public JSONObject run()throws Exception{return new JellyfinClient(jellyBase,jellyToken,jellyUser,deviceId).voiceSelection(intent.optString("query"),intent.optString("mode"));}},new Result(){public void done(JSONObject selection){if(selection!=null){play(selection);setVoiceMessage("Preparando "+selection.optString("Name"));}}},false);return;}
        voiceReply(type.equals("help")?"Peça para ligar um ambiente, tocar um artista, tocar música samba, consultar a chuva ou controlar o volume da TV.":"Não entendi esse pedido. Veja os exemplos em Menu, Voz.");
    }catch(Exception e){voiceReply(message(e));}}
    private void voiceOutput(String target){if(target.equals("tablet")){selectedDevice="__tablet__";sessionId="__tablet__";selectedSession=tablet.snapshot();clearRemoteQueue();prefs.edit().putString("device",selectedDevice).commit();setVoiceMessage("Saída selecionada: tablet");return;}request("/v1/sessions",null,new Result(){public void done(JSONObject data){JSONArray list=data==null?null:data.optJSONArray("sessions");if(list==null||list.length()!=1){voiceReply(list!=null&&list.length()>1?"Há mais de uma sessão. Selecione a TV no player.":"Abra o Jellyfin na TV e tente novamente.");return;}tablet.command("Pause");selectedSession=list.optJSONObject(0);selectedDevice=selectedSession.optString("DeviceId");sessionId=selectedSession.optString("Id");prefs.edit().putString("device",selectedDevice).commit();clearRemoteQueue();setVoiceMessage("Saída: "+selectedSession.optString("DeviceName"));}},false);}

    private void renderSettings() {
        ScrollView scroll = new ScrollView(this); LinearLayout settings = column(); scroll.addView(settings); content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        settings.addView(text("Seu painel, do seu jeito", 21, theme.ink)); gap(settings, 12);
        Button tvSettings=button("TV Samsung · controle e conexão",false);settings.addView(tvSettings);tvSettings.setOnClickListener(new View.OnClickListener(){public void onClick(View v){navigate("tv");}});gap(settings,6);Button voiceSettings=button("Voz · Alexa e comandos",false);settings.addView(voiceSettings);voiceSettings.setOnClickListener(new View.OnClickListener(){public void onClick(View v){navigate("voice");}});gap(settings,12);
        LinearLayout design = card(); design.addView(text("APARÊNCIA", 10, theme.muted)); gap(design, 6);
        Button themes = button("Estilo: " + theme.title, true); design.addView(themes); themes.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { chooseTheme(); } });
        Button clockStyle=button("Relógio: Bauhaus / StandBy · 30 / 60 fps",false);design.addView(clockStyle);clockStyle.setOnClickListener(new View.OnClickListener(){public void onClick(View v){clockOptions();}});
        final CheckBox seconds = check("Mostrar ponteiro dos segundos", prefs.getBoolean("clock_seconds", true)); design.addView(seconds);
        seconds.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { prefs.edit().putBoolean("clock_seconds", seconds.isChecked()).commit(); } });
        Button shortcuts=button("Personalizar os dois atalhos do relógio",false);design.addView(shortcuts);shortcuts.setOnClickListener(new View.OnClickListener(){public void onClick(View v){new AlertDialog.Builder(MainActivity.this).setTitle("Atalhos do relógio").setItems(new String[]{"Atalho 1","Atalho 2"},new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){chooseShortcut(which);}}).show();}});
        Button fontChoice=button("Fonte do LCARS: "+(prefs.getBoolean("lcars_system_font",false)?"padrão do tablet":"Barlow Condensed"),false);design.addView(fontChoice);fontChoice.setOnClickListener(new View.OnClickListener(){public void onClick(View v){new AlertDialog.Builder(MainActivity.this).setTitle("Fonte do tema LCARS").setItems(new String[]{"Barlow Condensed · incluída no APK","Padrão do tablet"},new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){prefs.edit().putBoolean("lcars_system_font",which==1).commit();render();}}).show();}});Button greetingNames=button("Nomes da saudação",false);design.addView(greetingNames);greetingNames.setOnClickListener(new View.OnClickListener(){public void onClick(View v){final EditText names=edit("Nomes separados por vírgula",prefs.getString("greeting_names",""),false);new AlertDialog.Builder(MainActivity.this).setTitle("Nomes da saudação").setMessage("Separe até seis nomes por vírgula. Deixe vazio para mostrar apenas bom dia, boa tarde ou boa noite.").setView(names).setPositiveButton("Salvar",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int which){String value=names.getText().toString().trim();String[] people=value.split(",");if(people.length>6){toast("Use até seis nomes.");return;}for(int i=0;i<people.length;i++)if(people[i].trim().length()>24){toast("Cada nome pode ter até 24 caracteres.");return;}prefs.edit().putString("greeting_names",value).commit();}}).setNegativeButton("Cancelar",null).show();}});settings.addView(design); gap(settings, 10);
        LinearLayout connections = card(); connections.addView(text("JELLYFIN DIRETO", 10, theme.muted));
        connections.addView(text(jellyToken.length() > 0 ? "Conectado como " + prefs.getString("jelly_name", "usuário") : "Entre por Quick Connect; não precisa da ponte.", 12, theme.ink)); gap(connections, 7);
        final EditText address = labeled(connections, "Endereço local do Jellyfin", jellyBase.length() > 0 ? jellyBase : "http://", false);
        LinearLayout actions = row();
        Button quick = button("Quick Connect", true), test = button("Testar URL", false), login = button("Usuário / senha", false);
        actions.addView(quick, new LinearLayout.LayoutParams(0, dp(37), 1)); hgap(actions, 5); actions.addView(test, new LinearLayout.LayoutParams(0, dp(37), 1)); hgap(actions, 5); actions.addView(login, new LinearLayout.LayoutParams(0, dp(37), 1)); connections.addView(actions);
        quick.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { startQuick(address.getText().toString()); } });
        test.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            final String url = address.getText().toString(); async(new Job() { public JSONObject run() throws Exception { return JellyfinClient.testServer(url, deviceId); } }, new Result() { public void done(JSONObject info) {
                if (info != null) new AlertDialog.Builder(MainActivity.this).setTitle("Servidor encontrado").setMessage(info.optString("ServerName", "Jellyfin") + "\nVersão " + info.optString("Version") + "\nAgora entre por Quick Connect.").setPositiveButton("OK", null).show();
            } }, false);
        } });
        login.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { passwordLogin(address.getText().toString()); } });
        if (jellyToken.length() > 0) {
            Button logout = button("Desconectar este painel do Jellyfin", false); connections.addView(logout);
            logout.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { prefs.edit().remove("jelly_token").remove("jelly_user").remove("device").commit(); resetConnections(); render(); } });
        }
        connections.addView(text("Escolha Tablet ou TV no player. Música toca no tablet; filmes e séries usam a TV com o Jellyfin aberto.", 10, theme.muted)); settings.addView(connections); gap(settings, 10);
        LinearLayout tuya = card(); tuya.addView(text("TUYA LOCAL · SEM HOME ASSISTANT", 10, theme.muted)); gap(tuya, 6);
        tuya.addView(text("Cadastre os aparelhos com ID, IP, local key e DP booleano. Depois o tablet controla diretamente pela rede Wi-Fi.", 12, theme.ink));
        Button importer=button("Importar lâmpadas de um arquivo",true);tuya.addView(importer);importer.setOnClickListener(new View.OnClickListener(){public void onClick(View v){chooseImport();}});
        Button manage = button("Adicionar e organizar dispositivos", false); tuya.addView(manage); manage.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { navigate("devices"); } });
        Button mode = button(tuyaMode.equals("local") ? "Modo atual: direto no tablet" : "Modo atual: ponte opcional", false); tuya.addView(mode);
        mode.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            new AlertDialog.Builder(MainActivity.this).setTitle("Controle Tuya").setItems(new String[]{"Direto no tablet (LAN)", "Ponte Docker opcional"}, new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int p) { prefs.edit().putString("tuya_mode", p == 0 ? "local" : "bridge").commit(); resetConnections(); render(); } }).show();
        } }); settings.addView(tuya); gap(settings, 10);
        LinearLayout climate = card(); climate.addView(text("CLIMA DIRETO", 10, theme.muted));
        climate.addView(text(prefs.getString("weather_city", "Escolha sua cidade"), 14, theme.ink));
        Button city = button("Buscar cidade", true); climate.addView(city); city.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { chooseCity(); } });
        climate.addView(text("Previsão: Open-Meteo · dados CC BY 4.0", 10, theme.muted)); settings.addView(climate); gap(settings, 10);
        LinearLayout advanced = card(); advanced.addView(text("DEMONSTRAÇÃO E COMPATIBILIDADE", 10, theme.muted));
        final CheckBox preview = check("Demonstração (dados fictícios)", demo); advanced.addView(preview);
        preview.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { prefs.edit().putBoolean("demo", preview.isChecked()).commit(); resetConnections(); render(); } });
        Button bridge = button("Configurar ponte opcional da versão anterior", false); advanced.addView(bridge); bridge.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { bridgeDialog(); } });
        advanced.addView(text("Painel 0.6.0 · Android 2.2 / API 8\nSem root, sem WebView. Credenciais ficam nas preferências privadas do tablet; use os endereços HTTP apenas na rede doméstica.", 10, theme.muted)); settings.addView(advanced);
    }

    private void resetConnections() {
        tvBusy=false;if(samsung!=null){samsung.close();samsung=null;}if(voice!=null)voice.stop();setVoiceMessage("Microfone desligado");
        generation++; quickGeneration++;clearRemoteQueue();quoteBusy=false;thumbnails.clear();libraryItems=new JSONArray();chooseAfterRefresh=false;if(tablet!=null)tablet.stop(); loadSettings();lyricId="";lyrics=new JSONArray(); devices = new JSONArray(); sessions = new JSONArray(); selectedSession = null; sessionId = ""; artId = ""; coverBitmap = null;
        weather = new JSONObject(); lastWeather = 0; pollBusy = false; sessionBusy = false; commandBusy = false; listBusy = false;
        kind = "Audio"; parent = ""; playlist = ""; artist = ""; query = ""; page = 0; total = 0; browseStack.clear(); libraryGeneration++;
    }

    private void startQuick(String input) {
        final String url;
        try { url = JellyfinClient.normalizeBase(input); } catch (Exception e) { toast(message(e)); return; }
        final int stamp = ++quickGeneration;
        async(new Job() { public JSONObject run() throws Exception { return JellyfinClient.initiateQuickConnect(url, deviceId); } }, new Result() { public void done(JSONObject data) {
            if (data == null || stamp != quickGeneration) return;
            final String secret = data.optString("Secret");
            LinearLayout view = column(); view.setPadding(dp(22), dp(18), dp(22), dp(18)); view.setBackgroundColor(theme.bg);
            TextView code = text(data.optString("Code"), 38, theme.ink); code.setGravity(Gravity.CENTER); view.addView(code);
            view.addView(text("No Jellyfin já conectado do celular ou computador:\nPerfil > Quick Connect > digite este código.\nO painel verifica a autorização a cada 5 segundos.", 13, theme.muted));
            final AlertDialog dialog = new AlertDialog.Builder(MainActivity.this).setTitle("Autorize o Painel").setView(view).setNegativeButton("Cancelar", null).create();
            dialog.setOnDismissListener(new DialogInterface.OnDismissListener() { public void onDismiss(DialogInterface d) { if (quickGeneration == stamp) quickGeneration++; } });
            dialog.show(); quickPoll(url, secret, dialog, stamp, 0);
        } }, false);
    }

    private void quickPoll(final String url, final String secret, final AlertDialog dialog, final int stamp, final int attempts) {
        if (stamp != quickGeneration || !dialog.isShowing()) return;
        if (attempts >= 60) { dialog.dismiss(); toast("Código expirado. Gere outro Quick Connect."); return; }
        async(new Job() { public JSONObject run() throws Exception { return JellyfinClient.pollQuickConnect(url, secret, deviceId); } }, new Result() { public void done(JSONObject state) {
            if (stamp != quickGeneration || !dialog.isShowing()) return;
            if (state == null) { dialog.dismiss(); toast("Não foi possível verificar o código. Teste a URL e tente novamente."); return; }
            if (state.optBoolean("Authenticated")) {
                async(new Job() { public JSONObject run() throws Exception { return JellyfinClient.authenticateQuickConnect(url, secret, deviceId); } }, new Result() { public void done(JSONObject auth) {
                    if (stamp != quickGeneration) return;
                    dialog.dismiss(); if (auth != null) saveAuth(url, auth);
                } }, false);
            } else handler.postDelayed(new Runnable() { public void run() { quickPoll(url, secret, dialog, stamp, attempts + 1); } }, 5000);
        } }, false);
    }

    private void saveAuth(String url, JSONObject auth) {
        JSONObject user = auth.optJSONObject("User");
        if (auth.optString("AccessToken").length() == 0 || user == null) { toast("O Jellyfin não devolveu uma autenticação válida."); return; }
        prefs.edit().putString("jelly_url", url).putString("jelly_token", auth.optString("AccessToken")).putString("jelly_user", user.optString("Id"))
            .putString("jelly_name", user.optString("Name", "usuário")).putBoolean("demo", false).remove("device").commit();
        resetConnections(); render(); toast("Jellyfin conectado. Abra Mídia e selecione a TV.");
    }

    private void passwordLogin(String input) {
        final String url;
        try { url = JellyfinClient.normalizeBase(input); } catch (Exception e) { toast(message(e)); return; }
        LinearLayout form = column(); form.setPadding(dp(15), dp(12), dp(15), dp(12)); form.setBackgroundColor(theme.bg);
        final EditText user = labeled(form, "Usuário", "", false), pass = labeled(form, "Senha", "", true);
        new AlertDialog.Builder(this).setTitle("Entrar no Jellyfin").setView(form).setPositiveButton("Entrar", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int p) {
            final String username = user.getText().toString(), password = pass.getText().toString();
            async(new Job() { public JSONObject run() throws Exception { return JellyfinClient.authenticateByName(url, username, password, deviceId); } }, new Result() { public void done(JSONObject data) { if (data != null) saveAuth(url, data); } }, false);
        } }).setNegativeButton("Cancelar", null).show();
    }

    private void chooseCity() {
        final EditText city = edit("Cidade", prefs.getString("weather_city", ""), false);
        new AlertDialog.Builder(this).setTitle("Buscar cidade").setView(city).setPositiveButton("Buscar", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int p) {
            final String name = city.getText().toString();
            async(new Job() { public JSONObject run() throws Exception { return obj("cities", WeatherClient.geocode(name)); } }, new Result() { public void done(JSONObject data) {
                if (data == null) return; final JSONArray cities = data.optJSONArray("cities");
                if (cities == null || cities.length() == 0) { toast("Cidade não encontrada."); return; }
                String[] names = new String[cities.length()]; for (int i = 0; i < names.length; i++) { JSONObject c = cities.optJSONObject(i); names[i] = c.optString("name") + " · " + c.optString("admin1") + " · " + c.optString("country"); }
                new AlertDialog.Builder(MainActivity.this).setTitle("Escolha sua cidade").setItems(names, new DialogInterface.OnClickListener() { public void onClick(DialogInterface dd, int w) {
                    JSONObject selected = cities.optJSONObject(w); prefs.edit().putString("weather_city", selected.optString("name")).putString("weather_lat", selected.optString("latitude")).putString("weather_lon", selected.optString("longitude")).commit();
                    lastWeather = 0; weather = new JSONObject(); render(); toast("Cidade salva. O clima será atualizado ao abrir o painel.");
                } }).show();
            } }, false);
        } }).setNegativeButton("Cancelar", null).show();
    }

    private void bridgeDialog() {
        LinearLayout form = column(); form.setPadding(dp(14), dp(10), dp(14), dp(10)); form.setBackgroundColor(theme.bg);
        final EditText url = labeled(form, "URL da ponte (opcional)", base, false), key = labeled(form, "Token da ponte", token, true);
        form.addView(text("Só é necessária se você optar por usar a ponte. Jellyfin, clima e Tuya local podem funcionar diretamente no tablet.", 11, theme.muted));
        new AlertDialog.Builder(this).setTitle("Ponte Docker opcional").setView(form).setPositiveButton("Salvar", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) {
            String value = url.getText().toString().trim(); while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
            prefs.edit().putString("base", value).putString("token", key.getText().toString().trim()).commit(); resetConnections(); render();
        } }).setNegativeButton("Cancelar", null).show();
    }

    public void onBackPressed() {
        if(screen.equals("lights")){navigate(lightReturn);return;}
        if(screen.equals("now")){navigate("music");return;}
        if (screen.equals("music") && !browseStack.isEmpty()) { browseBack(); return; }
        if (!screen.equals("clock")) { navigate("clock"); return; }
        new AlertDialog.Builder(this).setTitle("Sair do painel?").setPositiveButton("Sair", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int w) { finish(); } }).setNegativeButton("Ficar", null).show();
    }

    private JSONObject obj(String key, Object value) { JSONObject o = new JSONObject(); put(o, key, value); return o; }
    private void put(JSONObject object, String key, Object value) { try { object.put(key, value); } catch (Exception ignored) { } }
    private String message(Exception e) { return e.getMessage() == null ? "Falha de conexão. Confira os dados em Ajustes." : e.getMessage(); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
    private int dp(float f) { return (int)(getResources().getDisplayMetrics().density * f + .5f); }
    private boolean compact() { return getResources().getDisplayMetrics().heightPixels / getResources().getDisplayMetrics().density < 470; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private void gap(LinearLayout l, int height) { l.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height))); }
    private void hgap(LinearLayout l, int width) { l.addView(new View(this), new LinearLayout.LayoutParams(dp(width), 1)); }
    private TextView text(String s, float size, int color) { TextView t = new TextView(this); t.setText(s); t.setTextSize(Math.max(12,size)); t.setTextColor(color); t.setIncludeFontPadding(false);if(theme.id.equals("lcars"))t.setTypeface(LcarsTypeface.get(this,theme)); return t; }
    private LinearLayout card() { LinearLayout l = column(); l.setBackgroundDrawable(theme.background(this, theme.panel, false)); l.setPadding(dp(12), dp(11), dp(12), dp(11)); return l; }
    private Button button(String s, boolean selected) {
        Button b = new Button(this); b.setText(s); b.setTextSize(14); b.setTextColor(theme.controlInk(selected));if(theme.id.equals("lcars")){b.setTypeface(LcarsTypeface.get(this,theme));} b.setPadding(dp(7), dp(5), dp(7), dp(5));
        b.setMinimumHeight(0); b.setMinHeight(dp(29)); b.setMinimumWidth(0); b.setMinWidth(0); b.setBackgroundDrawable(theme.buttonBackground(this, selected ? theme.accent : theme.panel, selected)); b.setPadding(dp(7), dp(5), dp(7), dp(5)); return b;
    }
    private EditText edit(String hint, String value, boolean password) {
        EditText e = new EditText(this); e.setText(value); e.setHint(hint); e.setTextSize(14); e.setTextColor(theme.ink); e.setHintTextColor(theme.muted);
        e.setSingleLine(true); e.setPadding(dp(10), dp(7), dp(10), dp(7)); e.setBackgroundDrawable(theme.background(this, theme.panel, false));
        e.setPadding(dp(10), dp(7), dp(10), dp(7)); e.setInputType(InputType.TYPE_CLASS_TEXT | (password ? InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_TEXT_VARIATION_NORMAL)); return e;
    }
    private EditText labeled(LinearLayout form, String label, String value, boolean password) { form.addView(text(label, 10, theme.muted)); gap(form, 4); EditText field = edit(label, value, password); form.addView(field, new LinearLayout.LayoutParams(-1, dp(37))); gap(form, 9); return field; }
    private CheckBox check(String label, boolean value) { CheckBox c = new CheckBox(this); c.setText(label); c.setTextSize(12); c.setTextColor(theme.ink); c.setChecked(value); return c; }
    private String join(JSONArray values) { String s = ""; for (int i = 0; values != null && i < values.length(); i++) s += (i == 0 ? "" : ", ") + values.optString(i); return s; }
    private boolean contains(JSONArray values, String value) { for (int i = 0; values != null && i < values.length(); i++) if (value.equals(values.optString(i))) return true; return false; }
    private String duration(long ticks) { long seconds = ticks / 10000000; return seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60; }
    private final class SymbolView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG); private final int ink;
        SymbolView(int color) { super(MainActivity.this); ink = color; }
        protected void onDraw(Canvas c) { float x = getWidth()/2f, y = getHeight()/2f, r = Math.min(x,y)*.68f; paint.setColor(ink); paint.setStrokeWidth(dp(2)); paint.setStyle(Paint.Style.STROKE); c.drawCircle(x,y,r,paint); c.drawCircle(x,y,r*.6f,paint); c.drawCircle(x,y,r*.12f,paint); }
    }
}
