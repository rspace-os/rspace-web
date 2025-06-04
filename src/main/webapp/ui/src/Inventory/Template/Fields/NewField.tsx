import React from "react";
import Button from "@mui/material/Button";
import { observer } from "mobx-react-lite";
import AddOutlinedIcon from "@mui/icons-material/AddOutlined";
import CustomTooltip from "../../../components/CustomTooltip";
import FormControl from "../../../components/Inputs/FormControl";
import TemplateModel from "../../../stores/models/TemplateModel";
import { type FieldModelAttrs } from "../../../stores/models/FieldModel";

const EMPTY_FIELD: FieldModelAttrs = {
  name: "",
  type: "text",
  editing: true,
  initial: true,
  selectedOptions: [],
  columnIndex: null,
  attachment: null,
  mandatory: false,
};

type NewFieldArgs = {
  record: TemplateModel;
};

function NewField({ record }: NewFieldArgs): React.ReactNode {
  return (
    <FormControl inline>
      <CustomTooltip title="Add field">
        <Button
          color="primary"
          startIcon={<AddOutlinedIcon />}
          variant="outlined"
          onClick={() => record.addField(EMPTY_FIELD)}
        >
          Add new field
        </Button>
      </CustomTooltip>
    </FormControl>
  );
}

export default observer(NewField);
