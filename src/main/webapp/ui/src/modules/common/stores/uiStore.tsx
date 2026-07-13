import * as React from "react";
import { useStore } from "zustand";
import { devtools } from "zustand/middleware";
import { createStore, type StoreApi } from "zustand/vanilla";

export type Theme = "light" | "dark";

const THEME_STORAGE_KEY = "rspace-ds-theme";

// TEMPORARY: dark mode is forced off across newApp until it's ready for
// release. Remove this flag (and the two short-circuits below) to restore
// stored-preference / system-preference detection. Exported so uiStore.test.ts
// can skip the now-inapplicable dark-mode assertions instead of deleting them.
export const FORCE_LIGHT_MODE = true;

function getInitialTheme(): Theme {
  if (FORCE_LIGHT_MODE) return "light";
  if (typeof window === "undefined") return "light";
  const stored = localStorage.getItem(THEME_STORAGE_KEY);
  if (stored === "light" || stored === "dark") return stored;
  if (typeof window.matchMedia !== "undefined") {
    return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
  }
  return "light";
}

// Persisted as a raw string (not persist-middleware JSON) so the key stays
// readable and compatible with values written before this store existed.
function applyTheme(theme: Theme) {
  if (typeof document === "undefined") return;
  document.documentElement.classList.toggle("dark", theme === "dark");
  localStorage.setItem(THEME_STORAGE_KEY, theme);
}

export type UIStore = {
  theme: Theme;
  setTheme: (theme: Theme) => void;
  toggleTheme: () => void;
};

export function createUIStore() {
  return createStore<UIStore>()(
    devtools(
      (set, get) => ({
        theme: getInitialTheme(),
        setTheme: (theme) => {
          if (FORCE_LIGHT_MODE) return;
          applyTheme(theme);
          set({ theme }, undefined, "setTheme");
        },
        toggleTheme: () => get().setTheme(get().theme === "light" ? "dark" : "light"),
      }),
      { name: "uiStore", enabled: import.meta.env.DEV },
    ),
  );
}

const UIStoreContext = React.createContext<StoreApi<UIStore> | null>(null);

export function UIStoreProvider({ children }: { children: React.ReactNode }) {
  const store = React.useRef<StoreApi<UIStore> | null>(null);
  store.current ??= createUIStore();
  const instance = store.current;

  // Reflect the initial (stored or system-preference) theme on <html> once.
  React.useEffect(() => {
    applyTheme(instance.getState().theme);
  }, [instance]);

  return <UIStoreContext.Provider value={instance}>{children}</UIStoreContext.Provider>;
}

export function useUIStore<T>(selector: (state: UIStore) => T) {
  const store = React.useContext(UIStoreContext);
  if (!store) throw new Error("useUIStore must be used within UIStoreProvider");
  return useStore(store, selector);
}
