import { test, expect, type Page } from '@playwright/test';

const PASSWORD = 'Password123!';

async function registerUser(page: Page, username: string, firstName: string, lastName: string) {
  await page.goto('/register');
  await page.getByLabel(/first name/i).fill(firstName);
  await page.getByLabel(/last name/i).fill(lastName);
  await page.getByLabel(/username/i).fill(username);
  await page.getByLabel(/email/i).fill(`${username}@example.com`);
  await page.getByLabel(/password/i).fill(PASSWORD);
  await page.getByRole('button', { name: /register/i }).click();
  await expect(page).toHaveURL(/\/login/, { timeout: 15000 });
}

async function loginUser(page: Page, username: string) {
  await page.goto('/login');
  await page.getByLabel(/username/i).fill(username);
  await page.getByLabel(/password/i).fill(PASSWORD);
  await page.getByRole('button', { name: /login/i }).click();
  await expect(page).toHaveURL(/\/$/, { timeout: 15000 });
}

/** Send a friend request from pageA to the given username. */
async function sendFriendRequest(pageA: Page, targetUsername: string) {
  await pageA.goto('/friends');
  await pageA.getByPlaceholder(/search by name or email/i).fill(targetUsername);
  await expect(pageA.getByText(`@${targetUsername}`)).toBeVisible({ timeout: 10000 });
  await pageA.getByRole('button', { name: /add friend/i }).click();
  await expect(pageA.getByText(/pending/i)).toBeVisible({ timeout: 5000 });
}

/** Accept the first pending friend request from the given sender. */
async function acceptFriendRequest(pageB: Page, fromUsername: string) {
  await pageB.goto('/friends');
  await expect(pageB.getByText(`@${fromUsername}`)).toBeVisible({ timeout: 10000 });
  await pageB.getByTitle('Accept').click();
  await expect(pageB.getByText(/no pending friend requests/i)).toBeVisible({ timeout: 5000 });
}

/**
 * Owner creates a group that includes the specified friend (by display name in friend picker).
 * Returns the full URL of the group details page.
 */
async function createGroupWithMember(
  pageOwner: Page,
  groupName: string,
  friendDisplayFirstName: string,
): Promise<string> {
  await pageOwner.goto('/groups');
  await pageOwner.getByRole('button', { name: /create group/i }).first().click();
  const modal = pageOwner.getByRole('dialog');
  await expect(modal).toBeVisible();
  await modal.getByLabel(/group name/i).fill(groupName);

  // Click the friend's name in the friend picker list
  const friendPicker = modal.locator('.max-h-48');
  const friendEntry = friendPicker.getByText(new RegExp(friendDisplayFirstName, 'i'));
  await expect(friendEntry).toBeVisible({ timeout: 10000 });
  await friendEntry.first().click();

  await modal.getByRole('button', { name: /create group/i }).click();
  await expect(modal).not.toBeVisible({ timeout: 10000 });

  await expect(pageOwner.getByText(groupName)).toBeVisible({ timeout: 10000 });
  await pageOwner.getByText(groupName).click();
  await expect(pageOwner).toHaveURL(/\/groups\/\d+/, { timeout: 10000 });

  return pageOwner.url();
}

test.describe('[E2E] Social Discovery and Group Exit', () => {
  /**
   * AC#1: User joins a group with non-friends and verifies the 'Temp Friend' badge appears.
   *
   * Setup: UserA and UserB are NOT friends. UserA creates a group and adds UserB
   * (this requires a different setup — UserB can't be added without being a friend
   * in the current UI. Instead: UserA and UserC are friends and in a group.
   * UserB is NOT a friend of UserA. UserB is added to the group via UserC's session.)
   *
   * Simpler valid interpretation: UserA creates group with UserB (who is a friend).
   * Then UserA removes UserB from friends. Now UserB is a Temp Friend in the group.
   * However, "remove friend" requires confirming a browser dialog.
   *
   * Cleanest approach: Create two users. Make them friends (required for group creation).
   * Add UserB to group. Confirm 'Temp Friend' badge does NOT appear when they are friends.
   * Then remove the friendship → reload → 'Temp Friend' badge should appear.
   *
   * Note: The isTempFriend logic: `!isCurrentUser && friends && !isFriend`
   * If `friends` is empty (non-friend) → isTempFriend = true.
   */
  test('Temp Friend badge appears for non-friend group members', async ({ browser }) => {
    const ts = Date.now();
    const ownerName = `owner_temp_${ts}`;
    const nonFriendName = `nonfriend_temp_${ts}`;
    const memberName = `member_temp_${ts}`;

    const ctxOwner = await browser.newContext();
    const ctxMember = await browser.newContext();
    const pageOwner = await ctxOwner.newPage();
    const pageMember = await ctxMember.newPage();

    try {
      // 1. Register all three users: owner, a friend (member), and a non-friend
      await registerUser(pageOwner, ownerName, 'Owner', 'User');
      await registerUser(pageMember, memberName, 'Member', 'User');
      // Non-friend just needs to exist; register via owner's page then owner logs back in
      await registerUser(pageOwner, nonFriendName, 'NonFriend', 'User');

      // 2. Login owner and member
      await loginUser(pageOwner, ownerName);
      await loginUser(pageMember, memberName);

      // 3. Establish friendship: owner sends to member, member accepts
      await sendFriendRequest(pageOwner, memberName);
      await acceptFriendRequest(pageMember, ownerName);

      // 4. Owner creates group with the member (friend) only
      const groupName = `TempFriend Group ${ts}`;
      const groupUrl = await createGroupWithMember(pageOwner, groupName, 'Member');

      // 5. In GroupDetails, the member (who is a friend of owner) should show "Member" badge
      //    but NOT "Temp Friend" badge (they are friends)
      const membersCard = pageOwner.locator('.divide-y');
      await expect(membersCard.locator('span', { hasText: 'Temp Friend' })).not.toBeVisible();

      // 6. Owner removes the member from friends via the friends page
      pageOwner.on('dialog', (dialog) => dialog.accept());
      await pageOwner.goto('/friends');
      await expect(pageOwner.getByText(`@${memberName}`)).toBeVisible({ timeout: 10000 });
      await pageOwner.getByTitle('Remove Friend').click();
      // Confirm friend was removed
      await expect(pageOwner.getByText(/no friends added yet/i)).toBeVisible({ timeout: 5000 });

      // 7. Navigate back to the group — member is now a non-friend → Temp Friend badge
      await pageOwner.goto(groupUrl);
      await expect(pageOwner).toHaveURL(/\/groups\/\d+/, { timeout: 10000 });

      // The member should now show the "Temp Friend" badge
      await expect(
        pageOwner.locator('span', { hasText: 'Temp Friend' }),
      ).toBeVisible({ timeout: 10000 });
    } finally {
      await ctxOwner.close();
      await ctxMember.close();
    }
  });

  /**
   * AC#2: User with a non-zero balance attempts to leave the group and is blocked by the UI.
   *
   * Setup: Two users (A=payer, B=debtor) share a group with an expense.
   * UserB (debtor) has a non-zero balance and cannot leave.
   * The leave modal shows an outstanding balance warning with the Leave button disabled.
   */
  test('User with non-zero balance is blocked from leaving group', async ({ browser }) => {
    const ts = Date.now();
    const payerName = `payer_exit_${ts}`;
    const debtorName = `debtor_exit_${ts}`;

    const ctxPayer = await browser.newContext();
    const ctxDebtor = await browser.newContext();
    const pagePayer = await ctxPayer.newPage();
    const pageDebtor = await ctxDebtor.newPage();

    try {
      // 1. Register both users
      await registerUser(pagePayer, payerName, 'Payer', 'User');
      await registerUser(pageDebtor, debtorName, 'Debtor', 'User');

      // 2. Login both
      await loginUser(pagePayer, payerName);
      await loginUser(pageDebtor, debtorName);

      // 3. Make them friends: payer → debtor
      await sendFriendRequest(pagePayer, debtorName);
      await acceptFriendRequest(pageDebtor, payerName);

      // 4. Payer creates group with Debtor included
      const groupName = `Debt Group ${ts}`;
      const groupUrl = await createGroupWithMember(pagePayer, groupName, 'Debtor');

      // 5. Payer creates an expense (equal split): $20 → each owes $10
      //    Payer paid, so Debtor owes Payer $10 (Debtor balance = -10)
      await pagePayer.goto('/groups');
      const groupCard = pagePayer.locator('.bg-white', { hasText: groupName });
      await groupCard.getByRole('button', { name: /add expense/i }).click();

      const expenseModal = pagePayer.getByRole('dialog', { name: /add new expense/i });
      await expect(expenseModal).toBeVisible();
      await expenseModal.getByLabel(/description/i).fill('Dinner');
      await expenseModal.getByLabel(/amount/i).fill('20.00');
      await expenseModal.getByRole('button', { name: /add expense/i }).click();
      await expect(expenseModal).not.toBeVisible({ timeout: 10000 });

      // 6. Debtor navigates to the group details
      await pageDebtor.goto(groupUrl);
      await expect(pageDebtor).toHaveURL(/\/groups\/\d+/, { timeout: 10000 });
      await expect(pageDebtor.getByText(groupName)).toBeVisible();

      // 7. Debtor clicks "Leave Group"
      await pageDebtor.getByRole('button', { name: /leave group/i }).click();
      const leaveModal = pageDebtor.getByRole('dialog');
      await expect(leaveModal).toBeVisible();

      // 8. Verify the outstanding balance warning is shown
      await expect(
        leaveModal.getByText(/cannot leave this group while you have an outstanding balance/i),
      ).toBeVisible({ timeout: 10000 });

      // 9. Verify the "Leave Group" confirm button is disabled
      const confirmLeaveBtn = leaveModal.getByRole('button', { name: /^leave group$/i });
      await expect(confirmLeaveBtn).toBeDisabled();
    } finally {
      await ctxPayer.close();
      await ctxDebtor.close();
    }
  });

  /**
   * AC#3: User with zero balance successfully leaves the group.
   *
   * A solo user (no expenses, balance = 0) creates a group and leaves it.
   * Verifies redirect to /groups and group no longer appears.
   */
  test('User with zero balance successfully leaves group', async ({ page }) => {
    const ts = Date.now();
    const username = `zero_exit_${ts}`;

    // 1. Register and login
    await registerUser(page, username, 'Zero', 'Exit');
    await loginUser(page, username);

    // 2. Create a group (solo, no expenses)
    await page.goto('/groups');
    await page.getByRole('button', { name: /create group/i }).first().click();
    const modal = page.getByRole('dialog');
    const groupName = `Zero Balance Group ${ts}`;
    await modal.getByLabel(/group name/i).fill(groupName);
    await modal.getByRole('button', { name: /create group/i }).click();
    await expect(modal).not.toBeVisible({ timeout: 10000 });

    // 3. Navigate to group details
    await expect(page.getByText(groupName)).toBeVisible({ timeout: 10000 });
    await page.getByText(groupName).click();
    await expect(page).toHaveURL(/\/groups\/\d+/, { timeout: 10000 });

    // 4. Click "Leave Group"
    await page.getByRole('button', { name: /leave group/i }).click();
    const leaveModal = page.getByRole('dialog');
    await expect(leaveModal).toBeVisible();

    // 5. Verify confirmation prompt (no outstanding balance warning)
    await expect(
      leaveModal.getByText(/are you sure you want to leave this group\?/i),
    ).toBeVisible();

    // 6. Confirm leave
    await leaveModal.getByRole('button', { name: /^leave group$/i }).click();

    // 7. Verify redirect and group is gone from list
    await expect(page).toHaveURL(/\/groups/, { timeout: 10000 });
    await expect(page.getByText(groupName)).not.toBeVisible();
  });
});
