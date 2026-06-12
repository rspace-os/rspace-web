import { faVial } from "@fortawesome/free-solid-svg-icons/faVial";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import Badge from "@mui/material/Badge";
import Box from "@mui/material/Box";
import ClickAwayListener from "@mui/material/ClickAwayListener";
import Fab from "@mui/material/Fab";
import Grow from "@mui/material/Grow";
import MenuItem from "@mui/material/MenuItem";
import MenuList from "@mui/material/MenuList";
import Paper from "@mui/material/Paper";
import Popper from "@mui/material/Popper";
import { ThemeProvider } from "@mui/material/styles";
import Typography from "@mui/material/Typography";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import { observer } from "mobx-react-lite";
import React, { useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import createAccentedTheme from "../../accentedTheme";
import { ACCENT_COLOR as INVENTORY_COLOR } from "../../assets/branding/rspace/inventory";
import AlwaysNewWindowNavigationContext from "../../components/AlwaysNewWindowNavigationContext";
import Analytics from "../../components/Analytics";
import AnalyticsContext from "../../stores/contexts/Analytics";
// biome-ignore lint/style/useImportType: initial biome migration
import { type ElnFieldId } from "../../stores/models/MaterialsModel";
import useStores from "../../stores/use-stores";
import materialTheme from "../../theme";
import MaterialsDialog from "./MaterialsDialog";
import PrintedMaterialsListing from "./PrintedMaterialsListing";

const FAB_SIZE = 48;

const itemTextSx = {
  whiteSpace: "nowrap",
  overflow: "hidden",
  textOverflow: "ellipsis",
  width: "240px",
} as const;

const MaterialsLauncher = observer(
  ({ elnFieldId, fabRightPadding }: { elnFieldId: ElnFieldId; fabRightPadding: number }) => {
    const { materialsStore } = useStores();
    const { trackEvent } = React.useContext(AnalyticsContext);

    const [showMenu, setShowMenu] = useState(false);
    const [showDialog, _setShowDialog] = useState(false);
    const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
    const setShowDialog = (value: boolean) => {
      _setShowDialog(value);
      window.dispatchEvent(new CustomEvent("listOfMaterialsOpened"));
    };

    const handleClose = () => setShowMenu(false);

    const fieldListings = materialsStore.fieldLists.get(elnFieldId);
    const fieldListCount = fieldListings?.length;
    return (
      <>
        <PrintedMaterialsListing listsOfMaterials={materialsStore.fieldLists.get(elnFieldId) ?? []} />
        <Box
          sx={{
            position: "absolute",
            top: FAB_SIZE,
            right: fabRightPadding,
            bottom: FAB_SIZE,
            pointerEvents: "none",
            "@media print": {
              display: "none",
            },
          }}
        >
          {typeof fieldListCount === "number" && fieldListCount > 0 && (
            <>
              <Badge
                badgeContent={fieldListCount}
                color="callToAction"
                sx={{ position: "sticky", top: FAB_SIZE, zIndex: 1 }}
                slotProps={{ badge: { style: { transform: "none" } } }}
              >
                <Fab
                  disabled={!materialsStore.canEdit && fieldListCount === 0}
                  color="callToAction"
                  onClick={({ currentTarget }) => {
                    trackEvent("user:open:menu:list_of_materials", {
                      fieldListCount,
                    });
                    setShowMenu(true);
                    setAnchorEl(currentTarget);
                  }}
                  size="medium"
                  sx={{ zIndex: "initial" }}
                  aria-label="Show list of materials associated with this field"
                  aria-haspopup="menu"
                >
                  <FontAwesomeIcon icon={faVial} size="sm" />
                </Fab>
              </Badge>
              <Popper
                open={showMenu}
                anchorEl={anchorEl}
                transition
                disablePortal
                placement="left"
                sx={{
                  zIndex: 1,
                  pointerEvents: "auto",
                }}
              >
                {({ TransitionProps }) => (
                  <Grow {...TransitionProps} style={{ transformOrigin: "center right" }}>
                    <Paper
                      sx={{
                        maxHeight: "calc(100vh - 16px)",
                        overflowY: "auto",
                        marginBottom: "8px",
                      }}
                    >
                      <ClickAwayListener onClickAway={handleClose}>
                        <MenuList>
                          {fieldListings?.map((list, i) => (
                            <MenuItem
                              sx={{ display: "flex", flexDirection: "column" }}
                              key={i}
                              onClick={() => {
                                trackEvent("user:open:list_of_materials");
                                materialsStore.setCurrentList(list);
                                setShowDialog(true);
                                handleClose();
                              }}
                            >
                              <Typography variant="inherit" component="span" sx={itemTextSx}>
                                {i + 1}: {list.name}
                              </Typography>
                              <Typography variant="inherit" component="em" sx={itemTextSx}>
                                {list.description}
                              </Typography>
                            </MenuItem>
                          ))}
                        </MenuList>
                      </ClickAwayListener>
                    </Paper>
                  </Grow>
                )}
              </Popper>
            </>
          )}

          <ThemeProvider theme={createAccentedTheme(INVENTORY_COLOR)}>
            <MaterialsDialog open={showDialog} setOpen={setShowDialog} />
          </ThemeProvider>
        </Box>
      </>
    );
  },
);

type MaterialsListingArgs = {
  elnFieldId: string;
  canEdit: boolean | null;
  fabRightPadding: number;
};

const MaterialsListing = observer(({ elnFieldId, canEdit, fabRightPadding }: MaterialsListingArgs) => {
  const { materialsStore } = useStores();
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    materialsStore.canEdit = canEdit;
  }, [canEdit]);

  useEffect(() => {
    materialsStore
      .setup()
      .then(() => setLoading(false))
      .catch((e) => console.error(e));
  }, []);

  useEffect(() => {
    if (!loading) void materialsStore.getFieldMaterialsListings(parseInt(elnFieldId, 10));
  }, [loading]);

  return !loading ? (
    <StyledEngineProvider injectFirst enableCssLayer>
      <ThemeProvider theme={materialTheme}>
        <AlwaysNewWindowNavigationContext>
          {materialsStore.fieldLists.has(parseInt(elnFieldId, 10)) && (
            <MaterialsLauncher elnFieldId={parseInt(elnFieldId, 10)} fabRightPadding={fabRightPadding} />
          )}
        </AlwaysNewWindowNavigationContext>
      </ThemeProvider>
    </StyledEngineProvider>
  ) : null;
});

type NewMaterialsListingArgs = {
  elnFieldId: string;
};

const NewMaterialsListing = observer(({ elnFieldId }: NewMaterialsListingArgs) => {
  const { materialsStore } = useStores();
  const { trackEvent } = React.useContext(AnalyticsContext);
  const [showDialog, _setShowDialog] = useState(false);
  const setShowDialog = (value: boolean) => {
    _setShowDialog(value);
    window.dispatchEvent(new CustomEvent("listOfMaterialsOpened"));
  };
  return (
    <Box sx={{ "@media print": { display: "none" } }}>
      <StyledEngineProvider injectFirst enableCssLayer>
        <ThemeProvider theme={materialTheme}>
          <AlwaysNewWindowNavigationContext>
            <div className="bootstrap-custom-flat">
              {/** biome-ignore lint/a11y/useButtonType: initial biome migration */}
              <button
                className="btn btn-default"
                style={{
                  float: "right",
                  marginRight: "8px",
                }}
                onClick={({ currentTarget }) => {
                  trackEvent("user:create:list_of_materials");
                  setShowDialog(true);
                  currentTarget.blur();
                  materialsStore.newListOfMaterials(parseInt(elnFieldId, 10));
                }}
              >
                New List of Materials
              </button>
              <ThemeProvider theme={createAccentedTheme(INVENTORY_COLOR)}>
                <MaterialsDialog open={showDialog} setOpen={setShowDialog} />
              </ThemeProvider>
            </div>
          </AlwaysNewWindowNavigationContext>
        </ThemeProvider>
      </StyledEngineProvider>
    </Box>
  );
});

function initListOfMaterials({
  makeWrapperRelative,
  fabRightPadding,
}: {
  makeWrapperRelative: boolean;
  fabRightPadding: number;
}) {
  const globalWindow = window as Window & {
    canBeEditable?: () => boolean;
    isEditable?: boolean;
  };
  let canEdit: boolean | null = null;

  if (typeof globalWindow.canBeEditable === "function") {
    canEdit = globalWindow.canBeEditable();
  } else if (typeof globalWindow.isEditable === "boolean") {
    canEdit = globalWindow.isEditable;
  }

  Array.from(document.getElementsByClassName("invMaterialsListing")).forEach((wrapperDiv) => {
    const listingWrapper = wrapperDiv as HTMLDivElement;
    const { fieldId, documentId } = listingWrapper.dataset;

    if (!fieldId) return;

    const root = createRoot(listingWrapper);
    root.render(
      <Analytics>
        <MaterialsListing elnFieldId={fieldId} canEdit={canEdit} fabRightPadding={fabRightPadding} />
      </Analytics>,
    );
    if (makeWrapperRelative) listingWrapper.style.position = "relative";

    if (!documentId) return;

    const newButtonWrapper = document.querySelector(
      `.invMaterialsListing_new[data-field-id="${fieldId}"][data-document-id="${documentId}"]`,
    );
    if (newButtonWrapper) {
      createRoot(newButtonWrapper).render(
        <Analytics>
          <NewMaterialsListing elnFieldId={fieldId} />
        </Analytics>,
      );
    }
  });
}

/*
 * This is invoked on page load, for structuredDocument
 */
initListOfMaterials({
  makeWrapperRelative: false,
  fabRightPadding: -FAB_SIZE / 2,
});

/*
 * This is invoked by journal.js, when viewing via notebookEditor
 */
window.addEventListener("listOfMaterialInit", () => {
  initListOfMaterials({
    makeWrapperRelative: true,
    fabRightPadding: -FAB_SIZE,
  });
});
