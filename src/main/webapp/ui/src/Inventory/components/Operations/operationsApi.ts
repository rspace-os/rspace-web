import ApiService from "@/common/InvApiService";
import type { OperationRequest } from "./types";

/** Minimal view of the created sample returned by the operations endpoint. */
export type OperationResult = { id: number; globalId: string; name: string };

/**
 * POST a configured operation to the thin backend endpoint. The /api/inventory/v1/ prefix is
 * already applied by InvApiService, so the resource is just "operations".
 */
export async function performOperation(request: OperationRequest): Promise<OperationResult> {
  const { data } = await ApiService.post<OperationResult>("operations", request);
  return data;
}

/**
 * Whether a sample name is free for the current user, used to de-duplicate the derived sample name
 * with a numeric suffix (adr/0004). Uses the purpose-built, exact, own-scoped endpoint
 * `samples/validateNameForNewSample` rather than the Inventory full-text search: that search is
 * tokenised Lucene, so a multi-word name plus a wildcard matches no single token (and a bare query
 * matches across every readable record's name/tags/description), neither of which is an exact
 * name-existence check. Inventory names are not uniqueness-constrained, so this is a usability
 * nicety: a blank name needs no check, and a failed check degrades to "available" (the name is used
 * as-is) rather than blocking the wizard.
 */
export async function sampleNameAvailable(name: string): Promise<boolean> {
  const trimmed = name.trim();
  if (trimmed === "") return true;
  try {
    const { data } = await ApiService.query<{ valid?: boolean }>(
      "samples/validateNameForNewSample",
      new URLSearchParams({ name: trimmed }),
    );
    return data.valid !== false;
  } catch {
    return true;
  }
}
