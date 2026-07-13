import AddOutlinedIcon from "@mui/icons-material/AddOutlined";
import Button from "@mui/material/Button";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useTranslation } from "react-i18next";
import CustomTooltip from "../../../../components/CustomTooltip";
import FormControl from "../../../../components/Inputs/FormControl";
import type { InventoryRecord } from "../../../../stores/definitions/InventoryRecord";

type NewFieldArgs = {
  record: InventoryRecord;
};

function NewField({ record }: NewFieldArgs): React.ReactNode {
  const { t } = useTranslation("inventory");
  const EMPTY_FIELD = {
    id: null,
    globalId: null,
    name: "",
    lastModified: null,
    type: "text" as const,
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
            ? t("fields.extraFields.addOneFieldAtATime")
            : t("fields.extraFields.addCustomField")
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
          {t("fields.extraFields.addNewField")}
        </Button>
      </CustomTooltip>
    </FormControl>
  );
}

export default observer(NewField);
