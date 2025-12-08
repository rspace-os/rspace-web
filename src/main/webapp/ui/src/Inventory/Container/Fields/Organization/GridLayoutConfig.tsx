import Grid from "@mui/material/Grid";
import { observer } from "mobx-react-lite";
import type React from "react";
import type { Container } from "@/stores/definitions/Container";
import { type Axis, DEFAULT_COLUMN_AXIS, DEFAULT_ROW_AXIS } from "@/stores/definitions/container/types";
import FormControl from "../../../../components/Inputs/FormControl";
import RadioField, { type RadioOption } from "../../../../components/Inputs/RadioField";

const LABEL_OPTIONS: Array<RadioOption<Axis>> = [
    { label: "ABC", value: "ABC" },
    { label: "CBA", value: "CBA" },
    { label: "123", value: "N123" },
    { label: "321", value: "N321" },
];

type GridLayoutConfigArgs = {
    container: Container;
};

function GridLayoutConfig({ container }: GridLayoutConfigArgs): React.ReactNode {
    const gridLayout = container.gridLayout;
    if (container.cType !== "GRID" || !gridLayout) throw new Error("Container must be a Grid Container");

    const handleChange = (e: { target: { name: string; value: Axis | null } }) => {
        const { name, value } = e.target;
        container.setAttributesDirty({
            gridLayout: { ...container.gridLayout, [name]: value },
        });
    };

    return (
        <Grid container spacing={2}>
            <Grid item xs={12} md={6}>
                <FormControl label="Row Labels">
                    <RadioField
                        name="rowsLabelType"
                        value={gridLayout.rowsLabelType ?? DEFAULT_ROW_AXIS}
                        onChange={handleChange}
                        options={LABEL_OPTIONS}
                    />
                </FormControl>
            </Grid>
            <Grid item xs={12} md={6}>
                <FormControl label="Column Labels">
                    <RadioField
                        name="columnsLabelType"
                        value={gridLayout.columnsLabelType ?? DEFAULT_COLUMN_AXIS}
                        onChange={handleChange}
                        options={LABEL_OPTIONS}
                    />
                </FormControl>
            </Grid>
        </Grid>
    );
}

export default observer(GridLayoutConfig);
