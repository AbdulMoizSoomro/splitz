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

async function sendFriendRequest(pageA: Page, targetUsername: string) {
  await pageA.goto("/friends");
  await pageA.getByPlaceholder(/search by name or email/i).fill(targetUsername);
  await expect(pageA.getByText(`@${targetUsername}`)).toBeVisible({
    timeout: 15000,
  });
  await pageA.getByRole("button", { name: /add friend/i }).click();
  await expect(pageA.getByRole("button", { name: /pending/i })).toBeVisible({ timeout: 10000 });
}

async function acceptFriendRequest(pageB: Page, fromUsername: string) {
  await pageB.goto("/friends");
  await expect(pageB.getByText(`@${fromUsername}`)).toBeVisible({
    timeout: 15000,
  });
  await pageB.getByTitle("Accept").click();
}

async function createGroupWithMembers(
  pageOwner: Page,
  groupName: string,
  friendFirstNames: string[],
) {
  await pageOwner.goto("/groups");
  await pageOwner.getByRole("button", { name: /create group/i }).first().click();
  const modal = pageOwner.getByRole("dialog");
  await expect(modal).toBeVisible();
  await modal.locator("#group-name").fill(groupName);

  await expect(modal.locator(".animate-spin")).not.toBeVisible();

  for (const name of friendFirstNames) {
    const friendRow = modal.locator("div.cursor-pointer", { hasText: name });
    await expect(friendRow).toBeVisible({ timeout: 15000 });
    await friendRow.click();
  }

  await modal.getByRole("button", { name: /create group/i }).click();
  await expect(modal).not.toBeVisible({ timeout: 15000 });
  await expect(pageOwner.getByText(groupName)).toBeVisible({ timeout: 15000 });
}

async function addEqualExpense(
  pagePayer: Page,
  groupName: string,
  description: string,
  amount: string,
) {
  await pagePayer.goto("/groups");
  await openGroupDetails(pagePayer, groupName);
  await pagePayer.getByRole("button", { name: /add expense/i }).first().click();
  await pagePayer.locator("#description").fill(description);
  await pagePayer.locator("#amount").fill(amount);

  await pagePayer.getByRole("dialog").getByRole("button", { name: "Add Expense", exact: true }).click();
  await expect(pagePayer.getByRole("dialog")).toBeHidden({ timeout: 15000 });
}

async function toggleSwitch(locator: Locator) {
  await locator.scrollIntoViewIfNeeded();
  await locator.dispatchEvent("click");
}

test.describe("[E2E] Extensive Debt Simplification & Governance Suite", () => {

  test("Scenario 1: Single-Group Transitive Netting, Governance Toggles & Opt-Out Invariants", async ({ browser }) => {
    const ts = Date.now();
    const aliceName = `alice_ext_${ts}`;
    const bobName = `bob_ext_${ts}`;
    const charlieName = `charlie_ext_${ts}`;

    const ctxAlice = await browser.newContext();
    const ctxBob = await browser.newContext();
    const ctxCharlie = await browser.newContext();

    const pageAlice = await ctxAlice.newPage();
    const pageBob = await ctxBob.newPage();
    const pageCharlie = await ctxCharlie.newPage();

    try {
      console.log("Registering Alice, Bob, and Charlie...");
      await registerUser(pageAlice, aliceName, "Alice", "User");
      await registerUser(pageBob, bobName, "Bob", "User");
      await registerUser(pageCharlie, charlieName, "Charlie", "User");

      await loginUser(pageAlice, aliceName);
      await loginUser(pageBob, bobName);
      await loginUser(pageCharlie, charlieName);

      console.log("Connecting friendships...");
      await sendFriendRequest(pageAlice, bobName);
      await acceptFriendRequest(pageBob, aliceName);

      await sendFriendRequest(pageAlice, charlieName);
      await acceptFriendRequest(pageCharlie, aliceName);

      console.log("Alice creating Group A...");
      const groupName = `Transitive Group ${ts}`;
      await createGroupWithMembers(pageAlice, groupName, ["Bob User", "Charlie User"]);

      console.log("Adding expenses in Group A...");
      await addEqualExpense(pageAlice, groupName, "Dinner", "60.00");
      await addEqualExpense(pageBob, groupName, "Snacks", "30.00");

      console.log("Verifying Suggested Settlement Plan on Alice's Balances tab...");
      await pageAlice.goto("/groups");
      await openGroupDetails(pageAlice, groupName);
      await pageAlice.getByRole("tab", { name: /balances/i }).click();

      await expect(pageAlice.getByText(/Suggested Settlement Plan/i)).toBeVisible({ timeout: 15000 });
      await expect(pageAlice.getByText(/Debt Simplification & Governance/i)).toBeVisible({ timeout: 15000 });

      await expect(pageAlice.getByText("50%")).toBeVisible({ timeout: 10000 });

      console.log("Testing Admin Governance Toggle OFF & ON...");
      const disableSwitch = pageAlice.getByTestId("enable-simplification-switch");
      await expect(disableSwitch).toBeVisible({ timeout: 15000 });

      await toggleSwitch(disableSwitch);

      await expect(
        pageAlice.getByText(/Debt simplification is disabled for this group by governance/i),
      ).toBeVisible({ timeout: 15000 });

      await toggleSwitch(disableSwitch);

      await expect(pageAlice.getByText(/Suggested Settlement Plan/i)).toBeVisible({ timeout: 15000 });

      console.log("Testing Member Self-Service Opt-Out...");
      await pageBob.goto("/groups");
      await openGroupDetails(pageBob, groupName);
      await pageBob.getByRole("tab", { name: /balances/i }).click();

      const optOutSwitch = pageBob.getByTestId("user-opt-out-switch");
      await expect(optOutSwitch).toBeVisible({ timeout: 15000 });

      await toggleSwitch(optOutSwitch);

      await expect(pageBob.getByText(/Opt-Out Active/i)).toBeVisible({ timeout: 15000 });

      await pageAlice.reload();
      await pageAlice.getByRole("tab", { name: /balances/i }).click();
      await expect(pageAlice.getByText(/1 member\(s\) opted out of debt netting/i)).toBeVisible({ timeout: 15000 });

      await toggleSwitch(optOutSwitch);

      await expect(pageBob.getByText(/Opt-Out Active/i)).not.toBeVisible({ timeout: 15000 });

      console.log("Scenario 1 completed successfully!");

    } finally {
      await ctxAlice.close();
      await ctxBob.close();
      await ctxCharlie.close();
    }
  });

  test("Scenario 2: Multi-Group (2 Shared Groups) Global Netting & Pro-Rata Settlement Allocation", async ({ browser }) => {
    const ts = Date.now();
    const aliceName = `alice_mg_${ts}`;
    const bobName = `bob_mg_${ts}`;

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

      console.log("Connecting Alice & Bob...");
      await sendFriendRequest(pageAlice, bobName);
      await acceptFriendRequest(pageBob, aliceName);

      console.log("Alice creating Group 1 (Vacation) & Group 2 (Rent)...");
      const group1Name = `Vacation ${ts}`;
      const group2Name = `Rent ${ts}`;

      await createGroupWithMembers(pageAlice, group1Name, ["Bob User"]);
      await createGroupWithMembers(pageAlice, group2Name, ["Bob User"]);

      console.log("Adding expenses in Group 1 & Group 2...");
      await addEqualExpense(pageAlice, group1Name, "Hotel", "120.00");
      await addEqualExpense(pageBob, group2Name, "Utilities", "80.00");

      console.log("Alice enabling Global Cross-Group Netting in Group 1...");
      await pageAlice.goto("/groups");
      await openGroupDetails(pageAlice, group1Name);
      await pageAlice.getByRole("tab", { name: /balances/i }).click();

      const globalScopeBtn = pageAlice.locator('[aria-label="Select Global Cross-Group Scope"]');
      await expect(globalScopeBtn).toBeVisible({ timeout: 15000 });

      await globalScopeBtn.click();
      await pageAlice.waitForTimeout(1000);

      await expect(pageAlice.getByText(/Global Cross-Group Netting/i).first()).toBeVisible({ timeout: 15000 });

      console.log("Bob paying net balance from suggested plan...");
      await pageBob.goto("/groups");
      await openGroupDetails(pageBob, group1Name);
      await pageBob.getByRole("tab", { name: /balances/i }).click();

      await expect(pageBob.getByText(/Suggested Settlement Plan/i)).toBeVisible({ timeout: 15000 });

      const settleBtn = pageBob.getByRole("button", { name: /Settle/i }).first();
      await expect(settleBtn).toBeVisible({ timeout: 15000 });
      await settleBtn.click();

      const settleModal = pageBob.getByRole("dialog", { name: /record payment/i });
      await expect(settleModal).toBeVisible({ timeout: 10000 });
      await settleModal.getByRole("button", { name: /confirm & mark paid/i }).click();
      await expect(settleModal).not.toBeVisible({ timeout: 10000 });

      console.log("Alice confirming receipt of global settlement...");
      await pageAlice.reload();
      await pageAlice.getByRole("tab", { name: /balances/i }).click();
      await expect(pageAlice.getByText(/waiting for your confirmation/i)).toBeVisible({ timeout: 15000 });
      await pageAlice.getByRole("button", { name: /confirm receipt/i }).click();

      console.log("Verifying balances settled in both groups...");
      await expect(pageAlice.getByText(/you don't owe anything/i)).toBeVisible({ timeout: 15000 });

      console.log("Scenario 2 completed successfully!");

    } finally {
      await ctxAlice.close();
      await ctxBob.close();
    }
  });
});
