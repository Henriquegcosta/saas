#!/usr/bin/env bash
# =====================================================================
# VIGENTRA - migração de uma VM instalada com o nome antigo (DocuGuard)
#
# Use SOMENTE se a VM já tiver uma instalação antiga (/etc/docuguard).
# Para instalações novas, use apenas deploy/scripts/instalar-vm.sh.
#
# Uso (dentro da pasta do projeto, já atualizada para a versão VIGENTRA):
#   sudo bash deploy/scripts/migrar-docuguard-para-vigentra.sh
#
# O que faz (NADA do ambiente antigo é apagado - ele fica como cópia de segurança):
#   1. para o serviço antigo "docuguard" e faz backup (mysqldump) do banco "docuguard";
#   2. COPIA o banco "docuguard" para um novo banco "vigentra" e confere as linhas de cada tabela;
#   3. COPIA anexos e configuração para /var/lib/vigentra e /etc/vigentra
#      (variáveis DOCUGUARD_* renomeadas para VIGENTRA_*, valores preservados);
#   4. cria o usuário de sistema "vigentra" e executa instalar-vm.sh (serviço, Nginx, JAR).
#
# Reversão (voltar para a instalação antiga):
#   sudo systemctl disable --now vigentra
#   sudo rm -f /etc/nginx/sites-enabled/vigentra
#   sudo ln -sf /etc/nginx/sites-available/docuguard /etc/nginx/sites-enabled/docuguard
#   sudo nginx -t && sudo systemctl reload nginx
#   sudo systemctl enable --now docuguard
# Depois de validar a nova versão por alguns dias, o banco "docuguard" e as pastas
# /etc/docuguard, /var/lib/docuguard e /opt/docuguard podem ser removidos manualmente.
# =====================================================================
set -euo pipefail

[[ $EUID -eq 0 ]] || { echo "Execute com sudo."; exit 1; }
RAIZ="$(cd "$(dirname "$0")/../.." && pwd)"
ANTIGO_ENV=/etc/docuguard/docuguard.env
NOVO_ENV=/etc/vigentra/vigentra.env
BACKUP_DIR=/var/backups/vigentra
CARIMBO="$(date +%Y%m%d-%H%M%S)"

[[ -f $ANTIGO_ENV ]] || { echo "Instalação antiga não encontrada ($ANTIGO_ENV). Nada a migrar."; exit 1; }
[[ ! -f $NOVO_ENV ]] || { echo "$NOVO_ENV já existe: a migração parece já ter sido feita."; exit 1; }

echo "==> 1/5 Parando o serviço antigo e fazendo backup do banco"
systemctl stop docuguard 2>/dev/null || true
install -d -m 700 "$BACKUP_DIR"
mysqldump --single-transaction --routines --triggers --set-gtid-purged=OFF \
          --default-character-set=utf8mb4 docuguard > "$BACKUP_DIR/docuguard-$CARIMBO.sql"
echo "    Backup: $BACKUP_DIR/docuguard-$CARIMBO.sql"

echo "==> 2/5 Copiando o banco docuguard -> vigentra"
if mysql -N -e "SHOW DATABASES LIKE 'vigentra'" | grep -q vigentra; then
  echo "    O banco 'vigentra' já existe. Interrompido para não sobrescrever dados."; exit 1
fi
mysql -e "CREATE DATABASE vigentra DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"
mysql --default-character-set=utf8mb4 vigentra < "$BACKUP_DIR/docuguard-$CARIMBO.sql"
# confere a quantidade de linhas tabela por tabela
DIVERGENCIAS=0
for T in $(mysql -N -e "SELECT table_name FROM information_schema.tables WHERE table_schema='docuguard' AND table_type='BASE TABLE'"); do
  A=$(mysql -N -e "SELECT COUNT(*) FROM docuguard.\`$T\`")
  B=$(mysql -N -e "SELECT COUNT(*) FROM vigentra.\`$T\`")
  [[ $A == "$B" ]] || { echo "    DIVERGÊNCIA em $T: $A x $B"; DIVERGENCIAS=1; }
done
[[ $DIVERGENCIAS -eq 0 ]] || { echo "Cópia do banco inconsistente. O ambiente antigo não foi alterado."; exit 1; }
echo "    Contagem de linhas idêntica em todas as tabelas."

echo "==> 3/5 Copiando configuração e anexos"
id vigentra &>/dev/null || useradd --system --home /opt/vigentra --shell /usr/sbin/nologin vigentra
install -d -m 750 /etc/vigentra
sed -e 's/^DOCUGUARD_/VIGENTRA_/' \
    -e 's|/var/lib/docuguard|/var/lib/vigentra|g' \
    -e 's|/var/log/docuguard|/var/log/vigentra|g' \
    -e 's|/docuguard.log|/vigentra.log|g' \
    -e 's|\(jdbc:mysql://[^/]*/\)docuguard?|\1vigentra?|' \
    -e 's|^MAIL_FROM_NAME=DocuGuard$|MAIL_FROM_NAME=VIGENTRA|' \
    "$ANTIGO_ENV" > "$NOVO_ENV"
chown root:vigentra "$NOVO_ENV"; chmod 640 "$NOVO_ENV"
# O usuário MySQL da aplicação passa a ter acesso também ao banco novo
DB_USER="$(grep -E '^VIGENTRA_DB_USER=' "$NOVO_ENV" | head -1 | cut -d= -f2-)"
mysql -e "GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES, CREATE ROUTINE, ALTER ROUTINE, EXECUTE ON vigentra.* TO '${DB_USER}'@'localhost'; FLUSH PRIVILEGES;"
install -d -o vigentra -g vigentra -m 750 /var/lib/vigentra /var/log/vigentra
[[ -d /var/lib/docuguard ]] && cp -a /var/lib/docuguard/. /var/lib/vigentra/
chown -R vigentra:vigentra /var/lib/vigentra
# reaproveita o certificado TLS existente com os nomes que o Nginx novo espera
if [[ -f /etc/ssl/docuguard/docuguard.crt && ! -f /etc/ssl/vigentra/vigentra.crt ]]; then
  install -d -m 755 /etc/ssl/vigentra
  cp -a /etc/ssl/docuguard/docuguard.crt /etc/ssl/vigentra/vigentra.crt
  cp -a /etc/ssl/docuguard/docuguard.key /etc/ssl/vigentra/vigentra.key
  chmod 600 /etc/ssl/vigentra/vigentra.key
fi

echo "==> 4/5 Desativando o serviço e o site Nginx antigos (arquivos mantidos)"
systemctl disable docuguard 2>/dev/null || true
rm -f /etc/nginx/sites-enabled/docuguard

echo "==> 5/5 Instalando a versão VIGENTRA"
bash "$RAIZ/deploy/scripts/instalar-vm.sh"

echo
echo "Migração concluída. Ambiente antigo preservado (banco 'docuguard', /etc/docuguard, /var/lib/docuguard)."
echo "Para reverter, veja as instruções no início deste script."
