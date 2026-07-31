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
  await expect(page).toHaveURL(/\/login/, { timeout: 15000 });
}

async function loginUser(page: Page, username: string) {
  await page.goto("/login");
  await page.locator('#username').fill(username);
  await page.locator('#password').fill(PASSWORD);
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
  await expect(pageA.getByRole("button", { name: /pending/i })).toBeVisible({ timeout: 5000 });
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
) {
  await pageOwner.goto("/groups");
  await pageOwner
    .getByRole("button", { name: /create group/i })
    .first()
    .click();
  const modal = pageOwner.getByRole("dialog");
  await expect(modal).toBeVisible();
  await modal.locator('#group-name').fill(groupName);

  await expect(modal.locator(".animate-spin")).not.toBeVisible();
  
  const friendRow = modal.locator("div.cursor-pointer", { hasText: friendDisplayFirstName });
  await expect(friendRow).toBeVisible({ timeout: 10000 });
  await friendRow.click();
  
  await modal.getByRole("button", { name: /create group/i }).click();
  await expect(modal).not.toBeVisible({ timeout: 10000 });

  await expect(pageOwner.getByText(groupName)).toBeVisible({ timeout: 10000 });
}

async function addEqualExpense(
  pageOwner: Page,
  groupName: string,
  description: string,
  amount: string,
) {
  await pageOwner.goto("/groups");
  await pageOwner.getByText(groupName).click();
  await pageOwner.getByRole("button", { name: /add expense/i }).first().click();
  await pageOwner.locator('#description').fill(description);
  await pageOwner.locator('#amount').fill(amount);
  
  const addResp = pageOwner.waitForResponse(
    (r) => r.url().includes("/expenses") && r.status() === 201
  );
  await pageOwner.getByRole("dialog").getByRole("button", { name: "Add Expense", exact: true }).click();
  await addResp;
  await expect(pageOwner.getByRole("dialog")).toBeHidden();
}

test.describe("[E2E] Unified Payment Auto-Allocation", () => {
  test.setTimeout(180000);

  // Helper setup to create 2 groups, 50/50 split of $100 total ($50 each group owed by Bob to Alice)
  async function setupAliceAndBobWithDebts(browser: any) {
    const ts = `${Date.now()}_${Math.floor(Math.random() * 10000)}`;
    const aliceName = `alice_u_${ts}`;
    const bobName = `bob_u_${ts}`;

    const ctxAlice = await browser.newContext();
    const ctxBob = await browser.newContext();
    const pageAlice = await ctxAlice.newPage();
    const pageBob = await ctxBob.newPage();

    // 1. Setup Alice and Bob as friends
    await registerUser(pageAlice, aliceName, "Alice", "User");
    await registerUser(pageBob, bobName, "Bob", "User");
    await loginUser(pageAlice, aliceName);
    await loginUser(pageBob, bobName);
    await sendFriendRequest(pageAlice, bobName);
    await acceptFriendRequest(pageBob, aliceName);

    // 2. Create Group A (Oldest) and Group B (Newer)
    const groupAName = `Group A Oldest ${ts}`;
    const groupBName = `Group B Newer ${ts}`;
    await createGroupWithMember(pageAlice, groupAName, "Bob");
    await createGroupWithMember(pageAlice, groupBName, "Bob");

    // 3. Add $100 expenses in each group (Bob owes $50 in each)
    await addEqualExpense(pageAlice, groupAName, "Dinner", "100.00");
    await addEqualExpense(pageAlice, groupBName, "Travel", "100.00");

    return {
      aliceName,
      bobName,
      groupAName,
      groupBName,
      ctxAlice,
      ctxBob,
      pageAlice,
      pageBob,
    };
  }

  test("1.1 Settlement clears all bills owed: Bob owes $100, pays $100", async ({ browser }) => {
    const setup = await setupAliceAndBobWithDebts(browser);
    const { pageAlice, pageBob, bobName, ctxAlice, ctxBob } = setup;

    try {
      // Bob goes to Alice's detail page to settle
      await pageBob.goto("/friends");
      await pageBob.getByText("Alice User").click();
      await expect(pageBob.getByText("-100.00")).toBeVisible();

      // Bob records full payment of $100.00
      await pageBob.getByRole("button", { name: /settle debt/i }).click();
      const modal = pageBob.getByRole("dialog");
      await expect(modal).toBeVisible();

      await modal.getByRole("button", { name: /i paid/i }).click();
      const amountInput = modal.locator("input[type='number']").first();
      await amountInput.fill("100.00");

      const saveButton = modal.getByRole("button", { name: /save settlement/i });
      await expect(saveButton).toBeEnabled();
      await saveButton.click();
      await expect(modal).not.toBeVisible({ timeout: 10000 });

      // Alice logs in or reloads to confirm receipt
      await pageAlice.goto("/friends");
      await pageAlice.getByText("Bob User").click();
      await expect(pageAlice.getByRole("button", { name: /confirm receipt/i })).toBeVisible({ timeout: 10000 });
      await pageAlice.getByRole("button", { name: /confirm receipt/i }).click();

      // Verify that after confirmation, both are fully settled up
      await expect(pageAlice.getByText(/you are all settled up/i)).toBeVisible({ timeout: 10000 });
      
      // Let's verify Group A balance (both you don't owe anything and no outstanding debt of bobName)
      await pageAlice.goto("/groups");
      await pageAlice.getByText(setup.groupAName).click();
      await pageAlice.getByRole("tab", { name: /balances/i }).click();
      await expect(pageAlice.getByText(/you don't owe anything/i)).toBeVisible({ timeout: 10000 });
      await expect(pageAlice.getByText(bobName)).not.toBeVisible({ timeout: 5000 });

      // Let's verify Group B balance
      await pageAlice.goto("/groups");
      await pageAlice.getByText(setup.groupBName).click();
      await pageAlice.getByRole("tab", { name: /balances/i }).click();
      await expect(pageAlice.getByText(/you don't owe anything/i)).toBeVisible({ timeout: 10000 });
      await expect(pageAlice.getByText(bobName)).not.toBeVisible({ timeout: 5000 });

    } finally {
      await ctxAlice.close();
      await ctxBob.close();
    }
  });

  test("1.2 Settlement: Bob owes $100, pays $80. Oldest Group A ($50) cleared, Group B ($50) has $20 remaining", async ({ browser }) => {
    const setup = await setupAliceAndBobWithDebts(browser);
    const { pageAlice, pageBob, bobName, ctxAlice, ctxBob } = setup;

    try {
      // Bob goes to Alice's detail page to settle
      await pageBob.goto("/friends");
      await pageBob.getByText("Alice User").click();
      await expect(pageBob.getByText("-100.00")).toBeVisible();

      // Bob records partial payment of $80.00
      await pageBob.getByRole("button", { name: /settle debt/i }).click();
      const modal = pageBob.getByRole("dialog");
      await expect(modal).toBeVisible();

      await modal.getByRole("button", { name: /i paid/i }).click();
      const amountInput = modal.locator("input[type='number']").first();
      await amountInput.fill("80.00");

      const saveButton = modal.getByRole("button", { name: /save settlement/i });
      await expect(saveButton).toBeEnabled();
      await saveButton.click();
      await expect(modal).not.toBeVisible({ timeout: 10000 });

      // Alice logs in or reloads to confirm receipt
      await pageAlice.goto("/friends");
      await pageAlice.getByText("Bob User").click();
      await expect(pageAlice.getByRole("button", { name: /confirm receipt/i })).toBeVisible({ timeout: 10000 });
      await pageAlice.getByRole("button", { name: /confirm receipt/i }).click();

      // Verify that after confirmation, total outstanding balance becomes +20.00
      await expect(pageAlice.getByText("+20.00")).toBeVisible({ timeout: 10000 });

      // Verify oldest Group A balance shows Alice doesn't owe anything
      await pageAlice.goto("/groups");
      await pageAlice.getByText(setup.groupAName).click();
      await pageAlice.getByRole("tab", { name: /balances/i }).click();
      await expect(pageAlice.getByText(/you don't owe anything/i)).toBeVisible({ timeout: 10000 });

    } finally {
      await ctxAlice.close();
      await ctxBob.close();
    }
  });

  test("1.3 Settlement: Bob owes $100, pays $30. Oldest Group A ($50) has $20 remaining, Group B ($50) untouched", async ({ browser }) => {
    const setup = await setupAliceAndBobWithDebts(browser);
    const { pageAlice, pageBob, bobName, ctxAlice, ctxBob } = setup;

    try {
      // Bob goes to Alice's detail page to settle
      await pageBob.goto("/friends");
      await pageBob.getByText("Alice User").click();
      await expect(pageBob.getByText("-100.00")).toBeVisible();

      // Bob records partial payment of $30.00
      await pageBob.getByRole("button", { name: /settle debt/i }).click();
      const modal = pageBob.getByRole("dialog");
      await expect(modal).toBeVisible();

      await modal.getByRole("button", { name: /i paid/i }).click();
      const amountInput = modal.locator("input[type='number']").first();
      await amountInput.fill("30.00");

      const saveButton = modal.getByRole("button", { name: /save settlement/i });
      await expect(saveButton).toBeEnabled();
      await saveButton.click();
      await expect(modal).not.toBeVisible({ timeout: 10000 });

      // Alice logs in or reloads to confirm receipt
      await pageAlice.goto("/friends");
      await pageAlice.getByText("Bob User").click();
      await expect(pageAlice.getByRole("button", { name: /confirm receipt/i })).toBeVisible({ timeout: 10000 });
      await pageAlice.getByRole("button", { name: /confirm receipt/i }).click();

      // Verify that after confirmation, total outstanding balance becomes +70.00
      await expect(pageAlice.getByText("+70.00")).toBeVisible({ timeout: 10000 });

      // Verify oldest Group A balance shows Alice doesn't owe anything
      await pageAlice.goto("/groups");
      await pageAlice.getByText(setup.groupAName).click();
      await pageAlice.getByRole("tab", { name: /balances/i }).click();
      await expect(pageAlice.getByText(/you don't owe anything/i)).toBeVisible({ timeout: 10000 });

      // Verify newer Group B balance shows Bob name
      await pageAlice.goto("/groups");
      await pageAlice.getByText(setup.groupBName).click();
      await pageAlice.getByRole("tab", { name: /balances/i }).click();
      await expect(pageAlice.getByText(bobName).first()).toBeVisible({ timeout: 10000 });

    } finally {
      await ctxAlice.close();
      await ctxBob.close();
    }
  });

  test("1.4 Settlement: Overpayment (Bob owes $100, pays $120. Both groups cleared, Alice owes Bob $20 overall)", async ({ browser }) => {
    const setup = await setupAliceAndBobWithDebts(browser);
    const { pageAlice, pageBob, bobName, ctxAlice, ctxBob } = setup;

    try {
      // Bob goes to Alice's detail page to settle
      await pageBob.goto("/friends");
      await pageBob.getByText("Alice User").click();
      await expect(pageBob.getByText("-100.00")).toBeVisible();

      // Bob records overpayment of $120.00
      await pageBob.getByRole("button", { name: /settle debt/i }).click();
      const modal = pageBob.getByRole("dialog");
      await expect(modal).toBeVisible();

      await modal.getByRole("button", { name: /i paid/i }).click();
      const amountInput = modal.locator("input[type='number']").first();
      await amountInput.fill("120.00");

      const saveButton = modal.getByRole("button", { name: /save settlement/i });
      await expect(saveButton).toBeEnabled();
      await saveButton.click();
      await expect(modal).not.toBeVisible({ timeout: 10000 });

      // Alice logs in/reloads to confirm receipt
      await pageAlice.goto("/friends");
      await pageAlice.getByText("Bob User").click();
      await expect(pageAlice.getByRole("button", { name: /confirm receipt/i })).toBeVisible({ timeout: 10000 });
      await pageAlice.getByRole("button", { name: /confirm receipt/i }).click();

      // Verify that Alice has a net credit balance of -20.00 overall (showing she owes Bob $20)
      await expect(pageAlice.getByText("-20.00")).toBeVisible({ timeout: 10000 });

      // Verify both groups are completely settled ($0 balance)
      await pageAlice.goto("/groups");
      await pageAlice.getByText(setup.groupAName).click();
      await pageAlice.getByRole("tab", { name: /balances/i }).click();
      await expect(pageAlice.getByText(/you don't owe anything/i)).toBeVisible({ timeout: 10000 });
      await expect(pageAlice.getByText(bobName)).not.toBeVisible({ timeout: 5000 });

      await pageAlice.goto("/groups");
      await pageAlice.getByText(setup.groupBName).click();
      await pageAlice.getByRole("tab", { name: /balances/i }).click();
      await expect(pageAlice.getByText(/you don't owe anything/i)).toBeVisible({ timeout: 10000 });
      await expect(pageAlice.getByText(bobName)).not.toBeVisible({ timeout: 5000 });

    } finally {
      await ctxAlice.close();
      await ctxBob.close();
    }
  });

  test("1.5 Settlement: Bidirectional Debts (Bob owes $50 in Group A, Alice owes $30 in Group B. Bob pays $20)", async ({ browser }) => {
    const ts = `${Date.now()}_${Math.floor(Math.random() * 10000)}`;
    const aliceName = `alice_u_${ts}`;
    const bobName = `bob_u_${ts}`;

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

      // 2. Create Group A (where Alice is creator and adds expense so Bob owes $50)
      const groupAName = `Group A Bidirectional ${ts}`;
      await createGroupWithMember(pageAlice, groupAName, "Bob");
      await addEqualExpense(pageAlice, groupAName, "Dinner", "100.00");

      // 3. Create Group B (where Bob is creator and adds expense so Alice owes $30)
      const groupBName = `Group B Bidirectional ${ts}`;
      await createGroupWithMember(pageBob, groupBName, "Alice");
      await addEqualExpense(pageBob, groupBName, "Travel", "60.00");

      // Bob goes to Alice's detail page to settle net outstanding debt (-20.00)
      await pageBob.goto("/friends");
      await pageBob.getByText("Alice User").click();
      await expect(pageBob.getByText("-20.00")).toBeVisible();

      // Bob records a payment of $20.00
      await pageBob.getByRole("button", { name: /settle debt/i }).click();
      const modal = pageBob.getByRole("dialog");
      await expect(modal).toBeVisible();

      await modal.getByRole("button", { name: /i paid/i }).click();
      const amountInput = modal.locator("input[type='number']").first();
      await amountInput.fill("20.00");

      const saveButton = modal.getByRole("button", { name: /save settlement/i });
      await expect(saveButton).toBeEnabled();
      await saveButton.click();
      await expect(modal).not.toBeVisible({ timeout: 10000 });

      // Alice confirms receipt
      await pageAlice.goto("/friends");
      await pageAlice.getByText("Bob User").click();
      await expect(pageAlice.getByRole("button", { name: /confirm receipt/i })).toBeVisible({ timeout: 10000 });
      await pageAlice.getByRole("button", { name: /confirm receipt/i }).click();

      // Verify that after confirmation, net outstanding balance becomes exactly 0
      await expect(pageAlice.getByText(/you are all settled up/i)).toBeVisible({ timeout: 10000 });

      // Verify Group A balance is updated (Bob owed $50, paid $20, so he still owes $30)
      await pageAlice.goto("/groups");
      await pageAlice.getByText(groupAName).click();
      await pageAlice.getByRole("tab", { name: /balances/i }).click();
      await expect(pageAlice.getByText(bobName).first()).toBeVisible({ timeout: 10000 });
      await expect(pageAlice.getByText("$30.00").first()).toBeVisible({ timeout: 10000 });

      // Verify Group B balance remains untouched (Alice owes Bob $30.00)
      await pageAlice.goto("/groups");
      await pageAlice.getByText(groupBName).click();
      await pageAlice.getByRole("tab", { name: /balances/i }).click();
      await expect(pageAlice.getByText("You owe").first()).toBeVisible({ timeout: 10000 });
      await expect(pageAlice.getByText("$30.00").first()).toBeVisible({ timeout: 10000 });

    } finally {
      await ctxAlice.close();
      await ctxBob.close();
    }
  });

  test("1.6 Settlement: Block Group Leave with Pending Settlement", async ({ browser }) => {
    const ts = `${Date.now()}_${Math.floor(Math.random() * 10000)}`;
    const aliceName = `alice_u_${ts}`;
    const bobName = `bob_u_${ts}`;

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

      // 2. Create Group A. Alice adds a $100 expense with Bob (Bob owes $50)
      const groupName = `Group Leave Block ${ts}`;
      await createGroupWithMember(pageAlice, groupName, "Bob");
      await addEqualExpense(pageAlice, groupName, "Dinner", "100.00");

      // Bob goes to Alice's detail page to settle the debt
      await pageBob.goto("/friends");
      await pageBob.getByText("Alice User").click();
      await expect(pageBob.getByText("-50.00")).toBeVisible();

      // Bob records a $50.00 payment (status = MARKED_PAID)
      await pageBob.getByRole("button", { name: /settle debt/i }).click();
      const modal = pageBob.getByRole("dialog");
      await expect(modal).toBeVisible();

      await modal.getByRole("button", { name: /i paid/i }).click();
      const amountInput = modal.locator("input[type='number']").first();
      await amountInput.fill("50.00");

      const saveButton = modal.getByRole("button", { name: /save settlement/i });
      await expect(saveButton).toBeEnabled();
      await saveButton.click();
      await expect(modal).not.toBeVisible({ timeout: 10000 });

      // Bob's group balance is now temporarily $0.00 because of the pending payment.
      // Bob goes to Group Page to leave group
      await pageBob.goto("/groups");
      await pageBob.getByText(groupName).click();
      
      // Bob clicks "Leave Group" button in the action card
      await pageBob.getByRole("tab", { name: "Members" }).click();
      await pageBob.getByRole("button", { name: /leave group/i }).click();
      
      // Leave group modal should appear
      const leaveModal = pageBob.getByRole("dialog");
      await expect(leaveModal).toBeVisible();
      
      // Verify that it displays the pending unconfirmed payment validation message
      await expect(
        leaveModal.getByText(/You cannot leave this group while you have pending unconfirmed payments/i)
      ).toBeVisible({ timeout: 10000 });
      
      // Verify that the "Leave Group" confirm button is disabled
      const confirmLeaveBtn = leaveModal.getByRole("button", { name: /^leave group$/i });
      await expect(confirmLeaveBtn).toBeDisabled();

    } finally {
      await ctxAlice.close();
      await ctxBob.close();
    }
  });
});
