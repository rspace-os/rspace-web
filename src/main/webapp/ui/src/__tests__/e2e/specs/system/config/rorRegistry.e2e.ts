import { expect } from "@playwright/test";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { ROR_TEST_ORGANISATION } from "@/__tests__/e2e/fixtures/flows/environment/rorRegistry";
import { tags } from "@/__tests__/e2e/tags";

const { id: ROR_ID, name: ORGANISATION } = ROR_TEST_ORGANISATION;

test.describe("ROR Registry", { tag: tags.SYSTEM }, () => {
  test("As a sysadmin, I can look up my institution in the ROR registry, link it to this RSpace instance, and unlink it", async ({
    flowRorRegistry,
  }) => {
    const { config, client } = flowRorRegistry;

    const ror = await config.openRorRegistry();
    await ror.search(ROR_ID);
    await expect(ror.detail(ORGANISATION)).toBeVisible();
    await expect(ror.detail("Bogotá, Colombia")).toBeVisible();

    await ror.link();
    await expect(ror.linkedMessage).toBeVisible();
    await expect.poll(() => client.linkedRorId()).toBe(ROR_ID);

    const reopened = await config.openRorRegistry();
    await expect(reopened.detail(ORGANISATION)).toBeVisible();
    await expect(reopened.linkedMessage).toBeVisible();

    await reopened.unlink();
    await expect(reopened.searchField).toBeVisible();
    await expect.poll(() => client.linkedRorId()).toBe("");
  });
});
