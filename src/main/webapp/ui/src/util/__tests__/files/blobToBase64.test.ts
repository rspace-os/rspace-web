/*
 */
import { describe, expect, it } from "vitest";
import "@testing-library/jest-dom/vitest";
import { blobToBase64 } from "../../files";

describe("blobToBase64", () => {
  it("Simple example", async () => {
    expect(await blobToBase64(new Blob(["foo\n"]))).toBe(
      "data:application/octet-stream;base64,Zm9vCg=="
    );
  });
});


