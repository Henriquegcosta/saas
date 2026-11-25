# Notificações e comunicações do VIGENTRA

Este documento descreve a arquitetura multicanal (e-mail, WhatsApp e SMS), como ativar cada
provedor, o processo de aprovação dos modelos na Meta, as regras de consentimento (LGPD) e como
integrar um gateway de SMS no futuro.

> **Custos:** nada foi contratado. Sem credenciais, todos os canais funcionam em *modo
> desenvolvimento* (mensagens geradas em disco e registradas como **"Não enviado"**) ou ficam
> **desativados** — nunca há registro falso de envio.

---

## 1. Visão geral

A infraestrutura de envio é **centralizada no VIGENTRA**: as empresas clientes não configuram
SMTP nem fornecem credenciais. Cada empresa cadastra seus usuários, responsáveis e destinatários,
escolhe os canais por prazo de alerta e personaliza nome do remetente, resposta, cópias e assinatura.

```
                    ┌──────────── Rotina diária (07:00) + retentativas (hora em hora) ────────────┐
                    │  trava no banco (bloqueios_agendador) → só 1 instância executa              │
                    ▼                                                                             │
 Documento ─► PeriodoAlerta (D30/D15/D7/D0/POSn) ─► RegraCanalAlerta da empresa ─► canais do prazo │
                    │                                                                             │
                    ▼                                                                             │
     Destinatários (responsável ou usuário vinculado, gestores, cópias) + preferências/consentimento│
                    │                                                                             │
                    ▼                                                                             │
   Deduplicação: (documento, período, vencimento, CANAL, destinatário) → consulta + UNIQUE no MySQL│
                    │                                                                             │
     ┌──────────────┼───────────────────────────┬──────────────────────────┐                      │
     ▼              ▼                           ▼                          ▼                      │
 EmailService   WhatsAppService             SmsService             historico_envios ◄─ webhook Meta
 ├ Brevo (API)  ├ Cloud API (Meta)          └ (nenhum provedor)     status, tentativas, erro
 ├ SMTP         └ modo desenvolvimento                               provedor, id externo
 └ modo dev
```

### Código

| Pacote | Responsabilidade |
|---|---|
| `service/comunicacao/CanalMensageria` | Contrato comum dos canais (`canal()`, `status()`) |
| `service/comunicacao/email/` | `EmailProvider` + `BrevoEmailProvider`, `SmtpEmailProvider`, `DevEmailProvider`; fachada `EmailService` |
| `service/comunicacao/whatsapp/` | `WhatsAppProvider` + `WhatsAppCloudProvider`, `WhatsAppDevProvider`; `WhatsAppService`; `WhatsAppWebhookService` |
| `service/comunicacao/sms/` | `SmsProvider` (ponto de extensão) e `SmsService` |
| `service/alerta/AlertaService` | Rotina multicanal, retentativas, aviso administrativo |
| `service/comunicacao/RegraCanalService` | Canais por prazo (por empresa) |
| `service/comunicacao/PreferenciasService` | Preferências, consentimento, descadastro |
| `service/comunicacao/ComunicacaoContaService` + `EnvioContaListener` | E-mails da jornada (após o commit, em segundo plano) |
| `service/conta/` | Tokens de uso único, confirmação de e-mail, convites |

Adicionar um **provedor** (ex.: Amazon SES) = nova classe que implementa `EmailProvider` + uma
linha na seleção do `EmailService`. Adicionar um **canal** = valor em `CanalEnvio` + implementação
de `CanalMensageria`. Nada no restante do módulo precisa ser reescrito.

---

## 2. Régua e canais

| Prazo | Canais padrão (ajustável em *Configurações › Canais de comunicação*) |
|---|---|
| 30 dias antes | E-mail |
| 15 dias antes | E-mail |
| 7 dias antes | E-mail + WhatsApp |
| No dia do vencimento | E-mail + WhatsApp |
| Após o vencimento | E-mail + WhatsApp |

Um destinatário recebe por um canal somente se: **(1)** a regra da empresa usa o canal naquele
prazo; **(2)** a integração está ativa (ou em modo desenvolvimento); **(3)** a pessoa aceitou o
canal (WhatsApp/SMS exigem consentimento) e aquele tipo de alerta.

**Sem duplicidade:** antes de enviar, a mensagem é *reservada* no histórico; a chave única
`uk_envio_canal (documento_id, periodo, data_vencimento_ref, canal, destinatario)` impede um
segundo registro mesmo que duas execuções concorram. A trava `bloqueios_agendador` evita que duas
instâncias rodem a rotina ao mesmo tempo. Renovar o documento (nova data) reinicia a régua.

**Falhas:** registradas com a mensagem do provedor e nova tentativa em 60 min (até 3). Na falha
definitiva, os administradores recebem um *alerta administrativo*. Falhas reportadas pela Meta via
webhook (ex.: número sem WhatsApp) não são repetidas.

---

## 3. E-mail

### Brevo (recomendado — API transacional, plano gratuito de 300 e-mails/dia)
1. Crie uma conta em brevo.com (gratuita).
2. *Senders, Domains & Dedicated IPs* › adicione e **verifique o remetente** (ou o domínio, com SPF/DKIM).
3. *SMTP & API › API Keys* › gere uma chave.
4. No ambiente:
   ```
   BREVO_API_KEY=xkeysib-...
   MAIL_FROM=o-remetente-verificado@seudominio.com.br
   MAIL_FROM_NAME=VIGENTRA
   APP_BASE_URL=https://seu-dominio
   ```
5. Reinicie e use *Configurações › Canais de comunicação › Enviar e-mail de teste*.

### SMTP (alternativa)
`MAIL_PROVIDER=smtp`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`. Conta Gmail
pessoal serve apenas para testes rápidos — não é solução definitiva (limites e reputação).

### Proteções
* Cabeçalhos `List-Unsubscribe` e `List-Unsubscribe-Post` (descadastro com um clique, RFC 8058).
* Endereços de demonstração/reservados (`.demo`, `.example`, `.test`, `.invalid`, `example.com`)
  **não são enviados** a provedores reais (status *"Não enviado (endereço de demonstração)"*) —
  protege a reputação do remetente.

### Modelos (templates/email)
Layout único `base.html` + `botao.html`: `confirmacao-email`, `alteracao-email`, `boas-vindas`,
`convite`, `recuperacao-senha`, `senha-alterada`, `alerta-proximo`, `alerta-vencido`,
`alerta-admin`, `teste`. Tabelas e estilos inline (compatíveis com Gmail/Outlook/Apple Mail),
responsivos e com o link "Gerenciar preferências" no rodapé dos alertas.

---

## 4. WhatsApp Business Cloud API (oficial)

Somente a **API oficial da Meta** é usada (nenhuma automação não oficial).

### 4.1 Configuração
1. Em developers.facebook.com crie um **app** do tipo *Business* e adicione o produto **WhatsApp**.
2. Em *WhatsApp › API Setup* anote o **Phone number ID** e o **WhatsApp Business Account ID**.
   A Meta fornece um número de teste e permite cadastrar até 5 números destinatários para testes.
3. Gere um **token permanente**: *Business Settings › System users* › crie um usuário de sistema,
   atribua o app e o WABA, e gere o token com as permissões `whatsapp_business_messaging` e
   `whatsapp_business_management`. (O token temporário do painel expira em 24 h.)
4. Variáveis:
   ```
   WHATSAPP_ACCESS_TOKEN=EAAG...
   WHATSAPP_PHONE_NUMBER_ID=1234567890
   WHATSAPP_BUSINESS_ACCOUNT_ID=9876543210
   WHATSAPP_API_VERSION=v23.0
   ```
5. Teste com o modelo pré-aprovado `hello_world` (*Canais de comunicação › Enviar WhatsApp de
   teste*). O celular do usuário precisa estar cadastrado em *Preferências de notificação*.

### 4.2 Modelos de mensagem (aprovação na Meta)
Mensagens iniciadas pela empresa exigem **modelos aprovados**. Em *WhatsApp Manager › Message
templates › Create template*, categoria **Utility**, idioma **Portuguese (BR)**:

**`vigentra_vencimento_proximo`**
```
Olá, {{1}}. O documento *{{2}}* vence em {{3}} ({{4}}).
Empresa: {{5}}. Acesse o VIGENTRA para acompanhar a renovação.
```
Rodapé: `Responda SAIR para não receber mais estas mensagens.`
Exemplos: `Ana` · `Alvará de Funcionamento` · `25/11/2026` · `Vence em 7 dias` · `Clínica Vida Plena`

**`vigentra_documento_vencido`**
```
Olá, {{1}}. O documento *{{2}}* venceu em {{3}} ({{4}}) e precisa ser renovado.
Empresa: {{5}}. Atualize o VIGENTRA após a renovação.
```
Rodapé: `Responda SAIR para não receber mais estas mensagens.`

A ordem dos parâmetros enviada pelo sistema é: `{{1}}` nome, `{{2}}` documento, `{{3}}` data de
vencimento, `{{4}}` prazo ("Vence em 7 dias" / "Vencido há 3 dias"), `{{5}}` empresa. A aprovação
costuma levar de minutos a 24 h; modelos de categoria *Utility* não podem conter promoções. Se
usar outros nomes, ajuste `WHATSAPP_TEMPLATE_PROXIMO` / `WHATSAPP_TEMPLATE_VENCIDO`.

> **Modelos aprovados antes da troca de nome** (`docuguard_*`): continuam funcionando enquanto as
> variáveis `WHATSAPP_TEMPLATE_*` apontarem para eles (o script de migração da VM preserva esses
> valores). Como o texto aprovado na Meta ainda cita o nome antigo, crie os modelos `vigentra_*`,
> aguarde a aprovação e só então troque as variáveis.

### 4.3 Webhook (status de entrega e descadastro)
1. Defina `WHATSAPP_APP_SECRET` (*App settings › Basic › App secret*) e escolha um
   `WHATSAPP_WEBHOOK_VERIFY_TOKEN` (qualquer texto longo e aleatório).
2. Em *WhatsApp › Configuration › Webhook*: URL `https://SEU-DOMINIO/webhooks/whatsapp`, token
   de verificação = o valor acima; assine o campo **messages**.
3. O VIGENTRA valida a assinatura `X-Hub-Signature-256` de **toda** notificação (requisições sem
   assinatura válida recebem 401), atualiza o status (`Enviado › Entregue › Lido` ou `Falha`) e,
   quando a pessoa responde **SAIR / PARAR / STOP / CANCELAR**, revoga o consentimento de WhatsApp
   daquele número em todas as empresas.

O webhook exige HTTPS público (Nginx + certificado válido — Let's Encrypt, ver `docs/DEPLOY.md`).

### 4.4 Custos
A Meta cobra por conversa/mensagem de modelo (valores por país e categoria, tabela em
developers.facebook.com/docs/whatsapp/pricing). O número de teste e os primeiros envios de
desenvolvimento são gratuitos. Nada é cobrado enquanto as variáveis não estiverem configuradas.

---

## 5. SMS (preparado para integração futura)

Nenhum gateway foi contratado. O canal aparece como **"Indisponível nesta versão"**, a rotina não
o utiliza e nenhum envio é registrado. Preferências e consentimentos de SMS já podem ser salvos.

Para integrar um provedor (Zenvia, Twilio, Amazon SNS, ...):
1. Crie `service/comunicacao/sms/ZenviaSmsProvider.java` implementando `SmsProvider`:
   ```java
   @Component
   public class ZenviaSmsProvider implements SmsProvider {
       public String nome() { return "zenvia"; }
       public ResultadoEnvio enviar(String para, String texto) {
           // chamada HTTP com RestClient; credenciais via variáveis de ambiente
           // retorno: ResultadoEnvio.enviado("zenvia", idMensagem) ou ResultadoEnvio.falha("zenvia", erro)
       }
   }
   ```
2. Configure `SMS_PROVIDER=zenvia` e as credenciais do provedor em variáveis de ambiente.
3. Habilite a coluna SMS na régua (*Canais de comunicação*). O `SmsService` passa a usar o provedor
   automaticamente; texto limitado a 160 caracteres.

---

## 6. Preferências, consentimento e LGPD

* **Usuários** configuram em *Configurações › Preferências de notificação*: e-mail, WhatsApp,
  SMS, celular, tipos de alerta (próximo, no dia, vencido) e notificações no sistema.
* **Responsáveis sem login** recebem em cada alerta o link **"Gerenciar preferências"** (assinado
  com HMAC-SHA256 usando `APP_SECRET_KEY`; adulterar o link → 404) e **"Não receber mais"**.
* **Gestores** podem registrar a autorização de um responsável; a origem fica gravada como
  "Registrado por <nome> <e-mail>, que declarou ter obtido a autorização do titular".
* Todo consentimento guarda **data, hora e origem** (incluindo o IP quando feito pelo titular).
  Trocar o celular revoga os consentimentos anteriores.
* Alterações de preferências e descadastros entram no log de atividades.

---

## 7. E-mails da jornada da conta

| Evento | E-mail | Regras |
|---|---|---|
| Cadastro da empresa | Confirmação de e-mail | Token de 256 bits (só o hash no banco), 48 h, uso único; sem confirmar, a área logada fica bloqueada; reenvio limitado e invalida o link anterior |
| Confirmação | Boas-vindas | Enviado **somente** após confirmar (ou ao aceitar convite) |
| Convite | Convite para a empresa | Empresa e perfil definidos por quem convida (respeitando a hierarquia), 7 dias, uso único, cancelável e reenviável |
| Esqueci a senha | Recuperação | 30 min, uso único, resposta genérica (não revela se o e-mail existe), limite por IP e por conta |
| Senha redefinida/alterada | Aviso de segurança | Sessões encerradas |
| Troca do e-mail de login | Confirmação no novo endereço | O e-mail atual vale até a confirmação |
| Falha definitiva de alerta | Alerta administrativo | Para os administradores da empresa |

Os links de confirmação abrem uma página com botão (a ação acontece no POST): robôs de
pré-visualização de links de provedores corporativos não consomem os tokens. Os e-mails de conta
são enviados **após o commit** da transação e em segundo plano; tokens nunca vão para os logs.

---

## 8. Painéis

* **Empresa** — *Configurações › Canais de comunicação*: status das integrações (sem credenciais),
  régua por canal e testes. *Notificações › Alertas enviados*: histórico com canal, tipo de
  mensagem, destinatário, situação, tentativas, erro e reenvio.
* **Plataforma** — `/plataforma/comunicacao`, apenas para os e-mails listados em
  `APP_PLATFORM_ADMINS`: saúde das integrações e envios de **todas** as empresas (filtros por
  empresa, canal, situação e tipo), com destinatários **mascarados**. Cada consulta é auditada.
  Administradores de empresas recebem 403.
