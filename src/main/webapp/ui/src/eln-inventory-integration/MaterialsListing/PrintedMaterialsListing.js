//@flow

import { ListOfMaterials } from "../../stores/models/MaterialsModel";
import React, { type Node } from "react";
import MaterialsTable from "./MaterialsTable";
import Box from "@mui/material/Box";
import { makeStyles } from "tss-react/mui";

const useStyles = makeStyles()(() => ({
  wrapper: {
    display: "none",
    "@media print": {
      display: "initial",
    },
  },
}));

type PrintedMaterialsListingArgs = {|
  listsOfMaterials: ?Array<ListOfMaterials>,
|};

/*
 * When a structured document is printed, this component is used to render the
 *  list of materials, as a table, onto the page rather than as it normally
 *  appears in a dialog.
 */
export default function PrintedMaterialsListing({
  listsOfMaterials,
}: PrintedMaterialsListingArgs): Node {
  const { classes } = useStyles();
  return listsOfMaterials ? (
    <div className={classes.wrapper}>
      {listsOfMaterials.map((lom) => (
        <Box border={1} mb={1} p={1} key={lom.id}>
          <h2>{lom.name}</h2>
          <p>{lom.description}</p>
          <MaterialsTable list={lom} isSingleColumn={false} canEdit={false} />
        </Box>
      ))}
    </div>
  ) : null;
}
