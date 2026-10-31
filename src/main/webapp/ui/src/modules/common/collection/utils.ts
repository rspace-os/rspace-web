import type { SelectOption } from "./config";

export function isRuntimeFieldInNamespace(name: string, namespace: string): boolean {
  return name.startsWith(`${namespace}.`);
}

export function isIdLikeField(name: string): boolean {
  return /Ids?$/.test(name);
}

export function selectOptionText(option: SelectOption): string {
  if (typeof option === "string") return option;
  return "textValue" in option ? option.textValue : option.label;
}
