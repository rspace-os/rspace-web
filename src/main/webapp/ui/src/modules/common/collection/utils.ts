import type { SelectOption } from "./config";

export function isRuntimeFieldInNamespace(name: string, namespace: string): boolean {
  return name.startsWith(`${namespace}.`);
}

export function selectOptionText(option: SelectOption): string {
  if (typeof option === "string") return option;
  return "textValue" in option ? option.textValue : option.label;
}
