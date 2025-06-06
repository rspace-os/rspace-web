/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import "@testing-library/jest-dom";
import { filenameExceptExtension } from "../../files";

describe("filenameExceptExtension", () => {
  test('filenameExceptExtension("testing.txt") === "testing"', () => {
    expect(filenameExceptExtension("testing.txt")).toBe("testing");
  });

  test('filenameExceptExtension("testing.md") === "testing"', () => {
    expect(filenameExceptExtension("testing.md")).toBe("testing");
  });

  test('filenameExceptExtension("testing") === "testing"', () => {
    expect(filenameExceptExtension("testing")).toBe("testing");
  });

  test('filenameExceptExtension("testing.js.flow") === "testing.js"', () => {
    expect(filenameExceptExtension("testing.js.flow")).toBe("testing.js");
  });
});
