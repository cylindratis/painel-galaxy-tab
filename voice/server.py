"""LAN voice appliance: no Tuya/Jellyfin credentials, no retained recordings.
Audio: signed PCM16 little endian, mono 16000 Hz. Commands execute on tablet.
"""
import asyncio
import collections
import hmac
import io
import os
import re
import subprocess
import threading
import time
import wave
from pathlib import Path
import numpy as np
from fastapi import FastAPI, Request, HTTPException
from fastapi.responses import Response

app = FastAPI(docs_url=None, redoc_url=None, openapi_url=None)
TOKEN = os.environ.get('VOICE_TOKEN', '')
if len(TOKEN) < 24:
    raise RuntimeError('VOICE_TOKEN deve ter ao menos 24 caracteres.')
READY = False
INIT_ERROR = False
WHISPER = None
WAKE_FACTORY = None
STT_LOCK = threading.Lock()
SESSIONS = {}
SESSION_LOCK = asyncio.Lock()
TTS_GATE = asyncio.Semaphore(2)
THRESHOLD = float(os.environ.get('WAKE_THRESHOLD', '.55'))

def initialize():
    global READY, INIT_ERROR, WHISPER, WAKE_FACTORY
    try:
        from openwakeword.utils import download_models
        from openwakeword.model import Model
        from faster_whisper import WhisperModel
        folder = Path(os.environ.get('OWW_DIR', '/models/wake'))
        folder.mkdir(parents=True, exist_ok=True)
        download_models(model_names=['alexa'], target_directory=str(folder))
        wake_files = list(folder.glob('alexa*.onnx'))
        if not wake_files:
            raise RuntimeError('Modelo Alexa ausente')
        def factory():
            return Model(wakeword_models=[str(wake_files[0])],
                         melspec_model_path=str(folder / 'melspectrogram.onnx'),
                         embedding_model_path=str(folder / 'embedding_model.onnx'),
                         inference_framework='onnx')
        WAKE_FACTORY = factory
        factory()  # Fail health check before accepting microphone audio.
        WHISPER = WhisperModel(os.environ.get('WHISPER_MODEL', 'small'), device='cpu',
                              compute_type='int8', download_root='/models/whisper', cpu_threads=4)
        READY = True
        print('Voz pronta: Alexa / português / processamento local.', flush=True)
    except Exception as exc:
        INIT_ERROR = True
        # Never include incoming speech, tokens or HTTP headers in logs.
        print('Falha ao carregar modelos: ' + type(exc).__name__ + ' ' + str(exc)[:250], flush=True)

@app.on_event('startup')
async def startup():
    threading.Thread(target=initialize, daemon=True).start()

def auth(request):
    if not hmac.compare_digest(request.headers.get('authorization', ''), 'Bearer ' + TOKEN):
        raise HTTPException(401, 'Chave incorreta')

@app.get('/health')
async def health(request: Request):
    auth(request)
    return {'ready': READY, 'error': INIT_ERROR, 'wake_word': 'alexa', 'language': 'pt', 'version': '0.7'}

class Session:
    def __init__(self):
        self.wake = WAKE_FACTORY()
        self.pre = collections.deque(maxlen=10)  # 0.8 s, avoids clipping the command.
        self.pending = []
        self.listening = False
        self.silence = 0
        self.speech = 0
        self.last = time.monotonic()
        self.busy = False
        self.task = None
        self.result = None
        self.cooldown = 0
        self.tail = np.empty(0, dtype=np.int16)

    def feed(self, samples):
        self.last = time.monotonic()
        if self.task and self.task.done():
            try:
                text = self.task.result()
            except Exception:
                text = ''
            self.task = None
            self.result = text or None
            self.cooldown = 25
            self.wake.reset()
        if self.result:
            text, self.result = self.result, None
            self.tail = np.empty(0, dtype=np.int16)
            return {'state': 'recognized', 'text': text}
        if self.task:
            return {'state': 'processing'}
        samples = np.concatenate((self.tail, samples))
        end = len(samples) - len(samples) % 1280
        self.tail = samples[end:].copy()
        for offset in range(0, end, 1280):
            frame = samples[offset:offset + 1280]
            self.pre.append(frame.copy())
            if self.cooldown:
                self.cooldown -= 1
                continue
            if not self.listening:
                scores = self.wake.predict(frame)
                if max(scores.values(), default=0) >= THRESHOLD:
                    self.listening = True
                    self.pending = list(self.pre)
                    self.silence = self.speech = 0
                continue
            self.pending.append(frame.copy())
            rms = np.sqrt(np.mean(frame.astype(np.float32) ** 2))
            if rms > 260:
                self.speech += 1
                self.silence = 0
            else:
                self.silence += 1
            if (self.speech >= 3 and self.silence >= 12) or len(self.pending) >= 175:
                audio = np.concatenate(self.pending).astype(np.float32) / 32768.0
                self.pending = []
                self.listening = False
                self.task = asyncio.create_task(asyncio.to_thread(transcribe, audio))
                self.tail = np.empty(0, dtype=np.int16)
                break
        return {'state': 'processing' if self.task else 'listening' if self.listening else 'idle'}

def transcribe(audio):
    with STT_LOCK:
        segments, info = WHISPER.transcribe(audio, language='pt', beam_size=5, vad_filter=True,
                                          initial_prompt='Português brasileiro. Comandos de casa: liga as luzes da sala, apaga o abajur, brilho cinquenta por cento, TV, volume, HDMI, música, Artista de teste, samba, previsão de chuva.',
                                          condition_on_previous_text=False)
        text = ' '.join(s.text.strip() for s in segments).strip()
    # The wake model gates the recording; remove just the leading wake word.
    text = re.sub(r'^\s*alexa[\s,.!:;-]*', '', text, flags=re.I)
    return text[:240]

@app.post('/v1/audio')
async def audio(request: Request, session: str):
    auth(request)
    if not READY:
        raise HTTPException(503, 'Modelos ainda carregando')
    if not re.fullmatch(r'[a-zA-Z0-9-]{8,64}', session):
        raise HTTPException(400, 'Sessão inválida')
    length = request.headers.get('content-length', '')
    if not length.isdigit() or not 2560 <= int(length) <= 64000 or int(length) % 2:
        raise HTTPException(413, 'Bloco PCM inválido')
    data = await request.body()
    if len(data) != int(length):
        raise HTTPException(400, 'Bloco incompleto')
    async with SESSION_LOCK:
        now = time.monotonic()
        for old in list(SESSIONS):
            if now - SESSIONS[old].last > 90 and not SESSIONS[old].task:
                del SESSIONS[old]
        if session not in SESSIONS:
            if len(SESSIONS) >= 3:
                raise HTTPException(429, 'Limite de tablets')
            SESSIONS[session] = Session()
        return SESSIONS[session].feed(np.frombuffer(data, dtype='<i2'))

@app.post('/v1/speak')
async def speak(request: Request):
    auth(request)
    if int(request.headers.get('content-length', '9999')) > 2048:
        raise HTTPException(413, 'Texto muito longo')
    data = await request.json()
    text = str(data.get('text', ''))[:400].replace('\x00', '')
    async with TTS_GATE:
        def generate():
            raw = subprocess.run(['espeak-ng', '-v', 'pt-br', '-s', '155', '--stdout', '--', text],
                                 check=True, capture_output=True, timeout=15).stdout
            # stdout espeak has placeholder RIFF lengths. Froyo needs real lengths.
            with wave.open(io.BytesIO(raw), 'rb') as source:
                params = source.getparams()
                frames = source.readframes(source.getnframes())
            result = io.BytesIO()
            with wave.open(result, 'wb') as target:
                target.setparams(params)
                target.writeframes(frames)
            return result.getvalue()
        wav = await asyncio.to_thread(generate)
    return Response(wav, media_type='audio/wav', headers={'Cache-Control': 'no-store'})

if __name__ == '__main__':
    import uvicorn
    uvicorn.run(app, host='0.0.0.0', port=8765, access_log=False)
