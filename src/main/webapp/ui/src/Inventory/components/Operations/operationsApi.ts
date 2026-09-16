import ApiService from "@/common/InvApiService";
import { getApiErrorDetail } from "@/util/error";
import { type InventoryOperation, parseOperationsConfig } from "./operationsConfig";
import type { OperationInputsRequest, ResolveLabel } from "./types";

/** Minimal view of the created sample returned by the operations endpoint. */
export type OperationResult = { id: number; globalId: string; name: string };

/** Throws on a fetch failure or an invalid config, so callers can show one load-failed state for both. */
export async function fetchOperationsConfig(): Promise<Array<InventoryOperation>> {
  const { data } = await ApiService.get<unknown>("operations", "config");
  return parseOperationsConfig(data);
}

/**
 * POST a configured operation to the thin backend endpoint. The /api/inventory/v1/ prefix is
 * already applied by InvApiService, so the resource is just "operations". Resolves to null for a
 * terminal operation (noOutput, e.g. Destroy), which creates no sample and so returns an empty body.
 */
export async function performOperation(request: OperationInputsRequest): Promise<OperationResult | null> {
  const { data } = await ApiService.post<OperationResult | null>("operations", request);
  return data || null;
}

/** A field-scoped error's leading path, when it is a bare word: the inputs shape names an input by its key alone. */
const BARE_KEY_PREFIX = /^([A-Za-z_]\w*):\s*/;

/**
 * The reason a Perform was rejected, worded for the wizard. An error against a declared input comes
 * back keyed by the bare input key ("sampleName: Required by this operation.", never
 * "inputs.sampleName": Spring cannot address a map entry by a dotted path, and the bare key is what a
 * typed client would have sent), so that key is swapped for the input's label as the wizard shows it.
 * Anything else (an origin error under "origins[0].amountTaken", a 409, a bare message) is left to
 * getApiErrorDetail, which strips a dotted path and falls back to the response message.
 */
export function describeOperationError(
  error: unknown,
  operation: InventoryOperation,
  resolveLabel: ResolveLabel,
  fallback: string,
): string {
  // The "which origin" marker is worded from the catalog: welding " (origin 3)" onto a reason the
  // server already localized shipped half an English sentence to a non-English user.
  const detail = getApiErrorDetail(error, fallback, (reason, index) =>
    resolveLabel("operations.wizard.originIndex", { reason, index }),
  );
  const match = BARE_KEY_PREFIX.exec(detail);
  const input = match ? operation.inputs.find((i) => i.key === match[1]) : undefined;
  // The "<label>: <reason>" join goes through the catalog: not every locale separates with a
  // colon-space.
  if (input && match) {
    return resolveLabel("operations.wizard.fieldReason", {
      label: resolveLabel(input.labelKey),
      reason: detail.slice(match[0].length),
    });
  }
  return detail;
}

/**
 * Whether a sample name is free for the current user, used to de-duplicate the derived sample name
 * with a numeric suffix. Uses the purpose-built, exact, own-scoped endpoint
 * `samples/validateNameForNewSample` rather than the Inventory full-text search, which is tokenised
 * Lucene and can't do an exact name-existence check. Inventory names are not uniqueness-constrained,
 * so this is only a usability nicety: a failed check degrades to "available" rather than blocking
 * the wizard.
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
