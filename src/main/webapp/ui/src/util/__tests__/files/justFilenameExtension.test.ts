/*
 * @vitest-environment jsdom
 */
import { describe, it, test, expect } from "vitest";
import "@testing-library/jest-dom/vitest";
import { justFilenameExtension } from "../../files";

describe("justFilenameExtension", () => {
  test('justFilenameExtension("testing.txt") === "txt"', () => {
    expect(justFilenameExtension("testing.txt")).toBe("txt");
  });

  test('justFilenameExtension("testing.md") === "md"', () => {
    expect(justFilenameExtension("testing.md")).toBe("md");
  });

  /*
   * Beware: it's just a dumb regex
   */
  test('justFilenameExtension("testing") === "testing"', () => {
    expect(justFilenameExtension("testing")).toBe("testing");
  });
});


