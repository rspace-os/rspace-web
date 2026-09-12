/**
 * Every i18n key the shipped operations config names must exist in the en-US catalog.
 *
 * This is the one gap the config-driven design leaves open. `resolveLabelFrom` (types.ts) casts `t`
 * to `(key, params) => string` precisely so a key the config supplies can be passed to a typed
 * i18next, which means TypeScript checks nothing about these keys and neither did any test: adding
 * an operation with an untranslated `labelKey` compiled, passed the whole suite, and rendered the
 * raw string "operations.foo.label" in the picker.
 *
 * The feature's premise is that a new operation needs a config entry and a catalog entry and no
 * frontend change. That premise is only true if something checks the second half, so this is it
 * (parallel review, A5).
 */
import { describe, expect, test } from "vitest";
import { createEnglishI18n } from "@/__tests__/realI18n";
import { rawConfig } from "./testOperations";

/** The config properties whose value is an i18n key, by suffix rather than by an enumerated list. */
const KEY_SUFFIX = /Key$/;

/**
 * `*Key` properties that name something other than a catalog entry. `iconKey` is a FontAwesome icon
 * name ("eye-dropper"); it is checked by the schema's icon registry, not by the catalog.
 */
const NOT_I18N = new Set(["iconKey"]);

/** Every `*Key` value anywhere in the config, with the path that named it for a readable failure. */
function i18nKeysIn(node: unknown, path: string): Array<{ path: string; key: string }> {
  if (Array.isArray(node)) {
    return node.flatMap((item, index) => i18nKeysIn(item, `${path}[${index}]`));
  }
  if (node === null || typeof node !== "object") return [];
  return Object.entries(node as Record<string, unknown>).flatMap(([property, value]) => {
    const here = path ? `${path}.${property}` : property;
    if (KEY_SUFFIX.test(property) && !NOT_I18N.has(property) && typeof value === "string") {
      return [{ path: here, key: value }];
    }
    return i18nKeysIn(value, here);
  });
}

describe("operations_config.json i18n keys", () => {
  const i18n = createEnglishI18n();
  const keys = i18nKeysIn(rawConfig, "");

  test("the config actually declares i18n keys, so an empty sweep cannot pass vacuously", () => {
    expect(keys.length).toBeGreaterThan(20);
  });

  // Cast for the same reason resolveLabelFrom (types.ts) does: these keys come from the config at
  // runtime, so i18next's typed key union cannot describe them. That cast is exactly what this test
  // exists to compensate for.
  const translate = i18n.t as unknown as (key: string) => string;

  test.each(keys)("$path resolves to real English, not the key itself ($key)", ({ key }) => {
    expect(i18n.exists(`inventory:${key}` as never)).toBe(true);
    // `exists` alone would accept an empty string, which renders as a blank label rather than an
    // obviously-broken one; the catalog is configured with returnEmptyString: false.
    expect(translate(`inventory:${key}`)).not.toBe(key);
  });
});
