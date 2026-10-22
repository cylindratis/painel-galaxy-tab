package br.casasala.painel;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Locale;

/** Seven-day forecast with honest missing values and bounded native views. */
public final class WeatherPanel extends ScrollView {
    private final VisualTheme theme;
    private final LinearLayout body;
    private final float density;
    public WeatherPanel(Context c,VisualTheme t){super(c);theme=t;density=getResources().getDisplayMetrics().density;body=col();addView(body);}
    private int dp(int n){return Math.round(n*density);}
    private LinearLayout col(){LinearLayout v=new LinearLayout(getContext());v.setOrientation(1);return v;}
    private TextView text(String s,int size,int ink){TextView v=new TextView(getContext());v.setText(s);if(theme.id.equals("lcars"))v.setTypeface(LcarsTypeface.get(getContext(),theme));v.setTextColor(ink);v.setTextSize(size);v.setPadding(0,dp(3),0,dp(3));return v;}
    public static String condition(int code){return code==0?"Céu limpo":code<=3?"Entre nuvens":code<=48?"Neblina":code<=67?"Chuva":code<=77?"Neve":code<=86?"Pancadas":"Trovoadas";}
    private String value(JSONObject d,String key,String unit){Object x=d.opt(key);return x==null||x==JSONObject.NULL?"—":String.valueOf(x)+unit;}
    private String clock(String value){return value.length()>=16?value.substring(11,16):"—";}
    private String date(String value){try{return new SimpleDateFormat("EEE, d MMM",new Locale("pt","BR")).format(new SimpleDateFormat("yyyy-MM-dd",Locale.US).parse(value));}catch(Exception e){return value;}}
    public void update(JSONObject weather,boolean demo){
        body.removeAllViews();body.addView(text(weather.optString("city","Previsão do tempo"),24,theme.ink));
        if(!weather.optBoolean("configured")){body.addView(text("Escolha sua cidade em Ajustes > Buscar cidade.",15,theme.muted));return;}
        LinearLayout current=new LinearLayout(getContext());current.setGravity(Gravity.CENTER_VERTICAL);current.addView(new WeatherIcon(getContext(),weather.optInt("code"),theme),new LinearLayout.LayoutParams(dp(44),dp(44)));current.addView(text(value(weather,"temperature","°")+" · "+condition(weather.optInt("code")),24,theme.ink));body.addView(current);
        body.addView(text(WeatherAdvice.rain(weather),21,theme.ink));body.addView(text(WeatherAdvice.comfort(weather),16,theme.muted));
        body.addView(text("Sensação "+value(weather,"apparent","°")+"    Umidade "+value(weather,"humidity","%")+"\nVento "+value(weather,"wind"," km/h")+"    Pressão "+value(weather,"pressure"," hPa"),14,theme.muted));
        body.addView(text(demo?"Demonstração · dados fictícios":"Open-Meteo · dados CC BY 4.0 · previsão de "+weather.optString("updated","data não informada"),12,theme.muted));
        JSONArray days=weather.optJSONArray("days");
        if(days==null||days.length()==0){body.addView(text("Aguardando a próxima atualização para carregar os sete dias.",14,theme.muted));return;}
        body.addView(text("Próximos sete dias",19,theme.ink));
        for(int i=0;i<days.length();i++){
            final JSONObject day=days.optJSONObject(i);if(day==null)continue;
            LinearLayout row=col();row.setBackgroundDrawable(theme.background(getContext(),theme.panel,false));row.setPadding(dp(14),dp(12),dp(14),dp(12));
            LinearLayout top=new LinearLayout(getContext());top.setGravity(Gravity.CENTER_VERTICAL);
            top.addView(new WeatherIcon(getContext(),day.optInt("weather_code"),theme),new LinearLayout.LayoutParams(dp(36),dp(36)));
            TextView name=text(i==0?"Hoje · "+date(day.optString("time")):date(day.optString("time")),16,theme.ink);top.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            top.addView(text(value(day,"temperature_2m_min","°")+" / "+value(day,"temperature_2m_max","°"),19,theme.ink));row.addView(top);
            row.addView(text(condition(day.optInt("weather_code"))+" · Chuva "+value(day,"precipitation_probability_max","%")+" / "+value(day,"precipitation_sum"," mm"),14,theme.muted));
            row.addView(text("Vento máx. "+value(day,"wind_speed_10m_max"," km/h")+" · UV "+value(day,"uv_index_max","")+"\nSol "+clock(day.optString("sunrise"))+" — "+clock(day.optString("sunset")),13,theme.muted));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(8),0,0);body.addView(row,lp);
        }
        JSONObject hourly=weather.optJSONObject("hourly");
        if(hourly!=null){
            body.addView(text("Próximas horas",19,theme.ink));JSONArray times=hourly.optJSONArray("time"),temps=hourly.optJSONArray("temperature_2m"),rain=hourly.optJSONArray("precipitation_probability");
            String now=weather.optString("updated");int shown=0;
            for(int i=0;times!=null&&i<times.length()&&shown<8;i++)if(times.optString(i).compareTo(now)>=0){
                if(shown++%2==0)body.addView(text(clock(times.optString(i))+"    "+(temps==null?"—":temps.optString(i))+"°    Chuva "+(rain==null?"—":rain.optString(i))+"%",14,theme.muted));
            }
        }
    }
}
