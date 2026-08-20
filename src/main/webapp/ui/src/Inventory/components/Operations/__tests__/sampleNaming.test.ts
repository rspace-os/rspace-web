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

  it("does not re-append the process name when it is already the tail of the origin name", () => {
    // otherwise repeated runs grow the name: "SUB PROC" -> "SUB PROC PROC" -> ...
    expect(derivedSampleName("SUB PROC", "PROC")).toBe("SUB PROC");
    expect(derivedSampleName("Blood dna extraction", "dna extraction")).toBe("Blood dna extraction");
  });

  it("ignores a _N dedup suffix and a .N subsample serial when checking for the process at the end", () => {
    expect(derivedSampleName("SUB PROC_1", "PROC")).toBe("SUB PROC");
    expect(derivedSampleName("SUB PROC.01", "PROC")).toBe("SUB PROC");
    expect(derivedSampleName("SUB PROC_2.01", "PROC")).toBe("SUB PROC");
  });

  it("still appends the process when it is not the tail of the origin name", () => {
    expect(derivedSampleName("SUB", "PROC")).toBe("SUB PROC");
    // must be a trailing word, not merely a substring at the end
    expect(derivedSampleName("SUBPROC", "PROC")).toBe("SUBPROC PROC");
  });

  it("matches the process at the end case-insensitively, preserving the origin's original casing", () => {
    expect(derivedSampleName("SUB proc", "PROC")).toBe("SUB proc");
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
