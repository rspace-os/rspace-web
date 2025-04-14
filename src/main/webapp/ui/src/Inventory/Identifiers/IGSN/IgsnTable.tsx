import React from "react";
import {
  DataGrid,
  GridRowSelectionModel,
  GridToolbarContainer,
  GridToolbarColumnsButton,
  GridSlotProps,
} from "@mui/x-data-grid";
import { type Identifier, useIdentifiersListing } from "../../useIdentifiers";
import { DataGridColumn } from "../../../util/table";
import { toTitleCase } from "../../../util/Util";
import GlobalId from "../../../components/GlobalId";
import LinkableRecordFromGlobalId from "../../../stores/models/LinkableRecordFromGlobalId";
import Box from "@mui/material/Box";

declare module "@mui/x-data-grid" {
  interface ToolbarPropsOverrides {
    setColumnsMenuAnchorEl: (anchorEl: HTMLElement | null) => void;
  }
}

function Toolbar({
  setColumnsMenuAnchorEl,
}: GridSlotProps["toolbar"]): React.ReactNode {
  /**
   * The columns menu can be opened by either tapping the "Columns" toolbar
   * button or by tapping the "Manage columns" menu item in each column's menu,
   * logic that is handled my MUI. We provide a custom `anchorEl` so that the
   * menu is positioned beneath the "Columns" toolbar button to be consistent
   * with the other toolbar menus, otherwise is appears far to the left. Rather
   * than having to hook into the logic that triggers the opening of the
   * columns menu in both places, we just set the `anchorEl` pre-emptively.
   */
  const columnMenuRef = React.useRef<HTMLButtonElement>();
  React.useEffect(() => {
    if (columnMenuRef.current) setColumnsMenuAnchorEl(columnMenuRef.current);
  }, [setColumnsMenuAnchorEl]);

  return (
    <GridToolbarContainer sx={{ width: "100%" }}>
      <Box flexGrow={1}></Box>
      <GridToolbarColumnsButton
        ref={(node) => {
          if (node) columnMenuRef.current = node;
        }}
      />
    </GridToolbarContainer>
  );
}

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
  const [columnsMenuAnchorEl, setColumnsMenuAnchorEl] =
    React.useState<HTMLElement | null>(null);

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
      getRowId={(row) => row.doi}
      initialState={{
        columns: {},
      }}
      density="compact"
      disableColumnFilter
      hideFooter
      autoHeight
      slots={{
        pagination: null,
        toolbar: Toolbar,
      }}
      slotProps={{
        toolbar: {
          setColumnsMenuAnchorEl,
        },
        panel: {
          anchorEl: columnsMenuAnchorEl,
        },
      }}
      localeText={{
        noRowsLabel: "No IGSN IDs",
      }}
    />
  );
}
