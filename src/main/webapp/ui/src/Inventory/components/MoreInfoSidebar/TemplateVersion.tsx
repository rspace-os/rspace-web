import FormControl from "@mui/material/FormControl";
import FormGroup from "@mui/material/FormGroup";
import FormLabel from "@mui/material/FormLabel";
import Grid from "@mui/material/Grid";
import { observer } from "mobx-react-lite";
import type React from "react";
import type { InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import TemplateModel from "../../../stores/models/TemplateModel";

type TemplateVersionArgs = {
    record: InventoryRecord;
};

function TemplateVersion({ record }: TemplateVersionArgs): React.ReactNode {
    if (!(record instanceof TemplateModel)) return null;

    return (
        <Grid item>
            <FormControl component="fieldset">
                <FormLabel component="legend">Version</FormLabel>
                <FormGroup>{record.version}</FormGroup>
            </FormControl>
        </Grid>
    );
}

export default observer(TemplateVersion);
