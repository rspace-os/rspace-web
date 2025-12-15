import FieldTypeMenuItem from "./FieldTypeMenuItem";
import Menu from "@mui/material/Menu";
import React from "react";
import { observer } from "mobx-react-lite";
import {
  SUPPORTED_TYPES,
  type FieldType,
} from "../../../stores/models/FieldTypes";

type FieldTypeMenuArgs = {
  fieldType: FieldType;
  onChange: (fieldType: FieldType) => void;
};

function FieldTypeMenu({
  fieldType,
  onChange,
}: FieldTypeMenuArgs): React.ReactNode {
  const [anchorEl, setAnchorEl] = React.useState<HTMLElement | null>(null);
  return (
    <>
      <FieldTypeMenuItem
        field={fieldType}
        onClick={(e) => setAnchorEl(e.currentTarget as HTMLElement)}
      />
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
