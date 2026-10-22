# Preparação da edição pública

O projeto publicado foi preparado em uma pasta nova, sem copiar histórico Git, preferências, pastas de execução ou configurações da instalação original.

Entram apenas fontes Java, recursos próprios, fonte TrueType com licença, scripts de build, pontes genéricas, exemplos fictícios, testes e documentação reescrita. A publicação exclui:

- nomes dos moradores e caminhos de perfil do computador;
- endereços, MACs, IDs e chaves dos dispositivos reais;
- URLs/tokens de servidores e dados de login;
- chaves de assinatura e suas senhas;
- arquivos `.env`, preferências, snapshots, logs, modelos e backups;
- capturas da instalação real ou do chat privado.

A saudação padrão pública não tem nomes. TV começa sem IP/MAC e os servidores devem ser configurados pelo usuário. Exemplos usam `192.0.2.x`; endereços de loopback/broadcast e a rota padrão de emulador são constantes de funcionamento, não dados pessoais.

Antes do envio, um verificador examina fontes e o conteúdo descompactado do APK procurando dados identificadores e as chaves reais conhecidas da instalação usada no desenvolvimento. Capturas mostram DEMO e nomes da saudação vazios. A chave privada de assinatura fica fora do repositório; o certificado público do APK contém apenas uma identificação genérica do aplicativo.

O `.gitignore` ajuda a evitar inclusão acidental futura, mas revise cada commit. Ao abrir issues, remova tokens, local keys, endereços e identificadores dos exemplos.
