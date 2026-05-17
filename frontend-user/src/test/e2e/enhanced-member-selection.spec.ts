import { test, expect, type Page } from "@playwright/test";

const PASSWORD = "Password123!";

async function registerUser(
  page: Page,
  username: string,
  firstName: string,
) {
  await page.goto("/register");
  await page.getByLabel(/first name/i).fill(firstName);
  await page.getByLabel(/last name/i).fill("User");
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

test.describe("[E2E] Enhanced Member Selection", () => {
  test("should allow searching and adding users to a new group even if not friends", async ({
    browser,
  }) => {
    const ts = `${Date.now()}`;
    const aliceName = `alice_ems_${ts}`;
    const strangerName = `stranger_ems_${ts}`;

    const ctxAlice = await browser.newContext();
    const ctxStranger = await browser.newContext();
    const pageAlice = await ctxAlice.newPage();
    const pageStranger = await ctxStranger.newPage();

    try {
      await registerUser(pageAlice, aliceName, "Alice");
      await registerUser(pageStranger, strangerName, "Stranger");

      await loginUser(pageAlice, aliceName);

      // Alice creates group
      await pageAlice.goto("/groups");
      await pageAlice.getByRole("button", { name: /create group/i }).first().click();
      const modal = pageAlice.getByRole("dialog");
      await modal.getByLabel(/group name/i).fill(`Group EMS ${ts}`);

      // SEARCH FOR STRANGER
      // This is expected to fail currently as CreateGroupModal doesn't have search
      const searchInput = modal.getByPlaceholder(/search/i);
      await expect(searchInput).toBeVisible();
      await searchInput.fill(strangerName);

      // Should see Stranger in results
      await expect(modal.getByText(/Stranger User/i)).toBeVisible({ timeout: 10000 });
      await modal.getByText(/Stranger User/i).click();

      await modal.getByRole("button", { name: /create group/i }).click();
      await expect(modal).not.toBeVisible();

      // Verify Stranger is in the group
      await pageAlice.getByText(`Group EMS ${ts}`).click();
      await pageAlice.getByRole("button", { name: "Members", exact: true }).click();
      await expect(pageAlice.getByText(/Stranger User/i)).toBeVisible();

    } finally {
      await ctxAlice.close();
      await ctxStranger.close();
    }
  });
});
