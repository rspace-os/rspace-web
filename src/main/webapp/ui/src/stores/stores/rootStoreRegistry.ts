import type { StoreContainer } from "./RootStore";

/**
 * Holds the lazily-constructed RootStore singleton and the factory that creates
 * it. This lives in its own module (separate from the `getRootStore` accessor)
 * for two reasons:
 *
 *  1. It imports `RootStore` for its TYPE only, so it never pulls the store
 *     graph in at module-load time — this is what breaks the model <-> store <->
 *     Factory circular import.
 *  2. Unit tests routinely `vi.mock` the `getRootStore` accessor to inject fake
 *     stores. Keeping the registration here means `RootStore.ts` can register
 *     its factory against an un-mocked module, so mocking the accessor never
 *     removes `registerRootStore`.
 *
 * `RootStore.ts` calls `registerRootStore` when it is imported during
 * application/test bootstrap.
 */

let storeContainer: StoreContainer | undefined;
let createStoreContainer: (() => StoreContainer) | undefined;

export function registerRootStore(factory: () => StoreContainer): void {
  createStoreContainer = factory;
}

export function resolveStoreContainer(): StoreContainer {
  if (!storeContainer) {
    if (!createStoreContainer) {
      throw new Error(
        "RootStore has not been initialised: the RootStore module must be imported during " +
          "application/test bootstrap before getRootStore() is called.",
      );
    }
    storeContainer = createStoreContainer();
  }
  return storeContainer;
}
