import { useContext } from "react";
import type { StoreContainer } from "./stores/RootStore";
import { storesContext } from "./stores-context";

const useStores: () => StoreContainer = () => useContext(storesContext);

export default useStores;
