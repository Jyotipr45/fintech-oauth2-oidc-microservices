#!/usr/bin/env bash
#
# Copyright (c) 2026 Jyotiprakash Parida
# SPDX-License-Identifier: MIT
#
# Runs once, on first start of an empty PostgreSQL volume.
# Creates one database and one least-privilege login role per service, so a compromised
# service cannot read another service's data. All names/passwords come from the environment.
#
set -euo pipefail

: "${KEYCLOAK_DB_NAME:?}" "${KEYCLOAK_DB_USER:?}" "${KEYCLOAK_DB_PASSWORD:?}"
: "${ACCOUNT_DB_NAME:?}"  "${ACCOUNT_DB_USER:?}"  "${ACCOUNT_DB_PASSWORD:?}"
: "${TRANSFER_DB_NAME:?}" "${TRANSFER_DB_USER:?}" "${TRANSFER_DB_PASSWORD:?}"

create_service_database() {
  local db_name="$1" db_user="$2" db_password="$3"

  # psql variables (:'var' / :"var") quote values safely; nothing is interpolated by the shell into SQL.
  psql -v ON_ERROR_STOP=1 \
       --username "${POSTGRES_USER}" --dbname postgres \
       -v db_name="${db_name}" -v db_user="${db_user}" -v db_password="${db_password}" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT', :'db_user', :'db_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'db_user') \gexec

SELECT format('CREATE DATABASE %I OWNER %I ENCODING ''UTF8''', :'db_name', :'db_user')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'db_name') \gexec

SELECT format('REVOKE ALL ON DATABASE %I FROM PUBLIC', :'db_name') \gexec
SELECT format('GRANT CONNECT, TEMPORARY ON DATABASE %I TO %I', :'db_name', :'db_user') \gexec
SQL

  # Lock down the public schema inside the new database (PostgreSQL 15+ default, made explicit).
  psql -v ON_ERROR_STOP=1 --username "${POSTGRES_USER}" --dbname "${db_name}" \
       -v db_user="${db_user}" <<'SQL'
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
SELECT format('GRANT USAGE, CREATE ON SCHEMA public TO %I', :'db_user') \gexec
SQL

  echo "Provisioned database '${db_name}' for role '${db_user}'."
}

create_service_database "${KEYCLOAK_DB_NAME}" "${KEYCLOAK_DB_USER}" "${KEYCLOAK_DB_PASSWORD}"
create_service_database "${ACCOUNT_DB_NAME}"  "${ACCOUNT_DB_USER}"  "${ACCOUNT_DB_PASSWORD}"
create_service_database "${TRANSFER_DB_NAME}" "${TRANSFER_DB_USER}" "${TRANSFER_DB_PASSWORD}"
