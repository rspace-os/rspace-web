/**
 * Operation keys never contain spaces, so combining `${operation.key} ${name}` as a single
 * space-separated string is an unambiguous key.
 */
import { createFilterOptions } from "@mui/material/Autocomplete";
import { omit } from "es-toolkit";
import { PREFERENCES } from "@/hooks/api/useUiPreference";
import { type InventoryOperation, resolveProcessName } from "./operationsConfig";

export function rememberKey(operation: InventoryOperation, values: Record<string, unknown>): string {
  if (!operation.effect.processNameFrom) return operation.key;
  const name = resolveProcessName(operation, values);
  return name === "" ? operation.key : `${operation.key} ${name}`;
}

/**
 * The grep-able mirror of the backend's UI_JSON_SETTINGS_KEYS allowlist (UserManagerImpl): every
 * entry here must appear there too, or writes of that key are refused.
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

export function processNameDefaultAfterPerform(
  current: Record<string, string>,
  operationKey: string,
  name: string,
): Record<string, string> {
  const trimmed = name.trim();
  if (trimmed === "") return omit(current, [operationKey]);
  return { ...current, [operationKey]: trimmed };
}

const startsWithFilter = createFilterOptions<string>({ matchFrom: "start", trim: true });

export function filterProcessNames(options: Array<string>, input: string): Array<string> {
  return startsWithFilter(options, { inputValue: input, getOptionLabel: (option) => option });
}
