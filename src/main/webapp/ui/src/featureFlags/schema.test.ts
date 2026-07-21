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
        docs: [{ name: FEATURE_FLAGS.bookingEnabled, ...validEntry }],
        totalDocs: 1,
        limit: 100,
        page: 1,
        pagingCounter: 1,
        totalPages: 1,
        hasPrevPage: false,
        hasNextPage: false,
        prevPage: null,
        nextPage: null,
      }),
    ).toMatchObject({
      flags: {
        [FEATURE_FLAGS.bookingEnabled]: validEntry,
      },
    });
  });

  test.each([
    null,
    { docs: null },
    {
      docs: [{ name: FEATURE_FLAGS.bookingEnabled, value: true }],
      totalDocs: 1,
      limit: 100,
      page: 1,
      pagingCounter: 1,
      totalPages: 1,
      hasPrevPage: false,
      hasNextPage: false,
      prevPage: null,
      nextPage: null,
    },
  ])("disables flags for an invalid response", (response) => {
    expect(v.parse(FeatureFlagResponseSchema, response)).toEqual(disabledResponse);
  });
});
