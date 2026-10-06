import { type APIRequestContext, type Browser, type BrowserContextOptions, expect, type Page } from "@playwright/test";
import { InventoryClient } from "@/__tests__/e2e/api/clients/InventoryClient";
import type { SysadminClient } from "@/__tests__/e2e/api/clients/SysadminClient";
import { storageStatePath } from "@/__tests__/e2e/authState";
import { OperationWizardComponent } from "@/__tests__/e2e/components/inventory/OperationWizardComponent";
import { ToastsComponent } from "@/__tests__/e2e/components/shared/ToastsComponent";
import { env } from "@/__tests__/e2e/env";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { LoginPage } from "@/__tests__/e2e/pageObjects/auth/LoginPage";
import { InventoryPage } from "@/__tests__/e2e/pageObjects/inventory/InventoryPage";
import { RequestsPage } from "@/__tests__/e2e/pageObjects/inventory/RequestsPage";
import { SystemConfigPage, type SystemPropertyValue } from "@/__tests__/e2e/pageObjects/system/SystemConfigPage";
import { tags } from "@/__tests__/e2e/tags";
import { alphaNumericUnique, uniqueName } from "@/__tests__/e2e/testData";
import { SYSADMIN } from "@/__tests__/e2e/users";

const PROPERTY = "inventory.sampleRequests.available";
const OPERATIONS_PROPERTY = "inventory.operations.available";
const PASSWORD = "Passw0rd!23";
const openContexts = new Set<Awaited<ReturnType<Browser["newContext"]>>>();
// Every requestable=true sample is visible to every other user's instance-wide Requestable
// search forever, regardless of who created it or whether their account is later disabled -
// left uncleared, they accumulate across every run of this file and eventually make that search
// slow enough to exceed any reasonable test timeout. Track each one so afterEach can un-flag it.
const createdRequestableSamples: Array<{ client: InventoryClient; id: number }> = [];

type Actor = { username: string; password: string };
type InventoryActor = Actor & { client: InventoryClient };

test.afterEach(async () => {
  // Closing a context doesn't trigger any "cancel edit" flow in the app, so a session that was
  // still mid-edit (e.g. because an earlier step in the test failed) leaves its edit lock held
  // server-side rather than releasing it - which would otherwise 422 the rename below with
  // "currently edited by another user". The lock owner can always release their own lock
  // (InventoryEditLocksController), and releasing is a no-op, not an error, if there was none, so
  // it's safe to always attempt this first.
  await Promise.all([...openContexts].map((context) => context.close()));
  openContexts.clear();
  await Promise.all(
    createdRequestableSamples.splice(0).map(async ({ client, id }) => {
      await client.releaseEditLock(`SA${id}`).catch(() => {});
      await client.renameSample(id, { requestable: false }).catch((error: unknown) => {
        console.error(`Failed to clear requestable flag for sample ${id} during teardown`, error);
      });
    }),
  );
});

/** Creates a sample and marks it for teardown to clear its requestable flag afterwards. */
async function createTestSample(actor: InventoryActor, name: string) {
  const sample = await actor.client.createSample({ name });
  createdRequestableSamples.push({ client: actor.client, id: sample.id });
  return sample;
}

/**
 * The backend only lets a sample's *owner* toggle `requestable` (RSDEV-1309:
 * `ApiSampleInfo.applyChangesToDatabaseSample`'s `requestedByOwner` check), so once a test
 * transfers ownership, the original owner's teardown call is silently ignored - it isn't rejected,
 * it just has no effect, leaking the sample as permanently requestable. Call this right after a
 * successful transfer so teardown clears the flag as whoever now actually owns it.
 */
function trackOwnershipTransferred(sampleId: number, newOwner: InventoryActor): void {
  const entry = createdRequestableSamples.find((e) => e.id === sampleId);
  if (entry) entry.client = newOwner.client;
}

async function createActor(clientSysadmin: SysadminClient, firstName: string, apiKey?: string): Promise<Actor> {
  const username = alphaNumericUnique(`e2eReq${firstName}`);
  await clientSysadmin.createUser({
    username,
    password: PASSWORD,
    email: `${username}@example.com`,
    firstName,
    lastName: "Requests",
    role: "ROLE_USER",
    apiKey,
  });
  return { username, password: PASSWORD };
}

async function createInventoryActor(
  clientSysadmin: SysadminClient,
  apiContext: APIRequestContext,
  firstName: string,
): Promise<InventoryActor> {
  const apiKey = alphaNumericUnique(`e2eReq${firstName}Key`).slice(0, 32);
  return {
    ...(await createActor(clientSysadmin, firstName, apiKey)),
    client: new InventoryClient(apiContext, apiKey),
  };
}

async function newSession(browser: Browser, browserContextOptions: BrowserContextOptions, user: Actor) {
  const ctx = await browser.newContext({ ...browserContextOptions, storageState: undefined });
  openContexts.add(ctx);
  ctx.once("close", () => openContexts.delete(ctx));
  const page = await ctx.newPage();
  const loginPage = new LoginPage(page);
  await loginPage.open();
  await loginPage.login(user.username, user.password);
  await page.waitForURL((url) => url.pathname === "/workspace");
  return {
    page,
    inventoryPage: new InventoryPage(page),
    requestsPage: new RequestsPage(page),
    close: () => ctx.close(),
  };
}

/**
 * Marks a sample requestable via the UI, then verifies via the API that it actually took effect.
 * A now-fixed bug (RSDEV-1309: InventoryBaseRecord.fetchAdditionalInfo could resolve after the
 * user had already started editing, silently reverting fields to their pre-edit values) meant
 * this could appear to succeed in the UI without actually persisting - verify outright rather than
 * assume.
 */
async function markSampleRequestable(
  browser: Browser,
  browserContextOptions: BrowserContextOptions,
  actor: InventoryActor & { sample: { id: number; name: string } },
): Promise<void> {
  const session = await newSession(browser, browserContextOptions, actor);
  try {
    await session.inventoryPage.openRecord("SAMPLE", actor.sample.name);
    await session.inventoryPage.detailsPanel.enterEditMode();
    await session.inventoryPage.detailsPanel.requestableSwitch().check();
    await expect(session.inventoryPage.detailsPanel.requestableSwitch()).toBeChecked();
    await session.inventoryPage.detailsPanel.saveEdit();
  } finally {
    await session.close();
  }

  const saved = await actor.client.getSample(actor.sample.id);
  if (saved.requestable !== true) {
    throw new Error(
      `Sample ${actor.sample.id} (${actor.sample.name}) is not requestable via the API immediately ` +
        "after saving it as requestable via the UI.",
    );
  }
}

/** Search Requestable Samples for one sample, by name, and open it. */
async function findRequestableSample(page: Page, inventoryPage: InventoryPage, sampleName: string): Promise<void> {
  await inventoryPage.openSearch("SAMPLE");
  // Select the filter once, outside the retry loop: MUI's Menu has an open/close transition, and
  // re-opening and re-closing it on every retry attempt (with no pause between attempts) risks
  // never letting that transition settle, which can leave its backdrop intercepting clicks on
  // unrelated elements indefinitely.
  await inventoryPage.searchPanel.filterChip("Requestable").click({ timeout: 15_000 });
  await page.getByRole("menuitem", { name: "Requestable only", exact: true }).click({ timeout: 15_000 });
  // Requestable is an instance-wide search across every requestable sample any test in this suite
  // has ever created, so without narrowing by name the row could be paginated arbitrarily deep in
  // that backlog, or past the backend's results cap entirely - narrow by name like any other
  // search. Wait on the results-loaded signal rather than a network response matching this exact
  // query: the fetcher retries an empty exact match with a trailing wildcard internally before
  // resolving, so matching the *first* (possibly still-empty) response resolves before that retry
  // has actually happened.
  await expect(async () => {
    await inventoryPage.searchPanel.searchAndWaitForLoad(sampleName);
    await inventoryPage.searchPanel.open(sampleName);
  }).toPass({ timeout: 60_000 });
}

test.describe("Inventory Sample Requests", { tag: [tags.INVENTORY] }, () => {
  test("As a system admin, with Sample S in my inventory, I can enable the sample request functionality", async ({
    browser,
    browserContextOptions,
    apiContext,
  }) => {
    env.assertGlobalMutationsAllowed(PROPERTY);
    env.assertGlobalMutationsAllowed(OPERATIONS_PROPERTY);

    const sampleName = uniqueName("e2e-sample-requests-enable");
    await new InventoryClient(apiContext, SYSADMIN.apiKey).createSample({ name: sampleName });

    const ctx = await browser.newContext({
      ...browserContextOptions,
      storageState: storageStatePath(SYSADMIN.username),
    });
    try {
      const page = await ctx.newPage();
      const inventoryPage = new InventoryPage(page);
      const systemConfig = new SystemConfigPage(page);
      const requestsPage = new RequestsPage(page);

      await systemConfig.open();
      const originalValue = (await systemConfig.getSetting(PROPERTY)).trim() as SystemPropertyValue;
      const originalOperationsValue = (
        await systemConfig.getSetting(OPERATIONS_PROPERTY)
      ).trim() as SystemPropertyValue;
      try {
        await test.step("Given inventory.sampleRequests.available and inventory.operations.available both start Denied", async () => {
          await systemConfig.setSetting(PROPERTY, "DENIED");
          await systemConfig.setSetting(OPERATIONS_PROPERTY, "DENIED");
        });

        await test.step("Then the Requests sidebar item and the sample's Requestable switch are both absent", async () => {
          await inventoryPage.open();
          await inventoryPage.sidebar.ensureOpen();
          await expect(inventoryPage.sidebar.item("Requests")).toBeHidden();

          await inventoryPage.openRecord("SAMPLE", sampleName);
          await expect(inventoryPage.detailsPanel.requestableSwitch()).toBeHidden();
        });

        await test.step("When a sysadmin sets inventory.sampleRequests.available to Allowed via System Settings", async () => {
          await systemConfig.open();
          await systemConfig.setSetting(PROPERTY, "ALLOWED");
        });

        await test.step("Then the Requests sidebar item appears, and its page loads with no requests", async () => {
          await inventoryPage.open();
          await inventoryPage.sidebar.ensureOpen();
          await expect(inventoryPage.sidebar.item("Requests")).toBeVisible();

          await inventoryPage.sidebar.navigateTo("Requests");
          await requestsPage.isLoaded();
          await expect(requestsPage.noRequestsMessage).toBeVisible();
        });

        await test.step("And the sample's Requestable switch now appears", async () => {
          await inventoryPage.openRecord("SAMPLE", sampleName);
          await expect(inventoryPage.detailsPanel.requestableSwitch()).toBeVisible();
        });
      } finally {
        await systemConfig.open();
        await systemConfig.ensureSettings({
          [PROPERTY]: originalValue,
          [OPERATIONS_PROPERTY]: originalOperationsValue,
        });
      }
    } finally {
      await ctx.close();
    }
  });

  test("As a user who does not own a requestable sample, I can request it and then cancel that request", async ({
    browser,
    browserContextOptions,
    clientSysadmin,
    apiContext,
    flowSampleRequestsAvailable,
    flowOperationsNotAvailable,
  }) => {
    // Five separate logins (a fresh browser context each), so allow extra time on a cold backend.
    test.slow();
    void flowSampleRequestsAvailable;
    void flowOperationsNotAvailable;
    const reasonText = "Need 5 vials for a follow-up experiment.";

    const alice = await test.step("Given Alice owns Sample X", async () => {
      const actor = await createInventoryActor(clientSysadmin, apiContext, "Alice");
      const sample = await createTestSample(actor, uniqueName("e2e-sample-requests"));
      return { ...actor, sample };
    });
    const bob = await test.step("And Bob is a separate user", () => createActor(clientSysadmin, "Bob"));

    await test.step("When Alice marks Sample X as requestable", () =>
      markSampleRequestable(browser, browserContextOptions, alice));

    const requestId =
      await test.step("And Bob finds Sample X via the Requestable Samples search and requests it", async () => {
        const session = await newSession(browser, browserContextOptions, bob);
        await findRequestableSample(session.page, session.inventoryPage, alice.sample.name);
        const id = await session.inventoryPage.detailsPanel.requestMaterial().sendRequest(reasonText);
        await session.close();
        return id;
      });

    await test.step("Then Alice sees Bob's request against Sample X, Pending, with his reason", async () => {
      const session = await newSession(browser, browserContextOptions, alice);
      await session.requestsPage.openRequest(requestId);
      await expect(session.requestsPage.heading).toContainText(alice.sample.name);
      await expect(session.requestsPage.statusChip("Pending")).toBeVisible();
      await expect(session.requestsPage.detailField("Requested by")).toContainText("Bob Requests");
      await expect(session.requestsPage.detailField("Notes from requester")).toContainText(reasonText);
      await session.close();
    });

    await test.step("When Bob cancels his request from the Sample UI", async () => {
      const session = await newSession(browser, browserContextOptions, bob);
      await findRequestableSample(session.page, session.inventoryPage, alice.sample.name);
      await session.inventoryPage.detailsPanel.requestMaterial().cancelRequest();
      await session.close();
    });

    await test.step("Then Alice sees the request is now Cancelled", async () => {
      const session = await newSession(browser, browserContextOptions, alice);
      await session.requestsPage.openRequest(requestId);
      await expect(session.requestsPage.statusChip("Cancelled")).toBeVisible();
      await session.close();
    });
  });

  test("As a user who does not own a requestable sample, I can select a requestable sample from the Samples UI and request a sample, and then cancel the request on that sample from the requests view", async ({
    browser,
    browserContextOptions,
    clientSysadmin,
    apiContext,
    flowSampleRequestsAvailable,
    flowOperationsNotAvailable,
  }) => {
    // Five separate logins (a fresh browser context each), so allow extra time on a cold backend.
    test.slow();
    void flowSampleRequestsAvailable;
    void flowOperationsNotAvailable;

    const alice = await test.step("Given Alice owns Sample X", async () => {
      const actor = await createInventoryActor(clientSysadmin, apiContext, "Alice");
      const sample = await createTestSample(actor, uniqueName("e2e-sample-requests"));
      return { ...actor, sample };
    });
    const bob = await test.step("And Bob is a separate user", () => createActor(clientSysadmin, "Bob"));

    await test.step("When Alice marks Sample X as requestable", () =>
      markSampleRequestable(browser, browserContextOptions, alice));

    const requestId =
      await test.step("And Bob finds Sample X via the Requestable Samples search and requests it", async () => {
        const session = await newSession(browser, browserContextOptions, bob);
        await findRequestableSample(session.page, session.inventoryPage, alice.sample.name);
        const id = await session.inventoryPage.detailsPanel.requestMaterial().sendRequest("Please can I use this.");
        await session.close();
        return id;
      });

    await test.step("Then Alice sees Bob's request against Sample X, Pending", async () => {
      const session = await newSession(browser, browserContextOptions, alice);
      await session.requestsPage.openRequest(requestId);
      await expect(session.requestsPage.heading).toContainText(alice.sample.name);
      await expect(session.requestsPage.statusChip("Pending")).toBeVisible();
      await session.close();
    });

    await test.step("When Bob cancels his request from its Actions section in the Requests view", async () => {
      const session = await newSession(browser, browserContextOptions, bob);
      await session.requestsPage.openRequest(requestId);
      await session.requestsPage.cancelRequest();
      await session.close();
    });

    await test.step("Then Alice sees the request is now Cancelled", async () => {
      const session = await newSession(browser, browserContextOptions, alice);
      await session.requestsPage.openRequest(requestId);
      await expect(session.requestsPage.statusChip("Cancelled")).toBeVisible();
      await session.close();
    });
  });

  test("As a user who has previously requested a sample I can make another request on that sample", async ({
    browser,
    browserContextOptions,
    clientSysadmin,
    apiContext,
    flowSampleRequestsAvailable,
    flowOperationsNotAvailable,
  }) => {
    // Five separate logins (a fresh browser context each), so allow extra time on a cold backend.
    test.slow();
    void flowSampleRequestsAvailable;
    void flowOperationsNotAvailable;

    const alice = await test.step("Given Alice owns Sample X", async () => {
      const actor = await createInventoryActor(clientSysadmin, apiContext, "Alice");
      const sample = await createTestSample(actor, uniqueName("e2e-sample-requests"));
      return { ...actor, sample };
    });
    const bob = await test.step("And Bob is a separate user", () => createActor(clientSysadmin, "Bob"));

    await test.step("When Alice marks Sample X as requestable", () =>
      markSampleRequestable(browser, browserContextOptions, alice));

    const firstRequestId =
      await test.step("And Bob finds Sample X via the Requestable Samples search and requests it", async () => {
        const session = await newSession(browser, browserContextOptions, bob);
        await findRequestableSample(session.page, session.inventoryPage, alice.sample.name);
        const id = await session.inventoryPage.detailsPanel.requestMaterial().sendRequest("First request.");
        await session.close();
        return id;
      });

    await test.step("Then Alice sees Bob's request, Pending, and rejects it", async () => {
      const session = await newSession(browser, browserContextOptions, alice);
      await session.requestsPage.openRequest(firstRequestId);
      await expect(session.requestsPage.statusChip("Pending")).toBeVisible();
      await session.requestsPage.rejectRequest("Not available right now.");
      await expect(session.requestsPage.statusChip("Rejected")).toBeVisible();
      await session.close();
    });

    const secondRequestId = await test.step("When Bob finds Sample X again and makes another request", async () => {
      const session = await newSession(browser, browserContextOptions, bob);
      await findRequestableSample(session.page, session.inventoryPage, alice.sample.name);
      const id = await session.inventoryPage.detailsPanel.requestMaterial().sendRequest("Second request.");
      await session.close();
      return id;
    });
    expect(secondRequestId).not.toBe(firstRequestId);

    await test.step("Then Alice sees the new request, Pending", async () => {
      const session = await newSession(browser, browserContextOptions, alice);
      await session.requestsPage.openRequest(secondRequestId);
      await expect(session.requestsPage.statusChip("Pending")).toBeVisible();
      await session.close();
    });
  });

  test("As a user who has had a sample request made against me I can reject that request", async ({
    browser,
    browserContextOptions,
    clientSysadmin,
    apiContext,
    flowSampleRequestsAvailable,
    flowOperationsNotAvailable,
  }) => {
    // Four separate logins (a fresh browser context each), so allow extra time on a cold backend.
    test.slow();
    void flowSampleRequestsAvailable;
    void flowOperationsNotAvailable;

    const alice = await test.step("Given Alice owns Sample X", async () => {
      const actor = await createInventoryActor(clientSysadmin, apiContext, "Alice");
      const sample = await createTestSample(actor, uniqueName("e2e-sample-requests"));
      return { ...actor, sample };
    });
    const bob = await test.step("And Bob is a separate user", () => createActor(clientSysadmin, "Bob"));

    await test.step("When Alice marks Sample X as requestable", () =>
      markSampleRequestable(browser, browserContextOptions, alice));

    const requestId =
      await test.step("And Bob finds Sample X via the Requestable Samples search and requests it", async () => {
        const session = await newSession(browser, browserContextOptions, bob);
        await findRequestableSample(session.page, session.inventoryPage, alice.sample.name);
        const id = await session.inventoryPage.detailsPanel.requestMaterial().sendRequest("Please can I use this.");
        await session.close();
        return id;
      });

    await test.step("Then Alice sees the request, Pending, and rejects it with a reason", async () => {
      const session = await newSession(browser, browserContextOptions, alice);
      await session.requestsPage.openRequest(requestId);
      await expect(session.requestsPage.statusChip("Pending")).toBeVisible();
      await session.requestsPage.rejectRequest("Rejected for test");
      await expect(session.requestsPage.statusChip("Rejected")).toBeVisible();
      await session.close();
    });

    await test.step("Then Bob also sees the request has been rejected", async () => {
      const session = await newSession(browser, browserContextOptions, bob);
      await session.requestsPage.openRequest(requestId);
      await expect(session.requestsPage.statusChip("Rejected")).toBeVisible();
      await session.close();
    });
  });

  test("As a user who has had a sample request made against me I can approve that request and then mark that request as fulfilled", async ({
    browser,
    browserContextOptions,
    clientSysadmin,
    apiContext,
    flowSampleRequestsAvailable,
    flowOperationsNotAvailable,
  }) => {
    // Six separate logins (a fresh browser context each), so allow extra time on a cold backend.
    test.slow();
    void flowSampleRequestsAvailable;
    void flowOperationsNotAvailable;

    const alice = await test.step("Given Alice owns Sample X", async () => {
      const actor = await createInventoryActor(clientSysadmin, apiContext, "Alice");
      const sample = await createTestSample(actor, uniqueName("e2e-sample-requests"));
      return { ...actor, sample };
    });
    const bob = await test.step("And Bob is a separate user", () => createActor(clientSysadmin, "Bob"));

    await test.step("When Alice marks Sample X as requestable", () =>
      markSampleRequestable(browser, browserContextOptions, alice));

    const requestId =
      await test.step("And Bob finds Sample X via the Requestable Samples search and requests it", async () => {
        const session = await newSession(browser, browserContextOptions, bob);
        await findRequestableSample(session.page, session.inventoryPage, alice.sample.name);
        const id = await session.inventoryPage.detailsPanel.requestMaterial().sendRequest("Please can I use this.");
        await session.close();
        return id;
      });

    await test.step("Then Alice sees the request, Pending, and approves it", async () => {
      const session = await newSession(browser, browserContextOptions, alice);
      await session.requestsPage.openRequest(requestId);
      await expect(session.requestsPage.statusChip("Pending")).toBeVisible();
      await session.requestsPage.approveRequest();
      await expect(session.requestsPage.statusChip("Approved")).toBeVisible();
      await session.close();
    });

    await test.step("Then Bob sees the request as Approved, both on the sample and in the Requests view", async () => {
      const session = await newSession(browser, browserContextOptions, bob);
      await findRequestableSample(session.page, session.inventoryPage, alice.sample.name);
      await expect(session.inventoryPage.detailsPanel.requestMaterial().statusChip("Approved")).toBeVisible();
      await session.requestsPage.openRequest(requestId);
      await expect(session.requestsPage.statusChip("Approved")).toBeVisible();
      await session.close();
    });

    await test.step("When Alice finds the approved request and marks it as fulfilled", async () => {
      const session = await newSession(browser, browserContextOptions, alice);
      await session.requestsPage.openRequest(requestId);
      await session.requestsPage.markAsFulfilled();
      await expect(session.requestsPage.statusChip("Fulfilled")).toBeVisible();
      await session.close();
    });

    await test.step("Then Bob sees the request as Fulfilled on the sample", async () => {
      const session = await newSession(browser, browserContextOptions, bob);
      await findRequestableSample(session.page, session.inventoryPage, alice.sample.name);
      await expect(session.inventoryPage.detailsPanel.requestMaterial().statusChip("Fulfilled")).toBeVisible();
      await session.close();
    });
  });

  test("As a user who has had a sample request made against me I can approve that request and then transfer that sample directly", async ({
    browser,
    browserContextOptions,
    clientSysadmin,
    apiContext,
    flowSampleRequestsAvailable,
    flowOperationsNotAvailable,
  }) => {
    // Six separate logins (a fresh browser context each), so allow extra time on a cold backend.
    test.slow();
    void flowSampleRequestsAvailable;
    void flowOperationsNotAvailable;

    const alice = await test.step("Given Alice owns Sample X", async () => {
      const actor = await createInventoryActor(clientSysadmin, apiContext, "Alice");
      const sample = await createTestSample(actor, uniqueName("e2e-sample-requests"));
      return { ...actor, sample };
    });
    const bob = await test.step("And Bob is a separate user", () =>
      createInventoryActor(clientSysadmin, apiContext, "Bob"));

    await test.step("When Alice marks Sample X as requestable", () =>
      markSampleRequestable(browser, browserContextOptions, alice));

    const requestId =
      await test.step("And Bob finds Sample X via the Requestable Samples search and requests it", async () => {
        const session = await newSession(browser, browserContextOptions, bob);
        await findRequestableSample(session.page, session.inventoryPage, alice.sample.name);
        const id = await session.inventoryPage.detailsPanel.requestMaterial().sendRequest("Please can I use this.");
        await session.close();
        return id;
      });

    await test.step("Then Alice approves the request, selects a subsample, and transfers the sample directly", async () => {
      const session = await newSession(browser, browserContextOptions, alice);
      await session.requestsPage.openRequest(requestId);
      await expect(session.requestsPage.statusChip("Pending")).toBeVisible();
      await session.requestsPage.approveRequest();
      await expect(session.requestsPage.statusChip("Approved")).toBeVisible();

      await expect(session.requestsPage.transferSampleButton).toBeDisabled();
      await session.requestsPage.selectFirstAvailableSubsample();
      await expect(session.requestsPage.transferSampleButton).toBeEnabled();

      await session.requestsPage.transferSampleButton.click();
      const dialog = session.page.getByRole("dialog");
      const dialogHeading = dialog.getByRole("heading");
      await expect(dialogHeading).toContainText(alice.sample.name);
      await expect(dialogHeading).toContainText("Bob Requests");
      await expect(dialog.getByRole("alert")).toContainText("Bob Requests");

      const recipientCombobox = dialog.getByRole("combobox");
      await expect(recipientCombobox).toHaveValue(new RegExp(`\\(${bob.username}\\)`));

      const toasts = new ToastsComponent(session.page);
      await dialog.getByRole("button", { name: "Transfer", exact: true }).click();
      await expect(toasts.byVariant("success", `${alice.sample.name} has been transferred to`)).toBeVisible();
      await expect(session.requestsPage.statusChip("Fulfilled")).toBeVisible();
      trackOwnershipTransferred(alice.sample.id, bob);
      await session.close();
    });

    await test.step("Then Bob sees the request Fulfilled, and now owns Sample X on his own Bench", async () => {
      const session = await newSession(browser, browserContextOptions, bob);
      await session.requestsPage.openRequest(requestId);
      await expect(session.requestsPage.statusChip("Fulfilled")).toBeVisible();

      await session.inventoryPage.openRecord("SAMPLE", alice.sample.name);
      await expect(session.inventoryPage.detailsPanel.overviewField("Owner")).toContainText("Bob Requests");

      // Location lives on the subsample, not the sample itself; a fresh sample's only subsample
      // is always named "<sample>.01".
      await session.inventoryPage.openRecord("SUBSAMPLE", `${alice.sample.name}.01`);
      const breadcrumb = session.inventoryPage.detailsPanel
        .section("Overview")
        .getByRole("navigation", { name: "breadcrumb" });
      await expect(breadcrumb).toContainText("My Bench");
      await session.close();
    });
  });

  test("As a user who has had a sample request made against me and inventory.operations.available is set to allowed I can approve that request and then transfer that sample directly", async ({
    browser,
    browserContextOptions,
    clientSysadmin,
    apiContext,
    flowSampleRequestsAvailable,
    flowOperationsAvailable,
  }) => {
    // Six separate logins (a fresh browser context each), so allow extra time on a cold backend.
    test.slow();
    void flowSampleRequestsAvailable;
    void flowOperationsAvailable;

    const alice = await test.step("Given Alice owns Sample X", async () => {
      const actor = await createInventoryActor(clientSysadmin, apiContext, "Alice");
      const sample = await createTestSample(actor, uniqueName("e2e-sample-requests"));
      return { ...actor, sample };
    });
    const bob = await test.step("And Bob is a separate user", () =>
      createInventoryActor(clientSysadmin, apiContext, "Bob"));

    await test.step("When Alice marks Sample X as requestable", () =>
      markSampleRequestable(browser, browserContextOptions, alice));

    const requestId =
      await test.step("And Bob finds Sample X via the Requestable Samples search and requests it", async () => {
        const session = await newSession(browser, browserContextOptions, bob);
        await findRequestableSample(session.page, session.inventoryPage, alice.sample.name);
        const id = await session.inventoryPage.detailsPanel.requestMaterial().sendRequest("Please can I use this.");
        await session.close();
        return id;
      });

    await test.step("Then Alice sees the Pending request with a Prepare Sample button, not a Transfer Sample button", async () => {
      const session = await newSession(browser, browserContextOptions, alice);
      await session.requestsPage.openRequest(requestId);
      await expect(session.requestsPage.statusChip("Pending")).toBeVisible();
      await expect(session.requestsPage.prepareSampleButton).toBeVisible();
      await expect(session.requestsPage.transferSampleButton).toBeHidden();
      await session.close();
    });

    await test.step("Then Alice approves the request, selects a subsample, and transfers the sample directly via Choose Sample to Prepare", async () => {
      const session = await newSession(browser, browserContextOptions, alice);
      await session.requestsPage.openRequest(requestId);
      await session.requestsPage.approveRequest();
      await expect(session.requestsPage.statusChip("Approved")).toBeVisible();

      await expect(session.requestsPage.prepareSampleButton).toBeDisabled();
      await session.requestsPage.selectFirstAvailableSubsample();
      await expect(session.requestsPage.prepareSampleButton).toBeEnabled();

      await session.requestsPage.chooseToTransferDirectly();
      const dialog = session.page.getByRole("dialog");
      const dialogHeading = dialog.getByRole("heading");
      await expect(dialogHeading).toContainText(alice.sample.name);
      await expect(dialogHeading).toContainText("Bob Requests");
      await expect(dialog.getByRole("alert")).toContainText("Bob Requests");

      const recipientCombobox = dialog.getByRole("combobox");
      await expect(recipientCombobox).toHaveValue(new RegExp(`\\(${bob.username}\\)`));

      const toasts = new ToastsComponent(session.page);
      await dialog.getByRole("button", { name: "Transfer", exact: true }).click();
      await expect(toasts.byVariant("success", `${alice.sample.name} has been transferred to`)).toBeVisible();
      await expect(session.requestsPage.statusChip("Fulfilled")).toBeVisible();
      trackOwnershipTransferred(alice.sample.id, bob);
      await session.close();
    });

    await test.step("Then Bob sees the request Fulfilled, and now owns Sample X on his own Bench", async () => {
      const session = await newSession(browser, browserContextOptions, bob);
      await session.requestsPage.openRequest(requestId);
      await expect(session.requestsPage.statusChip("Fulfilled")).toBeVisible();

      await session.inventoryPage.openRecord("SAMPLE", alice.sample.name);
      await expect(session.inventoryPage.detailsPanel.overviewField("Owner")).toContainText("Bob Requests");

      // Location lives on the subsample, not the sample itself; a fresh sample's only subsample
      // is always named "<sample>.01".
      await session.inventoryPage.openRecord("SUBSAMPLE", `${alice.sample.name}.01`);
      const breadcrumb = session.inventoryPage.detailsPanel
        .section("Overview")
        .getByRole("navigation", { name: "breadcrumb" });
      await expect(breadcrumb).toContainText("My Bench");
      await session.close();
    });
  });

  test("As a user who has had a sample request made against me and inventory.operations.available is set to allowed I can approve that request and then use the operations wizard to process the request, before transferring the sample to the requesting user", async ({
    browser,
    browserContextOptions,
    clientSysadmin,
    apiContext,
    flowSampleRequestsAvailable,
    flowOperationsAvailable,
  }) => {
    // Six separate logins (a fresh browser context each), so allow extra time on a cold backend.
    test.slow();
    void flowSampleRequestsAvailable;
    void flowOperationsAvailable;

    const alice = await test.step("Given Alice owns Sample X", async () => {
      const actor = await createInventoryActor(clientSysadmin, apiContext, "Alice");
      const sample = await createTestSample(actor, uniqueName("e2e-sample-requests"));
      return { ...actor, sample };
    });
    const bob = await test.step("And Bob is a separate user", () =>
      createInventoryActor(clientSysadmin, apiContext, "Bob"));
    const aliquotName = `${alice.sample.name} Aliquot`;

    await test.step("When Alice marks Sample X as requestable", () =>
      markSampleRequestable(browser, browserContextOptions, alice));

    const requestId =
      await test.step("And Bob finds Sample X via the Requestable Samples search and requests it", async () => {
        const session = await newSession(browser, browserContextOptions, bob);
        await findRequestableSample(session.page, session.inventoryPage, alice.sample.name);
        const id = await session.inventoryPage.detailsPanel.requestMaterial().sendRequest("Please can I use this.");
        await session.close();
        return id;
      });

    await test.step("Then Alice sees the Pending request with a Prepare Sample button, not a Transfer Sample button", async () => {
      const session = await newSession(browser, browserContextOptions, alice);
      await session.requestsPage.openRequest(requestId);
      await expect(session.requestsPage.statusChip("Pending")).toBeVisible();
      await expect(session.requestsPage.prepareSampleButton).toBeVisible();
      await expect(session.requestsPage.transferSampleButton).toBeHidden();
      await session.close();
    });

    await test.step("Then Alice approves the request, selects a subsample, and uses the Operations Wizard to aliquot it before transferring the new sample to Bob", async () => {
      const session = await newSession(browser, browserContextOptions, alice);
      await session.requestsPage.openRequest(requestId);
      await session.requestsPage.approveRequest();
      await expect(session.requestsPage.statusChip("Approved")).toBeVisible();

      await expect(session.requestsPage.prepareSampleButton).toBeDisabled();
      await session.requestsPage.selectFirstAvailableSubsample();
      await expect(session.requestsPage.prepareSampleButton).toBeEnabled();

      await session.requestsPage.chooseToCreateViaWizard();
      const wizard = new OperationWizardComponent(session.page);
      await wizard.performAliquot(aliquotName);

      const dialog = session.page.getByRole("dialog");
      const dialogHeading = dialog.getByRole("heading");
      await expect(dialogHeading).toContainText(aliquotName);
      await expect(dialogHeading).toContainText("Bob Requests");
      // Reached via the wizard, the info box explains the new sample instead of the usual
      // ownership warning, and names only the new sample, not the requester - the requester is
      // instead named in the "will be moved to {requester}'s bench" bullet below.
      await expect(dialog.getByRole("alert")).toContainText(aliquotName);
      await expect(dialog.getByRole("listitem").filter({ hasText: "moved" })).toContainText("Bob Requests");

      const recipientCombobox = dialog.getByRole("combobox");
      await expect(recipientCombobox).toHaveValue(new RegExp(`\\(${bob.username}\\)`));

      const toasts = new ToastsComponent(session.page);
      await dialog.getByRole("button", { name: "Transfer", exact: true }).click();
      await expect(toasts.byVariant("success", `${aliquotName} has been transferred to`)).toBeVisible();
      await expect(session.requestsPage.statusChip("Fulfilled")).toBeVisible();

      // The newly-created aliquot, not the originally requested Sample X, should be what's
      // recorded as transferred - its Transferred Sample chip must therefore differ from the
      // Requested Sample chip, which always still names Sample X.
      const transferredSampleLink = session.requestsPage.detailField("Transferred Sample").getByRole("link");
      await transferredSampleLink.waitFor({ state: "visible" });
      const transferredSampleId = await transferredSampleLink.innerText();
      const requestedSampleId = await session.requestsPage
        .detailField("Requested Sample")
        .getByRole("link")
        .innerText();
      expect(transferredSampleId).not.toBe(requestedSampleId);
      await session.close();
    });

    await test.step("Then Bob sees the request Fulfilled, Sample X is still Alice's, and he now owns the new Aliquot on his own Bench", async () => {
      const session = await newSession(browser, browserContextOptions, bob);
      await session.requestsPage.openRequest(requestId);
      await expect(session.requestsPage.statusChip("Fulfilled")).toBeVisible();

      // Bob has no general access to Alice's Sample X (only to the Aliquot, which he now owns),
      // so a plain search for it - unlike for the Aliquot below - only finds it via the
      // Requestable filter, which Sample X still qualifies for (its requestable flag is untouched
      // by this flow). A plain search wouldn't even need that filter to find *something*: Sample
      // X's name is a substring of the Aliquot's, so it would resolve the Aliquot's own row
      // instead of correctly finding no match.
      await findRequestableSample(session.page, session.inventoryPage, alice.sample.name);
      await expect(session.inventoryPage.detailsPanel.overviewField("Owner")).toContainText("Alice Requests");

      await session.inventoryPage.openRecord("SAMPLE", aliquotName);
      await expect(session.inventoryPage.detailsPanel.overviewField("Owner")).toContainText("Bob Requests");

      // Location lives on the subsample, not the sample itself; a fresh sample's only subsample
      // is always named "<sample>.01".
      await session.inventoryPage.openRecord("SUBSAMPLE", `${aliquotName}.01`);
      const breadcrumb = session.inventoryPage.detailsPanel
        .section("Overview")
        .getByRole("navigation", { name: "breadcrumb" });
      await expect(breadcrumb).toContainText("My Bench");
      await session.close();
    });
  });

  test("As a user who has had a sample request made against me and inventory.operations.available is set to allowed I can approve that request and then use the operations wizard to process the request, but without transferring the sample to the requesting user", async ({
    browser,
    browserContextOptions,
    clientSysadmin,
    apiContext,
    flowSampleRequestsAvailable,
    flowOperationsAvailable,
  }) => {
    // Five separate logins (a fresh browser context each), so allow extra time on a cold backend.
    test.slow();
    void flowSampleRequestsAvailable;
    void flowOperationsAvailable;

    const alice = await test.step("Given Alice owns Sample X", async () => {
      const actor = await createInventoryActor(clientSysadmin, apiContext, "Alice");
      const sample = await createTestSample(actor, uniqueName("e2e-sample-requests"));
      return { ...actor, sample };
    });
    const bob = await test.step("And Bob is a separate user", () =>
      createInventoryActor(clientSysadmin, apiContext, "Bob"));
    const aliquotName = `${alice.sample.name} Aliquot`;

    await test.step("When Alice marks Sample X as requestable", () =>
      markSampleRequestable(browser, browserContextOptions, alice));

    const requestId =
      await test.step("And Bob finds Sample X via the Requestable Samples search and requests it", async () => {
        const session = await newSession(browser, browserContextOptions, bob);
        await findRequestableSample(session.page, session.inventoryPage, alice.sample.name);
        const id = await session.inventoryPage.detailsPanel.requestMaterial().sendRequest("Please can I use this.");
        await session.close();
        return id;
      });

    await test.step("Then Alice sees the Pending request with a Prepare Sample button, not a Transfer Sample button", async () => {
      const session = await newSession(browser, browserContextOptions, alice);
      await session.requestsPage.openRequest(requestId);
      await expect(session.requestsPage.statusChip("Pending")).toBeVisible();
      await expect(session.requestsPage.prepareSampleButton).toBeVisible();
      await expect(session.requestsPage.transferSampleButton).toBeHidden();
      await session.close();
    });

    await test.step("Then Alice approves the request, selects a subsample, uses the Operations Wizard to aliquot it, but cancels the transfer", async () => {
      const session = await newSession(browser, browserContextOptions, alice);
      await session.requestsPage.openRequest(requestId);
      await session.requestsPage.approveRequest();
      await expect(session.requestsPage.statusChip("Approved")).toBeVisible();

      await expect(session.requestsPage.prepareSampleButton).toBeDisabled();
      await session.requestsPage.selectFirstAvailableSubsample();
      await expect(session.requestsPage.prepareSampleButton).toBeEnabled();

      await session.requestsPage.chooseToCreateViaWizard();
      const wizard = new OperationWizardComponent(session.page);
      await wizard.performAliquot(aliquotName);

      const dialog = session.page.getByRole("dialog");
      const dialogHeading = dialog.getByRole("heading");
      await expect(dialogHeading).toContainText(aliquotName);
      await expect(dialogHeading).toContainText("Bob Requests");
      // Reached via the wizard, the info box explains the new sample instead of the usual
      // ownership warning, and names only the new sample, not the requester - the requester is
      // instead named in the "will be moved to {requester}'s bench" bullet below.
      await expect(dialog.getByRole("alert")).toContainText(aliquotName);
      await expect(dialog.getByRole("listitem").filter({ hasText: "moved" })).toContainText("Bob Requests");

      const recipientCombobox = dialog.getByRole("combobox");
      await expect(recipientCombobox).toHaveValue(new RegExp(`\\(${bob.username}\\)`));

      await dialog.getByRole("button", { name: "Cancel", exact: true }).click();
      await dialog.waitFor({ state: "hidden" });

      // Cancelling the transfer leaves the request Approved, not Fulfilled - the new sample was
      // created (by the wizard's Perform step), but never handed over, so it stays Alice's.
      await expect(session.requestsPage.statusChip("Approved")).toBeVisible();

      await session.inventoryPage.openRecord("SAMPLE", aliquotName);
      await expect(session.inventoryPage.detailsPanel.overviewField("Owner")).toContainText("Alice Requests");

      await session.inventoryPage.openRecord("SUBSAMPLE", `${aliquotName}.01`);
      const breadcrumb = session.inventoryPage.detailsPanel
        .section("Overview")
        .getByRole("navigation", { name: "breadcrumb" });
      await expect(breadcrumb).toContainText("My Bench");
      await session.close();
    });

    await test.step("Then Bob still sees the request as Approved", async () => {
      const session = await newSession(browser, browserContextOptions, bob);
      await session.requestsPage.openRequest(requestId);
      await expect(session.requestsPage.statusChip("Approved")).toBeVisible();
      await session.close();
    });
  });

  test("As a user who has had a sample request made against me I can transfer that sample without first using the approval step", async ({
    browser,
    browserContextOptions,
    clientSysadmin,
    apiContext,
    flowSampleRequestsAvailable,
    flowOperationsNotAvailable,
  }) => {
    // Five separate logins (a fresh browser context each), so allow extra time on a cold backend.
    test.slow();
    void flowSampleRequestsAvailable;
    void flowOperationsNotAvailable;

    const alice = await test.step("Given Alice owns Sample X", async () => {
      const actor = await createInventoryActor(clientSysadmin, apiContext, "Alice");
      const sample = await createTestSample(actor, uniqueName("e2e-sample-requests"));
      return { ...actor, sample };
    });
    const bob = await test.step("And Bob is a separate user", () =>
      createInventoryActor(clientSysadmin, apiContext, "Bob"));

    await test.step("When Alice marks Sample X as requestable", () =>
      markSampleRequestable(browser, browserContextOptions, alice));

    const requestId =
      await test.step("And Bob finds Sample X via the Requestable Samples search and requests it", async () => {
        const session = await newSession(browser, browserContextOptions, bob);
        await findRequestableSample(session.page, session.inventoryPage, alice.sample.name);
        const id = await session.inventoryPage.detailsPanel.requestMaterial().sendRequest("Please can I use this.");
        await session.close();
        return id;
      });

    await test.step("Then Alice sees the request Pending, and transfers the sample directly without approving it first", async () => {
      const session = await newSession(browser, browserContextOptions, alice);
      await session.requestsPage.openRequest(requestId);
      await expect(session.requestsPage.statusChip("Pending")).toBeVisible();

      await expect(session.requestsPage.transferSampleButton).toBeDisabled();
      await session.requestsPage.selectFirstAvailableSubsample();
      await expect(session.requestsPage.transferSampleButton).toBeEnabled();

      await session.requestsPage.transferSampleButton.click();
      const dialog = session.page.getByRole("dialog");
      const dialogHeading = dialog.getByRole("heading");
      await expect(dialogHeading).toContainText(alice.sample.name);
      await expect(dialogHeading).toContainText("Bob Requests");
      await expect(dialog.getByRole("alert")).toContainText("Bob Requests");

      const recipientCombobox = dialog.getByRole("combobox");
      await expect(recipientCombobox).toHaveValue(new RegExp(`\\(${bob.username}\\)`));

      const toasts = new ToastsComponent(session.page);
      await dialog.getByRole("button", { name: "Transfer", exact: true }).click();
      await expect(toasts.byVariant("success", `${alice.sample.name} has been transferred to`)).toBeVisible();
      await expect(session.requestsPage.statusChip("Fulfilled")).toBeVisible();
      trackOwnershipTransferred(alice.sample.id, bob);
      await session.close();
    });

    await test.step("Then Bob sees the request Fulfilled, and now owns Sample X on his own Bench", async () => {
      const session = await newSession(browser, browserContextOptions, bob);
      await session.requestsPage.openRequest(requestId);
      await expect(session.requestsPage.statusChip("Fulfilled")).toBeVisible();

      await session.inventoryPage.openRecord("SAMPLE", alice.sample.name);
      await expect(session.inventoryPage.detailsPanel.overviewField("Owner")).toContainText("Bob Requests");

      // Location lives on the subsample, not the sample itself; a fresh sample's only subsample
      // is always named "<sample>.01".
      await session.inventoryPage.openRecord("SUBSAMPLE", `${alice.sample.name}.01`);
      const breadcrumb = session.inventoryPage.detailsPanel
        .section("Overview")
        .getByRole("navigation", { name: "breadcrumb" });
      await expect(breadcrumb).toContainText("My Bench");
      await session.close();
    });
  });

  test("As a user who has had multiple requests made against a sample that I own I can approve and fulfil one of them, and doing so causes the other request to be rejected", async ({
    browser,
    browserContextOptions,
    clientSysadmin,
    apiContext,
    flowSampleRequestsAvailable,
    flowOperationsNotAvailable,
  }) => {
    // Six separate logins (a fresh browser context each), so allow extra time on a cold backend.
    test.slow();
    void flowSampleRequestsAvailable;
    void flowOperationsNotAvailable;

    const alice = await test.step("Given Alice owns Sample X", async () => {
      const actor = await createInventoryActor(clientSysadmin, apiContext, "Alice");
      const sample = await createTestSample(actor, uniqueName("e2e-sample-requests"));
      return { ...actor, sample };
    });
    const bob = await test.step("And Bob is a separate user", () =>
      createInventoryActor(clientSysadmin, apiContext, "Bob"));
    const carol = await test.step("And Carol is a separate user", () => createActor(clientSysadmin, "Carol"));

    await test.step("When Alice marks Sample X as requestable", () =>
      markSampleRequestable(browser, browserContextOptions, alice));

    const bobRequestId =
      await test.step("And Bob finds Sample X via the Requestable Samples search and requests it", async () => {
        const session = await newSession(browser, browserContextOptions, bob);
        await findRequestableSample(session.page, session.inventoryPage, alice.sample.name);
        const id = await session.inventoryPage.detailsPanel.requestMaterial().sendRequest("Please can I use this.");
        await session.close();
        return id;
      });

    const carolRequestId =
      await test.step("And Carol finds Sample X via the Requestable Samples search and requests it", async () => {
        const session = await newSession(browser, browserContextOptions, carol);
        await findRequestableSample(session.page, session.inventoryPage, alice.sample.name);
        const id = await session.inventoryPage.detailsPanel.requestMaterial().sendRequest("I need this too.");
        await session.close();
        return id;
      });
    expect(carolRequestId).not.toBe(bobRequestId);

    await test.step("Then Alice sees both requests Pending, approves Bob's, and transfers the sample to him directly", async () => {
      const session = await newSession(browser, browserContextOptions, alice);

      await session.requestsPage.openRequest(bobRequestId);
      await expect(session.requestsPage.statusChip("Pending")).toBeVisible();
      await session.requestsPage.openRequest(carolRequestId);
      await expect(session.requestsPage.statusChip("Pending")).toBeVisible();

      await session.requestsPage.openRequest(bobRequestId);
      await session.requestsPage.approveRequest();
      await expect(session.requestsPage.statusChip("Approved")).toBeVisible();

      await expect(session.requestsPage.transferSampleButton).toBeDisabled();
      await session.requestsPage.selectFirstAvailableSubsample();
      await expect(session.requestsPage.transferSampleButton).toBeEnabled();

      await session.requestsPage.transferSampleButton.click();
      const dialog = session.page.getByRole("dialog");
      const dialogHeading = dialog.getByRole("heading");
      await expect(dialogHeading).toContainText(alice.sample.name);
      await expect(dialogHeading).toContainText("Bob Requests");
      await expect(dialog.getByRole("alert")).toContainText("Bob Requests");

      const bullets = dialog.getByRole("listitem");
      await expect(bullets.filter({ hasText: "moved" })).toContainText("Bob Requests");
      const otherRequestsBullet = bullets.filter({ hasText: /active request.*rejected/i });
      await expect(otherRequestsBullet).toContainText("Carol Requests");
      await expect(otherRequestsBullet).not.toContainText("Bob Requests");

      const recipientCombobox = dialog.getByRole("combobox");
      await expect(recipientCombobox).toHaveValue(new RegExp(`\\(${bob.username}\\)`));

      const toasts = new ToastsComponent(session.page);
      await dialog.getByRole("button", { name: "Transfer", exact: true }).click();
      await expect(toasts.byVariant("success", `${alice.sample.name} has been transferred to`)).toBeVisible();
      await expect(session.requestsPage.statusChip("Fulfilled")).toBeVisible();
      trackOwnershipTransferred(alice.sample.id, bob);

      await session.requestsPage.openRequest(carolRequestId);
      await expect(session.requestsPage.statusChip("Rejected")).toBeVisible();
      await session.close();
    });

    await test.step("Then Bob sees his request Fulfilled, and now owns Sample X on his own Bench", async () => {
      const session = await newSession(browser, browserContextOptions, bob);
      await session.requestsPage.openRequest(bobRequestId);
      await expect(session.requestsPage.statusChip("Fulfilled")).toBeVisible();

      await session.inventoryPage.openRecord("SAMPLE", alice.sample.name);
      await expect(session.inventoryPage.detailsPanel.overviewField("Owner")).toContainText("Bob Requests");

      // Location lives on the subsample, not the sample itself; a fresh sample's only subsample
      // is always named "<sample>.01".
      await session.inventoryPage.openRecord("SUBSAMPLE", `${alice.sample.name}.01`);
      const breadcrumb = session.inventoryPage.detailsPanel
        .section("Overview")
        .getByRole("navigation", { name: "breadcrumb" });
      await expect(breadcrumb).toContainText("My Bench");
      await session.close();
    });

    await test.step("Then Carol sees her request Rejected, with a reason", async () => {
      const session = await newSession(browser, browserContextOptions, carol);
      await session.requestsPage.openRequest(carolRequestId);
      await expect(session.requestsPage.statusChip("Rejected")).toBeVisible();
      const reason = session.requestsPage.detailField("Additional notes");
      await expect(reason).toContainText("rejected because the sample was transferred");
      await expect(reason).toContainText("Bob Requests");
      await session.close();
    });
  });
});
