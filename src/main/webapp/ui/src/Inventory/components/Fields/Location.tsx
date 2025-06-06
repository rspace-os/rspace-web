import React from "react";
import { type HasUneditableFields } from "../../../stores/definitions/Editable";
import { type InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import Breadcrumbs from "../Breadcrumbs";
import FormField from "../../../components/Inputs/FormField";
import Box from "@mui/material/Box";

export default function Location<
  Fields extends {
    location: InventoryRecord;
  },
  FieldOwner extends HasUneditableFields<Fields>
>({ fieldOwner }: { fieldOwner: FieldOwner }): React.ReactNode {
  return (
    <FormField
      value={undefined}
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