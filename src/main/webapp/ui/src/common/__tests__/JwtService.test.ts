import { beforeEach, describe, expect, it } from "vitest";
import JwtService from "@/common/JwtService";

describe("JwtService", () => {
  beforeEach(() => {
    globalThis.sessionStorage.clear();
  });

  it("saves, reads, and removes tokens from sessionStorage", () => {
    expect(JwtService.getToken()).toBeNull();

    JwtService.saveToken("test-token");
    expect(JwtService.getToken()).toBe("test-token");

    JwtService.destroyToken();
    expect(JwtService.getToken()).toBeNull();
  });
});

