import useStores from "../../stores/use-stores";
import Fab from "@mui/material/Fab";
import MaterialsDialog from "./MaterialsDialog";
import React, { useState, useEffect } from "react";
import materialTheme from "../../theme";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { ThemeProvider } from "@mui/material/styles";
import StyledEngineProvider from "@mui/styled-engine/StyledEngineProvider";
import { faVial } from "@fortawesome/free-solid-svg-icons/faVial";
import { withStyles } from "Styles";
import { makeStyles } from "tss-react/mui";
import { observer } from "mobx-react-lite";
import { type ElnFieldId } from "../../stores/models/MaterialsModel";
import MenuItem from "@mui/material/MenuItem";
import ClickAwayListener from "@mui/material/ClickAwayListener";
import Grow from "@mui/material/Grow";
import Paper from "@mui/material/Paper";
import Popper from "@mui/material/Popper";
import MenuList from "@mui/material/MenuList";
import Badge from "@mui/material/Badge";
import AlwaysNewWindowNavigationContext from "../../components/AlwaysNewWindowNavigationContext";
import PrintedMaterialsListing from "./PrintedMaterialsListing";
import { createRoot } from "react-dom/client";
import { ACCENT_COLOR as INVENTORY_COLOR } from "../../assets/branding/rspace/inventory";
import createAccentedTheme from "../../accentedTheme";

const FAB_SIZE = 48;

const WrappingMenuItem = withStyles<
  React.ComponentProps<typeof MenuItem>,
  { root: string }
>(() => ({
  root: {
    display: "flex",
    flexDirection: "column",
  },
}))((props) => <MenuItem {...props} />);

const NewLoMButtonWrapper = withStyles<
  { children: React.ReactNode },
  { root: string }
>(() => ({
  root: {
    "@media print": {
      display: "none",
    },
  },
}))(({ children, classes }) => <div className={classes.root}>{children}</div>);

const useStyles = makeStyles<{ fabRightPadding: number }>()(
  (theme, { fabRightPadding }) => ({
    launcherWrapper: {
      position: "absolute",
      top: FAB_SIZE,
      right: fabRightPadding,
      bottom: FAB_SIZE,
      pointerEvents: "none",
      "@media print": {
        display: "none",
      },
    },
    growTransform: { transformOrigin: "center right" },
    primary: { color: theme.palette.primary.main },
    popper: {
      zIndex: 1, // so it appears above the TinyMCE Editor
      pointerEvents: "auto",
    },
    itemName: { fontWeight: "bold" },
    itemText: {
      whiteSpace: "nowrap",
      overflow: "hidden",
      textOverflow: "ellipsis",
      width: "240px",
    },
    fab: {
      zIndex: "initial",
    },
  }),
);

const CustomBadge = withStyles<
  { children: React.ReactNode; count: number },
  { root: string; badge: string }
>(() => ({
  root: {
    position: "sticky",
    top: FAB_SIZE,
    zIndex: 1, // so it appears above the TinyMCE Editor
    pointerEvents: "auto",
  },
  badge: {
    transform: "none",
  },
}))(({ classes, children, count }) => (
  <Badge badgeContent={count} color="callToAction" classes={classes}>
    {children}
  </Badge>
));

const MaterialsLauncher = observer(
  ({
    elnFieldId,
    fabRightPadding,
  }: {
    elnFieldId: ElnFieldId;
    fabRightPadding: number;
  }) => {
    const { materialsStore } = useStores();

    const [showMenu, setShowMenu] = useState(false);
    const [showDialog, _setShowDialog] = useState(false);
    const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
    const { classes } = useStyles({ fabRightPadding });

    const setShowDialog = (value: boolean) => {
      _setShowDialog(value);
      window.dispatchEvent(new CustomEvent("listOfMaterialsOpened"));
    };

    const handleClose = () => setShowMenu(false);

    const fieldListings = materialsStore.fieldLists.get(elnFieldId);
    const fieldListCount = fieldListings?.length;
    return (
      <>
        <PrintedMaterialsListing
          listsOfMaterials={materialsStore.fieldLists.get(elnFieldId) ?? []}
        />
        <div className={classes.launcherWrapper}>
          {typeof fieldListCount === "number" && fieldListCount > 0 && (
            <>
              <CustomBadge count={fieldListCount}>
                <Fab
                  disabled={!materialsStore.canEdit && fieldListCount === 0}
                  color="callToAction"
                  onClick={({ currentTarget }) => {
                    // @ts-expect-error global
                    RS.trackEvent("user:open:menu:list_of_materials", {
                      fieldListCount,
                    });  
                    setShowMenu(true);
                    setAnchorEl(currentTarget);
                  }}
                  size="medium"
                  className={classes.fab}
                  aria-label="Show list of materials associated with this field"
                  aria-haspopup="menu"
                >
                  <FontAwesomeIcon icon={faVial} size="sm" />
                </Fab>
              </CustomBadge>
              <Popper
                open={showMenu}
                anchorEl={anchorEl}
                transition
                disablePortal
                placement="left"
                className={classes.popper}
              >
                {({ TransitionProps }) => (
                  <Grow {...TransitionProps} className={classes.growTransform}>
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
                            <WrappingMenuItem
                              key={i}
                              onClick={() => {
                                // @ts-expect-error global
                                RS.trackEvent("user:open:list_of_materials"); //eslint-disable-line
                                materialsStore.setCurrentList(list);
                                setShowDialog(true);
                                handleClose();
                              }}
                            >
                              <span className={classes.itemText}>
                                {i + 1}: {list.name}
                              </span>
                              <em className={classes.itemText}>
                                {list.description}
                              </em>
                            </WrappingMenuItem>
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
        </div>
      </>
    );
  },
);

type MaterialsListingArgs = {
  elnFieldId: string;
  canEdit: boolean | null;
  fabRightPadding: number;
};

const MaterialsListing = observer(
  ({ elnFieldId, canEdit, fabRightPadding }: MaterialsListingArgs) => {
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
      if (!loading)
        void materialsStore.getFieldMaterialsListings(parseInt(elnFieldId, 10));
       
    }, [loading]);

    return !loading ? (
      <StyledEngineProvider injectFirst>
        <ThemeProvider theme={materialTheme}>
          <AlwaysNewWindowNavigationContext>
            {materialsStore.fieldLists.has(parseInt(elnFieldId, 10)) && (
              <MaterialsLauncher
                elnFieldId={parseInt(elnFieldId, 10)}
                fabRightPadding={fabRightPadding}
              />
            )}
          </AlwaysNewWindowNavigationContext>
        </ThemeProvider>
      </StyledEngineProvider>
    ) : null;
  },
);

type NewMaterialsListingArgs = {
  elnFieldId: string;
};

const NewMaterialsListing = observer(
  ({ elnFieldId }: NewMaterialsListingArgs) => {
    const { materialsStore } = useStores();
    const [showDialog, _setShowDialog] = useState(false);
    const setShowDialog = (value: boolean) => {
      _setShowDialog(value);
      window.dispatchEvent(new CustomEvent("listOfMaterialsOpened"));
    };
    return (
      <NewLoMButtonWrapper>
        <StyledEngineProvider injectFirst>
          <ThemeProvider theme={materialTheme}>
            <AlwaysNewWindowNavigationContext>
              <div className="bootstrap-custom-flat">
                <button
                  className="btn btn-default"
                  style={{
                    float: "right",
                    marginRight: "8px",
                  }}
                  onClick={({ currentTarget }) => {
                    // @ts-expect-error global
                    RS.trackEvent("user:create:list_of_materials"); //eslint-disable-line
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
      </NewLoMButtonWrapper>
    );
  },
);

function initListOfMaterials({
  makeWrapperRelative,
  fabRightPadding,
}: {
  makeWrapperRelative: boolean;
  fabRightPadding: number;
}) {
  let canEdit;
  try {
    // @ts-expect-error eslint does not recognise the global variable
    canEdit = canBeEditable();  
  } catch {
    try {
      // @ts-expect-error eslint does not recognise the global variable
      canEdit = isEditable;  
    } catch {
      canEdit = null;
    }
  }

  [...document.getElementsByClassName("invMaterialsListing")].forEach(
    (wrapperDiv) => {
      const root = createRoot(wrapperDiv);
      root.render(
        <MaterialsListing
          // @ts-expect-error dataset does exist on HTMLDivElement
          elnFieldId={wrapperDiv.dataset.fieldId}
          canEdit={canEdit}
          fabRightPadding={fabRightPadding}
        />,
      );
      // @ts-expect-error style does exist on HTMLDivElement
      if (makeWrapperRelative) wrapperDiv.style.position = "relative";

      const newButtonWrapper = document.querySelector(
        // @ts-expect-error dataset does exist on HTMLDivElement
        `.invMaterialsListing_new[data-field-id="${wrapperDiv.dataset.fieldId}"][data-document-id="${wrapperDiv.dataset.documentId}"]`,
      );
      if (newButtonWrapper) {
        createRoot(newButtonWrapper).render(
          // @ts-expect-error dataset does exist on HTMLDivElement
          <NewMaterialsListing elnFieldId={wrapperDiv.dataset.fieldId} />,
        );
      }
    },
  );
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
