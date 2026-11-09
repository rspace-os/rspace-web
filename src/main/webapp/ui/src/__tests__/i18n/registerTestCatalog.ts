import i18n from "@/modules/common/i18n";

/** Adds an English test-only catalog. Translators never receive it; call it from the fixture, story or spec that uses it. */
export function registerTestCatalog(namespace: string, resources: Record<string, unknown>): void {
  i18n.addResourceBundle("en-US", namespace, resources, true, true);
}
