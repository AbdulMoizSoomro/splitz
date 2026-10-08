import { expect, test, type APIRequestContext, type Locator, type Page } from "@playwright/test";
import { openGroupDetails } from "./helpers/navigation";
import type { SplitType } from "../../types/expense";

/**
 * Editing an expense must reopen it on the split type it was created with.
 *
 * The split type and each member's split value are persisted per split, but the read path used to
 * omit them. The edit form therefore had nothing to restore from and fell back to EQUAL, so saving
 * an untouched EXACT expense silently re-split it evenly.
 *
 * Expenses are seeded through the API rather than the UI on purpose: the behaviour under test is
 * what the *edit* form restores, and driving creation through the form would spend the spec on radio
 * and input plumbing that the create path already covers elsewhere. Only the edit form is exercised
 * through the UI.
 */

const PASSWORD = "Password123!";

interface Member {
  userId: number;
}

interface SeededExpense {
  id: number;
  description: string;
}

async function register(request: APIRequestContext, username: string, firstName: string) {
  const res = await request.post("/api/user/users", {
    data: {
      username,
      email: `${username}@example.com`,
      password: PASSWORD,
      firstName,
      lastName: "User",
    },
  });
  expect(res.ok(), `register ${username}`).toBeTruthy();
  return (await res.json()) as { id: number; username: string };
}

async function authenticate(request: APIRequestContext, username: string): Promise<string> {
  const res = await request.post("/api/user/authenticate", {
    data: { username, password: PASSWORD },
  });
  expect(res.ok(), `authenticate ${username}`).toBeTruthy();
  return ((await res.json()) as { token: string }).token;
}

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

/**
 * The visible radio for a split type.
 *
 * Located by ARIA role and accessible name, not by id. base-ui renders two elements per radio: the
 * visible `span[role=radio]` carrying `aria-checked`, and a visually hidden
 * `input[aria-hidden=true]` that owns the `split-type-${type}` id. The id therefore resolves to the
 * hidden input, which has no `aria-checked` and sits under the dialog overlay, so clicking or
 * reading state from it fails. The accessible name comes from the label's text, which
 * `splitTypeLabel` renders lowercase ("exact"), hence the case-insensitive match.
 */
function splitTypeRadio(dialog: Locator, splitType: SplitType): Locator {
  return dialog.getByRole("radio", { name: new RegExp(`^${splitType}$`, "i") });
}

async function isSplitTypeSelected(dialog: Locator, splitType: SplitType): Promise<boolean> {
  return (await splitTypeRadio(dialog, splitType).getAttribute("aria-checked")) === "true";
}

test.describe("Expense split type survives a round trip through the edit form", () => {
  let alicePage: Page;
  let request: APIRequestContext;
  let token: string;
  let groupId: number;
  let aliceId: number;
  let bobId: number;
  let groupName: string;

  // `beforeAll` runs once per worker, and the suite is fullyParallel, so each worker needs its own
  // users and group or the registrations collide. A per-invocation nonce keeps them independent.
  test.beforeAll(async ({ browser, playwright, baseURL }) => {
    const nonce = `${Date.now().toString(36)}${Math.random().toString(36).slice(2, 7)}`;
    const alice = `alice_rt_${nonce}`;
    const bob = `bob_rt_${nonce}`;
    groupName = `Round Trip Group ${nonce}`;

    // A hand-made context rather than the `request` fixture: Playwright forbids reusing a
    // beforeAll fixture inside a test, and these helpers are called from test bodies.
    request = await playwright.request.newContext({ baseURL });

    const aliceUser = await register(request, alice, "Alice");
    const bobUser = await register(request, bob, "Bob");
    aliceId = aliceUser.id;
    bobId = bobUser.id;
    token = await authenticate(request, alice);

    // Group members do not have to be friends, so this skips the RabbitMQ-mediated friendship
    // round trip entirely — that round trip was the slowest and flakiest part of the setup.
    const groupRes = await request.post("/api/expense/groups", {
      headers: { Authorization: `Bearer ${token}` },
      data: { name: groupName, memberUserIds: [bobId] },
    });
    expect(groupRes.ok(), "create group").toBeTruthy();
    const group = (await groupRes.json()) as { id: number; members: Member[] };
    groupId = group.id;
    expect(group.members.map((m) => m.userId).sort()).toEqual([aliceId, bobId].sort());

    alicePage = await browser.newPage();
    await alicePage.goto("/login");
    await alicePage.locator("#username").fill(alice);
    await alicePage.locator("#password").fill(PASSWORD);
    await alicePage.getByRole("button", { name: /login/i }).click();
    await expect(alicePage).toHaveURL(/\/$/);
  });

  test.afterAll(async () => {
    await alicePage?.close();
    await request?.dispose();
  });

  /** Navigates to the group's expenses tab from a known route. */
  async function gotoGroup(): Promise<void> {
    // Always start from the groups list. `openGroupDetails` clicks the first element matching the
    // group name, and on the group page that is the (disabled) breadcrumb rather than a link.
    await alicePage.goto("/groups");
    await openGroupDetails(alicePage, groupName);
    await expect(alicePage.getByTestId("group-details")).toBeAttached();
  }

  /** Creates an expense directly through the API, on the given split type. */
  async function seedExpense(
    description: string,
    amount: string,
    splitType: SplitType,
    splitValues: (number | null)[],
  ): Promise<SeededExpense> {
    const res = await request.post(`/api/expense/groups/${groupId}/expenses`, {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        description,
        amount: Number(amount),
        paidBy: aliceId,
        splitType,
        splits: [
          { userId: aliceId, splitValue: splitValues[0] ?? undefined },
          { userId: bobId, splitValue: splitValues[1] ?? undefined },
        ],
      },
    });
    expect(res.ok(), `seed ${description}`).toBeTruthy();
    return (await res.json()) as SeededExpense;
  }

  /** Opens the edit modal for a seeded expense. */
  async function openEditModal(description: string): Promise<Locator> {
    await gotoGroup();

    // Scope to the expense card specifically. The group page mounts the expenses list and the
    // activity log at the same time, and the activity entry for this expense repeats its
    // description (in quotes) inside its own card with an identically labelled dropdown trigger, so
    // matching on the description alone matches two cards.
    const card = alicePage
      .locator("[data-slot='card']")
      .filter({ hasText: description })
      .filter({ hasNotText: `"${description}"` });
    await expect(card).toHaveCount(1);
    await card.locator("[data-slot='dropdown-menu-trigger']").click();
    await alicePage.getByRole("menuitem", { name: /^Edit$/i }).click();

    const modal = alicePage.getByRole("dialog", { name: /edit expense/i });
    await expect(modal).toBeVisible();
    return modal;
  }

  /** Clicks Save Changes and returns the PUT payload. */
  async function saveAndCapturePayload(modal: Locator): Promise<Record<string, unknown>> {
    const updatePromise = alicePage.waitForRequest(
      (r) => r.url().includes("/expenses/") && r.method() === "PUT",
    );
    await modal.getByRole("button", { name: /save changes/i }).click();
    const payload = (await updatePromise).postDataJSON() as Record<string, unknown>;
    await expect(modal).not.toBeVisible();
    return payload;
  }

  test("the read path reports splitType and splitValue per split", async () => {
    await seedExpense("Read Path Check", "60", "EXACT", [20, 40]);

    const responsePromise = alicePage.waitForResponse(
      (response) =>
        response.request().method() === "GET" &&
        /\/groups\/\d+\/expenses$/.test(new URL(response.url()).pathname),
    );
    await gotoGroup();
    const response = await responsePromise;
    expect(response.ok()).toBe(true);

    const body = (await response.json()) as Array<{
      description: string;
      splits: Array<{ splitType: string; splitValue: number | null; shareAmount: number }>;
    }>;
    const expense = body.find((e) => e.description === "Read Path Check");
    expect(expense).toBeDefined();
    expect(expense!.splits).toHaveLength(2);
    // Without these the edit form has nothing to restore from and defaults to EQUAL.
    expect(expense!.splits.every((s) => s.splitType === "EXACT")).toBe(true);
    expect(expense!.splits.map((s) => s.splitValue).sort((a, b) => (a ?? 0) - (b ?? 0))).toEqual([
      20, 40,
    ]);
  });

  test("an EXACT expense reopens as EXACT with its amounts intact", async () => {
    await seedExpense("Exact Round Trip", "60", "EXACT", [20, 40]);

    const modal = await openEditModal("Exact Round Trip");

    expect(await isSplitTypeSelected(modal, "EXACT")).toBe(true);
    expect(await isSplitTypeSelected(modal, "EQUAL")).toBe(false);
    // Restored amounts still sum to the total, so the form reports a valid split.
    await expect(modal.getByText(/fully allocated/i)).toBeVisible();

    const inputs = await splitValueInputs(modal);
    expect(inputs.size).toBe(2);
    for (const input of inputs.values()) {
      await expect(input).toHaveValue(/^(20|40)(\.0+)?$/);
    }

    const payload = await saveAndCapturePayload(modal);
    expect(payload.splitType).toBe("EXACT");
    expect(
      (payload.splits as Array<{ splitValue: number }>)
        .map((s) => s.splitValue)
        .sort((a, b) => a - b),
    ).toEqual([20, 40]);
  });

  test("a PERCENTAGE expense reopens as PERCENTAGE with its percentages intact", async () => {
    await seedExpense("Percentage Round Trip", "80", "PERCENTAGE", [25, 75]);

    const modal = await openEditModal("Percentage Round Trip");

    expect(await isSplitTypeSelected(modal, "PERCENTAGE")).toBe(true);
    await expect(modal.getByText(/100% allocated/i)).toBeVisible();

    const inputs = await splitValueInputs(modal);
    for (const input of inputs.values()) {
      await expect(input).toHaveValue(/^(25|75)(\.0+)?$/);
    }

    const payload = await saveAndCapturePayload(modal);
    expect(payload.splitType).toBe("PERCENTAGE");
    // Percentages, not the amounts they were converted into.
    expect(
      (payload.splits as Array<{ splitValue: number }>)
        .map((s) => s.splitValue)
        .sort((a, b) => a - b),
    ).toEqual([25, 75]);
  });

  test("a SHARES expense reopens as SHARES with its share counts intact", async () => {
    await seedExpense("Shares Round Trip", "40", "SHARES", [1, 3]);

    const modal = await openEditModal("Shares Round Trip");

    expect(await isSplitTypeSelected(modal, "SHARES")).toBe(true);
    await expect(modal.getByText(/total shares: 4/i)).toBeVisible();

    const inputs = await splitValueInputs(modal);
    for (const input of inputs.values()) {
      await expect(input).toHaveValue(/^(1|3)(\.0+)?$/);
    }

    const payload = await saveAndCapturePayload(modal);
    expect(payload.splitType).toBe("SHARES");
    // Share counts, not the amounts they were converted into.
    expect(
      (payload.splits as Array<{ splitValue: number }>)
        .map((s) => s.splitValue)
        .sort((a, b) => a - b),
    ).toEqual([1, 3]);
  });

  test("an EQUAL expense still reopens as EQUAL with no per-member inputs", async () => {
    await seedExpense("Equal Round Trip", "45", "EQUAL", [null, null]);

    const modal = await openEditModal("Equal Round Trip");

    expect(await isSplitTypeSelected(modal, "EQUAL")).toBe(true);
    // EQUAL has no per-member input, so the form shows the even-share summary instead.
    await expect(modal.getByText(/each person pays: \$22\.50/i)).toBeVisible();
    expect((await splitValueInputs(modal)).size).toBe(0);

    const payload = await saveAndCapturePayload(modal);
    expect(payload.splitType).toBe("EQUAL");
  });

  test("switching split type in the editor still clears the previous inputs", async () => {
    // Seed its own expense: the suite is fullyParallel, so a fixture created by a sibling test
    // would live in that worker's group and not be visible here.
    await seedExpense("Mode Switch Check", "60", "EXACT", [20, 40]);
    const modal = await openEditModal("Mode Switch Check");

    expect(await isSplitTypeSelected(modal, "EXACT")).toBe(true);
    await splitTypeRadio(modal, "PERCENTAGE").click();

    expect(await isSplitTypeSelected(modal, "PERCENTAGE")).toBe(true);
    // Changing mode starts from a clean slate, so stale EXACT amounts are not carried over.
    for (const input of (await splitValueInputs(modal)).values()) {
      await expect(input).toHaveValue("");
    }
    await expect(modal.getByText(/total: 0\.0%/i)).toBeVisible();

    await modal.getByRole("button", { name: /cancel/i }).click();
    await expect(modal).not.toBeVisible();
  });
});