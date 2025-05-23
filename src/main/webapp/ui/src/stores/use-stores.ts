import { useContext } from "react";

import { storesContext } from "./stores-context";
import { type StoreContainer } from "./stores/RootStore";

const useStores: () => StoreContainer = () => useContext(storesContext);

export default useStores;
