import { test, expect } from '@playwright/test';

test.describe('Activity Logging', () => {
  let username: string;
  let email: string;
  let groupName: string;

  test.beforeEach(async ({ page }) => {
    page.on('console', msg => console.log('BROWSER:', msg.text()));
    page.on('pageerror', err => console.log('BROWSER ERROR:', err.message));

    const random = Math.random().toString(36).substring(7);
    username = `user_${random}`;
    email = `${username}@example.com`;
    groupName = `Group ${random}`;

    // Register & Login
    console.log('Navigating to /register');
    await page.goto('/register');
    await page.waitForLoadState('networkidle');
    
    console.log('Filling registration form');
    const firstNameInput = page.locator('#firstName');
    await expect(firstNameInput).toBeVisible({ timeout: 10000 });
    await firstNameInput.fill('Activity');
    
    await page.locator('#lastName').fill('Tester');
    await page.locator('#username').fill(username);
    await page.locator('#email').fill(email);
    await page.locator('#password').fill('password123');
    await page.getByRole('button', { name: /register/i }).click();
    
    console.log('Waiting for redirect to /login');
    await expect(page).toHaveURL(/\/login/, { timeout: 10000 });

    await page.locator('#username').fill(username);
    await page.locator('#password').fill('password123');
    await page.getByRole('button', { name: /login/i }).click();
    
    console.log('Waiting for redirect to home');
    await expect(page).toHaveURL(/\/$/, { timeout: 10000 });

    // Create a group
    await page.goto('/groups');
    await page.getByRole('button', { name: /create group/i }).click();
    const modal = page.getByRole('dialog');
    await modal.locator('#group-name').fill(groupName);
    await modal.getByRole('button', { name: /create/i }).click();
    await expect(modal).not.toBeVisible();
    await page.getByText(groupName).click();
  });

  test('should log creation and deletion of expenses', async ({ page }) => {
    // 1. Create an expense
    await page.getByRole('button', { name: /add expense/i }).first().click();
    const expenseModal = page.getByRole('dialog');
    await expenseModal.locator('#description').fill('Lunch');
    await expenseModal.locator('#amount').fill('20');
    await expenseModal.getByRole('button', { name: /add expense/i }).click();

    // Verify "added Lunch" log entry
    await expect(page.getByText(/you added "Lunch"/i)).toBeVisible({ timeout: 10000 });

    // 2. Delete the expense
    await page.getByLabel(/actions for Lunch/i).first().click();
    await page.getByRole('menuitem', { name: /delete/i }).click();
    const confirmModal = page.getByRole('dialog').filter({ hasText: /delete expense/i });
    await confirmModal.getByRole('button', { name: /delete expense/i }).click();

    // Verify "deleted Lunch" log entry
    await expect(page.getByText(/you deleted "Lunch"/i)).toBeVisible({ timeout: 10000 });
    
    // Verify both logs exist (chronological order)
    await expect(page.getByText(/you added "Lunch"/i)).toBeVisible();
    await expect(page.getByText(/you deleted "Lunch"/i)).toBeVisible();
  });

  test('should log updates with diffs', async ({ page }) => {
    // 1. Create an expense
    await page.getByRole('button', { name: /add expense/i }).first().click();
    const expenseModal = page.getByRole('dialog');
    await expenseModal.locator('#description').fill('Initial');
    await expenseModal.locator('#amount').fill('10');
    await expenseModal.getByRole('button', { name: /add expense/i }).click();
    await expect(page.getByText(/you added "Initial"/i)).toBeVisible();

    // 2. Update the expense
    await page.getByLabel(/actions for Initial/i).first().click();
    await page.getByRole('menuitem', { name: /edit/i }).click();
    
    const editModal = page.getByRole('dialog');
    await editModal.locator('#description').fill('Updated');
    await editModal.locator('#amount').fill('25');
    await editModal.getByRole('button', { name: /save changes/i }).click();

    // 3. Verify update log with diff
    await expect(page.getByText(/you updated "Updated"/i)).toBeVisible({ timeout: 10000 });
    await expect(page.getByText(/description: Initial -> Updated/i)).toBeVisible();
    await expect(page.getByText(/amount: 10.00 -> 25.00/i)).toBeVisible();
  });
});
