//@flow

import React, { type Node, type ComponentType } from "react";
import { observer } from "mobx-react-lite";
import NoteItem from "./NoteItem";
import List from "@mui/material/List";
import SubSampleModel from "../../../../stores/models/SubSampleModel";
import Button from "@mui/material/Button";
import Collapse from "@mui/material/Collapse";
import Divider from "@mui/material/Divider";

type NotesListArgs = {|
  record: SubSampleModel,
|};

function NotesList({ record }: NotesListArgs): Node {
  const [firstNote, secondNote, ...restOfNotes] = record.notes.toReversed();
  const [open, setOpen] = React.useState(false);

  if (record.loading) return null;
  if (record.notes.length === 0) return null;

  return (
    <List disablePadding>
      {restOfNotes.length > 0 && (
        <Divider textAlign="center" sx={{ backgroundColor: "white" }}>
          <Button
            size="small"
            onClick={() => {
              setOpen(!open);
            }}
          >
            {open ? "Show fewer" : "Show more"}
          </Button>
        </Divider>
      )}
      <Collapse in={open}>
        {restOfNotes.toReversed().map((note, i) => (
          <NoteItem divider key={i + 2} note={note} />
        ))}
      </Collapse>
      {secondNote && <NoteItem divider key={1} note={secondNote} />}
      {firstNote && <NoteItem divider key={0} note={firstNote} />}
    </List>
  );
}

export default (observer(NotesList): ComponentType<NotesListArgs>);
