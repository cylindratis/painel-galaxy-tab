"""Interactive configuration, run on the Linux host: python3 setup_config.py."""
import getpass
import json
import os
from pathlib import Path
import secrets

root = Path(__file__).resolve().parent
dest = root / 'config.json'
if dest.exists():
    raise SystemExit('config.json já existe. Edite o arquivo para preservar a configuração.')

cfg = json.loads((root / 'config.example.json').read_text(encoding='utf-8'))
cfg['bridge_token'] = secrets.token_urlsafe(32)
print('Casa Sala · configuração inicial')
cfg['jellyfin']['url'] = input('URL local do Jellyfin (ex.: http://192.0.2.10:8096): ').strip().rstrip('/')
cfg['jellyfin']['api_key'] = getpass.getpass('Chave de API Jellyfin (entrada oculta): ').strip()
cfg['jellyfin']['user_id'] = input('ID do usuário Jellyfin com acesso à música: ').strip()
cfg['weather']['city'] = input('Cidade para o clima (Enter para configurar depois): ').strip()
if cfg['weather']['city']:
    cfg['weather']['latitude'] = float(input('Latitude (ex.: -23.55): ').strip().replace(',', '.'))
    cfg['weather']['longitude'] = float(input('Longitude (ex.: -46.63): ').strip().replace(',', '.'))
with dest.open('x', encoding='utf-8') as out:
    json.dump(cfg, out, ensure_ascii=False, indent=2)
os.chmod(dest, 0o600)
if hasattr(os, 'getuid'):
    (root / '.env').write_text('CASA_UID=%s\nCASA_GID=%s\n' % (os.getuid(), os.getgid()), encoding='utf-8')
print('Arquivo config.json criado. Token para cadastrar no tablet:')
print(cfg['bridge_token'])
print('Agora execute: docker compose up -d --build')
