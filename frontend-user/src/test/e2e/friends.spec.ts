import { test, expect, type Page } from '@playwright/test';

async function registerAndLogin(page: Page, username: string) {
  const email = `${username}@example.com`;
  const password = 'Password123!';

  await page.goto('/register');
  await page.getByLabel(/first name/i).fill('Test');
  await page.getByLabel(/last name/i).fill('User');
  await page.getByLabel(/username/i).fill(username);
  await page.getByLabel(/email/i).fill(email);
  await page.getByLabel(/password/i).fill(password);
  await page.getByRole('button', { name: /register/i }).click();

  await expect(page).toHaveURL(/\/login/);
  await page.getByLabel(/username/i).fill(username);
  await page.getByLabel(/password/i).fill(password);
  await page.getByRole('button', { name: /login/i }).click();
  await expect(page).toHaveURL(/\/$/);
  await expect(page.getByText(new RegExp(`Hi, ${username}`, 'i'))).toBeVisible();
}

test.describe('Friend Request Management', () => {
  test('should allow sending, accepting, viewing, and removing friends', async ({ browser }) => {
    const timestamp = Date.now();
    const usernameA = `userA_${timestamp}`;
    const usernameB = `userB_${timestamp}`;

    const contextA = await browser.newContext();
    const contextB = await browser.newContext();
    const pageA = await contextA.newPage();
    const pageB = await contextB.newPage();

    // 1. Register and Login both users
    await registerAndLogin(pageA, usernameA);
    await registerAndLogin(pageB, usernameB);

    // 2. User A navigates to Friends page and searches for User B
    await pageA.goto('/friends');
    await pageA.getByPlaceholder(/search by name or email/i).fill(usernameB);
    await expect(pageA.getByText(`@${usernameB}`)).toBeVisible();

    // 3. User A sends friend request
    await pageA.getByRole('button', { name: /add friend/i }).click();
    await expect(pageA.getByText(/pending/i)).toBeVisible();

    // 4. User B should see the friend request on Friends page
    await pageB.goto('/friends');
    await expect(pageB.getByText(`@${usernameA}`)).toBeVisible();

    // 5. User B accepts the friend request
    await pageB.getByTitle('Accept').click();
    await expect(pageB.getByText(/no pending friend requests/i)).toBeVisible();

    // 6. User B should see User A in friends list
    await expect(pageB.getByText(`@${usernameA}`)).toBeVisible();

    // 7. User A should see User B in friends list
    await pageA.reload();
    await expect(pageA.getByText(`@${usernameB}`)).toBeVisible();
    
    // 8. User A should see "Friends" status in search results
    await pageA.getByPlaceholder(/search by name or email/i).fill(usernameB);
    await expect(pageA.getByText(/friends/i).first()).toBeVisible();

    // 9. User A removes User B
    pageA.on('dialog', dialog => dialog.accept());
    await pageA.getByTitle('Remove Friend').click();
    await expect(pageA.getByText(/no friends added yet/i)).toBeVisible();

    // 10. User B should also see User A removed
    await pageB.reload();
    await expect(pageB.getByText(/no friends added yet/i)).toBeVisible();

    await contextA.close();
    await contextB.close();
  });

  test('should allow rejecting a friend request', async ({ browser }) => {
    const timestamp = Date.now();
    const usernameC = `userC_${timestamp}`;
    const usernameD = `userD_${timestamp}`;

    const contextC = await browser.newContext();
    const contextD = await browser.newContext();
    const pageC = await contextC.newPage();
    const pageD = await contextD.newPage();

    await registerAndLogin(pageC, usernameC);
    await registerAndLogin(pageD, usernameD);

    // User C sends request to User D
    await pageC.goto('/friends');
    await pageC.getByPlaceholder(/search by name or email/i).fill(usernameD);
    await pageC.getByRole('button', { name: /add friend/i }).click();

    // User D rejects
    await pageD.goto('/friends');
    await expect(pageD.getByText(`@${usernameC}`)).toBeVisible();
    await pageD.getByTitle('Reject').click();
    await expect(pageD.getByText(/no pending friend requests/i)).toBeVisible();

    // User D should NOT have User C in friends list
    await expect(pageD.getByText(`@${usernameC}`)).not.toBeVisible();

    await contextC.close();
    await contextD.close();
  });
});
