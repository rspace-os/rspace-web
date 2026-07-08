import AddOutlinedIcon from "@mui/icons-material/AddOutlined";
import Button from "@mui/material/Button";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useTranslation } from "react-i18next";
import CustomTooltip from "../../../components/CustomTooltip";
import FormControl from "../../../components/Inputs/FormControl";
import type { FieldModelAttrs } from "../../../stores/models/FieldModel";
import type TemplateModel from "../../../stores/models/TemplateModel";

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
  const { t } = useTranslation("inventory");
  return (
    <FormControl inline>
      <CustomTooltip title={t("fields.templateFields.addField")}>
        <Button
          color="primary"
          startIcon={<AddOutlinedIcon />}
          variant="outlined"
          onClick={() => record.addField(EMPTY_FIELD)}
        >
          {t("fields.extraFields.addNewField")}
        </Button>
      </CustomTooltip>
    </FormControl>
  );
}

export default observer(NewField);
