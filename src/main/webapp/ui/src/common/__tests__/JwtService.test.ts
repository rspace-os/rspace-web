import { afterEach, beforeEach, describe, expect, it } from "vitest";
import JwtService from "@/common/JwtService";

const originalSessionStorageDescriptor = Object.getOwnPropertyDescriptor(
  globalThis,
  "sessionStorage",
);

describe("JwtService", () => {
  beforeEach(() => {
    globalThis.sessionStorage.clear();
  });

  afterEach(() => {
    if (originalSessionStorageDescriptor) {
      Object.defineProperty(
        globalThis,
        "sessionStorage",
        originalSessionStorageDescriptor,
      );
    }
  });

  it("saves, reads, and removes tokens from sessionStorage", () => {
    expect(JwtService.getToken()).toBeNull();

    JwtService.saveToken("test-token");
    expect(JwtService.getToken()).toBe("test-token");

    JwtService.destroyToken();
    expect(JwtService.getToken()).toBeNull();
  });

  it("does not fail when sessionStorage is unavailable", () => {
    Object.defineProperty(globalThis, "sessionStorage", {
      configurable: true,
      get: () => undefined,
    });

    expect(JwtService.getToken()).toBeNull();
    expect(() => JwtService.saveToken("test-token")).not.toThrow();
    expect(() => JwtService.destroyToken()).not.toThrow();
  });

  it("does not fail when sessionStorage access throws", () => {
    const throwingStorage = {
      getItem: () => {
        throw new Error("SecurityError");
      },
      setItem: () => {
        throw new Error("SecurityError");
      },
      removeItem: () => {
        throw new Error("SecurityError");
      },
      clear: () => {},
    };

    Object.defineProperty(globalThis, "sessionStorage", {
      configurable: true,
      value: throwingStorage,
    });

    expect(JwtService.getToken()).toBeNull();
    expect(() => JwtService.saveToken("test-token")).not.toThrow();
    expect(() => JwtService.destroyToken()).not.toThrow();
  });
});

