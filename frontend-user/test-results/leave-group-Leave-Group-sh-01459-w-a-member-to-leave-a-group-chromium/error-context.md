# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: leave-group.spec.ts >> Leave Group >> should allow a member to leave a group
- Location: src/test/e2e/leave-group.spec.ts:42:3

# Error details

```
Error: expect(locator).toBeVisible() failed

Locator: getByText('Leave Test Group 1778378031798')
Expected: visible
Timeout: 10000ms
Error: element(s) not found

Call log:
  - Expect "toBeVisible" with timeout 10000ms
  - waiting for getByText('Leave Test Group 1778378031798')

```

# Test source

```ts
  1  | import { test, expect, type Page } from '@playwright/test';
  2  | 
  3  | const PASSWORD = 'Password123!';
  4  | 
  5  | async function registerUser(page: Page, username: string) {
  6  |   await page.goto('/register');
  7  |   await page.getByLabel(/first name/i).fill('Test');
  8  |   await page.getByLabel(/last name/i).fill('User');
  9  |   await page.getByLabel(/username/i).fill(username);
  10 |   await page.getByLabel(/email/i).fill(`${username}@example.com`);
  11 |   await page.getByLabel(/password/i).fill(PASSWORD);
  12 |   await page.getByRole('button', { name: /register/i }).click();
  13 |   await expect(page).toHaveURL(/\/login/, { timeout: 15000 });
  14 | }
  15 | 
  16 | async function loginUser(page: Page, username: string) {
  17 |   await page.goto('/login');
  18 |   await page.getByLabel(/username/i).fill(username);
  19 |   await page.getByLabel(/password/i).fill(PASSWORD);
  20 |   await page.getByRole('button', { name: /login/i }).click();
  21 |   await expect(page).toHaveURL(/\/$/, { timeout: 15000 });
  22 | }
  23 | 
  24 | /** Send a friend request from pageA to the given username. */
  25 | async function sendFriendRequest(pageA: Page, targetUsername: string) {
  26 |   await pageA.goto('/friends');
  27 |   await pageA.getByPlaceholder(/search by name or email/i).fill(targetUsername);
  28 |   await expect(pageA.getByText(`@${targetUsername}`)).toBeVisible({ timeout: 10000 });
  29 |   await pageA.getByRole('button', { name: /add friend/i }).click();
  30 |   await expect(pageA.getByText(/pending/i)).toBeVisible({ timeout: 5000 });
  31 | }
  32 | 
  33 | /** Accept the first pending friend request on pageB from the given sender. */
  34 | async function acceptFriendRequest(pageB: Page, fromUsername: string) {
  35 |   await pageB.goto('/friends');
  36 |   await expect(pageB.getByText(`@${fromUsername}`)).toBeVisible({ timeout: 10000 });
  37 |   await pageB.getByTitle('Accept').click();
  38 |   await expect(pageB.getByText(/no pending friend requests/i)).toBeVisible({ timeout: 5000 });
  39 | }
  40 | 
  41 | test.describe('Leave Group', () => {
  42 |   test('should allow a member to leave a group', async ({ browser }) => {
  43 |     const timestamp = Date.now();
  44 |     const ownerName = `owner_leave_${timestamp}`;
  45 |     const memberName = `member_leave_${timestamp}`;
  46 | 
  47 |     const ctxOwner = await browser.newContext();
  48 |     const ctxMember = await browser.newContext();
  49 |     const pageOwner = await ctxOwner.newPage();
  50 |     const pageMember = await ctxMember.newPage();
  51 | 
  52 |     try {
  53 |       // 1. Register and Login both users
  54 |       await registerUser(pageOwner, ownerName);
  55 |       await registerUser(pageMember, memberName);
  56 |       await loginUser(pageOwner, ownerName);
  57 |       await loginUser(pageMember, memberName);
  58 | 
  59 |       // 2. Establish friendship
  60 |       await sendFriendRequest(pageOwner, memberName);
  61 |       await acceptFriendRequest(pageMember, ownerName);
  62 | 
  63 |       // 3. Owner creates a group with the member
  64 |       await pageOwner.goto('/groups');
  65 |       await pageOwner.getByRole('button', { name: /create group/i }).first().click();
  66 |       let modal = pageOwner.getByRole('dialog');
  67 |       const groupName = `Leave Test Group ${timestamp}`;
  68 |       await modal.getByLabel(/group name/i).fill(groupName);
  69 |       
  70 |       // Select member in picker
  71 |       await modal.locator('.max-h-48').getByText(/test user/i).first().click();
  72 |       
  73 |       await modal.getByRole('button', { name: 'Create Group', exact: true }).click();
  74 |       await expect(modal).not.toBeVisible();
  75 | 
  76 |       // 4. Member navigates to the group
> 77 |       await expect(pageMember.getByText(groupName)).toBeVisible({ timeout: 10000 });
     |                                                     ^ Error: expect(locator).toBeVisible() failed
  78 |       await pageMember.getByText(groupName).click();
  79 |       await expect(pageMember).toHaveURL(/\/groups\/\d+/);
  80 | 
  81 |       // 5. Member clicks "Leave Group"
  82 |       await pageMember.getByRole('button', { name: /leave group/i }).click();
  83 |       modal = pageMember.getByRole('dialog');
  84 |       await expect(modal).toBeVisible();
  85 |       await expect(modal.getByText(/Are you sure you want to leave this group\?/i)).toBeVisible();
  86 | 
  87 |       // 6. Confirm Leave
  88 |       await modal.getByRole('button', { name: /^leave group$/i }).click();
  89 | 
  90 |       // 7. Verify redirect and group absence for member
  91 |       await expect(pageMember).toHaveURL(/\/groups/);
  92 |       await expect(pageMember.getByText(groupName)).not.toBeVisible();
  93 |     } finally {
  94 |       await ctxOwner.close();
  95 |       await ctxMember.close();
  96 |     }
  97 |   });
  98 | });
  99 | 
```