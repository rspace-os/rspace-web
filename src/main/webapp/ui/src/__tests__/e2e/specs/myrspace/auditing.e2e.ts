import { expect } from "@playwright/test";
import { DocumentsClient } from "@/__tests__/e2e/api/clients/DocumentsClient";
import { dynamicUserTest as test } from "@/__tests__/e2e/fixtures/dynamicUser";
import { uniqueName } from "@/__tests__/e2e/testData";

test.describe("Auditing (ELN)", () => {
  test("As a user, I can filter the audit trail by activity domain", async ({
    clientDocuments,
    pageAuditTrail,
    pageMyRSpace,
  }) => {
    const name = uniqueName("e2e-audit-domain-doc");
    const doc = await clientDocuments.create({ name });

    await pageMyRSpace.open();
    await pageMyRSpace.openAuditTrail();
    await pageAuditTrail.isLoaded();
    await pageAuditTrail.filterByGlobalId(doc.globalId);
    await pageAuditTrail.setDomains(["Inventory"]);
    await pageAuditTrail.submitQuery();
    expect(await pageAuditTrail.hitCount()).toBe(0);

    await pageAuditTrail.setDomains(["ELN"]);
    await pageAuditTrail.submitQuery();
    await expect(pageAuditTrail.rowsWithName(name).first()).toBeVisible();
  });

  test("As a user, I can filter the audit trail by date range", async ({
    clientDocuments,
    pageAuditTrail,
    pageMyRSpace,
  }) => {
    const name = uniqueName("e2e-audit-daterange-doc");
    const doc = await clientDocuments.create({ name });

    await pageMyRSpace.open();
    await pageMyRSpace.openAuditTrail();
    await pageAuditTrail.isLoaded();
    await pageAuditTrail.filterByGlobalId(doc.globalId);
    await pageAuditTrail.filterByDateRange("2020-01-15", "2020-01-16");
    await pageAuditTrail.submitQuery();
    expect(await pageAuditTrail.hitCount()).toBe(0);

    await pageAuditTrail.open();
    await pageAuditTrail.isLoaded();
    await pageAuditTrail.filterByGlobalId(doc.globalId);
    await pageAuditTrail.filterByDateRange("2020-01-15");
    await pageAuditTrail.submitQuery();
    await expect(pageAuditTrail.rowsWithName(name).first()).toBeVisible();
  });

  test("As a user, I can filter the audit trail by user", async ({
    apiContext,
    appUser,
    flowFreshPiPermissions,
    pageAuditTrail,
    pageMyRSpace,
  }) => {
    const name = uniqueName("e2e-audit-user-doc");
    const member = await flowFreshPiPermissions("e2eAuditMember");
    const doc = await new DocumentsClient(apiContext, member.apiKey).create({ name });

    await pageMyRSpace.open();
    await pageMyRSpace.openAuditTrail();
    await pageAuditTrail.isLoaded();
    await pageAuditTrail.filterByGlobalId(doc.globalId);
    await pageAuditTrail.filterByUser(appUser.username);
    await pageAuditTrail.submitQuery();
    expect(await pageAuditTrail.hitCount()).toBe(0);

    await pageAuditTrail.open();
    await pageAuditTrail.isLoaded();
    await pageAuditTrail.filterByGlobalId(doc.globalId);
    await pageAuditTrail.filterByUser(member.username);
    await pageAuditTrail.submitQuery();
    await expect(pageAuditTrail.rowsWithName(name).first()).toBeVisible();
  });

  test("As a user, I can filter the audit trail by action", async ({
    clientDocuments,
    pageAuditTrail,
    pageMyRSpace,
  }) => {
    const name = uniqueName("e2e-audit-action-doc");
    const doc = await clientDocuments.create({ name });

    await pageMyRSpace.open();
    await pageMyRSpace.openAuditTrail();
    await pageAuditTrail.isLoaded();
    await pageAuditTrail.filterByGlobalId(doc.globalId);
    await pageAuditTrail.checkAction("DELETE");
    await pageAuditTrail.submitQuery();
    expect(await pageAuditTrail.hitCount()).toBe(0);

    await pageAuditTrail.checkAction("CREATE");
    await pageAuditTrail.submitQuery();
    await expect(pageAuditTrail.rowsWithName(name).first()).toBeVisible();
  });

  test("As a user, I can download the audit report as a CSV file", async ({
    clientDocuments,
    pageAuditTrail,
    pageMyRSpace,
  }) => {
    const name = uniqueName("e2e-audit-download-doc");
    const doc = await clientDocuments.create({ name });

    await pageMyRSpace.open();
    await pageMyRSpace.openAuditTrail();
    await pageAuditTrail.isLoaded();
    await pageAuditTrail.filterByGlobalId(doc.globalId);
    const csv = await pageAuditTrail.downloadReport();
    expect(csv).toContain("Time,User,Action,Type,Resource,Name,Description");
    expect(csv).toContain(name);
  });

  test("As a user, I can click through an audit row to the underlying record", async ({
    clientDocuments,
    pageAuditTrail,
    pageDocument,
    pageMyRSpace,
  }) => {
    const name = uniqueName("e2e-audit-clickthrough-doc");
    const doc = await clientDocuments.create({ name });

    await pageMyRSpace.open();
    await pageMyRSpace.openAuditTrail();
    await pageAuditTrail.isLoaded();
    await pageAuditTrail.filterByGlobalId(doc.globalId);
    await pageAuditTrail.submitQuery();

    await pageAuditTrail.resourceLink(name).click();
    await pageDocument.isLoaded();
    await expect(pageDocument.header.name).toHaveText(name);
  });
});
