import { renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { UIStoreProvider } from "@/modules/common/stores/uiStore";
import { useTheme } from "../useTheme";

beforeEach(() => {
  localStorage.clear();
  document.documentElement.classList.remove("dark");

  if (typeof window.matchMedia === "undefined") {
    Object.defineProperty(window, "matchMedia", {
      writable: true,
      value: (_query: string) => ({
        matches: false,
        addEventListener: () => {},
        removeEventListener: () => {},
      }),
    });
  }
});

afterEach(() => {
  localStorage.clear();
  document.documentElement.classList.remove("dark");
});

describe("useTheme", () => {
  it("defaults to light when no storage and no system preference", () => {
    const { result } = renderHook(() => useTheme(), { wrapper: UIStoreProvider });
    expect(result.current.theme).toBe("light");
    expect(document.documentElement.classList.contains("dark")).toBe(false);
  });
});
