import { expect } from "@playwright/test";
import { test } from "@/__tests__/e2e/fixtures/flows";
import type { AuditTrailPage } from "@/__tests__/e2e/pageObjects/myrspace/AuditTrailPage";
import { tags } from "@/__tests__/e2e/tags";
import { uniqueName } from "@/__tests__/e2e/testData";

async function expectNameInResults(auditTrail: AuditTrailPage, name: string): Promise<void> {
  await expect(async () => {
    await auditTrail.submitQuery();
    await expect(auditTrail.rowsWithName(name).first()).toBeVisible({ timeout: 3_000 });
  }).toPass();
}

test.describe("Inventory audit trail", { tag: [tags.INVENTORY, tags.MOBILE] }, () => {
  test("As a user, I can find a sample's history in the ELN audit trail by its global id", async ({
    clientInventory,
    pageMyRSpace,
    pageAuditTrail,
  }) => {
    const sampleName = uniqueName("e2e-audit-sample");
    const sample = await clientInventory.createSample({ name: sampleName });
    const globalId = sample.globalId;

    await pageMyRSpace.open();
    await pageMyRSpace.openAuditTrail();
    await pageAuditTrail.isLoaded();
    await pageAuditTrail.filterByGlobalId(globalId);

    await expectNameInResults(pageAuditTrail, sampleName);

    await pageAuditTrail.open();
    await pageAuditTrail.isLoaded();
    await pageAuditTrail.filterByGlobalId(globalId);
    await pageAuditTrail.checkAction("CREATE");
    await expectNameInResults(pageAuditTrail, sampleName);
  });

  test("As a user, I can find a container's history in the ELN audit trail by its global id", async ({
    clientInventory,
    pageMyRSpace,
    pageAuditTrail,
  }) => {
    const containerName = uniqueName("e2e-audit-container");
    const container = await clientInventory.createContainer({ name: containerName, cType: "LIST" });
    const globalId = container.globalId;

    await pageMyRSpace.open();
    await pageMyRSpace.openAuditTrail();
    await pageAuditTrail.isLoaded();
    await pageAuditTrail.filterByGlobalId(globalId);

    await expectNameInResults(pageAuditTrail, containerName);
  });
});
