import { useContext } from "react";

import getRootStore from "./stores/getRootStore";
// Side-effect import that registers the RootStore factory with the accessor.
// Models import getRootStore from the dependency-free leaf and no longer pull
// RootStore in transitively (that broke a circular import), so this is the
// application bootstrap point: every store-using component imports useStores,
// guaranteeing RootStore is registered before getRootStore() is first called.
import "./stores/RootStore";
import { storesContext } from "./stores-context";
import { type StoreContainer } from "./stores/RootStore";

// Falls back to the real singleton when no storesContext Provider is present
// (the application renders without one). Resolving here at render time avoids a
// module-load-time getRootStore() call in stores-context.
const useStores: () => StoreContainer = () =>
  useContext(storesContext) ?? getRootStore();

export default useStores;
