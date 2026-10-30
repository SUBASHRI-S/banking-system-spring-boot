#!/usr/bin/env bash
# End-to-end smoke test against a running instance (local or deployed).
#   ./scripts/smoke-test.sh                       -> http://localhost:8080
#   ./scripts/smoke-test.sh https://your-app.onrender.com
# Needs: curl and jq. The first call to a sleeping free instance can take about a minute.
set -euo pipefail

BASE="${1:-http://localhost:8080}"
API="$BASE/api/v1"
PASS='Str0ng#Pass1'
STAMP="$(date +%s)"

say() { printf '\n== %s\n' "$*"; }
req() { curl -sS --max-time 120 -H 'Content-Type: application/json' "$@"; }

say "health"
req "$BASE/actuator/health"; echo

for who in alice bob; do
  say "register $who"
  req -X POST "$API/auth/register" -d "{\"fullName\":\"$who\",\"email\":\"$who-$STAMP@example.com\",\"password\":\"$PASS\"}"; echo
done

login() { req -X POST "$API/auth/login" -d "{\"email\":\"$1-$STAMP@example.com\",\"password\":\"$PASS\"}" | jq -r .accessToken; }
A_TOKEN="$(login alice)"
B_TOKEN="$(login bob)"

open_account() { req -X POST "$API/accounts" -H "Authorization: Bearer $1" -d '{"type":"SAVINGS"}'; }
A_ACC="$(open_account "$A_TOKEN")"; B_ACC="$(open_account "$B_TOKEN")"
A_ID="$(echo "$A_ACC" | jq -r .id)"
B_NUM="$(echo "$B_ACC" | jq -r .accountNumber)"

say "alice deposits 500.00"
req -X POST "$API/accounts/$A_ID/deposit" -H "Authorization: Bearer $A_TOKEN" -d '{"amount":"500.00"}'; echo

say "alice transfers 250.00 to bob (by account number)"
req -X POST "$API/transfers" -H "Authorization: Bearer $A_TOKEN" -H 'Idempotency-Key: smoke-'"$STAMP" \
  -d "{\"fromAccountId\":$A_ID,\"toAccountNumber\":\"$B_NUM\",\"amount\":\"250.00\",\"description\":\"smoke\"}"; echo

say "same Idempotency-Key again: same transferId, no second debit"
req -X POST "$API/transfers" -H "Authorization: Bearer $A_TOKEN" -H 'Idempotency-Key: smoke-'"$STAMP" \
  -d "{\"fromAccountId\":$A_ID,\"toAccountNumber\":\"$B_NUM\",\"amount\":\"250.00\",\"description\":\"smoke\"}"; echo

say "failure case: withdraw 9999.00 -> 422 INSUFFICIENT_FUNDS"
req -X POST "$API/accounts/$A_ID/withdraw" -H "Authorization: Bearer $A_TOKEN" -d '{"amount":"9999.00"}'; echo

say "failure case: no token -> 401"
req "$API/accounts"; echo

say "history"
req "$API/accounts/$A_ID/transactions" -H "Authorization: Bearer $A_TOKEN" | jq '.content[] | {entryType, amount, balanceAfter}'

echo; echo "Smoke test finished. Check the outputs above."
