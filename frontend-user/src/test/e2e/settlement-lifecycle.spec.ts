import { test, expect, type Page } from "@playwright/test";

async function registerAndLogin(page: Page, username: string, firstName: string) {
  const email = `${username}@example.com`;
  const password = "Password123!";

  await page.goto("/register");
  await page.getByLabel(/first name/i).fill(firstName);
  await page.getByLabel(/last name/i).fill("User");
  await page.getByLabel(/username/i).fill(username);
  await page.getByLabel(/email/i).fill(email);
  await page.getByLabel(/password/i).fill(password);
  await page.getByRole("button", { name: /register/i }).click();

  await expect(page).toHaveURL(/\/login/);
  await page.getByLabel(/username/i).fill(username);
  await page.getByLabel(/password/i).fill(password);
  await page.getByRole("button", { name: /login/i }).click();
  await expect(page).toHaveURL(/\/$/);
  await expect(
    page.getByText(new RegExp(`Hi, ${username}`, "i")),
  ).toBeVisible();
}

async function sendFriendRequest(page: Page, friendUsername: string) {
  await page.goto("/friends");
  await page.getByPlaceholder(/search by name or email/i).fill(friendUsername);
  await expect(page.getByText(`@${friendUsername}`)).toBeVisible();
  await page.getByRole("button", { name: /add friend/i }).click();
  await expect(page.getByText(/pending/i)).toBeVisible();
}

async function acceptFriendRequest(page: Page, requesterUsername: string) {
  await page.goto("/friends");
  await expect(page.getByText(`@${requesterUsername}`)).toBeVisible();
  await page.getByTitle("Accept").click();
  await expect(page.getByText(/no pending friend requests/i)).toBeVisible();
}

test.describe("[E2E] Settlement Lifecycle", () => {
  test("Alice pays Bob and Bob confirms", async ({ browser }) => {
    test.setTimeout(90000);
    const ts = Date.now();
    const aliceName = `alice_${ts}`;
    const bobName = `bob_${ts}`;

    const ctxAlice = await browser.newContext();
    const ctxBob = await browser.newContext();
    const pageAlice = await ctxAlice.newPage();
    const pageBob = await ctxBob.newPage();

    try {
      // 1. Setup users and friendship
      console.log("Registering and logging in users...");
      await registerAndLogin(pageAlice, aliceName, "Alice");
      await registerAndLogin(pageBob, bobName, "Bob");

      console.log("Sending friend request...");
      await sendFriendRequest(pageAlice, bobName);
      console.log("Accepting friend request...");
      await acceptFriendRequest(pageBob, aliceName);

      // 2. Alice creates a group and an expense where Bob owes her
      console.log("Creating group...");
      await pageAlice.goto("/groups");
      await pageAlice.getByRole("button", { name: /create group/i }).first().click();
      const groupModal = pageAlice.getByRole("dialog");
      const groupName = `Settlement Group ${ts}`;
      await groupModal.getByLabel(/group name/i).fill(groupName);
      // Wait for Bob to be available in the list
      await expect(groupModal.getByText(/Bob/i)).toBeVisible();
      await groupModal.getByText(/Bob/i).click();
      await groupModal.getByRole("button", { name: /create group/i }).click();
      await expect(groupModal).not.toBeVisible();

      console.log("Adding expense...");
      await pageAlice.getByText(groupName).click();
      await pageAlice.getByRole("button", { name: /add expense/i }).first().click();
      const expenseModal = pageAlice.getByRole("dialog");
      await expenseModal.getByLabel(/description/i).fill("Dinner");
      await expenseModal.getByLabel(/amount/i).fill("40.00");
      await expenseModal.getByRole("button", { name: /add expense/i }).click();
      await expect(expenseModal).not.toBeVisible();

      // Alice should see Bob owes her $20
      await expect(pageAlice.getByText(/you are owed/i)).toBeVisible();
      await expect(pageAlice.locator(".text-green-600", { hasText: "$20.00" })).toBeVisible();

      // 3. Bob pays Alice
      console.log("Bob paying Alice...");
      await pageBob.goto("/groups");
      await pageBob.getByText(groupName).click();
      await pageBob.getByRole("button", { name: /balances/i }).click();
      
      await expect(pageBob.getByText(/you owe/i)).toBeVisible();
      await expect(pageBob.locator(".text-red-600", { hasText: "$20.00" })).toBeVisible();
      
      await pageBob.getByRole("button", { name: /settle/i }).click();
      const settleModal = pageBob.getByRole("dialog", { name: /record payment/i });
      await expect(settleModal).toBeVisible();
      await settleModal.getByRole("button", { name: /confirm & mark paid/i }).click();
      await expect(settleModal).not.toBeVisible();

      // 4. Bob sees "Waiting for confirmation"
      await expect(pageBob.getByText(/waiting for confirmation/i)).toBeVisible();

      // 5. Alice confirms receipt from Group Activity
      console.log("Alice confirming receipt...");
      await pageAlice.reload();
      await pageAlice.getByRole("button", { name: /activity/i }).click();
      // Locate the settlement item - it has "paid" text and "Confirm Receipt" button
      const settlementItem = pageAlice.locator(".bg-blue-50\\/30", { hasText: /paid/i });
      await expect(settlementItem).toBeVisible();
      
      await settlementItem.getByRole("button", { name: /confirm receipt/i }).click();
      await expect(settlementItem.getByText(/settled/i)).toBeVisible();

      // 6. Verify balances are zero for both
      console.log("Verifying final balances...");
      await pageAlice.getByRole("button", { name: /balances/i }).click();
      await expect(pageAlice.getByText(/you don't owe anything/i)).toBeVisible();

      await pageBob.reload();
      await pageBob.getByRole("button", { name: /balances/i }).click();
      await expect(pageBob.getByText(/you don't owe anything/i)).toBeVisible();

      // 7. Verify Friend Detail Page
      console.log("Verifying Friend Detail Page...");
      await pageAlice.goto("/friends");
      await pageAlice.locator('a').filter({ hasText: 'Bob User' }).click();
      await expect(pageAlice).toHaveURL(/\/friends\/\d+/);
      
      // Wait for content to load
      await expect(pageAlice.getByText(/Net Balance/i)).toBeVisible({ timeout: 10000 });
      await expect(pageAlice.getByText(/you are all settled up/i)).toBeVisible();
      
      await pageBob.goto("/friends");
      await pageBob.locator('a').filter({ hasText: 'Alice User' }).click();
      await expect(pageBob).toHaveURL(/\/friends\/\d+/);
      await expect(pageBob.getByText(/Net Balance/i)).toBeVisible({ timeout: 10000 });
      await expect(pageBob.getByText(/you are all settled up/i)).toBeVisible();
      console.log("Test completed successfully!");

    } finally {
      await ctxAlice.close();
      await ctxBob.close();
    }
  });
});
