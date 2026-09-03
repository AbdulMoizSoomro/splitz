import { test, expect, type Page, type Locator } from "@playwright/test";

const PASSWORD = "Password123!";

async function registerUser(
  page: Page,
  username: string,
  firstName: string,
  lastName: string,
) {
  await page.goto("/register");
  await page.locator("#firstName").fill(firstName);
  await page.locator("#lastName").fill(lastName);
  await page.locator("#username").fill(username);
  await page.locator("#email").fill(`${username}@example.com`);
  await page.locator("#password").fill(PASSWORD);

  await page.getByRole("button", { name: /register/i }).click();
  await expect(page).toHaveURL(/\/login/, { timeout: 15000 });
}

async function loginUser(page: Page, username: string) {
  await page.goto("/login");
  await page.locator("#username").fill(username);
  await page.locator("#password").fill(PASSWORD);

  await page.getByRole("button", { name: /login/i }).click();
  await expect(page).toHaveURL(/\/$/, { timeout: 15000 });
}

async function sendFriendRequest(page: Page, friendUsername: string) {
  await page.goto("/");
  const searchInput = page.getByPlaceholder(/search by name or email/i);
  await searchInput.fill(friendUsername);
  await page.getByRole("button", { name: /add friend/i }).click();
}

async function acceptFriendRequest(page: Page, requesterName: string) {
  await page.goto("/friends");
  await page.getByText(new RegExp(requesterName, "i")).waitFor({ state: "visible", timeout: 10000 });
  await page.getByTitle("Accept").last().click();
}

async function toggleSwitch(locator: Locator) {
  await locator.scrollIntoViewIfNeeded();
  await locator.dispatchEvent("click");
}

test.describe("[E2E] Debt Simplification & Opt-Out Governance", () => {
  test.setTimeout(180000);

  test("Group Debt Simplification flow with opt-out and settlement", async ({ browser }) => {
    const ts = Date.now();
    const aliceName = `alice_simp_${ts}`;
    const bobName = `bob_simp_${ts}`;

    const ctxAlice = await browser.newContext();
    const ctxBob = await browser.newContext();
    const pageAlice = await ctxAlice.newPage();
    const pageBob = await ctxBob.newPage();

    try {
      console.log("Registering Alice & Bob...");
      await registerUser(pageAlice, aliceName, "Alice", "User");
      await registerUser(pageBob, bobName, "Bob", "User");

      await loginUser(pageAlice, aliceName);
      await loginUser(pageBob, bobName);

      console.log("Connecting Alice & Bob as friends...");
      await sendFriendRequest(pageAlice, bobName);
      await acceptFriendRequest(pageBob, aliceName);

      console.log("Alice creating group...");
      await pageAlice.goto("/groups");
      await pageAlice.getByRole("button", { name: /create group/i }).first().click();
      const groupModal = pageAlice.getByRole("dialog");
      const groupName = `Simplification Group ${ts}`;
      await groupModal.locator("#group-name").fill(groupName);
      await expect(groupModal.getByText("Bob User")).toBeVisible({ timeout: 10000 });
      await groupModal.getByText("Bob User").click();
      await groupModal.getByRole("button", { name: /create group/i }).click();
      await expect(groupModal).not.toBeVisible({ timeout: 10000 });

      console.log("Alice adding an expense...");
      await pageAlice.getByText(groupName).click();
      await pageAlice.getByRole("button", { name: /add expense/i }).first().click();
      const expenseModal = pageAlice.getByRole("dialog");
      await expenseModal.locator("#description").fill("Group Outing");
      await expenseModal.locator("#amount").fill("100.00");
      await expenseModal.getByRole("button", { name: /add expense/i }).click();
      await expect(expenseModal).not.toBeVisible({ timeout: 10000 });

      console.log("Navigating Bob to Balances tab...");
      await pageBob.goto("/groups");
      await pageBob.getByText(groupName).click();
      await pageBob.getByRole("tab", { name: /balances/i }).click();

      console.log("Verifying Suggested Settlement Plan & Governance Cards...");
      await expect(pageBob.getByText(/Suggested Settlement Plan/i)).toBeVisible({ timeout: 15000 });
      await expect(pageBob.getByText(/Debt Simplification & Governance/i)).toBeVisible({ timeout: 15000 });

      console.log("Bob toggles self opt-out...");
      const optOutSwitch = pageBob.getByTestId("user-opt-out-switch");
      await expect(optOutSwitch).toBeVisible({ timeout: 15000 });

      await toggleSwitch(optOutSwitch);
      await expect(pageBob.getByText(/Opt-Out Active/i)).toBeVisible({ timeout: 15000 });

      console.log("Bob toggles opt-out back off...");
      await toggleSwitch(optOutSwitch);
      await expect(pageBob.getByText(/Opt-Out Active/i)).not.toBeVisible({ timeout: 15000 });

      console.log("Alice checking Admin Governance scope buttons...");
      await pageAlice.goto("/groups");
      await pageAlice.getByText(groupName).click();
      await pageAlice.getByRole("tab", { name: /balances/i }).click();

      const globalScopeBtn = pageAlice.locator('[aria-label="Select Global Cross-Group Scope"]');
      await expect(globalScopeBtn).toBeVisible({ timeout: 15000 });

      await globalScopeBtn.click();
      await pageAlice.waitForTimeout(1000);

      await expect(pageAlice.getByText(/Global Cross-Group Netting/i).first()).toBeVisible({ timeout: 15000 });

    } finally {
      await ctxAlice.close();
      await ctxBob.close();
    }
  });

  test("when intra-group debt simplification is disabled, debts should revert back to original unsimplified debts", async ({
    browser,
  }) => {
    const ts = Date.now();
    const aliceName = `alice_rev_${ts}`;
    const bobName = `bob_rev_${ts}`;
    const charlieName = `charlie_rev_${ts}`;

    const ctxAlice = await browser.newContext();
    const ctxBob = await browser.newContext();
    const ctxCharlie = await browser.newContext();
    const pageAlice = await ctxAlice.newPage();
    const pageBob = await ctxBob.newPage();
    const pageCharlie = await ctxCharlie.newPage();

    try {
      // 1. Register users
      await registerUser(pageAlice, aliceName, "Alice", "User");
      await registerUser(pageBob, bobName, "Bob", "User");
      await registerUser(pageCharlie, charlieName, "Charlie", "User");

      // 2. Login users
      await loginUser(pageAlice, aliceName);
      await loginUser(pageBob, bobName);
      await loginUser(pageCharlie, charlieName);

      // 3. Connect friendships
      await sendFriendRequest(pageAlice, bobName);
      await acceptFriendRequest(pageBob, aliceName);
      await sendFriendRequest(pageAlice, charlieName);
      await acceptFriendRequest(pageCharlie, aliceName);

      // 4. Create Group
      const groupName = `Revert Simplification Group ${ts}`;
      await pageAlice.goto("/groups");
      await pageAlice.getByRole("button", { name: /create group/i }).first().click();
      const groupModal = pageAlice.getByRole("dialog");
      await groupModal.locator("#group-name").fill(groupName);
      await expect(groupModal.locator(".animate-spin")).not.toBeVisible();
      await groupModal.locator("div.cursor-pointer", { hasText: "Bob User" }).first().click();
      await groupModal.locator("div.cursor-pointer", { hasText: "Charlie User" }).first().click();
      await groupModal.getByRole("button", { name: /create group/i }).click();
      await expect(groupModal).not.toBeVisible({ timeout: 15000 });

      // 5. Add expenses creating transitive debt:
      // Alice pays $60 split 3 ways (Bob owes $20, Charlie owes $20)
      await pageAlice.getByText(groupName).click();
      await pageAlice.getByRole("button", { name: /add expense/i }).first().click();
      const expenseModal1 = pageAlice.getByRole("dialog");
      await expenseModal1.locator("#description").fill("Dinner");
      await expenseModal1.locator("#amount").fill("60.00");
      await expenseModal1.getByRole("button", { name: /add expense/i }).click();
      await expect(expenseModal1).not.toBeVisible({ timeout: 10000 });

      // Bob pays $30 split 3 ways (Alice owes $10, Charlie owes $10)
      await pageBob.goto("/groups");
      await pageBob.getByText(groupName).click();
      await pageBob.getByRole("button", { name: /add expense/i }).first().click();
      const expenseModal2 = pageBob.getByRole("dialog");
      await expenseModal2.locator("#description").fill("Snacks");
      await expenseModal2.locator("#amount").fill("30.00");
      await expenseModal2.getByRole("button", { name: /add expense/i }).click();
      await expect(expenseModal2).not.toBeVisible({ timeout: 10000 });

      // 6. Check Balances tab with simplification enabled (default):
      // Net balances: Alice +30, Bob 0, Charlie -30.
      // Transitive netting simplifies this to 1 transaction: Charlie owes Alice $30.
      await pageAlice.goto("/groups");
      await pageAlice.getByText(groupName).click();
      await pageAlice.getByRole("tab", { name: /balances/i }).click();

      await expect(pageAlice.getByText(/Suggested Settlement Plan/i)).toBeVisible({ timeout: 15000 });
      // In the simplified plan, Charlie owes Alice $30.00
      const planCard = pageAlice.locator(".border-blue-500\\/20");
      await expect(planCard.getByText(charlieName)).toBeVisible({ timeout: 10000 });
      await expect(planCard.getByText("You")).toBeVisible();
      await expect(planCard.getByText("$30.00").first()).toBeVisible();

      // 7. Admin (Alice) disables intra-group simplification
      const disableSwitch = pageAlice.getByTestId("enable-simplification-switch");
      await expect(disableSwitch).toBeVisible({ timeout: 15000 });
      await toggleSwitch(disableSwitch);

      // Verify simplification is disabled message appears
      await expect(
        pageAlice.getByText(/Debt simplification is disabled for this group by governance/i),
      ).toBeVisible({ timeout: 15000 });

      // 8. Now verify that the debts revert back to the original / raw debts!
      // In raw debts, Bob owes Alice $10, Charlie owes Bob $10, and Charlie owes Alice $20 (or similar unsimplified breakdown).
      // Check "All Raw Group Debts" card:
      // It should NOT show the single transitive $30 debt (Charlie owes Alice $30).
      // It SHOULD show Bob's debt (Bob owes Alice $10 or Charlie owes Bob $10).
      const rawDebtsCard = pageAlice.locator(".bg-card", { hasText: "All Raw Group Debts" });
      await expect(rawDebtsCard).toBeVisible();

      // Check if Bob and Charlie appear with direct debts in the raw debts:
      await expect(rawDebtsCard.getByText(new RegExp(`${bobName}.*owes.*${aliceName}`, "i"))).toBeVisible({
        timeout: 10000,
      });
      await expect(rawDebtsCard.getByText(new RegExp(`${charlieName}.*owes.*${bobName}`, "i"))).toBeVisible({
        timeout: 10000,
      });

      // Also on Bob's page: Bob should see direct debts (Bob owes Alice $10)
      await pageBob.goto("/groups");
      await pageBob.getByText(groupName).click();
      await pageBob.getByRole("tab", { name: /balances/i }).click();

      // When unsimplified, Bob owes Alice $10 (Direct), so Bob should NOT see "You don't owe anything directly!"
      await expect(pageBob.getByText(/You don't owe anything directly!/i)).not.toBeVisible({ timeout: 10000 });
      await expect(pageBob.locator(".text-destructive", { hasText: "$10.00" })).toBeVisible();
      await expect(pageBob.locator(".text-emerald-500", { hasText: "$10.00" })).toBeVisible();
    } finally {
      await ctxAlice.close();
      await ctxBob.close();
      await ctxCharlie.close();
    }
  });
});
