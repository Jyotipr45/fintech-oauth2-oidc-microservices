#!/usr/bin/env bash
#
# Copyright (c) 2026 Jyotiprakash Parida
# SPDX-License-Identifier: MIT
#
# Interactive OAuth 2.0 Authorization Code + PKCE (S256) login against Keycloak, from a terminal.
#
#   TOKENS="$(./infra/scripts/pkce-login.sh)"
#   ACCESS_TOKEN="$(jq -r .access_token <<<"${TOKENS}")"
#
# 1. Generates a random code_verifier and its S256 code_challenge, plus state and nonce.
# 2. Prints the authorization URL: open it, sign in (e.g. alice.customer).
# 3. Catches the redirect on http://127.0.0.1:8765/callback, checks state, and exchanges the code
#    together with the code_verifier for tokens. Prints the token response JSON to stdout.
#
# Requires: bash, openssl, curl, python3.
set -euo pipefail

ISSUER="${KEYCLOAK_ISSUER_URI:-http://keycloak:8080/realms/banking-realm}"
CLIENT_ID="${CLIENT_ID:-banking-web-pkce}"
CALLBACK_PORT=8765
REDIRECT_URI="http://127.0.0.1:${CALLBACK_PORT}/callback"

for tool in openssl curl python3; do
  command -v "${tool}" >/dev/null 2>&1 || { echo "${tool} is required" >&2; exit 1; }
done

b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }

# RFC 7636: 43-128 chars from the unreserved set.
CODE_VERIFIER="$(openssl rand 48 | b64url)"
CODE_CHALLENGE="$(printf '%s' "${CODE_VERIFIER}" | openssl dgst -sha256 -binary | b64url)"
STATE="$(openssl rand -hex 16)"
NONCE="$(openssl rand -hex 16)"

ENCODED_REDIRECT="$(python3 -c 'import sys, urllib.parse; print(urllib.parse.quote(sys.argv[1], safe=""))' "${REDIRECT_URI}")"
AUTH_URL="${ISSUER}/protocol/openid-connect/auth?response_type=code&client_id=${CLIENT_ID}"
AUTH_URL+="&redirect_uri=${ENCODED_REDIRECT}&scope=openid&state=${STATE}&nonce=${NONCE}"
AUTH_URL+="&code_challenge=${CODE_CHALLENGE}&code_challenge_method=S256"

{
  echo "Open this URL in your browser and sign in:"
  echo
  echo "  ${AUTH_URL}"
  echo
  echo "Waiting for the redirect on ${REDIRECT_URI} ..."
} >&2

# One-shot local listener: validates state, returns the authorization code.
AUTH_CODE="$(python3 - "${CALLBACK_PORT}" "${STATE}" <<'PY'
import http.server, sys, urllib.parse

port, expected_state = int(sys.argv[1]), sys.argv[2]
result = {}

class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        query = urllib.parse.parse_qs(urllib.parse.urlparse(self.path).query)
        ok = query.get("state", [""])[0] == expected_state and "code" in query
        result["code"] = query["code"][0] if ok else ""
        self.send_response(200 if ok else 400)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.end_headers()
        self.wfile.write(b"Login complete. You can close this tab." if ok else b"Invalid state or missing code.")

    def log_message(self, *args):
        pass

server = http.server.HTTPServer(("127.0.0.1", port), Handler)
server.handle_request()
print(result.get("code", ""))
PY
)"

if [[ -z "${AUTH_CODE}" ]]; then
  echo "Did not receive a valid authorization code (state mismatch or login cancelled)." >&2
  exit 1
fi

# The code alone is useless without the verifier: an intercepted code cannot be redeemed.
curl -fsS -X POST "${ISSUER}/protocol/openid-connect/token" \
  -d grant_type=authorization_code \
  -d client_id="${CLIENT_ID}" \
  --data-urlencode "code=${AUTH_CODE}" \
  --data-urlencode "redirect_uri=${REDIRECT_URI}" \
  --data-urlencode "code_verifier=${CODE_VERIFIER}"
echo
