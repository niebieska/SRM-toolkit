#!/bin/bash
# Sourced by the MariaDB image on FIRST initialization of its data directory.
# Existing databases/users are intentionally never altered by this script.
(
set -e
# The image SQL helper inspects optional positional parameters; do not enable -u.

: "${BIURO_DB_USER:?Set BIURO_DB_USER}"
: "${BIURO_DB_PASSWORD:?Set BIURO_DB_PASSWORD}"
case "$BIURO_DB_USER" in
    root|*[!a-zA-Z0-9_]*) echo "Invalid Biuro database username" >&2; exit 1 ;;
esac
if [ "$BIURO_DB_USER" = "$MARIADB_USER" ]; then
    echo "Biuro and Registration need distinct database users" >&2
    exit 1
fi

# NO_BACKSLASH_ESCAPES and doubled quotes keep the password a SQL string literal.
biuro_password_sql=${BIURO_DB_PASSWORD//\'/\'\'}
docker_process_sql <<SQL
SET SESSION sql_mode = 'NO_BACKSLASH_ESCAPES';
CREATE DATABASE biuro_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER '$BIURO_DB_USER'@'%' IDENTIFIED BY '$biuro_password_sql';
GRANT ALL PRIVILEGES ON biuro_db.* TO '$BIURO_DB_USER'@'%';
SQL
unset biuro_password_sql
)
