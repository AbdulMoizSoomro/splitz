import { test, expect } from '@playwright/test';

test.describe('Group Management', () => {
  test('should allow a user to create a new group', async ({ page }) => {
    const timestamp = Date.now();
    const username = `user_groups_${timestamp}`;
    const email = `user_groups_${timestamp}@example.com`;
    const password = 'Password123!';

    // 1. Register and Login
    await page.goto('/register');
    console.log('On Register Page');
    await page.getByLabel(/first name/i).fill('Group');
    await page.getByLabel(/last name/i).fill('Tester');
    await page.getByLabel(/username/i).fill(username);
    await page.getByLabel(/email/i).fill(email);
    await page.getByLabel(/password/i).fill(password);
    console.log('Submitting Registration');
    await page.getByRole('button', { name: /register/i }).click();

    await expect(page).toHaveURL(/\/login/, { timeout: 10000 });
    console.log('On Login Page');
    await page.getByLabel(/username/i).fill(username);
    await page.getByLabel(/password/i).fill(password);
    console.log('Submitting Login');
    await page.getByRole('button', { name: /login/i }).click();
    
    // Check for errors on page
    const errorLocator = page.locator('.text-red-600');
    if (await errorLocator.isVisible()) {
        console.log('Error visible on page:', await errorLocator.innerText());
    }

    await expect(page).toHaveURL(/\/$/, { timeout: 10000 });
    console.log('On Dashboard');

    // 2. Go to Groups page
    await page.goto('/groups');
    await expect(page).toHaveURL(/\/groups/);
    await expect(page.getByText(/your groups/i)).toBeVisible();

    // 3. Open Create Group Modal
    console.log('Opening Create Group Modal');
    await page.getByRole('button', { name: /create group/i }).click();
    const modal = page.getByRole('dialog');
    await expect(modal).toBeVisible();
    await expect(modal.getByText(/create new group/i)).toBeVisible();

    // 4. Fill group form
    const groupName = `Group ${timestamp}`;
    console.log('Filling group form:', groupName);
    await modal.getByLabel(/group name/i).fill(groupName);
    await modal.getByLabel(/description/i).fill('Testing group creation');
    
    // Explicitly find the submit button in the modal
    const submitButton = modal.getByRole('button', { name: /create group/i });
    console.log('Submitting Group Creation');
    await submitButton.click();

    // Check for errors on page after group creation attempt
    if (await errorLocator.isVisible()) {
        console.log('Error visible on page after group creation:', await errorLocator.innerText());
    }

    // 5. Verify group is created and visible in list
    console.log('Waiting for group name to appear in list');
    // The modal should close upon success
    await expect(modal).not.toBeVisible({ timeout: 10000 });
    await expect(page.getByText(groupName)).toBeVisible({ timeout: 10000 });
    await expect(page.getByText(/testing group creation/i)).toBeVisible();
  });
});
