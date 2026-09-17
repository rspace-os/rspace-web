import type { InventoryKey } from "./operationsConfig";

/**
 * Types for the Inventory operation wizard. The wizard collects the operation's values, then
 * buildFacadeRequest turns them into the body its endpoint takes. Shapes mirror the backend
 * ApiInventoryOperationRequests so the JSON maps straight through.
 */

export type OperationQuantity = { numericValue: number; unitId: number };

/**
 * How the amount taken is decided across a multi-origin operation's origins. "same" is the single
 * shared amount (the default, and the only mode single-origin operations use); "all" empties every
 * origin to zero; "perSubsample" takes a separate amount from each origin.
 */
export type AmountMode = "same" | "all" | "perSubsample";

/** Per-origin amount taken in "perSubsample" mode, keyed by the origin subsample's global id. */
export type PerSubsampleAmounts = Record<string, OperationQuantity>;

/**
 * Sentinel unitId meaning "no unit chosen yet". Fresh amounts are prefilled with the origin
 * subsample's own unit; this marker appears when a picked template changes the measurement
 * category, which clears the created amount's unit. An amount carrying it is incomplete and blocks
 * the step (see detailsValid). Real unit ids from the store are positive, so 0 is safe as the
 * unset marker.
 */
export const UNSET_UNIT = 0;

/** A single collected input value. Quantity/temperature inputs carry their unit; text is a string. */
export type OperationInputValue = string | number | OperationQuantity;
export type OperationInputs = Record<string, OperationInputValue>;

/**
 * The definition key that produced a field. The server stamps it when it builds an operation's
 * fields, and ignores it in every request since it's read-only on the wire - which is what lets a
 * later operation identify a field an earlier one generated regardless of the locale its name was
 * rendered in. A field absorbed into an inherited template field carries no key, since
 * InventoryEntityField has no such column.
 */
export type OperationFieldKey = { operationFieldKey: string };

export type OperationLinkField = OperationFieldKey & {
  name: string;
  type: "link";
  newFieldRequest: true;
  link: { relationType: string; targetGlobalId: string; versionPin: number | null };
};

export type OperationTextFieldValue = OperationFieldKey & {
  name: string;
  // "number" is available for origin custom fields (effect.originFields). Inventory subsample
  // fields have no native date type, so a date (Destroy's disposed) is a text field holding an
  // ISO date. See ApiExtraField.
  type: "text" | "number";
  newFieldRequest: true;
  content: string;
};

export type OperationExtraField = OperationLinkField | OperationTextFieldValue;

/** An origin subsample the wizard was launched on. */
export type OperationOrigin = {
  id: number;
  globalId: string;
  /** The subsample's name, interpolated into a per-origin link field name (e.g. Pool's, so the
   * several links back to the pooled subsamples get distinct names - a record cannot hold two fields
   * with the same name). Available to link `fieldNameKey`s as `{originName}`. */
  name: string;
  quantity: OperationQuantity | null;
};

/** i18next-style resolver, injected so the builder stays pure and unit-testable. */
export type ResolveLabel = (key: InventoryKey, params?: Record<string, unknown>) => string;

/**
 * Adapts i18next's `t` to the two-argument resolver the wizard's shared components take. `t` is
 * overloaded on its second parameter in ways no plain function type can express (one overload
 * requires a defaultValue), so no signature TypeScript will accept exists and the cast stays. The
 * KEYS are checked, here and at every call site, because ResolveLabel takes an InventoryKey.
 */
export function resolveLabelFrom(t: unknown): ResolveLabel {
  return t as ResolveLabel;
}
