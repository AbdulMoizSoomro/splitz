#!/bin/bash

# Fix BalanceServiceTest
sed -i 's/when(splitzAuthorizer.getCurrentUserId()).thenReturn/lenient().when(splitzAuthorizer.getCurrentUserId()).thenReturn/g' src/test/java/com/splitz/expense/service/BalanceServiceTest.java
sed -i '/@BeforeEach/!b;n;a\    lenient().when(splitzAuthorizer.getCurrentUserId()).thenReturn(101L);' src/test/java/com/splitz/expense/service/BalanceServiceTest.java

# Fix SettlementServiceTest
sed -i 's/when(splitzAuthorizer.getCurrentUserId()).thenReturn/lenient().when(splitzAuthorizer.getCurrentUserId()).thenReturn/g' src/test/java/com/splitz/expense/service/SettlementServiceTest.java
sed -i '/@BeforeEach/!b;n;a\    lenient().when(splitzAuthorizer.getCurrentUserId()).thenReturn(101L);' src/test/java/com/splitz/expense/service/SettlementServiceTest.java
sed -i 's/when(splitzAuthorizer.isAdmin()).thenReturn(false);/lenient().when(splitzAuthorizer.isAdmin()).thenReturn(false);/g' src/test/java/com/splitz/expense/service/SettlementServiceTest.java

# Fix FriendshipSettlementServiceTest
sed -i 's/when(splitzAuthorizer.getCurrentUserId()).thenReturn/lenient().when(splitzAuthorizer.getCurrentUserId()).thenReturn/g' src/test/java/com/splitz/expense/service/FriendshipSettlementServiceTest.java
sed -i 's/when(splitzAuthorizer.isAdmin()).thenReturn(false);/lenient().when(splitzAuthorizer.isAdmin()).thenReturn(false);/g' src/test/java/com/splitz/expense/service/FriendshipSettlementServiceTest.java
