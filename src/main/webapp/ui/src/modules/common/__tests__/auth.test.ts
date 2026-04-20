import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  clearStoredToken,
  getStoredToken,
  isExpiringSoon,
  resolveToken,
  saveStoredToken,
  secondsToExpiry,
} from "@/modules/common/utils/auth";

const originalSessionStorageDescriptor = Object.getOwnPropertyDescriptor(
  globalThis,
  "sessionStorage",
);

function encodeBase64Url(value: string) {
  return Buffer.from(value)
    .toString("base64")
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/g, "");
}

function createJwt(exp: number) {
  return [
    encodeBase64Url(JSON.stringify({ alg: "HS256", typ: "JWT" })),
    encodeBase64Url(JSON.stringify({ exp })),
    "signature",
  ].join(".");
}

describe("token storage helpers", () => {
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
    expect(getStoredToken()).toBeNull();

    saveStoredToken("test-token");
    expect(getStoredToken()).toBe("test-token");

    clearStoredToken();
    expect(getStoredToken()).toBeNull();
  });

  it("does not fail when sessionStorage is unavailable", () => {
    Object.defineProperty(globalThis, "sessionStorage", {
      configurable: true,
      get: () => undefined,
    });

    expect(getStoredToken()).toBeNull();
    expect(() => saveStoredToken("test-token")).not.toThrow();
    expect(() => clearStoredToken()).not.toThrow();
  });

  it("does not fail when sessionStorage access throws", () => {
    Object.defineProperty(globalThis, "sessionStorage", {
      configurable: true,
      value: {
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
      },
    });

    expect(getStoredToken()).toBeNull();
    expect(() => saveStoredToken("test-token")).not.toThrow();
    expect(() => clearStoredToken()).not.toThrow();
  });
});

describe("token expiry helpers", () => {
  it("treats non-JWT tokens as non-expiring", () => {
    expect(secondsToExpiry("api-key")).toBe(Infinity);
    expect(isExpiringSoon("api-key")).toBe(false);
  });

  it("calculates JWT expiry and expiring-soon state", () => {
    const nowSeconds = Math.floor(Date.now() / 1000);

    expect(secondsToExpiry(createJwt(nowSeconds + 600))).toBeGreaterThan(590);
    expect(isExpiringSoon(createJwt(nowSeconds + 60))).toBe(true);
  });
});

describe("resolveToken", () => {
  it("returns direct token when provided and does not call getToken", async () => {
    const getToken = vi.fn(() => Promise.resolve("callback-token"));

    const result = await resolveToken({ token: "direct-token", getToken });

    expect(result).toBe("direct-token");
    expect(getToken).not.toHaveBeenCalled();
  });

  it("uses getToken when direct token is not provided", async () => {
    const getToken = vi.fn(() => Promise.resolve("callback-token"));

    const result = await resolveToken({ getToken });

    expect(result).toBe("callback-token");
    expect(getToken).toHaveBeenCalledTimes(1);
  });

  it("throws when neither token nor getToken is provided", async () => {
    await expect(resolveToken({})).rejects.toThrow(
      "Token is required to perform this operation",
    );
  });
});

