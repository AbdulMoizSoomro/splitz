import { test, expect, type Page } from "@playwright/test";

const PASSWORD = "Password123!";

async function registerUser(
  page: Page,
  username: string,
  firstName: string = "Test",
  lastName: string = "User",
) {
  await page.goto("/register");
  await page.getByLabel(/first name/i).fill(firstName);
  await page.getByLabel(/last name/i).fill(lastName);
  await page.getByLabel(/username/i).fill(username);
  await page.getByLabel(/email/i).fill(`${username}@example.com`);
  await page.getByLabel(/password/i).fill(PASSWORD);
  await page.getByRole("button", { name: /register/i }).click();
  await expect(page).toHaveURL(/\/login/, { timeout: 15000 });
}

async function loginUser(page: Page, username: string) {
  await page.goto("/login");
  await page.getByLabel(/username/i).fill(username);
  await page.getByLabel(/password/i).fill(PASSWORD);
  await page.getByRole("button", { name: /login/i }).click();
  await expect(page).toHaveURL(/\/$/, { timeout: 15000 });
}

/** Send a friend request from pageA to the given username. */
async function sendFriendRequest(pageA: Page, targetUsername: string) {
  await pageA.goto("/friends");
  await pageA.getByPlaceholder(/search by name or email/i).fill(targetUsername);
  await expect(pageA.getByText(`@${targetUsername}`)).toBeVisible({
    timeout: 10000,
  });
  await pageA.getByRole("button", { name: /add friend/i }).click();
  await expect(pageA.getByText(/pending/i)).toBeVisible({ timeout: 5000 });
}

/** Accept the first pending friend request on pageB from the given sender. */
async function acceptFriendRequest(pageB: Page, fromUsername: string) {
  await pageB.goto("/friends");
  await expect(pageB.getByText(`@${fromUsername}`)).toBeVisible({
    timeout: 10000,
  });
  await pageB.getByTitle("Accept").click();
  await expect(pageB.getByText(/no pending friend requests/i)).toBeVisible({
    timeout: 5000,
  });
}

test.describe("Leave Group", () => {
  test("should allow a member to leave a group", async ({ browser }) => {
    const timestamp = Date.now();
    const ownerName = `owner_leave_${timestamp}`;
    const memberName = `member_leave_${timestamp}`;

    const ctxOwner = await browser.newContext();
    const ctxMember = await browser.newContext();
    const pageOwner = await ctxOwner.newPage();
    const pageMember = await ctxMember.newPage();

    try {
      // 1. Register and Login both users
      const ownerFullName = `Owner_${timestamp}`;
      const memberFullName = `Member_${timestamp}`;
      await registerUser(pageOwner, ownerName, ownerFullName);
      await registerUser(pageMember, memberName, memberFullName);
      await loginUser(pageOwner, ownerName);
      await loginUser(pageMember, memberName);

      // 2. Establish friendship
      await sendFriendRequest(pageOwner, memberName);
      await acceptFriendRequest(pageMember, ownerName);

      // 3. Owner creates a group with the member
      await pageOwner.goto("/groups");
      await pageOwner
        .getByRole("button", { name: /create group/i })
        .first()
        .click();
      let modal = pageOwner.getByRole("dialog");
      const groupName = `Leave Test Group ${timestamp}`;
      await modal.getByLabel(/group name/i).fill(groupName);

      // Select member in picker
      await modal
        .locator(".max-h-48")
        .getByText(memberFullName)
        .first()
        .click();

      await modal
        .getByRole("button", { name: "Create Group", exact: true })
        .click();
      await expect(modal).not.toBeVisible();

      // 4. Member navigates to the group
      await pageMember.goto("/groups");
      await expect(pageMember.getByText(groupName)).toBeVisible({
        timeout: 10000,
      });
      await pageMember.getByText(groupName).click();
      await expect(pageMember).toHaveURL(/\/groups\/\d+/);

      // 5. Member clicks "Leave Group"
      await pageMember.getByRole("button", { name: /leave group/i }).click();
      modal = pageMember.getByRole("dialog");
      await expect(modal).toBeVisible();
      await expect(
        modal.getByText(/Are you sure you want to leave this group\?/i),
      ).toBeVisible();

      // 6. Confirm Leave
      await modal.getByRole("button", { name: /^leave group$/i }).click();
      await expect(modal).not.toBeVisible({ timeout: 10000 });

      // 7. Verify redirect and group absence for member
      await expect(pageMember).toHaveURL(/\/groups/, { timeout: 15000 });
      await expect(
        pageMember.getByRole("heading", { name: /your groups/i }),
      ).toBeVisible({ timeout: 15000 });

      // Look for the group name specifically in an h3 (the card title) to avoid matching other text
      await expect(
        pageMember.locator("h3").filter({ hasText: groupName }),
      ).not.toBeVisible({ timeout: 15000 });
    } finally {
      await ctxOwner.close();
      await ctxMember.close();
    }
  });
});
