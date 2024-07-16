//@flow

import React, { type Node } from "react";
import ListItem from "@mui/material/ListItem";
import ListItemText from "@mui/material/ListItemText";
import UserDetails from "../../../components/UserDetails";
import TimeAgoCustom from "../../../../components/TimeAgoCustom";
import TextField from "../../../../components/Inputs/TextField";
import { type Note } from "../../../../stores/models/SubSampleModel";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";

type NoteItemArgs = {|
  note: Note,
  divider?: boolean,
|};

export default function NoteItem({ note, divider = true }: NoteItemArgs): Node {
  return (
    <ListItem alignItems="flex-start" divider={divider}>
      <ListItemText
        disableTypography
        primary={
          <>
            <UserDetails
              userId={note.createdBy.id}
              fullName={`${note.createdBy.firstName} ${note.createdBy.lastName}`}
              position={["bottom", "right"]}
            />
            <Box ml={2} component="span">
              <Typography variant="caption">
                <TimeAgoCustom time={note.created} />
              </Typography>
            </Box>
          </>
        }
        secondary={
          <TextField value={note.content} disabled={true} variant="standard" />
        }
      />
    </ListItem>
  );
}
