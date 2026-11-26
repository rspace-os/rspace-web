import Box from "@mui/material/Box";
import Stack from "@mui/material/Stack";
import { observer } from "mobx-react-lite";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import NoValue from "../../../components/NoValue";
import FieldModel from "../../../stores/models/FieldModel";
import TemplateModel from "../../../stores/models/TemplateModel";
import useStores from "../../../stores/use-stores";
import { objectKey } from "../../../util/objectKey";
import CustomField from "./CustomField";
import NewField from "./NewField";

type FieldsArgs = {
  onErrorStateChange: (fieldIdentifier: string, errorState: boolean) => void;
};

type TemplateFieldsArgs = {
  activeResult: TemplateModel;
  editable: boolean;
  onErrorStateChange: (fieldIdentifier: string, errorState: boolean) => void;
};

const TemplateFields = observer(({ activeResult, editable, onErrorStateChange }: TemplateFieldsArgs) => {
  const { uiStore } = useStores();
  const removeCustomField =
    (field: FieldModel) =>
    (deleteFromSamples = false) => {
      activeResult.removeCustomField(field.id, activeResult.fields.indexOf(field), deleteFromSamples);
    };

  return (
    <Stack spacing={2}>
      {activeResult.fields
        .filter((field): field is FieldModel => field instanceof FieldModel)
        .map((field: FieldModel, i: number) => (
          <CustomField
            field={field}
            i={i}
            key={objectKey(field)}
            editable={editable}
            onErrorStateChange={(value) => onErrorStateChange(field.globalId ?? "NEW", value)}
            onRemove={(b) => removeCustomField(field)(b)}
            forceColumnLayout={!uiStore.isLarge}
            onMove={(index) => activeResult.moveField(field, index)}
          />
        ))}
    </Stack>
  );
});

function Fields({ onErrorStateChange }: FieldsArgs): ReactNode {
  const { t } = useTranslation("inventory");
  const {
    searchStore: { activeResult },
  } = useStores();
  if (!activeResult || !(activeResult instanceof TemplateModel)) throw new Error("ActiveResult must be a Template");

  const fields = activeResult.fields;
  const editable = activeResult.isFieldEditable("fields");
  return (
    <>
      {fields.length > 0 ? (
        <TemplateFields activeResult={activeResult} editable={editable} onErrorStateChange={onErrorStateChange} />
      ) : (
        <Box sx={{ mt: 2, mb: 1 }}>
          <NoValue label={t("fields.templateFields.customField.noMoreFields")} />
        </Box>
      )}
      {editable && <NewField record={activeResult} />}
    </>
  );
}

export default observer(Fields);
