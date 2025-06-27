import InventoryPicker from "../../Inventory/components/Picker/Picker";
import Alerts from "../../Inventory/components/Alerts";
import Confirm from "../../components/Confirm";
import CustomTooltip from "../../components/CustomTooltip";
import ErrorBoundary from "../../components/ErrorBoundary";
import Exporter from "../../Inventory/components/Export/Exporter";
import { type ExportOptions } from "../../stores/definitions/Search";
import { defaultExportOptions } from "../../Inventory/components/Export/ExportDialog";
import HelpLinkIcon from "../../components/HelpLinkIcon";
import useStores from "../../stores/use-stores";
import { preventEventBubbling, doNotAwait } from "../../util/Util";
import { showToastWhilstPending } from "../../util/alerts";
import MaterialsTable from "./MaterialsTable";
import PopoutPrintIcon from "./PopoutPrintIcon";
import Button from "@mui/material/Button";
import { Dialog, DialogBoundary } from "../../components/DialogBoundary";
import Portal from "@mui/material/Portal";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Grid from "@mui/material/Grid";
import IconButton from "@mui/material/IconButton";
import Slide from "@mui/material/Slide";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { withStyles } from "Styles";
import { makeStyles } from "tss-react/mui";
import PrintIcon from "@mui/icons-material/Print";
import clsx from "clsx";
import { observer, Observer } from "mobx-react-lite";
import React, { useState, forwardRef, useEffect } from "react";
import docLinks from "../../assets/DocLinks";
import { type ListOfMaterials } from "../../stores/models/MaterialsModel";
import WarningBar from "../../components/WarningBar";
import Box from "@mui/material/Box";
import ValidatingSubmitButton, {
  IsInvalid,
  IsValid,
} from "../../components/ValidatingSubmitButton";
import { useIsSingleColumnLayout } from "../../Inventory/components/Layout/Layout2x1";
import getRootStore from "../../stores/stores/RootStore";
import { hasLocation } from "../../stores/models/HasLocation";

const EmptyListText = ({
  currentList,
}: {
  currentList: ListOfMaterials | null | undefined;
}) =>
  currentList && currentList.materials.length === 0 ? (
    <Typography
      component="div"
      variant="body2"
      color="textPrimary"
      align="center"
    >
      Use &quot;Add items&quot; to add materials to this list.
    </Typography>
  ) : null;

type CardWrapperInternalsArgs = {
  children: React.ReactNode;
  classes: { root: string };
};

const CardWrapperInternals = forwardRef<
  React.ElementRef<typeof Grid>,
  CardWrapperInternalsArgs
>(
  // eslint-disable-next-line react/prop-types
  ({ children, classes }: CardWrapperInternalsArgs, ref) => {
    const isSingleColumnLayout = useIsSingleColumnLayout();
    return (
      <Observer>
        {() => (
          <Grid
            item
            xs={isSingleColumnLayout ? 12 : 9}
            classes={classes}
            ref={ref}
            onClick={preventEventBubbling()}
          >
            {children}
          </Grid>
        )}
      </Observer>
    );
  }
);

CardWrapperInternals.displayName = "CardWrapperInternals";
const CardWrapper = withStyles<
  Omit<React.ComponentProps<typeof CardWrapperInternals>, "classes">,
  { root: string }
>(() => ({
  root: {
    position: "absolute",
    right: 0,
    top: 0,
    bottom: 0,
    zIndex: 100,
    width: "100%",
  },
}))(CardWrapperInternals);

const CustomDialog = withStyles<
  React.ComponentProps<typeof Dialog>,
  { paper?: string }
>((theme, { fullScreen }) => ({
  paper: {
    overflow: "hidden",

    // this is to avoid intercom help button
    maxHeight: fullScreen ? "unset" : "86vh",

    // this is to ensure the picker has enough height even when list is empty
    minHeight: "86vh",
  },
}))(Dialog);

const useStyles = makeStyles<{
  openSlide?: boolean;
  isSingleColumn?: boolean;
}>()((theme, { openSlide, isSingleColumn }) => ({
  contentWrapper: {
    overscrollBehavior: "contain",
    WebkitOverflowScrolling: "unset",
  },
  actionsBar: { marginBottom: theme.spacing(1) },
  barWrapper: {
    display: "flex",
    alignSelf: "center",
    width: "95%",
    flexDirection: "column",
    alignItems: "center",
  },
  fullWidth: { width: "100%" },
  bottomSpaced: {
    marginBottom: isSingleColumn ? theme.spacing(2) : theme.spacing(0.5),
  },
  sideSpaced: { marginRight: theme.spacing(1), marginLeft: theme.spacing(1) },
  spacedBetweenRow: {
    display: "flex",
    flexDirection: "row",
    justifyContent: "space-between",
  },
  flexEndRow: {
    display: "flex",
    flexDirection: "row",
    justifyContent: "flex-end",
  },
  warningRed: { color: theme.palette.warningRed },
  disableBackground: {
    transition: "all 225ms ease-in-out",
    filter: openSlide ? "grayscale(1) opacity(0.3)" : "none",
    pointerEvents: openSlide ? "none" : "unset",
  },
  primary: { color: theme.palette.primary.main },
  black: {
    "& input": {
      color: "black",
    },
  },
  textField: {
    marginLeft: theme.spacing(0.5),
    marginRight: theme.spacing(0.5),
    fontWeight: "normal",
  },
  hideWhenPrinting: {
    "@media print": { display: "none" },
  },
  dialogTitle: {
    paddingBottom: theme.spacing(0.5),
  },
}));

const BigButton = withStyles<
  { onClick: () => void; icon: React.ReactNode },
  { root: string }
>((theme) => ({
  root: {
    padding: 0,
    cursor: "pointer",
    margin: theme.spacing(0.25),
    "& svg": {
      width: "2rem",
      height: "2rem",
    },
  },
}))(({ icon, onClick, classes }) => (
  <IconButton
    classes={classes}
    component="div"
    size="small"
    color="primary"
    onClick={onClick}
  >
    {icon}
  </IconButton>
));

const MetadataBar = observer(
  ({
    currentList,
    canEdit,
    isSingleColumn,
  }: {
    currentList: ListOfMaterials | null | undefined;
    canEdit: boolean;
    isSingleColumn: boolean;
  }) => {
    const { classes } = useStyles({ isSingleColumn });
    return (
      <div className={clsx(classes.barWrapper, classes.bottomSpaced)}>
        <div className={clsx(classes.spacedBetweenRow, classes.fullWidth)}>
          <TextField
            variant="standard"
            className={clsx(classes.textField, !canEdit && classes.black)}
            style={{ flex: 5 }}
            label="List Name"
            margin="dense"
            value={currentList?.name ?? ""}
            onChange={(e) => currentList?.setName(e.target.value)}
            disabled={!canEdit}
            error={(currentList?.name ?? "").length > 255}
            helperText={`${(currentList?.name ?? "").length} / 255`}
          />
          <TextField
            variant="standard"
            className={classes.textField}
            style={{ flex: 1 }}
            label="ID"
            margin="dense"
            value={currentList?.id ?? "-"}
            disabled
          />
        </div>
        <TextField
          variant="standard"
          className={clsx(classes.textField, !canEdit && classes.black)}
          fullWidth
          multiline
          label="Description"
          margin="dense"
          value={currentList?.description ?? ""}
          onChange={(e) => currentList?.setDescription(e.target.value)}
          disabled={!canEdit}
          error={(currentList?.description ?? "").length > 255}
          helperText={`${(currentList?.description ?? "").length} / 255`}
        />
      </div>
    );
  }
);

const ActionsBar = observer(
  ({
    setOpenPicker,
    currentList,
    standalonePage,
    onOpenStandalone,
    canEdit,
  }: {
    setOpenPicker: (open: boolean) => void;
    currentList: ListOfMaterials | null | undefined;
    standalonePage: boolean;
    onOpenStandalone: () => void;
    canEdit: boolean;
  }) => {
    const { classes } = useStyles({});
    const { moveStore, materialsStore } = useStores();
    const anyDataInList = (currentList?.materials.length ?? -1) > 0;
    const originalList = materialsStore.originalList;
    const canEditQuantities = currentList?.canEditQuantities;
    const editingMode = currentList?.editingMode;
    const currentUser = getRootStore().peopleStore.currentUser;

    const allOnBench =
      currentUser === null
        ? false
        : currentList?.materials.every((m) => {
            return hasLocation(m.invRec)
              .map(
                (r) => currentUser && r.isDirectlyOnWorkbenchOfUser(currentUser)
              )
              .orElse(true);
          }) ?? false;

    const moveAllToBenchValidation = () => {
      if (currentList?.materials.length === 0)
        return IsInvalid("Nothing to move.");
      if (originalList && !currentList?.isEqual(originalList))
        return IsInvalid("Cannot move whilst there are unsaved changes.");
      if (allOnBench) return IsInvalid("All items are already on your bench.");
      return IsValid();
    };

    return (
      <Grid
        container
        spacing={2}
        className={clsx(classes.actionsBar, classes.hideWhenPrinting)}
      >
        <Grid item>
          <Button
            color="primary"
            variant="contained"
            disableElevation
            onClick={preventEventBubbling<React.MouseEvent<HTMLButtonElement>>(
              () => {
                setOpenPicker(true);
              }
            )}
            disabled={!canEdit}
          >
            Add items
          </Button>
        </Grid>
        <Grid item>
          <Button
            color="primary"
            variant="contained"
            disableElevation
            onClick={preventEventBubbling<React.MouseEvent<HTMLButtonElement>>(
              () => {
                currentList?.setEditingMode(!editingMode);
              }
            )}
            disabled={!canEditQuantities}
          >
            {editingMode ? "Close Quantity Editor" : "Edit Quantities"}
          </Button>
        </Grid>
        <Grid item>
          <ValidatingSubmitButton
            onClick={doNotAwait(async () => {
              if (currentList) {
                await currentList.moveAllToBench();
              }
            })}
            loading={moveStore.submitting === "TO-OTHER"}
            validationResult={moveAllToBenchValidation()}
          >
            Move all to my bench
          </ValidatingSubmitButton>
        </Grid>
        {!standalonePage && currentList?.id !== null && (
          <Grid item>
            <CustomTooltip title="View in new tab">
              <BigButton
                icon={<PopoutPrintIcon />}
                onClick={onOpenStandalone}
              />
            </CustomTooltip>
          </Grid>
        )}
        {standalonePage && (
          <Grid item>
            <BigButton
              icon={<PrintIcon />}
              onClick={() => {
                window.print();
              }}
            />
          </Grid>
        )}
        {anyDataInList && (
          <Grid item>
            <p style={{ margin: 0 }}>
              Tip: to edit an item click its Global ID, then the Edit button in
              the new browser tab.
            </p>
          </Grid>
        )}
      </Grid>
    );
  }
);

type DialogArgs = {
  open: boolean;
  setOpen: (open: boolean) => void;
  standalonePage?: boolean;
};

function MaterialsDialog({
  open,
  setOpen,
  standalonePage = false,
}: DialogArgs): React.ReactNode {
  const { materialsStore } = useStores();
  const isSingleColumnLayout = useIsSingleColumnLayout();
  const isSingleColumn = isSingleColumnLayout;

  const [openPicker, setOpenPicker] = useState<boolean>(false);
  const [openExporter, setOpenExporter] = useState<boolean>(false);

  const openSlide = openPicker || openExporter;
  const { classes } = useStyles({ openSlide });

  const currentList = materialsStore.currentList;
  const isListNew = materialsStore.isListNew;
  const isListExisting = materialsStore.isListExisting;
  const isListValid = materialsStore.isListValid;
  const isListLoading = currentList?.loading ?? true;
  const isUnchanged = materialsStore.isCurrentListUnchanged;
  const canEdit = Boolean(currentList?.canEdit);

  const [exportOptions, setExportOptions] = useState<ExportOptions>(
    defaultExportOptions(
      currentList?.materials.map((m) => m.invRec),
      "listOfMaterials"
    )
  );

  const refetch = () => {
    if (currentList)
      void materialsStore.getFieldMaterialsListings(currentList.elnFieldId);
  };

  useEffect(() => {
    if (currentList?.pickerSearch) {
      currentList.pickerSearch.fetcher.resetFetcher();
      void currentList.pickerSearch.fetcher.performInitialSearch({
        resultType: "SUBSAMPLE",
      });
    }
  }, [openPicker]);

  const confirmListDeletion = async () => {
    if (currentList) {
      const deletionConfirmed = await currentList.delete();

      if (deletionConfirmed) {
        materialsStore.setCurrentList(undefined);
        refetch();
        // if inside pop-out window, tell parent that delete occurred
        if (window.opener) window.opener.postMessage("deleted", window.origin);
        setOpen(false);
      }
    }
  };

  const onOpenStandalone = () => {
    if (!currentList?.id) throw new Error("List must have an id.");
    window.open(`${location.origin}/listOfMaterials/${currentList.id}`);
    window.addEventListener("message", ({ origin, data }) => {
      // checking origin is a vital security check
      if (origin === window.origin) {
        switch (data) {
          case "closing":
            if (materialsStore.currentList) {
              const list = materialsStore.currentList;
              void materialsStore.getMaterialsListing(list.id).then((lom) => {
                materialsStore.replaceListInField(lom, list.elnFieldId);
              });
            }
            break;
          case "deleted":
            setOpen(false);
            materialsStore.setCurrentList(undefined);
            refetch();
            break;
        }
      }
    });
  };

  return (
    <ErrorBoundary topOfViewport>
      <Portal>
        <Alerts>
          <DialogBoundary>
            <CustomDialog
              onClose={() => {
                materialsStore.setCurrentList(materialsStore.originalList);
                setOpen(false);
              }}
              open={open}
              maxWidth="lg"
              fullWidth
              fullScreen={isSingleColumn || standalonePage}
              onClick={() => {
                setOpenPicker(false);
                setOpenExporter(false);
              }}
            >
              <DialogTitle className={classes.dialogTitle}>
                {currentList?.id === undefined && "New "} List of Materials
                (Inventory)&nbsp;
                <HelpLinkIcon
                  link={docLinks.listOfMaterials}
                  title="Info on using Lists of Materials."
                />
                {!isSingleColumn && (
                  <MetadataBar
                    currentList={currentList}
                    canEdit={canEdit}
                    isSingleColumn={false}
                  />
                )}
              </DialogTitle>
              <DialogContent className={classes.contentWrapper}>
                <Grid container>
                  <Grid item xs={12} className={classes.disableBackground}>
                    {isSingleColumn && (
                      <MetadataBar
                        currentList={currentList}
                        canEdit={canEdit}
                        isSingleColumn={isSingleColumn}
                      />
                    )}
                    <ActionsBar
                      setOpenPicker={setOpenPicker}
                      currentList={currentList}
                      standalonePage={standalonePage}
                      onOpenStandalone={onOpenStandalone}
                      canEdit={canEdit}
                    />
                    {currentList && (
                      <MaterialsTable
                        list={currentList}
                        isSingleColumn={isSingleColumn}
                        onRemove={(mat) => currentList.removeMaterial(mat)}
                        canEdit={canEdit}
                      />
                    )}
                    <EmptyListText currentList={currentList} />
                  </Grid>
                  <Slide
                    in={openSlide}
                    direction="left"
                    onClick={preventEventBubbling(() => {})}
                  >
                    <CardWrapper>
                      {openPicker && currentList?.pickerSearch && (
                        <InventoryPicker
                          elevation={6}
                          onAddition={(additions) => {
                            if (currentList) {
                              currentList.addMaterials(additions);
                            }
                            setOpenPicker(false);
                          }}
                          search={currentList.pickerSearch}
                          header="Pick Inventory Items"
                          showActions
                        />
                      )}
                      {openExporter && currentList && (
                        <Exporter
                          elevation={6}
                          header={"Export Options"}
                          showActions
                          selectedResults={currentList.materials.map(
                            (m) => m.invRec
                          )}
                          setOpenExporter={setOpenExporter}
                          exportOptions={exportOptions}
                          setExportOptions={setExportOptions}
                          onExport={() => {
                            void currentList.export(exportOptions);
                            setOpenExporter(false);
                          }}
                        />
                      )}
                    </CardWrapper>
                  </Slide>
                </Grid>
              </DialogContent>
              {!materialsStore.isCurrentListUnchanged && (
                <Box mr={3}>
                  <WarningBar />
                </Box>
              )}
              <DialogActions
                className={clsx(
                  classes.barWrapper,
                  classes.disableBackground,
                  classes.hideWhenPrinting
                )}
              >
                <div
                  className={clsx(classes.spacedBetweenRow, classes.fullWidth)}
                >
                  <Button
                    color="primary"
                    variant="contained"
                    disableElevation
                    onClick={preventEventBubbling<
                      React.MouseEvent<HTMLButtonElement>
                    >(
                      doNotAwait(async () => {
                        if (currentList) {
                          const changed = materialsStore.hasListChanged;
                          if (changed) {
                            await showToastWhilstPending(
                              `Saving changes...`,
                              currentList.update()
                            );
                            materialsStore.setCurrentList(currentList);
                            refetch();
                          }
                          setOpenExporter(true);
                        }
                      })
                    )}
                    disabled={!isListExisting || !isListValid}
                  >
                    Export
                  </Button>
                  {isListExisting && (
                    <Button
                      className={clsx(classes.warningRed, classes.sideSpaced)}
                      disableElevation
                      onClick={doNotAwait(() => confirmListDeletion())}
                      disabled={!canEdit}
                    >
                      Delete List
                    </Button>
                  )}
                  <div>
                    <Button
                      className={classes.sideSpaced}
                      onClick={
                        isUnchanged
                          ? () => setOpen(false)
                          : () => {
                              materialsStore.setCurrentList(
                                materialsStore.originalList
                              );
                              if (isListNew) setOpen(false);
                            }
                      }
                    >
                      {isUnchanged ? "Close" : "Cancel"}
                    </Button>
                    <ValidatingSubmitButton
                      onClick={doNotAwait(async () => {
                        if (currentList && isListValid) {
                          if (isListNew) {
                            await showToastWhilstPending(
                              `Creating list...`,
                              currentList.create()
                            );
                          }
                          if (isListExisting) {
                            const changed = materialsStore.hasListChanged;
                            if (changed)
                              await showToastWhilstPending(
                                `Updating list...`,
                                currentList.update()
                              );
                          }
                          materialsStore.setCurrentList(currentList);
                          refetch();
                        }
                      })}
                      loading={isListLoading}
                      validationResult={materialsStore.cantSaveCurrentList}
                    >
                      Save
                    </ValidatingSubmitButton>
                  </div>
                </div>
              </DialogActions>
              <Confirm />
            </CustomDialog>
          </DialogBoundary>
        </Alerts>
      </Portal>
    </ErrorBoundary>
  );
}

export default observer(MaterialsDialog);
