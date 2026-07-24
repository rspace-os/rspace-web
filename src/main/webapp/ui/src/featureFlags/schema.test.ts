import * as v from "valibot";
import { describe, expect, test } from "vitest";
import { FEATURE_FLAGS } from "./generatedFeatureFlags";
import { FeatureFlagResponseSchema, featureFlagNames } from "./schema";

const disabledResponse = {
  flags: Object.fromEntries(
    featureFlagNames.map((flagName) => [
      flagName,
      {
        value: false,
        baselineValue: false,
        source: "DEFAULT",
        canOverride: false,
      },
    ]),
  ),
};

const validEntry = {
  value: true,
  baselineValue: true,
  source: "DATABASE",
  canOverride: true,
};

describe("FeatureFlagResponseSchema", () => {
  test("parses valid flags", () => {
    expect(
      v.parse(FeatureFlagResponseSchema, {
        flags: {
          [FEATURE_FLAGS.bookingEnabled]: validEntry,
        },
      }),
    ).toMatchObject({
      flags: {
        [FEATURE_FLAGS.bookingEnabled]: validEntry,
      },
    });
  });

  test.each([
    null,
    { flags: null },
    {
      flags: {
        [FEATURE_FLAGS.bookingEnabled]: {
          value: true,
        },
      },
    },
  ])("disables flags for an invalid response", (response) => {
    expect(v.parse(FeatureFlagResponseSchema, response)).toEqual(disabledResponse);
  });
});
