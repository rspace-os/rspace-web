import { type Browser, type BrowserContextOptions, expect } from "@playwright/test";
import { InventoryClient } from "@/__tests__/e2e/api/clients/InventoryClient";
import type { SysadminClient } from "@/__tests__/e2e/api/clients/SysadminClient";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { LoginPage } from "@/__tests__/e2e/pageObjects/auth/LoginPage";
import { InventoryPage } from "@/__tests__/e2e/pageObjects/inventory/InventoryPage";
import { RequestsPage } from "@/__tests__/e2e/pageObjects/inventory/RequestsPage";
import { tags } from "@/__tests__/e2e/tags";
import { alphaNumericUnique } from "@/__tests__/e2e/testData";

const PASSWORD = "Passw0rd!23";
const openContexts = new Set<Awaited<ReturnType<Browser["newContext"]>>>();

type Actor = { username: string; password: string };

test.afterEach(async () => {
  await Promise.all([...openContexts].map((context) => context.close()));
  openContexts.clear();
});

async function createActor(clientSysadmin: SysadminClient, firstName: string, apiKey?: string): Promise<Actor> {
  const username = alphaNumericUnique(`e2eReq${firstName}`);
  await clientSysadmin.createUser({
    username,
    password: PASSWORD,
    email: `${username}@example.com`,
    firstName,
    lastName: "Req",
    role: "ROLE_USER",
    apiKey,
  });
  return { username, password: PASSWORD };
}

/** Logs in as `user` in a fresh browser context, so a test can switch between users mid-run. */
async function newSession(browser: Browser, browserContextOptions: BrowserContextOptions, user: Actor) {
  const ctx = await browser.newContext({ ...browserContextOptions, storageState: undefined });
  openContexts.add(ctx);
  ctx.once("close", () => openContexts.delete(ctx));
  try {
    const page = await ctx.newPage();
    const loginPage = new LoginPage(page);
    await loginPage.open();
    await loginPage.login(user.username, user.password);
    await page.waitForURL((url) => url.pathname === "/workspace");
    return { page, close: () => ctx.close() };
  } catch (error) {
    await ctx.close();
    throw error;
  }
}

test.describe("Sample requests", { tag: tags.INVENTORY }, () => {
  test.beforeEach(async ({ flowSampleRequestsAvailable }) => {
    void flowSampleRequestsAvailable;
  });

  test(`As a user who does not own a requestable sample, I can request it and then cancel that request`, async ({
    browser,
    browserContextOptions,
    clientSysadmin,
    apiContext,
  }) => {
    test.slow();

    const ownerApiKey = alphaNumericUnique("e2eReqOwnerKey").slice(0, 32);
    const owner = await createActor(clientSysadmin, "Owner", ownerApiKey);
    const ownerClient = new InventoryClient(apiContext, ownerApiKey);
    const requester = await createActor(clientSysadmin, "Requester");

    const sampleName = alphaNumericUnique("e2eReqSample");
    await test.step("Given Sample X exists, owned by User A", () => ownerClient.createSample({ name: sampleName }));

    await test.step("When User A marks Sample X as requestable", async () => {
      const session = await newSession(browser, browserContextOptions, owner);
      const pageInventory = new InventoryPage(session.page);
      await pageInventory.openRecord("SAMPLE", sampleName);
      await pageInventory.detailsPanel.enterEditMode();
      await pageInventory.detailsPanel.requestableSwitch().check();
      await pageInventory.detailsPanel.saveEdit();
      await expect(pageInventory.detailsPanel.requestableSwitch()).toBeChecked();
      await session.close();
    });

    const requestId =
      await test.step("When User B searches Requestable Samples for Sample X and requests it", async () => {
        const session = await newSession(browser, browserContextOptions, requester);
        const pageInventory = new InventoryPage(session.page);
        await pageInventory.openSearch("SAMPLE");
        await pageInventory.searchPanel.filterChip("Requestable").click();
        await session.page.getByRole("menuitem", { name: "Requestable only", exact: true }).click();
        await pageInventory.searchPanel.search(sampleName);
        await pageInventory.searchPanel.open(sampleName);
        const id = await pageInventory.detailsPanel.requestMaterial().sendRequest("Need 2ml for testing");
        await session.close();
        return id;
      });

    await test.step("Then User A sees a Pending request for Sample X from User B", async () => {
      const session = await newSession(browser, browserContextOptions, owner);
      const pageRequests = new RequestsPage(session.page);
      await pageRequests.openRequest(requestId);
      await expect(pageRequests.heading).toContainText(sampleName);
      await expect(pageRequests.statusChip("Pending")).toBeVisible();
      await session.close();
    });

    await test.step("When User B cancels the request from Sample X's own page", async () => {
      const session = await newSession(browser, browserContextOptions, requester);
      const pageInventory = new InventoryPage(session.page);
      await pageInventory.openRecord("SAMPLE", sampleName);
      await pageInventory.detailsPanel.requestMaterial().cancelRequest();
      await session.close();
    });

    await test.step("Then User A sees the request for Sample X from User B is now Cancelled", async () => {
      const session = await newSession(browser, browserContextOptions, owner);
      const pageRequests = new RequestsPage(session.page);
      await pageRequests.openRequest(requestId);
      await expect(pageRequests.statusChip("Cancelled")).toBeVisible();
      await session.close();
    });

    const secondRequestId =
      await test.step("When User B searches Requestable Samples for Sample X again and makes another request", async () => {
        const session = await newSession(browser, browserContextOptions, requester);
        const pageInventory = new InventoryPage(session.page);
        await pageInventory.openSearch("SAMPLE");
        await pageInventory.searchPanel.filterChip("Requestable").click();
        await session.page.getByRole("menuitem", { name: "Requestable only", exact: true }).click();
        await pageInventory.searchPanel.search(sampleName);
        await pageInventory.searchPanel.open(sampleName);
        const id = await pageInventory.detailsPanel.requestMaterial().sendRequest("Need 2ml for testing, again");
        await session.close();
        return id;
      });

    await test.step("Then User A sees the new request for Sample X from User B is Pending", async () => {
      const session = await newSession(browser, browserContextOptions, owner);
      const pageRequests = new RequestsPage(session.page);
      await pageRequests.openRequest(secondRequestId);
      await expect(pageRequests.statusChip("Pending")).toBeVisible();
      await session.close();
    });
  });

  test(`As a user who has had a sample request made against me, I can reject that request`, async ({
    browser,
    browserContextOptions,
    clientSysadmin,
    apiContext,
  }) => {
    test.slow();

    const ownerApiKey = alphaNumericUnique("e2eReqOwnerKey").slice(0, 32);
    const owner = await createActor(clientSysadmin, "Owner", ownerApiKey);
    const ownerClient = new InventoryClient(apiContext, ownerApiKey);
    const requester = await createActor(clientSysadmin, "Requester");

    const sampleName = alphaNumericUnique("e2eReqSample");
    await test.step("Given Sample X exists, owned by User A", () => ownerClient.createSample({ name: sampleName }));

    await test.step("When User A marks Sample X as requestable", async () => {
      const session = await newSession(browser, browserContextOptions, owner);
      const pageInventory = new InventoryPage(session.page);
      await pageInventory.openRecord("SAMPLE", sampleName);
      await pageInventory.detailsPanel.enterEditMode();
      await pageInventory.detailsPanel.requestableSwitch().check();
      await pageInventory.detailsPanel.saveEdit();
      await expect(pageInventory.detailsPanel.requestableSwitch()).toBeChecked();
      await session.close();
    });

    const requestId =
      await test.step("When User B searches Requestable Samples for Sample X and requests it", async () => {
        const session = await newSession(browser, browserContextOptions, requester);
        const pageInventory = new InventoryPage(session.page);
        await pageInventory.openSearch("SAMPLE");
        await pageInventory.searchPanel.filterChip("Requestable").click();
        await session.page.getByRole("menuitem", { name: "Requestable only", exact: true }).click();
        await pageInventory.searchPanel.search(sampleName);
        await pageInventory.searchPanel.open(sampleName);
        const id = await pageInventory.detailsPanel.requestMaterial().sendRequest("Need 2ml for testing");
        await session.close();
        return id;
      });

    const rejectReason = "Rejected for test";

    await test.step("Then User A sees a Pending request for Sample X from User B and rejects it", async () => {
      const session = await newSession(browser, browserContextOptions, owner);
      const pageRequests = new RequestsPage(session.page);
      await pageRequests.openRequest(requestId);
      await expect(pageRequests.statusChip("Pending")).toBeVisible();

      await pageRequests.rejectRequest(rejectReason);

      await expect(pageRequests.statusChip("Rejected")).toBeVisible();
      await expect(pageRequests.detailField("Additional notes")).toContainText(rejectReason);
      await session.close();
    });

    await test.step("Then User B sees the request for Sample X is now Rejected", async () => {
      const session = await newSession(browser, browserContextOptions, requester);
      const pageRequests = new RequestsPage(session.page);
      await pageRequests.openRequest(requestId);
      await expect(pageRequests.statusChip("Rejected")).toBeVisible();
      await session.close();
    });
  });

  test(`As a user who has had a sample request made against me, I can approve that request and then mark it as fulfilled`, async ({
    browser,
    browserContextOptions,
    clientSysadmin,
    apiContext,
  }) => {
    test.slow();

    const ownerApiKey = alphaNumericUnique("e2eReqOwnerKey").slice(0, 32);
    const owner = await createActor(clientSysadmin, "Owner", ownerApiKey);
    const ownerClient = new InventoryClient(apiContext, ownerApiKey);
    const requester = await createActor(clientSysadmin, "Requester");

    const sampleName = alphaNumericUnique("e2eReqSample");
    await test.step("Given Sample X exists, owned by User A", () => ownerClient.createSample({ name: sampleName }));

    await test.step("When User A marks Sample X as requestable", async () => {
      const session = await newSession(browser, browserContextOptions, owner);
      const pageInventory = new InventoryPage(session.page);
      await pageInventory.openRecord("SAMPLE", sampleName);
      await pageInventory.detailsPanel.enterEditMode();
      await pageInventory.detailsPanel.requestableSwitch().check();
      await pageInventory.detailsPanel.saveEdit();
      await expect(pageInventory.detailsPanel.requestableSwitch()).toBeChecked();
      await session.close();
    });

    const requestId =
      await test.step("When User B searches Requestable Samples for Sample X and requests it", async () => {
        const session = await newSession(browser, browserContextOptions, requester);
        const pageInventory = new InventoryPage(session.page);
        await pageInventory.openSearch("SAMPLE");
        await pageInventory.searchPanel.filterChip("Requestable").click();
        await session.page.getByRole("menuitem", { name: "Requestable only", exact: true }).click();
        await pageInventory.searchPanel.search(sampleName);
        await pageInventory.searchPanel.open(sampleName);
        const id = await pageInventory.detailsPanel.requestMaterial().sendRequest("Need 2ml for testing");
        await session.close();
        return id;
      });

    await test.step("Then User A sees a Pending request for Sample X from User B and approves it", async () => {
      const session = await newSession(browser, browserContextOptions, owner);
      const pageRequests = new RequestsPage(session.page);
      await pageRequests.openRequest(requestId);
      await expect(pageRequests.statusChip("Pending")).toBeVisible();

      await pageRequests.approveRequest();

      await expect(pageRequests.statusChip("Approved")).toBeVisible();
      await session.close();
    });

    await test.step("Then User B sees Sample X's own page, and the Requests view, both show the request as Approved", async () => {
      const session = await newSession(browser, browserContextOptions, requester);
      const pageInventory = new InventoryPage(session.page);
      await pageInventory.openRecord("SAMPLE", sampleName);
      await expect(pageInventory.detailsPanel.requestMaterial().statusChip("Approved")).toBeVisible();

      const pageRequests = new RequestsPage(session.page);
      await pageRequests.openRequest(requestId);
      await expect(pageRequests.statusChip("Approved")).toBeVisible();
      await session.close();
    });

    await test.step("When User A finds the approved request and marks it as fulfilled", async () => {
      const session = await newSession(browser, browserContextOptions, owner);
      const pageRequests = new RequestsPage(session.page);
      await pageRequests.openRequest(requestId);
      await expect(pageRequests.statusChip("Approved")).toBeVisible();

      await pageRequests.markAsFulfilled();

      await expect(pageRequests.statusChip("Fulfilled")).toBeVisible();
      await session.close();
    });

    await test.step("Then User B sees Sample X's own page shows the request as Fulfilled", async () => {
      const session = await newSession(browser, browserContextOptions, requester);
      const pageInventory = new InventoryPage(session.page);
      await pageInventory.openRecord("SAMPLE", sampleName);
      await expect(pageInventory.detailsPanel.requestMaterial().statusChip("Fulfilled")).toBeVisible();
      await session.close();
    });
  });

  test(`As a user who has had a sample request made against me, I can approve that request and then transfer that sample directly`, async ({
    browser,
    browserContextOptions,
    clientSysadmin,
    apiContext,
  }) => {
    test.slow();

    const ownerApiKey = alphaNumericUnique("e2eReqOwnerKey").slice(0, 32);
    const owner = await createActor(clientSysadmin, "Owner", ownerApiKey);
    const ownerClient = new InventoryClient(apiContext, ownerApiKey);
    const requester = await createActor(clientSysadmin, "Requester");

    const sampleName = alphaNumericUnique("e2eReqSample");
    // RSpace's own subsample-naming convention: a fresh sample's first (and here, only)
    // subsample is named "<sample name>.01".
    const subsampleName = `${sampleName}.01`;
    await test.step("Given Sample X exists, owned by User A", () => ownerClient.createSample({ name: sampleName }));

    await test.step("When User A marks Sample X as requestable", async () => {
      const session = await newSession(browser, browserContextOptions, owner);
      const pageInventory = new InventoryPage(session.page);
      await pageInventory.openRecord("SAMPLE", sampleName);
      await pageInventory.detailsPanel.enterEditMode();
      await pageInventory.detailsPanel.requestableSwitch().check();
      await pageInventory.detailsPanel.saveEdit();
      await expect(pageInventory.detailsPanel.requestableSwitch()).toBeChecked();
      await session.close();
    });

    const requestId =
      await test.step("When User B searches Requestable Samples for Sample X and requests it", async () => {
        const session = await newSession(browser, browserContextOptions, requester);
        const pageInventory = new InventoryPage(session.page);
        await pageInventory.openSearch("SAMPLE");
        await pageInventory.searchPanel.filterChip("Requestable").click();
        await session.page.getByRole("menuitem", { name: "Requestable only", exact: true }).click();
        await pageInventory.searchPanel.search(sampleName);
        await pageInventory.searchPanel.open(sampleName);
        const id = await pageInventory.detailsPanel.requestMaterial().sendRequest("Need 2ml for testing");
        await session.close();
        return id;
      });

    await test.step("Then User A approves the request, selects a subsample, and transfers the whole sample", async () => {
      const session = await newSession(browser, browserContextOptions, owner);
      const pageRequests = new RequestsPage(session.page);
      await pageRequests.openRequest(requestId);
      await expect(pageRequests.statusChip("Pending")).toBeVisible();

      await pageRequests.approveRequest();
      await expect(pageRequests.statusChip("Approved")).toBeVisible();

      await expect(pageRequests.prepareSampleButton).toBeDisabled();
      await pageRequests.selectFirstAvailableSubsample();
      await expect(pageRequests.prepareSampleButton).toBeEnabled();

      await pageRequests.prepareAndTransferSample();

      await expect(pageRequests.statusChip("Fulfilled")).toBeVisible();
      await session.close();
    });

    await test.step("Then User B sees the request is Fulfilled, and now owns Sample X on their own Bench", async () => {
      const session = await newSession(browser, browserContextOptions, requester);

      const pageRequests = new RequestsPage(session.page);
      await pageRequests.openRequest(requestId);
      await expect(pageRequests.statusChip("Fulfilled")).toBeVisible();

      const pageInventory = new InventoryPage(session.page);
      await pageInventory.openRecord("SAMPLE", sampleName);
      await expect(pageInventory.detailsPanel.overviewField("Owner")).toContainText("Requester Req");

      await pageInventory.openSearch("SUBSAMPLE");
      await pageInventory.searchPanel.search(subsampleName);
      await pageInventory.searchPanel.open(subsampleName);
      const breadcrumb = pageInventory.detailsPanel.section("Overview").getByRole("navigation", { name: "breadcrumb" });
      await expect(breadcrumb.getByRole("listitem").first()).toHaveText("My Bench");

      await session.close();
    });
  });

  test(`As a user who has had a sample request made against me, I can transfer that sample without first using the approval step`, async ({
    browser,
    browserContextOptions,
    clientSysadmin,
    apiContext,
  }) => {
    test.slow();

    const ownerApiKey = alphaNumericUnique("e2eReqOwnerKey").slice(0, 32);
    const owner = await createActor(clientSysadmin, "Owner", ownerApiKey);
    const ownerClient = new InventoryClient(apiContext, ownerApiKey);
    const requester = await createActor(clientSysadmin, "Requester");

    const sampleName = alphaNumericUnique("e2eReqSample");
    // RSpace's own subsample-naming convention: a fresh sample's first (and here, only)
    // subsample is named "<sample name>.01".
    const subsampleName = `${sampleName}.01`;
    await test.step("Given Sample X exists, owned by User A", () => ownerClient.createSample({ name: sampleName }));

    await test.step("When User A marks Sample X as requestable", async () => {
      const session = await newSession(browser, browserContextOptions, owner);
      const pageInventory = new InventoryPage(session.page);
      await pageInventory.openRecord("SAMPLE", sampleName);
      await pageInventory.detailsPanel.enterEditMode();
      await pageInventory.detailsPanel.requestableSwitch().check();
      await pageInventory.detailsPanel.saveEdit();
      await expect(pageInventory.detailsPanel.requestableSwitch()).toBeChecked();
      await session.close();
    });

    const requestId =
      await test.step("When User B searches Requestable Samples for Sample X and requests it", async () => {
        const session = await newSession(browser, browserContextOptions, requester);
        const pageInventory = new InventoryPage(session.page);
        await pageInventory.openSearch("SAMPLE");
        await pageInventory.searchPanel.filterChip("Requestable").click();
        await session.page.getByRole("menuitem", { name: "Requestable only", exact: true }).click();
        await pageInventory.searchPanel.search(sampleName);
        await pageInventory.searchPanel.open(sampleName);
        const id = await pageInventory.detailsPanel.requestMaterial().sendRequest("Need 2ml for testing");
        await session.close();
        return id;
      });

    await test.step("Then User A sees the Pending request, selects a subsample, and transfers the whole sample without approving first", async () => {
      const session = await newSession(browser, browserContextOptions, owner);
      const pageRequests = new RequestsPage(session.page);
      await pageRequests.openRequest(requestId);
      await expect(pageRequests.statusChip("Pending")).toBeVisible();

      await expect(pageRequests.prepareSampleButton).toBeDisabled();
      await pageRequests.selectFirstAvailableSubsample();
      await expect(pageRequests.prepareSampleButton).toBeEnabled();

      await pageRequests.prepareAndTransferSample();

      await expect(pageRequests.statusChip("Fulfilled")).toBeVisible();
      await session.close();
    });

    await test.step("Then User B sees the request is Fulfilled, and now owns Sample X on their own Bench", async () => {
      const session = await newSession(browser, browserContextOptions, requester);

      const pageRequests = new RequestsPage(session.page);
      await pageRequests.openRequest(requestId);
      await expect(pageRequests.statusChip("Fulfilled")).toBeVisible();

      const pageInventory = new InventoryPage(session.page);
      await pageInventory.openRecord("SAMPLE", sampleName);
      await expect(pageInventory.detailsPanel.overviewField("Owner")).toContainText("Requester Req");

      await pageInventory.openSearch("SUBSAMPLE");
      await pageInventory.searchPanel.search(subsampleName);
      await pageInventory.searchPanel.open(subsampleName);
      const breadcrumb = pageInventory.detailsPanel.section("Overview").getByRole("navigation", { name: "breadcrumb" });
      await expect(breadcrumb.getByRole("listitem").first()).toHaveText("My Bench");

      await session.close();
    });
  });

  test(`As a user who has had a sample request made against me, I can approve that request and then use the operations wizard to process the request, before transferring the sample to the requesting user`, async ({
    browser,
    browserContextOptions,
    clientSysadmin,
    apiContext,
  }) => {
    test.slow();

    const ownerApiKey = alphaNumericUnique("e2eReqOwnerKey").slice(0, 32);
    const owner = await createActor(clientSysadmin, "Owner", ownerApiKey);
    const ownerClient = new InventoryClient(apiContext, ownerApiKey);
    const requester = await createActor(clientSysadmin, "Requester");

    const sampleName = alphaNumericUnique("e2eReqSample");
    const aliquotName = `${sampleName} Aliquot`;
    // RSpace's own subsample-naming convention: a fresh sample's first subsample is named
    // "<sample name>.01", whether the sample was created directly or by an operation.
    const aliquotSubsampleName = `${aliquotName}.01`;
    await test.step("Given Sample X exists, owned by User A", () => ownerClient.createSample({ name: sampleName }));

    await test.step("When User A marks Sample X as requestable", async () => {
      const session = await newSession(browser, browserContextOptions, owner);
      const pageInventory = new InventoryPage(session.page);
      await pageInventory.openRecord("SAMPLE", sampleName);
      await pageInventory.detailsPanel.enterEditMode();
      await pageInventory.detailsPanel.requestableSwitch().check();
      await pageInventory.detailsPanel.saveEdit();
      await expect(pageInventory.detailsPanel.requestableSwitch()).toBeChecked();
      await session.close();
    });

    const requestId =
      await test.step("When User B searches Requestable Samples for Sample X and requests it", async () => {
        const session = await newSession(browser, browserContextOptions, requester);
        const pageInventory = new InventoryPage(session.page);
        await pageInventory.openSearch("SAMPLE");
        await pageInventory.searchPanel.filterChip("Requestable").click();
        await session.page.getByRole("menuitem", { name: "Requestable only", exact: true }).click();
        await pageInventory.searchPanel.search(sampleName);
        await pageInventory.searchPanel.open(sampleName);
        const id = await pageInventory.detailsPanel.requestMaterial().sendRequest("Need 2ml for testing");
        await session.close();
        return id;
      });

    const aliquotGlobalId =
      await test.step("Then User A approves the request, selects a subsample, and runs the Aliquot operation to prepare and transfer a new sample", async () => {
        const session = await newSession(browser, browserContextOptions, owner);
        const pageRequests = new RequestsPage(session.page);
        await pageRequests.openRequest(requestId);
        await expect(pageRequests.statusChip("Pending")).toBeVisible();

        await pageRequests.approveRequest();
        await expect(pageRequests.statusChip("Approved")).toBeVisible();

        await expect(pageRequests.prepareSampleButton).toBeDisabled();
        await pageRequests.selectFirstAvailableSubsample();
        await expect(pageRequests.prepareSampleButton).toBeEnabled();

        const { globalId } = await pageRequests.performAliquotAndTransfer(aliquotName);

        await expect(pageRequests.statusChip("Fulfilled")).toBeVisible();
        await expect(pageRequests.detailField("Transferred Sample")).toContainText(globalId);
        await session.close();
        return globalId;
      });

    await test.step("Then User B sees the request is Fulfilled, and now owns the new Aliquot sample on their own Bench", async () => {
      const session = await newSession(browser, browserContextOptions, requester);

      const pageRequests = new RequestsPage(session.page);
      await pageRequests.openRequest(requestId);
      await expect(pageRequests.statusChip("Fulfilled")).toBeVisible();
      await expect(pageRequests.detailField("Transferred Sample")).toContainText(aliquotGlobalId);

      const pageInventory = new InventoryPage(session.page);
      await pageInventory.openRecord("SAMPLE", aliquotName);
      await expect(pageInventory.detailsPanel.overviewField("Owner")).toContainText("Requester Req");

      await pageInventory.openSearch("SUBSAMPLE");
      await pageInventory.searchPanel.search(aliquotSubsampleName);
      await pageInventory.searchPanel.open(aliquotSubsampleName);
      const breadcrumb = pageInventory.detailsPanel.section("Overview").getByRole("navigation", { name: "breadcrumb" });
      await expect(breadcrumb.getByRole("listitem").first()).toHaveText("My Bench");

      await session.close();
    });
  });

  test(`As a user who has had a sample request made against me, I can approve that request and then use the operations wizard to process the request, but without transferring the sample to the requesting user`, async ({
    browser,
    browserContextOptions,
    clientSysadmin,
    apiContext,
  }) => {
    test.slow();

    const ownerApiKey = alphaNumericUnique("e2eReqOwnerKey").slice(0, 32);
    const owner = await createActor(clientSysadmin, "Owner", ownerApiKey);
    const ownerClient = new InventoryClient(apiContext, ownerApiKey);
    const requester = await createActor(clientSysadmin, "Requester");

    const sampleName = alphaNumericUnique("e2eReqSample");
    const aliquotName = `${sampleName} Aliquot`;
    const aliquotSubsampleName = `${aliquotName}.01`;
    await test.step("Given Sample X exists, owned by User A", () => ownerClient.createSample({ name: sampleName }));

    await test.step("When User A marks Sample X as requestable", async () => {
      const session = await newSession(browser, browserContextOptions, owner);
      const pageInventory = new InventoryPage(session.page);
      await pageInventory.openRecord("SAMPLE", sampleName);
      await pageInventory.detailsPanel.enterEditMode();
      await pageInventory.detailsPanel.requestableSwitch().check();
      await pageInventory.detailsPanel.saveEdit();
      await expect(pageInventory.detailsPanel.requestableSwitch()).toBeChecked();
      await session.close();
    });

    const requestId =
      await test.step("When User B searches Requestable Samples for Sample X and requests it", async () => {
        const session = await newSession(browser, browserContextOptions, requester);
        const pageInventory = new InventoryPage(session.page);
        await pageInventory.openSearch("SAMPLE");
        await pageInventory.searchPanel.filterChip("Requestable").click();
        await session.page.getByRole("menuitem", { name: "Requestable only", exact: true }).click();
        await pageInventory.searchPanel.search(sampleName);
        await pageInventory.searchPanel.open(sampleName);
        const id = await pageInventory.detailsPanel.requestMaterial().sendRequest("Need 2ml for testing");
        await session.close();
        return id;
      });

    await test.step("Then User A approves the request, selects a subsample, runs the Aliquot operation, and cancels the transfer", async () => {
      const session = await newSession(browser, browserContextOptions, owner);
      const pageRequests = new RequestsPage(session.page);
      await pageRequests.openRequest(requestId);
      await expect(pageRequests.statusChip("Pending")).toBeVisible();

      await pageRequests.approveRequest();
      await expect(pageRequests.statusChip("Approved")).toBeVisible();

      await expect(pageRequests.prepareSampleButton).toBeDisabled();
      await pageRequests.selectFirstAvailableSubsample();
      await expect(pageRequests.prepareSampleButton).toBeEnabled();

      await pageRequests.performAliquotWithoutTransferring(aliquotName);

      // Cancelling the transfer never fulfils the request: only confirming one does.
      await expect(pageRequests.statusChip("Approved")).toBeVisible();
      await session.close();
    });

    await test.step("Then User A still owns the new Aliquot sample, on their own Bench, since the transfer was cancelled", async () => {
      const session = await newSession(browser, browserContextOptions, owner);
      const pageInventory = new InventoryPage(session.page);
      await pageInventory.openRecord("SAMPLE", aliquotName);
      await expect(pageInventory.detailsPanel.overviewField("Owner")).toContainText("Owner Req");

      await pageInventory.openSearch("SUBSAMPLE");
      await pageInventory.searchPanel.search(aliquotSubsampleName);
      await pageInventory.searchPanel.open(aliquotSubsampleName);
      const breadcrumb = pageInventory.detailsPanel.section("Overview").getByRole("navigation", { name: "breadcrumb" });
      await expect(breadcrumb.getByRole("listitem").first()).toHaveText("My Bench");

      await session.close();
    });

    await test.step("Then User B still sees the request as Approved", async () => {
      const session = await newSession(browser, browserContextOptions, requester);
      const pageRequests = new RequestsPage(session.page);
      await pageRequests.openRequest(requestId);
      await expect(pageRequests.statusChip("Approved")).toBeVisible();
      await session.close();
    });
  });

  test(`As a user who has had multiple requests made against a sample that I own, I can approve and fulfil one of them, and doing so causes the other request to be rejected`, async ({
    browser,
    browserContextOptions,
    clientSysadmin,
    apiContext,
  }) => {
    test.slow();

    const ownerApiKey = alphaNumericUnique("e2eReqOwnerKey").slice(0, 32);
    const owner = await createActor(clientSysadmin, "Owner", ownerApiKey);
    const ownerClient = new InventoryClient(apiContext, ownerApiKey);
    const requesterB = await createActor(clientSysadmin, "RequesterB");
    const requesterC = await createActor(clientSysadmin, "RequesterC");

    const sampleName = alphaNumericUnique("e2eReqSample");
    const subsampleName = `${sampleName}.01`;
    await test.step("Given Sample X exists, owned by User A", () => ownerClient.createSample({ name: sampleName }));

    await test.step("When User A marks Sample X as requestable", async () => {
      const session = await newSession(browser, browserContextOptions, owner);
      const pageInventory = new InventoryPage(session.page);
      await pageInventory.openRecord("SAMPLE", sampleName);
      await pageInventory.detailsPanel.enterEditMode();
      await pageInventory.detailsPanel.requestableSwitch().check();
      await pageInventory.detailsPanel.saveEdit();
      await expect(pageInventory.detailsPanel.requestableSwitch()).toBeChecked();
      await session.close();
    });

    const requestBId =
      await test.step("When User B searches Requestable Samples for Sample X and requests it", async () => {
        const session = await newSession(browser, browserContextOptions, requesterB);
        const pageInventory = new InventoryPage(session.page);
        await pageInventory.openSearch("SAMPLE");
        await pageInventory.searchPanel.filterChip("Requestable").click();
        await session.page.getByRole("menuitem", { name: "Requestable only", exact: true }).click();
        await pageInventory.searchPanel.search(sampleName);
        await pageInventory.searchPanel.open(sampleName);
        const id = await pageInventory.detailsPanel.requestMaterial().sendRequest("Need 2ml for testing (B)");
        await session.close();
        return id;
      });

    const requestCId =
      await test.step("When User C searches Requestable Samples for Sample X and requests it", async () => {
        const session = await newSession(browser, browserContextOptions, requesterC);
        const pageInventory = new InventoryPage(session.page);
        await pageInventory.openSearch("SAMPLE");
        await pageInventory.searchPanel.filterChip("Requestable").click();
        await session.page.getByRole("menuitem", { name: "Requestable only", exact: true }).click();
        await pageInventory.searchPanel.search(sampleName);
        await pageInventory.searchPanel.open(sampleName);
        const id = await pageInventory.detailsPanel.requestMaterial().sendRequest("Need 2ml for testing (C)");
        await session.close();
        return id;
      });

    await test.step("Then User A sees both requests Pending, approves User B's, prepares and transfers the sample, seeing warnings about the other request along the way", async () => {
      const session = await newSession(browser, browserContextOptions, owner);
      const pageRequests = new RequestsPage(session.page);

      await pageRequests.openRequest(requestBId);
      await expect(pageRequests.statusChip("Pending")).toBeVisible();

      await pageRequests.openRequest(requestCId);
      await expect(pageRequests.statusChip("Pending")).toBeVisible();

      await pageRequests.openRequest(requestBId);
      await pageRequests.approveRequest();
      await expect(pageRequests.statusChip("Approved")).toBeVisible();

      await expect(pageRequests.prepareSampleButton).toBeDisabled();
      await pageRequests.selectFirstAvailableSubsample();
      await expect(pageRequests.prepareSampleButton).toBeEnabled();

      await pageRequests.prepareSampleButton.click();
      const chooseDialog = pageRequests.chooseMethodDialog();
      await chooseDialog
        .getByRole("radio", { name: "Transfer the existing sample and all subsamples", exact: true })
        .check();
      await expect(chooseDialog).toContainText(
        "There are other requests against this sample that are either in the Pending or Approved states.",
      );
      await chooseDialog.getByRole("button", { name: "Proceed", exact: true }).click();

      const transferDialog = pageRequests.transferDialog();
      await expect(transferDialog).toContainText(
        "Other Pending or Approved requests from other users will be closed automatically when this sample is transferred.",
      );
      await transferDialog.getByRole("button", { name: "Transfer", exact: true }).click();
      await transferDialog.waitFor({ state: "hidden" });

      await expect(pageRequests.statusChip("Fulfilled")).toBeVisible();

      await pageRequests.openRequest(requestCId);
      await expect(pageRequests.statusChip("Rejected")).toBeVisible();

      await session.close();
    });

    await test.step("Then User B sees their request is Fulfilled, and now owns Sample X on their own Bench", async () => {
      const session = await newSession(browser, browserContextOptions, requesterB);

      const pageRequests = new RequestsPage(session.page);
      await pageRequests.openRequest(requestBId);
      await expect(pageRequests.statusChip("Fulfilled")).toBeVisible();

      const pageInventory = new InventoryPage(session.page);
      await pageInventory.openRecord("SAMPLE", sampleName);
      await expect(pageInventory.detailsPanel.overviewField("Owner")).toContainText("RequesterB Req");

      await pageInventory.openSearch("SUBSAMPLE");
      await pageInventory.searchPanel.search(subsampleName);
      await pageInventory.searchPanel.open(subsampleName);
      const breadcrumb = pageInventory.detailsPanel.section("Overview").getByRole("navigation", { name: "breadcrumb" });
      await expect(breadcrumb.getByRole("listitem").first()).toHaveText("My Bench");

      await session.close();
    });

    await test.step("Then User C sees their request is Rejected, with a reason", async () => {
      const session = await newSession(browser, browserContextOptions, requesterC);
      const pageRequests = new RequestsPage(session.page);
      await pageRequests.openRequest(requestCId);
      await expect(pageRequests.statusChip("Rejected")).toBeVisible();
      await expect(pageRequests.detailField("Additional notes")).toContainText(
        "This request has been cancelled as a result of the sample being transferred",
      );
      await session.close();
    });
  });
});
