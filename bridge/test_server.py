import copy
import json
import threading
import unittest
import urllib.error
import urllib.parse
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from unittest.mock import patch

from server import ApiError, Bridge, handler_for


class Device:
    states = {'20': False}
    failure = False

    def __init__(self, *args, **kwargs):
        pass

    def set_socketTimeout(self, value):
        pass

    def set_socketRetryLimit(self, value):
        pass

    def close(self):
        pass

    def status(self):
        return {'Error': 'offline'} if self.failure else {'dps': self.states.copy()}

    def set_status(self, on, switch):
        if not self.failure:
            self.states[str(switch)] = on
        return None


class TestBridge(unittest.TestCase):
    def setUp(self):
        Device.failure = False
        Device.states = {'20': False}
        self.bridge = Bridge({'bridge_token': 'test-token-24-characters-long', 'tuya': [
            {'id': 'light', 'name': 'Luz', 'device_id': 'physical', 'ip': '192.0.2.50',
             'local_key': '1234567890123456', 'version': 3.3, 'switch_dp': 20}
        ]}, Device)
        self.addCleanup(self.bridge.pool.shutdown)

    def test_explicit_dp_and_readback_after_empty_ack(self):
        self.assertFalse(self.bridge.devices()[0]['on'])
        result = self.bridge.set_device('light', {'on': True})
        self.assertTrue(result['confirmed'])
        self.assertTrue(Device.states['20'])
        self.assertTrue(self.bridge.devices()[0]['on'])

    def test_offline_never_becomes_off_or_confirmed(self):
        Device.failure = True
        state = self.bridge.status(self.bridge.device('light'))
        self.assertFalse(state['online'])
        self.assertIsNone(state['on'])
        result = self.bridge.set_device('light', {'on': True})
        self.assertFalse(result['confirmed'])

    def test_missing_dp_is_not_a_switch(self):
        Device.states = {'20': 500}
        self.assertFalse(self.bridge.status(self.bridge.device('light'))['online'])

    def test_payload_validation(self):
        for invalid in ('false', 1, None):
            with self.assertRaises(ApiError):
                self.bridge.set_device('light', {'on': invalid})

    def test_session_revalidation_and_no_arbitrary_target(self):
        with patch.object(self.bridge, 'jelly', return_value=[]):
            with self.assertRaises(ApiError) as error:
                self.bridge.play({'session_id': 'expired', 'item_id': 'song'})
            self.assertEqual(error.exception.status, 409)

    def test_album_expands_into_ordered_audio_queue(self):
        calls = []
        def jelly(path, query=None, method='GET', body=None, **kwargs):
            calls.append((path, query, method, body))
            if path == '/Sessions':
                return [{'Id': 'tv', 'DeviceId': 'samsung', 'SupportsRemoteControl': True}]
            if path == '/Items/album':
                return {'Id': 'album', 'Type': 'MusicAlbum'}
            if path == '/Items':
                self.assertEqual(query['ParentId'], 'album')
                self.assertEqual(query['SortBy'], 'ParentIndexNumber,IndexNumber,SortName')
                return {'Items': [{'Id': 'song1', 'Type': 'Audio'}, {'Id': 'song2', 'Type': 'Audio'}], 'TotalRecordCount': 2}
            return {}
        with patch.object(self.bridge, 'jelly', side_effect=jelly):
            self.assertEqual(self.bridge.play({'session_id': 'tv', 'item_id': 'album'})['tracks'], 2)
        self.assertEqual(calls[-1][:3], ('/Sessions/tv/Playing', {'ItemIds': 'song1,song2', 'PlayCommand': 'PlayNow'}, 'POST'))

    def test_playlist_uses_playlist_order(self):
        calls = []
        def jelly(path, query=None, method='GET', **kwargs):
            calls.append(path)
            if path == '/Sessions':
                return [{'Id': 'tv', 'SupportsRemoteControl': True}]
            if path == '/Items/list':
                return {'Type': 'Playlist'}
            if path == '/Playlists/list/Items':
                return {'Items': [{'Id': 'b', 'Type': 'Audio'}, {'Id': 'a', 'Type': 'Audio'}], 'TotalRecordCount': 2}
            self.assertEqual(query['ItemIds'], 'b,a')
            return {}
        with patch.object(self.bridge, 'jelly', side_effect=jelly):
            self.bridge.play({'session_id': 'tv', 'item_id': 'list'})
        self.assertIn('/Playlists/list/Items', calls)

    def test_volume_requires_client_capability(self):
        with patch.object(self.bridge, 'sessions', return_value=[{'Id': 'tv', 'SupportedCommands': []}]):
            with self.assertRaises(ApiError):
                self.bridge.command({'session_id': 'tv', 'command': 'VolumeUp'})

    def test_artists_use_artist_endpoint(self):
        with patch.object(self.bridge, 'jelly', return_value={'Items': [], 'TotalRecordCount': 0}) as call:
            self.bridge.library({'kind': 'MusicArtist'})
            self.assertEqual(call.call_args.args[0], '/Artists')

    def test_large_playlist_is_refused_before_playing(self):
        calls = []
        def jelly(path, query=None, method='GET', **kwargs):
            calls.append(method)
            if path == '/Sessions':
                return [{'Id': 'tv', 'SupportsRemoteControl': True}]
            if path == '/Items/list':
                return {'Type': 'Playlist'}
            return {'Items': [{'Id': str(i), 'Type': 'Audio'} for i in range(200)], 'TotalRecordCount': 201}
        with patch.object(self.bridge, 'jelly', side_effect=jelly):
            with self.assertRaises(ApiError):
                self.bridge.play({'session_id': 'tv', 'item_id': 'list'})
        self.assertNotIn('POST', calls)

    def test_http_auth_validation_and_secret_redaction(self):
        server = ThreadingHTTPServer(('127.0.0.1', 0), handler_for(self.bridge))
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        self.addCleanup(server.server_close)
        self.addCleanup(server.shutdown)
        base = 'http://127.0.0.1:%s' % server.server_port
        self.assertTrue(json.load(urllib.request.urlopen(base + '/health'))['ok'])
        with self.assertRaises(urllib.error.HTTPError) as error:
            urllib.request.urlopen(base + '/v1/devices')
        self.assertEqual(error.exception.code, 401)
        headers = {'Authorization': 'Bearer ' + self.bridge.cfg['bridge_token']}
        request = urllib.request.Request(base + '/v1/devices', headers=headers)
        data = json.load(urllib.request.urlopen(request))
        self.assertTrue(data['devices'][0]['online'])
        self.assertNotIn('local_key', json.dumps(data))
        request = urllib.request.Request(base + '/v1/devices/light', headers=headers,
                                         data=b'{"on":"false"}', method='POST')
        with self.assertRaises(urllib.error.HTTPError) as error:
            urllib.request.urlopen(request)
        self.assertEqual(error.exception.code, 400)

    def test_real_http_jellyfin_headers_and_query_parameters(self):
        seen = []
        class Jelly(BaseHTTPRequestHandler):
            def log_message(self, *args):
                pass
            def do_GET(self):
                seen.append((self.path, self.headers.get('X-Emby-Token')))
                self.send_response(200)
                self.end_headers()
                self.wfile.write(b'{"Items": [], "TotalRecordCount": 0}')
        server = ThreadingHTTPServer(('127.0.0.1', 0), Jelly)
        threading.Thread(target=server.serve_forever, daemon=True).start()
        self.addCleanup(server.server_close)
        self.addCleanup(server.shutdown)
        self.bridge.cfg['jellyfin'] = {'url': 'http://127.0.0.1:%s' % server.server_port, 'api_key': 'secret', 'user_id': 'user'}
        self.bridge.library({'q': 'João & Maria', 'kind': 'Audio'})
        query = urllib.parse.parse_qs(urllib.parse.urlsplit(seen[0][0]).query)
        self.assertEqual(query['SearchTerm'], ['João & Maria'])
        self.assertEqual(query['UserId'], ['user'])
        self.assertEqual(seen[0][1], 'secret')


if __name__ == '__main__':
    unittest.main()
