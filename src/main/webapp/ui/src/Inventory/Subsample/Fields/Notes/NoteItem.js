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
import { styled, darken } from "@mui/material/styles";
import { chipClasses } from "@mui/material/Chip";

type NoteItemArgs = {|
  note: Note,
|};

const CustomListItem = styled(ListItem)(({ theme }) => ({
  borderLeft: `4px solid ${theme.palette.record.subSample.bg}`,
  backgroundColor: theme.palette.hover.tableRow,
  color: darken(theme.palette.record.subSample.bg, 0.9),
  padding: theme.spacing(0, 1),
  marginBottom: theme.spacing(1),
  "& p": {
    marginBlockStart: theme.spacing(0.25),
    marginBlockEnd: theme.spacing(0.25),
    marginLeft: theme.spacing(2),
    fontSize: "0.85rem",
  },
  [`& .${chipClasses.root}`]: {
    background: "transparent",
    border: `2px solid ${theme.palette.record.subSample.bg}`,
  },
}));

export default function NoteItem({ note }: NoteItemArgs): Node {
  return (
    <CustomListItem alignItems="flex-start">
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
    </CustomListItem>
  );
}
