#!/usr/bin/env bash
# =====================================================================
# VIGENTRA - instalação em VM Linux (Ubuntu 22.04/24.04 ou Debian 12)
# SEM Docker: Java 25 + MySQL + systemd + Nginx (HTTPS autoassinado).
#
# Uso (dentro da pasta do projeto clonado):
#   sudo bash deploy/scripts/instalar-vm.sh
#
# O script é idempotente: pode ser executado novamente para atualizar a versão.
# Ele NUNCA apaga o banco de dados.
# =====================================================================
set -euo pipefail

[[ $EUID -eq 0 ]] || { echo "Execute com sudo."; exit 1; }
RAIZ="$(cd "$(dirname "$0")/../.." && pwd)"
ENV_FILE=/etc/vigentra/vigentra.env

# Instalação antiga (nome DocuGuard) nesta VM? Ela precisa ser MIGRADA, não reinstalada:
# senão seria criado um banco novo e vazio, e os dados existentes ficariam de fora.
if [[ -f /etc/docuguard/docuguard.env && ! -f $ENV_FILE ]]; then
  echo "Encontrada uma instalação anterior (/etc/docuguard)."
  echo "Execute primeiro: sudo bash deploy/scripts/migrar-docuguard-para-vigentra.sh"
  exit 1
fi

echo "==> 1/7 Instalando dependências (Java 25, Maven, MySQL, Nginx, OpenSSL)"
apt-get update -y
apt-get install -y openjdk-25-jdk-headless maven mysql-server nginx openssl
systemctl enable --now mysql

echo "==> 2/7 Usuário de sistema e diretórios"
id vigentra &>/dev/null || useradd --system --home /opt/vigentra --shell /usr/sbin/nologin vigentra
install -d -o vigentra -g vigentra -m 750 /opt/vigentra /var/lib/vigentra /var/lib/vigentra/uploads \
    /var/lib/vigentra/emails-dev /var/lib/vigentra/whatsapp-dev /var/log/vigentra
install -d -m 750 /etc/vigentra

echo "==> 3/7 Variáveis de ambiente ($ENV_FILE)"
if [[ ! -f $ENV_FILE ]]; then
  DB_PASS="$(openssl rand -base64 24 | tr -d '/+=')"
  sed -e "s|^VIGENTRA_DB_PASSWORD=.*|VIGENTRA_DB_PASSWORD=${DB_PASS}|" \
      -e "s|^APP_REMEMBER_ME_KEY=.*|APP_REMEMBER_ME_KEY=$(openssl rand -hex 32)|" \
      -e "s|^APP_SECRET_KEY=.*|APP_SECRET_KEY=$(openssl rand -hex 32)|" \
      -e "s|^WHATSAPP_DEV_DIR=.*|WHATSAPP_DEV_DIR=/var/lib/vigentra/whatsapp-dev|" \
      -e "s|^APP_STORAGE_DIR=.*|APP_STORAGE_DIR=/var/lib/vigentra/uploads|" \
      -e "s|^MAIL_DEV_DIR=.*|MAIL_DEV_DIR=/var/lib/vigentra/emails-dev|" \
      -e "s|^VIGENTRA_LOG_FILE=.*|VIGENTRA_LOG_FILE=/var/log/vigentra/vigentra.log|" \
      -e "s|^SESSION_COOKIE_SECURE=.*|SESSION_COOKIE_SECURE=true|" \
      -e "s|^APP_BASE_URL=.*|APP_BASE_URL=https://$(hostname -I | awk '{print $1}')|" \
      "$RAIZ/.env.example" > "$ENV_FILE"
  echo "    Arquivo criado com senha aleatória para o banco."
else
  echo "    Arquivo já existe - mantido."
fi
chown root:vigentra "$ENV_FILE"; chmod 640 "$ENV_FILE"
# lê apenas o necessário (o arquivo não é um script shell: contém '&' e '*')
ler() { grep -E "^$1=" "$ENV_FILE" | head -1 | cut -d= -f2-; }
VIGENTRA_DB_USER="$(ler VIGENTRA_DB_USER)"
VIGENTRA_DB_PASSWORD="$(ler VIGENTRA_DB_PASSWORD)"

echo "==> 4/7 Banco de dados MySQL"
mysql <<SQL
CREATE DATABASE IF NOT EXISTS vigentra DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS '${VIGENTRA_DB_USER}'@'localhost' IDENTIFIED BY '${VIGENTRA_DB_PASSWORD}';
ALTER USER '${VIGENTRA_DB_USER}'@'localhost' IDENTIFIED BY '${VIGENTRA_DB_PASSWORD}';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES, CREATE ROUTINE, ALTER ROUTINE, EXECUTE
      ON vigentra.* TO '${VIGENTRA_DB_USER}'@'localhost';
FLUSH PRIVILEGES;
SQL
mysql --default-character-set=utf8mb4 < "$RAIZ/script.sql"

echo "==> 5/7 Compilando a aplicação"
cd "$RAIZ"
mvn -B -q package -DskipTests
install -o vigentra -g vigentra -m 640 target/vigentra.jar /opt/vigentra/vigentra.jar

echo "==> 6/7 Serviço systemd"
install -m 644 deploy/systemd/vigentra.service /etc/systemd/system/vigentra.service
systemctl daemon-reload
systemctl enable vigentra
systemctl restart vigentra

echo "==> 7/7 Nginx + certificado TLS"
if [[ ! -f /etc/ssl/vigentra/vigentra.crt ]]; then
  install -d -m 755 /etc/ssl/vigentra
  openssl req -x509 -nodes -newkey rsa:2048 -days 365 \
    -keyout /etc/ssl/vigentra/vigentra.key -out /etc/ssl/vigentra/vigentra.crt \
    -subj "/CN=$(hostname -I | awk '{print $1}')/O=VIGENTRA"
  chmod 600 /etc/ssl/vigentra/vigentra.key
fi
sed "s/vigentra.exemplo.com.br/_/g" deploy/nginx/vigentra.conf > /etc/nginx/sites-available/vigentra
ln -sf /etc/nginx/sites-available/vigentra /etc/nginx/sites-enabled/vigentra
rm -f /etc/nginx/sites-enabled/default
nginx -t && systemctl reload nginx

echo
echo "Aguardando a aplicação iniciar..."
for _ in $(seq 1 60); do curl -fs http://127.0.0.1:8080/actuator/health | grep -q UP && break; sleep 2; done
systemctl --no-pager status vigentra | head -5
echo
echo "Pronto! Acesse: https://$(hostname -I | awk '{print $1}')  (certificado autoassinado: aceite o aviso do navegador)"
echo "Configuração: $ENV_FILE  |  Logs: journalctl -u vigentra -f"
