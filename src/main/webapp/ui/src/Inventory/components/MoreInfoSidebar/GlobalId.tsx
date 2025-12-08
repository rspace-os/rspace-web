import FormControl from "@mui/material/FormControl";
import FormLabel from "@mui/material/FormLabel";
import Grid from "@mui/material/Grid";
import { observer } from "mobx-react-lite";
import type React from "react";
import GlobalIdLink from "../../../components/GlobalId";
import type { InventoryRecord } from "../../../stores/definitions/InventoryRecord";

type GlobalIdArgs = {
    record: InventoryRecord;
};

function GlobalId({ record }: GlobalIdArgs): React.ReactNode {
    return (
        <Grid item>
            <FormControl component="fieldset" style={{ alignItems: "flex-start" }}>
                <FormLabel component="legend">Global ID</FormLabel>
                <GlobalIdLink record={record} />
            </FormControl>
        </Grid>
    );
}

export default observer(GlobalId);
