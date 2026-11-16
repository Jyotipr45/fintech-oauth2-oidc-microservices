#!/usr/bin/env bash
#
# Copyright (c) 2026 Jyotiprakash Parida
# SPDX-License-Identifier: MIT
#
# Creates infra/.env from infra/.env.example, replacing every CHANGE_ME value with a
# freshly generated random secret. Refuses to overwrite an existing infra/.env.
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INFRA_DIR="$(dirname "${SCRIPT_DIR}")"
TEMPLATE="${INFRA_DIR}/.env.example"
TARGET="${INFRA_DIR}/.env"

if [[ -f "${TARGET}" ]]; then
  echo "Refusing to overwrite existing ${TARGET}. Delete it first if you really want new secrets." >&2
  exit 1
fi

command -v openssl >/dev/null 2>&1 || { echo "openssl is required" >&2; exit 1; }

random_secret() {
  # 32 URL-safe characters
  openssl rand -base64 48 | tr -d '/+=\n' | cut -c1-32
}

random_user_password() {
  # Satisfies the realm password policy: upper, lower, digit, special, >= 12 chars.
  printf 'Bk#%s9a' "$(openssl rand -base64 24 | tr -d '/+=\n' | cut -c1-16)"
}

umask 077
while IFS= read -r line || [[ -n "${line}" ]]; do
  if [[ "${line}" =~ ^(KEYCLOAK_TEST_USER_PASSWORD)=CHANGE_ME ]]; then
    echo "${BASH_REMATCH[1]}=$(random_user_password)"
  elif [[ "${line}" =~ ^([A-Z0-9_]+)=CHANGE_ME ]]; then
    echo "${BASH_REMATCH[1]}=$(random_secret)"
  else
    echo "${line}"
  fi
done < "${TEMPLATE}" > "${TARGET}"

chmod 600 "${TARGET}"
echo "Wrote ${TARGET} with freshly generated secrets (mode 600)."
echo "Demo user password: grep KEYCLOAK_TEST_USER_PASSWORD ${TARGET}"
