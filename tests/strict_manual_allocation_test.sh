#!/bin/bash

# Splitz Strict Manual Allocation Test Script
USER_URL=${USER_URL:-"http://localhost:8080"}
EXPENSE_URL=${EXPENSE_URL:-"http://localhost:8081"}
RANDOM_SUFFIX=$((1000 + RANDOM % 9000))

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "=== Strict Manual Allocation Test (Suffix: $RANDOM_SUFFIX) ==="

# Helper to run curl
call_api() {
  local method=$1
  local url=$2
  local token=$3
  local data=$4
  local desc=$5

  echo -n "Testing $desc... " >&2
  
  local args=(-s -X "$method" "$url" -H "Content-Type: application/json")
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$data" ] && args+=(-d "$data")

  local res=$(curl "${args[@]}")
  
  if echo "$res" | jq . > /dev/null 2>&1; then
    echo -e "${GREEN}SUCCESS${NC}" >&2
    echo "$res"
  else
    echo -e "${RED}FAILED${NC}" >&2
    echo "Raw Response: $res" >&2
    exit 1
  fi
}

# 1. Register & Login
USER_A_RES=$(call_api "POST" "$USER_URL/users" "" "{\"username\": \"alice_$RANDOM_SUFFIX\", \"email\": \"alice_$RANDOM_SUFFIX@ex.com\", \"password\": \"pass\", \"firstName\": \"Alice\", \"lastName\": \"User\"}" "Register Alice")
USER_ID_A=$(echo "$USER_A_RES" | jq -r .id)
echo "Alice ID: $USER_ID_A"

USER_B_RES=$(call_api "POST" "$USER_URL/users" "" "{\"username\": \"bob_$RANDOM_SUFFIX\", \"email\": \"bob_$RANDOM_SUFFIX@ex.com\", \"password\": \"pass\", \"firstName\": \"Bob\", \"lastName\": \"User\"}" "Register Bob")
USER_ID_B=$(echo "$USER_B_RES" | jq -r .id)
echo "Bob ID: $USER_ID_B"

TOKEN_A=$(call_api "POST" "$USER_URL/authenticate" "" "{\"username\":\"alice_$RANDOM_SUFFIX\",\"password\":\"pass\"}" "Login Alice" | jq -r .token)
echo "Token A: $TOKEN_A"
TOKEN_B=$(call_api "POST" "$USER_URL/authenticate" "" "{\"username\":\"bob_$RANDOM_SUFFIX\",\"password\":\"pass\"}" "Login Bob" | jq -r .token)
echo "Token B: $TOKEN_B"

# 2. Create Two Groups
GROUP_1_RES=$(call_api "POST" "$EXPENSE_URL/groups" "$TOKEN_A" "{\"name\":\"Dinner Group $RANDOM_SUFFIX\"}" "Create Dinner Group")
GROUP_ID_1=$(echo "$GROUP_1_RES" | jq -r .id)

GROUP_2_RES=$(call_api "POST" "$EXPENSE_URL/groups" "$TOKEN_A" "{\"name\":\"Travel Group $RANDOM_SUFFIX\"}" "Create Travel Group")
GROUP_ID_2=$(echo "$GROUP_2_RES" | jq -r .id)

# 3. Add Bob to both groups
call_api "POST" "$EXPENSE_URL/groups/$GROUP_ID_1/members" "$TOKEN_A" "{\"userId\":$USER_ID_B}" "Add Bob to Dinner Group" > /dev/null
call_api "POST" "$EXPENSE_URL/groups/$GROUP_ID_2/members" "$TOKEN_A" "{\"userId\":$USER_ID_B}" "Add Bob to Travel Group" > /dev/null

# 4. Create Expenses
# Dinner: $40, split EXACT (Bob owes $20)
call_api "POST" "$EXPENSE_URL/groups/$GROUP_ID_1/expenses" "$TOKEN_A" "{\"description\": \"Dinner\", \"amount\": 40, \"currency\": \"EUR\", \"groupId\": $GROUP_ID_1, \"paidBy\": $USER_ID_A, \"categoryId\": 1, \"splitType\": \"EXACT\", \"splits\": [{\"userId\": $USER_ID_A, \"splitValue\": 20}, {\"userId\": $USER_ID_B, \"splitValue\": 20}]}" "Create Dinner Expense" > /dev/null

# Travel: $100, split EXACT (Bob owes $50)
call_api "POST" "$EXPENSE_URL/groups/$GROUP_ID_2/expenses" "$TOKEN_A" "{\"description\": \"Flight\", \"amount\": 100, \"currency\": \"EUR\", \"groupId\": $GROUP_ID_2, \"paidBy\": $USER_ID_A, \"categoryId\": 2, \"splitType\": \"EXACT\", \"splits\": [{\"userId\": $USER_ID_A, \"splitValue\": 50}, {\"userId\": $USER_ID_B, \"splitValue\": 50}]}" "Create Travel Expense" > /dev/null

# 5. Verify Balance
echo "Checking Balance for Alice ($USER_ID_A) with Bob ($USER_ID_B)..."
MAX_RETRIES=5
RETRY_COUNT=0
while [ $RETRY_COUNT -lt $MAX_RETRIES ]; do
  curl -s -X "GET" "$EXPENSE_URL/users/$USER_ID_A/balances/with/$USER_ID_B" -H "Authorization: Bearer $TOKEN_A" > balance_v.json
  if [ -s balance_v.json ]; then
    break
  fi
  echo "Retry $RETRY_COUNT..."
  sleep 2
  RETRY_COUNT=$((RETRY_COUNT+1))
done

cat balance_v.json
NET_BALANCE=$(cat balance_v.json | jq -r .netBalance)
echo "Net Balance: $NET_BALANCE"

if [ "$NET_BALANCE" != "70.00" ]; then
  echo -e "${RED}ERROR: Expected net balance 70.00, got $NET_BALANCE${NC}"
  # Print log for debugging
  cat balance_v.log
  exit 1
fi

# 6. Alice records receiving $70 from Bob, allocated manually
echo "Alice (Payee) recording receipt from Bob (Payer)..."
ALLOCATION_PAYLOAD=$(cat <<EOF
{
  "payerId": $USER_ID_B,
  "payeeId": $USER_ID_A,
  "amount": 70.00,
  "allocations": [
    { "groupId": $GROUP_ID_1, "amount": 20.00 },
    { "groupId": $GROUP_ID_2, "amount": 50.00 }
  ]
}
EOF
)

SETTLEMENT_RES=$(call_api "POST" "$EXPENSE_URL/friendship-settlements" "$TOKEN_A" "$ALLOCATION_PAYLOAD" "Create Manual Allocation Settlement (Payee recording)")

# 7. Verify Response
COUNT=$(echo "$SETTLEMENT_RES" | jq '. | length')
if [ "$COUNT" != "2" ]; then
  echo -e "${RED}ERROR: Expected 2 friendship settlements, got $COUNT${NC}"
  exit 1
fi

STATUS_1=$(echo "$SETTLEMENT_RES" | jq -r '.[0].status')
if [ "$STATUS_1" != "COMPLETED" ]; then
  echo -e "${RED}ERROR: Expected status COMPLETED when recorded by Payee, got $STATUS_1${NC}"
  exit 1
fi

echo -e "${GREEN}=== Manual Allocation Verified (2 settlements created) ===${NC}"

# 8. Test Failure: Mismatching allocation amount
MISMATCH_PAYLOAD=$(cat <<EOF
{
  "payerId": $USER_ID_B,
  "payeeId": $USER_ID_A,
  "amount": 70.00,
  "allocations": [
    { "groupId": $GROUP_ID_1, "amount": 20.00 },
    { "groupId": $GROUP_ID_2, "amount": 40.00 }
  ]
}
EOF
)

echo -n "Testing Mismatch Failure... "
res=$(curl -s -X "POST" "$EXPENSE_URL/friendship-settlements" -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN_B" -d "$MISMATCH_PAYLOAD")

if echo "$res" | grep -q "Total allocated amount (60.00) must match settlement amount (70.00)"; then
  echo -e "${GREEN}SUCCESS (Caught mismatch)${NC}"
else
  echo -e "${RED}FAILED (Did not catch mismatch)${NC}"
  echo "Response: $res"
  exit 1
fi

echo -e "${GREEN}=== All Tests Passed ===${NC}"
rm balance_v.json balance_v.log
