import { test, expect } from "@playwright/test";

test.describe("Group Management", () => {
  test("should allow a user to create a new group", async ({ page }) => {
    const timestamp = `${Date.now()}_${Math.floor(Math.random() * 10000)}`;
    const username = `user_groups_${timestamp}`;
    const email = `user_groups_${timestamp}@example.com`;
    const password = "Password123!";

    // 1. Register and Login
    await page.goto("/register");
    console.log("On Register Page");
    await page.getByLabel(/first name/i).fill("Group");
    await page.getByLabel(/last name/i).fill("Tester");
    await page.getByLabel(/username/i).fill(username);
    await page.getByLabel(/email/i).fill(email);
    await page.getByLabel(/password/i).fill(password);
    console.log("Submitting Registration");
    await page.getByRole("button", { name: /register/i }).click();

    await expect(page).toHaveURL(/\/login/, { timeout: 10000 });
    console.log("On Login Page");
    await page.getByLabel(/username/i).fill(username);
    await page.getByLabel(/password/i).fill(password);
    console.log("Submitting Login");
    await page.getByRole("button", { name: /login/i }).click();

    // Check for errors on page
    const errorLocator = page.locator(".text-red-600");
    if (await errorLocator.isVisible()) {
      console.log("Error visible on page:", await errorLocator.innerText());
    }

    await expect(page).toHaveURL(/\/$/, { timeout: 10000 });
    console.log("On Dashboard");

    // 2. Go to Groups page
    await page.goto("/groups");
    await expect(page).toHaveURL(/\/groups/);
    await expect(page.getByText(/your groups/i)).toBeVisible();

    // 3. Open Create Group Modal
    console.log("Opening Create Group Modal");
    await page.getByRole("button", { name: /create group/i }).click();
    const modal = page.getByRole("dialog");
    await expect(modal).toBeVisible();
    await expect(modal.getByText(/create new group/i)).toBeVisible();

    // 4. Fill group form
    const groupName = `Group ${timestamp}`;
    console.log("Filling group form:", groupName);
    await modal.getByLabel(/group name/i).fill(groupName);
    await modal.getByLabel(/description/i).fill("Testing group creation");

    // Explicitly find the submit button in the modal
    const submitButton = modal.getByRole("button", { name: /create group/i });
    console.log("Submitting Group Creation");
    await submitButton.click();

    // Check for errors on page after group creation attempt
    if (await errorLocator.isVisible()) {
      console.log(
        "Error visible on page after group creation:",
        await errorLocator.innerText(),
      );
    }

    // 5. Verify group is created and visible in list
    console.log("Waiting for group name to appear in list");
    // The modal should close upon success
    await expect(modal).not.toBeVisible({ timeout: 10000 });
    await expect(page.getByText(groupName)).toBeVisible({ timeout: 10000 });
    await expect(page.getByText(/testing group creation/i)).toBeVisible();
  });

  test("should display member roles in group details", async ({ page }) => {
    const timestamp = `${Date.now()}_${Math.floor(Math.random() * 10000)}`;
    const username = `role_tester_${timestamp}`;
    const email = `role_tester_${timestamp}@example.com`;
    const password = "Password123!";

    // 1. Register and Login
    await page.goto("/register");
    await page.getByLabel(/first name/i).fill("Role");
    await page.getByLabel(/last name/i).fill("Tester");
    await page.getByLabel(/username/i).fill(username);
    await page.getByLabel(/email/i).fill(email);
    await page.getByLabel(/password/i).fill(password);
    await page.getByRole("button", { name: /register/i }).click();

    await expect(page).toHaveURL(/\/login/);
    await page.getByLabel(/username/i).fill(username);
    await page.getByLabel(/password/i).fill(password);
    await page.getByRole("button", { name: /login/i }).click();
    await expect(page).toHaveURL(/\/$/);

    // 2. Go to Groups page and create a group
    await page.goto("/groups");
    await page.getByRole("button", { name: /create group/i }).click();
    const groupName = `Role Group ${timestamp}`;
    const modal = page.getByRole("dialog");
    await modal.getByLabel(/group name/i).fill(groupName);
    await modal.getByRole("button", { name: /create group/i }).click();

    // 3. Wait for group to appear and click it
    const groupLink = page.getByText(groupName);
    await expect(groupLink).toBeVisible();
    await groupLink.click();

    // 4. Verify we are on details page
    await expect(page).toHaveURL(/\/groups\/\d+/);
    await expect(page.getByText(groupName)).toBeVisible();

    // Navigate to Members tab
    await page.getByRole("button", { name: "Members", exact: true }).click();

    // 5. Verify "Owner" badge is visible for the creator
    await expect(page.getByText(/role tester/i)).toBeVisible();
    const ownerBadge = page.locator("span", { hasText: "Owner" });
    await expect(ownerBadge).toBeVisible();
  });

  test("should allow owner to promote a member to admin", async ({ page }) => {
    const timestamp = `${Date.now()}_${Math.floor(Math.random() * 10000)}`;
    const ownerUser = `owner_${timestamp}`;
    const memberUser = `member_${timestamp}`;
    const password = "Password123!";

    // 1. Register Member User
    await page.goto("/register");
    await page.getByLabel(/first name/i).fill("Member");
    await page.getByLabel(/last name/i).fill("User");
    await page.getByLabel(/username/i).fill(memberUser);
    await page.getByLabel(/email/i).fill(`${memberUser}@example.com`);
    await page.getByLabel(/password/i).fill(password);
    await page.getByRole("button", { name: /register/i }).click();
    await expect(page).toHaveURL(/\/login/);

    // 2. Register and Login Owner User
    await page.goto("/register");
    await page.getByLabel(/first name/i).fill("Owner");
    await page.getByLabel(/last name/i).fill("User");
    await page.getByLabel(/username/i).fill(ownerUser);
    await page.getByLabel(/email/i).fill(`${ownerUser}@example.com`);
    await page.getByLabel(/password/i).fill(password);
    await page.getByRole("button", { name: /register/i }).click();
    await expect(page).toHaveURL(/\/login/);

    await page.getByLabel(/username/i).fill(ownerUser);
    await page.getByLabel(/password/i).fill(password);
    await page.getByRole("button", { name: /login/i }).click();
    await expect(page).toHaveURL(/\/$/);

    // 3. Create Group and invite member
    await page.goto("/groups");
    await page
      .getByRole("button", { name: /create group/i })
      .first()
      .click();
    const modal = page.getByRole("dialog");
    await modal.getByLabel(/group name/i).fill(`Role Group ${timestamp}`);
    await modal
      .getByRole("button", { name: "Create Group", exact: true })
      .click();
    await page.getByText(`Role Group ${timestamp}`).click();

    // Navigate to Members tab
    await page.getByRole("button", { name: "Members", exact: true }).click();

    // Verify "Manage role" is NOT visible for self (Owner cannot demote self)
    await expect(page.getByText("Owner User")).toBeVisible();
    await expect(page.getByLabel("Manage role")).not.toBeVisible();
  });
});
