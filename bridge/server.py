"""Casa Sala: local Tuya + Jellyfin bridge. No Home Assistant dependency."""
import concurrent.futures
import copy
import hmac
import ipaddress
import json
import os
import re
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path


class ApiError(Exception):
    def __init__(self, message, status=400):
        super().__init__(message)
        self.status = status


def identifier(value):
    if not isinstance(value, str) or not re.fullmatch(r'[A-Za-z0-9_-]{1,128}', value):
        raise ApiError('Identificador inválido.')
    return value


def read_config(path):
    cfg = json.loads(Path(path).read_text(encoding='utf-8'))
    if not isinstance(cfg.get('bridge_token'), str) or len(cfg['bridge_token']) < 24:
        raise ValueError('bridge_token precisa ter pelo menos 24 caracteres.')
    seen = set()
    for device in cfg.get('tuya', []):
        identifier(device['id'])
        if device['id'] in seen:
            raise ValueError('Cada controle precisa ter um id único.')
        seen.add(device['id'])
        if not str(device.get('switch_dp', '')).isdigit():
            raise ValueError('Informe switch_dp explicitamente para cada controle.')
        ipaddress.IPv4Address(device['ip'])
        if float(device.get('version', 0)) not in (3.1, 3.2, 3.3, 3.4, 3.5):
            raise ValueError('Versão Tuya inválida.')
        if len(device.get('local_key', '').encode()) != 16:
            raise ValueError('local_key deve ter 16 bytes.')
    return cfg


class Bridge:
    def __init__(self, cfg, tuya_factory=None):
        self.cfg = cfg
        self.tuya_factory = tuya_factory
        self.cache = {}
        self.lock = threading.Lock()
        self.device_locks = {}
        self.pool = concurrent.futures.ThreadPoolExecutor(max_workers=6)

    def cached(self, key, ttl, producer):
        with self.lock:
            entry = self.cache.get(key)
        if entry and time.monotonic() - entry[0] < ttl:
            return copy.deepcopy(entry[1])
        value = producer()
        with self.lock:
            self.cache[key] = (time.monotonic(), value)
        return copy.deepcopy(value)

    def device_lock(self, device):
        with self.lock:
            return self.device_locks.setdefault(device['device_id'], threading.Lock())

    def device(self, item_id):
        for device in self.cfg.get('tuya', []):
            if device['id'] == item_id:
                return device
        raise ApiError('Controle não cadastrado.', 404)

    def connect(self, cfg):
        factory = self.tuya_factory
        if factory is None:
            import tinytuya
            factory = tinytuya.Device
        device = factory(cfg['device_id'], cfg['ip'], cfg['local_key'], version=float(cfg['version']))
        device.set_socketTimeout(3)
        device.set_socketRetryLimit(1)
        return device

    def status(self, cfg):
        base = {'id': cfg['id'], 'name': cfg.get('name', cfg['id']),
                'room': cfg.get('room', 'Casa'), 'on': None, 'online': False}
        try:
            with self.device_lock(cfg):
                device = self.connect(cfg)
                try:
                    data = device.status()
                finally:
                    device.close()
            value = data.get('dps', {}).get(str(cfg['switch_dp'])) if isinstance(data, dict) else None
            if type(value) is not bool:
                base['error'] = 'Sem estado booleano. Confira a chave, o protocolo e o switch_dp.'
            else:
                base.update(on=value, online=True)
        except Exception:
            base['error'] = 'Sem resposta do dispositivo na rede local.'
        return base

    def devices(self):
        return self.cached('devices', 5, lambda: list(self.pool.map(self.status, self.cfg.get('tuya', []))))

    def set_device(self, item_id, body):
        if type(body.get('on')) is not bool:
            raise ApiError('O campo on deve ser true ou false.')
        cfg = self.device(item_id)
        with self.device_lock(cfg):
            device = self.connect(cfg)
            try:
                result = device.set_status(body['on'], switch=int(cfg['switch_dp']))
            finally:
                device.close()
        with self.lock:
            self.cache.pop('devices', None)
        if isinstance(result, dict) and ('Error' in result or 'Err' in result):
            raise ApiError('O dispositivo recusou o comando. Confira a configuração.', 502)
        # Never treat an empty acknowledgement as a confirmed state.
        state = self.status(cfg)
        state['confirmed'] = state['online'] and state['on'] == body['on']
        return state

    def jelly(self, path, query=None, method='GET', body=None, binary=False):
        cfg = self.cfg.get('jellyfin', {})
        base = cfg.get('url', '').rstrip('/')
        token = cfg.get('api_key', '')
        if not base or not token:
            raise ApiError('Configure a URL e a chave de API do Jellyfin na ponte.', 503)
        if urllib.parse.urlsplit(base).scheme not in ('http', 'https'):
            raise ApiError('URL Jellyfin inválida.', 503)
        url = base + path
        if query:
            url += '?' + urllib.parse.urlencode({k: v for k, v in query.items() if v is not None})
        data = json.dumps(body).encode() if body is not None else (b'' if method == 'POST' else None)
        request = urllib.request.Request(url, data=data, method=method,
                                        headers={'X-Emby-Token': token, 'Content-Type': 'application/json'})
        try:
            with urllib.request.urlopen(request, timeout=8) as response:
                payload = response.read(4 * 1024 * 1024 + 1)
                if len(payload) > 4 * 1024 * 1024:
                    raise ApiError('Resposta Jellyfin muito grande.', 502)
                if binary:
                    return payload, response.headers.get('Content-Type', 'image/jpeg')
                return json.loads(payload) if payload else {}
        except urllib.error.HTTPError as exc:
            if exc.code in (401, 403):
                raise ApiError('Jellyfin recusou a credencial ou o controle remoto.', 502) from None
            raise ApiError('Jellyfin respondeu com erro HTTP %s.' % exc.code, 502) from None
        except (urllib.error.URLError, TimeoutError, OSError):
            raise ApiError('Jellyfin não respondeu. Confira endereço e rede.', 502) from None

    def sessions(self):
        result = []
        for session in self.jelly('/Sessions', {'ActiveWithinSeconds': 120}):
            if not session.get('SupportsRemoteControl'):
                continue
            result.append({key: session.get(key) for key in
                           ('Id', 'DeviceId', 'DeviceName', 'Client', 'NowPlayingItem', 'PlayState', 'SupportedCommands')})
        return result

    def session(self, session_id):
        identifier(session_id)
        for session in self.sessions():
            if session['Id'] == session_id:
                return session
        raise ApiError('Abra o Jellyfin na TV e selecione a sessão novamente.', 409)

    def library(self, params):
        kind = params.get('kind', 'Audio')
        if kind not in ('Audio', 'MusicAlbum', 'Playlist', 'MusicArtist'):
            raise ApiError('Tipo de biblioteca inválido.')
        try:
            start = max(0, min(1000000, int(params.get('start', 0))))
        except ValueError:
            raise ApiError('Página inválida.') from None
        query = {'UserId': self.cfg.get('jellyfin', {}).get('user_id') or None,
                 'IncludeItemTypes': kind, 'Recursive': 'true', 'Limit': 40,
                 'StartIndex': start, 'SortBy': 'SortName', 'SortOrder': 'Ascending',
                 'SearchTerm': params.get('q', '')[:200] or None, 'Fields': 'PrimaryImageAspectRatio'}
        if params.get('parent'):
            query['ParentId'] = identifier(params['parent'])
        if params.get('artist'):
            query['ArtistIds'] = identifier(params['artist'])
        # Playlists require the playlist items endpoint to preserve order.
        if params.get('playlist'):
            query = {'UserId': query['UserId'], 'Limit': 40, 'StartIndex': start}
            payload = self.jelly('/Playlists/%s/Items' % identifier(params['playlist']), query)
        elif kind == 'MusicArtist':
            query.pop('IncludeItemTypes', None)
            payload = self.jelly('/Artists', query)
        else:
            payload = self.jelly('/Items', query)
        return {'items': [{key: item.get(key) for key in
                           ('Id', 'Name', 'Type', 'Artists', 'Album', 'RunTimeTicks', 'ImageTags')}
                          for item in payload.get('Items', [])],
                'total': payload.get('TotalRecordCount', 0), 'start': start}

    def play(self, body):
        session_id = identifier(body.get('session_id'))
        item_id = identifier(body.get('item_id'))
        self.session(session_id)
        item = self.jelly('/Items/' + item_id, {'UserId': self.cfg.get('jellyfin', {}).get('user_id') or None})
        kind = item.get('Type')
        if kind not in ('Audio', 'MusicAlbum', 'Playlist', 'MusicArtist'):
            raise ApiError('Escolha uma música, álbum, artista ou playlist.')
        if kind == 'Audio':
            ids = [item_id]
        else:
            user_id = self.cfg.get('jellyfin', {}).get('user_id') or None
            ids = []
            start = 0
            while True:
                query = {'UserId': user_id, 'Limit': 200, 'StartIndex': start}
                if kind == 'Playlist':
                    page = self.jelly('/Playlists/' + item_id + '/Items', query)
                else:
                    query.update(IncludeItemTypes='Audio', Recursive='true',
                                 SortBy='ParentIndexNumber,IndexNumber,SortName', SortOrder='Ascending')
                    query['ArtistIds' if kind == 'MusicArtist' else 'ParentId'] = item_id
                    page = self.jelly('/Items', query)
                items = page.get('Items', [])
                ids.extend(i['Id'] for i in items if i.get('Type') == 'Audio')
                start += len(items)
                if start >= page.get('TotalRecordCount', start) or not items:
                    break
                if start >= 200:
                    raise ApiError('Seleção grande demais. Escolha um álbum ou playlist de até 200 faixas.')
            if not ids:
                raise ApiError('Nenhuma faixa de áudio encontrada.')
        if len(ids) > 200:
            raise ApiError('Limite de 200 faixas por seleção.')
        self.jelly('/Sessions/' + session_id + '/Playing',
                   {'ItemIds': ','.join(ids), 'PlayCommand': 'PlayNow'}, method='POST')
        return {'accepted': True, 'tracks': len(ids), 'message': 'Comando enviado à TV.'}

    def command(self, body):
        session_id = identifier(body.get('session_id'))
        session = self.session(session_id)
        command = body.get('command')
        if command in ('Pause', 'Unpause', 'Stop', 'NextTrack', 'PreviousTrack'):
            self.jelly('/Sessions/%s/Playing/%s' % (session_id, command), method='POST')
        elif command in ('VolumeUp', 'VolumeDown'):
            if command not in (session.get('SupportedCommands') or []):
                raise ApiError('Este cliente Jellyfin não anuncia controle de volume.', 409)
            self.jelly('/Sessions/%s/Command/%s' % (session_id, command), method='POST')
        else:
            raise ApiError('Comando inválido.')
        return {'accepted': True, 'message': 'Comando enviado à TV.'}

    def weather(self):
        cfg = self.cfg.get('weather', {})
        if cfg.get('latitude') is None or cfg.get('longitude') is None:
            return {'configured': False}
        def fetch():
            query = urllib.parse.urlencode({'latitude': float(cfg['latitude']), 'longitude': float(cfg['longitude']),
                    'current': 'temperature_2m,relative_humidity_2m,weather_code',
                    'daily': 'temperature_2m_max,temperature_2m_min,precipitation_probability_max',
                    'timezone': 'auto', 'forecast_days': 1})
            try:
                with urllib.request.urlopen('https://api.open-meteo.com/v1/forecast?' + query, timeout=6) as response:
                    data = json.load(response)
                current, daily = data['current'], data['daily']
                return {'configured': True, 'city': cfg.get('city', 'Sua cidade'),
                        'temperature': current['temperature_2m'], 'humidity': current['relative_humidity_2m'],
                        'code': current['weather_code'], 'high': daily['temperature_2m_max'][0],
                        'low': daily['temperature_2m_min'][0], 'rain': daily['precipitation_probability_max'][0],
                        'updated': current['time'], 'source': 'Open-Meteo'}
            except (urllib.error.URLError, TimeoutError, OSError, ValueError, KeyError, IndexError):
                raise ApiError('Previsão indisponível no momento.', 502) from None
        return self.cached('weather', 900, fetch)

    def route(self, method, path, params, body):
        if method == 'GET' and path == '/v1/devices':
            return {'devices': self.devices()}
        if method == 'POST' and re.fullmatch(r'/v1/devices/[A-Za-z0-9_-]+', path):
            return self.set_device(path.rsplit('/', 1)[-1], body)
        if method == 'GET' and path == '/v1/weather':
            return self.weather()
        if method == 'GET' and path == '/v1/sessions':
            return {'sessions': self.sessions()}
        if method == 'GET' and path == '/v1/library':
            return self.library(params)
        if method == 'POST' and path == '/v1/play':
            return self.play(body)
        if method == 'POST' and path == '/v1/command':
            return self.command(body)
        raise ApiError('Rota não encontrada.', 404)


def handler_for(bridge):
    class Handler(BaseHTTPRequestHandler):
        def log_message(self, fmt, *args):
            # Do not log paths, tokens, request bodies or upstream error details.
            pass

        def setup(self):
            super().setup()
            self.connection.settimeout(15)

        def send(self, status, payload, content_type='application/json; charset=utf-8'):
            data = payload if isinstance(payload, bytes) else json.dumps(payload, ensure_ascii=False).encode('utf-8')
            self.send_response(status)
            self.send_header('Content-Type', content_type)
            self.send_header('Content-Length', str(len(data)))
            self.send_header('Cache-Control', 'no-store')
            self.send_header('X-Content-Type-Options', 'nosniff')
            self.end_headers()
            self.wfile.write(data)

        def do_GET(self):
            self.dispatch('GET')

        def do_POST(self):
            self.dispatch('POST')

        def dispatch(self, method):
            try:
                parsed = urllib.parse.urlsplit(self.path)
                if method == 'GET' and parsed.path == '/health':
                    self.send(200, {'ok': True, 'service': 'casa-sala'})
                    return
                received = self.headers.get('Authorization', '').encode('utf-8')
                expected = ('Bearer ' + bridge.cfg['bridge_token']).encode('utf-8')
                if not hmac.compare_digest(received, expected):
                    raise ApiError('Token da ponte inválido.', 401)
                if self.headers.get('Origin'):
                    raise ApiError('Use o aplicativo nativo.', 403)
                body = {}
                if method == 'POST':
                    try:
                        length = int(self.headers.get('Content-Length', '0'))
                    except ValueError:
                        raise ApiError('Tamanho inválido.') from None
                    if not 0 < length <= 8192:
                        raise ApiError('Corpo inválido ou muito grande.', 413)
                    try:
                        body = json.loads(self.rfile.read(length))
                    except (ValueError, UnicodeError):
                        raise ApiError('JSON inválido.') from None
                    if not isinstance(body, dict):
                        raise ApiError('Envie um objeto JSON.')
                params = {k: v[-1] for k, v in urllib.parse.parse_qs(parsed.query).items()}
                if method == 'GET' and re.fullmatch(r'/v1/art/[A-Za-z0-9_-]+', parsed.path):
                    item_id = identifier(parsed.path.rsplit('/', 1)[-1])
                    data, mime = bridge.jelly('/Items/%s/Images/Primary' % item_id,
                                             {'MaxWidth': 240, 'MaxHeight': 240, 'Quality': 75, 'Format': 'Jpg'}, binary=True)
                    self.send(200, data, 'image/jpeg')
                    return
                self.send(200, bridge.route(method, parsed.path, params, body))
            except ApiError as exc:
                self.send(exc.status, {'error': str(exc)})
            except (BrokenPipeError, ConnectionResetError):
                pass
            except Exception:
                self.send(500, {'error': 'Falha na ponte. Confira a configuração e a disponibilidade dos serviços.'})
    return Handler


if __name__ == '__main__':
    config = read_config(os.environ.get('CASA_CONFIG', '/config/config.json'))
    server = ThreadingHTTPServer((os.environ.get('CASA_BIND', '0.0.0.0'), 8090), handler_for(Bridge(config)))
    print('Casa Sala pronta na porta 8090.', flush=True)
    server.serve_forever()
