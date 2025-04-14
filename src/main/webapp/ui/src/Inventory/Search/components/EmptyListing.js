//@flow

import React, { type ComponentType } from "react";
import Box from "@mui/material/Box";
import { withStyles } from "Styles";
import Typography from "@mui/material/Typography";
// $FlowExpectedError[cannot-resolve-module] An .svg, not a JS module
import EmptyListingSvg from "/src/assets/graphics/EmptyListing.svg";
import { globalIdToInventoryRecordTypeLabel } from "../../../stores/definitions/BaseRecord";
import { type GlobalId } from "../../../stores/definitions/BaseRecord";

type EmptyListingArgs = {|
  parentGlobalId: GlobalId,
|};

const EmptyListing: ComponentType<EmptyListingArgs> = withStyles<
  EmptyListingArgs,
  { root: string, heading: string, help: string }
>((theme) => ({
  root: {
    fontWeight: 700,
    fontSize: "1.6rem",
    color: theme.palette.lightestGrey,
    display: "flex",
    flexDirection: "column",
    alignItems: "center",
    marginBottom: theme.spacing(1),
  },
  heading: {
    color: theme.palette.primary.placeholderText,
  },
  help: {
    textAlign: "center",
    color: theme.palette.primary.placeholderText,
    marginTop: theme.spacing(2),
    fontSize: "1rem",
    maxWidth: "20em",
  },
}))(({ classes, parentGlobalId }) => (
  <Box mt={6} className={classes.root}>
    <img src={EmptyListingSvg} alt="Empty Listing" />
    <span className={classes.heading}>
      Empty {globalIdToInventoryRecordTypeLabel(parentGlobalId)}
    </span>
    <Typography className={classes.help}>Nothing here yet.</Typography>
  </Box>
));

EmptyListing.displayName = "EmptyListing";
export default EmptyListing;
