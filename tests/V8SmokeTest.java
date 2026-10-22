package br.casasala.test;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** API-8 instrumentation only. Run separately from the delivery APK. */
public final class V8SmokeTest extends Instrumentation {
    private Activity activity;
    private SharedPreferences preferences;
    private Map<String, ?> original;
    private String prefix = "v2";
    private final StringBuilder report = new StringBuilder();

    public void onCreate(Bundle args) {
        super.onCreate(args);
        if (args != null && args.getString("prefix") != null) {
            prefix = args.getString("prefix").replaceAll("[^A-Za-z0-9_-]", "");
            if (prefix.length() == 0) prefix = "v2";
        }
        start();
    }

    public void onStart(){Bundle result=new Bundle();int status=Activity.RESULT_CANCELED;try{
        preferences=getTargetContext().getSharedPreferences("casa-sala",0);original=new HashMap<String,Object>(preferences.getAll());preferences.edit().clear().putBoolean("demo",true).putString("theme","lcars").putString("greeting_names","").commit();launch();android.graphics.Typeface font=android.graphics.Typeface.createFromAsset(getTargetContext().getAssets(),"fonts/BarlowCondensed-SemiBold.ttf");require(font!=null&&!font.equals(android.graphics.Typeface.DEFAULT),"Fonte TrueType não carregou no Froyo");orient(false);assertNoScroll();assertSquareClock();capture("lcars-relogio-horizontal");click("Painel");await("Teto 1");assertNoScroll();assertVisible("Abajur");assertVisible("Lâmpada 3");capture("lcars-painel-horizontal");click("Cor / brilho");await("Voltar");assertNoScroll();assertVisible("Brilho · 75%");capture("lcars-rgbw-horizontal");click("Voltar");
        click("Painel");click("TV");await("TV da sala");assertNoScroll();assertVisible("Volume +");assertVisible("Parar");capture("lcars-tv-horizontal");click("Tempo");await("Próximos sete dias");capture("lcars-tempo-horizontal");click("Mídia");await("Fim de tarde");click("Fim de tarde");await("Reproduzindo");capture("lcars-midia-horizontal");
        click("Relógio");orient(true);assertNoScroll();assertSquareClock();capture("lcars-relogio-vertical");click("Painel");await("Teto 1");assertNoScroll();assertVisible("Teto 1");assertVisible("Abajur");assertVisible("Lâmpada 3");capture("lcars-painel-vertical");click("Cor / brilho");await("Voltar");assertNoScroll();assertVisible("Brilho · 75%");capture("lcars-rgbw-vertical");click("Voltar");click("Painel");click("TV");await("TV da sala");assertNoScroll();assertVisible("Parar");capture("lcars-tv-vertical");
        menu("Tema");click("Branco");click("Relógio");assertSquareClock();capture("branco-preservado");menu("Tema");click("LCARS · The Next Generation");require(preferences.getString("theme","").equals("lcars"),"LCARS não foi salvo");capture("lcars-tema-selecionado");preferences.edit().putBoolean("lcars_system_font",true).commit();require(br.casasala.painel.LcarsTypeface.get(getTargetContext(),br.casasala.painel.VisualTheme.get("lcars")).equals(android.graphics.Typeface.DEFAULT),"Opção da fonte padrão não foi respeitada");preferences.edit().putBoolean("lcars_system_font",false).commit();
        report.append("PASS LCARS API 8: tema selecionável e persistido, relógio/painel/RGBW/TV sem rolagem e visíveis nas duas orientações, mídia e clima, sem nomes pessoais nas capturas, tema Branco preservado.\n");status=Activity.RESULT_OK;
    }catch(Throwable error){report.append("FAILED ").append(error.toString());try{capture("falha");captureTop("falha-top");}catch(Exception ignored){}}finally{try{if(activity!=null)runOnMainSync(new Runnable(){public void run(){activity.finish();}});restorePreferences();}catch(Throwable ignored){}result.putString("stream",report.toString());finish(status,result);}}
    private void testPendingEdit()throws Exception{
        final Throwable[] failed={null};runOnMainSync(new Runnable(){public void run(){try{Field busy=activity.getClass().getDeclaredField("commandBusy"),members=activity.getClass().getDeclaredField("lightMembers"),values=activity.getClass().getDeclaredField("lightValues");busy.setAccessible(true);members.setAccessible(true);values.setAccessible(true);busy.setBoolean(activity,true);Method queue=activity.getClass().getDeclaredMethod("queueLightEdit",JSONArray.class,JSONObject.class,TextView.class);queue.setAccessible(true);TextView status=new TextView(activity);JSONObject one=new JSONObject(values.get(activity).toString()).put("brightness",25),last=new JSONObject(values.get(activity).toString()).put("brightness",60);queue.invoke(activity,members.get(activity),one,status);queue.invoke(activity,members.get(activity),last,status);}catch(Throwable e){failed[0]=e;}}});require(failed[0]==null,"Fila do gesto falhou");require(demoLight(0).optInt("brightness")==75,"Enviou ajuste enquanto ocupado");runOnMainSync(new Runnable(){public void run(){try{Field busy=activity.getClass().getDeclaredField("commandBusy");busy.setAccessible(true);busy.setBoolean(activity,false);Method drain=activity.getClass().getDeclaredMethod("drainLightEdit");drain.setAccessible(true);drain.invoke(activity);}catch(Throwable e){failed[0]=e;}}});awaitBrightness(60);require(failed[0]==null,"Último ajuste não foi drenado");runOnMainSync(new Runnable(){public void run(){try{Field members=activity.getClass().getDeclaredField("lightMembers"),values=activity.getClass().getDeclaredField("lightValues");members.setAccessible(true);values.setAccessible(true);Method queue=activity.getClass().getDeclaredMethod("queueLightEdit",JSONArray.class,JSONObject.class,TextView.class);queue.setAccessible(true);queue.invoke(activity,members.get(activity),new JSONObject(values.get(activity).toString()),new TextView(activity));}catch(Throwable e){failed[0]=e;}}});awaitBrightness(75);
    }
    private void awaitBrightness(int value)throws Exception{for(int i=0;i<30;i++){if(demoLight(0).optInt("brightness")==value)return;Thread.sleep(100);}throw new Exception("Brilho final não chegou: "+value);}
    private boolean demoPower(int index)throws Exception{Field field=activity.getClass().getDeclaredField("demoData");field.setAccessible(true);Object demo=field.get(activity);synchronized(demo){Field lights=demo.getClass().getDeclaredField("lights");lights.setAccessible(true);return ((boolean[])lights.get(demo))[index];}}
    private JSONObject demoLight(int index)throws Exception{Field field=activity.getClass().getDeclaredField("demoData");field.setAccessible(true);Object demo=field.get(activity);synchronized(demo){Field settings=demo.getClass().getDeclaredField("settings");settings.setAccessible(true);return new JSONObject(((JSONObject[])settings.get(demo))[index].toString());}}
    private void dragPicker(final String cls,boolean temperature)throws Exception{
        final View view=findType(cls);final int[] loc=new int[2];runOnMainSync(new Runnable(){public void run(){view.getLocationOnScreen(loc);}});float x=loc[0]+view.getWidth()*.75f,y=loc[1]+view.getHeight()*.5f;if(temperature){x=loc[0]+view.getWidth()*.5f;y=loc[1]+view.getHeight()*.8f;}
        final JSONObject before=demoLight(0);long now=android.os.SystemClock.uptimeMillis();sendPointerSync(android.view.MotionEvent.obtain(now,now,0,x,y,0));Thread.sleep(180);JSONObject during=demoLight(0);require(before.toString().equals(during.toString()),"Arrastar enviou antes de soltar");sendPointerSync(android.view.MotionEvent.obtain(now,now+200,2,x,y,0));sendPointerSync(android.view.MotionEvent.obtain(now,now+250,1,x,y,0));Thread.sleep(900);
    }
    private void assertNoScroll()throws Exception{final boolean[] ok={true};runOnMainSync(new Runnable(){public void run(){View root=activity.getWindow().getDecorView();ok[0]=findClass(root,"android.widget.ScrollView")==null&&findClass(root,"android.widget.HorizontalScrollView")==null;}});require(ok[0],"Tela contém rolagem");assertButtonsVisible();}
    private void assertButtonsVisible()throws Exception{final String[] failure={null};runOnMainSync(new Runnable(){public void run(){failure[0]=clippedButton(activity.getWindow().getDecorView());}});require(failure[0]==null,"Ação cortada: "+failure[0]);}
    private String clippedButton(View view){if(view instanceof android.widget.Button&&view.getVisibility()==View.VISIBLE){Rect r=new Rect();if(!view.getGlobalVisibleRect(r)||r.width()<view.getWidth()-2||r.height()<view.getHeight()-2)return ((TextView)view).getText().toString();}if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){String found=clippedButton(group.getChildAt(i));if(found!=null)return found;}}return null;}
    private void assertVisible(final String text)throws Exception{final boolean[] ok={false};runOnMainSync(new Runnable(){public void run(){View v=find(activity.getWindow().getDecorView(),text);if(v==null)return;Rect rect=new Rect();ok[0]=v.getGlobalVisibleRect(rect)&&rect.width()>=v.getWidth()-2&&rect.height()>=v.getHeight()-2;}});require(ok[0],"Conteúdo cortado: "+text);}
    private View findType(final String name){final View[] out={null};runOnMainSync(new Runnable(){public void run(){View[] roots=windows();for(int i=roots.length-1;i>=0;i--){out[0]=findClass(roots[i],name);if(out[0]!=null)break;}}});return out[0];}
    private String importSample()throws Exception{return new JSONObject().put("schema","painel-tuya").put("version",1).put("devices",new JSONArray().put(new JSONObject().put("name","Lâmpada importada de teste").put("room","Teste").put("device_id","fixture-import-only").put("local_key","0123456789abcdef").put("ip","127.0.0.1").put("version",3.3).put("switch_dp","20").put("mode_dp","21").put("brightness_dp","22").put("temperature_dp","23").put("color_dp","24").put("brightness_min",10).put("brightness_max",1000).put("color_format","hsv16"))).toString();}
    private void validateImport()throws Exception{String sample=importSample();JSONObject first=br.casasala.painel.DeviceImport.parse(sample,new JSONArray());require(first.getInt("added")==1,"Importação não adicionou");JSONObject again=br.casasala.painel.DeviceImport.parse(sample,first.getJSONArray("devices"));require(again.getInt("updated")==1&&again.getJSONArray("devices").length()==1,"Reimportação duplicou cadastro");require(first.getJSONArray("devices").getJSONObject(0).getString("id").equals(again.getJSONArray("devices").getJSONObject(0).getString("id")),"ID não foi preservado");boolean rejected=false;try{br.casasala.painel.DeviceImport.parse(sample.replace("127.0.0.1","999.0.0.1"),first.getJSONArray("devices"));}catch(Exception expected){rejected=true;}require(rejected&&first.getJSONArray("devices").length()==1,"Importação inválida alterou dados");}
    private void testImportPreview()throws Exception{final File file=new File(getTargetContext().getFilesDir(),"lampadas-teste.json");FileOutputStream out=new FileOutputStream(file);out.write(importSample().getBytes("UTF-8"));out.close();runOnMainSync(new Runnable(){public void run(){try{Method method=activity.getClass().getDeclaredMethod("importUri",android.net.Uri.class);method.setAccessible(true);method.invoke(activity,android.net.Uri.fromFile(file));}catch(Exception e){throw new RuntimeException(e);}}});await("Conferir importação");captureTop("importacao-confirmar");click("Cancelar");require(!preferences.contains("local_devices"),"Cancelar importou dados");runOnMainSync(new Runnable(){public void run(){try{Method method=activity.getClass().getDeclaredMethod("importUri",android.net.Uri.class);method.setAccessible(true);method.invoke(activity,android.net.Uri.fromFile(file));}catch(Exception e){throw new RuntimeException(e);}}});await("Conferir importação");click("Importar");Thread.sleep(400);require(new JSONArray(preferences.getString("local_devices","[]")).length()==1&&!preferences.getBoolean("demo",true),"Confirmação não salvou cadastro real");}
    private void menu(String item)throws Exception{sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_MENU);await(item);click(item);}
    private View findSeek(){final View[] out={null};runOnMainSync(new Runnable(){public void run(){out[0]=findClass(activity.getWindow().getDecorView(),"br.casasala.painel.ThinSeekBar");}});return out[0];}
    private void swipeClock()throws Exception{
        final View[] deck={null};final int[] point=new int[2];runOnMainSync(new Runnable(){public void run(){deck[0]=findClass(activity.getWindow().getDecorView(),"br.casasala.painel.SwipeDeck");deck[0].getLocationOnScreen(point);}});
        float start=point[0]+deck[0].getWidth()*.8f,end=point[0]+deck[0].getWidth()*.2f,y=point[1]+deck[0].getHeight()*.5f;long time=android.os.SystemClock.uptimeMillis();
        sendPointerSync(android.view.MotionEvent.obtain(time,time,0,start,y,0));for(int i=1;i<=6;i++)sendPointerSync(android.view.MotionEvent.obtain(time,time+i*40,2,start+(end-start)*i/6,y,0));sendPointerSync(android.view.MotionEvent.obtain(time,time+300,1,end,y,0));Thread.sleep(600);
        final boolean[] shown={false};runOnMainSync(new Runnable(){public void run(){shown[0]=((android.widget.ViewFlipper)deck[0]).getDisplayedChild()==1;}});require(shown[0],"Deslizar não abriu o clima lateral");
    }
    private void orient(final boolean portrait)throws Exception{runOnMainSync(new Runnable(){public void run(){activity.setRequestedOrientation(portrait?1:0);}});Thread.sleep(1800);android.os.SystemClock.sleep(250);}

    private void launch() {
        Intent intent = new Intent();
        intent.setClassName("br.casasala.painel", "br.casasala.painel.MainActivity");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity = startActivitySync(intent);
        android.os.SystemClock.sleep(250);
    }

    private void restorePreferences() {
        if (preferences == null || original == null) return;
        SharedPreferences.Editor editor = preferences.edit().clear();
        for (Map.Entry<String, ?> entry : original.entrySet()) {
            Object value = entry.getValue(); String key = entry.getKey();
            if (value instanceof String) editor.putString(key, (String)value);
            else if (value instanceof Boolean) editor.putBoolean(key, ((Boolean)value).booleanValue());
            else if (value instanceof Integer) editor.putInt(key, ((Integer)value).intValue());
            else if (value instanceof Long) editor.putLong(key, ((Long)value).longValue());
            else if (value instanceof Float) editor.putFloat(key, ((Float)value).floatValue());
        }
        editor.commit();
    }

    private View find(View root, String text) {
        if (root instanceof TextView && text.equals(((TextView)root).getText().toString())) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = find(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private View findClass(View root, String className) {
        if (root.getClass().getName().equals(className)) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findClass(group.getChildAt(i), className);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** Only the API-8 test harness uses reflection to inspect its dialog windows. */
    private View[] windows() {
        try {
            Class<?> wmClass = Class.forName("android.view.WindowManagerImpl");
            Method get = wmClass.getDeclaredMethod("getDefault");
            get.setAccessible(true);
            Object manager = get.invoke(null);
            Field field = wmClass.getDeclaredField("mViews"); field.setAccessible(true);
            return (View[])field.get(manager);
        } catch (Exception ignored) {
            return new View[]{activity.getWindow().getDecorView()};
        }
    }

    private View findTop(String text) {
        View[] roots = windows();
        for (int i = roots.length - 1; i >= 0; i--) {
            View found = find(roots[i], text);
            if (found != null) return found;
        }
        return null;
    }

    private void await(final String text) throws Exception {
        for (int i = 0; i < 50; i++) {
            if (exists(text)) return;
            Thread.sleep(300);
        }
        throw new Exception("Texto não encontrado: " + text);
    }

    private boolean exists(final String text) {
        final boolean[] found = {false};
        runOnMainSync(new Runnable() { public void run() { found[0] = findTop(text) != null; } });
        return found[0];
    }

    private boolean activate(View view) {
        while (view != null) {
            ViewParent parent = view.getParent();
            if (parent instanceof AdapterView<?>) {
                AdapterView<?> adapter = (AdapterView<?>)parent;
                int position = adapter.getPositionForView(view);
                return position >= 0 && adapter.performItemClick(view, position, adapter.getItemIdAtPosition(position));
            }
            if (view.isClickable()) return view.performClick();
            view = parent instanceof View ? (View)parent : null;
        }
        return false;
    }

    private void click(final String text) throws Exception {
        final boolean[] clicked = {false};
        runOnMainSync(new Runnable() { public void run() {
            View view = findTop(text); if (view != null) clicked[0] = activate(view);
        } });
        if (!clicked[0]) throw new Exception("Clique não encontrado: " + text);
        android.os.SystemClock.sleep(250);
        Thread.sleep(120);
    }

    private void clickRowButton(final String label, final String button) throws Exception {
        final boolean[] clicked = {false};
        runOnMainSync(new Runnable() { public void run() {
            View item = find(activity.getWindow().getDecorView(), label);
            View ancestor = item;
            while (ancestor != null) {
                View target = find(ancestor, button);
                if (target != null) { clicked[0] = activate(target); return; }
                ViewParent parent = ancestor.getParent(); ancestor = parent instanceof View ? (View)parent : null;
            }
        } });
        if (!clicked[0]) throw new Exception("Botão " + button + " não encontrado na linha " + label);
        android.os.SystemClock.sleep(250);
    }

    private EditText firstEdit(View root) {
        if (root instanceof EditText) return (EditText)root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)root;
            for (int i = 0; i < group.getChildCount(); i++) {
                EditText found = firstEdit(group.getChildAt(i)); if (found != null) return found;
            }
        }
        return null;
    }

    private void setDialogText(final String value) throws Exception {
        final boolean[] changed = {false};
        runOnMainSync(new Runnable() { public void run() {
            View[] roots = windows();
            for (int i = roots.length - 1; i >= 0; i--) {
                EditText field = firstEdit(roots[i]);
                if (field != null) { field.setText(value); changed[0] = true; return; }
            }
        } });
        if (!changed[0]) throw new Exception("Campo de diálogo não encontrado.");
    }

    private void assertHasClock() throws Exception {
        final boolean[] found = {false};
        runOnMainSync(new Runnable() { public void run() {
            found[0] = findClass(activity.getWindow().getDecorView(), "br.casasala.painel.AnalogClockView") != null;
        } });
        require(found[0], "Relógio analógico não apareceu.");
    }

    private void assertSquareClock() throws Exception {
        final boolean[] square = {false};
        runOnMainSync(new Runnable() { public void run() {
            View clock = findClass(activity.getWindow().getDecorView(), "br.casasala.painel.AnalogClockView");
            square[0] = clock != null && clock.getWidth() > 100 && clock.getWidth() == clock.getHeight();
        } });
        require(square[0], "O relógio dedicado não foi medido como quadrado.");
    }

    private void assertDevicesBeforeClock() throws Exception {
        final boolean[] before = {false};
        runOnMainSync(new Runnable() { public void run() {
            View root = activity.getWindow().getDecorView();
            View clock = findClass(root, "br.casasala.painel.AnalogClockView"), device = find(root, "Abajur");
            int[] clockPosition = new int[2], devicePosition = new int[2];
            if (clock != null && device != null) {
                clock.getLocationOnScreen(clockPosition); device.getLocationOnScreen(devicePosition);
                before[0] = devicePosition[0] < clockPosition[0];
            }
        } });
        require(before[0], "A opção de posição dos dispositivos não mudou o painel.");
    }

    private void capture(String name) throws Exception { Thread.sleep(650);saveCapture(name, false); }
    private void captureTop(String name) throws Exception { saveCapture(name, true); }

    private void saveCapture(final String name, final boolean topWindow) throws Exception {
        final Throwable[] failed = {null};
        runOnMainSync(new Runnable() { public void run() {
            try {
                View view = activity.getWindow().getDecorView();
                if (topWindow) { View[] roots = windows(); if (roots.length > 0) view = roots[roots.length - 1]; }
                Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
                view.draw(new Canvas(bitmap));
                int density = getTargetContext().getResources().getDisplayMetrics().densityDpi;
                File file = new File(getTargetContext().getFilesDir(), prefix + "-" + density + "-" + name + ".png");
                FileOutputStream output = new FileOutputStream(file);
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); output.close(); bitmap.recycle();
            } catch (Throwable error) { failed[0] = error; }
        } });
        if (failed[0] != null) throw new Exception("Falha ao capturar " + name, failed[0]);
    }

    private void require(boolean value, String message) throws Exception {
        if (!value) throw new Exception(message);
    }
}
