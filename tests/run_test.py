import json
from pathlib import Path
import subprocess
import threading
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

root = Path(__file__).resolve().parents[1]
calls = []

class Fixture(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def do_POST(self):
        self.process('POST')

    def do_GET(self):
        self.process('GET')

    def process(self, method):
        url = urllib.parse.urlsplit(self.path)
        assert url.path.startswith('/jellyfin/')
        path = url.path[len('/jellyfin'):]
        params = urllib.parse.parse_qs(url.query)
        body = self.rfile.read(int(self.headers.get('Content-Length', 0)))
        body = json.loads(body) if body else None
        assert 'own-device' in self.headers.get('Authorization', '')
        calls.append((method, path, params, body))
        auth = self.headers.get('Authorization', '')
        assert self.headers.get('X-Emby-Authorization') is None
        token = 'bad-token' if 'Token="bad-token"' in auth else 'fixture-token' if 'Token="fixture-token"' in auth else None
        if token == 'bad-token':
            return self.send(401, {'error': 'bad-token private upstream error'})
        if path == '/System/Info/Public':
            return self.send(200, {'ServerName': 'Fixture', 'Id': 'fixture-server'})
        if path == '/QuickConnect/Initiate':
            assert method == 'POST'
            return self.send(200, {'Code': '123456', 'Secret': 'secret-fixture', 'Authenticated': False})
        if path == '/QuickConnect/Connect':
            assert params['Secret'] == ['secret-fixture']
            return self.send(200, {'Code': '123456', 'Authenticated': False})
        if path in ('/Users/AuthenticateWithQuickConnect', '/Users/AuthenticateByName'):
            assert method == 'POST'
            assert body == ({'Secret': 'secret-fixture'} if 'QuickConnect' in path else {'Username': 'test', 'Pw': 'pw'})
            return self.send(200, {'AccessToken': 'fixture-token', 'User': {'Id': 'user1'}})
        assert token == 'fixture-token'
        if path == '/Sessions':
            assert params['ControllableByUserId'] == ['user1']
            return self.send(200, [
                {'Id': 'self', 'DeviceId': 'own-device', 'SupportsRemoteControl': True},
                {'Id': 'no-remote', 'DeviceId': 'no-remote', 'SupportsRemoteControl': False},
                {'Id': 'tv-session', 'DeviceId': 'tv-device', 'SupportsRemoteControl': True,
                 'SupportedCommands': ['VolumeUp'], 'PlayableMediaTypes': ['Audio', 'Video']}])
        if path == '/Users/Me':
            return self.send(200, {'Name': 'Tester'})
        if path == '/Items/song1/Images/Primary':
            self.send_response(200)
            self.end_headers()
            self.wfile.write(b'jpg')
            return
        if path.startswith('/Sessions/tv-session/'):
            assert method == 'POST'
            self.send_response(204)
            self.end_headers()
            return
        if path in ('/Items', '/Artists', '/Playlists/playlist1/Items'):
            assert params['UserId'] == ['user1']
            if params.get('ParentId') == ['album1'] or path == '/Playlists/playlist1/Items':
                return self.send(200, {'Items': [{'Id': 'song2', 'Type': 'Audio'}, {'Id': 'song1', 'Type': 'Audio'}], 'TotalRecordCount': 2})
            kind = 'MusicArtist' if path == '/Artists' else params['IncludeItemTypes'][0]
            assert params['SearchTerm'] == ['João & Maria']
            return self.send(200, {'Items': [{'Id': 'row1', 'Type': kind, 'Name': 'Name', 'Path': '/private/path'}], 'TotalRecordCount': 1})
        if path.startswith('/Items/'):
            kind = {'song1': 'Audio', 'movie1': 'Movie', 'episode1': 'Episode', 'album1': 'MusicAlbum',
                    'playlist1': 'Playlist', 'series1': 'Series'}[path.rsplit('/', 1)[-1]]
            return self.send(200, {'Id': path.rsplit('/', 1)[-1], 'Type': kind})
        self.send(404, {})

    def send(self, status, data):
        payload = json.dumps(data).encode()
        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)


sdk = Path(__import__('os').environ.get('PAINEL_TOOLS',str(root / 'android/tools')))
classes = root / 'android/build/qa'
classes.mkdir(parents=True,exist_ok=True)
java = __import__('os').environ.get('JAVA8') or __import__('shutil').which('java') or 'java'
source = root / 'android/src/br/casasala/painel/JellyfinClient.java'
subprocess.run([str(java), '-jar', str(sdk / 'ecj.jar'), '-1.6', '-encoding', 'UTF-8', '-nowarn',
                '-bootclasspath', str(sdk / 'android-2.2_r03-linux/android.jar'), '-d', str(classes), str(source), str(source.parent / "VoiceIntent.java")], check=True)
subprocess.run([str(java), '-jar', str(sdk / 'ecj.jar'), '-1.6', '-encoding', 'UTF-8', '-nowarn',
                '-classpath', str(classes) + __import__('os').pathsep + str(sdk / 'json.jar'), '-d', str(classes),
                str(root / 'tests/ClientTest.java')], check=True)
server = ThreadingHTTPServer(('127.0.0.1', 0), Fixture)
threading.Thread(target=server.serve_forever, daemon=True).start()
try:
    subprocess.run([str(java), '-cp', str(classes) + __import__('os').pathsep + str(sdk / 'json.jar'), 'ClientTest',
                    'http://127.0.0.1:%s' % server.server_port], check=True)
    queues = [params['ItemIds'][0] for method, path, params, body in calls if method == 'POST' and path.endswith('/Playing')]
    assert queues == ['song1', 'movie1', 'episode1', 'song2,song1', 'song2,song1'], queues
    print('PASS exact queue ordering, movies/episodes and QuickConnect HTTP payloads')
finally:
    server.shutdown()
    server.server_close()
