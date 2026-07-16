import { describe, expect, it } from "vitest";
import { derivedSampleName, firstAvailableName } from "../sampleNaming";

describe("derivedSampleName", () => {
  it("joins the origin sample name and the process name with a space", () => {
    expect(derivedSampleName("Blood", "dna extraction")).toBe("Blood dna extraction");
  });

  it("trims and drops an empty part rather than leaving a dangling space", () => {
    expect(derivedSampleName("  Blood  ", "")).toBe("Blood");
    expect(derivedSampleName("Blood", "   ")).toBe("Blood");
  });
});

describe("firstAvailableName", () => {
  // isAvailable stub: a name is available unless it is in the "taken" set.
  const availabilityOf =
    (taken: ReadonlyArray<string>) =>
    (name: string): Promise<boolean> =>
      Promise.resolve(!taken.includes(name));

  it("returns the base name when it is available", async () => {
    expect(await firstAvailableName("Blood dna", availabilityOf([]))).toBe("Blood dna");
    expect(await firstAvailableName("Blood dna", availabilityOf(["Other"]))).toBe("Blood dna");
  });

  it("appends _1 when the base is taken", async () => {
    expect(await firstAvailableName("Blood dna", availabilityOf(["Blood dna"]))).toBe("Blood dna_1");
  });

  it("finds the first free suffix, skipping taken ones", async () => {
    expect(await firstAvailableName("Blood dna", availabilityOf(["Blood dna", "Blood dna_1", "Blood dna_2"]))).toBe(
      "Blood dna_3",
    );
  });

  it("fills a gap rather than always taking the highest+1", async () => {
    expect(await firstAvailableName("Blood dna", availabilityOf(["Blood dna", "Blood dna_2"]))).toBe("Blood dna_1");
  });
});
