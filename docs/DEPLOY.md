# Implantação do VIGENTRA em VM Linux

Guia para Ubuntu 22.04/24.04 ou Debian 12. Há três caminhos — escolha um:

| Caminho | Quando usar |
|---|---|
| **A. Script automático** (`deploy/scripts/instalar-vm.sh`) | Mais rápido; instala tudo sem Docker |
| **B. Passo a passo manual** | Para entender cada etapa (recomendado para a apresentação) |
| **C. Docker Compose** | Se a VM já tiver Docker |

Arquitetura na VM:

```
Navegador ──HTTPS 443──► Nginx (TLS, proxy reverso) ──HTTP 127.0.0.1:8080──► VIGENTRA (Java/systemd)  ──► MySQL (localhost:3306)
                                                                              │
                                                       /var/lib/vigentra/uploads  (anexos, fora da web)
                                                       /var/log/vigentra          (logs com rotação)
```

---

## A. Script automático

```bash
sudo apt-get update && sudo apt-get install -y git
git clone <URL-DO-REPOSITORIO> vigentra && cd vigentra
sudo bash deploy/scripts/instalar-vm.sh
```

O script instala Java 25, Maven, MySQL e Nginx; cria o usuário de sistema `vigentra`;
gera `/etc/vigentra/vigentra.env` com **senha aleatória** para o banco; cria o usuário MySQL com
privilégios mínimos; executa o `script.sql`; compila; registra o serviço systemd e configura o Nginx
com certificado TLS autoassinado. Ao final, acesse `https://IP-DA-VM`.

Para **atualizar** a aplicação depois: `git pull && sudo bash deploy/scripts/instalar-vm.sh`
(o banco e o arquivo de ambiente são preservados).

> VM instalada **antes da troca de nome** (existe `/etc/docuguard`)? Não reinstale: siga
> [Migração de uma instalação DocuGuard](#migração-de-uma-instalação-docuguard). O `instalar-vm.sh`
> detecta esse caso e para, para não criar um banco novo e vazio.

---

## B. Passo a passo manual

### 1. Dependências
```bash
sudo apt-get update
sudo apt-get install -y openjdk-25-jdk-headless maven mysql-server nginx openssl git
java -version   # deve mostrar 25
```

### 2. Usuário de sistema e diretórios
```bash
sudo useradd --system --home /opt/vigentra --shell /usr/sbin/nologin vigentra
sudo install -d -o vigentra -g vigentra -m 750 /opt/vigentra /var/lib/vigentra/uploads \
     /var/lib/vigentra/emails-dev /var/log/vigentra
```
> A aplicação roda com um usuário **sem privilégios** e só consegue escrever nas pastas de dados e logs.

### 3. Banco de dados
```bash
sudo mysql
```
```sql
CREATE DATABASE IF NOT EXISTS vigentra DEFAULT CHARACTER SET utf8mb4;
CREATE USER 'vigentra'@'localhost' IDENTIFIED BY 'UMA-SENHA-FORTE';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES,
      CREATE ROUTINE, ALTER ROUTINE, EXECUTE ON vigentra.* TO 'vigentra'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```
```bash
sudo mysql --default-character-set=utf8mb4 < script.sql
```

> Executado assim, o script **não cria contas de demonstração** (elas são opcionais e exigem
> `SET @vigentra_demo = 1` antes do script). Não habilite os dados de demonstração num servidor público.

### 4. Variáveis de ambiente
```bash
sudo install -d -m 750 /etc/vigentra
sudo cp .env.example /etc/vigentra/vigentra.env
sudo nano /etc/vigentra/vigentra.env
```
Ajuste no mínimo:
```
VIGENTRA_DB_USER=vigentra
VIGENTRA_DB_PASSWORD=UMA-SENHA-FORTE
APP_BASE_URL=https://IP-OU-DOMINIO
APP_REMEMBER_ME_KEY=<saída de: openssl rand -hex 32>
APP_STORAGE_DIR=/var/lib/vigentra/uploads
MAIL_DEV_DIR=/var/lib/vigentra/emails-dev
VIGENTRA_LOG_FILE=/var/log/vigentra/vigentra.log
SESSION_COOKIE_SECURE=true
```
```bash
sudo chown root:vigentra /etc/vigentra/vigentra.env
sudo chmod 640 /etc/vigentra/vigentra.env     # só root e o serviço leem as credenciais
```

### 5. Compilação
```bash
mvn -B package            # compila e executa os testes automatizados
sudo install -o vigentra -g vigentra -m 640 target/vigentra.jar /opt/vigentra/vigentra.jar
```

### 6. Serviço systemd (inicialização e reinício automáticos)
```bash
sudo cp deploy/systemd/vigentra.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now vigentra
systemctl status vigentra
journalctl -u vigentra -f          # acompanha os logs
curl http://127.0.0.1:8080/actuator/health   # {"status":"UP"}
```
`Restart=on-failure` reinicia o serviço automaticamente se ele cair; `enable` faz iniciar junto com a VM.

### 7. Nginx + HTTPS
**Certificado autoassinado** (VM de laboratório, sem domínio):
```bash
sudo install -d /etc/ssl/vigentra
sudo openssl req -x509 -nodes -newkey rsa:2048 -days 365 \
  -keyout /etc/ssl/vigentra/vigentra.key -out /etc/ssl/vigentra/vigentra.crt -subj "/CN=$(hostname -I | awk '{print $1}')"
```
**Let's Encrypt** (com domínio público apontando para a VM):
```bash
sudo apt-get install -y certbot python3-certbot-nginx
sudo certbot --nginx -d vigentra.seudominio.com.br
```
Ativar o site:
```bash
sudo cp deploy/nginx/vigentra.conf /etc/nginx/sites-available/vigentra
sudo nano /etc/nginx/sites-available/vigentra      # ajuste server_name e caminhos do certificado
sudo ln -s /etc/nginx/sites-available/vigentra /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t && sudo systemctl reload nginx
```

### 8. Firewall
```bash
sudo ufw allow OpenSSH
sudo ufw allow 'Nginx Full'     # portas 80 e 443
sudo ufw enable
```
A porta 8080 **não** precisa ficar aberta: só o Nginx acessa a aplicação, via `127.0.0.1`.

---

## C. Docker Compose (opcional)
```bash
cp .env.example .env && nano .env     # defina VIGENTRA_DB_PASSWORD e MYSQL_ROOT_PASSWORD
docker compose up -d --build
docker compose logs -f app
```
O MySQL executa o `script.sql` na primeira inicialização. Os dados ficam nos volumes `db-data` e `app-data`.
Para HTTPS, use o Nginx da etapa 7 apontando para `127.0.0.1:8080`.

---

## Migração de uma instalação DocuGuard

O produto se chamava **DocuGuard**. Instalações feitas antes da troca de nome usam o banco
`docuguard` e caminhos como `/etc/docuguard`. A aplicação nova usa o banco `vigentra`, e o MySQL
não renomeia bancos sozinho: por isso a migração **copia** os dados para o banco novo e mantém o
antigo intacto como cópia de segurança.

### VM sem Docker (caminhos A ou B)
```bash
cd <pasta-do-projeto> && git pull
sudo bash deploy/scripts/migrar-docuguard-para-vigentra.sh
```
O script para o serviço antigo, faz backup (`/var/backups/vigentra/docuguard-<data>.sql`), copia o
banco para `vigentra` e **confere a quantidade de linhas de cada tabela**. Depois copia anexos,
certificado TLS e o arquivo de ambiente (variáveis `DOCUGUARD_*` viram `VIGENTRA_*`, valores e senhas
preservados) e executa o `instalar-vm.sh`. Se algo divergir, ele para antes de mexer no ambiente
antigo. As instruções para voltar à versão anterior estão no início do script.

Depois de alguns dias com tudo funcionando, o banco `docuguard` e as pastas `/etc/docuguard`,
`/var/lib/docuguard` e `/opt/docuguard` podem ser removidos **manualmente**.

### Docker Compose (caminho C)
O volume `db-data` antigo contém o banco `docuguard`. Faça na ordem abaixo:
```bash
# 1. AINDA com a versão antiga: backup do banco e parada dos contêineres (sem "-v": os volumes ficam)
docker compose exec db sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers docuguard' > backup-docuguard.sql
docker compose down

# 2. Atualize o código e renomeie as variáveis no .env
git pull
sed -i 's/^DOCUGUARD_/VIGENTRA_/' .env
#    Mantenha VIGENTRA_DB_USER com o usuário que já existe no volume (ex.: docuguard):
#    o MySQL só cria o usuário de MYSQL_USER na primeira inicialização do volume.

# 3. Suba só o banco, crie o "vigentra" e importe o backup
docker compose up -d db
docker compose exec db sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "CREATE DATABASE vigentra DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"'
docker compose exec -T db sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" vigentra' < backup-docuguard.sql
docker compose exec db sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "GRANT ALL ON vigentra.* TO \"$MYSQL_USER\"@\"%\""'

# 4. Suba a aplicação
docker compose up -d --build app
```

---

## Operação

| Tarefa | Comando |
|---|---|
| Ver logs | `journalctl -u vigentra -f` ou `/var/log/vigentra/vigentra.log` (rotação de 10 MB, 30 arquivos) |
| Reiniciar | `sudo systemctl restart vigentra` |
| Backup do banco | `sudo mysqldump --single-transaction vigentra > backup-$(date +%F).sql` |
| Backup dos anexos | `sudo tar czf anexos-$(date +%F).tgz /var/lib/vigentra/uploads` |
| Ativar e-mail real | preencher `BREVO_API_KEY` (ou `MAIL_*`) e `WHATSAPP_*` em `/etc/vigentra/vigentra.env` e reiniciar (ver docs/NOTIFICACOES.md) |
| Testar e-mail | Configurações › Configuração de e-mail › Enviar e-mail de teste |

**Rotina de alertas:** executa diariamente às 07:00 (`ALERTS_CRON`) e as retentativas a cada hora
(`ALERTS_RETRY_CRON`). Como a aplicação roda numa única instância, não há execução concorrente;
se no futuro houver várias instâncias, a restrição `UNIQUE` em `historico_envios` continua impedindo
e-mails duplicados.
