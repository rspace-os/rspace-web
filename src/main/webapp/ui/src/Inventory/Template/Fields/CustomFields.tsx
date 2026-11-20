import Box from "@mui/material/Box";
import { observer } from "mobx-react-lite";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import NoValue from "../../../components/NoValue";
import TemplateModel from "../../../stores/models/TemplateModel";
import useStores from "../../../stores/use-stores";
import NewField from "./NewField";
import TemplateFieldList from "./TemplateFieldList";
import useFocusAddedField from "./useFocusAddedField";

type FieldsArgs = {
  onErrorStateChange: (fieldIdentifier: string, errorState: boolean) => void;
};

function Fields({ onErrorStateChange }: FieldsArgs): ReactNode {
  const { t } = useTranslation("inventory");
  const {
    searchStore: { activeResult },
    uiStore,
  } = useStores();
  if (!activeResult || !(activeResult instanceof TemplateModel)) throw new Error("ActiveResult must be a Template");

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
          />
        </Box>
      ) : (
        <Box sx={{ mt: 2, mb: 1 }}>
          <NoValue label={t("fields.templateFields.customField.noMoreFields")} />
        </Box>
      )}
      {editable && <NewField record={activeResult} onAdd={fieldAdded} />}
    </>
  );
}

export default observer(Fields);
