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

  const handleChange = ({
    target: { value },
  }: {
    target: { name: string; value: string };
  }) => {
    setNote(value);
    if (value === "") {
      // when editing, unsetting the dirty flag could result in other changes
      // being not savable
      if (record.state === "preview") record.unsetDirtyFlag();
    } else {
      record.setDirtyFlag();
    }
    onErrorStateChange(validate().isError);
  };

  const createNote = () => {
    void record.createNote({ content: note });
    setNote("");
  };

  useEffect(() => {
    setNote("");
  }, [record]);

  function validate() {
    if (!record.isFieldEditable("notes")) {
      return IsInvalid("Notes are not editable");
    }
    if (note === "") {
      return IsInvalid("Note cannot be empty.");
    }
    if (note.length > 2000) {
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
          validationResult={validate()}
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
