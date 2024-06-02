//@flow

import React, { type Node, type ComponentType } from "react";
import Button from "@mui/material/Button";
import { observer } from "mobx-react-lite";
import AddOutlinedIcon from "@mui/icons-material/AddOutlined";
import CustomTooltip from "../../../../components/CustomTooltip";
import FormControl from "../../../../components/Inputs/FormControl";
import { type InventoryRecord } from "../../../../stores/definitions/InventoryRecord";

type NewFieldArgs = {|
  record: InventoryRecord,
|};

function NewField({ record }: NewFieldArgs): Node {
  const EMPTY_FIELD = {
    id: null,
    globalId: null,
    name: "",
    lastModified: null,
    type: "text",
    content: "",
    parentGlobalId: record.globalId,
    editing: true,
    initial: true,
  };

  return (
    <FormControl inline>
      <CustomTooltip
        title={
          record.hasUnsavedExtraField
            ? "You can only create one field at a time."
            : "Add custom field."
        }
      >
        <Button
          color="primary"
          disabled={record.hasUnsavedExtraField}
          startIcon={<AddOutlinedIcon />}
          variant="outlined"
          onClick={() => record.addExtraField(EMPTY_FIELD)}
          data-test-id="AddCustomFieldButton"
        >
          Add new field
        </Button>
      </CustomTooltip>
    </FormControl>
  );
}

export default (observer(NewField): ComponentType<NewFieldArgs>);
