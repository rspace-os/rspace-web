//@flow

import React, { type Node } from "react";
import { observer } from "mobx-react-lite";
import TextField from "../../../components/Inputs/TextField";
import { type HasEditableFields } from "../../../stores/definitions/Editable";
import BatchFormField from "../Inputs/BatchFormField";

const MAX_LENGTH = 250;

/*
 * The fact that the description can be null results in a rare bug. If a record
 * has both a null description and another text field that uses the TinyMCE
 * editor (such as sample's fields or any record's text extra field) then when
 * the record transitions from preview mode to edit mode, the other TinyMCE
 * editors trigger their `onEditorChange` handlers. This appears to be a bug
 * within TinyMCE as within our code these components to do not share any
 * information. The result is that `setAttributesDirty` is called and the
 * dialog that checks that the user wishes to discard their changes -- even if
 * they haven't made any changes -- is shown. This is does not happen if the
 * description is a string value rather than null. When creating new records in
 * the UI, we set the description to an empty string if the users doesn't
 * provide an explicit value so null only occurs when the database is
 * prepopulated with records or when records are created directly from the API.
 */
function Description<
  Fields: {
    description: ?string,
    ...
  },
  FieldOwner: HasEditableFields<Fields>
>({
  fieldOwner,
  onErrorStateChange,
}: {|
  fieldOwner: FieldOwner,
  onErrorStateChange: (boolean) => void,
|}): Node {
  const handleChange = (e: {
    target: { name: string, value: string, ... },
    ...
  }) => {
    fieldOwner.setFieldsDirty({
      description: e.target.value,
    });
    onErrorStateChange(e.target.value.length > MAX_LENGTH);
  };

  const errorMessage = () => {
    if ((fieldOwner.fieldValues.description ?? "").length > MAX_LENGTH)
      return `Description must be no longer than ${MAX_LENGTH} characters (including HTML tags).`;
    return null;
  };

  return (
    <BatchFormField
      label="Description"
      value={fieldOwner.fieldValues.description ?? ""}
      disabled={!fieldOwner.isFieldEditable("description")}
      maxLength={MAX_LENGTH}
      error={Boolean(errorMessage())}
      helperText={errorMessage()}
      // ID is not used because TinyMCE does not expose an HTMLInputElement to attach it to
      doNotAttachIdToLabel
      renderInput={({ error: _error, id: _id, ...props }) => (
        <TextField {...props} onChange={handleChange} />
      )}
      noValueLabel={fieldOwner.noValueLabel.description}
      canChooseWhichToEdit={fieldOwner.canChooseWhichToEdit}
      setDisabled={(d) => {
        fieldOwner.setFieldEditable("description", d);
      }}
    />
  );
}

export default (observer(Description): typeof Description);
