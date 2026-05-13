import { test, expect, type Page } from "@playwright/test";

const PASSWORD = "Password123!";

async function registerUser(
  page: Page,
  username: string,
  firstName: string,
  lastName: string,
) {
  await page.goto("/register");
  await page.getByLabel(/first name/i).fill(firstName);
  await page.getByLabel(/last name/i).fill(lastName);
  await page.getByLabel(/username/i).fill(username);
  await page.getByLabel(/email/i).fill(`${username}@example.com`);
  await page.getByLabel(/password/i).fill(PASSWORD);
  await page.getByRole("button", { name: /register/i }).click();
  await expect(page).toHaveURL(/\/login/);
}

async function loginUser(page: Page, username: string) {
  await page.goto("/login");
  await page.getByLabel(/username/i).fill(username);
  await page.getByLabel(/password/i).fill(PASSWORD);
  await page.getByRole("button", { name: /login/i }).click();
  await expect(page).toHaveURL(/\/$/);
}

async function sendFriendRequest(pageA: Page, targetUsername: string) {
  await pageA.goto("/friends");
  await pageA.getByPlaceholder(/search by name or email/i).fill(targetUsername);
  await expect(pageA.getByText(`@${targetUsername}`)).toBeVisible();
  await pageA.getByRole("button", { name: /add friend/i }).click();
  await expect(pageA.getByText(/pending/i)).toBeVisible();
}

async function acceptFriendRequest(pageB: Page, fromUsername: string) {
  await pageB.goto("/friends");
  await expect(pageB.getByText(`@${fromUsername}`)).toBeVisible();
  await pageB.getByTitle("Accept").click();
  await expect(pageB.getByText(/no pending friend requests/i)).toBeVisible();
}

async function createGroupWithMember(
  pageOwner: Page,
  groupName: string,
  friendDisplayFirstName: string,
): Promise<string> {
  await pageOwner.goto("/groups");
  await pageOwner
    .getByRole("button", { name: /create group/i })
    .first()
    .click();
  const modal = pageOwner.getByRole("dialog");
  await expect(modal).toBeVisible();
  await modal.getByLabel(/group name/i).fill(groupName);

  const friendPicker = modal.locator(".max-h-48");
  const friendEntry = friendPicker.getByText(
    new RegExp(friendDisplayFirstName, "i"),
  );
  await expect(friendEntry).toBeVisible();
  await friendEntry.first().click();

  await modal.getByRole("button", { name: /create group/i }).click();
  await expect(modal).not.toBeVisible();

  await expect(pageOwner.getByText(groupName)).toBeVisible();
  await pageOwner.getByText(groupName).click();
  await expect(pageOwner).toHaveURL(/\/groups\/\d+/);

  return pageOwner.url();
}

test.describe("[E2E] Temporary Friends List", () => {
  test("should show temporary friends with group badges and allow request cancellation", async ({
    browser,
  }) => {
    const ts = `${Date.now()}_${Math.floor(Math.random() * 10000)}`;
    const aliceName = `alice_${ts}`;
    const bobName = `bob_${ts}`;

    const ctxAlice = await browser.newContext();
    const ctxBob = await browser.newContext();
    const pageAlice = await ctxAlice.newPage();
    const pageBob = await ctxBob.newPage();

    try {
      // 1. Register Alice and Bob
      await registerUser(pageAlice, aliceName, "Alice", "User");
      await registerUser(pageBob, bobName, "Bob", "User");

      // 2. Login both
      await loginUser(pageAlice, aliceName);
      await loginUser(pageBob, bobName);

      // 3. Make them friends
      await sendFriendRequest(pageAlice, bobName);
      await acceptFriendRequest(pageBob, aliceName);

      // 4. Alice creates a group with Bob
      const groupName = `Shared Group ${ts}`;
      await createGroupWithMember(pageAlice, groupName, "Bob");

      // 5. Alice adds an expense (Alice pays $10, shared with Bob)
      //    Bob will owe Alice $5
      await pageAlice.goto("/groups");
      const groupCard = pageAlice.locator(".bg-white", { hasText: groupName });
      await groupCard.getByRole("button", { name: /add expense/i }).click();

      const expenseModal = pageAlice.getByRole("dialog", {
        name: /add new expense/i,
      });
      await expect(expenseModal).toBeVisible();
      await expenseModal.getByLabel(/description/i).fill("Lunch");
      await expenseModal.getByLabel(/amount/i).fill("10.00");
      await expenseModal.getByRole("button", { name: /add expense/i }).click();
      await expect(expenseModal).not.toBeVisible();

      // 6. Alice removes Bob from friends
      await pageAlice.goto("/friends");
      await expect(pageAlice.getByText(`@${bobName}`)).toBeVisible();
      pageAlice.once("dialog", (dialog) => dialog.accept());
      await pageAlice.getByTitle("Remove Friend").click();
      await expect(pageAlice.getByText(/no friends added yet/i)).toBeVisible();

      // 7. Alice should now see Bob in the "Temporary Friends" list
      await expect(pageAlice.getByText("Temporary Friends")).toBeVisible();
      const tempFriendCard = pageAlice.locator(".bg-orange-50\\/30");
      await expect(tempFriendCard.getByText(bobName)).toBeVisible();

      // 8. Verify the group badge is visible
      await expect(tempFriendCard.getByText(groupName)).toBeVisible();

      // 9. Alice sends a friend request to Bob from the temp friends list
      await tempFriendCard.getByRole("button", { name: /add friend/i }).click();

      // 10. Verify "Cancel Request" button appears
      await expect(
        tempFriendCard.getByRole("button", { name: /cancel request/i }),
      ).toBeVisible();

      // 11. Alice cancels the request
      await tempFriendCard
        .getByRole("button", { name: /cancel request/i })
        .click();

      // 12. Verify "Add Friend" button returns
      await expect(
        tempFriendCard.getByRole("button", { name: /add friend/i }),
      ).toBeVisible();
    } finally {
      await ctxAlice.close();
      await ctxBob.close();
    }
  });
});
