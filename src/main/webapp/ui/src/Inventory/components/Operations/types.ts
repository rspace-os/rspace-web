import type { InventoryKey } from "./operationsConfig";

export type OperationQuantity = { numericValue: number; unitId: number };

/**
 * How the amount taken is decided across a multi-origin operation's origins. "same" is the single
 * shared amount (the default, and the only mode single-origin operations use); "all" empties every
 * origin to zero; "perSubsample" takes a separate amount from each origin.
 */
export type AmountMode = "same" | "all" | "perSubsample";

export type PerSubsampleAmounts = Record<string, OperationQuantity>;

/**
 * Sentinel unitId meaning "no unit chosen yet"; an amount carrying it is incomplete and blocks the
 * step. Real unit ids from the store are positive, so 0 is safe as the unset marker.
 */
export const UNSET_UNIT = 0;

export type OperationInputValue = string | number | OperationQuantity;
export type OperationInputs = Record<string, OperationInputValue>;

/**
 * The definition key that produced a field: its stable identity, where `name` is only a localized
 * rendering of that key. Read-only on the wire - the server stamps it and ignores it in requests.
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
  // Inventory subsample fields have no native date type, so a date (Destroy's disposed) is a
  // text field holding an ISO date.
  type: "text" | "number";
  newFieldRequest: true;
  content: string;
};

export type OperationExtraField = OperationLinkField | OperationTextFieldValue;

export type OperationOrigin = {
  id: number;
  globalId: string;
  /** The subsample's name. Available to link `fieldNameKey`s as `{originName}`. */
  name: string;
  quantity: OperationQuantity | null;
};

export type ResolveLabel = (key: InventoryKey, params?: Record<string, unknown>) => string;

/**
 * Adapts i18next's `t` to the two-argument resolver the wizard's shared components take. `t` is
 * overloaded on its second parameter in ways no plain function type can express (one overload
 * requires a defaultValue), so no signature TypeScript will accept exists and the cast stays.
 */
export function resolveLabelFrom(t: unknown): ResolveLabel {
  return t as ResolveLabel;
}
