# Iluminação, Jellyfin e Samsung

## Tuya

Use uma local key obtida da sua conta/dispositivo, protocolo real e IP reservado. Confirme os DPs de liga/desliga, modo, brilho, temperatura e RGB. Há perfis comuns A/B e edição manual, mas fabricante/potência não identificam os DPs com segurança.

Importe um arquivo conforme `examples/lampadas.example.json`. Os campos e a lista são validados antes de salvar; cancelar não grava. Cadastros repetidos preservam o identificador. Um cartão sem resposta oferece nova consulta; reconectar não deve acionar a lâmpada sozinho.

## Jellyfin

Endereço completo em Ajustes, teste de URL, Quick Connect ou login. Para autorizar um código, use um cliente Jellyfin já conectado. Para TV, mantenha o Jellyfin aberto e selecione a sessão no player; o controle Samsung não abre automaticamente esse aplicativo.

Áudio no tablet usa stream MP3 e depende da permissão de transcodificação do servidor. Vídeos vão para a sessão da TV. Capas de música são quadradas; filmes/séries usam cartazes verticais. Letras e seus tempos precisam existir no Jellyfin.

Filas de álbum/playlist/artista/gênero são carregadas por páginas e iniciam na faixa escolhida. Há limite explícito de 10 mil itens. Uma seleção ambígua por voz pede escolha visual.

## TV

Configure os dados do próprio aparelho e aceite a autorização na TV. A porta LAN padrão é 8001; 8002 depende de TLS validável neste Android. O app não desativa validação de certificado.

Disponíveis: energia, volume, mudo, entradas/HDMI, canais, setas/OK, Menu/Início/Voltar/Sair e transporte. UPnP permite definir mudo/volume com leitura de confirmação quando suportado. Entradas diretas e ligar por Wake-on-LAN dependem do firmware/rede em standby. Use Entradas e setas se HDMI direto não funcionar.

Identificação/consulta de áudio foram feitas numa K5300. Os testes automatizados de atuação usam simuladores. Não se promete compatibilidade universal Samsung.
