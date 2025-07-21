import React, { useState, useEffect } from "react";
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

  /*
   * TextField's onChange handler is called both when the user types and when
   * the `value` prop changes. `handleChange` needs to call `onErrorStateChange`
   * so that if the user types more characters than is allowed then we propagate
   * on error state up to the parent section of the form. However, when
   * `createNote` resets the TextField after succesfully saving the new note,
   * `handleChange` will now be called with a value of the empty string. This is
   * technically an error, but we don't want the parent form section to be in an
   * error state when the user has just created a note, so we track whether this
   * is the initial state of the TextField with `initial` and only call
   * `onErrorStateChange` if it is not the initial state.
   */
  const [initial, setInitial] = useState(true);

  const handleChange = ({
    target: { value },
  }: {
    target: { name: string; value: string };
  }) => {
    setNote(value);
    setInitial(false);
    if (value === "") {
      // when editing, unsetting the dirty flag could result in other changes
      // being not savable
      if (record.state === "preview") record.unsetDirtyFlag();
    } else {
      record.setDirtyFlag();
    }
    console.debug("calling onErrorStateChange", {
      value,
      initial,
      isError: validate(value).isError,
      errors: validate(value).orElseGet((e) => e),
    });
    onErrorStateChange(!initial && validate(value).isError);
  };

  const createNote = () => {
    void record.createNote({ content: note });
    setNote("");
    setInitial(true);
  };

  useEffect(() => {
    setNote("");
  }, [record]);

  function validate(value: string) {
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
          validationResult={validate(note)}
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
