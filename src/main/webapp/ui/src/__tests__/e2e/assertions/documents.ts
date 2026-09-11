import { expect } from "@playwright/test";
import { ApiError } from "@/__tests__/e2e/api/clients/BaseApiClient";
import type { DocumentsClient } from "@/__tests__/e2e/api/clients/DocumentsClient";

/** Missing and forbidden both conceal a document; authentication and server failures must fail the test. */
export async function expectDocumentUnavailable(client: DocumentsClient, id: number): Promise<void> {
  try {
    await client.getById(id);
  } catch (error) {
    expect(error, `Reading document ${id} must fail with an API response`).toBeInstanceOf(ApiError);
    if (!(error instanceof ApiError)) throw error;
    expect([403, 404], `Document ${id} must be forbidden or missing: ${error.message}`).toContain(error.status);
    return;
  }
  throw new Error(`Document ${id} is still readable.`);
}
