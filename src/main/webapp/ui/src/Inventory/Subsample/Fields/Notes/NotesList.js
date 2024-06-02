//@flow

import React, { type Node, type ComponentType } from "react";
import { observer } from "mobx-react-lite";
import NoteItem from "./NoteItem";
import List from "@mui/material/List";
import SubSampleModel from "../../../../stores/models/SubSampleModel";

type NotesListArgs = {|
  record: SubSampleModel,
|};

function NotesList({ record }: NotesListArgs): Node {
  return (
    !record.loading &&
    record.notes.length > 0 && (
      <List disablePadding>
        {record.notes.map((note, i) => (
          <NoteItem key={i} note={note} />
        ))}
      </List>
    )
  );
}

export default (observer(NotesList): ComponentType<NotesListArgs>);
