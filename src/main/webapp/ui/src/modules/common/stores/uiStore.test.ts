import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { createUIStore, FORCE_LIGHT_MODE } from "./uiStore";

beforeEach(() => {
  localStorage.clear();
  document.documentElement.classList.remove("dark");
});

afterEach(() => {
  localStorage.clear();
  document.documentElement.classList.remove("dark");
});

describe("uiStore", () => {
  it.skipIf(!FORCE_LIGHT_MODE)("ignores stored/toggled theme while dark mode is force-disabled", () => {
    localStorage.setItem("rspace-ds-theme", "dark");
    const store = createUIStore();

    expect(store.getState().theme).toBe("light");

    store.getState().toggleTheme();

    expect(store.getState().theme).toBe("light");
    expect(document.documentElement.classList.contains("dark")).toBe(false);
  });
});
