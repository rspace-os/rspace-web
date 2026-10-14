import { renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import { UIStoreProvider } from "@/modules/common/stores/uiStore";
import { useTheme } from "../useTheme";

beforeEach(() => {
  localStorage.clear();
  document.documentElement.classList.remove("dark");
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
