# Ro — segurança e permissões

## Regra principal

A Ro não observa a tela, não lê notificações e não acessa mensagens por padrão. Cada capacidade é desligada inicialmente, explicada em linguagem simples e acionada apenas pelo pedido do usuário ou por uma configuração explícita.

## Acompanhamento da tela

O comando pretendido é “Ro, acompanhe minha tela”. A Ro deve abrir a confirmação oficial de captura do Android; somente depois da confirmação a sessão poderá começar. “Ro, pare de acompanhar minha tela” encerra a sessão, e o botão “Parar agora” funciona como interrupção de emergência. A aplicação não pode esconder o indicador do Android, iniciar captura silenciosa ou continuar após a revogação da permissão.

A captura deve ser tratada como dado altamente sensível: processamento temporário, sem gravação permanente por padrão, sem envio para a IA sem uma confirmação separada e descarte automático ao terminar. Se no futuro houver gravação, ela deverá ser uma ação distinta, com nome, duração e local de armazenamento visíveis.

## WhatsApp, mensagens e notificações

A integração inicial deve usar a permissão de acesso a notificações do Android, e não automação por MacroDroid. O usuário escolhe os contatos permitidos individualmente. Por exemplo, de dez contatos, oito podem ser autorizados e dois podem permanecer bloqueados. O conteúdo de contatos bloqueados deve ser descartado sem ser enviado para a IA. Acesso direto a conversas do WhatsApp ou envio automático de mensagens depende de APIs oficiais e não deve ser simulado por engenharia reversa.

## E-mail

A conexão deve usar OAuth. A Ro não deve pedir nem armazenar a senha do e-mail. O usuário pode revogar a conexão e visualizar quais permissões foram concedidas.

## Senhas e contas

A opção segura é um cofre local criptografado, protegido por um código mestre que a Ro não conhece. Senhas nunca entram no histórico de conversa, nunca são enviadas ao modelo de IA e não ficam em texto puro no banco ou nos logs. O Android Keystore deve proteger a chave local; exportação, sincronização em nuvem e recuperação precisam ser escolhas explícitas e não podem ser ativadas por padrão.

## Proteções contra abuso

A Ro deve aplicar menor privilégio, confirmação para ações externas, limite de tentativas, bloqueio após falhas, atualização segura, validação de origem de comandos, logs sem segredos, separação entre interface e credenciais, chaves somente no servidor e exclusão de dados sob demanda. Nenhum comando recebido da tela, de uma notificação ou de uma mensagem deve ganhar permissão para executar outra ação sem passar pelas regras locais.

## Limite do protótipo atual

A interface e as regras de intenção estão preparadas, mas a captura real de tela, o NotificationListenerService, o cofre Android e o OAuth ainda exigem implementação nativa e validação em um build Android. A prévia Web não consegue comprovar permissões do sistema nem captura de outros aplicativos.
