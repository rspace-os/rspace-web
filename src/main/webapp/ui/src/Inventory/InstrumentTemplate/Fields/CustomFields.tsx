import AddOutlinedIcon from "@mui/icons-material/AddOutlined";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Stack from "@mui/material/Stack";
import { observer } from "mobx-react-lite";
import { type ReactNode, useMemo } from "react";
import { useTranslation } from "react-i18next";
import CustomTooltip from "../../../components/CustomTooltip";
import FormControl from "../../../components/Inputs/FormControl";
import NoValue from "../../../components/NoValue";
import FieldModel, { type FieldModelAttrs } from "../../../stores/models/FieldModel";
import InstrumentTemplateModel from "../../../stores/models/InstrumentTemplateModel";
import useStores from "../../../stores/use-stores";
import CustomField from "../../Template/Fields/CustomField";
import TemplateDisplayRules from "../../Template/Fields/TemplateDisplayRules";
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
  // a record not yet saved has no global id, so each one (activeResult) gets its own session for the panel key
  const session = useMemo(() => crypto.randomUUID(), [activeResult]);

  const TemplateFields = observer(({ editable }: { editable: boolean }) => {
    const removeCustomField =
      (field: FieldModel) =>
      (b = false) => {
        activeResult.removeCustomField(field.id, activeResult.fields.indexOf(field), b);
      };

    return (
      <Stack spacing={2}>
        {activeResult.fields
          .filter((field): field is FieldModel => field instanceof FieldModel)
          .map((field: FieldModel, i: number) => (
            <CustomField
              field={field}
              i={i}
              key={field.fieldKey ?? i}
              editable={editable}
              onErrorStateChange={(value) => onErrorStateChange(field.globalId ?? "NEW", value)}
              onRemove={(b) => removeCustomField(field)(b)}
              forceColumnLayout={!uiStore.isLarge}
              onMove={(index) => activeResult.moveField(field, index)}
              recordTypeName="instrument"
            />
          ))}
      </Stack>
    );
  });

  const fields = activeResult.fields;
  const editable = activeResult.isFieldEditable("fields");
  const { listRef, fieldAdding } = useFocusAddedField(fields.length);
  return (
    <>
      {/* keyed by edit session, not loading, so a failed save keeps drafts but another template or edit starts afresh */}
      <TemplateDisplayRules
        key={`${activeResult.globalId ?? session}:${activeResult.state}`}
        fields={fields}
        editable={editable}
      />
      {fields.length > 0 ? (
        <Box ref={listRef}>
          <TemplateFields editable={editable} />
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
                fieldAdding();
                activeResult.addField(EMPTY_FIELD);
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
