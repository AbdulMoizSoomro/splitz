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
});
