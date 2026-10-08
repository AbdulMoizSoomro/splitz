import { expect, type Page } from "@playwright/test";

/** True once a lazy group-details route has mounted. */
export async function waitForGroupDetails(page: Page) {
  await expect(page.getByTestId("group-details")).toBeAttached({ timeout: 30_000 });
}

/**
 * Opens a group's details page and waits until that route has actually loaded.
 *
 * Routes are code-split with React.lazy, so clicking a group starts an async chunk fetch and the
 * previous page stays on screen meanwhile. Interacting in that window targets the outgoing page —
 * notably the groups list, which also renders an "Add Expense" control per card. Waiting for the
 * details marker makes navigation deterministic instead of racing the boundary.
 */
export async function openGroupDetails(page: Page, groupName: string) {
  await page.getByText(groupName).first().click();
  await waitForGroupDetails(page);
}

/** Open a group's Balances tab, waiting for the route first. */
export async function openGroupBalances(page: Page, groupName: string) {
  await openGroupDetails(page, groupName);
  await page.getByRole("tab", { name: /balances/i }).click();
  await expect(page.getByRole("tab", { name: /balances/i })).toBeVisible();
}