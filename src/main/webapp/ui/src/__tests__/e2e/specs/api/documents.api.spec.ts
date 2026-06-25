import type { ApiDocument } from "@/__tests__/e2e/api/models/document";
import { expect, tags, test } from "@/__tests__/e2e/fixtures";

test.describe(`Documents API ${tags.SMOKE}`, () => {
  test("As an API client, I can create a document and retrieve it by id", async ({ clientDocuments }) => {
    const name = `e2e-doc-${Date.now()}`;
    let created!: ApiDocument;

    await test.step("Given I have a unique document name", async () => {
      expect(name).toMatch(/^e2e-doc-/);
    });

    await test.step("When I create a document via the API", async () => {
      created = await clientDocuments.create({ name });
    });

    await test.step("Then the response contains the new document's id and name", () => {
      expect(created.id).toBeGreaterThan(0);
      expect(created.name).toBe(name);
    });

    await test.step("And I can retrieve the same document by id", async () => {
      const fetched = await clientDocuments.getById(created.id);
      expect(fetched.id).toBe(created.id);
      expect(fetched.name).toBe(name);
    });
  });
});
