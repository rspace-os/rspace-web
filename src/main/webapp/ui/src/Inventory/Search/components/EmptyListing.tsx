import React, { type ComponentType } from "react";
import Box from "@mui/material/Box";
import { withStyles } from "Styles";
import Typography from "@mui/material/Typography";
import EmptyListingSvg from "/src/assets/graphics/EmptyListing.svg";
import {
  globalIdToInventoryRecordTypeLabel,
  type GlobalId,
} from "../../../stores/definitions/BaseRecord";
import { darken } from "@mui/material/styles";

type EmptyListingArgs = {
  parentGlobalId: GlobalId;
};

const EmptyListing: ComponentType<EmptyListingArgs> = withStyles<
  EmptyListingArgs,
  { root: string; help: string }
>((theme) => ({
  root: {
    fontWeight: 700,
    fontSize: "1.6rem",
    color: darken(theme.palette.primary.main, 0.2),
    display: "flex",
    flexDirection: "column",
    alignItems: "center",
    marginBottom: theme.spacing(1),
  },
  help: {
    textAlign: "center",
    color: darken(theme.palette.primary.main, 0.2),
    marginTop: theme.spacing(2),
    fontSize: "1rem",
    maxWidth: "20em",
  },
}))(({ classes, parentGlobalId }) => (
  <Box mt={6} className={classes.root}>
    <img src={EmptyListingSvg} alt="Empty Listing" />
    Empty {globalIdToInventoryRecordTypeLabel(parentGlobalId)}
    <Typography className={classes.help}>Nothing here yet.</Typography>
  </Box>
));

EmptyListing.displayName = "EmptyListing";
export default EmptyListing;
