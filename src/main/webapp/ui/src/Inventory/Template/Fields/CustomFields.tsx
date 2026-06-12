import Box from "@mui/material/Box";
import Stack from "@mui/material/Stack";
import { observer } from "mobx-react-lite";
import type { ReactNode } from "react";
import NoValue from "../../../components/NoValue";
import FieldModel from "../../../stores/models/FieldModel";
import TemplateModel from "../../../stores/models/TemplateModel";
import useStores from "../../../stores/use-stores";
import * as ArrayUtils from "../../../util/ArrayUtils";
import CustomField from "./CustomField";
import NewField from "./NewField";

type FieldsArgs = {
  onErrorStateChange: (fieldIdentifier: string, errorState: boolean) => void;
};

function Fields({ onErrorStateChange }: FieldsArgs): ReactNode {
  const {
    searchStore: { activeResult },
    uiStore,
  } = useStores();
  if (!activeResult || !(activeResult instanceof TemplateModel)) throw new Error("ActiveResult must be a Template");

  const TemplateFields = observer(({ editable }: { editable: boolean }) => {
    const removeCustomField =
      (field: FieldModel) =>
      (deleteFromSamples = false) => {
        activeResult.removeCustomField(field.id, activeResult.fields.indexOf(field), deleteFromSamples);
      };

    return (
      <Stack spacing={2}>
        {ArrayUtils.filterClass(FieldModel, activeResult.fields).map((field: FieldModel, i: number) => (
          <CustomField
            field={field}
            i={i}
            key={i}
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

  const fields = activeResult.fields;
  const editable = activeResult.isFieldEditable("fields");
  return (
    <>
      {fields.length > 0 ? (
        <TemplateFields editable={editable} />
      ) : (
        <Box sx={{ mt: 2, mb: 1 }}>
          <NoValue label="No more fields" />
        </Box>
      )}
      {editable && <NewField record={activeResult} />}
    </>
  );
}

export default observer(Fields);
