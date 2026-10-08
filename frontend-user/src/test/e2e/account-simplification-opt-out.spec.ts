import { openGroupDetails } from "./helpers/navigation";
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
  // UserSearch lives on /friends, not the dashboard.
  await page.goto("/friends");
  const searchInput = page.getByPlaceholder(/search by name or email/i);
  await searchInput.fill(friendUsername);
  const addButton = page.getByRole("button", { name: /add friend/i }).first();
  await expect(addButton).toBeVisible({ timeout: 15000 });
  await addButton.click();
}

async function acceptFriendRequest(page: Page) {
  await page.goto("/friends");
  const acceptButton = page.getByTitle("Accept").first();
  await expect(acceptButton).toBeVisible({ timeout: 20000 });
  await acceptButton.click();
  // Confirm the request is gone from the pending list.
  await expect(page.getByTitle("Accept")).toHaveCount(0, { timeout: 20000 });
}

async function toggleSwitch(locator: Locator) {
  await locator.scrollIntoViewIfNeeded();
  await locator.dispatchEvent("click");
}

/**
 * The account-level opt-out is a hard override rather than an inherited default (issue #75): once a
 * user opts out in Settings they leave the netting set of every group they belong to, including
 * groups that were configured before they changed it.
 */
test.describe("[E2E] Account-Level Debt Simplification Opt-Out", () => {

  test("account opt-out overrides netting in a group configured before the opt-out", async ({
    browser,
  }) => {
    const ts = Date.now();
    const aliceName = `alice_acct_${ts}`;
    const bobName = `bob_acct_${ts}`;

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
      await acceptFriendRequest(pageBob);

      console.log("Alice creating the group and enabling netting...");
      await pageAlice.goto("/groups");
      await pageAlice.getByRole("button", { name: /create group/i }).first().click();
      const groupModal = pageAlice.getByRole("dialog");
      const groupName = `Override Group ${ts}`;
      await groupModal.locator("#group-name").fill(groupName);
      await expect(groupModal.getByText("Bob User")).toBeVisible({ timeout: 10000 });
      await groupModal.getByText("Bob User").click();
      await groupModal.getByRole("button", { name: /create group/i }).click();
      await expect(groupModal).not.toBeVisible({ timeout: 10000 });

      // Open the group so the expense form is reachable.
      await openGroupDetails(pageAlice, groupName);
      await expect(pageAlice.getByRole("tab", { name: /balances/i })).toBeVisible({
        timeout: 20000,
      });

      console.log("Alice adding an expense so real netting debt exists...");
      await pageAlice.getByRole("button", { name: /add expense/i }).first().click();
      const expenseModal = pageAlice.getByRole("dialog");
      await expenseModal.locator("#description").fill("Override Outing");
      await expenseModal.locator("#amount").fill("100.00");
      await expenseModal.getByRole("button", { name: /add expense/i }).click();
      await expect(expenseModal).not.toBeVisible({ timeout: 10000 });

      // BASELINE. Assert a suggested settlement actually exists before the opt-out. Without this
      // the later "excluded" assertion could pass vacuously against a plan that never netted.
      console.log("BASELINE: confirming a settlement plan exists while Bob is opted in...");
      await pageAlice.getByRole("tab", { name: /balances/i }).click();
      // Scoping to the card is what makes the later "excluded" assertion trustworthy: this card
      // renders null when its query errors, so seeing it proves the plan really loaded.
      const planCard = pageAlice.getByTestId("simplification-plan-card");
      await expect(planCard).toBeVisible({ timeout: 20000 });
      await expect(planCard.getByText(/Suggested Settlement Plan/i)).toBeVisible({ timeout: 10000 });
      await expect(planCard.getByText(new RegExp(bobName, "i"))).toBeVisible({ timeout: 20000 });
      // $50 appears both in the transaction row and in the Total Debt Volume metric.
      await expect(planCard.getByText(/\$50\.00/).first()).toBeVisible({ timeout: 20000 });
      await expect(planCard.getByText(/opted out of debt netting/i)).toHaveCount(0, {
        timeout: 10000,
      });

      console.log("Bob opting out account-wide, in a group configured before it...");
      await pageBob.goto("/settings");
      const accountSwitch = pageBob.getByTestId("account-opt-out-switch");
      await expect(accountSwitch).toBeVisible({ timeout: 15000 });
      // Starts opted in.
      await expect(accountSwitch).toHaveAttribute("aria-checked", "false", { timeout: 10000 });
      await toggleSwitch(accountSwitch);
      await expect(pageBob.getByTestId("account-opt-out-notice")).toBeVisible({ timeout: 15000 });

      console.log("Verifying the group plan reports the account-level override...");
      await pageAlice.reload();
      await pageAlice.getByRole("tab", { name: /balances/i }).click();
      const planCardAfter = pageAlice.getByTestId("simplification-plan-card");
      await expect(planCardAfter).toBeVisible({ timeout: 20000 });
      await expect(planCardAfter.getByText(/opted out of debt netting/i)).toBeVisible({
        timeout: 20000,
      });
      // Bob is no longer netted: his suggested transaction is gone from the plan.
      await expect(planCardAfter.getByText(new RegExp(bobName, "i"))).toHaveCount(0, {
        timeout: 15000,
      });
      await expect(
        planCardAfter.getByText(/all balances are settled/i),
      ).toBeVisible({ timeout: 15000 });

      console.log("Verifying Bob's group view explains the exclusion came from Settings...");
      await pageBob.goto("/groups");
      await openGroupDetails(pageBob, groupName);
      await pageBob.getByRole("tab", { name: /balances/i }).click();
      await expect(pageBob.getByText(/opted out account-wide in Settings/i)).toBeVisible({
        timeout: 15000,
      });

      console.log("Bob opting back in account-wide and returning to netting...");
      await pageBob.goto("/settings");
      await toggleSwitch(pageBob.getByTestId("account-opt-out-switch"));
      await expect(pageBob.getByTestId("account-opt-out-notice")).not.toBeVisible({
        timeout: 15000,
      });

      await pageBob.goto("/groups");
      await openGroupDetails(pageBob, groupName);
      await pageBob.getByRole("tab", { name: /balances/i }).click();
      const planCardBack = pageBob.getByTestId("simplification-plan-card");
      await expect(planCardBack).toBeVisible({ timeout: 20000 });
      // Bob is the payer, so he sees the Settle control again once netting applies to him.
      // Matched loosely: the button's accessible name comes from aria-label
      // ("Settle 50.00 to <user>"), which overrides its visible "Settle" text.
      await expect(planCardBack.getByRole("button", { name: /Settle/i })).toBeVisible({
        timeout: 20000,
      });
      await expect(pageBob.getByText(/opted out account-wide in Settings/i)).not.toBeVisible({
        timeout: 15000,
      });
    } finally {
      await ctxAlice.close();
      await ctxBob.close();
    }
  });

  test("the Settings nav link resolves to a real page", async ({ browser }) => {
    const ts = Date.now();
    const carolName = `carol_nav_${ts}`;

    const ctx = await browser.newContext();
    const page = await ctx.newPage();

    try {
      await registerUser(page, carolName, "Carol", "User");
      await loginUser(page, carolName);

      console.log("Clicking the Settings sidebar link...");
      await page.goto("/");
      await page.getByRole("link", { name: /settings/i }).first().click();
      await expect(page).toHaveURL(/\/settings$/, { timeout: 15000 });
      await expect(page.getByRole("heading", { name: /^settings$/i })).toBeVisible({
        timeout: 15000,
      });
      await expect(page.getByTestId("account-opt-out-switch")).toBeVisible({ timeout: 15000 });
    } finally {
      await ctx.close();
    }
  });
});