import Button from "@mui/material/Button";
import FormControl from "@mui/material/FormControl";
import FormGroup from "@mui/material/FormGroup";
import FormLabel from "@mui/material/FormLabel";
import Grid from "@mui/material/Grid";
import { observer } from "mobx-react-lite";
import type React from "react";
import type { InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import TemplateModel from "../../../stores/models/TemplateModel";

type LatestTemplateActionsArgs = {
    record: InventoryRecord;
};

function LatestTemplateActions({ record }: LatestTemplateActionsArgs): React.ReactNode {
    if (!(record instanceof TemplateModel) || record.historicalVersion) return null;

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
