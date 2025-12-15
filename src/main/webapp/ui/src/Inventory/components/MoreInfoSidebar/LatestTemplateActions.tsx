import React from "react";
import { observer } from "mobx-react-lite";
import FormControl from "@mui/material/FormControl";
import FormGroup from "@mui/material/FormGroup";
import FormLabel from "@mui/material/FormLabel";
import Button from "@mui/material/Button";
import TemplateModel from "../../../stores/models/TemplateModel";
import { type InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import Grid from "@mui/material/Grid";

type LatestTemplateActionsArgs = {
  record: InventoryRecord;
};

function LatestTemplateActions({
  record,
}: LatestTemplateActionsArgs): React.ReactNode {
  if (!(record instanceof TemplateModel) || record.historicalVersion)
    return null;

  return (
    <Grid item>
      <FormControl component="fieldset">
        <FormLabel component="legend">Update Samples</FormLabel>
        <FormGroup>
          <Button
            variant="outlined"
            disableElevation
            onClick={() => {
              void record.updateSamplesToLatest();
            }}
          >
            Update Samples
          </Button>
        </FormGroup>
      </FormControl>
    </Grid>
  );
}

export default observer(LatestTemplateActions);
