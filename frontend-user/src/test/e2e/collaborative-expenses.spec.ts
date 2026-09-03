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
  await page.goto("/friends");
  await page.getByText(new RegExp(requesterName, "i")).waitFor({ state: 'visible' });
  await page.getByTitle("Accept").last().click();
}

test.describe("[E2E] Collaborative Expenses", () => {
  test("Members can edit and delete expenses when collaborative editing is enabled", async ({ browser }) => {
    test.setTimeout(60000);
    const ts = Date.now();
    const aliceName = `alice_collab_${ts}`;
    const bobName = `bob_collab_${ts}`;

    const ctxAlice = await browser.newContext();
    const ctxBob = await browser.newContext();
    const pageAlice = await ctxAlice.newPage();
    const pageBob = await ctxBob.newPage();

    try {
      // 1. Setup
      await registerAndLogin(pageAlice, aliceName, "Alice");
      await registerAndLogin(pageBob, bobName, "Bob");
      await sendFriendRequest(pageAlice, bobName);
      await acceptFriendRequest(pageBob, aliceName);

      // 2. Alice creates group
      await pageAlice.goto("/groups");
      await pageAlice.getByRole("button", { name: /create group/i }).first().click();
      const groupName = `Collab Group ${ts}`;
      await pageAlice.locator('#group-name').fill(groupName);
      await pageAlice.getByText("Bob User").click();
      await pageAlice.getByRole("button", { name: /create group/i }).last().click();
      await expect(pageAlice.getByText(groupName)).toBeVisible();

      // 3. Alice adds expense
      await pageAlice.getByText(groupName).click();
      await pageAlice.getByRole("button", { name: /add expense/i }).first().click();
      await pageAlice.locator('#description').fill("Initial Lunch");
      await pageAlice.locator('#amount').fill("30.00");
      await pageAlice.getByRole("button", { name: /add expense/i }).last().click();
      await expect(pageAlice.getByText(/Initial Lunch/i).first()).toBeVisible();

      // 4. Bob edits Alice's expense
      await pageBob.goto("/groups");
      await pageBob.getByText(groupName).click();
      await pageBob.getByRole("tab", { name: /expenses/i }).click();
      
      // Bob opens dropdown and clicks edit
      await pageBob.getByLabel(/Actions for Initial Lunch/i).first().click();
      await pageBob.getByRole("menuitem", { name: /Edit/i }).click();
      
      await pageBob.locator('#description').fill("Updated Lunch by Bob");
      await pageBob.locator('#amount').fill("40.00");
      await pageBob.getByRole("button", { name: /save changes/i }).click();
      
      await expect(pageBob.getByText(/Updated Lunch by Bob/i).first()).toBeVisible();
      await expect(pageBob.getByText(/amount: 30.00 -> 40.00/i)).toBeVisible();

      // 4.1 Verify Story 7: Last edited by [Name]
      await pageBob.getByLabel(/Actions for Updated Lunch by Bob/i).first().click();
      await pageBob.getByRole("menuitem", { name: /Edit/i }).click();
      await expect(pageBob.getByText(/Last modified by You/i)).toBeVisible();
      await pageBob.getByRole("button", { name: /cancel/i }).click();

      // 5. Alice disables collaborative editing
      await pageAlice.reload();
      await pageAlice.getByRole("tab", { name: /members/i }).click();
      await pageAlice.getByLabel(/Toggle allow members to edit expenses/i).first().click();
      
      // 6. Bob attempts to edit again (should not see actions)
      await pageBob.reload();
      await pageBob.getByRole("tab", { name: /expenses/i }).click();
      await expect(pageBob.getByLabel(/Actions for Updated Lunch by Bob/i)).not.toBeVisible();

      // 7. Bob creates his own expense and Alice (admin) deletes it
      await pageBob.getByRole("tab", { name: /expenses/i }).click();
      await pageBob.getByRole("button", { name: /add expense/i }).first().click();
      await pageBob.locator('#description').fill("Bob's Coffee");
      await pageBob.locator('#amount').fill("5.00");
      await pageBob.getByRole("button", { name: /add expense/i }).last().click();
      
      await pageAlice.reload();
      await pageAlice.getByRole("tab", { name: /expenses/i }).click();
      await pageAlice.getByLabel(/Actions for Bob's Coffee/i).first().click();
      await pageAlice.getByRole('menuitem', { name: 'Delete' }).click();
      
      await expect(pageAlice.getByText(/Are you sure you want to delete/i)).toBeVisible();
      await pageAlice.getByRole("button", { name: /delete expense/i }).last().click();
      
      // The creation log might still be there, so we check for the deletion log
      await expect(pageAlice.getByText(/You deleted "Bob's Coffee"/i)).toBeVisible();

    } finally {
      await ctxAlice.close();
      await ctxBob.close();
    }
  });
});
