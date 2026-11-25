# VIGENTRA — Gestor de Validade de Documentos e Certificados

Micro-SaaS multi-tenant para controlar o vencimento de alvarás, certidões negativas, contratos, CNHs,
certificados digitais, licenças e documentos de funcionários, com **alertas automáticos por e-mail e
WhatsApp** (SMS preparado), jornada completa da conta (confirmação, convites, recuperação) e
infraestrutura de envio centralizada.

> Projeto acadêmico de Desenvolvimento Full Stack (disciplina Innovation Lab – SaaS / Infrastructure),
> com scripts e documentação para implantação em VM Linux.

---

## 1. Objetivo e problema resolvido

Prestadores de serviço, escritórios, clínicas e pequenas empresas controlam dezenas de documentos com
prazos de validade diferentes — geralmente em planilhas ou na memória de uma pessoa. Quando um documento
vence sem que ninguém perceba, a empresa sofre **multas, interdições, contratos suspensos, licitações
perdidas, colaboradores impedidos de trabalhar** e até a impossibilidade de emitir notas fiscais
(certificado digital vencido).

O VIGENTRA centraliza esses documentos, **calcula automaticamente a situação** de cada um e **avisa os
responsáveis no momento certo** (30, 15 e 7 dias antes, no dia e após o vencimento).

## 2. Funcionalidades

| Área | O que faz |
|---|---|
| **Página pública** | Apresentação do produto, funcionalidades, como funciona, benefícios, segurança, CTA, termos e política de privacidade |
| **Autenticação** | Login com mostrar/ocultar senha, "lembrar acesso" (14 dias), bloqueio após 5 tentativas, limite por IP, recuperação de senha por link seguro com aviso de "senha alterada" |
| **Cadastro de empresa** | Nome, empresa, CNPJ (validado), e-mail, telefone, senha forte com medidor, aceite dos termos (registrado – LGPD) e **confirmação obrigatória do e-mail** antes de acessar o sistema; boas-vindas após confirmar |
| **Convites** | Administrador/gestor convida por e-mail com perfil definido; o convidado cria a própria senha; link de uso único, 7 dias, reenviável e cancelável |
| **Comunicação multicanal** | E-mail (API Brevo ou SMTP), WhatsApp (Cloud API oficial da Meta, modelos aprovados, webhook de status e descadastro), SMS preparado; canais por prazo configuráveis; preferências e consentimento por pessoa; painel da plataforma |
| **Dashboard** | Totais por situação, sem vencimento, próximos vencimentos, vencidos, últimos cadastrados, histórico de notificações, gráfico de situação (clicável) e de vencimentos por mês, atalhos |
| **Documentos** | Cadastrar, editar, ver detalhes, excluir (com confirmação), pesquisa, filtros por situação/categoria/responsável/empresa/período, ordenação, anexo PDF/imagem com pré-visualização, documentos sem vencimento |
| **Calendário** | Grade mensal interativa com cores por situação, navegação entre meses, detalhes ao clicar numa data |
| **Notificações** | Alertas do usuário (lidas/não lidas, por tipo, marcar como lida, abrir documento) e histórico de mensagens de todos os canais (data/hora, canal, tipo, destinatário, situação, tentativas, erro, reenviar, executar verificação agora) |
| **Log de atividades** | Cadastros, alterações (com diferença dos campos), exclusões, login/logout, falhas de login, alterações de usuários e permissões, envios e falhas de e-mail — com pesquisa e filtros |
| **Relatórios** | Totais, vencidos, próximos, regulares, por mês, por categoria, por responsável — exportação **PDF, Excel (.xlsx) e CSV** |
| **Usuários** | Convidar, editar, desativar/reativar, enviar link de redefinição de senha, excluir (somente admin), perfis, convites pendentes, consultar atividades do usuário |
| **Responsáveis** | Cadastro de quem recebe os alertas (e-mail, celular, canais e consentimento registrado pelo gestor), vínculo opcional com usuário |
| **Configurações** | Dados da empresa, dados do usuário, troca do e-mail de login (com confirmação), senha, preferências de notificação (canais, celular, consentimento, tipos de alerta), régua de alertas, **canais de comunicação** (status das integrações, canais por prazo, testes de envio), personalização dos e-mails, sistema, categorias, permissões, exportar meus dados (LGPD), sair |

### Níveis de acesso

| Perfil | Padrão |
|---|---|
| **Administrador** | Acesso completo à empresa; único que exclui usuários e altera permissões |
| **Gestor** | Documentos, relatórios, responsáveis, notificações e usuários de nível *Usuário* |
| **Usuário** | Cadastra e edita documentos; **vê apenas os documentos sob sua responsabilidade** |

As permissões de Gestor e Usuário podem ser ajustadas por empresa em *Configurações › Gerenciamento de permissões*.

### Rotina automática de alertas (multicanal)

Executada **todos os dias às 07:00** (configurável) para todas as empresas, sob uma trava no banco
(apenas uma instância executa por vez):

1. consulta os documentos com vencimento dentro da maior antecedência configurada;
2. identifica o **período de alerta** atual de cada um (`D30`, `D15`, `D7`, `D0`, `POS1`, `POS2`…);
3. localiza o responsável, administradores/gestores que aceitam e-mail e os e-mails em cópia;
4. escolhe os canais do prazo (regra da empresa) e respeita preferências e consentimento de cada pessoa;
5. monta o e-mail (modelo HTML com identidade visual) ou a mensagem de WhatsApp (modelo aprovado na Meta);
6. envia pelo provedor do canal e registra cada mensagem em `historico_envios` (e a notificação no sistema);
7. **impede duplicidade**: consulta prévia + `UNIQUE (documento, período, vencimento, canal, destinatário)` no banco;
8. **registra falhas** com a mensagem do provedor, agenda nova tentativa (a cada hora, até 3) e, na falha definitiva, envia um alerta administrativo.

Usar *faixas* (e não o dia exato) garante o aviso mesmo que o servidor fique desligado num dia.
Renovar o documento (nova data de vencimento) reinicia a régua automaticamente.

**Modo desenvolvimento:** sem credenciais de envio (`BREVO_API_KEY` ou `MAIL_HOST`), **nenhum e-mail é
enviado**: a mensagem é gravada em `data/emails-dev/*.html` e o histórico mostra *"Não enviado (modo
desenvolvimento)"*. O WhatsApp sem credenciais fica **não configurado** (não é usado), ou em modo
desenvolvimento com `WHATSAPP_DEV_MODE=true` (gera `data/whatsapp-dev/*.json`). O SMS aparece como
indisponível e nunca gera registros. Detalhes completos em **[docs/NOTIFICACOES.md](docs/NOTIFICACOES.md)**.

## 3. Tecnologias

| Camada | Tecnologia | Por quê |
|---|---|---|
| Linguagem | **Java 25** (LTS) | Disponível nas VMs Linux via pacote Java 25; compilado com JDK 25 |
| Backend | **Spring Boot 3.5** (Web MVC, Security, Data JPA, Validation, Mail, Actuator, RestClient) | Ecossistema maduro, um único JAR executável |
| Comunicação | **Brevo** (API transacional) ou SMTP; **WhatsApp Business Cloud API** (Meta) | Infraestrutura centralizada, sem custo até configurar |
| Frontend | **Thymeleaf** + HTML5/CSS3/JavaScript, **Bootstrap 5.3**, **Bootstrap Icons**, **Chart.js 4** | Renderização no servidor (proteção XSS nativa); bibliotecas via WebJars, sem depender de CDN |
| Banco | **MySQL 9.7** (compatível com 8.x) | Requisito do projeto |
| Relatórios | **OpenPDF** (PDF) e **Apache POI** (Excel) | Bibliotecas livres |
| Testes | JUnit 5, Spring Boot Test, MockMvc, Spring Security Test, **H2** (modo MySQL) | Testes rodam sem MySQL |
| Build | **Maven** | Gerenciamento de dependências |
| Infra | systemd, Nginx (proxy reverso + TLS), Docker/Docker Compose (opcional) | Implantação em VM |

## 4. Arquitetura

O VIGENTRA é oferecido como SaaS em três camadas:

```
Navegador ──HTTPS──► Nginx (proxy reverso, TLS) ──► Aplicação Spring Boot (JAR, Tomcat embutido) ──► MySQL
 (aplicação web: Thymeleaf)                         (servidor de aplicação: regras, sessões, rotinas)    (dados)
```

| Camada | No projeto |
|---|---|
| **Aplicação web** | Páginas renderizadas no servidor (Thymeleaf + Bootstrap), acessadas pelo navegador |
| **Servidor de aplicação** | JAR executável do Spring Boot, gerenciado pelo systemd (ou Docker) atrás do Nginx |
| **Banco de dados** | MySQL: empresas, usuários, documentos, sessões lembradas, auditoria e histórico de envios |

Internamente é um monólito modular em camadas (MVC), adequado a um Micro-SaaS em uma VM:

```
src/main/java/br/com/vigentra
├── config/        Segurança, propriedades tipadas, Clock, locale, dados de referência
├── security/      Usuário autenticado (com empresaId), login/logout, bloqueio de tentativas, sessões
├── domain/        Entidades JPA (model) e enums (perfis, permissões, situações, ações de log)
├── repository/    Spring Data JPA + Specifications (consultas dinâmicas sempre com empresa_id)
├── service/       Regras de negócio (documentos, usuários, permissões, auditoria, arquivos)
│   ├── alerta/    Rotina multicanal (PeriodoAlerta = regra pura, AlertaService = orquestração)
│   ├── comunicacao/  Canais e provedores: email/ (Brevo, SMTP, dev), whatsapp/ (Cloud API, webhook), sms/
│   ├── conta/     Tokens de uso único, confirmação de e-mail, convites
│   └── relatorio/ Consolidação e exportação PDF/XLSX/CSV
├── scheduler/     Agendamentos @Scheduled (diário e retentativas)
└── web/           Controllers, DTOs de formulário (Bean Validation), advices, helper de templates
src/main/resources
├── templates/     fragments (layout, componentes), public, auth, app, email, error
└── static/        css (design system), js (sem scripts inline), img (logo)
```

### Multi-tenant (isolamento por empresa)

* Estratégia **banco compartilhado, coluna discriminadora** (`empresa_id` em todas as tabelas de negócio).
* O `empresaId` vem **sempre da sessão autenticada** (`UsuarioPrincipal`), nunca de parâmetros do navegador.
* Toda consulta de repositório recebe o `empresaId` (`findByIdAndEmpresaId`, `DocumentoSpecs.daEmpresa`).
* Registro de outra empresa responde **404** (não revela que existe).
* Arquivos ficam em pastas separadas por empresa e só são servidos após a mesma checagem.
* Testes automatizados (`MultiTenantTest`) tentam acessar documentos, usuários, anexos, notificações,
  calendário e relatórios de outra empresa por URL direta — e falham, como esperado.

### Segurança (OWASP / LGPD)

* Senhas com **BCrypt** (custo 11); política de senha forte.
* **Sessão controlada pelo servidor** (Spring Security): o navegador guarda apenas um identificador opaco
  (cookie `VGSESSION`, `HttpOnly`, `SameSite=Lax`, `Secure` atrás de HTTPS); usuário e senha nunca circulam
  como variáveis da sessão. Fixação de sessão prevenida, timeout de 30 min, logout invalida a sessão e os cookies.
* O hash da senha é apagado do usuário autenticado logo após o login (`CredentialsContainer`) e não fica na sessão.
* Sessões **encerradas automaticamente** quando o usuário é desativado, tem a senha redefinida, o perfil/permissões
  alterados ou quando a empresa é desativada.
* **"Lembrar acesso"** com tokens no banco, rotacionados a cada uso e **revogados em todos os dispositivos** quando
  a senha é alterada ou redefinida.
* **CSRF** em todos os formulários e chamadas; **XSS**: escape automático do Thymeleaf + Content-Security-Policy sem scripts inline.
* **SQL Injection**: apenas consultas parametrizadas (JPQL/Criteria); curingas de busca escapados.
* **Força bruta**: bloqueio da conta após 5 falhas (15 min) e limite por IP.
* **Recuperação de senha**: token de 256 bits, guardado só como hash SHA-256, validade de 30 min, uso único, resposta que não revela se o e-mail existe.
* **Upload**: extensão, tamanho (10 MB) e assinatura binária (*magic bytes*) validados; nome aleatório; fora da pasta pública; proteção contra *path traversal*.
* **CSV/Excel injection** neutralizado nas exportações.
* Controle de acesso no backend (`@PreAuthorize` + verificação nos serviços), não só escondendo botões.
* Credenciais apenas em variáveis de ambiente; `.env` fora do Git.
* LGPD: consentimento registrado, política de privacidade, exportação dos dados do titular, trilha de auditoria, minimização de dados.

## 5. Estrutura do banco de dados

Definida em [`script.sql`](script.sql) (17 tabelas, InnoDB, utf8mb4). O Hibernate apenas **valida** o mapeamento
(`ddl-auto=validate`). O script também **atualiza bancos de versões anteriores** de forma idempotente
(só adiciona colunas, índices e tabelas que faltarem — nenhum dado é apagado).

```
empresas ─┬─< usuarios >── perfis
          ├─< perfil_permissoes >── perfis, permissoes
          ├─< categorias ─┐
          ├─< responsaveis ┼─< documentos ─┬─< notificacoes >── usuarios
          │      └── usuarios (opcional)   └─< historico_envios
          ├─< logs_atividades
          └── configuracoes (1:1)
empresas ─< convites >── perfis          empresas ─< regras_canais_alerta
usuarios ─< tokens_usuario
persistent_logins (lembrar acesso)       bloqueios_agendador (trava da rotina)
```

| Tabela | Conteúdo |
|---|---|
| `empresas` | Tenants (flag `demo` identifica dados de demonstração) |
| `perfis`, `permissoes`, `perfil_permissoes` | Níveis de acesso e matriz de permissões por empresa |
| `usuarios` | Login, hash BCrypt, e-mail verificado, celular, preferências e consentimentos, bloqueio, aceite dos termos |
| `categorias` | Categorias por empresa (cor e ícone) |
| `responsaveis` | Destinatários dos alertas, vínculo opcional com usuário |
| `documentos` | Documentos (situação calculada, não armazenada), anexo |
| `notificacoes` | Alertas internos por usuário (chave de idempotência) |
| `historico_envios` | Mensagens de todos os canais: canal, tipo, provedor, id externo, status, tentativas, erro, próxima tentativa, `UNIQUE` anti-duplicidade por canal |
| `convites` | Convites de usuários (hash do token, perfil, validade, aceite/cancelamento) |
| `regras_canais_alerta` | Canais usados em cada prazo da régua, por empresa |
| `bloqueios_agendador` | Trava distribuída da rotina de alertas |
| `logs_atividades` | Auditoria |
| `configuracoes` | Régua de alertas, e-mail e preferências por empresa |
| `tokens_usuario` | Tokens de uso único (confirmação/troca de e-mail, recuperação de senha): hash, tipo, expiração, uso |
| `persistent_logins` | Tokens do "lembrar acesso" |

Chaves primárias e estrangeiras, índices compostos por `empresa_id`, restrições `UNIQUE` e `CHECK`
(ex.: vencimento obrigatório salvo "sem vencimento"; vencimento ≥ emissão) e campos `created_at`/`updated_at`.

## 6. Como instalar e executar localmente (Windows)

Pré-requisitos: **JDK 25**, Maven 3.9, MySQL 8+ (o `script.sql` cria o banco `vigentra`).

> **Já tem uma instalação local antiga (banco `docuguard`)?** Ela continua funcionando: as variáveis
> `DOCUGUARD_DB_*` do seu `.env` ainda são aceitas. Só não execute o `script.sql` novo sem antes
> migrar, porque ele cria um banco `vigentra` separado (o seu continuaria sendo o `docuguard`).
> Para migrar, veja "Migração de uma instalação DocuGuard" em [docs/DEPLOY.md](docs/DEPLOY.md).

### Configurar o MySQL

Crie um usuário próprio para a aplicação (não use o `root` no `.env`) e execute o `script.sql`, que cria o
banco `vigentra` e as tabelas (é idempotente: não apaga nada e pode ser repetido). Os **dados de demonstração
são opcionais**: só são criados quando a variável `@vigentra_demo = 1` é definida antes do script (uso local e
apresentações). As instalações em servidor (`instalar-vm.sh`, Docker Compose) não a definem.

```sql
CREATE USER 'vigentra'@'localhost' IDENTIFIED BY '<escolha-uma-senha-forte>';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES ON vigentra.* TO 'vigentra'@'localhost';
```

### Configurar o ambiente e executar

```powershell
cd C:\Projetos\gestor-validade-documentos

# 1. Variáveis de ambiente: copie o modelo e preencha VIGENTRA_DB_PASSWORD e as chaves
#    (o .env fica fora do Git; nunca publique credenciais)
copy .env.example .env
code .env

# 2. Criar as tabelas e (opcional) os dados de demonstração
& "C:\Program Files\MySQL\MySQL Server 9.7\bin\mysql.exe" -u root -p --default-character-set=utf8mb4 -e "SET @vigentra_demo = 1; source script.sql"
#    sem demonstração: -e "source script.sql"

# 3. Iniciar
mvn spring-boot:run
#   ou: mvn package  e  java -jar target\vigentra.jar
```

Acesse **http://localhost:8080**.

**Contas de demonstração** (dados fictícios criados pelo `script.sql`):

| E-mail | Perfil | Empresa |
|---|---|---|
| `admin@vidaplena.demo` | Administrador | Clínica Vida Plena (DEMO) |
| `gestor@vidaplena.demo` | Gestor | Clínica Vida Plena (DEMO) |
| `usuario@vidaplena.demo` | Usuário | Clínica Vida Plena (DEMO) |
| `admin@contabilnorte.demo` | Administrador | Contábil Norte (DEMO) — para demonstrar o isolamento |

A senha dessas contas **não é publicada** (a equipe a mantém num arquivo local fora do Git). Para usá-las,
em modo desenvolvimento (sem provedor de e-mail configurado), use *Esqueci minha senha* na tela de login: o link
de redefinição é gravado em `data/emails-dev/`. Também é possível criar uma empresa nova em `/cadastro`.
Os dados de demonstração servem apenas para ambientes locais e de apresentação, não para um servidor público.

Para ver o painel da plataforma localmente, defina `APP_PLATFORM_ADMINS=admin@vidaplena.demo`.
Para demonstrar o WhatsApp sem credenciais, defina `WHATSAPP_DEV_MODE=true` e cadastre um celular em
*Preferências de notificação* (use apenas o **seu próprio** número).

Os e-mails `.demo` são fictícios: com SMTP real configurado, os envios para eles falharão (e
aparecerão como falha no histórico). Troque os e-mails dos responsáveis por endereços reais para testar a entrega.

Para ver a rotina de alertas sem esperar as 07:00: *Notificações › Alertas enviados › Executar verificação agora*.

## 7. Como configurar o e-mail e o WhatsApp

A infraestrutura é **centralizada**: as empresas clientes não configuram nada disso. Guia completo
(Brevo, SMTP, WhatsApp Cloud API, aprovação dos modelos na Meta, webhook, SMS e custos) em
**[docs/NOTIFICACOES.md](docs/NOTIFICACOES.md)**.

**E-mail pela API da Brevo (recomendado, plano gratuito):**
```
BREVO_API_KEY=xkeysib-...
MAIL_FROM=remetente-verificado@seudominio.com.br
APP_BASE_URL=https://seu-dominio
APP_SECRET_KEY=<openssl rand -hex 32>
```

**WhatsApp (API oficial da Meta):**
```
WHATSAPP_ACCESS_TOKEN=...            WHATSAPP_PHONE_NUMBER_ID=...
WHATSAPP_APP_SECRET=...              WHATSAPP_WEBHOOK_VERIFY_TOKEN=...
```

**E-mail por SMTP (alternativa):**

```
MAIL_PROVIDER=smtp
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=sua-conta@gmail.com
MAIL_PASSWORD=senha-de-app          # Gmail: Conta Google › Segurança › Senhas de app (exige 2FA)
MAIL_FROM=sua-conta@gmail.com
MAIL_STARTTLS=true
APP_BASE_URL=https://seu-dominio     # usado nos links dos e-mails
```

Porta 465 → `MAIL_SSL=true` e `MAIL_STARTTLS=false`. Uma conta Gmail pessoal serve só para testes rápidos.
Depois de configurar, reinicie e use *Configurações › Canais de comunicação › Enviar e-mail de teste*.
Credenciais nunca são exibidas, gravadas no banco ou registradas em log.

## 8. Como implantar na VM Linux

Resumo (detalhes em **[docs/DEPLOY.md](docs/DEPLOY.md)**):

```bash
git clone <URL-DO-REPOSITORIO> vigentra && cd vigentra
sudo bash deploy/scripts/instalar-vm.sh
```

Inclui: Java 25, MySQL (usuário com privilégios mínimos), variáveis de ambiente em arquivo protegido,
compilação, serviço **systemd** com reinício automático, **Nginx** como proxy reverso com **HTTPS/TLS**
(autoassinado ou Let's Encrypt), armazenamento de anexos em `/var/lib/vigentra`, logs com rotação em
`/var/log/vigentra`. Alternativa opcional: `docker compose up -d --build`.

> Os scripts de implantação ainda estão em validação: a execução completa em uma VM limpa faz parte das
> próximas etapas do projeto.

## 9. Testes automatizados

```bash
mvn test
```

215 testes (unitários + integração com H2 em memória), executados com JDK 25. Não é preciso MySQL. Nenhum teste
usa provedores reais: o perfil `test` força o e-mail em modo desenvolvimento, desliga a rotina de alertas e o
WhatsApp, e as integrações HTTP são verificadas contra um servidor simulado (`MockRestServiceServer`).

Em alguns ambientes Windows o Java não consegue criar o socket local usado pelo NIO
("Unable to establish loopback connection"). Nesse caso, indique uma pasta para esses sockets:

```bash
mvn test "-DargLine=-Djdk.net.unixdomain.tmpdir=C:/Projetos/gestor-validade-documentos/target/udstmp"
```

| Classe | Cobre |
|---|---|
| `RegrasDeNegocioTest` | Cálculo de situação, régua de alertas, CNPJ, escape de busca |
| `AutenticacaoTest` | Cadastro de empresa, validações, login, bloqueio por tentativas, usuário inativo, rotas protegidas, CSRF |
| `DocumentoTest` | Cadastro via formulário com anexo, arquivo falso rejeitado, validações, edição, exclusão, filtros e ordenação |
| `AlertaTest` | Disparo, prevenção de duplicidade, renovação, falha + retentativa, preferências |
| `PermissaoTest` | Perfis Usuário/Gestor/Administrador, hierarquia, matriz de permissões |
| `MultiTenantTest` | Tentativas de acesso cruzado entre empresas (serviço e HTTP) |
| `RelatorioTest` | Consolidação e exportação PDF/XLSX/CSV, CSV injection |
| `JornadaContaTest` | Confirmação de e-mail (bloqueio até confirmar, uso único, reenvio), boas-vindas só após confirmar, convites (empresa/perfil, uso único, expiração, cancelamento, hierarquia), recuperação de senha e troca de e-mail |
| `CanaisTest` | Régua multicanal, consentimento, deduplicação por canal, SMS nunca registrado, links HMAC e descadastro One-Click, webhook da Meta (assinatura, status, "SAIR"), painel da plataforma |
| `ProvedoresTest` | Brevo e WhatsApp Cloud API (requisição/resposta/erros), bloqueio de endereços de demonstração, telefones E.164, mascaramento |
| `PaginasTest` | Renderiza todas as páginas (admin, usuário, públicas) — detecta templates/links quebrados |
| `SessaoSegurancaTest` | Revogação do "lembrar acesso" ao trocar/redefinir a senha, sessões encerradas ao desativar a empresa, hash da senha fora da sessão |
| `EstadosVaziosTest`, `UsuarioListaTest`, `PlataformaVaziosTest` | Estados vazios, pesquisas sem resultado e listagens |
| `DokiTest`, `DokiInterpretadorTest` | Assistente virtual Doki (reconhecimento das perguntas e respostas isoladas por empresa) |
| `RequestUtilsTest` | Utilitários de requisição (IP do cliente) |

## 10. Integrantes

* Andressa Ayumi Kitajima
* Henrique Gonçalves Costa
* Maria Eduarda Chaves dos Santos
* Manoela Soares Feitosa
* Vitor Soares Ferreira
