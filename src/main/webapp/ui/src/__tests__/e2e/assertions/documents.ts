import { expect } from "@playwright/test";
import { ApiError } from "@/__tests__/e2e/api/clients/BaseApiClient";
import type { DocumentsClient } from "@/__tests__/e2e/api/clients/DocumentsClient";

/** Missing and forbidden both conceal a document; authentication and server failures must fail the test. */
export async function expectDocumentUnavailable(client: DocumentsClient, id: number): Promise<void> {
  try {
    await client.getById(id);
  } catch (error) {
    expect(error, `Reading document ${id} must fail with an API response`).toBeInstanceOf(ApiError);
    if (!(error instanceof ApiError)) {
      throw error;
    }
    expect([403, 404], `Document ${id} must be forbidden or missing: ${error.message}`).toContain(error.status);
    return;
  }
  throw new Error(`Document ${id} is still readable.`);
}

/** Read-only users get their API edit refused. Bad keys are 401 too, so the message is what proves it. */
export async function expectDocumentUpdateRefused(client: DocumentsClient, id: number): Promise<void> {
  const [field] = (await client.getById(id)).fields;
  try {
    await client.update(id, { fields: [{ id: field.id, content: "<p>refused edit</p>" }] });
  } catch (error) {
    expect(error, `Editing document ${id} must fail with an API response`).toBeInstanceOf(ApiError);
    if (!(error instanceof ApiError)) {
      throw error;
    }
    expect(error.status, `Editing document ${id} must be refused: ${error.message}`).toBe(401);
    expect(error.message).toContain("Unauthorised attempt by user");
    expect((await client.getById(id)).fields[0].content).toBe(field.content);
    return;
  }
  throw new Error(`Document ${id} accepted an edit from a user without edit permission.`);
}
