import type { StoreContainer } from "./RootStore";
import { resolveStoreContainer } from "./rootStoreRegistry";

/**
 * Dependency-free accessor for the singleton RootStore's store container.
 *
 * The model layer calls `getRootStore()` at runtime, but the models, the stores
 * and the record Factory form a circular import graph. Importing `getRootStore`
 * straight from `./RootStore` used to eagerly evaluate `RootStore.ts` (which
 * imports all ten store classes -> Factory -> models), so depending on import
 * order a base class could be `undefined` when a subclass evaluated
 * `class X extends Y`.
 *
 * This module imports `RootStore` for its TYPE only and resolves the concrete
 * instance through `rootStoreRegistry`, which `RootStore.ts` populates when it
 * is loaded during bootstrap. Consumers therefore never pull the store graph in
 * at module-load time.
 *
 * It is intentionally a thin wrapper so that unit tests can `vi.mock` it to
 * inject fake stores without disturbing the registry.
 */
export default function getRootStore(): StoreContainer {
  return resolveStoreContainer();
}
