import { test, expect, type Page } from "@playwright/test";

async function registerAndLogin(page: Page, username: string, firstName: string) {
  const email = `${username}@example.com`;
  const password = "Password123!";

  await page.goto("/register");
  await page.locator("#firstName").fill(firstName);
  await page.locator("#lastName").fill("User");
  await page.locator("#username").fill(username);
  await page.locator("#email").fill(email);
  await page.locator("#password").fill(password);
  await page.getByRole("button", { name: /register/i }).click();

  await expect(page).toHaveURL(/\/login/);
  await page.locator("#username").fill(username);
  await page.locator("#password").fill(password);
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
  await page.goto("/friends");
  await page.getByText(new RegExp(requesterName, "i")).waitFor({ state: "visible", timeout: 10000 });
  await page.getByTitle("Accept").last().click();
}

test.describe("[E2E] Debt Simplification & Opt-Out Governance", () => {
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
      await registerAndLogin(pageAlice, aliceName, "Alice");
      await registerAndLogin(pageBob, bobName, "Bob");

      console.log("Connecting Alice & Bob as friends...");
      await sendFriendRequest(pageAlice, bobName);
      await acceptFriendRequest(pageBob, aliceName);

      console.log("Alice creating group...");
      await pageAlice.goto("/groups");
      await pageAlice.getByRole("button", { name: /create group/i }).first().click();
      const groupModal = pageAlice.getByRole("dialog");
      const groupName = `Simplification Group ${ts}`;
      await groupModal.locator("#group-name").fill(groupName);
      await expect(groupModal.getByText("Bob User")).toBeVisible();
      await groupModal.getByText("Bob User").click();
      await groupModal.getByRole("button", { name: /create group/i }).click();
      await expect(groupModal).not.toBeVisible();

      console.log("Alice adding an expense...");
      await pageAlice.getByText(groupName).click();
      await pageAlice.getByRole("button", { name: /add expense/i }).first().click();
      const expenseModal = pageAlice.getByRole("dialog");
      await expenseModal.locator("#description").fill("Group Outing");
      await expenseModal.locator("#amount").fill("100.00");
      await expenseModal.getByRole("button", { name: /add expense/i }).click();
      await expect(expenseModal).not.toBeVisible();

      console.log("Navigating Bob to Balances tab...");
      await pageBob.goto("/groups");
      await pageBob.getByText(groupName).click();
      await pageBob.getByRole("tab", { name: /balances/i }).click();

      console.log("Verifying Suggested Settlement Plan & Governance Cards...");
      await expect(pageBob.getByText(/Suggested Settlement Plan/i)).toBeVisible({ timeout: 10000 });
      await expect(pageBob.getByText(/Debt Simplification & Governance/i)).toBeVisible();

      console.log("Bob toggles self opt-out...");
      const optOutSwitch = pageBob.getByLabel(/Opt out of debt simplification/i);
      await expect(optOutSwitch).toBeVisible();
      await optOutSwitch.click();
      await expect(pageBob.getByText(/Opt-Out Active/i)).toBeVisible({ timeout: 5000 });

      console.log("Bob toggles opt-out back off...");
      await optOutSwitch.click();
      await expect(pageBob.getByText(/Opt-Out Active/i)).not.toBeVisible({ timeout: 5000 });

      console.log("Alice checking Admin Governance scope buttons...");
      await pageAlice.goto("/groups");
      await pageAlice.getByText(groupName).click();
      await pageAlice.getByRole("tab", { name: /balances/i }).click();

      await expect(pageAlice.getByLabel(/Select Intra-Group Scope/i)).toBeVisible();
      await expect(pageAlice.getByLabel(/Select Global Cross-Group Scope/i)).toBeVisible();

      await pageAlice.getByLabel(/Select Global Cross-Group Scope/i).click();
      await expect(pageAlice.getByText(/Global Cross-Group Netting/i).first()).toBeVisible({ timeout: 5000 });

    } finally {
      await ctxAlice.close();
      await ctxBob.close();
    }
  });
});
