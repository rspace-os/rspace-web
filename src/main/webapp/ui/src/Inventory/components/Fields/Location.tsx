import Box from "@mui/material/Box";
import type React from "react";
import FormField from "../../../components/Inputs/FormField";
// biome-ignore lint/style/useImportType: initial biome migration
import { type HasUneditableFields } from "../../../stores/definitions/Editable";
// biome-ignore lint/style/useImportType: initial biome migration
import { type InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import Breadcrumbs from "../Breadcrumbs";

export default function Location<
  Fields extends {
    location: InventoryRecord;
  },
  FieldOwner extends HasUneditableFields<Fields>,
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
