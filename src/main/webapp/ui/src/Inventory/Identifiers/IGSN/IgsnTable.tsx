import ClearIcon from "@mui/icons-material/Clear";
import SearchIcon from "@mui/icons-material/Search";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import IconButton from "@mui/material/IconButton";
import InputAdornment from "@mui/material/InputAdornment";
import Popover from "@mui/material/Popover";
import TextField from "@mui/material/TextField";
import {
  DataGrid,
  type DataGridProps,
  type GridRowId,
  type GridRowSelectionModel,
  type GridSlotProps,
  GridToolbarColumnsButton,
  GridToolbarContainer,
  GridToolbarExportContainer,
  useGridApiContext,
} from "@mui/x-data-grid";
import React from "react";
import { useTranslation } from "react-i18next";
import { DataGridWithRadioSelection } from "@/components/DataGridWithRadioSelection";
import ExportMenuItem from "@/components/ExportMenuItem";
import SearchBarcodeIcon from "../../../assets/graphics/SearchBarcode";
import AccentMenuItem from "../../../components/AccentMenuItem";
import GlobalId from "../../../components/GlobalId";
import MenuWithSelectedState from "../../../components/MenuWithSelectedState";
import useDebounce from "../../../hooks/ui/useDebounce";
import LinkableRecordFromGlobalId from "../../../stores/models/LinkableRecordFromGlobalId";
import RsSet from "../../../util/set";
import { DataGridColumn } from "../../../util/table";
import BarcodeScanner from "../../components/BarcodeScanner/AllBarcodeScanner";
import { type Identifier, useIdentifiersListing, useIdentifiersRefresh } from "../../useIdentifiers";

declare module "@mui/x-data-grid" {
  interface ToolbarPropsOverrides {
    setColumnsMenuAnchorEl: (anchorEl: HTMLElement | null) => void;
    state: "draft" | "findable" | "registered" | null;
    setState: (newState: "draft" | "findable" | "registered" | null) => void;
    isAssociated: boolean | null;
    setIsAssociated: (newIsAssociated: boolean | null) => void;
    searchTerm: string;
    setSearchTerm: (newSearchTerm: string) => void;
  }
}

const Panel = ({
  anchorEl,
  children,
  onClose,
}: {
  anchorEl: HTMLElement | null;
  children: React.ReactNode;
  onClose: () => void;
}) => (
  <Popover
    open={Boolean(anchorEl)}
    anchorEl={anchorEl}
    onClose={onClose}
    anchorOrigin={{
      vertical: "bottom",
      horizontal: "center",
    }}
    transformOrigin={{
      vertical: "top",
      horizontal: "center",
    }}
    slotProps={{
      paper: {
        variant: "outlined",
        elevation: 0,
        style: {
          minWidth: 300,
        },
      },
    }}
  >
    {Boolean(anchorEl) && children}
  </Popover>
);
function Toolbar({
  setColumnsMenuAnchorEl,
  state,
  setState,
  isAssociated,
  setIsAssociated,
  searchTerm,
  setSearchTerm,
}: GridSlotProps["toolbar"]): React.ReactNode {
  const { t } = useTranslation("inventory");
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
  const columnMenuRef = React.useRef<HTMLButtonElement | undefined>(undefined);
  React.useEffect(() => {
    if (columnMenuRef.current) setColumnsMenuAnchorEl(columnMenuRef.current);
  }, [setColumnsMenuAnchorEl]);

  const linkedItemStateLabel = (() => {
    if (isAssociated === null) return t("igsnTable.filters.all");
    if (isAssociated === true) return t("igsnTable.filters.linkedItemStates.yes");
    return t("igsnTable.filters.linkedItemStates.no");
  })();
  const stateLabelFor = (identifierState: string) => {
    if (identifierState === "draft") return t("igsnTable.filters.stateOptions.draft.title");
    if (identifierState === "findable") return t("igsnTable.filters.stateOptions.findable.title");
    if (identifierState === "registered") return t("igsnTable.filters.stateOptions.registered.title");
    return identifierState;
  };

  const [localSearchTerm, setLocalSearchTerm] = React.useState(searchTerm);
  const debouncedCallback = React.useCallback((value: string) => {
    setSearchTerm(value);
  }, []);
  const debouncedSetSearchTerm = useDebounce<string>(debouncedCallback, 300);

  const handleSearchChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const value = event.target.value;
    setLocalSearchTerm(value);
    debouncedSetSearchTerm(value);
  };

  const [scannerAnchorEl, setScannerAnchorEl] = React.useState<null | HTMLElement>(null);

  return (
    <GridToolbarContainer sx={{ width: "100%" }}>
      <TextField
        type="search"
        placeholder={t("igsnTable.searchPlaceholder")}
        value={localSearchTerm}
        onChange={handleSearchChange}
        size="small"
        sx={{
          width: 230, // wide enough to show the whole placeholder text
        }}
        slotProps={{
          input: {
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon fontSize="small" />
              </InputAdornment>
            ),
            endAdornment: localSearchTerm ? (
              <InputAdornment position="end">
                <IconButton
                  aria-label={t("search.controls.searchbar.clearSearch")}
                  onClick={() => {
                    setLocalSearchTerm("");
                    setSearchTerm("");
                  }}
                  edge="end"
                  size="small"
                >
                  <ClearIcon fontSize="small" />
                </IconButton>
              </InputAdornment>
            ) : null,
          },
        }}
      />
      <Button
        color="primary"
        onClick={(event) => {
          setScannerAnchorEl(event.currentTarget);
        }}
        startIcon={<SearchBarcodeIcon />}
      >
        {t("igsnTable.scan")}
      </Button>
      <Panel anchorEl={scannerAnchorEl} onClose={() => setScannerAnchorEl(null)}>
        <BarcodeScanner
          onScan={(result) => {
            setLocalSearchTerm(result.rawValue);
            setSearchTerm(result.rawValue);
          }}
          onClose={() => setScannerAnchorEl(null)}
          buttonPrefix={t("igsnTable.searchButtonPrefix")}
        />
      </Panel>
      <MenuWithSelectedState
        label={t("igsnTable.filters.state")}
        currentState={state ? stateLabelFor(state) : t("igsnTable.filters.all")}
        defaultState={t("igsnTable.filters.all")}
      >
        <AccentMenuItem
          title={t("igsnTable.filters.stateOptions.all.title")}
          subheader={t("igsnTable.filters.stateOptions.all.subheader")}
          onClick={() => {
            setState(null);
          }}
          current={state === null}
        />
        <AccentMenuItem
          title={t("igsnTable.filters.stateOptions.draft.title")}
          subheader={t("igsnTable.filters.stateOptions.draft.subheader")}
          onClick={() => {
            setState("draft");
          }}
          current={state === "draft"}
        />
        <AccentMenuItem
          title={t("igsnTable.filters.stateOptions.findable.title")}
          subheader={t("igsnTable.filters.stateOptions.findable.subheader")}
          onClick={() => {
            setState("findable");
          }}
          current={state === "findable"}
        />
        <AccentMenuItem
          title={t("igsnTable.filters.stateOptions.registered.title")}
          subheader={t("igsnTable.filters.stateOptions.registered.subheader")}
          onClick={() => {
            setState("registered");
          }}
          current={state === "registered"}
        />
      </MenuWithSelectedState>
      <MenuWithSelectedState
        label={t("igsnTable.filters.linkedItem")}
        currentState={linkedItemStateLabel}
        defaultState={t("igsnTable.filters.all")}
      >
        <AccentMenuItem
          title={t("igsnTable.filters.all")}
          onClick={() => {
            setIsAssociated(null);
          }}
          current={isAssociated === null}
        />
        <AccentMenuItem
          title={t("igsnTable.filters.noLinkedItem")}
          onClick={() => {
            setIsAssociated(false);
          }}
          current={isAssociated === false}
        />
        <AccentMenuItem
          title={t("igsnTable.filters.hasLinkedItem")}
          onClick={() => {
            setIsAssociated(true);
          }}
          current={isAssociated === true}
        />
      </MenuWithSelectedState>
      <Box sx={{ flexGrow: 1 }}></Box>
      <GridToolbarColumnsButton
        ref={(node) => {
          if (node) columnMenuRef.current = node;
        }}
      />
      <GridToolbarExportContainer>
        <ExportMenuItem
          onClick={() => {
            apiRef.current?.exportDataAsCsv({
              allColumns: true,
            });
          }}
        >
          {t("igsnTable.exportToCsv")}
        </ExportMenuItem>
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
  controlDefaults,
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

  /**
   * Default values for the table controls. If not provided, the controls will
   * be initialized to their default values.
   */
  controlDefaults?: {
    state?: "draft" | "findable" | "registered" | null;
    isAssociated?: boolean | null;
    searchTerm?: string | null;
  };
}): React.ReactNode {
  const { t } = useTranslation("inventory");
  const [state, setState] = React.useState<"draft" | "findable" | "registered" | null>(controlDefaults?.state ?? null);
  const [isAssociated, setIsAssociated] = React.useState<boolean | null>(controlDefaults?.isAssociated ?? null);
  const [searchTerm, setSearchTerm] = React.useState<string>(controlDefaults?.searchTerm ?? "");
  const { identifiers, loading, refreshListing } = useIdentifiersListing({
    state,
    isAssociated,
    searchTerm,
  });
  const { setRefreshListing } = useIdentifiersRefresh();
  const stateLabelFor = (identifierState: string) => {
    if (identifierState === "draft") return t("igsnTable.filters.stateOptions.draft.title");
    if (identifierState === "findable") return t("igsnTable.filters.stateOptions.findable.title");
    if (identifierState === "registered") return t("igsnTable.filters.stateOptions.registered.title");
    return identifierState;
  };
  React.useEffect(() => {
    setRefreshListing(refreshListing);
    return () => setRefreshListing(null);
  }, [refreshListing, setRefreshListing]);

  const [columnsMenuAnchorEl, setColumnsMenuAnchorEl] = React.useState<HTMLElement | null>(null);

  const common: DataGridProps<Identifier> = {
    rows: identifiers,
    columns: [
      DataGridColumn.newColumnWithFieldName<"doi", Identifier>("doi", {
        headerName: t("igsnTable.columns.doi"),
        flex: 1,
        sortable: false,
        resizable: true,
      }),
      DataGridColumn.newColumnWithFieldName<"state", Identifier>("state", {
        headerName: t("igsnTable.columns.state"),
        flex: 1,
        resizable: true,
        sortable: false,
        renderCell: ({ row }) => stateLabelFor(row.state),
      }),
      DataGridColumn.newColumnWithFieldName<"associatedGlobalId", Identifier>("associatedGlobalId", {
        headerName: t("igsnTable.columns.linkedItem"),
        flex: 1,
        resizable: true,
        sortable: false,
        renderCell: ({ row }) => {
          if (row.associatedGlobalId === null) {
            return t("igsnTable.noLinkedItem");
          }
          return <GlobalId record={new LinkableRecordFromGlobalId(row.associatedGlobalId)} onClick={() => {}} />;
        },
      }),
    ],
    loading,
    initialState: {
      columns: {},
    },
    density: "compact" as const,
    disableColumnFilter: true,
    hideFooter: true,
    autoHeight: true,
    showToolbar: true,
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
        searchTerm,
        setSearchTerm,
      },
      panel: {
        target: columnsMenuAnchorEl,
      },
    },
    localeText: {
      noRowsLabel: t("igsnTable.noRows"),
    },
  };

  const selectedRowIds = React.useMemo(
    () => selectedIgsns.map((identifier) => identifier.doi).toArray(),
    [selectedIgsns],
  );

  if (disableMultipleRowSelection)
    return (
      <DataGridWithRadioSelection
        {...common}
        getRowId={(row: Identifier) => row.doi}
        selectRadioAriaLabelFunc={() => t("igsnTable.selectIgsn")}
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
      getRowId={(row: Identifier) => row.doi}
      checkboxSelection={true}
      rowSelectionModel={{
        type: "include",
        ids: new Set(selectedRowIds),
      }}
      onRowSelectionModelChange={(selectionModel: GridRowSelectionModel) => {
        const selectedIdentifiers =
          selectionModel.type === "include"
            ? identifiers.filter((identifier) => selectionModel.ids.has(identifier.doi))
            : identifiers;
        setSelectedIgsns(new RsSet(selectedIdentifiers));
      }}
    />
  );
}
