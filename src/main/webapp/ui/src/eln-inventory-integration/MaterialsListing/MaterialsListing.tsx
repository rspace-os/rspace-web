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
import { createRoot, type Root } from "react-dom/client";
import { useTranslation } from "react-i18next";
import createAccentedTheme from "../../accentedTheme";
import { ACCENT_COLOR as INVENTORY_COLOR } from "../../assets/branding/rspace/inventory";
import AlwaysNewWindowNavigationContext from "../../components/AlwaysNewWindowNavigationContext";
import Analytics from "../../Inventory/Analytics";
import I18nRoot from "../../modules/common/i18n/I18nRoot";
import AnalyticsContext from "../../stores/contexts/Analytics";
import type { ElnFieldId } from "../../stores/models/MaterialsModel";
import useStores from "../../stores/use-stores";
import materialTheme from "../../theme";
import MaterialsDialog from "./MaterialsDialog";
import PrintedMaterialsListing from "./PrintedMaterialsListing";

const FAB_SIZE = 48;
const materialsListingRoots = new WeakMap<Element, Root>();

function getMaterialsListingRoot(container: Element): Root {
  const existingRoot = materialsListingRoots.get(container);
  if (existingRoot) {
    return existingRoot;
  }

  const root = createRoot(container);
  materialsListingRoots.set(container, root);
  return root;
}

const itemTextSx = {
  whiteSpace: "nowrap",
  overflow: "hidden",
  textOverflow: "ellipsis",
  width: "240px",
} as const;

const MaterialsLauncher = observer(
  ({ elnFieldId, fabRightPadding }: { elnFieldId: ElnFieldId; fabRightPadding: number }) => {
    const { materialsStore } = useStores();
    const { t } = useTranslation("inventory");
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
                sx={{ zIndex: "initial", pointerEvents: "auto" }}
                aria-label={t("materialsListing.launcher.showAssociatedLists")}
                aria-haspopup="menu"
              >
                <Badge
                  badgeContent={fieldListCount}
                  color="callToAction"
                  sx={{ top: FAB_SIZE, zIndex: 1, position: "initial" }}
                  slotProps={{ badge: { style: { transform: "none" } } }}
                />
                <FontAwesomeIcon icon={faVial} size="sm" />
              </Fab>
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
                                {`${i + 1}: ${list.name}`}
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
  const { t } = useTranslation("inventory");
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
                {t("materialsListing.launcher.newList")}
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

    const root = getMaterialsListingRoot(listingWrapper);
    root.render(
      <I18nRoot namespaces={["inventory", "common"]}>
        <Analytics>
          <MaterialsListing elnFieldId={fieldId} canEdit={canEdit} fabRightPadding={fabRightPadding} />
        </Analytics>
      </I18nRoot>,
    );
    if (makeWrapperRelative) listingWrapper.style.position = "relative";

    if (!documentId) return;

    const newButtonWrapper = document.querySelector(
      `.invMaterialsListing_new[data-field-id="${fieldId}"][data-document-id="${documentId}"]`,
    );
    if (newButtonWrapper) {
      getMaterialsListingRoot(newButtonWrapper).render(
        <I18nRoot namespaces={["inventory", "common"]}>
          <Analytics>
            <NewMaterialsListing elnFieldId={fieldId} />
          </Analytics>
        </I18nRoot>,
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
