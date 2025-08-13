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
import { Dialog } from "../../components/DialogBoundary";
import DialogContent from "@mui/material/DialogContent";
import Result from "@/util/result";
import { LandmarksProvider } from "@/components/LandmarksContext";
import GalleryPicker from "../../eln/gallery/picker";
import { MemoryRouter } from "react-router-dom";
import * as Parsers from "../../util/parsers";
import { filenameExceptExtension } from "@/util/files";

declare module "@mui/x-data-grid" {
  interface ToolbarPropsOverrides {
    setColumnsMenuAnchorEl: (anchorEl: HTMLElement | null) => void;
    onAddReagent: (smilesString: string, name: string) => Promise<void>;
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
  const [galleryDialogOpen, setGalleryDialogOpen] = React.useState(false);

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
                  setGalleryDialogOpen(true);
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
                onCompoundsSelected={doNotAwait(async (compounds) => {
                  for (const c of compounds) {
                    await onAddReagent(c.smiles, c.name);
                  }
                })}
                title="Insert from PubChem"
                submitButtonText="Insert"
                showPubChemInfo
                allowMultipleSelection
              />
            </ThemeProvider>
            {galleryDialogOpen && (
              <MemoryRouter>
                <LandmarksProvider>
                  <GalleryPicker
                    open={true}
                    onClose={() => {
                      setGalleryDialogOpen(false);
                    }}
                    onSubmit={doNotAwait(async (files) => {
                      for (const file of files) {
                        await Parsers.getValueWithKey("chemString")(
                          file.metadata,
                        )
                          .flatMap(Parsers.isString)
                          .doAsync((smiles) => {
                            return onAddReagent(
                              smiles,
                              filenameExceptExtension(file.name),
                            );
                          });
                      }
                      setGalleryDialogOpen(false);
                    })}
                    validateSelection={(file) => {
                      if (file.type !== "Chemistry")
                        return Result.Error([
                          new Error(
                            "Only chemistry files can be added to stoichiometry tables",
                          ),
                        ]);
                      return Result.Ok(null);
                    }}
                  />
                </LandmarksProvider>
              </MemoryRouter>
            )}
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

function LoadingDialog({
  open,
  message = "Loading molecule information...",
}: {
  open: boolean;
  message?: string;
}): React.ReactNode {
  return (
    <Dialog
      open={open}
      disableEscapeKeyDown
      sx={{
        "& .MuiDialog-paper": {
          minWidth: 300,
        },
      }}
    >
      <DialogContent>
        <Box
          display="flex"
          flexDirection="column"
          justifyContent="center"
          alignItems="center"
          py={3}
          gap={2}
        >
          <CircularProgress size={40} />
          <Typography variant="body1" textAlign="center">
            {message}
          </Typography>
        </Box>
      </DialogContent>
    </Dialog>
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
  const {
    getStoichiometry,
    updateStoichiometry,
    deleteStoichiometry,
    getMoleculeInfo,
  } = useStoichiometry();
  const theme = useTheme();
  const [data, setData] = React.useState<StoichiometryResponse | null>(null);
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string | null>(null);
  const [allMolecules, setAllMolecules] = React.useState<
    ReadonlyArray<StoichiometryMolecule>
  >([]);
  const [columnsMenuAnchorEl, setColumnsMenuAnchorEl] =
    React.useState<HTMLElement | null>(null);
  const [moleculeInfoLoading, setMoleculeInfoLoading] = React.useState(false);

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
            if (!m.name) throw new Error("New reagents must have a name");
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
    async (smilesString: string, name: string) => {
      // Prevent concurrent requests
      if (moleculeInfoLoading) {
        throw new Error(
          "Please wait for the current reagent to be processed before adding another.",
        );
      }

      setMoleculeInfoLoading(true);

      try {
        const moleculeInfo = await getMoleculeInfo({ smiles: smilesString });

        // Generate a temporary unique ID for the new molecule (negative to distinguish from server IDs)
        const tempId = -(allMolecules.length + 1);

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

        const newMolecule: StoichiometryMolecule = {
          id: tempId,
          rsChemElement: mockRsChemElement,
          role: "agent",
          formula: moleculeInfo.formula,
          name: name,
          smiles: smilesString,
          coefficient: 1,
          molecularWeight: moleculeInfo.molecularWeight,
          mass: null,
          moles: null,
          expectedAmount: null,
          actualAmount: null,
          actualYield: null,
          limitingReagent: false,
          notes: null,
        };

        setAllMolecules((prev) => [...prev, newMolecule]);
        onChangesUpdate?.(true);
      } catch (error) {
        // Error handling is done by getMoleculeInfo hook, just log it here
        console.error("Failed to fetch molecule information:", error);
      } finally {
        setMoleculeInfoLoading(false);
      }
    },
    [allMolecules, moleculeInfoLoading, getMoleculeInfo, onChangesUpdate],
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
    <>
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
          },
        }}
      />
      <LoadingDialog open={moleculeInfoLoading} />
    </>
  );
});

export default StoichiometryTable;
