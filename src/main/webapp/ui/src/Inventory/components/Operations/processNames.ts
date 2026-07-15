/**
 * Pure helpers for process-name-scoped "remember" defaults.
 *
 * Operations that declare a process-name input (e.g. Derive's "processName") save their remembered
 * template/documentation under a key that combines the operation with the entered process name, so
 * "dna extraction" and "boil" keep independent defaults. Operations without a process name key by
 * the operation alone (e.g. Cryopreserve). Operation keys never contain spaces, so a single space
 * is an unambiguous separator.
 */
import { omit } from "es-toolkit";

export function rememberKey(
  operationKey: string,
  processNameFrom: string | undefined,
  values: Record<string, unknown>,
): string {
  if (!processNameFrom) return operationKey;
  const raw = values[processNameFrom];
  const name = typeof raw === "string" ? raw.trim() : "";
  return name === "" ? operationKey : `${operationKey} ${name}`;
}

/** Adds a trimmed, non-empty process name to the operation's saved list (deduped). */
export function addProcessName(list: Array<string>, name: string): Array<string> {
  const trimmed = name.trim();
  if (trimmed === "" || list.includes(trimmed)) return list;
  return [...list, trimmed];
}

/**
 * The remembered default process name per operation after Perform. When "remember" is ticked and a
 * non-blank name was entered, store it as this operation's default so future runs pre-fill it;
 * otherwise drop any previous default, so unticking (or clearing the name) is itself remembered.
 * Keyed by operation (Cryopreserve has no process name and contributes nothing).
 */
export function processNameDefaultAfterPerform(
  current: Record<string, string>,
  operationKey: string,
  name: string,
  remember: boolean,
): Record<string, string> {
  const trimmed = name.trim();
  if (!remember || trimmed === "") return omit(current, [operationKey]);
  return { ...current, [operationKey]: trimmed };
}

/**
 * Options to show in the process-name autocomplete for the current input. Leading whitespace is
 * ignored, so an empty or whitespace-only input shows every saved name; otherwise the list is a
 * case-insensitive prefix match. When nothing matches the user is free-typing a new name.
 */
export function filterProcessNames(options: Array<string>, input: string): Array<string> {
  const query = input.replace(/^\s+/, "");
  if (query === "") return options;
  const lower = query.toLowerCase();
  return options.filter((option) => option.toLowerCase().startsWith(lower));
}
