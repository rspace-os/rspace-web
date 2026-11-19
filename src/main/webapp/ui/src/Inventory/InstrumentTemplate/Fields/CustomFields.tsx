import AddOutlinedIcon from "@mui/icons-material/AddOutlined";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import { observer } from "mobx-react-lite";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import CustomTooltip from "../../../components/CustomTooltip";
import FormControl from "../../../components/Inputs/FormControl";
import NoValue from "../../../components/NoValue";
import type { FieldModelAttrs } from "../../../stores/models/FieldModel";
import InstrumentTemplateModel from "../../../stores/models/InstrumentTemplateModel";
import useStores from "../../../stores/use-stores";
import TemplateFieldList from "../../Template/Fields/TemplateFieldList";
import useFocusAddedField from "../../Template/Fields/useFocusAddedField";

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

type CustomFieldsArgs = {
  onErrorStateChange: (fieldIdentifier: string, errorState: boolean) => void;
};

function CustomFields({ onErrorStateChange }: CustomFieldsArgs): ReactNode {
  const { t } = useTranslation("inventory");
  const {
    searchStore: { activeResult },
    uiStore,
  } = useStores();
  if (!activeResult || !(activeResult instanceof InstrumentTemplateModel))
    throw new Error("ActiveResult must be an Instrument Template");

  const fields = activeResult.fields;
  const editable = activeResult.isFieldEditable("fields");
  const { listRef, fieldAdded } = useFocusAddedField(fields.length);
  return (
    <>
      {fields.length > 0 ? (
        <Box ref={listRef}>
          <TemplateFieldList
            record={activeResult}
            editable={editable}
            onErrorStateChange={onErrorStateChange}
            forceColumnLayout={!uiStore.isLarge}
            recordTypeName="instrument"
          />
        </Box>
      ) : (
        <Box sx={{ mt: 2, mb: 1 }}>
          <NoValue label={t("instrumentTemplate.customFields.noCustomFields")} />
        </Box>
      )}
      {editable && (
        <FormControl inline>
          <CustomTooltip title={t("instrumentTemplate.customFields.addFieldTooltip")}>
            <Button
              color="primary"
              startIcon={<AddOutlinedIcon />}
              variant="outlined"
              onClick={() => {
                activeResult.addField(EMPTY_FIELD);
                fieldAdded();
              }}
            >
              {t("fields.extraFields.addNewField")}
            </Button>
          </CustomTooltip>
        </FormControl>
      )}
    </>
  );
}

export default observer(CustomFields);
