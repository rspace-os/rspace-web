import React from "react";
import { DataGrid, GridRowSelectionModel } from "@mui/x-data-grid";
import { type Identifier, useIdentifiersListing } from "../../useIdentifiers";
import { DataGridColumn } from "../../../util/table";
import { toTitleCase } from "../../../util/Util";
import GlobalId from "../../../components/GlobalId";
import LinkableRecordFromGlobalId from "../../../stores/models/LinkableRecordFromGlobalId";

/**
 * A table listing all of the IGSNs that the current users owns.
 */
export default function IgsnTable({
  selectedIgsns,
  setSelectedIgsns,
}: {
  selectedIgsns: ReadonlyArray<Identifier>;
  setSelectedIgsns: (newlySelectedIgsns: ReadonlyArray<Identifier>) => void;
}): React.ReactNode {
  const [state] = React.useState<"draft" | "findable" | "registered" | null>(
    null
  );
  const [isAssociated] = React.useState<boolean | null>(null);
  const { identifiers, loading } = useIdentifiersListing({
    state,
    isAssociated,
  });
  return (
    <DataGrid
      rows={identifiers}
      columns={[
        DataGridColumn.newColumnWithFieldName<"doi", Identifier>("doi", {
          headerName: "DOI",
          flex: 1,
          sortable: false,
          resizable: true,
        }),
        DataGridColumn.newColumnWithFieldName<"state", Identifier>("state", {
          headerName: "State",
          flex: 1,
          resizable: true,
          sortable: false,
          renderCell: ({ row }) => toTitleCase(row.state),
        }),
        DataGridColumn.newColumnWithFieldName<"associatedGlobalId", Identifier>(
          "associatedGlobalId",
          {
            headerName: "Linked Item",
            flex: 1,
            resizable: true,
            sortable: false,
            renderCell: ({ row }) => {
              if (row.associatedGlobalId === null) {
                return "None";
              }
              return (
                <GlobalId
                  record={
                    new LinkableRecordFromGlobalId(row.associatedGlobalId)
                  }
                  onClick={() => {}}
                />
              );
            },
          }
        ),
      ]}
      loading={loading}
      checkboxSelection
      rowSelectionModel={selectedIgsns.map((id) => id.doi)}
      onRowSelectionModelChange={(ids: GridRowSelectionModel) => {
        const selectedIdentifiers = identifiers.filter((id) =>
          ids.includes(id.doi)
        );
        setSelectedIgsns(selectedIdentifiers);
      }}
    />
  );
}
