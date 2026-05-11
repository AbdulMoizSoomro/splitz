#!/bin/bash

# Splitz Expense Service Smoke Test Script (Clean)
USER_URL=${USER_URL:-"http://localhost:8080"}
EXPENSE_URL=${EXPENSE_URL:-"http://localhost:8081"}
RANDOM_SUFFIX=$((1000 + RANDOM % 9000))

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "=== Splitz Smoke Tests (Suffix: $RANDOM_SUFFIX) ==="

# Helper to run curl
# Prints JSON to stdout, status to stderr
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
USER_A_RES=$(call_api "POST" "$USER_URL/users" "" "{\"username\": \"a_$RANDOM_SUFFIX\", \"email\": \"a_$RANDOM_SUFFIX@ex.com\", \"password\": \"pass\", \"firstName\": \"A\", \"lastName\": \"User\"}" "Register User A")
USER_ID_A=$(echo "$USER_A_RES" | jq -r .id)

USER_B_RES=$(call_api "POST" "$USER_URL/users" "" "{\"username\": \"b_$RANDOM_SUFFIX\", \"email\": \"b_$RANDOM_SUFFIX@ex.com\", \"password\": \"pass\", \"firstName\": \"B\", \"lastName\": \"User\"}" "Register User B")
USER_ID_B=$(echo "$USER_B_RES" | jq -r .id)

TOKEN_A=$(call_api "POST" "$USER_URL/authenticate" "" "{\"username\":\"a_$RANDOM_SUFFIX\",\"password\":\"pass\"}" "Login A" | jq -r .jwt)
TOKEN_B=$(call_api "POST" "$USER_URL/authenticate" "" "{\"username\":\"b_$RANDOM_SUFFIX\",\"password\":\"pass\"}" "Login B" | jq -r .jwt)

# 2. Create Group & Add Member
GROUP_RES=$(call_api "POST" "$EXPENSE_URL/groups" "$TOKEN_A" "{\"name\":\"Group $RANDOM_SUFFIX\"}" "Create Group")
GROUP_ID=$(echo "$GROUP_RES" | jq -r .id)

call_api "POST" "$EXPENSE_URL/groups/$GROUP_ID/members" "$TOKEN_A" "{\"userId\":$USER_ID_B}" "Add Member B" > /dev/null

# 3. Create Expense
call_api "POST" "$EXPENSE_URL/groups/$GROUP_ID/expenses" "$TOKEN_A" "{\"description\": \"Dinner\", \"amount\": 100, \"currency\": \"USD\", \"groupId\": $GROUP_ID, \"paidBy\": $USER_ID_A, \"categoryId\": 1, \"splits\": [{\"userId\": $USER_ID_A, \"splitValue\": 50}, {\"userId\": $USER_ID_B, \"splitValue\": 50}]}" "Create Expense" > /dev/null

# 4. Global Activity
call_api "GET" "$EXPENSE_URL/activity" "$TOKEN_A" "" "Get Activity" > /dev/null

echo -e "${GREEN}=== All Happy Paths Verified ===${NC}"
