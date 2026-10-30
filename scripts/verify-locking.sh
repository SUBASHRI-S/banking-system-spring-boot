#!/usr/bin/env bash
# Proves the locking rule from docs/DESIGN.md section 7 on a REAL PostgreSQL, without Java.
#
# It runs the same SQL pattern TransferService uses (SELECT ... FOR UPDATE, then update both balances,
# insert a transfer) for 25 A->B and 25 B->A transfers at the same moment, twice:
#   1. ordered  : lowest account id locked first (the design)      -> expect 0 deadlocks, balances unchanged
#   2. naive    : source account locked first                      -> expect many deadlocks
# It also shows that the CHECK (balance >= 0) constraint blocks a negative balance.
#
# Usage (needs psql and a database you do not mind adding two demo rows to; run the Flyway migrations first,
# for example by starting the app once against it):
#   PGHOST=localhost PGPORT=5432 PGUSER=bank PGPASSWORD=bank PGDATABASE=bank ./scripts/verify-locking.sh
set -euo pipefail

P="psql -q -t -A -v ON_ERROR_STOP=1"
STAMP="$(date +%s)"

$P -c "INSERT INTO users(email,password_hash,full_name,role) VALUES ('lock-a-$STAMP@example.com','x','A','USER'),('lock-b-$STAMP@example.com','x','B','USER');"
UA=$($P -c "SELECT id FROM users WHERE email='lock-a-$STAMP@example.com'")
UB=$($P -c "SELECT id FROM users WHERE email='lock-b-$STAMP@example.com'")
NA=$($P -c "SELECT lpad((900000000000 + floor(random()*99999999))::bigint::text, 12, '0')")
NB=$($P -c "SELECT lpad((800000000000 + floor(random()*99999999))::bigint::text, 12, '0')")
$P -c "INSERT INTO accounts(account_number,user_id,type,balance) VALUES ('$NA',$UA,'SAVINGS',1000.00),('$NB',$UB,'SAVINGS',1000.00);"
A=$($P -c "SELECT id FROM accounts WHERE account_number='$NA'")
B=$($P -c "SELECT id FROM accounts WHERE account_number='$NB'")

# one transfer = one transaction. args: from to mode(ordered|naive)
xfer() {
  local from=$1 to=$2 mode=$3 lo hi
  if [ "$mode" = ordered ]; then lo=$(( from < to ? from : to )); hi=$(( from < to ? to : from )); else lo=$from; hi=$to; fi
  $P 2>&1 <<SQL | grep -i "deadlock" | head -1 || true
BEGIN;
SELECT balance FROM accounts WHERE id=$lo FOR UPDATE;
SELECT pg_sleep(0.01);
SELECT balance FROM accounts WHERE id=$hi FOR UPDATE;
UPDATE accounts SET balance = balance - 10 WHERE id=$from;
UPDATE accounts SET balance = balance + 10 WHERE id=$to;
INSERT INTO transfers(from_account_id,to_account_id,amount) VALUES ($from,$to,10);
COMMIT;
SQL
}

run() { for _ in $(seq 1 25); do xfer "$A" "$B" "$1" & xfer "$B" "$A" "$1" & done; wait; }
balances() { $P -c "SELECT string_agg(balance::text, ' / ' ORDER BY id) || '   total=' || sum(balance) FROM accounts WHERE id IN ($A,$B)"; }

echo "Accounts under test: $A and $B (start: 1000.00 / 1000.00)"
echo
echo "1) ORDERED locks (lowest id first, the design):"
echo "   deadlock errors: $(run ordered | wc -l)"
echo "   balances: $(balances)"
echo "   transfers recorded: $($P -c "SELECT count(*) FROM transfers WHERE from_account_id IN ($A,$B)")  (expect 50)"
echo
echo "2) NAIVE locks (source first, what the design avoids):"
echo "   deadlock errors: $(run naive | wc -l)  (PostgreSQL aborts one side of each deadlock)"
echo "   total money: $($P -c "SELECT sum(balance) FROM accounts WHERE id IN ($A,$B)")  (still conserved: aborted transactions roll back)"
echo
echo "3) CHECK (balance >= 0) as the last safety net:"
$P -c "UPDATE accounts SET balance = -1 WHERE id=$A;" 2>&1 | head -1 || true
