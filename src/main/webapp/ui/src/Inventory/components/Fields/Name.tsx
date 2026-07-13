import Box from "@mui/material/Box";
import FormControl from "@mui/material/FormControl";
import { observer } from "mobx-react-lite";
import React, { useState } from "react";
import { useTranslation } from "react-i18next";
import { Heading } from "@/components/DynamicHeadingLevel";
import GlobalId from "../../../components/GlobalId";
import StringField from "../../../components/Inputs/StringField";
import type { HasEditableFields } from "../../../stores/definitions/Editable";
import type { Record } from "../../../stores/definitions/Record";
import FormField from "../../components/Inputs/FormField";

const MIN = 2;
const MAX = 255;

function Name<
  Fields extends {
    name: string;
  },
  FieldOwner extends HasEditableFields<Fields>,
>({
  fieldOwner,
  record,
  onErrorStateChange,
}: {
  fieldOwner: FieldOwner;
  record?: Record;
  onErrorStateChange: (isError: boolean) => void;
}): React.ReactNode {
  const { t } = useTranslation("inventory");
  const [initial, setInitial] = useState(true);

  const handleChange = ({ target: { value: name } }: { target: { value: string } }) => {
    fieldOwner.setFieldsDirty({ name });
    setInitial(false);
    onErrorStateChange(name.length > MAX || name.length < MIN || name.trim().length === 0);
  };

  const errorMessage = () => {
    if (fieldOwner.fieldValues.name.trim().length === 0 && !initial) return t("fields.name.nonWhitespace");
    if (fieldOwner.fieldValues.name.length < MIN && !initial) return t("fields.name.minLength", { min: MIN });
    if (fieldOwner.fieldValues.name.length > MAX) return t("fields.name.maxLength", { max: MAX });
    return null;
  };

  const labelId = React.useId();
  if (!fieldOwner.isFieldEditable("name")) {
    const globalId = record ? (
      <Box sx={{ ml: 1 }} component="span">
        <GlobalId record={record} />
      </Box>
    ) : null;

    return (
      <FormControl fullWidth role="group" aria-labelledby={labelId}>
        {/*
         * Rendering an HTMLLabelElement would be an accessibility violation
         * as there is no interactable HTMLInputElement to attach it to. A
         * heading is more appropriate when the form is not ediable.
         */}
        <Heading sx={{ mt: 0 }} id={labelId}>
          {t("fields.name.label")}
        </Heading>
        <Box sx={{ wordBreak: "break-all" }}>
          {fieldOwner.fieldValues.name}
          {globalId}
        </Box>
      </FormControl>
    );
  }

  return (
    <FormField
      label={t("fields.name.label")}
      value={fieldOwner.fieldValues.name || ""}
      maxLength={MAX}
      disabled={!fieldOwner.isFieldEditable("name")}
      error={Boolean(errorMessage())}
      helperText={errorMessage()}
      required
      renderInput={(props) => (
        <StringField
          {...props}
          onChange={handleChange}
          onBlur={() => {
            setInitial(false);
          }}
        />
      )}
    />
  );
}

export default observer(Name);
