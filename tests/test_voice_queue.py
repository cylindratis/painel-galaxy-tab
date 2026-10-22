from pathlib import Path
import subprocess,threading,json
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
from urllib.parse import urlsplit,parse_qs
root=Path(__file__).resolve().parents[1];calls=[]
class Fixture(BaseHTTPRequestHandler):
 def log_message(self,*a):pass
 def do_GET(self):
  p=urlsplit(self.path);q=parse_qs(p.query);assert 'Token="fixture-token"' in self.headers.get('Authorization','');calls.append((p.path,q))
  if p.path in ('/Artists','/MusicGenres'):
   name=q['SearchTerm'][0];rows=[{'Id':'artist1' if p.path=='/Artists' else 'genre1','Name':'Artista de teste' if p.path=='/Artists' else 'Samba'}]
   if name=='ambiguous':rows=[{'Id':'a','Name':'Ambiguous'},{'Id':'b','Name':'Ambiguous'}]
   body={'Items':rows,'TotalRecordCount':len(rows)}
  else:
   assert p.path=='/Items';assert 'ArtistIds' in q or 'GenreIds' in q;start=int(q['StartIndex'][0]);limit=int(q['Limit'][0]);body={'Items':[{'Id':'song'+str(i),'Type':'Audio','Name':'Música '+str(i),'RunTimeTicks':10000000} for i in range(start,min(450,start+limit))],'TotalRecordCount':450}
  b=json.dumps(body).encode();self.send_response(200);self.send_header('Content-Length',str(len(b)));self.end_headers();self.wfile.write(b)
sdk=Path(__import__('os').environ.get('PAINEL_TOOLS',str(root/'android/tools')));classes=root/'android/build/qa';classes.mkdir(parents=True,exist_ok=True);java=__import__('os').environ.get('JAVA8') or __import__('shutil').which('java') or 'java';sources=root/'android/src/br/casasala/painel'
subprocess.run([java,'-jar',str(sdk/'ecj.jar'),'-1.6','-encoding','UTF-8','-nowarn','-classpath',str(sdk/'json.jar'),'-d',str(classes),str(sources/'JellyfinClient.java'),str(sources/'VoiceIntent.java'),str(root/'tests/VoiceQueueTest.java')],check=True)
server=ThreadingHTTPServer(('127.0.0.1',0),Fixture);threading.Thread(target=server.serve_forever,daemon=True).start()
try:
 subprocess.run([java,'-cp',str(classes)+__import__('os').pathsep+str(sdk/'json.jar'),'VoiceQueueTest','http://127.0.0.1:'+str(server.server_port)],check=True)
 assert [q['StartIndex'][0] for p,q in calls if p=='/Items']==['0','200','400']*2
finally:server.shutdown()
