import { test, expect } from "@playwright/test";

test.describe("Expense Management", () => {
  test("should allow a user to create an expense with equal split", async ({
    page,
  }) => {
    const timestamp = Date.now();
    const username = `user_exp_${timestamp}`;
    const email = `user_exp_${timestamp}@example.com`;
    const password = "Password123!";

    // 1. Register and Login
    await page.goto("/register");
    await page.getByLabel(/first name/i).fill("Exp");
    await page.getByLabel(/last name/i).fill("Tester");
    await page.getByLabel(/username/i).fill(username);
    await page.getByLabel(/email/i).fill(email);
    await page.getByLabel(/password/i).fill(password);
    await page.getByRole("button", { name: /register/i }).click();

    await expect(page).toHaveURL(/\/login/, { timeout: 10000 });
    await page.getByLabel(/username/i).fill(username);
    await page.getByLabel(/password/i).fill(password);
    await page.getByRole("button", { name: /login/i }).click();
    await expect(page).toHaveURL(/\/$/, { timeout: 10000 });

    // 2. Go to Groups page and create a group
    await page.goto("/groups");
    await page.getByRole("button", { name: /create group/i }).click();

    const groupName = `Group Exp ${timestamp}`;
    const groupModal = page.getByRole("dialog");
    await groupModal.getByLabel(/group name/i).fill(groupName);
    await groupModal.getByRole("button", { name: /create group/i }).click();
    await expect(groupModal).not.toBeVisible();

    // 3. Open Create Expense Modal from the new group card
    const groupCard = page.locator(".bg-white", { hasText: groupName });
    await groupCard.getByRole("button", { name: /add expense/i }).click();

    const expenseModal = page.getByRole("dialog", { name: /add new expense/i });
    await expect(expenseModal).toBeVisible();

    // 4. Fill expense form
    await expenseModal.getByLabel(/description/i).fill("Team Lunch");
    await expenseModal.getByLabel(/amount/i).fill("45.00");

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
    const timestamp = Date.now();
    const username = `user_exact_${timestamp}`;
    const email = `user_exact_${timestamp}@example.com`;
    const password = "Password123!";

    // 1. Register and Login
    await page.goto("/register");
    await page.getByLabel(/first name/i).fill("Exact");
    await page.getByLabel(/last name/i).fill("Tester");
    await page.getByLabel(/username/i).fill(username);
    await page.getByLabel(/email/i).fill(email);
    await page.getByLabel(/password/i).fill(password);
    await page.getByRole("button", { name: /register/i }).click();

    await expect(page).toHaveURL(/\/login/);
    await page.getByLabel(/username/i).fill(username);
    await page.getByLabel(/password/i).fill(password);
    await page.getByRole("button", { name: /login/i }).click();
    await expect(page).toHaveURL(/\/$/);

    // 2. Go to Groups page and create a group
    await page.goto("/groups");
    await page.getByRole("button", { name: /create group/i }).click();

    const groupName = `Group Exact ${timestamp}`;
    const groupModal = page.getByRole("dialog");
    await groupModal.getByLabel(/group name/i).fill(groupName);
    await groupModal.getByRole("button", { name: /create group/i }).click();
    await expect(groupModal).not.toBeVisible();

    // 3. Open Create Expense Modal
    const groupCard = page.locator(".bg-white", { hasText: groupName });
    await groupCard.getByRole("button", { name: /add expense/i }).click();

    const expenseModal = page.getByRole("dialog", { name: /add new expense/i });
    await expect(expenseModal).toBeVisible();

    // 4. Fill basic info
    await expenseModal.getByLabel(/description/i).fill("Exact Test");
    await expenseModal.getByLabel(/amount/i).fill("100.00");

    // Verify equal split is default
    await expect(
      expenseModal.getByText(/each person pays: \$100.00/i),
    ).toBeVisible();

    // 5. Toggle to Exact
    await expenseModal.getByLabel(/exact/i).click();

    // 6. Verify Exact Split UI changes
    // "Each person pays" should be hidden
    await expect(expenseModal.getByText(/each person pays/i)).not.toBeVisible();

    // Share input for the current user should be visible
    // The current user's ID is not easily known here without checking the store,
    // but the component labels it "User X (You)". We can use a regex for "share".
    await expect(expenseModal.getByLabel(/share/i)).toBeVisible();

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
});
