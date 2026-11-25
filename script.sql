-- =====================================================================
--  VIGENTRA - Gestor de Validade de Documentos e Certificados
--  Script de criação do banco de dados (MySQL 8.0+ / 9.x)
--
--  * NÃO apaga dados: usa CREATE ... IF NOT EXISTS, inserções idempotentes e
--    atualização de schema que só ADICIONA colunas/índices que faltarem.
--  * Pode ser executado mais de uma vez com segurança.
--  * Os dados de DEMONSTRAÇÃO (empresas marcadas com demo = 1) são OPCIONAIS:
--    só são inseridos quando a variável de sessão @vigentra_demo = 1 é definida
--    antes do script, e apenas se ainda não existir nenhuma empresa de demonstração.
--    Instalações públicas (instalar-vm.sh, Docker Compose) NÃO definem a variável.
--
--  Execução:
--     mysql -u <usuario> -p < script.sql                                   (sem demonstração)
--     mysql -u <usuario> -p -e "SET @vigentra_demo = 1; source script.sql" (com demonstração, uso local)
--  (o script seleciona o banco "vigentra"; crie o usuário antes - ver README)
-- =====================================================================

CREATE DATABASE IF NOT EXISTS vigentra
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE vigentra;

SET NAMES utf8mb4;
SET time_zone = '-03:00';

-- ---------------------------------------------------------------------
-- EMPRESAS (tenants)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS empresas (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  nome        VARCHAR(150) NOT NULL,
  cnpj        VARCHAR(18)  NULL,
  email       VARCHAR(150) NULL,
  telefone    VARCHAR(20)  NULL,
  endereco    VARCHAR(255) NULL,
  demo        BOOLEAN      NOT NULL DEFAULT FALSE COMMENT 'Empresa de demonstração (dados fictícios)',
  ativo       BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_empresas_cnpj (cnpj),
  KEY idx_empresas_ativo (ativo)
) ENGINE=InnoDB COMMENT='Empresas clientes (multi-tenant)';

-- ---------------------------------------------------------------------
-- PERFIS e PERMISSÕES (tabelas de referência globais)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS perfis (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  codigo     VARCHAR(20)  NOT NULL,
  nome       VARCHAR(50)  NOT NULL,
  descricao  VARCHAR(255) NULL,
  nivel      INT          NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_perfis_codigo (codigo),
  CONSTRAINT ck_perfis_nivel CHECK (nivel BETWEEN 1 AND 3)
) ENGINE=InnoDB COMMENT='Perfis de acesso: ADMIN, GESTOR, USUARIO';

CREATE TABLE IF NOT EXISTS permissoes (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  codigo     VARCHAR(40)  NOT NULL,
  grupo      VARCHAR(50)  NOT NULL,
  descricao  VARCHAR(255) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_permissoes_codigo (codigo)
) ENGINE=InnoDB COMMENT='Permissões granulares';

CREATE TABLE IF NOT EXISTS perfil_permissoes (
  id            BIGINT NOT NULL AUTO_INCREMENT,
  empresa_id    BIGINT NOT NULL,
  perfil_id     BIGINT NOT NULL,
  permissao_id  BIGINT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_perfil_permissao (empresa_id, perfil_id, permissao_id),
  KEY idx_pp_perfil (perfil_id),
  KEY idx_pp_permissao (permissao_id),
  CONSTRAINT fk_pp_empresa   FOREIGN KEY (empresa_id)   REFERENCES empresas (id)   ON DELETE CASCADE,
  CONSTRAINT fk_pp_perfil    FOREIGN KEY (perfil_id)    REFERENCES perfis (id),
  CONSTRAINT fk_pp_permissao FOREIGN KEY (permissao_id) REFERENCES permissoes (id)
) ENGINE=InnoDB COMMENT='Matriz perfil x permissão de cada empresa';

-- ---------------------------------------------------------------------
-- USUÁRIOS
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS usuarios (
  id                       BIGINT       NOT NULL AUTO_INCREMENT,
  empresa_id               BIGINT       NOT NULL,
  perfil_id                BIGINT       NOT NULL,
  nome                     VARCHAR(150) NOT NULL,
  email                    VARCHAR(150) NOT NULL,
  telefone                 VARCHAR(20)  NULL,
  senha_hash               VARCHAR(100) NOT NULL COMMENT 'Hash BCrypt',
  ativo                    BOOLEAN      NOT NULL DEFAULT TRUE,
  celular                  VARCHAR(20)  NULL COMMENT 'E.164 (+5511999990000) para WhatsApp/SMS',
  receber_alertas_email    BOOLEAN      NOT NULL DEFAULT TRUE,
  receber_whatsapp               BOOLEAN      NOT NULL DEFAULT FALSE,
  receber_sms                    BOOLEAN      NOT NULL DEFAULT FALSE,
  whatsapp_consentimento_em      DATETIME(6)  NULL COMMENT 'Consentimento (LGPD/Meta) - data',
  whatsapp_consentimento_origem  VARCHAR(255) NULL COMMENT 'Quem registrou e por qual meio',
  sms_consentimento_em           DATETIME(6)  NULL,
  sms_consentimento_origem       VARCHAR(255) NULL,
  alerta_antecedencia            BOOLEAN      NOT NULL DEFAULT TRUE,
  alerta_vence_hoje              BOOLEAN      NOT NULL DEFAULT TRUE,
  alerta_vencido                 BOOLEAN      NOT NULL DEFAULT TRUE,
  email_verificado_em      DATETIME(6)  NULL COMMENT 'Contas não verificadas não acessam a área logada',
  email_pendente           VARCHAR(150) NULL COMMENT 'Novo e-mail aguardando confirmação',
  notificacoes_app         BOOLEAN      NOT NULL DEFAULT TRUE,
  tentativas_login_falhas  INT          NOT NULL DEFAULT 0,
  bloqueado_ate            DATETIME(6)  NULL,
  ultimo_login             DATETIME(6)  NULL,
  termos_aceitos_em        DATETIME(6)  NULL COMMENT 'Consentimento LGPD',
  created_at               DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at               DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_usuarios_email (email),
  KEY idx_usuarios_empresa (empresa_id, ativo),
  KEY idx_usuarios_perfil (perfil_id),
  KEY idx_usuarios_celular (celular),
  CONSTRAINT fk_usuarios_empresa FOREIGN KEY (empresa_id) REFERENCES empresas (id),
  CONSTRAINT fk_usuarios_perfil  FOREIGN KEY (perfil_id)  REFERENCES perfis (id)
) ENGINE=InnoDB COMMENT='Usuários (sempre vinculados a uma empresa)';

-- ---------------------------------------------------------------------
-- CATEGORIAS DE DOCUMENTOS
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS categorias (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  empresa_id  BIGINT       NOT NULL,
  nome        VARCHAR(80)  NOT NULL,
  descricao   VARCHAR(255) NULL,
  cor         VARCHAR(7)   NOT NULL DEFAULT '#2563EB',
  icone       VARCHAR(40)  NOT NULL DEFAULT 'bi-file-earmark-text',
  ativo       BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_categoria_empresa_nome (empresa_id, nome),
  CONSTRAINT fk_categorias_empresa FOREIGN KEY (empresa_id) REFERENCES empresas (id) ON DELETE CASCADE,
  CONSTRAINT ck_categorias_cor CHECK (cor REGEXP '^#[0-9A-Fa-f]{6}$')
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- RESPONSÁVEIS
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS responsaveis (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  empresa_id  BIGINT       NOT NULL,
  usuario_id  BIGINT       NULL,
  nome        VARCHAR(150) NOT NULL,
  email       VARCHAR(150) NOT NULL,
  telefone    VARCHAR(20)  NULL,
  cargo       VARCHAR(100) NULL,
  celular     VARCHAR(20)  NULL COMMENT 'E.164 para WhatsApp/SMS',
  receber_email                  BOOLEAN      NOT NULL DEFAULT TRUE,
  receber_whatsapp               BOOLEAN      NOT NULL DEFAULT FALSE,
  receber_sms                    BOOLEAN      NOT NULL DEFAULT FALSE,
  whatsapp_consentimento_em      DATETIME(6)  NULL COMMENT 'Consentimento (LGPD/Meta) - data',
  whatsapp_consentimento_origem  VARCHAR(255) NULL COMMENT 'Quem registrou e por qual meio',
  sms_consentimento_em           DATETIME(6)  NULL,
  sms_consentimento_origem       VARCHAR(255) NULL,
  alerta_antecedencia            BOOLEAN      NOT NULL DEFAULT TRUE,
  alerta_vence_hoje              BOOLEAN      NOT NULL DEFAULT TRUE,
  alerta_vencido                 BOOLEAN      NOT NULL DEFAULT TRUE,
  ativo       BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_responsavel_empresa_email (empresa_id, email),
  KEY idx_responsaveis_usuario (usuario_id),
  KEY idx_responsaveis_celular (celular),
  CONSTRAINT fk_responsaveis_empresa FOREIGN KEY (empresa_id) REFERENCES empresas (id) ON DELETE CASCADE,
  CONSTRAINT fk_responsaveis_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id) ON DELETE SET NULL
) ENGINE=InnoDB COMMENT='Responsáveis pelos documentos / destinatários dos alertas';

-- ---------------------------------------------------------------------
-- DOCUMENTOS
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS documentos (
  id                     BIGINT        NOT NULL AUTO_INCREMENT,
  empresa_id             BIGINT        NOT NULL,
  categoria_id           BIGINT        NOT NULL,
  responsavel_id         BIGINT        NULL,
  criado_por_id          BIGINT        NULL,
  nome                   VARCHAR(150)  NOT NULL,
  numero                 VARCHAR(80)   NULL,
  descricao              VARCHAR(2000) NULL,
  empresa_responsavel    VARCHAR(150)  NULL,
  data_emissao           DATE          NULL,
  data_vencimento        DATE          NULL,
  sem_vencimento         BOOLEAN       NOT NULL DEFAULT FALSE,
  observacoes            VARCHAR(2000) NULL,
  arquivo_nome_original  VARCHAR(255)  NULL,
  arquivo_armazenado     VARCHAR(255)  NULL COMMENT 'Nome aleatório (UUID) no disco',
  arquivo_tipo           VARCHAR(100)  NULL,
  arquivo_tamanho        BIGINT        NULL,
  created_at             DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at             DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_documentos_vencimento (empresa_id, sem_vencimento, data_vencimento),
  KEY idx_documentos_categoria (empresa_id, categoria_id),
  KEY idx_documentos_responsavel (empresa_id, responsavel_id),
  KEY idx_documentos_criado (empresa_id, created_at),
  KEY fk_documentos_categoria_idx (categoria_id),
  KEY fk_documentos_responsavel_idx (responsavel_id),
  KEY fk_documentos_criador_idx (criado_por_id),
  CONSTRAINT fk_documentos_empresa     FOREIGN KEY (empresa_id)     REFERENCES empresas (id),
  CONSTRAINT fk_documentos_categoria   FOREIGN KEY (categoria_id)   REFERENCES categorias (id),
  CONSTRAINT fk_documentos_responsavel FOREIGN KEY (responsavel_id) REFERENCES responsaveis (id) ON DELETE SET NULL,
  CONSTRAINT fk_documentos_criador     FOREIGN KEY (criado_por_id)  REFERENCES usuarios (id) ON DELETE SET NULL,
  CONSTRAINT ck_documentos_vencimento  CHECK (sem_vencimento = TRUE OR data_vencimento IS NOT NULL),
  CONSTRAINT ck_documentos_datas       CHECK (data_emissao IS NULL OR data_vencimento IS NULL OR data_vencimento >= data_emissao)
) ENGINE=InnoDB COMMENT='Documentos e certificados controlados (situação é calculada, não armazenada)';

-- ---------------------------------------------------------------------
-- NOTIFICAÇÕES (no sistema - sino)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notificacoes (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  empresa_id    BIGINT       NOT NULL,
  usuario_id    BIGINT       NOT NULL,
  documento_id  BIGINT       NULL,
  tipo          VARCHAR(20)  NOT NULL,
  titulo        VARCHAR(150) NOT NULL,
  mensagem      VARCHAR(500) NOT NULL,
  chave         VARCHAR(100) NOT NULL COMMENT 'Idempotência: documento + período + vencimento',
  lida          BOOLEAN      NOT NULL DEFAULT FALSE,
  lida_em       DATETIME(6)  NULL,
  created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_notificacao_usuario_chave (usuario_id, chave),
  KEY idx_notificacoes_usuario (usuario_id, lida, created_at),
  KEY idx_notificacoes_empresa (empresa_id),
  KEY idx_notificacoes_documento (documento_id),
  CONSTRAINT fk_notificacoes_empresa   FOREIGN KEY (empresa_id)   REFERENCES empresas (id) ON DELETE CASCADE,
  CONSTRAINT fk_notificacoes_usuario   FOREIGN KEY (usuario_id)   REFERENCES usuarios (id) ON DELETE CASCADE,
  CONSTRAINT fk_notificacoes_documento FOREIGN KEY (documento_id) REFERENCES documentos (id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- HISTÓRICO DE ENVIOS (e-mails)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS historico_envios (
  id                   BIGINT        NOT NULL AUTO_INCREMENT,
  empresa_id           BIGINT        NOT NULL,
  documento_id         BIGINT        NULL,
  documento_nome       VARCHAR(150)  NOT NULL,
  canal                VARCHAR(20)   NOT NULL DEFAULT 'EMAIL' COMMENT 'EMAIL, WHATSAPP, SMS',
  tipo_mensagem        VARCHAR(30)   NOT NULL DEFAULT 'ALERTA_VENCIMENTO',
  provedor             VARCHAR(30)   NULL COMMENT 'brevo, smtp, whatsapp-cloud, dev...',
  id_externo           VARCHAR(150)  NULL COMMENT 'ID da mensagem no provedor (webhooks)',
  falha_notificada     BOOLEAN       NOT NULL DEFAULT FALSE,
  tipo_alerta          VARCHAR(20)   NOT NULL,
  periodo              VARCHAR(20)   NOT NULL COMMENT 'D30, D15, D7, D0, POS1, POS2..., TESTE',
  data_vencimento_ref  DATE          NULL,
  destinatario         VARCHAR(150)  NOT NULL,
  assunto              VARCHAR(255)  NOT NULL,
  status               VARCHAR(20)   NOT NULL COMMENT 'PENDENTE, ENVIADO, ENTREGUE, LIDO, FALHA, MODO_DEV, IGNORADO',
  tentativas           INT           NOT NULL DEFAULT 0,
  ultimo_erro          VARCHAR(1000) NULL,
  enviado_em           DATETIME(6)   NULL,
  proxima_tentativa    DATETIME(6)   NULL,
  created_at           DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at           DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  -- impede notificação duplicada para o mesmo documento, período, CANAL e destinatário
  UNIQUE KEY uk_envio_canal (documento_id, periodo, data_vencimento_ref, canal, destinatario),
  KEY idx_envios_id_externo (id_externo),
  KEY idx_envios_empresa (empresa_id, created_at),
  KEY idx_envios_retentativa (status, proxima_tentativa),
  CONSTRAINT fk_envios_empresa   FOREIGN KEY (empresa_id)   REFERENCES empresas (id) ON DELETE CASCADE,
  CONSTRAINT fk_envios_documento FOREIGN KEY (documento_id) REFERENCES documentos (id) ON DELETE SET NULL
) ENGINE=InnoDB COMMENT='Histórico de mensagens (todos os canais)';

-- ---------------------------------------------------------------------
-- LOG DE ATIVIDADES (auditoria)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS logs_atividades (
  id             BIGINT        NOT NULL AUTO_INCREMENT,
  empresa_id     BIGINT        NULL,
  usuario_id     BIGINT        NULL,
  usuario_nome   VARCHAR(150)  NULL,
  usuario_email  VARCHAR(150)  NULL,
  acao           VARCHAR(40)   NOT NULL,
  entidade       VARCHAR(50)   NULL,
  entidade_id    BIGINT        NULL,
  descricao      VARCHAR(1000) NOT NULL,
  ip             VARCHAR(45)   NULL,
  sucesso        BOOLEAN       NOT NULL DEFAULT TRUE,
  created_at     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  KEY idx_logs_empresa_data (empresa_id, created_at),
  KEY idx_logs_empresa_acao (empresa_id, acao),
  KEY idx_logs_usuario (usuario_id),
  CONSTRAINT fk_logs_empresa FOREIGN KEY (empresa_id) REFERENCES empresas (id) ON DELETE CASCADE,
  CONSTRAINT fk_logs_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id) ON DELETE SET NULL
) ENGINE=InnoDB COMMENT='Trilha de auditoria';

-- ---------------------------------------------------------------------
-- CONFIGURAÇÕES (uma linha por empresa)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS configuracoes (
  id                          BIGINT       NOT NULL AUTO_INCREMENT,
  empresa_id                  BIGINT       NOT NULL,
  dias_alerta                 VARCHAR(50)  NOT NULL DEFAULT '30,15,7,0',
  alertar_apos_vencimento     BOOLEAN      NOT NULL DEFAULT TRUE,
  intervalo_pos_vencimento    INT          NOT NULL DEFAULT 7,
  max_alertas_pos_vencimento  INT          NOT NULL DEFAULT 3,
  dias_proximo_vencimento     INT          NOT NULL DEFAULT 30,
  notificar_responsavel       BOOLEAN      NOT NULL DEFAULT TRUE,
  notificar_gestores          BOOLEAN      NOT NULL DEFAULT TRUE,
  email_remetente_nome        VARCHAR(100) NULL,
  email_resposta              VARCHAR(150) NULL,
  email_copia                 VARCHAR(500) NULL,
  email_assinatura            VARCHAR(500) NULL,
  itens_por_pagina            INT          NOT NULL DEFAULT 10,
  created_at                  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at                  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_configuracoes_empresa (empresa_id),
  CONSTRAINT fk_configuracoes_empresa FOREIGN KEY (empresa_id) REFERENCES empresas (id) ON DELETE CASCADE,
  CONSTRAINT ck_config_intervalo CHECK (intervalo_pos_vencimento BETWEEN 1 AND 90),
  CONSTRAINT ck_config_proximo   CHECK (dias_proximo_vencimento BETWEEN 1 AND 365)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- TOKENS DE USO ÚNICO (confirmação de e-mail, troca de e-mail, recuperação de senha)
-- Apenas o hash SHA-256 do token é armazenado.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tokens_usuario (
  id              BIGINT      NOT NULL AUTO_INCREMENT,
  usuario_id      BIGINT      NOT NULL,
  tipo            VARCHAR(30) NOT NULL COMMENT 'CONFIRMACAO_EMAIL, ALTERACAO_EMAIL, RECUPERACAO_SENHA',
  token_hash      CHAR(64)    NOT NULL,
  expira_em       DATETIME(6) NOT NULL,
  usado_em        DATETIME(6) NULL,
  ip_solicitacao  VARCHAR(45) NULL,
  created_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_tokens_usuario_hash (token_hash),
  KEY idx_tokens_usuario_tipo (usuario_id, tipo),
  CONSTRAINT fk_tokens_usuario_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- CONVITES de usuários (token de uso único, com validade)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS convites (
  id                 BIGINT       NOT NULL AUTO_INCREMENT,
  empresa_id         BIGINT       NOT NULL,
  perfil_id          BIGINT       NOT NULL,
  convidado_por_id   BIGINT       NULL,
  usuario_id         BIGINT       NULL COMMENT 'Usuário criado no aceite',
  email              VARCHAR(150) NOT NULL,
  nome               VARCHAR(150) NULL,
  token_hash         CHAR(64)     NOT NULL,
  expira_em          DATETIME(6)  NOT NULL,
  aceito_em          DATETIME(6)  NULL,
  cancelado_em       DATETIME(6)  NULL,
  criar_responsavel  BOOLEAN      NOT NULL DEFAULT TRUE,
  created_at         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at         DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_convites_token (token_hash),
  KEY idx_convites_empresa_email (empresa_id, email),
  CONSTRAINT fk_convites_empresa  FOREIGN KEY (empresa_id)       REFERENCES empresas (id) ON DELETE CASCADE,
  CONSTRAINT fk_convites_perfil   FOREIGN KEY (perfil_id)        REFERENCES perfis (id),
  CONSTRAINT fk_convites_convidou FOREIGN KEY (convidado_por_id) REFERENCES usuarios (id) ON DELETE SET NULL,
  CONSTRAINT fk_convites_usuario  FOREIGN KEY (usuario_id)       REFERENCES usuarios (id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- CANAIS POR PRAZO DE ALERTA (por empresa)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS regras_canais_alerta (
  id              BIGINT      NOT NULL AUTO_INCREMENT,
  empresa_id      BIGINT      NOT NULL,
  periodo         VARCHAR(10) NOT NULL COMMENT 'D30, D15, D7, D0 ou POS',
  canal_email     BOOLEAN     NOT NULL DEFAULT TRUE,
  canal_whatsapp  BOOLEAN     NOT NULL DEFAULT FALSE,
  canal_sms       BOOLEAN     NOT NULL DEFAULT FALSE,
  created_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_regra_empresa_periodo (empresa_id, periodo),
  CONSTRAINT fk_regras_empresa FOREIGN KEY (empresa_id) REFERENCES empresas (id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- TRAVA DO AGENDADOR (evita execução simultânea da rotina em várias instâncias)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bloqueios_agendador (
  nome           VARCHAR(64)  NOT NULL,
  bloqueado_ate  DATETIME(6)  NOT NULL,
  bloqueado_em   DATETIME(6)  NOT NULL,
  bloqueado_por  VARCHAR(255) NOT NULL,
  PRIMARY KEY (nome)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- "LEMBRAR ACESSO" (Spring Security PersistentTokenBasedRememberMeServices)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS persistent_logins (
  series     VARCHAR(64)  NOT NULL,
  username   VARCHAR(150) NOT NULL,
  token      VARCHAR(64)  NOT NULL,
  last_used  DATETIME(6)  NOT NULL,
  PRIMARY KEY (series),
  KEY idx_persistent_username (username)
) ENGINE=InnoDB;

-- =====================================================================
-- ATUALIZAÇÃO DE BANCOS EXISTENTES (versão 2 - notificações multicanal)
-- Idempotente: só adiciona o que faltar. NENHUM dado é apagado.
-- (Em instalações novas as tabelas acima já estão completas e nada acontece aqui.)
-- =====================================================================
DROP PROCEDURE IF EXISTS dg_add_coluna;
DROP PROCEDURE IF EXISTS dg_add_indice;
DROP PROCEDURE IF EXISTS dg_migrar_v2;
DELIMITER $$
CREATE PROCEDURE dg_add_coluna(IN p_tabela VARCHAR(64), IN p_coluna VARCHAR(64), IN p_definicao VARCHAR(500))
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = p_tabela AND column_name = p_coluna) THEN
    SET @dg_ddl = CONCAT('ALTER TABLE `', p_tabela, '` ADD COLUMN `', p_coluna, '` ', p_definicao);
    PREPARE dg_st FROM @dg_ddl; EXECUTE dg_st; DEALLOCATE PREPARE dg_st;
  END IF;
END$$

CREATE PROCEDURE dg_add_indice(IN p_tabela VARCHAR(64), IN p_indice VARCHAR(64), IN p_definicao VARCHAR(500))
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = p_tabela AND index_name = p_indice) THEN
    SET @dg_ddl = CONCAT('ALTER TABLE `', p_tabela, '` ADD ', p_definicao);
    PREPARE dg_st FROM @dg_ddl; EXECUTE dg_st; DEALLOCATE PREPARE dg_st;
  END IF;
END$$

CREATE PROCEDURE dg_migrar_v2()
BEGIN
  -- contas que já existiam antes da confirmação de e-mail são consideradas verificadas
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = 'usuarios' AND column_name = 'email_verificado_em') THEN
    ALTER TABLE usuarios ADD COLUMN email_verificado_em DATETIME(6) NULL;
    UPDATE usuarios SET email_verificado_em = created_at;
  END IF;
  CALL dg_add_coluna('usuarios', 'email_pendente', 'VARCHAR(150) NULL');
  CALL dg_add_coluna('usuarios', 'celular', 'VARCHAR(20) NULL');
  CALL dg_add_coluna('responsaveis', 'celular', 'VARCHAR(20) NULL');
  CALL dg_add_coluna('responsaveis', 'receber_email', 'BOOLEAN NOT NULL DEFAULT TRUE');
  CALL dg_add_coluna('usuarios', 'receber_whatsapp', 'BOOLEAN NOT NULL DEFAULT FALSE');
  CALL dg_add_coluna('responsaveis', 'receber_whatsapp', 'BOOLEAN NOT NULL DEFAULT FALSE');
  CALL dg_add_coluna('usuarios', 'receber_sms', 'BOOLEAN NOT NULL DEFAULT FALSE');
  CALL dg_add_coluna('responsaveis', 'receber_sms', 'BOOLEAN NOT NULL DEFAULT FALSE');
  CALL dg_add_coluna('usuarios', 'whatsapp_consentimento_em', 'DATETIME(6) NULL');
  CALL dg_add_coluna('responsaveis', 'whatsapp_consentimento_em', 'DATETIME(6) NULL');
  CALL dg_add_coluna('usuarios', 'whatsapp_consentimento_origem', 'VARCHAR(255) NULL');
  CALL dg_add_coluna('responsaveis', 'whatsapp_consentimento_origem', 'VARCHAR(255) NULL');
  CALL dg_add_coluna('usuarios', 'sms_consentimento_em', 'DATETIME(6) NULL');
  CALL dg_add_coluna('responsaveis', 'sms_consentimento_em', 'DATETIME(6) NULL');
  CALL dg_add_coluna('usuarios', 'sms_consentimento_origem', 'VARCHAR(255) NULL');
  CALL dg_add_coluna('responsaveis', 'sms_consentimento_origem', 'VARCHAR(255) NULL');
  CALL dg_add_coluna('usuarios', 'alerta_antecedencia', 'BOOLEAN NOT NULL DEFAULT TRUE');
  CALL dg_add_coluna('responsaveis', 'alerta_antecedencia', 'BOOLEAN NOT NULL DEFAULT TRUE');
  CALL dg_add_coluna('usuarios', 'alerta_vence_hoje', 'BOOLEAN NOT NULL DEFAULT TRUE');
  CALL dg_add_coluna('responsaveis', 'alerta_vence_hoje', 'BOOLEAN NOT NULL DEFAULT TRUE');
  CALL dg_add_coluna('usuarios', 'alerta_vencido', 'BOOLEAN NOT NULL DEFAULT TRUE');
  CALL dg_add_coluna('responsaveis', 'alerta_vencido', 'BOOLEAN NOT NULL DEFAULT TRUE');
  CALL dg_add_indice('usuarios', 'idx_usuarios_celular', 'KEY idx_usuarios_celular (celular)');
  CALL dg_add_indice('responsaveis', 'idx_responsaveis_celular', 'KEY idx_responsaveis_celular (celular)');

  CALL dg_add_coluna('historico_envios', 'canal', "VARCHAR(20) NOT NULL DEFAULT 'EMAIL'");
  CALL dg_add_coluna('historico_envios', 'tipo_mensagem', "VARCHAR(30) NOT NULL DEFAULT 'ALERTA_VENCIMENTO'");
  CALL dg_add_coluna('historico_envios', 'provedor', 'VARCHAR(30) NULL');
  CALL dg_add_coluna('historico_envios', 'id_externo', 'VARCHAR(150) NULL');
  CALL dg_add_coluna('historico_envios', 'falha_notificada', 'BOOLEAN NOT NULL DEFAULT FALSE');
  -- mensagens antigas de teste passam a ter o tipo correto
  UPDATE historico_envios SET tipo_mensagem = 'TESTE' WHERE periodo = 'TESTE' AND tipo_mensagem = 'ALERTA_VENCIMENTO';
  CALL dg_add_indice('historico_envios', 'idx_envios_id_externo', 'KEY idx_envios_id_externo (id_externo)');
  -- a chave anti-duplicidade passa a considerar o canal (a nova é criada ANTES de remover a antiga,
  -- pois ela também atende à chave estrangeira de documento_id)
  CALL dg_add_indice('historico_envios', 'uk_envio_canal',
       'UNIQUE KEY uk_envio_canal (documento_id, periodo, data_vencimento_ref, canal, destinatario)');
  IF EXISTS (SELECT 1 FROM information_schema.statistics
             WHERE table_schema = DATABASE() AND table_name = 'historico_envios' AND index_name = 'uk_envio_unico') THEN
    ALTER TABLE historico_envios DROP INDEX uk_envio_unico;
  END IF;
END$$
DELIMITER ;

CALL dg_migrar_v2();
DROP PROCEDURE IF EXISTS dg_migrar_v2;
DROP PROCEDURE IF EXISTS dg_add_coluna;
DROP PROCEDURE IF EXISTS dg_add_indice;

-- =====================================================================
-- DADOS DE REFERÊNCIA (obrigatórios)
-- =====================================================================
INSERT IGNORE INTO perfis (codigo, nome, descricao, nivel) VALUES
 ('ADMIN',   'Administrador', 'Acesso completo ao ambiente da empresa.', 3),
 ('GESTOR',  'Gestor',        'Gerencia documentos, relatórios e usuários conforme permissões.', 2),
 ('USUARIO', 'Usuário',       'Consulta e gerencia os documentos autorizados.', 1);

INSERT IGNORE INTO permissoes (codigo, grupo, descricao) VALUES
 ('DOC_VIEW_ALL',    'Documentos',    'Visualizar todos os documentos da empresa'),
 ('DOC_CREATE',      'Documentos',    'Cadastrar documentos'),
 ('DOC_EDIT',        'Documentos',    'Editar documentos'),
 ('DOC_DELETE',      'Documentos',    'Excluir documentos'),
 ('RESP_MANAGE',     'Responsáveis',  'Gerenciar responsáveis pelos documentos'),
 ('CATEGORY_MANAGE', 'Documentos',    'Gerenciar categorias de documentos'),
 ('REPORT_VIEW',     'Relatórios',    'Consultar relatórios'),
 ('REPORT_EXPORT',   'Relatórios',    'Exportar relatórios (PDF, Excel, CSV)'),
 ('NOTIF_MANAGE',    'Notificações',  'Gerenciar envios (reenviar falhas, executar verificação)'),
 ('USER_MANAGE',     'Administração', 'Gerenciar usuários'),
 ('AUDIT_VIEW',      'Administração', 'Consultar log de atividades'),
 ('SETTINGS_MANAGE', 'Administração', 'Configurar empresa, alertas e e-mail');

-- =====================================================================
-- DADOS DE DEMONSTRAÇÃO (fictícios - identificados por empresas.demo = 1)
-- As contas demo compartilham uma senha (hash BCrypt abaixo); ela não é publicada na documentação.
-- =====================================================================
DROP PROCEDURE IF EXISTS dg_seed_demo;
DELIMITER $$
CREATE PROCEDURE dg_seed_demo()
BEGIN
  DECLARE v_hash VARCHAR(100) DEFAULT '$2a$11$G6QEJRiGvZN3r50s9fAKNefxeYpe5xmZjXLA4zGm889gUTeqadGYu';
  DECLARE e1, e2, u_admin, u_gestor, u_user, u_admin2 BIGINT;
  DECLARE r_admin, r_gestor, r_user, r_helena, r_carlos, r_marcos, r_admin2 BIGINT;
  DECLARE p_admin, p_gestor, p_usuario BIGINT;

  -- opt-in: sem "SET @vigentra_demo = 1" antes do script, nenhuma conta de demonstração é criada
  IF COALESCE(@vigentra_demo, 0) = 1 AND NOT EXISTS (SELECT 1 FROM empresas WHERE demo = TRUE) THEN

    SELECT id INTO p_admin   FROM perfis WHERE codigo = 'ADMIN';
    SELECT id INTO p_gestor  FROM perfis WHERE codigo = 'GESTOR';
    SELECT id INTO p_usuario FROM perfis WHERE codigo = 'USUARIO';

    -- ----------------------- Empresa 1 -----------------------
    INSERT INTO empresas (nome, cnpj, email, telefone, endereco, demo)
      VALUES ('Clínica Vida Plena (DEMO)', '11.222.333/0001-81', 'contato@vidaplena.demo', '(11) 3333-4444',
              'Av. Exemplo, 1000 - São Paulo/SP', TRUE);
    SET e1 = LAST_INSERT_ID();

    INSERT INTO usuarios (empresa_id, perfil_id, nome, email, telefone, senha_hash, termos_aceitos_em, email_verificado_em)
      VALUES (e1, p_admin, 'Ana Ribeiro (Demo)', 'admin@vidaplena.demo', '(11) 99999-0001', v_hash, NOW(6), NOW(6));
    SET u_admin = LAST_INSERT_ID();
    INSERT INTO usuarios (empresa_id, perfil_id, nome, email, telefone, senha_hash, termos_aceitos_em, email_verificado_em)
      VALUES (e1, p_gestor, 'Bruno Martins (Demo)', 'gestor@vidaplena.demo', '(11) 99999-0002', v_hash, NOW(6), NOW(6));
    SET u_gestor = LAST_INSERT_ID();
    INSERT INTO usuarios (empresa_id, perfil_id, nome, email, telefone, senha_hash, termos_aceitos_em, email_verificado_em)
      VALUES (e1, p_usuario, 'Júlia Santos (Demo)', 'usuario@vidaplena.demo', '(11) 99999-0003', v_hash, NOW(6), NOW(6));
    SET u_user = LAST_INSERT_ID();

    -- matriz de permissões padrão
    INSERT INTO perfil_permissoes (empresa_id, perfil_id, permissao_id)
      SELECT e1, p_admin, id FROM permissoes;
    INSERT INTO perfil_permissoes (empresa_id, perfil_id, permissao_id)
      SELECT e1, p_gestor, id FROM permissoes WHERE codigo <> 'SETTINGS_MANAGE';
    INSERT INTO perfil_permissoes (empresa_id, perfil_id, permissao_id)
      SELECT e1, p_usuario, id FROM permissoes WHERE codigo IN ('DOC_CREATE', 'DOC_EDIT');

    INSERT INTO categorias (empresa_id, nome, descricao, cor, icone) VALUES
     (e1, 'Alvarás e Licenças', 'Alvarás de funcionamento, licenças sanitárias e ambientais', '#2563EB', 'bi-building-check'),
     (e1, 'Certidões', 'Certidões negativas de débitos (federal, estadual, municipal, FGTS, trabalhista)', '#7C3AED', 'bi-patch-check'),
     (e1, 'Contratos', 'Contratos com clientes, fornecedores e locação', '#0891B2', 'bi-file-earmark-text'),
     (e1, 'CNH e Habilitações', 'Carteiras de habilitação de motoristas e colaboradores', '#EA580C', 'bi-car-front'),
     (e1, 'Certificados Digitais', 'Certificados e-CNPJ, e-CPF (A1/A3)', '#16A34A', 'bi-shield-lock'),
     (e1, 'Documentos de Funcionários', 'ASO, NRs, treinamentos e documentos pessoais', '#DB2777', 'bi-people'),
     (e1, 'Certificados Profissionais', 'Registros em conselhos, certificações técnicas', '#CA8A04', 'bi-award'),
     (e1, 'Documentos Empresariais', 'Contrato social, inscrições e registros da empresa', '#475569', 'bi-briefcase');

    INSERT INTO responsaveis (empresa_id, usuario_id, nome, email, telefone, cargo) VALUES
      (e1, u_admin, 'Ana Ribeiro (Demo)', 'admin@vidaplena.demo', '(11) 99999-0001', 'Diretora administrativa');
    SET r_admin = LAST_INSERT_ID();
    INSERT INTO responsaveis (empresa_id, usuario_id, nome, email, telefone, cargo) VALUES
      (e1, u_gestor, 'Bruno Martins (Demo)', 'gestor@vidaplena.demo', '(11) 99999-0002', 'Gerente de operações');
    SET r_gestor = LAST_INSERT_ID();
    INSERT INTO responsaveis (empresa_id, usuario_id, nome, email, telefone, cargo) VALUES
      (e1, u_user, 'Júlia Santos (Demo)', 'usuario@vidaplena.demo', '(11) 99999-0003', 'Enfermeira responsável técnica');
    SET r_user = LAST_INSERT_ID();
    INSERT INTO responsaveis (empresa_id, nome, email, cargo) VALUES (e1, 'Dra. Helena Prado (Demo)', 'helena@vidaplena.demo', 'Diretora clínica');
    SET r_helena = LAST_INSERT_ID();
    INSERT INTO responsaveis (empresa_id, nome, email, cargo) VALUES (e1, 'Carlos Menezes (Demo)', 'rh@vidaplena.demo', 'Recursos Humanos');
    SET r_carlos = LAST_INSERT_ID();
    INSERT INTO responsaveis (empresa_id, nome, email, cargo) VALUES (e1, 'Marcos Lima (Demo)', 'marcos@vidaplena.demo', 'Motorista');
    SET r_marcos = LAST_INSERT_ID();

    INSERT INTO configuracoes (empresa_id, email_remetente_nome, email_assinatura)
      VALUES (e1, 'VIGENTRA - Clínica Vida Plena', 'Administração - Clínica Vida Plena (dados de demonstração)');

    -- Documentos (datas relativas a hoje, para que a demonstração sempre tenha situações variadas)
    INSERT INTO documentos (empresa_id, categoria_id, responsavel_id, criado_por_id, nome, numero, descricao, empresa_responsavel,
                            data_emissao, data_vencimento, sem_vencimento, observacoes)
    SELECT e1, c.id, x.resp, u_admin, x.nome, x.numero, x.descricao, x.emp_resp,
           IF(x.emissao IS NULL, NULL, DATE_ADD(CURDATE(), INTERVAL x.emissao DAY)),
           IF(x.venc IS NULL, NULL, DATE_ADD(CURDATE(), INTERVAL x.venc DAY)),
           x.venc IS NULL, x.obs
    FROM (
      SELECT 'Alvarás e Licenças' cat, r_admin resp, 'Alvará de Funcionamento' nome, 'ALV-2025/004812' numero,
             'Alvará de localização e funcionamento emitido pela Prefeitura.' descricao, 'Matriz' emp_resp, -353 emissao, 12 venc,
             'Renovação pelo portal da Prefeitura. Exige AVCB válido.' obs
      UNION ALL SELECT 'Alvarás e Licenças', r_helena, 'Licença Sanitária (Vigilância Sanitária)', 'LS-778120', 'Licença de funcionamento sanitária.', 'Matriz', -320, 45, NULL
      UNION ALL SELECT 'Alvarás e Licenças', r_gestor, 'AVCB - Corpo de Bombeiros', 'AVCB-552301', 'Auto de Vistoria do Corpo de Bombeiros.', 'Matriz', -1100, -5, 'Agendar vistoria com empresa credenciada.'
      UNION ALL SELECT 'Certidões', r_admin, 'CND Federal (Receita Federal/PGFN)', 'CND-F-0098', 'Certidão Negativa de Débitos relativos a tributos federais.', NULL, -155, 25, NULL
      UNION ALL SELECT 'Certidões', r_admin, 'CND Estadual (SEFAZ)', 'CND-E-1140', NULL, NULL, -100, 80, NULL
      UNION ALL SELECT 'Certidões', r_gestor, 'CND Municipal (Tributos Mobiliários)', 'CND-M-3320', NULL, NULL, -198, -18, 'Necessária para renovação do contrato com o convênio.'
      UNION ALL SELECT 'Certidões', r_admin, 'Certificado de Regularidade do FGTS (CRF)', 'CRF-55120', NULL, NULL, -24, 6, NULL
      UNION ALL SELECT 'Certidões', r_carlos, 'Certidão Negativa de Débitos Trabalhistas (CNDT)', 'CNDT-77801', NULL, NULL, -30, 150, NULL
      UNION ALL SELECT 'Certificados Digitais', r_admin, 'Certificado Digital e-CNPJ A1', 'SERIAL 4F:22:9A', 'Usado para emissão de NFS-e e acesso ao e-CAC.', 'Matriz', -362, 3, 'Renovar com a Autoridade Certificadora antes do vencimento.'
      UNION ALL SELECT 'Contratos', r_gestor, 'Contrato de Locação do Imóvel', 'LOC-2024-01', 'Locação do imóvel da sede.', 'Imobiliária Exemplo Ltda.', -520, 210, NULL
      UNION ALL SELECT 'Contratos', r_gestor, 'Contrato de Coleta de Resíduos de Saúde', 'CT-RSS-019', 'Coleta e destinação de resíduos (RDC 222).', 'Ambiental Coleta Ltda.', -337, 28, NULL
      UNION ALL SELECT 'CNH e Habilitações', r_marcos, 'CNH - Marcos Lima', '0456789123-4', 'Motorista da unidade móvel.', NULL, -1500, 330, NULL
      UNION ALL SELECT 'Documentos de Funcionários', r_user, 'ASO Periódico - Júlia Santos', 'ASO-2025-118', 'Atestado de Saúde Ocupacional.', NULL, -351, 14, NULL
      UNION ALL SELECT 'Documentos de Funcionários', r_carlos, 'Treinamento NR-32 - Equipe de Enfermagem', 'NR32-T07', 'Segurança e saúde no trabalho em serviços de saúde.', NULL, -405, -40, 'Reciclagem anual obrigatória.'
      UNION ALL SELECT 'Certificados Profissionais', r_helena, 'Registro CRM - Dra. Helena Prado', 'CRM-SP 000000', NULL, NULL, -1800, 400, NULL
      UNION ALL SELECT 'Certificados Profissionais', r_user, 'COREN - Júlia Santos', 'COREN-SP 000000', NULL, NULL, -700, 95, NULL
      UNION ALL SELECT 'Contratos', r_admin, 'Seguro de Responsabilidade Civil Profissional', 'APL-8890021', 'Apólice anual.', 'Seguradora Exemplo S.A.', -365, 0, NULL
      UNION ALL SELECT 'Documentos Empresariais', r_admin, 'Contrato Social Consolidado', '35.200.000.000', 'Última alteração contratual registrada na Junta Comercial.', 'Matriz', -900, NULL, NULL
      UNION ALL SELECT 'Documentos Empresariais', r_admin, 'Registro no CNES', 'CNES 0000000', 'Cadastro Nacional de Estabelecimentos de Saúde.', 'Matriz', -1200, NULL, NULL
    ) x
    JOIN categorias c ON c.empresa_id = e1 AND c.nome = x.cat;

    -- ----------------------- Empresa 2 (para demonstrar o isolamento multi-tenant) -----------------------
    INSERT INTO empresas (nome, cnpj, email, telefone, endereco, demo)
      VALUES ('Contábil Norte Assessoria (DEMO)', '44.555.666/0001-20', 'contato@contabilnorte.demo', '(92) 3222-1100',
              'Rua Exemplo, 200 - Manaus/AM', TRUE);
    SET e2 = LAST_INSERT_ID();
    INSERT INTO usuarios (empresa_id, perfil_id, nome, email, senha_hash, termos_aceitos_em, email_verificado_em)
      VALUES (e2, p_admin, 'Rafael Costa (Demo)', 'admin@contabilnorte.demo', v_hash, NOW(6), NOW(6));
    SET u_admin2 = LAST_INSERT_ID();

    INSERT INTO perfil_permissoes (empresa_id, perfil_id, permissao_id) SELECT e2, p_admin, id FROM permissoes;
    INSERT INTO perfil_permissoes (empresa_id, perfil_id, permissao_id) SELECT e2, p_gestor, id FROM permissoes WHERE codigo <> 'SETTINGS_MANAGE';
    INSERT INTO perfil_permissoes (empresa_id, perfil_id, permissao_id) SELECT e2, p_usuario, id FROM permissoes WHERE codigo IN ('DOC_CREATE', 'DOC_EDIT');

    INSERT INTO categorias (empresa_id, nome, descricao, cor, icone) VALUES
     (e2, 'Certidões', 'Certidões negativas', '#7C3AED', 'bi-patch-check'),
     (e2, 'Certificados Digitais', 'Certificados A1/A3', '#16A34A', 'bi-shield-lock'),
     (e2, 'Contratos', 'Contratos de clientes', '#0891B2', 'bi-file-earmark-text');

    INSERT INTO responsaveis (empresa_id, usuario_id, nome, email, cargo)
      VALUES (e2, u_admin2, 'Rafael Costa (Demo)', 'admin@contabilnorte.demo', 'Sócio');
    SET r_admin2 = LAST_INSERT_ID();

    INSERT INTO configuracoes (empresa_id, email_remetente_nome) VALUES (e2, 'VIGENTRA - Contábil Norte');

    INSERT INTO documentos (empresa_id, categoria_id, responsavel_id, criado_por_id, nome, numero, data_emissao, data_vencimento, sem_vencimento)
    SELECT e2, c.id, r_admin2, u_admin2, x.nome, x.numero, DATE_ADD(CURDATE(), INTERVAL x.emissao DAY), DATE_ADD(CURDATE(), INTERVAL x.venc DAY), FALSE
    FROM (
      SELECT 'Certificados Digitais' cat, 'Certificado e-CNPJ A3 - Contábil Norte' nome, 'A3-99812' numero, -700 emissao, 20 venc
      UNION ALL SELECT 'Certidões', 'CND Federal - Contábil Norte', 'CND-F-7781', -100, 80
      UNION ALL SELECT 'Contratos', 'Contrato de prestação - Cliente Alfa', 'CPS-0012', -300, -2
      UNION ALL SELECT 'Certidões', 'CRF FGTS - Contábil Norte', 'CRF-8812', -20, 10
    ) x
    JOIN categorias c ON c.empresa_id = e2 AND c.nome = x.cat;

    INSERT INTO logs_atividades (empresa_id, usuario_id, usuario_nome, usuario_email, acao, entidade, entidade_id, descricao, sucesso)
      VALUES (e1, NULL, 'Sistema (script.sql)', NULL, 'EMPRESA_CADASTRADA', 'Empresa', e1, 'Dados de demonstração criados pelo script.sql', TRUE),
             (e2, NULL, 'Sistema (script.sql)', NULL, 'EMPRESA_CADASTRADA', 'Empresa', e2, 'Dados de demonstração criados pelo script.sql', TRUE);
  END IF;
END$$
DELIMITER ;

CALL dg_seed_demo();
DROP PROCEDURE IF EXISTS dg_seed_demo;

-- Conferência rápida
SELECT e.nome AS empresa, e.demo, COUNT(d.id) AS documentos
FROM empresas e LEFT JOIN documentos d ON d.empresa_id = e.id
GROUP BY e.id, e.nome, e.demo;
