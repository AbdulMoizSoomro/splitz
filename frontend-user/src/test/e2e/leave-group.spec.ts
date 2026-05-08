import { test, expect } from '@playwright/test';

test.describe('Leave Group', () => {
  test('should allow a user to leave a group', async ({ page }) => {
    const timestamp = Date.now();
    const username = `leave_user_${timestamp}`;
    const email = `leave_user_${timestamp}@example.com`;
    const password = 'Password123!';

    // 1. Register and Login
    await page.goto('/register');
    await page.getByLabel(/first name/i).fill('Leave');
    await page.getByLabel(/last name/i).fill('Tester');
    await page.getByLabel(/username/i).fill(username);
    await page.getByLabel(/email/i).fill(email);
    await page.getByLabel(/password/i).fill(password);
    await page.getByRole('button', { name: /register/i }).click();

    await expect(page).toHaveURL(/\/login/, { timeout: 15000 });
    await page.getByLabel(/username/i).fill(username);
    await page.getByLabel(/password/i).fill(password);
    await page.getByRole('button', { name: /login/i }).click();
    await expect(page).toHaveURL(/\/$/);

    // 2. Create a group
    await page.goto('/groups');
    await page.getByRole('button', { name: /create group/i }).first().click();
    let modal = page.getByRole('dialog');
    const groupName = `Leave Group Test ${timestamp}`;
    await modal.getByLabel(/group name/i).fill(groupName);
    await modal.getByRole('button', { name: /create group/i }).click();
    await expect(page.getByText(groupName)).toBeVisible();

    // 3. Navigate to group details
    await page.getByText(groupName).click();
    await expect(page).toHaveURL(/\/groups\/\d+/);
    await expect(page.getByText(groupName)).toBeVisible();

    // 4. Click Leave Group
    await page.getByRole('button', { name: /leave group/i }).click();
    modal = page.getByRole('dialog');
    await expect(modal).toBeVisible();
    await expect(modal.getByText(/Are you sure you want to leave this group\?/i)).toBeVisible();

    // 5. Confirm Leave
    await modal.getByRole('button', { name: /^leave group$/i }).click();

    // 6. Verify redirect and group absence
    await expect(page).toHaveURL(/\/groups/);
    await expect(page.getByText(groupName)).not.toBeVisible();
  });
});
