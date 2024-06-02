//@flow

import React, { type Node } from "react";
import { type HasUneditableFields } from "../../../stores/definitions/Editable";
import { type InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import Breadcrumbs from "../Breadcrumbs";
import FormField from "../../../components/Inputs/FormField";
import Box from "@mui/material/Box";

export default function Location<
  Fields: {
    location: InventoryRecord,
    ...
  },
  FieldOwner: HasUneditableFields<Fields>
>({ fieldOwner }: {| fieldOwner: FieldOwner |}): Node {
  return (
    <FormField
      value={void 0}
      disabled
      label="Location"
      renderInput={() => (
        <Box>
          <Breadcrumbs record={fieldOwner.fieldValues.location} showCurrent />
        </Box>
      )}
    />
  );
}
