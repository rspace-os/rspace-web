import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { justFilenameExtension } from "../../files";

describe("justFilenameExtension", () => {
  it('justFilenameExtension("testing.txt") === "txt"', () => {
    expect(justFilenameExtension("testing.txt")).toBe("txt");
  });

  it('justFilenameExtension("testing.md") === "md"', () => {
    expect(justFilenameExtension("testing.md")).toBe("md");
  });

  /*
   * Beware: it's just a dumb regex
   */
  it('justFilenameExtension("testing") === "testing"', () => {
    expect(justFilenameExtension("testing")).toBe("testing");
  });
});


