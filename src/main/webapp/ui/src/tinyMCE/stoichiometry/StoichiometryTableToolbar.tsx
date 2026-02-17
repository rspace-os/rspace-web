import React from "react";
import Box from "@mui/material/Box";
import CircularProgress from "@mui/material/CircularProgress";
import Menu from "@mui/material/Menu";
import PubChemLogo from "../../assets/branding/pubchem/logo.svg";
import { ACCENT_COLOR as PUBCHEM_ACCENT_COLOR } from "@/assets/branding/pubchem";
import { ACCENT_COLOR as GALLERY_COLOR } from "@/assets/branding/rspace/gallery";
import { ACCENT_COLOR as CHEMISTRY_COLOR } from "@/assets/branding/chemistry";
import { ThemeProvider, useTheme } from "@mui/material/styles";
import type { EditableMolecule } from "@/tinyMCE/stoichiometry/types";
import * as Parsers from "../../util/parsers";
import {
  GridSlotProps,
  GridToolbarColumnsButton,
  GridToolbarContainer,
  GridToolbarExportContainer,
  useGridApiContext,
} from "@mui/x-data-grid";
import Button from "@mui/material/Button";
import AddIcon from "@mui/icons-material/Add";
import AccentMenuItem from "@/components/AccentMenuItem";
import CardMedia from "@mui/material/CardMedia";
import EditIcon from "@mui/icons-material/Edit";
import FileIcon from "@mui/icons-material/InsertDriveFile";
import StoichiometryAddReagentDialog from "@/tinyMCE/stoichiometry/StoichiometryAddReagentDialog";
import CompoundSearchDialog from "@/tinyMCE/pubchem/CompoundSearchDialog";
import createAccentedTheme from "@/accentedTheme";
import { MemoryRouter } from "react-router-dom";
import { LandmarksProvider } from "@/components/LandmarksContext";
import Backdrop from "@mui/material/Backdrop";
import { filenameExceptExtension } from "@/util/files";
import Result from "@/util/result";
import MenuItem from "@mui/material/MenuItem";

const GalleryPicker = React.lazy(() => import("../../eln/gallery/picker"));

declare module "@mui/x-data-grid" {
  interface ToolbarPropsOverrides {
    setColumnsMenuAnchorEl: (anchorEl: HTMLElement | null) => void;
    onAddReagent: (
      smilesString: string,
      name: string,
      source: string,
    ) => Promise<void>;
    editable: boolean;
    allMolecules: ReadonlyArray<EditableMolecule>;
  }
}

function StoichiometryTableToolbar({
  setColumnsMenuAnchorEl,
  onAddReagent,
  editable,
  allMolecules,
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
            <Menu
              open={Boolean(addReagantMenuAnchorEl)}
              anchorEl={addReagantMenuAnchorEl}
              onClose={() => setAddReagentMenuAnchorEl(null)}
              sx={
                Boolean(addReagantMenuAnchorEl)
                  ? {
                      "& .MuiPaper-root": {
                        transform: "translate(0px, 4px) !important",
                      },
                    }
                  : {}
              }
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
            </Menu>
            <StoichiometryAddReagentDialog
              open={addReagentSmilesDialogOpen}
              onClose={() => {
                setAddReagentSmilesDialogOpen(false);
              }}
              onAddReagent={(smilesString, name) =>
                onAddReagent(smilesString, name, "manual")
              }
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
                        <CircularProgress
                          color="inherit"
                          aria-label="Loading gallery picker"
                        />
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
                            await Parsers.getValueWithKey("chemString")(
                              file.metadata,
                            )
                              .flatMap(Parsers.isString)
                              .doAsync((smiles) => {
                                return onAddReagent(
                                  smiles,
                                  filenameExceptExtension(file.name),
                                  "gallery",
                                );
                              });
                          }
                          setGalleryDialogOpen(false);
                        })();
                      }}
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
                  </React.Suspense>
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
                getRowsToExport: () =>
                  allMolecules.map((molecule) => molecule.id),
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

export default StoichiometryTableToolbar;
