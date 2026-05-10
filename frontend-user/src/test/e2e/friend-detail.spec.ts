import { test, expect, type Page } from "@playwright/test";

const PASSWORD = "Password123!";

async function registerUser(
  page: Page,
  username: string,
  firstName: string,
  lastName: string,
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

async function sendFriendRequest(pageA: Page, targetUsername: string) {
  await pageA.goto("/friends");
  await pageA.getByPlaceholder(/search by name or email/i).fill(targetUsername);
  await expect(pageA.getByText(`@${targetUsername}`)).toBeVisible({
    timeout: 10000,
  });
  await pageA.getByRole("button", { name: /add friend/i }).click();
  await expect(pageA.getByText(/pending/i)).toBeVisible({ timeout: 5000 });
}

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

async function createGroupWithMember(
  pageOwner: Page,
  groupName: string,
  friendDisplayFirstName: string,
): Promise<string> {
  await pageOwner.goto("/groups");
  await pageOwner
    .getByRole("button", { name: /create group/i })
    .first()
    .click();
  const modal = pageOwner.getByRole("dialog");
  await expect(modal).toBeVisible();
  await modal.getByLabel(/group name/i).fill(groupName);

  const friendPicker = modal.locator(".max-h-48");
  const friendEntry = friendPicker.getByText(
    new RegExp(friendDisplayFirstName, "i"),
  );
  await expect(friendEntry).toBeVisible({ timeout: 10000 });
  await friendEntry.first().click();

  await modal.getByRole("button", { name: /create group/i }).click();
  await expect(modal).not.toBeVisible({ timeout: 10000 });

  await expect(pageOwner.getByText(groupName)).toBeVisible({ timeout: 10000 });
  await pageOwner.getByText(groupName).click();
  await expect(pageOwner).toHaveURL(/\/groups\/\d+/, { timeout: 10000 });

  return pageOwner.url();
}

test.describe("[E2E] Friend Detail Page", () => {
  test("should navigate to friend detail page and show basic info, groups, and expenses", async ({
    browser,
  }) => {
    const ts = Date.now();
    const aliceName = `alice_${ts}`;
    const bobName = `bob_${ts}`;

    const ctxAlice = await browser.newContext();
    const ctxBob = await browser.newContext();
    const pageAlice = await ctxAlice.newPage();
    const pageBob = await ctxBob.newPage();

    try {
      // 1. Register Alice and Bob
      await registerUser(pageAlice, aliceName, "Alice", "User");
      await registerUser(pageBob, bobName, "Bob", "User");

      // 2. Login both
      await loginUser(pageAlice, aliceName);
      await loginUser(pageBob, bobName);

      // 3. Make them friends
      await sendFriendRequest(pageAlice, bobName);
      await acceptFriendRequest(pageBob, aliceName);

      // 4. Create a shared group
      const groupName = `Shared Group ${ts}`;
      await createGroupWithMember(pageAlice, groupName, "Bob");

      // 5. Alice navigates to Bob's detail page from friends list
      await pageAlice.goto("/friends");
      const friendItem = pageAlice.getByText("Bob User");
      await expect(friendItem).toBeVisible();
      await friendItem.click();

      // 6. Assert URL and basic info
      await expect(pageAlice).toHaveURL(/\/friends\/\d+/);
      await expect(
        pageAlice.getByRole("heading", { name: "Bob User" }),
      ).toBeVisible();
      await expect(pageAlice.getByText(`@${bobName}`)).toBeVisible();
      await expect(pageAlice.getByText(`${bobName}@example.com`)).toBeVisible();

      // 7. Assert Shared Groups
      await expect(pageAlice.getByText("Shared Groups")).toBeVisible();
      await expect(pageAlice.getByText(groupName)).toBeVisible();

      // 8. Alice creates an expense in the shared group
      await pageAlice.goto("/groups");
      const groupCard = pageAlice.locator(".bg-white", { hasText: groupName });
      await groupCard.getByRole("button", { name: /add expense/i }).click();

      const expenseModal = pageAlice.getByRole("dialog", {
        name: /add new expense/i,
      });
      await expect(expenseModal).toBeVisible();
      await expenseModal.getByLabel(/description/i).fill("Pizza Party");
      await expenseModal.getByLabel(/amount/i).fill("40.00");
      await expenseModal.getByRole("button", { name: /add expense/i }).click();
      await expect(expenseModal).not.toBeVisible({ timeout: 10000 });

      // 9. Navigate back to Bob's detail page
      await pageAlice.goto("/friends");
      await pageAlice.getByText("Bob User").click();
      await expect(pageAlice).toHaveURL(/\/friends\/\d+/);

      // 10. Assert Shared Expenses
      await expect(pageAlice.getByText("Shared Expenses")).toBeVisible();
      await expect(pageAlice.getByText("Pizza Party")).toBeVisible();
    } finally {
      await ctxAlice.close();
      await ctxBob.close();
    }
  });
});
