import { test, expect } from '@playwright/test';

test.describe('Stale Token Handling', () => {
  test('should redirect to login when token is stale (401/403)', async ({ page }) => {
    const timestamp = Date.now();
    const username = `stale_user_${timestamp}`;
    const email = `stale_${timestamp}@example.com`;
    const password = 'Password123!';

    // 1. Register and Login to get a valid session
    await page.goto('/register');
    await page.getByLabel(/first name/i).fill('Stale');
    await page.getByLabel(/last name/i).fill('User');
    await page.getByLabel(/username/i).fill(username);
    await page.getByLabel(/email/i).fill(email);
    await page.getByLabel(/password/i).fill(password);
    await page.getByRole('button', { name: /register/i }).click();

    await page.waitForURL(/\/login/);
    await page.getByLabel(/username/i).fill(username);
    await page.getByLabel(/password/i).fill(password);
    await page.getByRole('button', { name: /login/i }).click();

    await page.waitForURL(/\/$/);
    await expect(page.getByText(new RegExp(`Hi, ${username}`, 'i'))).toBeVisible();

    // 2. Simulate a stale token by corrupting it in localStorage
    // We need to keep the structure but make the token invalid
    await page.evaluate(() => {
      const authData = JSON.parse(localStorage.getItem('splitz-auth') || '{}');
      if (authData.state) {
        authData.state.token = 'invalid-stale-token';
        localStorage.setItem('splitz-auth', JSON.stringify(authData));
      }
    });

    // 3. Attempt an action that triggers an API call (e.g., refresh or navigate)
    // Refreshing the page will cause the components to fetch data (friends, friend requests)
    await page.reload();

    // 4. Verify that the user is redirected to login because the API calls returned 401/403
    // The axios interceptor should have triggered logout(), which updates the store and ProtectedRoute redirects.
    await expect(page).toHaveURL(/\/login/);
    
    // 5. Verify localStorage is cleared (or at least token is null)
    const finalAuthData = await page.evaluate(() => JSON.parse(localStorage.getItem('splitz-auth') || '{}'));
    expect(finalAuthData.state.token).toBeNull();
  });
});
