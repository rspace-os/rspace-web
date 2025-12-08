import Menu from "@mui/material/Menu";
import { observer } from "mobx-react-lite";
import React from "react";
import { type FieldType, SUPPORTED_TYPES } from "../../../stores/models/FieldTypes";
import FieldTypeMenuItem from "./FieldTypeMenuItem";

type FieldTypeMenuArgs = {
    fieldType: FieldType;
    onChange: (fieldType: FieldType) => void;
};

function FieldTypeMenu({ fieldType, onChange }: FieldTypeMenuArgs): React.ReactNode {
    const [anchorEl, setAnchorEl] = React.useState<HTMLElement | null>(null);
    return (
        <>
            <FieldTypeMenuItem field={fieldType} onClick={(e) => setAnchorEl(e.currentTarget as HTMLElement)} />
            <Menu
                id="fieldTypeMenu"
                keepMounted
                anchorEl={anchorEl}
                open={Boolean(anchorEl)}
                onClose={() => setAnchorEl(null)}
            >
                {[...SUPPORTED_TYPES].map((f, i) => (
                    <FieldTypeMenuItem
                        key={i}
                        field={f}
                        onClick={() => {
                            onChange(f);
                            setAnchorEl(null);
                        }}
                        inMenu
                    />
                ))}
            </Menu>
        </>
    );
}

export default observer(FieldTypeMenu);
