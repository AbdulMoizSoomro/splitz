import { test, expect, type Page } from "@playwright/test";

const PASSWORD = "Password123!";

async function registerUser(
  page: Page,
  username: string,
  firstName: string,
  lastName: string,
) {
  await page.goto("/register");
  await page.locator('#firstName').fill(firstName);
  await page.locator('#lastName').fill(lastName);
  await page.locator('#username').fill(username);
  await page.locator('#email').fill(`${username}@example.com`);
  await page.locator('#password').fill(PASSWORD);
  await page.getByRole("button", { name: /register/i }).click();
  await expect(page).toHaveURL(/\/login/);
}

async function loginUser(page: Page, username: string) {
  await page.goto("/login");
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(PASSWORD);
  await page.getByRole("button", { name: /login/i }).click();
  await expect(page).toHaveURL(/\/$/);
}

async function sendFriendRequest(pageA: Page, targetUsername: string) {
  await pageA.goto("/friends");
  await pageA.getByPlaceholder(/search by name or email/i).fill(targetUsername);
  await expect(pageA.getByText(`@${targetUsername}`)).toBeVisible();
  await pageA.getByRole("button", { name: /add friend/i }).click();
  await expect(pageA.getByRole("button", { name: /pending/i })).toBeVisible();
}

async function acceptFriendRequest(pageB: Page, fromUsername: string) {
  await pageB.goto("/friends");
  await expect(pageB.getByText(`@${fromUsername}`)).toBeVisible();
  await pageB.getByTitle("Accept").click();
  await expect(pageB.getByText(/no pending friend requests/i)).toBeVisible();
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
  await modal.locator("#group-name").fill(groupName);

  await expect(modal.locator(".animate-spin")).not.toBeVisible();
  const friendRow = modal.locator("div.cursor-pointer", {
    hasText: new RegExp(friendDisplayFirstName, "i"),
  });
  await expect(friendRow.first()).toBeVisible({ timeout: 15000 });
  await friendRow.first().click();

  await modal.getByRole("button", { name: /create group/i }).click();
  await expect(modal).not.toBeVisible({ timeout: 15000 });

  await expect(pageOwner.getByText(groupName)).toBeVisible({ timeout: 15000 });
  await pageOwner.getByText(groupName).click();
  await expect(pageOwner).toHaveURL(/\/groups\/\d+/);

  return pageOwner.url();
}

async function addEqualExpense(
  pagePayer: Page,
  groupName: string,
  description: string,
  amount: string,
) {
  await pagePayer.goto("/groups");
  await pagePayer.getByText(groupName).click();
  await pagePayer.getByRole("button", { name: /add expense/i }).first().click();
  await pagePayer.locator("#description").fill(description);
  await pagePayer.locator("#amount").fill(amount);

  await pagePayer.getByRole("dialog").getByRole("button", { name: "Add Expense", exact: true }).click();
  await expect(pagePayer.getByRole("dialog")).toBeHidden({ timeout: 15000 });
}

test.describe("[E2E] Temporary Friends List", () => {
  test("should show temporary friends with group badges and allow request cancellation", async ({
    browser,
  }) => {
    const ts = `${Date.now()}_${Math.floor(Math.random() * 10000)}`;
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

      // 4. Alice creates a group with Bob
      const groupName = `Shared Group ${ts}`;
      await createGroupWithMember(pageAlice, groupName, "Bob");

      // 5. Alice adds an expense (Alice pays $10, shared with Bob)
      //    Bob will owe Alice $5
      await pageAlice.goto("/groups");
      const groupCard = pageAlice.locator(".bg-card", { hasText: groupName });
      await groupCard.getByRole("button", { name: /add expense/i }).click();

      const expenseModal = pageAlice.getByRole("dialog", {
        name: /add new expense/i,
      });
      await expect(expenseModal).toBeVisible();
      await expenseModal.locator('#description').fill("Lunch");
      await expenseModal.locator('#amount').fill("10.00");
      await expenseModal.getByRole("button", { name: /add expense/i }).click();
      await expect(expenseModal).not.toBeVisible();

      // 6. Alice removes Bob from friends
      await pageAlice.goto("/friends");
      await expect(pageAlice.getByText(`@${bobName}`)).toBeVisible();
      pageAlice.once("dialog", (dialog) => dialog.accept());
      // Remove Friend now lives inside each connection card's three-dot menu.
      await pageAlice.getByLabel("More options").first().click();
      await pageAlice.getByTitle("Remove Friend").click();
      // Bob still shares a group, so he stays visible in the unified connections list as a
      // temporary friend. The list is therefore NOT empty, and the "no friends added yet"
      // empty state correctly does not appear.
      await expect(pageAlice.getByText(/no friends added yet/i)).toHaveCount(0);

      // 7. Alice should now see Bob flagged as a temporary friend.
      // Reload to trigger a fresh fetch of the unified connections ledger.
      await pageAlice.reload();
      await pageAlice.waitForLoadState('networkidle');
      await expect(pageAlice.getByTitle("Temporary Friends")).toBeVisible({ timeout: 15000 });
      const tempFriendCard = pageAlice.locator(".bg-orange-50\\/30");
      // "@<username>" is unique per user; a /name|Bob/ alternation would match several ancestors.
      await expect(tempFriendCard.getByText(`@${bobName}`, { exact: true })).toBeVisible();

      // 8. Verify the group badge is visible
      await expect(tempFriendCard.getByText(groupName)).toBeVisible();

      // 9. Alice sends a friend request to Bob from the temp friends list
      await tempFriendCard.getByRole("button", { name: /add friend/i }).click();

      // 10. Verify "Cancel Request" button appears
      await expect(
        tempFriendCard.getByRole("button", { name: /cancel request/i }),
      ).toBeVisible();

      // 11. Alice cancels the request
      await tempFriendCard
        .getByRole("button", { name: /cancel request/i })
        .click();

      // 12. Verify "Add Friend" button returns
      await expect(
        tempFriendCard.getByRole("button", { name: /add friend/i }),
      ).toBeVisible();
    } finally {
      await ctxAlice.close();
      await ctxBob.close();
    }
  });

  test("should aggregate counterparty positions across multiple groups using single counterparties endpoint", async ({
    browser,
  }) => {
    const ts = `${Date.now()}_${Math.floor(Math.random() * 10000)}`;
    const aliceName = `alice_mg_${ts}`;
    const bobName = `bob_mg_${ts}`;

    const ctxAlice = await browser.newContext();
    const ctxBob = await browser.newContext();
    const pageAlice = await ctxAlice.newPage();
    const pageBob = await ctxBob.newPage();

    try {
      // 1. Register and login Alice & Bob
      await registerUser(pageAlice, aliceName, "Alice", "User");
      await registerUser(pageBob, bobName, "Bob", "User");
      await loginUser(pageAlice, aliceName);
      await loginUser(pageBob, bobName);

      // 2. Connect as friends
      await sendFriendRequest(pageAlice, bobName);
      await acceptFriendRequest(pageBob, aliceName);

      // 3. Create Group 1 and add $20 expense (Bob owes $10)
      const group1Name = `Trip Group ${ts}`;
      await createGroupWithMember(pageAlice, group1Name, "Bob");
      await addEqualExpense(pageAlice, group1Name, "Tickets", "20.00");

      // 4. Create Group 2 and add $10 expense (Bob owes $5)
      const group2Name = `Dinner Group ${ts}`;
      await createGroupWithMember(pageAlice, group2Name, "Bob");
      await addEqualExpense(pageAlice, group2Name, "Dinner", "10.00");

      // 5. Alice unfriends Bob
      await pageAlice.goto("/friends");
      await expect(pageAlice.getByText(`@${bobName}`)).toBeVisible();
      pageAlice.once("dialog", (dialog) => dialog.accept());
      // Remove Friend now lives inside each connection card's three-dot menu.
      await pageAlice.getByLabel("More options").first().click();
      await pageAlice.getByTitle("Remove Friend").click();
      // Bob still shares groups, so he remains listed as a temporary connection.
      await expect(pageAlice.getByText(/no friends added yet/i)).toHaveCount(0);

      // 6. Prove the UI calls /counterparties (single aggregated endpoint, not per-group fan-out).
      const counterpartiesCalled = pageAlice.waitForRequest(
        (req) => req.url().includes("/counterparties"),
        { timeout: 20000 },
      );

      // 7. Reload /friends page to trigger useLedger({ detail: true })
      await pageAlice.reload();
      await counterpartiesCalled;
      await pageAlice.waitForLoadState("networkidle");

      // Read the payload directly. The response body is discarded once the reload navigates away,
      // so reading it off the intercepted response is inherently racy; an in-page fetch with the
      // live session token is deterministic.
      const cpData: any = await pageAlice.evaluate(async () => {
        const raw = localStorage.getItem("splitz-auth") ?? "";
        const parsed = raw ? JSON.parse(raw) : {};
        const token = parsed?.state?.token ?? parsed?.token;
        const userId = parsed?.state?.user?.id ?? parsed?.user?.id;
        const res = await fetch(`/api/expense/users/${userId}/counterparties`, {
          headers: { Authorization: `Bearer ${token}` },
        });
        return res.json();
      });

      // 8. Assert backend payload contains hydrated user details and multi-group references
      const bobCounterparty = cpData.find((cp: any) => cp.username === bobName);
      expect(bobCounterparty).toBeDefined();
      expect(bobCounterparty.firstName).toBe("Bob");
      expect(bobCounterparty.lastName).toBe("User");
      expect(bobCounterparty.balance).toBe(15);
      expect(bobCounterparty.groups).toHaveLength(2);

      // 9. Assert UI renders both group badges and the netted balance ($15.00)
      await expect(pageAlice.getByTitle("Temporary Friends")).toBeVisible({
        timeout: 15000,
      });
      const tempCard = pageAlice.locator(".bg-orange-50\\/30");
      await expect(tempCard.getByText(`@${bobName}`, { exact: true })).toBeVisible();
      await expect(tempCard.getByText(group1Name)).toBeVisible();
      await expect(tempCard.getByText(group2Name)).toBeVisible();
      await expect(tempCard.getByText(/15\.00/)).toBeVisible();
    } finally {
      await ctxAlice.close();
      await ctxBob.close();
    }
  });
});
