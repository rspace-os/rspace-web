import * as v from "valibot";
import { afterEach, describe, expect, test, vi } from "vitest";
import { createDisplayRule, FieldSnapshotSchema } from "../displayRule";
import { newFieldKey } from "../newFieldKey";
import { validateDisplayRules } from "../validateDisplayRules";

const V4_UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

describe("newFieldKey", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  test("works outside a secure context, where crypto.randomUUID is missing", () => {
    const { getRandomValues } = globalThis.crypto;
    vi.stubGlobal("crypto", { getRandomValues: getRandomValues.bind(globalThis.crypto) });
    expect(globalThis.crypto.randomUUID).toBeUndefined();

    const key = newFieldKey();

    expect(key).toMatch(V4_UUID);
    const source = v.parse(FieldSnapshotSchema, {
      key,
      canBeSource: true,
      rule: null,
      allowedOptions: ["Yes"],
      selectedOptions: [],
    });
    const dependent = v.parse(FieldSnapshotSchema, {
      key: newFieldKey(),
      canBeSource: false,
      rule: createDisplayRule(key, "IS_ANY_OF", ["Yes"]),
      allowedOptions: [],
      selectedOptions: [],
    });
    expect(validateDisplayRules([source, dependent])).toEqual([]);
  });
});
