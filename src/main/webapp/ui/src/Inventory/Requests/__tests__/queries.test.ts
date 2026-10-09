import { describe, expect, test } from "vitest";
import { sampleRequestsQueryKeys } from "../queries";

describe("sampleRequestsQueryKeys.otherActiveForSample", () => {
  test("differs for different excludeRequestId values on the same sample", () => {
    // The bug this guards against: two different requests against the same sample shared one
    // cache entry (keyed on sampleId alone), so opening one request right after another could
    // show the previous request's own otherActive result - including that request wrongly
    // appearing in its own "will be rejected" list.
    expect(sampleRequestsQueryKeys.otherActiveForSample(55, 101)).not.toEqual(
      sampleRequestsQueryKeys.otherActiveForSample(55, 102),
    );
  });

  test("still differs for different sampleId values", () => {
    expect(sampleRequestsQueryKeys.otherActiveForSample(55, 101)).not.toEqual(
      sampleRequestsQueryKeys.otherActiveForSample(56, 101),
    );
  });
});
