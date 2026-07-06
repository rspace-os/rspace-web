import AddOutlinedIcon from "@mui/icons-material/AddOutlined";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Stack from "@mui/material/Stack";
import { observer } from "mobx-react-lite";
import type { ReactNode } from "react";
import CustomTooltip from "../../../components/CustomTooltip";
import FormControl from "../../../components/Inputs/FormControl";
import NoValue from "../../../components/NoValue";
import FieldModel, { type FieldModelAttrs } from "../../../stores/models/FieldModel";
import InstrumentTemplateModel from "../../../stores/models/InstrumentTemplateModel";
import useStores from "../../../stores/use-stores";
import CustomField from "../../Template/Fields/CustomField";

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
  const {
    searchStore: { activeResult },
    uiStore,
  } = useStores();
  if (!activeResult || !(activeResult instanceof InstrumentTemplateModel))
    throw new Error("ActiveResult must be an Instrument Template");

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
              key={i}
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
  return (
    <>
      {fields.length > 0 ? (
        <TemplateFields editable={editable} />
      ) : (
        <Box sx={{ mt: 2, mb: 1 }}>
          <NoValue label="No custom fields" />
        </Box>
      )}
      {editable && (
        <FormControl inline>
          <CustomTooltip title="Add field">
            <Button
              color="primary"
              startIcon={<AddOutlinedIcon />}
              variant="outlined"
              onClick={() => activeResult.addField(EMPTY_FIELD)}
            >
              Add new field
            </Button>
          </CustomTooltip>
        </FormControl>
      )}
    </>
  );
}

export default observer(CustomFields);
