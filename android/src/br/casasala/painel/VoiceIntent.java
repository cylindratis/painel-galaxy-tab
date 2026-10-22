package br.casasala.painel;
import java.util.Locale;import java.util.regex.*;import org.json.*;
/** Deliberately bounded Portuguese commands. Unknown requests never become device writes. */
public final class VoiceIntent{
 public static String fold(String raw){String s=raw==null?"":raw.toLowerCase(Locale.US);String a="áàâãäéèêëíìîïóòôõöúùûüç²",b="aaaaaeeeeiiiiooooouuuuc2";for(int i=0;i<a.length();i++)s=s.replace(a.charAt(i),b.charAt(i));return s.replaceAll("[^a-z0-9% ]"," ").replaceAll(" +"," ").trim();}
 public static JSONObject parse(String raw)throws Exception{
  String s=fold(raw).replaceFirst("^alexa +","").replaceFirst(" +por favor$","");JSONObject out=new JSONObject().put("text",s).put("type","unknown");
  if(s.matches("^(liga|ligue|desliga|desligue|apaga|apague|acende|acenda|coloca|coloque|muda|mude|deixa|deixe|aumenta|aumente|diminui|diminua) .* (e|depois) .*"))return out;
  if(s.matches(".*(chover|chuva|previsao|tempo hoje|tempo amanha|temperatura hoje|temperatura amanha).*"))return out.put("type","weather").put("tomorrow",s.indexOf("amanha")>=0);
  if(s.matches("(que horas sao|qual e a hora|me diga as horas)"))return out.put("type","time");
  if(s.matches("(que dia e hoje|qual e a data|qual a data)"))return out.put("type","date");
  if(s.matches("(ajuda|o que voce faz|quais comandos)"))return out.put("type","help");
  if(s.matches("(toque|toca|reproduza|reproduzir) (no |na )?(tablet|tv|televisao)"))return out.put("type","output").put("target",s.endsWith("tablet")?"tablet":"tv");
  if(s.matches("(pausa|pause|pausar|pause a musica|pausa a musica)"))return out.put("type","media").put("command","Pause");
  if(s.matches("(continua|continue|continuar|retoma|retome|continue a musica)"))return out.put("type","media").put("command","Unpause");
  if(s.matches("(proxima|proxima musica|pula a musica|pule a musica)"))return out.put("type","media").put("command","NextTrack");
  if(s.matches("(anterior|musica anterior|volta a musica|volte a musica)"))return out.put("type","media").put("command","PreviousTrack");
  if(s.matches("(pare|parar|para a musica|pare a musica)"))return out.put("type","media").put("command","Stop");
  if(s.indexOf("tv")>=0||s.indexOf("televisao")>=0){
   if(s.matches("(liga|ligue|ligar|desliga|desligue|desligar) (a )?(tv|televisao)"))return out.put("type","tv").put("command",s.startsWith("des")?"KEY_POWEROFF":"wake");
   if(s.matches("(aumenta|aumente|suba|sobe|abaixa|abaixe|diminua|diminui) (o )?volume (da |do )?(tv|televisao)"))return out.put("type","tv").put("command",s.matches("^(aument|sub|sob).*" )?"KEY_VOLUP":"KEY_VOLDOWN").put("repeats",3);
   if(s.matches("(silencia|silencie|tira o som|tire o som) (da |a )?(tv|televisao)"))return out.put("type","tv").put("command","mute:on");
   if(s.matches("(ativa o som|ative o som|tira o mudo|tire o mudo) (da |a )?(tv|televisao)"))return out.put("type","tv").put("command","mute:off");
   if(s.matches("mudo (da |a )?(tv|televisao)"))return out.put("type","tv").put("command","KEY_MUTE");
   Matcher volume=Pattern.compile("^(?:ajusta|ajuste|coloca|coloque) (?:o )?volume (?:da |do )?(?:tv|televisao) (?:em|para|a) ([0-9]{1,3})(?:%| por cento)?$").matcher(s);if(volume.matches()&&Integer.parseInt(volume.group(1))<=100)return out.put("type","tv").put("command","volume:"+volume.group(1));
   Matcher input=Pattern.compile("^(?:muda|mude|troca|troque|coloca|coloque) (?:a )?(?:tv|televisao) (?:para |na |no |em )?(hdmi [12]|hdmi[12]|tv|antena)$").matcher(s);if(input.matches())return out.put("type","tv").put("command",input.group(1).startsWith("hdmi")?"KEY_HDMI"+input.group(1).replaceAll("[^12]",""):"KEY_TV");
   Matcher channel=Pattern.compile("^(?:muda|mude|coloca|coloque) (?:a )?(?:tv|televisao) (?:no |para o )?canal ([0-9]{1,4})$").matcher(s);if(channel.matches())return out.put("type","tv").put("channel",Integer.parseInt(channel.group(1)));
  }
  Matcher light=Pattern.compile("^(liga|ligue|ligar|acende|acenda|desliga|desligue|desligar|apaga|apague) (.+)$").matcher(s);
  if(light.matches()){String target=target(light.group(2));if(target.length()>0)return out.put("type","lights").put("target",target).put("action",new JSONObject().put("on",!light.group(1).matches("^(des|apag).*")));}
  Matcher bright=Pattern.compile("^(?:coloca|coloque|ajusta|ajuste|muda|mude) (.+?) (?:em|para|a) ([0-9]{1,3})(?:%| por cento)?$").matcher(s);
  if(bright.matches()){int n=Integer.parseInt(bright.group(2));if(n<=100&&target(bright.group(1)).length()>0)return out.put("type","lights").put("target",target(bright.group(1))).put("action",new JSONObject().put("brightness",n).put("preserve_mode",true));}
  Matcher relative=Pattern.compile("^(aumenta|aumente|diminui|diminua|abaixa|abaixe) (?:o )?brilho (?:da |do |de )?(.+)$").matcher(s);if(relative.matches())return out.put("type","lights").put("target",target(relative.group(2))).put("action",new JSONObject().put("delta",relative.group(1).startsWith("aument")?10:-10).put("preserve_mode",true));
  Matcher color=Pattern.compile("^(?:deixa|deixe|coloca|coloque|muda|mude) (.+?) (?:em |na cor |para )?(vermelha|vermelho|verde|azul|amarela|amarelo|roxa|roxo|rosa|laranja|branco quente|branco frio)$").matcher(s);
  if(color.matches()){String c=color.group(2),t=target(color.group(1));if(t.length()>0){JSONObject action=new JSONObject().put("brightness",100);if(c.startsWith("branco"))action.put("mode","white").put("temperature",c.endsWith("quente")?0:100);else action.put("mode","colour").put("saturation",100).put("hue",c.startsWith("vermelh")?0:c.equals("verde")?120:c.equals("azul")?240:c.startsWith("amarel")?60:c.startsWith("rox")?280:c.equals("rosa")?320:30);return out.put("type","lights").put("target",t).put("action",action);}}
  Matcher music=Pattern.compile("^(?:toca|toque|reproduza|reproduzir) (.+)$").matcher(s);if(music.matches()){String q=music.group(1);String mode="artist";if(q.startsWith("musica ")){mode="genre";q=q.substring(7);}else if(q.startsWith("genero ")){mode="genre";q=q.substring(7);}else if(q.startsWith("artista "))q=q.substring(8);else if(q.startsWith("a musica ")){mode="track";q=q.substring(9);}if(q.trim().length()>0)return out.put("type","music").put("mode",mode).put("query",q.trim());}
  return out;
 }
 private static String target(String s){return s.replaceFirst("^(?:o brilho |brilho )","").replaceFirst("^(?:todas as |as |a |o |os )","").replaceFirst("^(?:luzes|lampadas|luz|lampada) ?","").replaceFirst("^(?:da |do |de )","").trim();}
 private VoiceIntent(){}
}
