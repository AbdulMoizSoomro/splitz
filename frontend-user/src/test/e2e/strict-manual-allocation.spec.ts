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

  // Wait for loader to disappear if any
  await expect(modal.locator(".animate-spin")).not.toBeVisible();
  
  const friendRow = modal.locator("div.cursor-pointer", { hasText: friendDisplayFirstName });
  await expect(friendRow).toBeVisible({ timeout: 10000 });
  await friendRow.click();
  
  // Verify selection state (blue background)
  await expect(friendRow).toHaveClass(/bg-blue-50/);

  await modal.getByRole("button", { name: /create group/i }).click();
  await expect(modal).not.toBeVisible({ timeout: 10000 });

  await expect(pageOwner.getByText(groupName)).toBeVisible({ timeout: 10000 });
  return pageOwner.url();
}

test.describe("[E2E] Strict Manual Debt Allocation", () => {
  test("should allow manual allocation of settlement amount to specific group debts", async ({
    browser,
  }) => {
    test.setTimeout(180000); // Higher timeout for slow CI/container environment
    const ts = `${Date.now()}_${Math.floor(Math.random() * 10000)}`;
    const aliceName = `alice_51_${ts}`;
    const bobName = `bob_51_${ts}`;

    const ctxAlice = await browser.newContext();
    const ctxBob = await browser.newContext();
    const pageAlice = await ctxAlice.newPage();
    const pageBob = await ctxBob.newPage();

    try {
      // 1. Setup Alice and Bob as friends
      await registerUser(pageAlice, aliceName, "Alice", "User");
      await registerUser(pageBob, bobName, "Bob", "User");
      await loginUser(pageAlice, aliceName);
      await loginUser(pageBob, bobName);
      await sendFriendRequest(pageAlice, bobName);
      await acceptFriendRequest(pageBob, aliceName);

      // 2. Create two shared groups
      const groupDinner = `Dinner Group ${ts}`;
      const groupTravel = `Travel Group ${ts}`;
      await createGroupWithMember(pageAlice, groupDinner, "Bob");
      await createGroupWithMember(pageAlice, groupTravel, "Bob");

      // 3. Alice creates expenses in both groups
      await pageAlice.goto("/groups");
      
      // Group 1
      await pageAlice.getByText(groupDinner).click();
      await pageAlice.getByRole("button", { name: /add expense/i }).first().click();
      await pageAlice.getByLabel(/description/i).fill("Dinner");
      await pageAlice.getByLabel(/amount/i).fill("40.00");
      const dinnerResp = pageAlice.waitForResponse(r => r.url().includes('/expenses') && r.status() === 201);
      await pageAlice.getByRole("dialog").getByRole("button", { name: "Add Expense", exact: true }).click();
      await dinnerResp;
      await expect(pageAlice.getByRole("dialog")).toBeHidden();

      // Group 2
      await pageAlice.goto("/groups");
      await pageAlice.getByText(groupTravel).click();
      await pageAlice.getByRole("button", { name: /add expense/i }).first().click();
      await pageAlice.getByLabel(/description/i).fill("Flight");
      await pageAlice.getByLabel(/amount/i).fill("100.00");
      const travelResp = pageAlice.waitForResponse(r => r.url().includes('/expenses') && r.status() === 201);
      await pageAlice.getByRole("dialog").getByRole("button", { name: "Add Expense", exact: true }).click();
      await travelResp;
      await expect(pageAlice.getByRole("dialog")).toBeHidden();

      // 4. Alice navigates to Bob's detail page
      await pageAlice.goto("/friends");
      await pageAlice.getByText("Bob User").click();
      await expect(pageAlice.getByText("+70.00")).toBeVisible();
      
      // 5. Alice opens Settle Debt modal
      await pageAlice.getByRole("button", { name: /settle debt/i }).click();
      const modal = pageAlice.getByRole("dialog");
      await expect(modal).toBeVisible();

      // 6. Alice expands manual allocation
      await modal.getByText(/allocate to group debts/i).click();

      // Verify both groups are present
      const dinnerAllocation = modal.getByLabel(groupDinner, { exact: false });
      const travelAllocation = modal.getByLabel(groupTravel, { exact: false });
      await expect(dinnerAllocation).toBeVisible({ timeout: 15000 });
      await expect(travelAllocation).toBeVisible({ timeout: 15000 });

      // 7. Alice enters allocated amounts
      await dinnerAllocation.fill("20.00");
      await travelAllocation.fill("50.00");

      // 8. Set total amount
      const totalAmountInput = modal.getByRole("spinbutton").first();
      await totalAmountInput.fill("70.00");
      
      // 9. Save and verify
      const saveButton = modal.getByRole("button", { name: /save settlement/i });
      await expect(saveButton).toBeEnabled({ timeout: 10000 });
      await saveButton.click();
      
      await expect(modal).not.toBeVisible({ timeout: 15000 });
      await expect(pageAlice.getByText(/settlement recorded successfully/i)).toBeVisible();

      // 10. Verify activity feed shows settlements
      await expect(pageAlice.getByText("Bob paid you").first()).toBeVisible({ timeout: 15000 });
      await expect(pageAlice.getByText("$20.00")).toBeVisible();
      await expect(pageAlice.getByText("$50.00")).toBeVisible();

    } finally {
      await ctxAlice.close();
      await ctxBob.close();
    }
  });
});
