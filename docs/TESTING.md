# Validação e limites

A versão 0.8 foi compilada como Java 6 contra o framework Android API 8, com assinatura v1 verificada. A interface foi executada em emulador ARM Android 2.2, em 1024×600 e 600×1024, densidade 240 dpi. As capturas do README são Views reais com dados DEMO e sem nomes na saudação.

## Verificações desta versão

- Instrumentação LCARS: seleção e persistência do tema, relógio quadrado, painel com sete lâmpadas, seletores RGB/branco e controle de TV sem ScrollView e sem botões cortados nas duas orientações.
- Mídia e previsão abertas em DEMO; tema Branco preservado ao alternar temas.
- Fonte TrueType incorporada carregada pelo Froyo. A alternativa de fonte do sistema também é verificada pelo teste de release.
- Fixtures HTTP Jellyfin: 37 verificações nativas de cliente, Quick Connect, cabeçalho de autorização, seleção de sessão, filmes/episódios e ordem da fila de álbuns/playlists.
- Pedidos de artista/gênero com 450 faixas: paginação 0/200/400, rejeição de nomes ambíguos e interpretação de comandos de voz.
- Fontes públicas e entradas descompactadas do APK verificadas contra os identificadores/segredos conhecidos do ambiente de desenvolvimento. Capturas inspecionadas visualmente.

Os testes desta entrega não acionaram lâmpadas ou TV reais. DEMO comprova o fluxo da interface; fixtures comprovam pedidos e respostas de protocolo. Não comprovam firmware, Wi-Fi, codecs, microfone ou desempenho do tablet físico. A opção de 60 quadros/s não garante 60 fps em todo aparelho.

## Repetir testes HTTP

Após compilar o app e baixar `org.json:json:20140107` para `android/tools/json.jar`:

```sh
python tests/run_test.py
python tests/test_voice_queue.py
```

`JAVA8` pode apontar ao executável Java 8 e `PAINEL_TOOLS` pode substituir a pasta de ferramentas. Os servidores de teste escutam somente em loopback.

## Instrumentação Android

`V8SmokeTest.java` e `V8ReleaseCheck.java` são testes separados do APK de entrega. Compile o app primeiro e então:

```sh
python tests/build_instrumentation.py --java /caminho/para/java
adb install -r dist/Painel.apk
adb install -r android/build/instrumentation/test.apk
adb shell am instrument -w br.casasala.test/.V8SmokeTest
adb shell am instrument -w br.casasala.test/.V8ReleaseCheck
```

App e teste precisam da mesma assinatura: use `--keystore` e `PAINEL_KS_PASS` iguais nos dois builds. O teste completo salva PNGs no diretório privado `files` do app e restaura as preferências anteriores; o teste curto de release limpa as preferências do emulador e inicia em DEMO. Execute em um emulador ou instalação exclusiva para testes.

Para reproduzir capturas menores, use também um perfil de 160 dpi. Não transfira preferências ou arquivos de importação pessoais para a instalação usada nas capturas públicas.
