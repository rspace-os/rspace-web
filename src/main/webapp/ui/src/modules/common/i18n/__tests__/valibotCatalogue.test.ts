import { existsSync, readdirSync, readFileSync } from "node:fs";
import { createRequire } from "node:module";
import path from "node:path";
import * as v from "valibot";
import { describe, expect, test } from "vitest";
import enUS from "../locales/en-US/valibot.json";

/*
 * Fails when the `valibot` catalogue drifts from Valibot or @valibot/i18n,
 * typically after one of them is upgraded. To fix a failure:
 *   1. Add or remove the key in locales/en-US/valibot.json, with the English
 *      text this test expects, and in the `actions` map in ../valibot.ts.
 *   2. Run `pnpm run i18n:valibot` to refresh the other locales.
 */

const localesDir = path.resolve(import.meta.dirname, "../locales");
const packageDir = path.dirname(createRequire(import.meta.url).resolve("@valibot/i18n"));

type Action = { expects?: string | null; "~run": (...args: never[]) => unknown };

function create(key: string): Action {
  if (key === "schema") return v.string();
  const factory = (v as unknown as Record<string, (...args: unknown[]) => Action>)[key];
  try {
    return factory();
  } catch {
    // Some actions need a list, e.g. v.values([...]).
    return factory([]);
  }
}

/**
 * Valibot's built-in English message for a key, in ICU: `Invalid <label>:`
 * then the expected value, if the action has one, and the received value.
 * The label is the literal that the action passes to `_addIssue`.
 */
function builtInMessage(key: string): string {
  const action = create(key);
  const label = /_addIssue\(this, (?:"([^"]+)"|[^,]*?\?\? "([^"]+)")/.exec(action["~run"].toString());
  if (!label)
    throw new Error(`Cannot find the issue label of ${key}; Valibot's internals changed, so update this test.`);
  const expected = action.expects == null ? "R" : "Expected {expected} but r";
  return `Invalid ${label[1] ?? label[2]}: ${expected}eceived {received}`;
}

describe("the valibot catalogue", () => {
  test("has a key for every message that @valibot/i18n translates, and no others", () => {
    const translated = new Set(
      readdirSync(packageDir, { withFileTypes: true })
        .filter((entry) => entry.isDirectory())
        .flatMap((language) => readdirSync(path.join(packageDir, language.name)))
        .filter((file) => file.endsWith(".mjs") && file !== "index.mjs")
        .map((file) => file.slice(0, -".mjs".length)),
    );
    expect(Object.keys(enUS).sort()).toEqual([...translated].sort());
  });

  test("repeats Valibot's built-in English messages in en-US", () => {
    expect(enUS).toEqual(Object.fromEntries(Object.keys(enUS).map((key) => [key, builtInMessage(key)])));
  });

  test("has the en-US keys in every other locale", () => {
    for (const locale of readdirSync(localesDir)) {
      const file = path.join(localesDir, locale, "valibot.json");
      if (locale === "en-US" || !existsSync(file)) continue;
      expect(Object.keys(JSON.parse(readFileSync(file, "utf8"))).sort(), locale).toEqual(Object.keys(enUS).sort());
    }
  });
});
