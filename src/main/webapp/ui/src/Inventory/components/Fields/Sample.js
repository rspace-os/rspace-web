//@flow

import React, { type Node } from "react";
import { type HasUneditableFields } from "../../../stores/definitions/Editable";
import { type Sample } from "../../../stores/definitions/Sample";
import { RecordLink } from "../RecordLink";
import FormField from "../../../components/Inputs/FormField";
import Box from "@mui/material/Box";
import Link from "@mui/material/Link";
import Typography from "@mui/material/Typography";
import NavigateContext from "../../../stores/contexts/Navigate";

export default function SampleField<
  Fields: {
    sample: Sample,
    ...
  },
  FieldOwner: HasUneditableFields<Fields>
>({ fieldOwner }: {| fieldOwner: FieldOwner |}): Node {
  const sample = fieldOwner.fieldValues.sample;
  const { useNavigate } = React.useContext(NavigateContext);
  const navigate = useNavigate();

  return (
    <FormField
      value={void 0}
      label="Parent Sample"
      disabled
      renderInput={() => (
        <>
          <Box>
            <RecordLink record={sample} />
          </Box>
          {sample.globalId && (
            <Typography variant="caption">
              <Link
                href={`/inventory/search?parentGlobalId=${sample.globalId}`}
                onClick={(e) => {
                  e.preventDefault();
                  if (sample.globalId)
                    navigate(
                      `/inventory/search?parentGlobalId=${sample.globalId}`
                    );
                }}
              >
                There {sample.subSamplesCount === 2 ? "is" : "are"}{" "}
                {sample.subSamplesCount - 1} other{" "}
                {sample.subSamplesCount === 2
                  ? sample.subSampleAlias.alias
                  : sample.subSampleAlias.plural}
                .
              </Link>
            </Typography>
          )}
        </>
      )}
    />
  );
}
