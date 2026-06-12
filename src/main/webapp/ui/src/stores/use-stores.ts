import { useContext } from "react";
// biome-ignore lint/style/useImportType: initial biome migration
import { type StoreContainer } from "./stores/RootStore";
import { storesContext } from "./stores-context";

const useStores: () => StoreContainer = () => useContext(storesContext);

export default useStores;
