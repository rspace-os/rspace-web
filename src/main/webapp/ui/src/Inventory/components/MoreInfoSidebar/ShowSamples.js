// @flow

import React, { type Node, type ComponentType, useContext } from "react";
import { observer } from "mobx-react-lite";
import FormControl from "@mui/material/FormControl";
import FormGroup from "@mui/material/FormGroup";
import FormLabel from "@mui/material/FormLabel";
import Button from "@mui/material/Button";
import TemplateModel from "../../../stores/models/TemplateModel";
import { type InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import Grid from "@mui/material/Grid";
import FormSectionsContext from "../../../stores/contexts/FormSections";

type ShowSamplesArgs = {|
  record: InventoryRecord,
|};

function ShowSamples({ record }: ShowSamplesArgs): Node {
  if (!(record instanceof TemplateModel) || record.historicalVersion)
    return null;
  const formSectionContext = useContext(FormSectionsContext);
  if (!formSectionContext)
    throw new Error("FormSectionContext is required by StepperPanel");
  const { setExpanded } = formSectionContext;

  return (
    <Grid item>
      <FormControl component="fieldset">
        <FormLabel component="legend">Created Samples</FormLabel>
        <FormGroup>
          <Button
            variant="outlined"
            disableElevation
            onClick={() => {
              if (document.body) window.scrollTo(0, document.body.scrollHeight);
              setExpanded("sampleTemplate", "samples", true);
            }}
          >
            Show Samples
          </Button>
        </FormGroup>
      </FormControl>
    </Grid>
  );
}

export default (observer(ShowSamples): ComponentType<ShowSamplesArgs>);
