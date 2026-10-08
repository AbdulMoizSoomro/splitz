import { expect, test, type Locator, type Page } from "@playwright/test";
import { openGroupDetails } from "./helpers/navigation";

/**
 * Editing an expense must reopen it on the split type it was created with.
 *
 * The split type and each member's split value are persisted per split, but the read path used to
 * omit them. The edit form therefore had nothing to restore from and fell back to EQUAL, so saving
 * an untouched EXACT expense silently re-split it evenly. These specs drive the real UI: create an
 * expense on a non-equal mode, reopen it, and assert both the visible form state and the outgoing
 * PUT payload still carry the original mode.
 */

const PASSWORD = "Password123!";

/** Per-member split inputs, keyed by their accessible label so tests need not know member ids. */
async function splitValueInputs(dialog: Locator): Promise<Map<string, Locator>> {
  const inputs = await dialog.getByLabel(/split value/i).all();
  const byLabel = new Map<string, Locator>();
  for (const input of inputs) {
    const label = await input.getAttribute("aria-label");
    if (label) byLabel.set(label, input);
  }
  return byLabel;
}

/** True when the given split-type radio is the selected one. */
async function isSplitTypeSelected(
  dialog: Locator,
  splitType: string,
): Promise<boolean> {
  return dialog
    .locator(`#split-type-${splitType}`)
    .getAttribute("aria-checked")
    .then((checked) => checked === "true");
}

async function registerAndLogin(
  page: Page,
  firstName: string,
  lastName: string,
  username: string,
): Promise<void> {
  await page.goto("/register");
  await page.locator("#firstName").fill(firstName);
  await page.locator("#lastName").fill(lastName);
  await page.locator("#username").fill(username);
  await page.locator("#email").fill(`${username}@example.com`);
  await page.locator("#password").fill(PASSWORD);
  await page.getByRole("button", { name: /register/i }).click();

  await expect(page).toHaveURL(/\/login/);
  await page.locator("#username").fill(username);
  await page.locator("#password").fill(PASSWORD);
  await page.getByRole("button", { name: /login/i }).click();
  await expect(page).toHaveURL(/\/$/);
}

test.describe.configure({ mode: "serial" });

test.describe("Expense split type survives a round trip through the edit form", () => {
  const ts = Date.now();
  const alice = `alice_split_${ts}`;
  const bob = `bob_split_${ts}`;
  const groupName = `Split Type Group ${ts}`;

  let alicePage: Page;
  let bobPage: Page;

  test.beforeAll(async ({ browser }) => {
    alicePage = await browser.newPage();
    bobPage = await browser.newPage();

    await registerAndLogin(alicePage, "Alice", "User", alice);
    await registerAndLogin(bobPage, "Bob", "User", bob);

    // Alice befriends Bob and Bob accepts, so both can be group members. The friendship round trip
    // goes through RabbitMQ, which is why setup lives in beforeAll rather than in every test.
    await alicePage.goto("/friends");
    await alicePage
      .getByPlaceholder(/search by name or email/i)
      .fill(bob);
    await alicePage.getByRole("button", { name: /add friend/i }).first().click();

    await bobPage.goto("/friends");
    await bobPage.getByText(/Alice User/i).waitFor({ state: "visible" });
    await bobPage.getByTitle("Accept").last().click();

    await alicePage.goto("/groups");
    await alicePage.getByRole("button", { name: /create group/i }).first().click();
    await alicePage.locator("#group-name").fill(groupName);
    await alicePage.getByText("Bob User").click();
    await alicePage.getByRole("button", { name: /create group/i }).last().click();
    await expect(alicePage.getByText(groupName)).toBeVisible();
  });

  test.afterAll(async () => {
    await alicePage?.close();
    await bobPage?.close();
  });

  /** Creates an expense on `splitType`, allocating `values` across the two members in order. */
  async function createExpense(
    description: string,
    amount: string,
    splitType: string,
    values: string[],
  ): Promise<Map<string, string>> {
    await openGroupDetails(alicePage, groupName);
    await alicePage.getByRole("button", { name: /add expense/i }).first().click();

    const modal = alicePage.getByRole("dialog", { name: /add new expense/i });
    await expect(modal).toBeVisible();
    await modal.locator("#description").fill(description);
    await modal.locator("#amount").fill(amount);
    await modal.getByLabel(splitType, { exact: true }).click();

    const inputs = await splitValueInputs(modal);
    const labels = [...inputs.keys()];
    expect(
      labels.length,
      `expected one split input per member for ${splitType}`,
    ).toBe(values.length);

    const filled = new Map<string, string>();
    for (const [index, label] of labels.entries()) {
      await inputs.get(label)!.fill(values[index]);
      filled.set(label, values[index]);
    }

    await modal.getByRole("button", { name: /add expense/i }).click();
    await expect(modal).not.toBeVisible();
    await expect(alicePage.getByText(description).first()).toBeVisible();

    return filled;
  }

  /** Opens the edit modal for an expense and returns it. */
  async function openEditModal(description: string): Promise<Locator> {
    await alicePage.getByLabel(new RegExp(`Actions for ${description}`, "i")).first().click();
    await alicePage.getByRole("menuitem", { name: /Edit/i }).click();

    const modal = alicePage.getByRole("dialog", { name: /edit expense/i });
    await expect(modal).toBeVisible();
    return modal;
  }

  test("the read path reports splitType and splitValue per split", async () => {
    await createExpense("Read Path Check", "60", "EXACT", ["20", "40"]);

    const responsePromise = alicePage.waitForResponse(
      (response) =>
        response.request().method() === "GET" &&
        /\/groups\/\d+\/expenses$/.test(new URL(response.url()).pathname),
    );

    await alicePage.reload();
    const response = await responsePromise;
    expect(response.ok()).toBe(true);

    const body = await response.json();
    const expense = body.find(
      (e: { description: string }) => e.description === "Read Path Check",
    );
    expect(expense).toBeDefined();
    expect(expense.splits).toHaveLength(2);
    // Without these the edit form has nothing to restore from and defaults to EQUAL.
    expect(expense.splits.every((s: { splitType: string }) => s.splitType === "EXACT")).toBe(true);
    expect(
      expense.splits
        .map((s: { splitValue: number }) => s.splitValue)
        .sort((a: number, b: number) => a - b),
    ).toEqual([20, 40]);
  });

  test("an EXACT expense reopens as EXACT with its amounts intact", async () => {
    const filled = await createExpense("Exact Round Trip", "60", "EXACT", [
      "20",
      "40",
    ]);

    const modal = await openEditModal("Exact Round Trip");

    expect(await isSplitTypeSelected(modal, "EXACT")).toBe(true);
    expect(await isSplitTypeSelected(modal, "EQUAL")).toBe(false);
    // Restored amounts still sum to the total, so the form reports a valid split.
    await expect(modal.getByText(/fully allocated/i)).toBeVisible();

    const reopened = await splitValueInputs(modal);
    expect([...reopened.keys()].sort()).toEqual([...filled.keys()].sort());
    for (const [label, value] of filled) {
      await expect(reopened.get(label)!).toHaveValue(value);
    }

    const updatePromise = alicePage.waitForRequest(
      (request) =>
        request.url().includes("/expenses/") && request.method() === "PUT",
    );
    await modal.getByRole("button", { name: /save changes/i }).click();

    const payload = (await updatePromise).postDataJSON();
    expect(payload.splitType).toBe("EXACT");
    expect(
      payload.splits
        .map((s: { splitValue: number }) => s.splitValue)
        .sort((a: number, b: number) => a - b),
    ).toEqual([20, 40]);
    await expect(modal).not.toBeVisible();
  });

  test("a PERCENTAGE expense reopens as PERCENTAGE with its percentages intact", async () => {
    const filled = await createExpense("Percentage Round Trip", "80", "percentage", [
      "25",
      "75",
    ]);

    const modal = await openEditModal("Percentage Round Trip");

    expect(await isSplitTypeSelected(modal, "PERCENTAGE")).toBe(true);
    await expect(modal.getByText(/100% allocated/i)).toBeVisible();

    const reopened = await splitValueInputs(modal);
    for (const [label, value] of filled) {
      await expect(reopened.get(label)!).toHaveValue(value);
    }

    const updatePromise = alicePage.waitForRequest(
      (request) =>
        request.url().includes("/expenses/") && request.method() === "PUT",
    );
    await modal.getByRole("button", { name: /save changes/i }).click();

    const payload = (await updatePromise).postDataJSON();
    expect(payload.splitType).toBe("PERCENTAGE");
    // Percentages, not the amounts they were converted into.
    expect(
      payload.splits
        .map((s: { splitValue: number }) => s.splitValue)
        .sort((a: number, b: number) => a - b),
    ).toEqual([25, 75]);
    await expect(modal).not.toBeVisible();
  });

  test("a SHARES expense reopens as SHARES with its share counts intact", async () => {
    const filled = await createExpense("Shares Round Trip", "40", "shares", [
      "1",
      "3",
    ]);

    const modal = await openEditModal("Shares Round Trip");

    expect(await isSplitTypeSelected(modal, "SHARES")).toBe(true);
    await expect(modal.getByText(/total shares: 4/i)).toBeVisible();

    const reopened = await splitValueInputs(modal);
    for (const [label, value] of filled) {
      await expect(reopened.get(label)!).toHaveValue(value);
    }

    const updatePromise = alicePage.waitForRequest(
      (request) =>
        request.url().includes("/expenses/") && request.method() === "PUT",
    );
    await modal.getByRole("button", { name: /save changes/i }).click();

    const payload = (await updatePromise).postDataJSON();
    expect(payload.splitType).toBe("SHARES");
    // Share counts, not the amounts they were converted into.
    expect(
      payload.splits
        .map((s: { splitValue: number }) => s.splitValue)
        .sort((a: number, b: number) => a - b),
    ).toEqual([1, 3]);
    await expect(modal).not.toBeVisible();
  });

  test("an EQUAL expense still reopens as EQUAL with no per-member inputs", async () => {
    await createExpense("Equal Round Trip", "45", "equal", []);

    const modal = await openEditModal("Equal Round Trip");

    expect(await isSplitTypeSelected(modal, "EQUAL")).toBe(true);
    // EQUAL has no per-member input, so the form shows the even-share summary instead.
    await expect(modal.getByText(/each person pays: \$22\.50/i)).toBeVisible();
    expect(await splitValueInputs(modal)).toHaveLength(0);

    const updatePromise = alicePage.waitForRequest(
      (request) =>
        request.url().includes("/expenses/") && request.method() === "PUT",
    );
    await modal.getByRole("button", { name: /save changes/i }).click();

    const payload = (await updatePromise).postDataJSON();
    expect(payload.splitType).toBe("EQUAL");
    await expect(modal).not.toBeVisible();
  });

  test("switching split type in the editor still clears the previous inputs", async () => {
    const modal = await openEditModal("Exact Round Trip");

    expect(await isSplitTypeSelected(modal, "EXACT")).toBe(true);
    await modal.getByLabel("percentage", { exact: true }).click();

    expect(await isSplitTypeSelected(modal, "PERCENTAGE")).toBe(true);
    // Changing mode starts from a clean slate, so stale EXACT amounts are not carried over.
    const reopened = await splitValueInputs(modal);
    for (const input of reopened.values()) {
      await expect(input).toHaveValue("");
    }
    await expect(modal.getByText(/total: 0\.0%/i)).toBeVisible();

    await modal.getByRole("button", { name: /cancel/i }).click();
    await expect(modal).not.toBeVisible();
  });
});