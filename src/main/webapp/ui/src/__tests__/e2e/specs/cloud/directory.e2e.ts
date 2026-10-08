import { expect } from "@playwright/test";
import { createDynamicUser } from "@/__tests__/e2e/createDynamicUser";
import { test } from "@/__tests__/e2e/fixtures/flows/sessions/cloudSessions";
import { E2E_AFFILIATION, uniqueName } from "@/__tests__/e2e/testData";

test.describe("Community directory", () => {
  test("As a community user, I only see other users once I search, with their affiliation", async ({
    pageDirectory,
    clientSysadmin,
  }) => {
    const other = await createDynamicUser(clientSysadmin, "ROLE_USER", "e2eCloudListed");
    const unknown = uniqueName("e2eNoSuchUser");

    await pageDirectory.open();
    await expect(pageDirectory.searchPrompt).toBeVisible();

    await pageDirectory.search(other.username);
    await expect(pageDirectory.columnHeader("Affiliation")).toBeVisible();
    await expect(pageDirectory.userRow(other.username)).toContainText(E2E_AFFILIATION);

    await pageDirectory.search(unknown);
    await expect(pageDirectory.noResultsMessage(unknown)).toBeVisible();
  });
});
