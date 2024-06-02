// @flow

import FieldTypeMenuItem from "./FieldTypeMenuItem";
import Menu from "@mui/material/Menu";
import React, { type Node, type ComponentType } from "react";
import { observer } from "mobx-react-lite";
import {
  SUPPORTED_TYPES,
  type FieldType,
} from "../../../stores/models/FieldTypes";

type FieldTypeMenuArgs = {|
  fieldType: FieldType,
  onChange: (FieldType) => void,
|};

function FieldTypeMenu({ fieldType, onChange }: FieldTypeMenuArgs): Node {
  const [anchorEl, setAnchorEl] = React.useState<?EventTarget>(null);
  return (
    <>
      <FieldTypeMenuItem
        field={fieldType}
        onClick={(e) => setAnchorEl(e.currentTarget)}
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

export default (observer(FieldTypeMenu): ComponentType<FieldTypeMenuArgs>);
