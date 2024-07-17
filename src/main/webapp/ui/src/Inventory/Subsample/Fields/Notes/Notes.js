//@flow

import React, { type Node, type ComponentType } from "react";
import { observer } from "mobx-react-lite";
import NotesList from "./NotesList";
import NewNote from "./NewNote";
import FormControl from "../../../../components/Inputs/FormControl";
import SubSampleModel from "../../../../stores/models/SubSampleModel";
import FormLabel from "@mui/material/FormLabel";

type NotesArgs = {|
  record: SubSampleModel,
  onErrorStateChange: (boolean) => void,
  hideLabel?: boolean,
|};

function Notes({
  record,
  onErrorStateChange,
  hideLabel = false,
}: NotesArgs): Node {
  return (
    <FormControl>
      {!hideLabel && <FormLabel>Notes</FormLabel>}
      {record.isFieldEditable("notes") && record.isFieldVisible("notes") && (
        <NewNote record={record} onErrorStateChange={onErrorStateChange} />
      )}
      {record.isFieldVisible("notes") && <NotesList record={record} />}
    </FormControl>
  );
}

export default (observer(Notes): ComponentType<NotesArgs>);
