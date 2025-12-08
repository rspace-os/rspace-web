import { type Context, createContext } from "react";
import getRootStore, { type StoreContainer } from "./stores/RootStore";

export const storesContext: Context<StoreContainer> = createContext(getRootStore());
