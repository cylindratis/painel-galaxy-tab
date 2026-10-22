package br.casasala.painel;
import org.json.JSONObject;import org.json.JSONArray;
/** Practical copy derived only from fields present in the forecast; no invented certainty. */
public final class WeatherAdvice{
 public static String rain(JSONObject weather){if(!weather.optBoolean("configured"))return "Escolha sua cidade em Ajustes para receber a previsão.";if(!weather.has("rain")||weather.isNull("rain"))return "A previsão ainda não informou a chance de chuva.";int chance=weather.optInt("rain");return chance>=40?"Leve um guarda-chuva: há "+chance+"% de chance de chover hoje.":chance>0?"Há "+chance+"% de chance de chuva hoje. Vale conferir antes de sair.":"A previsão indica 0% de chance de chuva hoje.";}
 public static String comfort(JSONObject w){String text="";if(w.has("high")&&!w.isNull("high")&&w.optDouble("high")>=30)text="Vai esquentar: máxima de "+Math.round(w.optDouble("high"))+"°. Leve água e procure sombra.";else if(w.has("low")&&!w.isNull("low")&&w.optDouble("low")<=16)text="Leve um casaco: a mínima prevista é de "+Math.round(w.optDouble("low"))+"°.";else if(w.has("high")&&!w.isNull("high")&&w.has("low")&&!w.isNull("low"))text="O dia varia de "+Math.round(w.optDouble("low"))+"° a "+Math.round(w.optDouble("high"))+"°.";
 JSONArray days=w.optJSONArray("days");JSONObject day=days==null?null:days.optJSONObject(0);if(day!=null&&day.optDouble("uv_index_max")>=6)text+=" Proteja a pele: UV previsto de "+day.optDouble("uv_index_max")+".";if(w.has("wind")&&w.optDouble("wind")>=30)text+=" Atenção ao vento: "+Math.round(w.optDouble("wind"))+" km/h.";return text.trim();}
}
