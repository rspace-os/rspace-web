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
 * an entry). This map is the grep-able mirror of the backend's UI_JSON_SETTINGS_KEYS allowlist
 * (UserManagerImpl): every entry here must appear there too, or writes of that key are refused.
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

/**
 * `operation.key` is a plain string, not a literal union, so a key the map does not list derives
 * the same name rather than throwing. The backend's allowlist then refuses it, which is the right
 * answer for an operation that does not exist.
 */
export function processValuesPreferenceFor(operationKey: string): symbol {
  return (
    PROCESS_VALUES_PREFERENCE_BY_OPERATION_KEY[operationKey] ??
    Symbol.for(`INVENTORY_OPERATION_PROCESS_VALUES_${operationKey.toUpperCase()}`)
  );
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
