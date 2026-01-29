/*
 */
import { describe, test, expect } from "vitest";
import "@testing-library/jest-dom/vitest";
import { blobToBase64 } from "../../files";

describe("blobToBase64", () => {
  test("Simple example", async () => {
    expect(await blobToBase64(new Blob(["foo\n"]))).toBe(
      "data:application/octet-stream;base64,Zm9vCg=="
    );
  });
});


