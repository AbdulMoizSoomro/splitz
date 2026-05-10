import { test, expect, type Page } from '@playwright/test';

const PASSWORD = 'Password123!';

async function registerUser(page: Page, username: string) {
  await page.goto('/register');
  await page.getByLabel(/first name/i).fill('Test');
  await page.getByLabel(/last name/i).fill('User');
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

/** Accept the first pending friend request on pageB from the given sender. */
async function acceptFriendRequest(pageB: Page, fromUsername: string) {
  await pageB.goto('/friends');
  await expect(pageB.getByText(`@${fromUsername}`)).toBeVisible({ timeout: 10000 });
  await pageB.getByTitle('Accept').click();
  await expect(pageB.getByText(/no pending friend requests/i)).toBeVisible({ timeout: 5000 });
}

/**
 * As Owner (pageOwner), create a group named `groupName` and include a friend
 * (identified by clicking their name in the friend picker in CreateGroupModal).
 * Returns the group URL after navigating to group details.
 */
async function createGroupWithMember(
  pageOwner: Page,
  groupName: string,
): Promise<string> {
  await pageOwner.goto('/groups');
  await pageOwner.getByRole('button', { name: /create group/i }).first().click();
  const modal = pageOwner.getByRole('dialog');
  await expect(modal).toBeVisible();
  await modal.getByLabel(/group name/i).fill(groupName);

  // The friend list shows "Test User" — click the first available friend to add them
  const friendPicker = modal.locator('.max-h-48');
  await expect(friendPicker.getByText(/test user/i)).toBeVisible({ timeout: 10000 });
  await friendPicker.getByText(/test user/i).first().click();

  await modal.getByRole('button', { name: /create group/i }).click();
  await expect(modal).not.toBeVisible({ timeout: 10000 });

  // Navigate to the created group
  await expect(pageOwner.getByText(groupName)).toBeVisible({ timeout: 10000 });
  await pageOwner.getByText(groupName).click();
  await expect(pageOwner).toHaveURL(/\/groups\/\d+/, { timeout: 10000 });

  return pageOwner.url();
}

test.describe('[E2E] Group Roles and Permissions', () => {
  /**
   * AC#1: Owner promotes a Member to Admin and verifies UI permissions
   * change for the new Admin.
   *
   * Setup: Owner and Member are friends. Owner creates group with Member.
   * Owner promotes Member → Admin.
   * As the newly promoted Admin, they see "Admin" badge on the group page.
   */
  test('Owner promotes a Member to Admin; new Admin sees Admin badge', async ({
    browser,
  }) => {
    const ts = Date.now();
    const ownerName = `owner_promo_${ts}`;
    const memberName = `member_promo_${ts}`;

    const ctxOwner = await browser.newContext();
    const ctxMember = await browser.newContext();
    const pageOwner = await ctxOwner.newPage();
    const pageMember = await ctxMember.newPage();

    try {
      // 1. Register both users
      await registerUser(pageOwner, ownerName);
      await registerUser(pageMember, memberName);

      // 2. Login both users
      await loginUser(pageOwner, ownerName);
      await loginUser(pageMember, memberName);

      // 3. Establish friendship: owner sends, member accepts
      await sendFriendRequest(pageOwner, memberName);
      await acceptFriendRequest(pageMember, ownerName);

      // 4. Owner creates group with the member included
      const groupName = `Promo Group ${ts}`;
      const groupUrl = await createGroupWithMember(pageOwner, groupName);

      // 5. Navigate to Members tab
      await pageOwner.getByRole('button', { name: 'Members', exact: true }).click();

      // 6. Verify member row shows "Member" badge (not Admin yet)
      //    Use the Members card to scope our badge search
      const membersCard = pageOwner.locator('.divide-y');
      await expect(membersCard.locator('span', { hasText: 'Member' }).first()).toBeVisible();
      await expect(membersCard.locator('span', { hasText: 'Admin' })).not.toBeVisible();

      // 7. Owner promotes the member to Admin via the "Manage role" dropdown
      //    (visible for all non-owner members when actor is Owner or Admin)
      const manageRoleBtn = pageOwner.getByLabel('Manage role');
      await expect(manageRoleBtn).toBeVisible();
      await manageRoleBtn.click();
      const promoteOption = pageOwner.getByText(/promote to admin/i);
      await expect(promoteOption).toBeVisible();
      await promoteOption.click();

      // 8. Owner's view: badge should now say "Admin"
      await expect(membersCard.locator('span', { hasText: 'Admin' })).toBeVisible({ timeout: 10000 });
      await expect(membersCard.locator('span', { hasText: 'Member' })).not.toBeVisible();

      // 9. Member navigates to the group page and confirms their Admin badge is visible
      await pageMember.goto(groupUrl);
      await expect(pageMember).toHaveURL(/\/groups\/\d+/, { timeout: 10000 });
      
      // Navigate to Members tab
      await pageMember.getByRole('button', { name: 'Members', exact: true }).click();

      const membersMemberCard = pageMember.locator('.divide-y');
      await expect(membersMemberCard.locator('span', { hasText: 'Admin' })).toBeVisible({
        timeout: 10000,
      });

      // 10. The new Admin also sees the Owner badge for the group creator
      await expect(membersMemberCard.locator('span', { hasText: 'Owner' })).toBeVisible();
    } finally {
      await ctxOwner.close();
      await ctxMember.close();
    }
  });

  /**
   * AC#2: Owner restricts member management and verifies role management
   * controls are absent for regular members.
   *
   * The governance toggle defaults to ON (allowMembersToManageMembers = true).
   * A regular Member (not admin, not owner) never sees the "Manage role" dropdown
   * regardless of this setting — it's gated by isAdmin || isOwner in the UI.
   * This test: Owner creates group with Member, Owner turns governance toggle OFF,
   * and verifies the Member still cannot see any role management controls.
   */
  test('Owner restricts member management; regular member cannot see role management controls', async ({
    browser,
  }) => {
    const ts = Date.now();
    const ownerName = `owner_gov_${ts}`;
    const memberName = `member_gov_${ts}`;

    const ctxOwner = await browser.newContext();
    const ctxMember = await browser.newContext();
    const pageOwner = await ctxOwner.newPage();
    const pageMember = await ctxMember.newPage();

    try {
      // 1. Register and login both users
      await registerUser(pageOwner, ownerName);
      await registerUser(pageMember, memberName);
      await loginUser(pageOwner, ownerName);
      await loginUser(pageMember, memberName);

      // 2. Establish friendship
      await sendFriendRequest(pageOwner, memberName);
      await acceptFriendRequest(pageMember, ownerName);

      // 3. Owner creates group with member
      const groupName = `Gov Group ${ts}`;
      const groupUrl = await createGroupWithMember(pageOwner, groupName);

      // 4. Owner verifies "Group Settings" section is visible (owner-only)
      await expect(pageOwner.getByText(/group settings/i)).toBeVisible();

      // 5. The governance toggle defaults to ON (allowMembersToManageMembers = true).
      //    Owner turns it OFF to restrict member management.
      const toggleBtn = pageOwner.getByLabel(/toggle allow members to manage members/i);
      await expect(toggleBtn).toBeVisible();
      const toggleThumb = toggleBtn.locator('span');

      // Default is ON: thumb should be at translate-x-6
      await expect(toggleThumb).toHaveClass(/translate-x-6/);

      // Click to turn OFF
      await toggleBtn.click();
      // Now OFF: thumb should be at translate-x-1
      await expect(toggleThumb).toHaveClass(/translate-x-1/, { timeout: 5000 });

      // 6. Member navigates to the group page
      await pageMember.goto(groupUrl);
      await expect(pageMember).toHaveURL(/\/groups\/\d+/, { timeout: 10000 });
      await expect(pageMember.getByText(groupName)).toBeVisible();

      // 7. Member should NOT see "Group Settings" section (owner-only)
      await expect(pageMember.getByText(/group settings/i)).not.toBeVisible();

      // 8. Regular member never sees "Manage role" dropdown (requires Admin or Owner role)
      // Navigate to Members tab first
      await pageMember.getByRole('button', { name: 'Members', exact: true }).click();
      await expect(pageMember.getByLabel('Manage role')).not.toBeVisible();
    } finally {
      await ctxOwner.close();
      await ctxMember.close();
    }
  });

  /**
   * AC#3: Admin demotes another Admin back to Member.
   *
   * Setup: Owner creates a group. Owner promotes Member → Admin.
   * Then Owner demotes the promoted Admin back to Member via "Demote to Member".
   * Verify the badge reverts to "Member" on both sides.
   */
  test('Admin demotes another Admin back to Member', async ({ browser }) => {
    const ts = Date.now();
    const ownerName = `owner_demote_${ts}`;
    const adminName = `admin_demote_${ts}`;

    const ctxOwner = await browser.newContext();
    const ctxAdmin = await browser.newContext();
    const pageOwner = await ctxOwner.newPage();
    const pageAdmin = await ctxAdmin.newPage();

    try {
      // 1. Register and login both users
      await registerUser(pageOwner, ownerName);
      await registerUser(pageAdmin, adminName);
      await loginUser(pageOwner, ownerName);
      await loginUser(pageAdmin, adminName);

      // 2. Establish friendship
      await sendFriendRequest(pageOwner, adminName);
      await acceptFriendRequest(pageAdmin, ownerName);

      // 3. Owner creates group with the future admin
      const groupName = `Demote Group ${ts}`;
      const groupUrl = await createGroupWithMember(pageOwner, groupName);

      // Navigate to Members tab
      await pageOwner.getByRole('button', { name: 'Members', exact: true }).click();

      const membersCard = pageOwner.locator('.divide-y');

      // 4. Owner promotes the member to Admin
      const manageRoleBtn = pageOwner.getByLabel('Manage role');
      await expect(manageRoleBtn).toBeVisible();
      await manageRoleBtn.click();
      await pageOwner.getByText(/promote to admin/i).click();
      await expect(membersCard.locator('span', { hasText: 'Admin' })).toBeVisible({ timeout: 10000 });

      // 5. Now demote the Admin back to Member using the dropdown
      await pageOwner.getByLabel('Manage role').click();
      const demoteOption = pageOwner.getByText(/demote to member/i);
      await expect(demoteOption).toBeVisible();
      await demoteOption.click();

      // 6. Verify the badge reverts to "Member"
      await expect(membersCard.locator('span', { hasText: 'Member' }).first()).toBeVisible({
        timeout: 10000,
      });
      await expect(membersCard.locator('span', { hasText: 'Admin' })).not.toBeVisible();

      // 7. The demoted user navigates to the group and confirms their badge is "Member"
      await pageAdmin.goto(groupUrl);
      await expect(pageAdmin).toHaveURL(/\/groups\/\d+/, { timeout: 10000 });
      
      // Navigate to Members tab
      await pageAdmin.getByRole('button', { name: /members/i }).click();

      const adminMembersCard = pageAdmin.locator('.divide-y');
      await expect(adminMembersCard.locator('span', { hasText: 'Member' }).first()).toBeVisible({
        timeout: 10000,
      });

      // 8. As a regular Member again, the demoted user should NOT see "Manage role" dropdown
      await expect(pageAdmin.getByLabel('Manage role')).not.toBeVisible();
    } finally {
      await ctxOwner.close();
      await ctxAdmin.close();
    }
  });
});
