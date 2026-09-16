/**
 * Pure helpers for process-name-scoped "remember" defaults. Operation keys never contain spaces,
 * so combining `${operation.key} ${name}` as a single space-separated string is an unambiguous key.
 */
import { omit } from "es-toolkit";
import { PREFERENCES } from "@/hooks/api/useUiPreference";
import { type InventoryOperation, resolveProcessName } from "./operationsConfig";

export function rememberKey(operation: InventoryOperation, values: Record<string, unknown>): string {
  if (!operation.effect.processNameFrom) return operation.key;
  const name = resolveProcessName(operation, values);
  return name === "" ? operation.key : `${operation.key} ${name}`;
}

/**
 * Each operation type gets its own "remember" bundle collection, so a heavy user's saved entries
 * for one operation don't grow the others past the server's per-key size cap (nothing ever removes
 * an entry). `operation.key` is a runtime string from the server config, not a TypeScript literal
 * union, so an operation this map doesn't recognise falls back to the legacy shared collection
 * rather than throwing.
 */
const PROCESS_VALUES_PREFERENCE_BY_OPERATION_KEY: Readonly<Record<string, symbol>> = {
  aliquot: PREFERENCES.INVENTORY_OPERATION_PROCESS_VALUES_ALIQUOT,
  passage: PREFERENCES.INVENTORY_OPERATION_PROCESS_VALUES_PASSAGE,
  pool: PREFERENCES.INVENTORY_OPERATION_PROCESS_VALUES_POOL,
  derive: PREFERENCES.INVENTORY_OPERATION_PROCESS_VALUES_DERIVE,
  cryopreserve: PREFERENCES.INVENTORY_OPERATION_PROCESS_VALUES_CRYOPRESERVE,
  revive: PREFERENCES.INVENTORY_OPERATION_PROCESS_VALUES_REVIVE,
  destroy: PREFERENCES.INVENTORY_OPERATION_PROCESS_VALUES_DESTROY,
};

export function processValuesPreferenceFor(operationKey: string): symbol {
  return PROCESS_VALUES_PREFERENCE_BY_OPERATION_KEY[operationKey] ?? PREFERENCES.INVENTORY_OPERATION_PROCESS_VALUES;
}

export function addProcessName(list: Array<string>, name: string): Array<string> {
  const trimmed = name.trim();
  if (trimmed === "" || list.includes(trimmed)) return list;
  return [...list, trimmed];
}

/**
 * A blank name is not producible through the wizard (the details step requires one), but is
 * defensively dropped here rather than stored as an empty default.
 */
export function processNameDefaultAfterPerform(
  current: Record<string, string>,
  operationKey: string,
  name: string,
): Record<string, string> {
  const trimmed = name.trim();
  if (trimmed === "") return omit(current, [operationKey]);
  return { ...current, [operationKey]: trimmed };
}

export function filterProcessNames(options: Array<string>, input: string): Array<string> {
  const query = input.replace(/^\s+/, "");
  if (query === "") return options;
  const lower = query.toLowerCase();
  return options.filter((option) => option.toLowerCase().startsWith(lower));
}
