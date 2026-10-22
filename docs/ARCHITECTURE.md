# Arquitetura

```
Tablet Android API 8
  ├─ TuyaLan / TuyaCrypto / LightingProfile → lâmpadas por Wi-Fi
  ├─ JellyfinClient → servidor Jellyfin
  ├─ TabletAudio → MediaPlayer / MP3
  ├─ SamsungRemote → WebSocket / UPnP / Wake-on-LAN
  ├─ WeatherClient / QuoteClient → dados públicos
  └─ VoiceCapture → ponte local → texto reconhecido
                         ↓
                    VoiceIntent no tablet → mesmos controles acima
```

`MainActivity` usa Views nativas, workers para rede e callbacks na thread da interface. Tokens/configurações ficam em preferências privadas. Os Drawables compartilham `VisualTheme`; LCARS usa `LcarsFrame` e a fonte estática `LcarsTypeface`, com fallback para a fonte padrão.

O app oferece controle direto; a pasta `bridge/` preserva uma ponte opcional de versões anteriores. `voice/` é outra ponte, dedicada ao reconhecimento/síntese, sem precisar de credenciais Tuya/Jellyfin.

Alterações de cor/brilho são enviadas ao terminar o gesto. Um último ajuste pendente é preservado enquanto o anterior termina. Grupos guardam IDs estáveis, separados dos nomes.

Jellyfin usa `Authorization: MediaBrowser ...`, Quick Connect e `ApiKey` para o stream de áudio. Filas de áudio são paginadas e compactadas antes de entrar na memória. A fila remota é observada pelo app e não pressupõe que todo cliente Tizen implemente uma playlist recebida.

Falhas de rede não viram confirmação de sucesso. O protocolo da TV permite confirmar o áudio via UPnP; um envio WebSocket permanece identificado como envio. Voz desconhecida ou com seleção ambígua não é interpretada como ação aproximada sobre uma lâmpada.
