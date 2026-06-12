import Box from "@mui/material/Box";
// biome-ignore lint/style/useImportType: initial biome migration
import React from "react";
import type { ListOfMaterials } from "../../stores/models/MaterialsModel";
import MaterialsTable from "./MaterialsTable";

type PrintedMaterialsListingArgs = {
  listsOfMaterials: Array<ListOfMaterials> | null;
};

/*
 * When a structured document is printed, this component is used to render the
 *  list of materials, as a table, onto the page rather than as it normally
 *  appears in a dialog.
 */
export default function PrintedMaterialsListing({ listsOfMaterials }: PrintedMaterialsListingArgs): React.ReactNode {
  return listsOfMaterials ? (
    <Box sx={{ display: "none", "@media print": { display: "initial" } }}>
      {listsOfMaterials.map((lom) => (
        <Box sx={{ border: 1, mb: 1, p: 1 }} key={lom.id}>
          <h2>{lom.name}</h2>
          <p>{lom.description}</p>
          <MaterialsTable list={lom} isSingleColumn={false} canEdit={false} />
        </Box>
      ))}
    </Box>
  ) : null;
}
