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
  // Dark mode is temporarily disabled repo-wide (see FORCE_LIGHT_MODE in
  // uiStore.tsx); these assert the real per-instance/persistence behavior and
  // will apply again once the flag is removed.
  it.skipIf(FORCE_LIGHT_MODE)("tracks the theme independently for each store instance", () => {
    const firstStore = createUIStore();
    const secondStore = createUIStore();

    expect(firstStore.getState().theme).toBe("light");

    firstStore.getState().setTheme("dark");

    expect(firstStore.getState().theme).toBe("dark");
    expect(secondStore.getState().theme).toBe("light");
  });

  it.skipIf(FORCE_LIGHT_MODE)("applies the theme to the document and persists it", () => {
    const store = createUIStore();

    store.getState().toggleTheme();

    expect(store.getState().theme).toBe("dark");
    expect(document.documentElement.classList.contains("dark")).toBe(true);
    expect(localStorage.getItem("rspace-ds-theme")).toBe("dark");

    store.getState().toggleTheme();

    expect(store.getState().theme).toBe("light");
    expect(document.documentElement.classList.contains("dark")).toBe(false);
    expect(localStorage.getItem("rspace-ds-theme")).toBe("light");
  });

  it.skipIf(FORCE_LIGHT_MODE)("initializes from a previously stored theme", () => {
    localStorage.setItem("rspace-ds-theme", "dark");

    expect(createUIStore().getState().theme).toBe("dark");
  });

  it.skipIf(!FORCE_LIGHT_MODE)("ignores stored/toggled theme while dark mode is force-disabled", () => {
    localStorage.setItem("rspace-ds-theme", "dark");
    const store = createUIStore();

    expect(store.getState().theme).toBe("light");

    store.getState().toggleTheme();

    expect(store.getState().theme).toBe("light");
    expect(document.documentElement.classList.contains("dark")).toBe(false);
  });
});
