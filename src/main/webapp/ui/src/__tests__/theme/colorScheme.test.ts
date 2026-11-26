import { afterEach, describe, expect, it, vi } from "vitest";
import { isDarkModePath, MUI_MODE_STORAGE_KEY } from "@/colorScheme";

describe("isDarkModePath", () => {
  it.each(["/gallery", "/inventory/search", "/apps", "/workspace", "/workspace/", "/workspace/123"])(
    "allows %s",
    (path) => {
      expect(isDarkModePath(path)).toBe(true);
    },
  );

  it.each(["/workspace/editor/structuredDocument/32", "/workspace/trash/list", "/userform", "/apps/foo"])(
    "keeps %s light",
    (path) => {
      expect(isDarkModePath(path)).toBe(false);
    },
  );
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
