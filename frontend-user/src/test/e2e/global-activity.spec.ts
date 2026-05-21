import { test, expect } from "@playwright/test";

test.describe("Global Activity Dashboard Page", () => {
  let username: string;
  let email: string;
  let groupName: string;

  test.beforeEach(async ({ page }) => {
    page.on("console", (msg) => console.log("BROWSER:", msg.text()));
    page.on("pageerror", (err) => console.log("BROWSER ERROR:", err.message));

    const random = Math.random().toString(36).substring(7);
    username = `user_${random}`;
    email = `${username}@example.com`;
    groupName = `Group ${random}`;

    // Register & Login
    await page.goto("/register");
    await page.waitForLoadState("networkidle");

    await page.getByLabel(/first name/i).fill("Global");
    await page.getByLabel(/last name/i).fill("Tester");
    await page.getByLabel(/username/i).fill(username);
    await page.getByLabel(/email/i).fill(email);
    await page.getByLabel(/^password$/i).fill("password123");
    await page.getByRole("button", { name: /register/i }).click();

    await expect(page).toHaveURL(/\/login/, { timeout: 10000 });

    await page.getByLabel(/username/i).fill(username);
    await page.getByLabel(/password/i).fill("password123");
    await page.getByRole("button", { name: /login/i }).click();

    await expect(page).toHaveURL(/\/$/, { timeout: 10000 });

    // Create a group
    await page.goto("/groups");
    await page.getByRole("button", { name: /create group/i }).click();
    const modal = page.getByRole("dialog");
    await modal.getByLabel(/group name/i).fill(groupName);
    await modal.getByRole("button", { name: /create/i }).click();
    await expect(modal).not.toBeVisible();
    await page.getByText(groupName).click();
  });

  test("should render the activity list with full details, operational sidebar, and links back to the group", async ({ page }) => {
    // 1. Add an expense
    await page.getByRole("button", { name: /add expense/i }).first().click();
    const expenseModal = page.getByRole("dialog");
    await expenseModal.getByLabel(/description/i).fill("Dinner");
    await expenseModal.getByLabel(/amount/i).fill("60.00");
    await expenseModal.getByRole("button", { name: /add expense/i }).click();
    await expect(expenseModal).not.toBeVisible();

    // 2. Navigate to the Global Activity Page via Sidebar
    // Wait for the side menu button or navigate directly to /activity
    await page.goto("/activity");

    // 3. Verify that the Sidebar layout remains fully operational & visible
    await expect(page.locator('aside[aria-label="Sidebar"]')).toBeVisible({ timeout: 10000 });
    await expect(page.getByText("Shared Activity")).toBeVisible();

    // 4. Verify that the activity entry shows amount, paid/owe details, and date & time
    await expect(page.getByText(/you paid for "Dinner"/i)).toBeVisible();
    
    // We should see "You paid $60.00 (you lent $0.00)" or similar details depending on splits
    await expect(page.getByText(/you paid \$60\.00/i)).toBeVisible();

    // Verify presence of dates/times (Calendar/Clock text)
    const now = new Date();
    const currentYear = now.getFullYear().toString();
    await expect(page.getByText(currentYear)).toBeVisible();

    // 5. Verify the group button is visible, and clicking it takes you back to that group!
    const groupPill = page.getByRole("button", { name: groupName });
    await expect(groupPill).toBeVisible();
    await groupPill.click();

    // Verify redirected back to the group detail page
    await expect(page).toHaveURL(/\/groups\/\d+/);
    await expect(page.getByRole("heading", { name: groupName })).toBeVisible();
  });
});
