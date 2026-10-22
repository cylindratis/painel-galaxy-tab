# Compilação

1. Instale Java 8 (incluindo `keytool`) e Python 3.
2. Clone o repositório e execute a partir da raiz:

```sh
python android/build.py --java /caminho/para/java --output dist/Painel.apk
```

No Windows, `--java` pode apontar ao `java.exe` do JDK/JRE 8 instalado. As ferramentas são baixadas para `android/tools/`; nenhum SDK precisa estar instalado globalmente. `android/build/` contém apenas saídas locais. Arquitetura do app: Java 6, DEX API 8, assinatura JAR/v1.

O build padrão gera uma chave de desenvolvimento ignorada pelo Git. Para manter atualizações, preserve sua chave privada e use:

```sh
export PAINEL_KS_PASS='sua-senha-local'
python android/build.py --java /caminho/para/java --keystore /fora/do/repositorio/release.jks --output dist/Painel.apk
```

O alias esperado é `casa-sala`. A senha vem do ambiente, não do repositório. No PowerShell, defina `$env:PAINEL_KS_PASS`. Não envie a chave nem a senha em commits/issues. A chave usada para as releases publicadas não está nas fontes.

Os arquivos TrueType e a licença são incorporados via assets. A fonte é estática para o carregador de tipos do Android antigo. Não usa fonte variável nem depende de um serviço online.

Para testes HTTP de JVM, baixe também `org.json:json:20140107` do Maven Central para `android/tools/json.jar` e rode:

```sh
python tests/run_test.py
python tests/test_voice_queue.py
```

Esses testes usam servidores locais simulados. O APK não inclui os testes, seus tokens fictícios ou dados de uma residência.
