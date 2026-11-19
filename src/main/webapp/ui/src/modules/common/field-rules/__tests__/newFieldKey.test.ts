import * as v from "valibot";
import { afterEach, describe, expect, test, vi } from "vitest";
import { createDisplayRule, FieldSnapshotSchema } from "../displayRule";
import { validateDisplayRules } from "../validateDisplayRules";

const V4_UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

describe("newFieldKey", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  test("works outside a secure context, where crypto.randomUUID is missing", async () => {
    const getRandomValues = vi.fn(globalThis.crypto.getRandomValues.bind(globalThis.crypto));
    vi.stubGlobal("crypto", { getRandomValues });
    expect(globalThis.crypto.randomUUID).toBeUndefined();
    // uuid 14 reads the global crypto on each call, but load it afresh after
    // the stub so that a version caching crypto.randomUUID at import time
    // could not hide the fallback.
    vi.resetModules();
    const { newFieldKey } = await import("../newFieldKey");

    const key = newFieldKey();

    expect(getRandomValues).toHaveBeenCalled();
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
