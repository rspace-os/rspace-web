// @flow

import { createContext, type Context } from "react";
import getRootStore, { type StoreContainer } from "./stores/RootStore";

export const storesContext: Context<StoreContainer> = createContext(
  getRootStore()
);
