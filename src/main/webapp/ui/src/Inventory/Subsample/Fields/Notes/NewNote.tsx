import React, { useState, useEffect, useRef } from "react";
import { observer } from "mobx-react-lite";
import Typography from "@mui/material/Typography";
import TextField from "../../../../components/Inputs/TextField";
import SubSampleModel from "../../../../stores/models/SubSampleModel";
import FormField from "../../../../components/Inputs/FormField";
import Stack from "@mui/material/Stack";
import Button from "@mui/material/Button";
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
  const [initial, setInitial] = useState(true);

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

    /*
     * Once the users start editing, they can't go back to the initial state
     * without pressing the clear or submit buttons. This is to ensure that the
     * form section doesn't show an error state before the user has interacted
     * with the field.
     */
    if (!initial || value !== "") {
      setInitial(false);
    }
  };

  /*
   * Thanks to the condition above, these two state changes are intrinsically tied together.
   * If we call `setNote` with anything but an empty string, then `handleChange`
   * is called immediately by TextField, setting `initial` to false. If we call
   * `setNote` with an empty string but do not set `initial` to true, then the
   * useEffect below will move us into an error state even though the user has not
   * performed any action.
   */
  function resetField() {
    setNote("");
    setInitial(true);
  }

  const createNote = async () => {
    await record.createNote({ content: note });
    resetField();
    if (record.state === "preview") record.unsetDirtyFlag();
  };

  const clearNote = async () => {
    resetField();
    if (record.state === "preview") record.unsetDirtyFlag();
  };

  useEffect(() => {
    resetField();
    /*
     * Note that we do not call unsetDirtyFlag on the previous record here. We
     * assume that if the `record` has changed then it is because the user has
     * chosen to discard any changes they made to the previous record.
     */
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

  /*
   * This HAS to be a useEffect because handleChange gets called both when the
   * user enters text into the TextField but also when the `value` prop changes
   * e.g. when we programmatically set the field when clearing or submitting. By
   * using an effect, the `setInitial(true)` calls get applied and only then do
   * we check the error state.
   */
  useEffect(() => {
    onErrorStateChange(!initial && validateValue(note).isError);
  }, [note, initial]);

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
        <Stack direction="row" spacing={1}>
          <Button variant="outlined" onClick={clearNote}>
            Clear
          </Button>
          <ValidatingSubmitButton
            validationResult={validateValue(note)}
            onClick={createNote}
            loading={false}
          >
            Create note
          </ValidatingSubmitButton>
        </Stack>
      </Stack>
    </>
  );
}

export default observer(NewNote);
