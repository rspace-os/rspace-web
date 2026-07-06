import AddIcon from "@mui/icons-material/Add";
import EditIcon from "@mui/icons-material/Edit";
import FileIcon from "@mui/icons-material/InsertDriveFile";
import Backdrop from "@mui/material/Backdrop";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import CardMedia from "@mui/material/CardMedia";
import CircularProgress from "@mui/material/CircularProgress";
import Menu from "@mui/material/Menu";
import MenuItem from "@mui/material/MenuItem";
import { paperClasses } from "@mui/material/Paper";
import { ThemeProvider } from "@mui/material/styles";
import Tooltip from "@mui/material/Tooltip";
import { ColumnsPanelTrigger, type GridSlotProps, Toolbar, useGridApiContext } from "@mui/x-data-grid";
import React from "react";
import { MemoryRouter } from "react-router";
import createAccentedTheme from "@/accentedTheme";
import { ACCENT_COLOR as CHEMISTRY_COLOR } from "@/assets/branding/chemistry";
import { ACCENT_COLOR as PUBCHEM_ACCENT_COLOR } from "@/assets/branding/pubchem";
import { ACCENT_COLOR as GALLERY_COLOR } from "@/assets/branding/rspace/gallery";
import AccentMenuItem from "@/components/AccentMenuItem";
import { LandmarksProvider } from "@/components/LandmarksContext";
import type { InventoryQuantityQueryResult } from "@/modules/inventory/queries";
import CompoundSearchDialog from "@/tinyMCE/pubchem/CompoundSearchDialog";
import StoichiometryAddReagentDialog from "@/tinyMCE/stoichiometry/StoichiometryAddReagentDialog";
import StoichiometryInventoryUpdateDialog, {
  type InventoryStockUpdateResult,
} from "@/tinyMCE/stoichiometry/StoichiometryInventoryUpdateDialog";
import type { EditableMolecule } from "@/tinyMCE/stoichiometry/types";
import { filenameExceptExtension } from "@/util/files";
import Result from "@/util/result";
import PubChemLogo from "../../assets/branding/pubchem/logo.svg";
import * as Parsers from "../../util/parsers";

const GalleryPicker = React.lazy(() => import("../../eln/gallery/picker"));
declare module "@mui/x-data-grid" {
  interface ToolbarPropsOverrides {
    onAddReagent: (smilesString: string, name: string, source: string) => Promise<void>;
    onUpdateInventoryStock?: (selectedMoleculeIds: number[]) => Promise<InventoryStockUpdateResult>;
    editable: boolean;
    allMolecules: ReadonlyArray<EditableMolecule>;
    hasChanges: boolean;
    linkedInventoryQuantityInfoByGlobalId: ReadonlyMap<string, InventoryQuantityQueryResult>;
  }
}
const StoichiometryTableToolbar = ({
  onAddReagent,
  onUpdateInventoryStock,
  editable,
  allMolecules,
  hasChanges,
  linkedInventoryQuantityInfoByGlobalId,
}: GridSlotProps["toolbar"]) => {
  const apiRef = useGridApiContext();
  const [addReagantMenuAnchorEl, setAddReagentMenuAnchorEl] = React.useState<HTMLButtonElement | null>(null);
  const [addReagentSmilesDialogOpen, setAddReagentSmilesDialogOpen] = React.useState(false);
  const [pubchemDialogOpen, setPubchemDialogOpen] = React.useState(false);
  const [galleryDialogOpen, setGalleryDialogOpen] = React.useState(false);
  const [inventoryUpdateDialogOpen, setInventoryUpdateDialogOpen] = React.useState(false);
  const [exportMenuAnchorEl, setExportMenuAnchorEl] = React.useState<HTMLButtonElement | null>(null);
  // Gate the dialog on quantity data so it always opens with complete data
  // and can compute its default selection on mount, with no reconciliation of
  // late-arriving results. Failed fetches still resolve into the map (as
  // error entries), so this only stays true while requests are in flight or
  // the OAuth token is unavailable.
  const isLoadingInventoryQuantities = allMolecules.some((molecule) => {
    const globalId = molecule.inventoryLink?.inventoryItemGlobalId;
    return globalId != null && !linkedInventoryQuantityInfoByGlobalId.has(globalId);
  });
  const inventoryUpdateDisabledTooltip = hasChanges
    ? "Save the stoichiometry table before updating inventory stock."
    : isLoadingInventoryQuantities
      ? "Loading inventory stock information..."
      : "";
  return (
    <Toolbar
      style={{
        width: "100%",
      }}
    >
      {editable && (
        <>
          <Button
            aria-label="Add Chemical"
            startIcon={<AddIcon />}
            onClick={(e) => setAddReagentMenuAnchorEl(e.currentTarget)}
            size="small"
            sx={{
              mr: 1,
            }}
          >
            Add Chemical
          </Button>
          <Tooltip title={inventoryUpdateDisabledTooltip}>
            <span>
              <Button
                aria-label="Update Inventory Stock"
                size="small"
                sx={{
                  mr: 1,
                }}
                disabled={hasChanges || isLoadingInventoryQuantities}
                onClick={() => {
                  setInventoryUpdateDialogOpen(true);
                }}
              >
                Update Inventory Stock
              </Button>
            </span>
          </Tooltip>
          <Menu
            open={Boolean(addReagantMenuAnchorEl)}
            anchorEl={addReagantMenuAnchorEl}
            onClose={() => setAddReagentMenuAnchorEl(null)}
            sx={
              addReagantMenuAnchorEl
                ? {
                    [`& .${paperClasses.root}`]: {
                      transform: "translate(0px, 4px) !important",
                    },
                  }
                : {}
            }
            slotProps={{
              list: {
                disablePadding: true,
                "aria-label": "add chemical menu",
              },
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
              avatar={
                <FileIcon
                  sx={{
                    width: "28px",
                    height: "28px",
                  }}
                />
              }
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
          </Menu>
          <StoichiometryAddReagentDialog
            open={addReagentSmilesDialogOpen}
            onClose={() => {
              setAddReagentSmilesDialogOpen(false);
            }}
            onAddReagent={(smilesString, name) => {
              void onAddReagent(smilesString, name, "manual");
            }}
          />
          <StoichiometryInventoryUpdateDialog
            open={inventoryUpdateDialogOpen}
            molecules={allMolecules}
            linkedInventoryQuantityInfoByGlobalId={linkedInventoryQuantityInfoByGlobalId}
            onSave={onUpdateInventoryStock}
            onClose={() => {
              setInventoryUpdateDialogOpen(false);
            }}
          />
          <ThemeProvider theme={createAccentedTheme(PUBCHEM_ACCENT_COLOR)}>
            <CompoundSearchDialog
              open={pubchemDialogOpen}
              onClose={() => {
                setPubchemDialogOpen(false);
                setAddReagentMenuAnchorEl(null);
              }}
              onCompoundsSelected={(compounds) => {
                void (async () => {
                  for (const c of compounds) {
                    await onAddReagent(c.smiles, c.name, "pubchem");
                  }
                })();
              }}
              title="Insert from PubChem"
              submitButtonText="Insert"
              showPubChemInfo
              allowMultipleSelection
            />
          </ThemeProvider>
          {galleryDialogOpen && (
            <MemoryRouter>
              <LandmarksProvider>
                <React.Suspense
                  fallback={
                    <Backdrop
                      open
                      sx={{
                        color: "#fff",
                        zIndex: 1,
                      }}
                    >
                      <CircularProgress color="inherit" aria-label="Loading gallery picker" />
                    </Backdrop>
                  }
                >
                  <GalleryPicker
                    open={true}
                    onClose={() => {
                      setGalleryDialogOpen(false);
                    }}
                    onSubmit={(files) => {
                      void (async () => {
                        for (const file of files) {
                          await Parsers.getValueWithKey("chemString")(file.metadata)
                            .flatMap(Parsers.isString)
                            .doAsync((smiles) => {
                              return onAddReagent(smiles, filenameExceptExtension(file.name), "gallery");
                            });
                        }
                        setGalleryDialogOpen(false);
                      })();
                    }}
                    validateSelection={(file) => {
                      if (file.type !== "Chemistry")
                        return Result.Error([new Error("Only chemistry files can be added to stoichiometry tables")]);
                      return Result.Ok(null);
                    }}
                  />
                </React.Suspense>
              </LandmarksProvider>
            </MemoryRouter>
          )}
        </>
      )}
      <Box
        sx={{
          flexGrow: 1,
        }}
      ></Box>
      <ColumnsPanelTrigger aria-label="Columns" size="small">
        Columns
      </ColumnsPanelTrigger>
      <Button
        aria-label="Export"
        size="small"
        onClick={(event) => {
          setExportMenuAnchorEl(event.currentTarget);
        }}
      >
        Export
      </Button>
      <Menu
        open={Boolean(exportMenuAnchorEl)}
        anchorEl={exportMenuAnchorEl}
        onClose={() => {
          setExportMenuAnchorEl(null);
        }}
        slotProps={{
          paper: {
            role: "tooltip",
          },
        }}
      >
        <MenuItem
          onClick={() => {
            setExportMenuAnchorEl(null);
            apiRef.current?.exportDataAsCsv({
              allColumns: true,
              getRowsToExport: () => allMolecules.map((molecule) => molecule.id),
            });
          }}
        >
          Export to CSV
        </MenuItem>
      </Menu>
    </Toolbar>
  );
};
export default StoichiometryTableToolbar;
