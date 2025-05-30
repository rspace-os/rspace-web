import React, { type Context } from "react";
import { type RecordType } from "../definitions/InventoryRecord";

export type AllowedFormTypes = RecordType | "mixed";

type FormSectionsContext = {
  isExpanded(type: AllowedFormTypes, section: string): boolean;
  setExpanded(type: AllowedFormTypes, section: string, newState: boolean): void;
  setAllExpanded(type: AllowedFormTypes, newState: boolean): void;
};

export default React.createContext(null) as Context<FormSectionsContext | null>;
