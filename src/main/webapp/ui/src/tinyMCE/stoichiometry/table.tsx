import React, { useCallback } from "react";
import {
  DataGrid,
  GridSlotProps,
  GridToolbarColumnsButton,
  GridToolbarContainer,
  GridToolbarExportContainer,
  useGridApiContext,
} from "@mui/x-data-grid";
import CircularProgress from "@mui/material/CircularProgress";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import Chip from "@mui/material/Chip";
import Radio from "@mui/material/Radio";
import MenuItem from "@mui/material/MenuItem";
import Button from "@mui/material/Button";
import AddIcon from "@mui/icons-material/Add";
import { lighten, styled, ThemeProvider, useTheme } from "@mui/material/styles";
import {
  calculateUpdatedMolecules,
  calculateActualMoles,
} from "./calculations";
import useStoichiometry, {
  type StoichiometryResponse,
  type StoichiometryMolecule,
  type RsChemElement,
  StoichiometryRequest,
} from "../../hooks/api/useStoichiometry";
import { doNotAwait } from "../../util/Util";
import { DataGridColumn } from "../../util/table";
import AddReagentDialog from "./AddReagentDialog";
import { ACCENT_COLOR as PUBCHEM_ACCENT_COLOR } from "@/assets/branding/pubchem";
import Menu from "@mui/material/Menu";
import AccentMenuItem from "@/components/AccentMenuItem";
import CardMedia from "@mui/material/CardMedia";
import { ACCENT_COLOR as GALLERY_COLOR } from "@/assets/branding/rspace/gallery";
import { ACCENT_COLOR as CHEMISTRY_COLOR } from "@/assets/branding/chemistry";
import FileIcon from "@mui/icons-material/InsertDriveFile";
import EditIcon from "@mui/icons-material/Edit";
import PubChemLogo from "../../assets/branding/pubchem/logo.svg";
import CompoundSearchDialog from "../pubchem/CompoundSearchDialog";
import createAccentedTheme from "@/accentedTheme";

declare module "@mui/x-data-grid" {
  interface ToolbarPropsOverrides {
    setColumnsMenuAnchorEl: (anchorEl: HTMLElement | null) => void;
    onAddReagent: (smilesString: string, name: string | null) => void;
    editable: boolean;
  }
}

export interface StoichiometryTableRef {
  save: () => Promise<void>;
  delete: () => Promise<void>;
}

const StyledMenu = styled(Menu)(({ open }) => ({
  "& .MuiPaper-root": {
    ...(open
      ? {
          transform: "translate(0px, 4px) !important",
        }
      : {}),
  },
}));

const RoleChip = ({ role }: { role: string }) => {
  const getRoleColor = (role: string) => {
    switch (role.toLowerCase()) {
      case "reactant":
        return { color: "#1566b7", backgroundColor: "#f5fbfe" }; // Blue
      case "product":
        return { color: "#2e7d32", backgroundColor: "#e8f5e9" }; // Green
      case "catalyst":
        return { color: "#7b1fa2", backgroundColor: "#f3e5f5" }; // Purple
      case "agent":
        return { color: "#ed6c02", backgroundColor: "#fff3e0" }; // Orange
      default:
        return { color: "#616161", backgroundColor: "#f5f5f5" }; // Grey
    }
  };

  const { color, backgroundColor } = getRoleColor(role);

  return (
    <Chip
      label={role.toLowerCase()}
      size="small"
      sx={{
        color: `${color} !important`,
        backgroundColor: `${backgroundColor} !important`,
        border: `1px solid ${color}`,
        fontWeight: 500,
        textTransform: "lowercase",
        "&.MuiChip-filled": {
          backgroundColor: `${backgroundColor} !important`,
          border: `1px solid ${color}`,
        },
      }}
    />
  );
};

function Toolbar({
  setColumnsMenuAnchorEl,
  onAddReagent,
  editable,
}: GridSlotProps["toolbar"]): React.ReactNode {
  const apiRef = useGridApiContext();
  const theme = useTheme();
  const [addReagantMenuAnchorEl, setAddReagentMenuAnchorEl] =
    React.useState<HTMLButtonElement | null>(null);
  const [addReagentSmilesDialogOpen, setAddReagentSmilesDialogOpen] =
    React.useState(false);
  const [pubchemDialogOpen, setPubchemDialogOpen] = React.useState(false);

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
    <>
      <GridToolbarContainer sx={{ mr: `${theme.spacing(0.5)} !important` }}>
        {editable && (
          <>
            <Button
              startIcon={<AddIcon />}
              onClick={(e) => setAddReagentMenuAnchorEl(e.currentTarget)}
              size="small"
              sx={{ mr: 1 }}
            >
              Add Reagent
            </Button>
            <StyledMenu
              open={Boolean(addReagantMenuAnchorEl)}
              anchorEl={addReagantMenuAnchorEl}
              onClose={() => setAddReagentMenuAnchorEl(null)}
              MenuListProps={{
                disablePadding: true,
                "aria-label": "add reagent menu",
              }}
            >
              <AccentMenuItem
                title="PubChem"
                subheader="Import compound from PubChem"
                backgroundColor={PUBCHEM_ACCENT_COLOR.background}
                foregroundColor={PUBCHEM_ACCENT_COLOR.backgroundContrastText}
                avatar={<CardMedia image={PubChemLogo} />}
                onClick={() => {
                  setPubchemDialogOpen(true);
                  setAddReagentMenuAnchorEl(null);
                }}
              />

              <AccentMenuItem
                title="Gallery"
                subheader="Import compound from Gallery"
                backgroundColor={GALLERY_COLOR.main}
                foregroundColor={GALLERY_COLOR.contrastText}
                avatar={<FileIcon sx={{ width: "28px", height: "28px" }} />}
                onClick={() => {
                  //TODO: open Gallery picker
                  setAddReagentMenuAnchorEl(null);
                }}
              />
              <AccentMenuItem
                title="Manually"
                subheader="Manually enter SMILES"
                backgroundColor={CHEMISTRY_COLOR.background}
                foregroundColor={CHEMISTRY_COLOR.backgroundContrastText}
                avatar={<EditIcon />}
                onClick={() => {
                  setAddReagentSmilesDialogOpen(true);
                  setAddReagentMenuAnchorEl(null);
                }}
              />
            </StyledMenu>
            <AddReagentDialog
              open={addReagentSmilesDialogOpen}
              onClose={() => {
                setAddReagentSmilesDialogOpen(false);
              }}
              onAddReagent={onAddReagent}
            />
            <ThemeProvider theme={createAccentedTheme(PUBCHEM_ACCENT_COLOR)}>
              <CompoundSearchDialog
                open={pubchemDialogOpen}
                onClose={() => {
                  setPubchemDialogOpen(false);
                  setAddReagentMenuAnchorEl(null);
                }}
                onCompoundsSelected={() => {
                  //TODO: save compound
                }}
                title="Insert from PubChem"
                submitButtonText="Insert"
                showPubChemInfo
                allowMultipleSelection
              />
            </ThemeProvider>
          </>
        )}
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
    </>
  );
}

const StoichiometryTable = React.forwardRef<
  StoichiometryTableRef,
  {
    chemId: number | null;
    editable?: boolean;
    onChangesUpdate?: (hasChanges: boolean) => void;
  }
>(function StoichiometryTable(
  { chemId, editable = false, onChangesUpdate },
  ref,
) {
  const { getStoichiometry, updateStoichiometry, deleteStoichiometry } =
    useStoichiometry();
  const theme = useTheme();
  const [data, setData] = React.useState<StoichiometryResponse | null>(null);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [allMolecules, setAllMolecules] = React.useState<
    ReadonlyArray<StoichiometryMolecule>
  >([]);
  const [columnsMenuAnchorEl, setColumnsMenuAnchorEl] =
    React.useState<HTMLElement | null>(null);

  React.useEffect(() => {
    setLoading(true);
    setError(null);
    doNotAwait(async () => {
      try {
        if (!chemId) throw new Error("chemId is required");
        const result = await getStoichiometry({ chemId });
        setData(result);
      } catch (e) {
        console.error(e);
        setError("Failed to load stoichiometry data");
      } finally {
        setLoading(false);
      }
    })();
  }, [chemId]);

  React.useEffect(() => {
    if (data?.molecules) {
      const molecules = data.molecules;
      const hasLimitingReagent = molecules.some(
        (m) => m.limitingReagent && m.role.toLowerCase() === "reactant",
      );

      // Usability enhancement: default first reactant as limiting reagent since it's usually the limiting one
      if (!hasLimitingReagent) {
        const firstReactant = molecules.find(
          (m) => m.role.toLowerCase() === "reactant",
        );

        if (firstReactant) {
          const updatedMolecules = molecules.map((m) =>
            m.id === firstReactant.id ? { ...m, limitingReagent: true } : m,
          );
          setAllMolecules(updatedMolecules);
        } else {
          setAllMolecules(molecules);
        }
      } else {
        setAllMolecules(molecules);
      }
    }
  }, [data]);

  React.useImperativeHandle(
    ref,
    () => ({
      save: async () => {
        if (!data || !data.id) {
          throw new Error("No stoichiometry data to save");
        }

        const updatedData: StoichiometryRequest = {
          ...data,
          molecules: allMolecules.map((m) => {
            if (m.id >= 0) return m;
            const smiles = m.rsChemElement.smilesString;
            if (!smiles)
              throw new Error("New reagents must have a SMILES string");
            return { role: "AGENT", smiles, name: m.name };
          }),
        };

        await updateStoichiometry({
          stoichiometryId: data.id,
          stoichiometryData: updatedData,
        });
        onChangesUpdate?.(false);
      },
      delete: async () => {
        if (!data || !data.id) {
          throw new Error("No stoichiometry data to delete");
        }
        await deleteStoichiometry({ stoichiometryId: data.id });
        setData(null);
        setAllMolecules([]);
        onChangesUpdate?.(false);
      },
    }),
    [data, allMolecules, updateStoichiometry],
  );

  const handleAddReagent = useCallback(
    (smilesString: string, name: string | null) => {
      // Generate a temporary unique ID for the new molecule (negative to distinguish from server IDs)
      const tempId = -(allMolecules.length + 1);

      // Create a mock RsChemElement for the new reagent
      const mockRsChemElement: RsChemElement = {
        id: tempId,
        parentId: null,
        ecatChemFileId: null,
        dataImage: null,
        chemElements: smilesString,
        smilesString: smilesString,
        chemId: null,
        reactionId: null,
        rgroupId: null,
        metadata: null,
        chemElementsFormat: "SMILES",
        creationDate: Date.now(),
        imageFileProperty: null,
      };

      // Create new molecule with empty molecular weight (will be filled after save)
      const newMolecule: StoichiometryMolecule = {
        id: tempId,
        rsChemElement: mockRsChemElement,
        role: "agent",
        formula: "", // Will be calculated when saved
        name: name,
        smiles: smilesString,
        coefficient: 1,
        molecularWeight: 0, // Empty until save
        mass: null,
        moles: null,
        expectedAmount: null,
        actualAmount: null,
        actualYield: null,
        limitingReagent: false,
        notes: null,
      };

      const updatedMolecules = [...allMolecules, newMolecule];
      setAllMolecules(updatedMolecules);
      onChangesUpdate?.(true);
    },
    [allMolecules, onChangesUpdate],
  );

  const limitingReagent = allMolecules.find(
    (m) => m.limitingReagent && m.role.toLowerCase() === "reactant",
  );

  const columns = [
    DataGridColumn.newColumnWithFieldName<"name", StoichiometryMolecule>(
      "name",
      {
        headerName: "Name",
        sortable: false,
        flex: 1.5,
        renderCell: (params) => {
          if (params.row.id < 0 && params.row.name === null)
            return "New reagent";
          /* I think this is a bug; the server should be assigning missing names */
          // if (params.row.name === null)
          // throw new Error("Name should not be missing");
          return params.row.name ?? "MISSING";
        },
      },
    ),
    DataGridColumn.newColumnWithFieldName<"role", StoichiometryMolecule>(
      "role",
      {
        headerName: "Role",
        sortable: false,
        flex: 1,
        renderCell: (params) => <RoleChip role={params.value || ""} />,
      },
    ),
    DataGridColumn.newColumnWithFieldName<
      "limitingReagent",
      StoichiometryMolecule
    >("limitingReagent", {
      headerName: "Limiting Reagent",
      sortable: false,
      flex: 1,
      align: "center",
      renderCell: (params) =>
        params.row.role.toLowerCase() === "reactant" ? (
          <Radio
            checked={params.row.limitingReagent || false}
            disabled={!editable}
            inputProps={{
              "aria-label": `Select ${params.row.name} as limiting reagent`,
            }}
            onChange={(e) => {
              if (e.target.checked && editable) {
                const updatedRow = { ...params.row, limitingReagent: true };
                const newMolecules = calculateUpdatedMolecules(
                  allMolecules,
                  updatedRow,
                );
                setAllMolecules(newMolecules);
                onChangesUpdate?.(true);
              }
            }}
          />
        ) : (
          <>&mdash;</>
        ),
    }),
    DataGridColumn.newColumnWithFieldName<"coefficient", StoichiometryMolecule>(
      "coefficient",
      {
        headerName: "Equivalent",
        sortable: false,
        flex: 1,
        // @ts-expect-error It's not documented or typed, but editable can be a function
        editable: (params) => {
          if (limitingReagent && params.id === limitingReagent.id) {
            return false;
          }
          return editable;
        },
        type: "number",
        headerAlign: "left",
        cellClassName: (params) => {
          if (limitingReagent && params.id === limitingReagent.id) {
            return "stoichiometry-disabled-cell";
          }
          return "";
        },
      },
    ),
    DataGridColumn.newColumnWithFieldName<
      "molecularWeight",
      StoichiometryMolecule
    >("molecularWeight", {
      headerName: "Molecular Weight (g/mol)",
      sortable: false,
      flex: 1.2,
      type: "number",
      headerAlign: "left",
    }),
    DataGridColumn.newColumnWithFieldName<"mass", StoichiometryMolecule>(
      "mass",
      {
        headerName: "Mass (g)",
        sortable: false,
        flex: 1,
        headerAlign: "left",
        // @ts-expect-error It's not documented or typed, but editable can be a function
        editable: (params) => {
          if (limitingReagent) {
            return editable && params.id === limitingReagent.id;
          }
          return editable;
        },
        type: "number",
        renderCell: (params) => params.value ?? <>&#8212;</>,
        cellClassName: (params) => {
          if (limitingReagent && params.id !== limitingReagent.id) {
            return "stoichiometry-disabled-cell";
          }
          return "";
        },
      },
    ),
    DataGridColumn.newColumnWithFieldName<"moles", StoichiometryMolecule>(
      "moles",
      {
        headerName: "Moles (mol)",
        sortable: false,
        flex: 1,
        headerAlign: "left",
        // @ts-expect-error It's not documented or typed, but editable can be a function
        editable: (params) => {
          if (limitingReagent) {
            return editable && params.id === limitingReagent.id;
          }
          return editable;
        },
        type: "number",
        renderCell: (params) => params.value ?? <>&#8212;</>,
        cellClassName: (params) => {
          if (limitingReagent && params.id !== limitingReagent.id) {
            return "stoichiometry-disabled-cell";
          }
          return "";
        },
      },
    ),
    DataGridColumn.newColumnWithFieldName<
      "actualAmount",
      StoichiometryMolecule
    >("actualAmount", {
      headerName: "Actual Mass (g)",
      sortable: false,
      flex: 1,
      headerAlign: "left",
      editable: editable,
      type: "number",
      renderCell: (params) => params.value ?? <>&mdash;</>,
    }),
    DataGridColumn.newColumnWithValueGetter<
      "actualMoles",
      StoichiometryMolecule,
      number | null
    >(
      "actualMoles",
      (row: StoichiometryMolecule) =>
        calculateActualMoles({
          actualAmount: row.actualAmount,
          molecularWeight: row.molecularWeight,
        }),
      {
        headerName: "Actual Moles (mol)",
        sortable: false,
        headerAlign: "left",
        flex: 1,
        type: "number",
        editable: false,
        renderCell: (params) => {
          return params.value !== null ? params.value : <>&mdash;</>;
        },
      },
    ),
    DataGridColumn.newColumnWithFieldName<"actualYield", StoichiometryMolecule>(
      "actualYield",
      {
        headerName: "Yield/Excess (%)",
        sortable: false,
        headerAlign: "left",
        flex: 1,
        type: "number",
        editable: false,
        renderCell: (params) => {
          // Don't show yield for limiting reagent
          if (limitingReagent && params.id === limitingReagent.id) {
            return <>&#8212;</>;
          }
          const value = params.value;
          return value !== null && value !== undefined ? (
            `${value}%`
          ) : (
            <>&#8212;</>
          );
        },
      },
    ),
    DataGridColumn.newColumnWithFieldName<"notes", StoichiometryMolecule>(
      "notes",
      {
        headerName: "Notes",
        sortable: false,
        flex: 1.5,
        editable: editable,
        type: "string",
        renderCell: (params) => params.value ?? <>&mdash;</>,
      },
    ),
  ];

  if (loading) {
    return (
      <Box
        display="flex"
        flexDirection="column"
        justifyContent="center"
        alignItems="center"
        minHeight={100}
        my={2}
        gap={1}
      >
        <CircularProgress size={24} />
        <Typography variant="body2" color="textSecondary">
          Loading stoichiometry table...
        </Typography>
      </Box>
    );
  }

  if (error) {
    return (
      <Box
        display="flex"
        justifyContent="center"
        alignItems="center"
        minHeight={100}
        my={2}
      >
        <Typography color="error" variant="body2">
          {error}
        </Typography>
      </Box>
    );
  }

  if (!data || !data.molecules?.length) {
    return (
      <Box
        display="flex"
        justifyContent="center"
        alignItems="center"
        minHeight={100}
        my={2}
      >
        <Typography variant="body2">No stoichiometry data available</Typography>
      </Box>
    );
  }

  return (
    <DataGrid
      rows={allMolecules ?? []}
      columns={columns}
      autoHeight
      hideFooter
      disableColumnFilter
      getRowId={(row) => row.id}
      processRowUpdate={(newRow) => {
        const newMolecules = calculateUpdatedMolecules(allMolecules, newRow);
        setAllMolecules(newMolecules);
        onChangesUpdate?.(true);
        return newMolecules.find((m) => m.id === newRow.id) || newRow;
      }}
      slots={{
        toolbar: Toolbar,
      }}
      slotProps={{
        toolbar: {
          setColumnsMenuAnchorEl,
          onAddReagent: handleAddReagent,
          editable,
        },
        panel: {
          anchorEl: columnsMenuAnchorEl,
        },
      }}
      sx={{
        border: "none",
        "& .MuiDataGrid-columnHeaders": {
          backgroundColor: "#f8f9fa",
          borderBottom: "2px solid #e0e0e0",
        },
        "& .MuiDataGrid-cell": {
          borderBottom: "1px solid #f0f0f0",
        },
        "& .MuiDataGrid-row:hover": {
          backgroundColor: "#f8f9fa",
        },
        "& .stoichiometry-disabled-cell": {
          backgroundColor: `${lighten(theme.palette.primary.background, 0.3)} !important`,
          color: `${theme.palette.primary.contrastText} !important`,
          fontStyle: "italic",
          "&:hover": {
            backgroundColor: `${theme.palette.action.hover} !important`,
          },
        },
      }}
    />
  );
});

export default StoichiometryTable;
