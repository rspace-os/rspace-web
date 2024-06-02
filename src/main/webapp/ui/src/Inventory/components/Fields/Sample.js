//@flow

import React, { type Node } from "react";
import { type HasUneditableFields } from "../../../stores/definitions/Editable";
import { type Sample } from "../../../stores/definitions/Sample";
import { RecordLink } from "../RecordLink";
import FormField from "../../../components/Inputs/FormField";
import Box from "@mui/material/Box";

export default function SampleField<
  Fields: {
    sample: Sample,
    ...
  },
  FieldOwner: HasUneditableFields<Fields>
>({ fieldOwner }: {| fieldOwner: FieldOwner |}): Node {
  const sample = fieldOwner.fieldValues.sample;

  return (
    <FormField
      value={void 0}
      label="Parent Sample"
      disabled
      renderInput={() => (
        <Box>
          <RecordLink record={sample} />
        </Box>
      )}
    />
  );
}
