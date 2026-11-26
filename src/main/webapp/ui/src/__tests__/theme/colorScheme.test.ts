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

  it("reports a JSP page in a browser without @scope as unsupported", async () => {
    window.history.pushState({}, "", "/userform");
    document.documentElement.setAttribute("data-dark-mode-unsupported", "");
    vi.resetModules();
    expect((await import("@/colorScheme")).darkModeUnsupported).toBe(true);
    document.documentElement.removeAttribute("data-dark-mode-unsupported");
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

describe("printing", () => {
  afterEach(() => {
    window.history.pushState({}, "", "/");
    window.localStorage.clear();
    vi.resetModules();
    document.documentElement.className = "";
  });

  it("prints light without changing the stored mode", async () => {
    window.history.pushState({}, "", "/gallery");
    window.localStorage.setItem(MUI_MODE_STORAGE_KEY, "dark");
    vi.resetModules();
    (await import("@/colorScheme")).initColorMode();
    const events: Array<string | null> = [];
    const record = (event: StorageEvent) => events.push(event.newValue);
    window.addEventListener("storage", record);

    window.dispatchEvent(new Event("beforeprint"));
    expect(document.documentElement).toHaveClass("light");
    expect(events).toContain("light");
    expect(window.localStorage.getItem(MUI_MODE_STORAGE_KEY)).toBe("dark");

    window.dispatchEvent(new Event("afterprint"));
    expect(document.documentElement).toHaveClass("dark");
    expect(window.localStorage.getItem(MUI_MODE_STORAGE_KEY)).toBe("dark");
    window.removeEventListener("storage", record);
  });
});
