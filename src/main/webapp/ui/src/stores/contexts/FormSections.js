//@flow

import React, { type Context } from "react";
import { type RecordType } from "../definitions/InventoryRecord";

export type AllowedFormTypes = RecordType | "mixed";

type FormSectionsContext = {|
  isExpanded(AllowedFormTypes, string): boolean,
  setExpanded(AllowedFormTypes, string, boolean): void,
  setAllExpanded(AllowedFormTypes, boolean): void,
|};

export default (React.createContext(null): Context<?FormSectionsContext>);
