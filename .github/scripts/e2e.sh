#!/usr/bin/env bash
#
# Copyright (c) 2026 Jyotiprakash Parida
# SPDX-License-Identifier: MIT
#
# CI-only end-to-end security test against the full Docker Compose stack.
#
# Interactive PKCE login cannot run headless, so this script creates a TEMPORARY test client with
# direct-access grants through Keycloak's admin API, inside the CI container only. The shipped realm
# (infra/keycloak/realm-export.json) never enables the password grant.
set -euo pipefail

set -a; . infra/.env; set +a

ISSUER="${KEYCLOAK_ISSUER_URI}"
KC_BASE="${ISSUER%/realms/*}"
GW="http://localhost:${GATEWAY_HOST_PORT:-8000}"
PASS=0

ok()   { PASS=$((PASS + 1)); echo "  PASS  $*"; }
fail() { echo "  FAIL  $*" >&2; exit 1; }
expect_status() { # expected actual description
  [[ "$2" == "$1" ]] && ok "$3 -> HTTP $2" || fail "$3: expected HTTP $1, got $2"
}
http() { # method url token [body] [extra-header] -> writes body to /tmp/body, prints status
  local method="$1" url="$2" token="$3" body="${4:-}" extra="${5:-}"
  local args=(-s -o /tmp/body -w '%{http_code}' -X "${method}" "${url}")
  [[ -n "${token}" ]] && args+=(-H "Authorization: Bearer ${token}")
  [[ -n "${body}" ]] && args+=(-H 'Content-Type: application/json' -d "${body}")
  [[ -n "${extra}" ]] && args+=(-H "${extra}")
  curl "${args[@]}"
}

echo "== Preparing CI-only test client"
ADMIN_TOKEN="$(curl -fsS -X POST "${KC_BASE}/realms/master/protocol/openid-connect/token" \
  -d grant_type=password -d client_id=admin-cli \
  --data-urlencode "username=${KEYCLOAK_ADMIN_USER}" --data-urlencode "password=${KEYCLOAK_ADMIN_PASSWORD}" \
  | jq -r .access_token)"
audience_mapper() {
  printf '{"name":"aud-%s","protocol":"openid-connect","protocolMapper":"oidc-audience-mapper","config":{"included.custom.audience":"%s","access.token.claim":"true","id.token.claim":"false"}}' "$1" "$1"
}
CLIENT_JSON="$(jq -n \
  --argjson m1 "$(audience_mapper banking-api)" \
  --argjson m2 "$(audience_mapper account-service)" \
  --argjson m3 "$(audience_mapper transfer-service)" \
  '{clientId:"ci-e2e-direct-grant", publicClient:true, standardFlowEnabled:false, directAccessGrantsEnabled:true, protocolMappers:[$m1,$m2,$m3]}')"
curl -fsS -X POST "${KC_BASE}/admin/realms/banking-realm/clients" \
  -H "Authorization: Bearer ${ADMIN_TOKEN}" -H 'Content-Type: application/json' -d "${CLIENT_JSON}"

user_tokens() {
  curl -fsS -X POST "${ISSUER}/protocol/openid-connect/token" \
    -d grant_type=password -d client_id=ci-e2e-direct-grant -d scope=openid \
    --data-urlencode "username=$1" --data-urlencode "password=${KEYCLOAK_TEST_USER_PASSWORD}"
}
ALICE_JSON="$(user_tokens alice.customer)"
ALICE="$(jq -r .access_token <<<"${ALICE_JSON}")"
ALICE_ID_TOKEN="$(jq -r .id_token <<<"${ALICE_JSON}")"
BOB="$(user_tokens bob.customer | jq -r .access_token)"
CAROL="$(user_tokens carol.admin | jq -r .access_token)"
DAVE="$(user_tokens dave.auditor | jq -r .access_token)"
SERVICE="$(curl -fsS -X POST "${ISSUER}/protocol/openid-connect/token" -d grant_type=client_credentials \
  -d client_id=transfer-service-client --data-urlencode "client_secret=${TRANSFER_SERVICE_CLIENT_SECRET}" \
  | jq -r .access_token)"

echo "== 401: authentication failures"
expect_status 401 "$(http GET "${GW}/api/v1/accounts/me" "")" "no token"
TAMPERED="$(cut -d. -f1 <<<"${ALICE}").$(printf '{"sub":"attacker","exp":9999999999}' | openssl base64 -A | tr '+/' '-_' | tr -d '=').$(cut -d. -f3 <<<"${ALICE}")"
expect_status 401 "$(http GET "${GW}/api/v1/accounts/me" "${TAMPERED}")" "tampered payload (signature mismatch)"
NONE_ALG="$(printf '{"alg":"none","typ":"JWT"}' | openssl base64 -A | tr '+/' '-_' | tr -d '=').$(cut -d. -f2 <<<"${ALICE}")."
expect_status 401 "$(http GET "${GW}/api/v1/accounts/me" "${NONE_ALG}")" "alg=none token"
expect_status 401 "$(http GET "${GW}/api/v1/accounts/me" "${ALICE_ID_TOKEN}")" "ID token used as access token"

echo "== Accounts and RBAC"
expect_status 201 "$(http POST "${GW}/api/v1/accounts" "${ALICE}" '{"type":"SAVINGS","currency":"INR"}')" "alice opens account"
A_ID="$(jq -r .id /tmp/body)"; A_NUM="$(jq -r .accountNumber /tmp/body)"
expect_status 201 "$(http POST "${GW}/api/v1/accounts" "${BOB}" '{"type":"CURRENT","currency":"INR"}')" "bob opens account"
B_ID="$(jq -r .id /tmp/body)"; B_NUM="$(jq -r .accountNumber /tmp/body)"
expect_status 400 "$(http POST "${GW}/api/v1/accounts" "${ALICE}" '{"type":"SAVINGS","currency":"INR","ownerSubject":"x"}')" "mass assignment of owner rejected"
expect_status 403 "$(http GET "${GW}/api/v1/accounts" "${ALICE}")" "customer lists all accounts"
expect_status 403 "$(http POST "${GW}/api/v1/accounts" "${CAROL}" '{"type":"SAVINGS","currency":"INR"}')" "admin opens customer account"
expect_status 200 "$(http GET "${GW}/api/v1/accounts" "${DAVE}")" "auditor lists all accounts"
expect_status 403 "$(http PATCH "${GW}/api/v1/accounts/${B_ID}/status" "${DAVE}" '{"status":"FROZEN","reason":"x"}')" "auditor freezes account"

expect_status 403 "$(http GET "${GW}/api/v1/accounts/me" "${SERVICE}")" "machine token on customer API (no account:read)"

echo "== BOLA / IDOR"
expect_status 404 "$(http GET "${GW}/api/v1/accounts/${B_ID}" "${ALICE}")" "alice reads bob's account"
expect_status 200 "$(http GET "${GW}/api/v1/accounts/${B_ID}" "${DAVE}")" "auditor reads bob's account"

echo "== Funding and transfers"
expect_status 200 "$(http POST "${GW}/api/v1/accounts/${A_ID}/deposits" "${CAROL}" '{"amount":1000.00}')" "admin deposit"
TRANSFER="$(jq -n --arg s "${A_ID}" --arg t "${B_NUM}" '{sourceAccountId:$s, targetAccountNumber:$t, amount:250.00, currency:"INR", reference:"rent"}')"
expect_status 201 "$(http POST "${GW}/api/v1/transfers" "${ALICE}" "${TRANSFER}" "Idempotency-Key: e2e-key-0001")" "alice transfers 250"
[[ "$(jq -r .status /tmp/body)" == "COMPLETED" ]] && ok "transfer COMPLETED" || fail "transfer status $(cat /tmp/body)"
T_ID="$(jq -r .id /tmp/body)"
expect_status 200 "$(http POST "${GW}/api/v1/transfers" "${ALICE}" "${TRANSFER}" "Idempotency-Key: e2e-key-0001")" "idempotent replay"
[[ "$(jq -r .id /tmp/body)" == "${T_ID}" ]] && ok "replay returned same transfer" || fail "replay created a new transfer"
expect_status 200 "$(http GET "${GW}/api/v1/accounts/${B_ID}" "${BOB}")" "bob reads own account"
[[ "$(jq -r '.balance | tostring' /tmp/body)" =~ ^250(\.0+)?$ ]] && ok "bob balance is 250" || fail "unexpected balance $(cat /tmp/body)"
expect_status 404 "$(http GET "${GW}/api/v1/transfers/${T_ID}" "${BOB}")" "bob reads alice's transfer"

STEAL="$(jq -n --arg s "${A_ID}" --arg t "${B_NUM}" '{sourceAccountId:$s, targetAccountNumber:$t, amount:10.00, currency:"INR"}')"
expect_status 404 "$(http POST "${GW}/api/v1/transfers" "${BOB}" "${STEAL}" "Idempotency-Key: e2e-key-0002")" "bob debits alice's account (token propagation)"

BIG="$(jq -n --arg s "${A_ID}" --arg t "${B_NUM}" '{sourceAccountId:$s, targetAccountNumber:$t, amount:99999.00, currency:"INR"}')"
expect_status 201 "$(http POST "${GW}/api/v1/transfers" "${ALICE}" "${BIG}" "Idempotency-Key: e2e-key-0003")" "overdraft attempt recorded"
[[ "$(jq -r .failureCode /tmp/body)" == "insufficient_funds" ]] && ok "overdraft FAILED insufficient_funds" || fail "$(cat /tmp/body)"

echo "== Internal endpoints are not exposed"
STATUS="$(http POST "${GW}/internal/v1/ledger/transfers" "${ALICE}" '{}')"
[[ "${STATUS}" == "403" || "${STATUS}" == "404" ]] && ok "internal ledger via gateway -> HTTP ${STATUS}" || fail "internal ledger reachable: ${STATUS}"

echo "== Velocity limit (per-user, transfer-service)"
GOT_429=no
for i in $(seq 10 20); do
  S="$(http POST "${GW}/api/v1/transfers" "${BOB}" "${STEAL}" "Idempotency-Key: e2e-rate-00${i}")"
  if [[ "${S}" == "429" ]]; then GOT_429=yes; break; fi
done
[[ "${GOT_429}" == "yes" ]] && ok "velocity limit -> HTTP 429" || fail "no 429 from velocity limit"

echo "== jti revocation"
expect_status 204 "$(http POST "${GW}/api/v1/transfers/token-revocations" "${ALICE}")" "alice revokes her token"
expect_status 401 "$(http POST "${GW}/api/v1/transfers" "${ALICE}" "${TRANSFER}" "Idempotency-Key: e2e-key-0004")" "revoked token on high-risk call"

echo
echo "All ${PASS} end-to-end security checks passed."
