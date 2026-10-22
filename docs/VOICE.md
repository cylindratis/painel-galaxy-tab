# Ponte opcional de voz

Requisitos: Docker com CPU/memória para Whisper small, conexão à internet na primeira inicialização para baixar modelos e acesso LAN pelo tablet.

```sh
cd voice
cp .env.example .env
# Gere uma chave privada, por exemplo: python -c "import secrets; print(secrets.token_urlsafe(32))"
# Preencha VOICE_TOKEN em .env; o arquivo fica fora do Git.
docker compose up -d --build
```

Modelos ficam no volume `voice/models`. Pode mover esse volume e o armazenamento do Docker para o disco desejado. Aguarde os modelos carregarem; `/health` exige `Authorization: Bearer SUA_CHAVE` e informa `ready`.

No tablet, configure URL HTTP local e a mesma chave em Menu > Voz. Também pode preencher `examples/voz.example.json`, renomear e importar uma cópia privada. Exemplos usam endereços de documentação: substitua-os pelo endereço do seu computador. Não publique o arquivo preenchido.

No Linux/Docker, a porta padrão é 8765. Em Windows/WSL com encaminhamento apenas de loopback, `lan_relay.py` encaminha a porta LAN 8766 para 8765 e mantém a distribuição Ubuntu ativa. Ajuste a distribuição no script se usar outra. Libere no firewall somente a porta e a sub-rede doméstica necessárias, sem abrir o roteador à internet.

Ative a escuta no app. O microfone segue à ponte enquanto a atividade está em primeiro plano; um botão visível permite desligar e sair da atividade interrompe a captura. Áudio é PCM16 mono/16 kHz. O servidor detecta Alexa, delimita fala, transcreve em português e devolve texto; `VoiceIntent` executa a intenção no tablet. Respostas faladas são eSpeak NG em WAV. A ponte não precisa de login Jellyfin nem de chaves Tuya.

Comandos incluem ambientes/grupos/nomes de lâmpadas, brilho absoluto/relativo, RGB/branco, TV, saída Tablet/TV, artista/gênero/faixa, transporte, chuva hoje/amanhã e hora/data. Dê um comando por vez. Isto não é o serviço Amazon Alexa e não entende qualquer pedido arbitrário.

Foi testado áudio sintético passando por wake, português e síntese. O WAV foi reproduzido no Froyo. Distância, ruído, latência e o microfone de cada tablet precisam de validação física. A voz sintetizada tem timbre robótico. Toque continua funcionando sem a ponte.
