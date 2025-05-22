import React from "react";
import {
  DataGrid,
  GridRowSelectionModel,
  useGridApiContext,
  GridToolbarContainer,
  GridToolbarColumnsButton,
  GridToolbarExportContainer,
  GridSlotProps,
  GridRowId,
} from "@mui/x-data-grid";
import {
  type Identifier,
  useIdentifiersListing,
  useIdentifiersRefresh,
} from "../../useIdentifiers";
import { DataGridColumn } from "../../../util/table";
import { toTitleCase } from "../../../util/Util";
import GlobalId from "../../../components/GlobalId";
import LinkableRecordFromGlobalId from "../../../stores/models/LinkableRecordFromGlobalId";
import Box from "@mui/material/Box";
import MenuItem from "@mui/material/MenuItem";
import MenuWithSelectedState from "../../../components/MenuWithSelectedState";
import AccentMenuItem from "../../../components/AccentMenuItem";
import { DataGridWithRadioSelection } from "@/components/DataGridWithRadioSelection";
import RsSet from "../../../util/set";

declare module "@mui/x-data-grid" {
  interface ToolbarPropsOverrides {
    setColumnsMenuAnchorEl: (anchorEl: HTMLElement | null) => void;
    state: "draft" | "findable" | "registered" | null;
    setState: (newState: "draft" | "findable" | "registered" | null) => void;
    isAssociated: boolean | null;
    setIsAssociated: (newIsAssociated: boolean | null) => void;
  }
}

function Toolbar({
  setColumnsMenuAnchorEl,
  state,
  setState,
  isAssociated,
  setIsAssociated,
}: GridSlotProps["toolbar"]): React.ReactNode {
  const apiRef = useGridApiContext();

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

  const linkedItemStateLabel = (() => {
    if (isAssociated === null) return "All";
    if (isAssociated === true) return "Yes";
    return "No";
  })();

  return (
    <GridToolbarContainer sx={{ width: "100%" }}>
      <MenuWithSelectedState label="State" currentState={state ?? "All"}>
        <AccentMenuItem
          title="All"
          subheader="Show all IGSN IDs"
          onClick={() => {
            setState(null);
          }}
          current={state === null}
        />
        <AccentMenuItem
          title="Draft"
          subheader="A newly created IGSN ID without any public metadata."
          onClick={() => {
            setState("draft");
          }}
          current={state === "draft"}
        />
        <AccentMenuItem
          title="Findable"
          subheader="A published, searchable IGSN ID with a public landing page."
          onClick={() => {
            setState("findable");
          }}
          current={state === "findable"}
        />
        <AccentMenuItem
          title="Registered"
          subheader="An IGSN ID that has been retracted from public access."
          onClick={() => {
            setState("registered");
          }}
          current={state === "registered"}
        />
      </MenuWithSelectedState>
      <MenuWithSelectedState
        label="Linked Item"
        currentState={linkedItemStateLabel}
      >
        <AccentMenuItem
          title="All Identifiers"
          onClick={() => {
            setIsAssociated(null);
          }}
          current={isAssociated === null}
        />
        <AccentMenuItem
          title="No Linked Item"
          onClick={() => {
            setIsAssociated(false);
          }}
          current={isAssociated === false}
        />
        <AccentMenuItem
          title="Has Linked Item"
          onClick={() => {
            setIsAssociated(true);
          }}
          current={isAssociated === true}
        />
      </MenuWithSelectedState>
      <Box flexGrow={1}></Box>
      <GridToolbarColumnsButton
        ref={(node) => {
          if (node) columnMenuRef.current = node;
        }}
      />
      <GridToolbarExportContainer>
        <MenuItem
          onClick={() => {
            apiRef.current?.exportDataAsCsv({
              allColumns: true,
            });
          }}
        >
          Export to CSV
        </MenuItem>
      </GridToolbarExportContainer>
    </GridToolbarContainer>
  );
}

/**
 * A table listing all of the IGSNs that the current users owns.
 *
 * ====  RACE CONDITIONAL WARNING  ===========================================
 *
 * This component sets the global refreshListing function in the IdentifiersRefresh
 * context. Only ONE instance of IgsnTable should be rendered at a time within
 * the same IdentifiersRefreshProvider.
 *
 * If multiple IgsnTable components are rendered simultaneously:
 * - They will overwrite each other's refreshListing function
 * - Only the last one to run its useEffect will have its refresh function available
 * - Components using useIdentifiersRefresh may trigger the wrong table's refresh
 *
 * If you need multiple tables, consider:
 * 1. Using separate IdentifiersRefreshProvider contexts for each table
 * 2. Modifying the context to support multiple named refresh functions
 * 3. Using a more comprehensive state management solution
 *
 * ============================================================================
 */
export default function IgsnTable({
  selectedIgsns,
  setSelectedIgsns,
  disableMultipleRowSelection = false,
}: {
  /**
   * Whether multiple row selection is disabled. If true, only one identifier
   * can be selected at a time and instead of checkboxes a radio button will be
   * used in the selection column.
   */
  disableMultipleRowSelection?: boolean;

  /**
   * The selected identifiers. The order of the array is not significant. If
   * `disableMultipleRowSelection` is true, only the first identifier in the array
   * will be used.
   */
  selectedIgsns: RsSet<Identifier>;

  /**
   * Callback to update the selected identifiers. If `disableMultipleRowSelection`
   * is true, the passed array will either be empty or contain only one
   * identifier.
   */
  setSelectedIgsns: (newlySelectedIgsns: RsSet<Identifier>) => void;
}): React.ReactNode {
  const [state, setState] = React.useState<
    "draft" | "findable" | "registered" | null
  >(null);
  const [isAssociated, setIsAssociated] = React.useState<boolean | null>(null);
  const { identifiers, loading, refreshListing } = useIdentifiersListing({
    state,
    isAssociated,
  });
  const { setRefreshListing } = useIdentifiersRefresh();
  React.useEffect(() => {
    setRefreshListing(refreshListing);
    return () => setRefreshListing(null);
  }, [refreshListing, setRefreshListing]);

  const [columnsMenuAnchorEl, setColumnsMenuAnchorEl] =
    React.useState<HTMLElement | null>(null);

  const common = {
    rows: identifiers,
    columns: [
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
                record={new LinkableRecordFromGlobalId(row.associatedGlobalId)}
                onClick={() => {}}
              />
            );
          },
        }
      ),
    ],
    loading,
    initialState: {
      columns: {},
    },
    density: "compact" as const,
    disableColumnFilter: true,
    hideFooter: true,
    autoHeight: true,
    slots: {
      pagination: null,
      toolbar: Toolbar,
    },
    slotProps: {
      toolbar: {
        setColumnsMenuAnchorEl,
        state,
        setState,
        isAssociated,
        setIsAssociated,
      },
      panel: {
        anchorEl: columnsMenuAnchorEl,
      },
    },
    localeText: {
      noRowsLabel: "No IGSN IDs",
    },
  };

  if (disableMultipleRowSelection)
    return (
      <DataGridWithRadioSelection
        {...common}
        getRowId={(row) => row.doi}
        selectRadioAriaLabelFunc={() => ""}
        onSelectionChange={(doi: GridRowId) => {
          const selectedIdentifier = identifiers.find((id) => id.doi === doi);
          if (selectedIdentifier) {
            setSelectedIgsns(new RsSet([selectedIdentifier]));
          } else {
            throw new Error("Selected identifier not found");
          }
        }}
        selectedRowId={selectedIgsns.only.map(({ doi }) => doi).orElse(null)}
      />
    );
  return (
    <DataGrid
      {...common}
      getRowId={(row) => row.doi}
      checkboxSelection={true}
      rowSelectionModel={selectedIgsns.map((id) => id.doi).toArray()}
      onRowSelectionModelChange={(ids: GridRowSelectionModel) => {
        const selectedIdentifiers = identifiers.filter((id) =>
          ids.includes(id.doi)
        );
        setSelectedIgsns(new RsSet(selectedIdentifiers));
      }}
    />
  );
}
