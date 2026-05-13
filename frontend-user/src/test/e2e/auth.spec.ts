import { test, expect } from "@playwright/test";

test.describe("Authentication Flow", () => {
  test("should allow a user to register and then login", async ({ page }) => {
    const timestamp = `${Date.now()}_${Math.floor(Math.random() * 10000)}`;
    page.on("console", (msg) => console.log("BROWSER:", msg.text()));
    const username = `user_${timestamp}`;
    const email = `user_${timestamp}@example.com`;
    const password = "Password123!";

    // 1. Go to Register page
    await page.goto("/register");
    await expect(page).toHaveTitle(/Splitz/);

    // 2. Fill registration form
    await page.getByLabel(/first name/i).fill("Test");
    await page.getByLabel(/last name/i).fill("User");
    await page.getByLabel(/username/i).fill(username);
    await page.getByLabel(/email/i).fill(email);
    await page.getByLabel(/password/i).fill(password);
    await page.getByRole("button", { name: /register/i }).click();

    // 3. Should be redirected to login
    await expect(page).toHaveURL(/\/login/);

    // 4. Fill login form
    await page.getByLabel(/username/i).fill(username);
    await page.getByLabel(/password/i).fill(password);
    await page.getByRole("button", { name: /login/i }).click();

    // 5. Should be redirected to dashboard
    await expect(page).toHaveURL(/\/$/);
    await expect(
      page.getByText(new RegExp(`Hi, ${username}`, "i")),
    ).toBeVisible();

    // 6. Logout
    await page.getByRole("button", { name: /logout/i }).click();
    await expect(page).toHaveURL(/\/login/);
  });
});
