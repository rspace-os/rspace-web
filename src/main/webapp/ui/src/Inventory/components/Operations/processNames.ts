/**
 * Operation keys never contain spaces, so combining `${operation.key} ${name}` as a single
 * space-separated string is an unambiguous key.
 */
import { createFilterOptions } from "@mui/material/Autocomplete";
import { omit } from "es-toolkit";
import { type InventoryOperation, resolveProcessName } from "./operations";

export function rememberKey(operation: InventoryOperation, values: Record<string, unknown>): string {
  if (!operation.effect.processNameFrom) return operation.key;
  const name = resolveProcessName(operation, values);
  return name === "" ? operation.key : `${operation.key} ${name}`;
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
