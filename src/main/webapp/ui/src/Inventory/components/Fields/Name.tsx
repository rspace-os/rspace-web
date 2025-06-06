import React, { useState } from "react";
import { observer } from "mobx-react-lite";
import StringField from "../../../components/Inputs/StringField";
import { type HasEditableFields } from "../../../stores/definitions/Editable";
import GlobalId from "../../../components/GlobalId";
import Box from "@mui/material/Box";
import { type Record } from "../../../stores/definitions/Record";
import FormField from "../../../components/Inputs/FormField";
import FormControl from "@mui/material/FormControl";
import FormLabel from "@mui/material/FormLabel";

const MIN = 2;
const MAX = 255;

function Name<
  Fields extends {
    name: string;
  },
  FieldOwner extends HasEditableFields<Fields>
>({
  fieldOwner,
  record,
  onErrorStateChange,
}: {
  fieldOwner: FieldOwner;
  record?: Record;
  onErrorStateChange: (isError: boolean) => void;
}): React.ReactNode {
  const [initial, setInitial] = useState(true);

  const handleChange = ({
    target: { value: name },
  }: {
    target: { value: string };
  }) => {
    fieldOwner.setFieldsDirty({ name });
    setInitial(false);
    onErrorStateChange(
      name.length > MAX || name.length < MIN || name.trim().length === 0
    );
  };

  const errorMessage = () => {
    if (fieldOwner.fieldValues.name.trim().length === 0 && !initial)
      return "Name must include at least one non-whitespace character.";
    if (fieldOwner.fieldValues.name.length < MIN && !initial)
      return `Name must be at least ${MIN} characters.`;
    if (fieldOwner.fieldValues.name.length > MAX)
      return `Name must be no longer than ${MAX} characters.`;
    return null;
  };

  const labelId = React.useId();
  if (!fieldOwner.isFieldEditable("name")) {
    const globalId = record ? (
      <Box ml={1} component="span">
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
        <FormLabel component="h4" sx={{ mt: 0 }} id={labelId}>
          Name
        </FormLabel>
        <div style={{ wordBreak: "break-all" }}>
          {fieldOwner.fieldValues.name}
          {globalId}
        </div>
      </FormControl>
    );
  }

  return (
    <FormField
      label="Name"
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
