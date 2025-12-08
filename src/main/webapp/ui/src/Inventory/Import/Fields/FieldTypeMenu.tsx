import Box from "@mui/material/Box";
import Menu from "@mui/material/Menu";
import { observer } from "mobx-react-lite";
import React from "react";
import type { ColumnFieldMap } from "../../../stores/models/ImportModel";
import FieldTypeMenuItem from "./FieldTypeMenuItem";
import UploadFormControl from "./FormControl";

type FieldTypeMenuArgs = {
    columnFieldMap: ColumnFieldMap;
};

function FieldTypeMenu({ columnFieldMap }: FieldTypeMenuArgs): React.ReactNode {
    const [anchorEl, setAnchorEl] = React.useState<HTMLElement | null>(null);
    return (
        <Box p={1}>
            <UploadFormControl label="Custom Field Type">
                <FieldTypeMenuItem
                    field={columnFieldMap.chosenFieldType}
                    onClick={(e) => setAnchorEl(e.currentTarget)}
                    options={columnFieldMap.options}
                />
            </UploadFormControl>
            <Menu
                id="fieldTypeMenu"
                keepMounted
                anchorEl={anchorEl}
                open={Boolean(anchorEl)}
                onClose={() => setAnchorEl(null)}
            >
                {columnFieldMap.allValidTypes.map((f, i) => (
                    <FieldTypeMenuItem
                        key={i}
                        field={f}
                        onClick={() => {
                            columnFieldMap.setChosenFieldType(f);
                            setAnchorEl(null);
                        }}
                        inMenu
                    />
                ))}
            </Menu>
        </Box>
    );
}

export default observer(FieldTypeMenu);
