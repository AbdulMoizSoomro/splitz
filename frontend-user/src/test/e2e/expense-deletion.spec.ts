import { waitForGroupDetails } from "./helpers/navigation";
import { test, expect } from '@playwright/test';

test.describe('Expense Deletion', () => {
  let username: string;
  let email: string;
  let groupName: string;

  test.beforeEach(async ({ page }) => {
    page.on("console", (msg) => console.log("BROWSER:", msg.text()));
    const random = Math.random().toString(36).substring(7);
    username = `user_${random}`;
    email = `${username}@example.com`;
    groupName = `Group ${random}`;

    // 1. Register & Login
    await page.goto('/register');
    await page.locator('#firstName').fill('Test');
    await page.locator('#lastName').fill('User');
    await page.locator('#username').fill(username);
    await page.locator('#email').fill(email);
    await page.locator('#password').fill('password123');
    await page.getByRole('button', { name: /register/i }).click();
    await expect(page).toHaveURL(/\/login/);

    await page.locator('#username').fill(username);
    await page.locator('#password').fill('password123');
    await page.getByRole('button', { name: /login/i }).click();
    await expect(page).toHaveURL(/\/$/);

    // 2. Create a group
    await page.goto('/groups');
    await page.getByRole('button', { name: /create group/i }).click();
    
    const modal = page.getByRole('dialog');
    await expect(modal).toBeVisible();
    
    await modal.locator('#group-name').fill(groupName);
    await modal.getByRole('button', { name: /create/i }).click();

    await expect(modal).not.toBeVisible({ timeout: 5000 });
    await page.getByText(groupName).click();
    await waitForGroupDetails(page);
    });

    test('should allow a user to delete their own expense', async ({ page }) => {
    // 3. Add an expense
    const addExpenseBtn = page.getByRole('button', { name: /add expense/i }).first();
    await expect(addExpenseBtn).toBeVisible({ timeout: 5000 });
    await addExpenseBtn.click();

    const expenseModal = page.getByRole('dialog');
    await expect(expenseModal).toBeVisible({ timeout: 5000 });

    await expenseModal.locator('#description').fill('Test Expense');
    await expenseModal.locator('#amount').fill('30');

    // Diagnostic: Check if members are selected (default should be the creator)
    const submitButton = expenseModal.getByRole('button', { name: /add expense/i });

    // If this fails, the button is disabled (likely due to validation or empty members)
    await expect(submitButton).toBeEnabled({ timeout: 5000 });
    await submitButton.click();

    await expect(expenseModal).not.toBeVisible({ timeout: 5000 });
    await expect(page.getByText('Test Expense').first()).toBeVisible({ timeout: 5000 });

    // 4. Delete the expense
    const actionButton = page.getByLabel(/actions for test expense/i).first();
    await expect(actionButton).toBeVisible({ timeout: 5000 });
    await actionButton.click();

    await page.getByRole('menuitem', { name: /delete/i }).click();
    // 5. Confirm deletion
    const confirmModal = page.getByRole('dialog').filter({ hasText: /delete expense/i });
    await expect(confirmModal).toBeVisible({ timeout: 5000 });
    await confirmModal.getByRole('button', { name: /delete expense/i }).click();

    // 6. Verify expense log entries exist
    await expect(confirmModal).not.toBeVisible({ timeout: 5000 });
    await expect(page.getByText(/you added "Test Expense"/i)).toBeVisible({ timeout: 5000 });
    await expect(page.getByText(/you deleted "Test Expense"/i)).toBeVisible({ timeout: 5000 });
  });
});
