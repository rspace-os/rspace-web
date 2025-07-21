import React, { useState, useEffect, useRef } from "react";
import { observer } from "mobx-react-lite";
import Typography from "@mui/material/Typography";
import TextField from "../../../../components/Inputs/TextField";
import SubSampleModel from "../../../../stores/models/SubSampleModel";
import FormField from "../../../../components/Inputs/FormField";
import Stack from "@mui/material/Stack";
import ValidatingSubmitButton, {
  IsInvalid,
  IsValid,
} from "@/components/ValidatingSubmitButton";

type NewNoteArgs = {
  record: SubSampleModel;
  onErrorStateChange: (hasError: boolean) => void;
};

function NewNote({ record, onErrorStateChange }: NewNoteArgs): React.ReactNode {
  const [note, setNote] = useState("");
  console.debug("NewNote", { record, note });

  /*
   * Track when we're programmatically resetting the field after creating a note.
   * This prevents showing an error state in the parent form section when
   * `setNote("")` triggers `handleChange` with an empty value, which would
   * normally be considered an error state. Using a ref to avoid timing issues
   * with state updates.
   */
  const isResettingRef = useRef(false);

  const handleChange = ({
    target: { value },
  }: {
    target: { name: string; value: string };
  }) => {
    setNote(value);

    if (value === "") {
      if (record.state === "preview") record.unsetDirtyFlag();
    } else {
      record.setDirtyFlag();
    }

    // Don't show error state when we're programmatically resetting the field
    if (!isResettingRef.current) {
      onErrorStateChange(validateValue(value).isError);
    }
  };

  const createNote = async () => {
    isResettingRef.current = true;
    await record.createNote({ content: note });
    setNote("");
    onErrorStateChange(false);
    // Reset the flag after a microtask to ensure onChange has been called
    await Promise.resolve();
    isResettingRef.current = false;
  };

  useEffect(() => {
    setNote("");
    isResettingRef.current = false;
  }, [record]);

  function validateValue(value: string) {
    if (!record.isFieldEditable("notes")) {
      return IsInvalid("Notes are not editable");
    }
    if (value === "") {
      return IsInvalid("Note cannot be empty.");
    }
    if (value.length > 2000) {
      return IsInvalid("Note cannot exceed 2000 characters.");
    }
    return IsValid();
  }

  return (
    <>
      <FormField
        label="New note"
        maxLength={2000}
        value={note}
        renderInput={({ error: _error, ...props }) => (
          <TextField onChange={handleChange} name="content" {...props} />
        )}
        disabled={!record.isFieldEditable("notes")}
      />
      <Stack
        direction="row"
        justifyContent="space-between"
        alignItems="flex-start"
      >
        <Typography variant="caption">
          Please note that once created, notes can be neither edited nor
          deleted.
        </Typography>
        <ValidatingSubmitButton
          validationResult={validateValue(note)}
          onClick={createNote}
          loading={false}
        >
          Create note
        </ValidatingSubmitButton>
      </Stack>
    </>
  );
}

export default observer(NewNote);
