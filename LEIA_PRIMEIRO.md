# Ro para Android — versão 0.1

Este pacote contém o **projeto-fonte** da primeira versão nativa da Ro. Ele não contém um APK pronto e ainda não foi compilado em um Android SDK nesta sessão.

## O que já está implementado no código

- Aplicativo Android nativo em Java, sem MacroDroid e sem dependências de automação de terceiros.
- Tela inicial em português, com perfil simples e nome do usuário.
- Lista local de contatos autorizados, com adicionar e remover.
- Serviço Android `NotificationListenerService`.
- Filtro inicial exclusivo para notificações do WhatsApp (`com.whatsapp`).
- Comparação exata do nome/título da notificação com os contatos autorizados, sem autorizar ninguém por padrão.
- Ignora notificações-resumo e notificações identificadas pelo Android como conversa em grupo.
- Aviso da Ro com ação **Ler em voz alta**. O conteúdo só é falado depois que o usuário toca nessa ação.
- Aviso falado opcional para informar o nome de quem enviou a mensagem; vem desligado por padrão.
- Teste de Text-to-Speech e entrada de voz iniciada por botão.
- Perguntas simples respondidas localmente, sem enviar áudio ou texto para uma IA.

## O que ainda não faz

- Não tem cérebro de IA/LLM conectado. As respostas de conversa são regras locais bem simples.
- Não fica ouvindo a palavra “Ro” continuamente. O microfone só é acionado quando a pessoa toca em **Falar com a Ro**.
- Não lê conversas diretamente dentro do WhatsApp; usa somente a prévia que o próprio Android disponibiliza na notificação.
- Não responde nem envia mensagens, não abre conversas por conta própria e não acessa o conteúdo interno do WhatsApp.
- Não lê notificações de Gmail ou outros aplicativos nesta versão.
- Não acompanha a tela, não grava áudio e não possui cofre de senhas.
- Conversas em grupo são ignoradas quando o Android as identifica como grupo. Como cada fabricante e versão do WhatsApp pode expor notificações de maneira diferente, o filtro precisa ser testado no aparelho real.

## Privacidade e permissões

1. O acesso a notificações é concedido manualmente nas configurações do Android.
2. A lista de contatos fica no armazenamento privado do aplicativo.
3. A lista começa vazia. A Ro só processa notificações cujo título corresponda exatamente a um nome cadastrado.
4. A prévia não é enviada a um servidor nem gravada num histórico local. Para o botão de leitura funcionar, ela fica temporariamente anexada à ação da notificação da Ro, que expira após dois minutos no Android 8 ou superior.
5. O Android pode mostrar conteúdo de notificação na tela bloqueada conforme as configurações do aparelho. A notificação da Ro é marcada como privada, mas revise as opções de privacidade do seu telefone.
6. O aviso falado fica desativado até você ativá-lo.
7. Você pode remover um contato autorizado ou desligar o acesso nas configurações do Android a qualquer momento.

**Importante:** a permissão de leitor de notificações é ampla no Android. Embora este código filtre para WhatsApp e contatos cadastrados, o Android concede a permissão ao serviço para receber eventos de notificação do dispositivo. Ative somente se concordar com esse acesso.

## Como gerar o APK no Windows

### Pré-requisitos

- Instale o [Android Studio](https://developer.android.com/studio).
- Abra o Android Studio uma vez e instale o Android SDK Platform 35 e Android SDK Build-Tools pelo **Tools → SDK Manager**.
- Use o JDK 17 (o Android Studio já inclui um JBR compatível).
- Tenha internet disponível na primeira compilação para baixar o Gradle e o Android Gradle Plugin.

### Compilação

1. Extraia o ZIP para uma pasta, por exemplo `C:\Projetos\RoAndroid`.
2. Abra o Prompt de Comando nessa pasta.
3. Execute `build_windows.bat`.
4. Se o script encontrar o Android SDK e o Java, baixará Gradle 8.9 se necessário e tentará compilar o projeto.
5. O APK de teste deverá aparecer em `app\build\outputs\apk\debug\app-debug.apk`.
6. Copie o APK para o celular Android e instale. Talvez seja necessário permitir a instalação pelo aplicativo usado para abrir o arquivo.

Se o script não localizar o SDK, abra Android Studio → **Tools → SDK Manager**, instale a plataforma Android 35 e confirme o caminho do SDK. O caminho padrão do Windows costuma ser `%LOCALAPPDATA%\Android\Sdk`.

## Configuração no celular

1. Abra a Ro.
2. Toque em **Ativar avisos da Ro** e permita as notificações do aplicativo.
3. Toque em **Autorizar acesso às notificações**. Nas configurações do Android, encontre **Ro — Assistente pessoal** e ligue o acesso.
4. Volte à Ro. O status deverá mostrar o acesso ativado.
5. Em **Contatos autorizados**, cadastre o nome como aparece no título da notificação do WhatsApp.
6. Opcionalmente, ligue **A Ro pode anunciar o nome do contato**.
7. Peça a um contato autorizado para enviar uma mensagem individual de texto, com prévia de notificação habilitada no WhatsApp.
8. Quando a Ro reconhecer a notificação, ela criará um aviso próprio. Toque em **Ler em voz alta** para ouvir a prévia.
9. Use **Testar voz da Ro** para verificar a síntese de voz. Use **Falar com a Ro** para testar o reconhecimento de voz acionado por toque.

## Se não funcionar

- Confirme que o acesso de notificações da Ro está ativado nas configurações do Android.
- Confirme que as notificações do WhatsApp estão habilitadas e exibem prévia de texto.
- Confira se o título exibido pelo WhatsApp corresponde exatamente ao nome cadastrado.
- Teste primeiro uma conversa individual, não um grupo.
- Verifique se as notificações do próprio aplicativo Ro estão permitidas.
- Alguns aparelhos podem restringir processos em segundo plano; reinicie o telefone e reative o acesso se necessário.
- Para a voz em português, verifique se há um mecanismo de Text-to-Speech com dados de português instalado nas configurações do Android.

## Estrutura do projeto

```text
RoAndroid/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/br/com/roassistente/
│       │   ├── MainActivity.java
│       │   └── RoNotificationListener.java
│       └── res/
│           ├── drawable/ic_ro.xml
│           └── values/
│               ├── strings.xml
│               └── styles.xml
├── build.gradle
├── settings.gradle
├── gradle.properties
├── build_windows.bat
└── build_windows.ps1
```

## Próximo passo depois do primeiro teste

Testar o comportamento real no seu modelo de Android, ajustar a leitura das notificações que o WhatsApp realmente publica e só então integrar um provedor de IA por meio de um servidor seguro. Nenhuma chave de API deve ser colocada diretamente no aplicativo Android.

## Situação da validação deste pacote

- Estrutura de pastas e referências de classes: verificadas.
- XML do manifesto e recursos: validado como XML bem-formado.
- Sintaxe Java: analisada sem erros de parsing.
- **Compilação Android/APK:** ainda não executada; este ambiente de montagem não possui Android SDK nem Android Build Tools.
- **Teste em aparelho físico:** ainda não executado. O comportamento real das notificações deve ser confirmado no seu modelo de Android e na versão do WhatsApp instalada.

## Gerar o APK usando somente o celular (compilação na nuvem)

O Moto e20 não precisa compilar o aplicativo localmente. Use o GitHub Actions para compilar nos servidores do GitHub:

1. Entre em https://github.com/ pelo navegador e crie uma conta gratuita, se ainda não tiver.
2. Crie um repositório novo chamado `RoAndroid`. Selecione **Public** para facilitar o uso gratuito do GitHub Actions em repositórios públicos.
3. No repositório, use **Add file → Upload files** e envie o conteúdo desta pasta (incluindo a pasta oculta `.github/workflows`). Se o navegador não mostrar pastas ocultas ou não preservar a estrutura, faça upload do ZIP e extraia-o em um ambiente GitHub Codespaces, ou use GitHub Desktop em um computador.
4. Confirme o envio para a branch `main`.
5. Abra a aba **Actions**. O fluxo **Compilar APK da Ro** deverá iniciar automaticamente após o envio.
6. Aguarde o fluxo terminar com marca verde. Abra a execução concluída e procure **Artifacts** → `Ro-Android-debug`.
7. Baixe o ZIP do artefato, extraia `app-debug.apk` e instale no Android.

A compilação na nuvem ainda precisa ser executada. O APK só existirá para download depois que o GitHub Actions terminar com sucesso. Em repositórios públicos, o código-fonte ficará visível publicamente; não coloque senhas ou chaves privadas nele.
