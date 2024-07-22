//@flow

import React, {
  useState,
  useEffect,
  type Node,
  type ComponentType,
} from "react";
import { observer } from "mobx-react-lite";
import Button from "@mui/material/Button";
import Typography from "@mui/material/Typography";
import TextField from "../../../../components/Inputs/TextField";
import SubSampleModel from "../../../../stores/models/SubSampleModel";
import FormField from "../../../../components/Inputs/FormField";
import Stack from "@mui/material/Stack";

const emptyNote = {
  content: "",
};

type NewNoteArgs = {|
  record: SubSampleModel,
  onErrorStateChange: (boolean) => void,
|};

function NewNote({ record, onErrorStateChange }: NewNoteArgs): Node {
  const [note, setNote] = useState(emptyNote);
  const [initial, setInitial] = useState(true);
  const [error, setError] = useState(false);
  const [errorMessage, setErrorMessage] = useState<?string>(null);

  const handleChange = ({
    target: { value },
  }: {
    target: { name: string, value: string, ... },
    ...
  }) => {
    setNote({
      ...note,
      content: value,
    });
    if (value === "") {
      // when editing, unsetting the dirty flag could result in other changes
      // being not savable
      if (record.state === "preview") record.unsetDirtyFlag();
    } else {
      record.setDirtyFlag();
    }
  };

  const createNote = () => {
    void record.createNote(note);
    setNote(emptyNote);
    setInitial(true);
  };

  useEffect(() => {
    if (initial) {
      setInitial(false);
    } else if (note.content === "") {
      setError(true);
      setErrorMessage("Content should not be empty.");
      onErrorStateChange(true);
    } else if (note.content.length > 2000) {
      setError(true);
      setErrorMessage("Content should not exceed 2000 characters.");
      onErrorStateChange(true);
    } else {
      setError(false);
      setErrorMessage(null);
      onErrorStateChange(false);
    }
  }, [note.content]);

  useEffect(() => {
    setError(false);
    setInitial(true);
    setErrorMessage(null);
    setNote(emptyNote);
  }, [record]);

  return (
    <>
      <FormField
        label="New note"
        maxLength={2000}
        helperText={errorMessage}
        error={error}
        value={note.content}
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
        <Button
          style={{ minWidth: "unset", whiteSpace: "nowrap" }}
          disableElevation
          disabled={
            !record.isFieldEditable("notes") || error || note.content === ""
          }
          variant="contained"
          id="add-note"
          data-test-id="add-note-button"
          aria-label="Add new note"
          onClick={createNote}
          color="primary"
        >
          Create note
        </Button>
      </Stack>
    </>
  );
}

export default (observer(NewNote): ComponentType<NewNoteArgs>);
