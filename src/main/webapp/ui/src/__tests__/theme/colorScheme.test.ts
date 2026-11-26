import { afterEach, describe, expect, it, vi } from "vitest";
import { isDarkModePath, MUI_MODE_STORAGE_KEY } from "@/colorScheme";

describe("isDarkModePath", () => {
  it.each(["/gallery", "/inventory/search", "/apps", "/about"])("allows %s", (path) => {
    expect(isDarkModePath(path)).toBe(true);
  });

  it.each(["/workspace", "/userform", "/apps/foo"])("leaves %s to the JSP decorator", (path) => {
    expect(isDarkModePath(path)).toBe(false);
  });
});

describe("darkModeAvailable", () => {
  afterEach(() => {
    window.history.pushState({}, "", "/");
    document.documentElement.removeAttribute("data-dark-mode");
    vi.resetModules();
  });

  it("follows the marker the JSP decorator sets", async () => {
    window.history.pushState({}, "", "/userform");
    vi.resetModules();
    expect((await import("@/colorScheme")).darkModeAvailable).toBe(false);

    document.documentElement.setAttribute("data-dark-mode", "");
    vi.resetModules();
    expect((await import("@/colorScheme")).darkModeAvailable).toBe(true);
  });
});

describe("setColorMode", () => {
  afterEach(() => {
    window.history.pushState({}, "", "/");
    vi.restoreAllMocks();
    vi.resetModules();
    document.documentElement.className = "";
  });

  it("keeps the chosen mode when storage cannot be written", async () => {
    window.history.pushState({}, "", "/gallery");
    vi.resetModules();
    const { initColorMode, setColorMode } = await import("@/colorScheme");
    initColorMode();
    vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new Error("QuotaExceededError");
    });
    vi.spyOn(Storage.prototype, "getItem").mockReturnValue(null);

    setColorMode("dark");

    expect(document.documentElement).toHaveClass("dark");
    window.dispatchEvent(new StorageEvent("storage", { key: MUI_MODE_STORAGE_KEY, newValue: "light" }));
    expect(document.documentElement).toHaveClass("light");
  });
});
