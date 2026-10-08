import { openGroupDetails } from "./helpers/navigation";
import { test, expect } from "@playwright/test";

test.describe("Expense Management", () => {
  test("should allow a user to create an expense with equal split", async ({
    page,
  }) => {
    const timestamp = `${Date.now()}_${Math.floor(Math.random() * 10000)}`;
    const username = `user_exp_${timestamp}`;
    const email = `user_exp_${timestamp}@example.com`;
    const password = "Password123!";

    // 1. Register and Login
    await page.goto("/register");
    await page.locator('#firstName').fill("Exp");
    await page.locator('#lastName').fill("Tester");
    await page.locator('#username').fill(username);
    await page.locator('#email').fill(email);
    await page.locator('#password').fill(password);
    await page.getByRole("button", { name: /register/i }).click();

    await expect(page).toHaveURL(/\/login/, { timeout: 10000 });
    await page.locator('#username').fill(username);
    await page.locator('#password').fill(password);
    await page.getByRole("button", { name: /login/i }).click();
    await expect(page).toHaveURL(/\/$/, { timeout: 10000 });

    // 2. Go to Groups page and create a group
    await page.goto("/groups");
    await page.getByRole("button", { name: /create group/i }).click();

    const groupName = `Group Exp ${timestamp}`;
    const groupModal = page.getByRole("dialog");
    await groupModal.locator('#group-name').fill(groupName);
    await groupModal.getByRole("button", { name: /create group/i }).click();
    await expect(groupModal).not.toBeVisible({ timeout: 10000 });

    // 3. Open Create Expense Modal from the new group card
    const groupCard = page.locator(".bg-card", { hasText: groupName });
    await groupCard.getByRole("button", { name: /add expense/i }).click();

    const expenseModal = page.getByRole("dialog", { name: /add new expense/i });
    await expect(expenseModal).toBeVisible();

    // 4. Fill expense form
    await expenseModal.locator('#description').fill("Team Lunch");
    await expenseModal.locator('#amount').fill("45.00");

    // Verify equal split display
    await expect(
      expenseModal.getByText(/each person pays: \$45.00/i),
    ).toBeVisible();

    // 5. Submit expense
    await expenseModal.getByRole("button", { name: /add expense/i }).click();

    // 6. Verify success (modal closes)
    await expect(expenseModal).not.toBeVisible({ timeout: 10000 });
  });

  test("should display exact split inputs and validation when toggled", async ({
    page,
  }) => {
    const timestamp = `${Date.now()}_${Math.floor(Math.random() * 10000)}`;
    const username = `user_exact_${timestamp}`;
    const email = `user_exact_${timestamp}@example.com`;
    const password = "Password123!";

    // 1. Register and Login
    await page.goto("/register");
    await page.locator('#firstName').fill("Exact");
    await page.locator('#lastName').fill("Tester");
    await page.locator('#username').fill(username);
    await page.locator('#email').fill(email);
    await page.locator('#password').fill(password);
    await page.getByRole("button", { name: /register/i }).click();

    await expect(page).toHaveURL(/\/login/);
    await page.locator('#username').fill(username);
    await page.locator('#password').fill(password);
    await page.getByRole("button", { name: /login/i }).click();
    await expect(page).toHaveURL(/\/$/);

    // 2. Go to Groups page and create a group
    await page.goto("/groups");
    await page.getByRole("button", { name: /create group/i }).click();

    const groupName = `Group Exact ${timestamp}`;
    const groupModal = page.getByRole("dialog");
    await groupModal.locator('#group-name').fill(groupName);
    await groupModal.getByRole("button", { name: /create group/i }).click();
    await expect(groupModal).not.toBeVisible({ timeout: 10000 });

    // 3. Open Create Expense Modal
    const groupCard = page.locator(".bg-card", { hasText: groupName });
    await groupCard.getByRole("button", { name: /add expense/i }).click();

    const expenseModal = page.getByRole("dialog", { name: /add new expense/i });
    await expect(expenseModal).toBeVisible();

    // 4. Fill basic info
    await expenseModal.locator('#description').fill("Exact Test");
    await expenseModal.locator('#amount').fill("100.00");

    // Verify equal split is default
    await expect(
      expenseModal.getByText(/each person pays: \$100.00/i),
    ).toBeVisible();

    // 5. Toggle to Exact
    await expenseModal.getByLabel(/exact/i).first().click();

    // 6. Verify Exact Split UI changes
    // "Each person pays" should be hidden
    await expect(expenseModal.getByText(/each person pays/i)).not.toBeVisible();

    // Share input for the current user should be visible
    // The current user's ID is not easily known here without checking the store,
    // but the component labels it "User X (You)". We can use a regex for "share".
    await expect(expenseModal.getByLabel(/split value/i).first()).toBeVisible();

    // Verify "Remaining" balance is shown
    await expect(expenseModal.getByText(/remaining: \$100\.00/i)).toBeVisible();

    // 7. Input shares and verify real-time updates
    const shareInputs = expenseModal.getByLabel(/split value/i);
    await shareInputs.first().fill("30");

    // Check remaining updates to 70
    await expect(expenseModal.getByText(/remaining: \$70\.00/i)).toBeVisible();

    // Fill the rest
    await shareInputs.first().fill("100");

    // Verify "Fully allocated" message
    await expect(expenseModal.getByText(/fully allocated/i)).toBeVisible();

    // 8. Verify Submission Guard & Payload
    const addButton = expenseModal.getByRole("button", {
      name: /add expense/i,
    });

    // Test disabled state when sum is wrong
    await shareInputs.first().fill("90");
    await expect(addButton).toBeDisabled();

    // Test enabled state when sum is right
    await shareInputs.first().fill("100");
    await expect(addButton).toBeEnabled();

    // Intercept API call
    const createRequestPromise = page.waitForRequest(
      (request) =>
        request.url().includes("/expenses") && request.method() === "POST",
    );

    await addButton.click();

    const request = await createRequestPromise;
    const payload = request.postDataJSON();

    // Verify payload structure
    expect(payload.splitType).toBe("EXACT");
    expect(payload.amount).toBe(100);
    expect(payload.splits[0].shareAmount).toBe(100);
    expect(payload.splits[0].splitType).toBe("EXACT");

    // Verify modal closes
    await expect(expenseModal).not.toBeVisible();
  });

  test("should allow a user to choose custom payer when creating and editing an expense", async ({
    browser,
  }) => {
    const ts = Date.now();
    const aliceName = `alice_payer_${ts}`;
    const bobName = `bob_payer_${ts}`;
    const password = "Password123!";

    const ctxAlice = await browser.newContext();
    const ctxBob = await browser.newContext();
    const pageAlice = await ctxAlice.newPage();
    const pageBob = await ctxBob.newPage();

    try {
      // 1. Register and Login Alice
      await pageAlice.goto("/register");
      await pageAlice.locator('#firstName').fill("Alice");
      await pageAlice.locator('#lastName').fill("User");
      await pageAlice.locator('#username').fill(aliceName);
      await pageAlice.locator('#email').fill(`${aliceName}@example.com`);
      await pageAlice.locator('#password').fill(password);
      await pageAlice.getByRole("button", { name: /register/i }).click();
      await expect(pageAlice).toHaveURL(/\/login/);
      await pageAlice.locator('#username').fill(aliceName);
      await pageAlice.locator('#password').fill(password);
      await pageAlice.getByRole("button", { name: /login/i }).click();
      await expect(pageAlice).toHaveURL(/\/$/);

      // 2. Register and Login Bob
      await pageBob.goto("/register");
      await pageBob.locator('#firstName').fill("Bob");
      await pageBob.locator('#lastName').fill("User");
      await pageBob.locator('#username').fill(bobName);
      await pageBob.locator('#email').fill(`${bobName}@example.com`);
      await pageBob.locator('#password').fill(password);
      await pageBob.getByRole("button", { name: /register/i }).click();
      await expect(pageBob).toHaveURL(/\/login/);
      await pageBob.locator('#username').fill(bobName);
      await pageBob.locator('#password').fill(password);
      await pageBob.getByRole("button", { name: /login/i }).click();
      await expect(pageBob).toHaveURL(/\/$/);

      // 3. Alice adds Bob as a friend (search lives on /friends)
      await pageAlice.goto("/friends");
      await pageAlice.getByPlaceholder(/search by name or email/i).fill(bobName);
      await pageAlice.getByRole("button", { name: /add friend/i }).first().click();

      // 4. Bob accepts friend request
      await pageBob.goto("/friends");
      await pageBob.getByText(/Alice User/i).waitFor({ state: "visible" });
      await pageBob.getByTitle("Accept").last().click();

      // 5. Alice creates a group with Bob
      await pageAlice.goto("/groups");
      await pageAlice.getByRole("button", { name: /create group/i }).first().click();
      const groupName = `Payer Group ${ts}`;
      await pageAlice.locator('#group-name').fill(groupName);
      await pageAlice.getByText("Bob User").click();
      await pageAlice.getByRole("button", { name: /create group/i }).last().click();
      await expect(pageAlice.getByText(groupName)).toBeVisible();

      // 6. Alice opens Add Expense Modal
      await openGroupDetails(pageAlice, groupName);
      await pageAlice.getByRole("button", { name: /add expense/i }).first().click();

      const expenseModal = pageAlice.getByRole("dialog", { name: /add new expense/i });
      await expect(expenseModal).toBeVisible();

      // 7. Verify "Paid By" select defaults to "You"
      await expect(expenseModal.locator('#paidBy')).toBeVisible();
      await expect(expenseModal.locator('#paidBy')).toHaveText(/You/);

      // 8. Alice records that Bob paid for it
      await expenseModal.locator('#paidBy').click();
      await pageAlice.getByRole('option', { name: /Bob User/i }).click();

      await expenseModal.locator('#description').fill("Dinner paid by Bob");
      await expenseModal.locator('#amount').fill("80.00");

      // Intercept POST request to verify paidBy in payload matches Bob's ID
      const createRequestPromise = pageAlice.waitForRequest(
        (request) =>
          request.url().includes("/expenses") && request.method() === "POST",
      );

      await expenseModal.getByRole("button", { name: /add expense/i }).last().click();

      const createRequest = await createRequestPromise;
      const createPayload = createRequest.postDataJSON();
      
      expect(createPayload.paidBy).toBeDefined();
      expect(createPayload.description).toBe("Dinner paid by Bob");
      expect(createPayload.amount).toBe(80.00);

      await expect(pageAlice.getByText(/Dinner paid by Bob/i).first()).toBeVisible();

      // 9. Now test editing the expense to change the payer
      await pageAlice.getByLabel(/Actions for/i).first().click();
      await pageAlice.getByRole("menuitem", { name: /Edit/i }).click();

      const editModal = pageAlice.getByRole("dialog", { name: /edit expense/i });
      await expect(editModal).toBeVisible();

      // Verify the payer dropdown defaults to Bob
      await expect(editModal.locator('#paidBy')).toHaveText(/Bob User/);

      // Change payer back to Alice ("You")
      await editModal.locator('#paidBy').click();
      await pageAlice.getByRole('option', { name: /Alice|You/i }).click();
      await editModal.locator('#description').fill("Dinner paid by Alice now");

      // Intercept PUT request to verify updated payer is sent
      const updateRequestPromise = pageAlice.waitForRequest(
        (request) =>
          request.url().includes("/expenses/") && request.method() === "PUT",
      );

      await editModal.getByRole("button", { name: /save changes/i }).click();

      const updateRequest = await updateRequestPromise;
      const updatePayload = updateRequest.postDataJSON();

      expect(updatePayload.paidBy).toBeDefined();
      expect(updatePayload.description).toBe("Dinner paid by Alice now");
    } finally {
      await ctxAlice.close();
      await ctxBob.close();
    }
  });
});
