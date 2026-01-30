/*
 */
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { filenameExceptExtension } from "../../files";

describe("filenameExceptExtension", () => {
  it('filenameExceptExtension("testing.txt") === "testing"', () => {
    expect(filenameExceptExtension("testing.txt")).toBe("testing");
  });

  it('filenameExceptExtension("testing.md") === "testing"', () => {
    expect(filenameExceptExtension("testing.md")).toBe("testing");
  });

  it('filenameExceptExtension("testing") === "testing"', () => {
    expect(filenameExceptExtension("testing")).toBe("testing");
  });

  it('filenameExceptExtension("testing.js.flow") === "testing.js"', () => {
    expect(filenameExceptExtension("testing.js.flow")).toBe("testing.js");
  });
});


