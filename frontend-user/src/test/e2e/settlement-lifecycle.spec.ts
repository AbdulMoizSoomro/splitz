import { test, expect, type Page } from "@playwright/test";

async function registerAndLogin(page: Page, username: string, firstName: string) {
  const email = `${username}@example.com`;
  const password = "Password123!";

  await page.goto("/register");
  await page.locator('#firstName').fill(firstName);
  await page.locator('#lastName').fill("User");
  await page.locator('#username').fill(username);
  await page.locator('#email').fill(email);
  await page.locator('#password').fill(password);
  await page.getByRole("button", { name: /register/i }).click();

  await expect(page).toHaveURL(/\/login/);
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(password);
  await page.getByRole("button", { name: /login/i }).click();
  await expect(page).toHaveURL(/\/$/);
}

async function sendFriendRequest(page: Page, friendUsername: string) {
  await page.goto("/");
  const searchInput = page.getByPlaceholder(/search by name or email/i);
  await searchInput.fill(friendUsername);
  await page.getByRole("button", { name: /add friend/i }).click();
}

async function acceptFriendRequest(page: Page, requesterName: string) {
  console.log(`Accepting request from ${requesterName}...`);
  await page.goto("/friends");
  await page.getByText(new RegExp(requesterName, "i")).waitFor({ state: 'visible', timeout: 10000 });
  await page.getByTitle("Accept").last().click();
}

test.describe("[E2E] Settlement Lifecycle", () => {
  test("Alice pays Bob and Bob confirms", async ({ browser }) => {
    test.setTimeout(60000);
    const ts = Date.now();
    const aliceName = `alice_${ts}`;
    const bobName = `bob_${ts}`;

    const ctxAlice = await browser.newContext();
    const ctxBob = await browser.newContext();
    const pageAlice = await ctxAlice.newPage();
    const pageBob = await ctxBob.newPage();

    try {
      console.log("Registering Alice...");
      await registerAndLogin(pageAlice, aliceName, "Alice");
      console.log("Registering Bob...");
      await registerAndLogin(pageBob, bobName, "Bob");

      console.log("Alice sending friend request to Bob...");
      await sendFriendRequest(pageAlice, bobName);
      console.log("Bob accepting Alice's friend request...");
      await acceptFriendRequest(pageBob, aliceName);

      console.log("Alice creating group...");
      await pageAlice.goto("/groups");
      await pageAlice.getByRole("button", { name: /create group/i }).first().click();
      const groupModal = pageAlice.getByRole("dialog");
      const groupName = `Settlement Group ${ts}`;
      await groupModal.locator('#group-name').fill(groupName);
      await expect(groupModal.getByText("Bob User")).toBeVisible();
      await groupModal.getByText("Bob User").click();
      await groupModal.getByRole("button", { name: /create group/i }).click();
      await expect(groupModal).not.toBeVisible();

      console.log("Alice adding expense...");
      await pageAlice.getByText(groupName).click();
      await pageAlice.getByRole("button", { name: /add expense/i }).first().click();
      const expenseModal = pageAlice.getByRole("dialog");
      await expenseModal.locator('#description').fill("Dinner");
      await expenseModal.locator('#amount').fill("40.00");
      await expenseModal.getByRole("button", { name: /add expense/i }).click();
      await expect(expenseModal).not.toBeVisible();

      // Alice should see Bob owes her $20
      console.log("Verifying Alice sees debt...");
      await pageAlice.reload();
      await expect(pageAlice.getByText(/you are owed/i)).toBeVisible({ timeout: 10000 });

      console.log("Bob paying Alice...");
      await pageBob.goto("/groups");
      await pageBob.getByText(groupName).click();
      await pageBob.getByRole("tab", { name: /balances/i }).click();
      
      await expect(pageBob.getByText(/you owe/i).first()).toBeVisible();
      
      await pageBob.getByRole("button", { name: /settle/i }).first().click();
      const settleModal = pageBob.getByRole("dialog", { name: /record payment/i });
      await expect(settleModal).toBeVisible();
      await settleModal.getByRole("button", { name: /confirm & mark paid/i }).click();
      await expect(settleModal).not.toBeVisible();

      // 4. Bob sees "Waiting for confirmation"
      console.log("Verifying Bob sees waiting status...");
      await expect(pageBob.getByText(/waiting for confirmation/i)).toBeVisible();

      // IMPORTANT: Verify that Bob now sees 0 balance in group even before confirmation
      await expect(pageBob.getByText(/you don't owe anything/i)).toBeVisible();

      console.log("Alice confirming receipt...");
      await pageAlice.reload();
      await pageAlice.getByRole("tab", { name: /balances/i }).click();
      
      await expect(pageAlice.getByText(/waiting for your confirmation/i)).toBeVisible();
      await pageAlice.getByRole("button", { name: /confirm receipt/i }).click();
      
      console.log("Verifying final balances...");
      await expect(pageAlice.getByText(/you don't owe anything/i)).toBeVisible();

      await pageBob.reload();
      await pageBob.getByRole("tab", { name: /balances/i }).click();
      await expect(pageBob.getByText(/you don't owe anything/i)).toBeVisible();

      console.log("Verifying Friend Detail Page...");
      await pageAlice.goto("/friends");
      await pageAlice.getByText(/Bob User/i).click();
      await expect(pageAlice.getByText(/you are all settled up/i)).toBeVisible();
      
      console.log("Test completed successfully!");

    } finally {
      await ctxAlice.close();
      await ctxBob.close();
    }
  });
});
