import { useTranslation } from "react-i18next";
import ApiService from "@/common/InvApiService";
import ContainerModel, { type ContainerAttrs } from "@/stores/models/ContainerModel";
import MemoisedFactory from "@/stores/models/Factory/MemoisedFactory";
import { getApiErrorDetails } from "@/util/error";
import type { FacadeRequest } from "./buildOperationRequest";
import type { InventoryOperation } from "./operations";
import type { CreatedSubSample, PlacementRecord } from "./placement";
import { type ResolveLabel, resolveLabelFrom } from "./types";

export type OperationResult = { id: number; globalId: string; name: string; subSamples: Array<CreatedSubSample> };

/**
 * The /api/inventory/v1/ prefix is already applied by InvApiService, so the resource is just
 * "operations/<key>". Resolves to null for a terminal operation (Destroy), which creates no
 * sample.
 */
export async function performOperation(
  operation: InventoryOperation,
  body: FacadeRequest,
): Promise<OperationResult | null> {
  const { data } = await ApiService.post<{ sample: OperationResult | null } | null>(
    `operations/${operation.key}`,
    body,
  );
  return data?.sample ?? null;
}

/** A field-scoped error's leading path, when it is a bare word: an operation's own field names. */
const BARE_KEY_PREFIX = /^([A-Za-z_]\w*):\s*/;

/**
 * An error against one of the operation's own fields comes back keyed by that bare field name
 * ("sampleName: Required by this operation."), so the key is swapped for the input's label as the
 * wizard shows it. Anything else (an origin error under "origins[0].amountTaken", a 409, a bare
 * message) is left to getApiErrorDetails. Returns one entry per reason.
 */
function describeOperationError(
  error: unknown,
  operation: InventoryOperation,
  resolveLabel: ResolveLabel,
  fallback: string,
): Array<string> {
  // The "which origin" marker is worded from the catalog: welding " (origin 3)" onto a reason the
  // server already localized shipped half an English sentence to a non-English user.
  return getApiErrorDetails(error, fallback, (reason, index) =>
    resolveLabel("operations.wizard.originIndex", { reason, index }),
  ).map((detail) => {
    const match = BARE_KEY_PREFIX.exec(detail);
    const input = match ? operation.inputs.find((i) => i.key === match[1]) : undefined;
    // The "<label>: <reason>" join goes through the catalog: not every locale separates with a
    // colon-space.
    if (!input || !match) return detail;
    return resolveLabel("operations.wizard.fieldReason", {
      label: resolveLabel(input.labelKey),
      reason: detail.slice(match[0].length),
    });
  });
}

/** Binds describeOperationError to the inventory catalog, with its failure message as the fallback. */
export function useDescribeOperationError(): (error: unknown, operation: InventoryOperation) => Array<string> {
  const { t } = useTranslation("inventory");
  const resolveLabel = resolveLabelFrom(t);
  return (error, operation) => describeOperationError(error, operation, resolveLabel, t("operations.wizard.failed"));
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

/** The bulk endpoint refused at least one move; with rollbackOnError nothing was moved. */
export class PlacementRefused extends Error {
  constructor(readonly reasons: Array<string>) {
    super(reasons.join("\n"));
  }
}

/** Moves the new subsamples with the bulk endpoint, all or nothing. */
export async function placeSubSamples(records: ReadonlyArray<PlacementRecord>): Promise<void> {
  const { data } = await ApiService.bulk<{
    errorCount: number;
    results: Array<{ error?: { errors: Array<string> } | null }>;
  }>(records, "MOVE", true);
  if (data.errorCount > 0) throw new PlacementRefused(data.results.flatMap((r) => r.error?.errors ?? []));
}

/** A container with its locations, as the placement step needs it. */
export async function fetchContainer(id: number): Promise<ContainerModel> {
  const { data } = await ApiService.query<ContainerAttrs>(
    `containers/${id}`,
    new URLSearchParams({ includeContent: "true" }),
  );
  return new ContainerModel(new MemoisedFactory(), data);
}
