import { renderHook } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { UIStoreProvider } from "@/modules/common/stores/uiStore";
import { useTheme } from "../useTheme";

afterEach(() => {
  document.documentElement.classList.remove("dark");
});

describe("useTheme", () => {
  it("applies forced light mode to the document", () => {
    document.documentElement.classList.add("dark");

    const { result } = renderHook(() => useTheme(), { wrapper: UIStoreProvider });

    expect(result.current.theme).toBe("light");
    expect(document.documentElement).not.toHaveClass("dark");
    expect(localStorage.getItem("rspace-ds-theme")).toBe("light");
  });
});
