import { createContext, type Context } from "react";
import { type StoreContainer } from "./stores/RootStore";

/**
 * The default is `null` (resolved lazily by `useStores` at render time via
 * `getRootStore()`), rather than calling `getRootStore()` here at module-load.
 * Doing it at module-load would require the RootStore module to have been
 * imported/registered before this module evaluates, reintroducing a bootstrap
 * ordering dependency. Components that are not wrapped in a `storesContext`
 * Provider fall back to the real singleton in `useStores`.
 */
export const storesContext: Context<StoreContainer | null> =
  createContext<StoreContainer | null>(null);
