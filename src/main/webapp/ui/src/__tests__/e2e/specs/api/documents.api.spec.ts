import { expect } from "@playwright/test";
import type { ApiDocument } from "@/__tests__/e2e/api/models/document";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { uniqueName } from "@/__tests__/e2e/testData";

test.describe("Documents API", () => {
  let created: ApiDocument | undefined;

  test.afterEach(async ({ clientDocuments }) => {
    if (created?.id) await clientDocuments.deleteById(created.id);
    created = undefined;
  });

  test("As an API client, I can create a document and retrieve it by id", async ({ clientDocuments }) => {
    const name = uniqueName("e2e-doc");

    const document = await clientDocuments.create({ name });
    created = document;
    expect(document.id).toBeGreaterThan(0);
    expect(document.name).toBe(name);

    const fetched = await clientDocuments.getById(document.id);
    expect(fetched.id).toBe(document.id);
    expect(fetched.name).toBe(name);
  });
});
