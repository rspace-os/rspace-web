export function isRuntimeFieldInNamespace(name: string, namespace: string): boolean {
  return name.startsWith(`${namespace}.`);
}
