import PrintIcon from "@mui/icons-material/Print";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Grid from "@mui/material/Grid";
import IconButton from "@mui/material/IconButton";
import Portal from "@mui/material/Portal";
import Slide from "@mui/material/Slide";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { Observer, observer } from "mobx-react-lite";
import type React from "react";
import { forwardRef, useEffect, useState } from "react";
import docLinks from "../../assets/DocLinks";
import Analytics from "../../components/Analytics";
import Confirm from "../../components/Confirm";
import CustomTooltip from "../../components/CustomTooltip";
import { Dialog, DialogBoundary } from "../../components/DialogBoundary";
import ErrorBoundary from "../../components/ErrorBoundary";
import HelpLinkIcon from "../../components/HelpLinkIcon";
import ValidatingSubmitButton, { IsInvalid, IsValid } from "../../components/ValidatingSubmitButton";
import WarningBar from "../../components/WarningBar";
import Alerts from "../../Inventory/components/Alerts";
import { defaultExportOptions } from "../../Inventory/components/Export/ExportDialog";
import Exporter from "../../Inventory/components/Export/Exporter";
import { useIsSingleColumnLayout } from "../../Inventory/components/Layout/Layout2x1";
import InventoryPicker from "../../Inventory/components/Picker/Picker";
import type { ExportOptions } from "../../stores/definitions/Search";
import { hasLocation } from "../../stores/models/HasLocation";
import type { ListOfMaterials } from "../../stores/models/MaterialsModel";
import getRootStore from "../../stores/stores/getRootStore";
import useStores from "../../stores/use-stores";
import { showToastWhilstPending } from "../../util/alerts";
import { preventEventBubbling } from "../../util/Util";
import MaterialsTable from "./MaterialsTable";
import PopoutPrintIcon from "./PopoutPrintIcon";

type CardWrapperArgs = {
  children: React.ReactNode;
};

const barWrapperSx = {
  display: "flex",
  alignSelf: "center",
  width: "95%",
  flexDirection: "column",
  alignItems: "center",
};

const spacedBetweenRowSx = {
  display: "flex",
  flexDirection: "row",
  justifyContent: "space-between",
  width: "100%",
};

const disabledBlackInputSx = {
  "& input": {
    color: "black",
  },
};

const textFieldSx = {
  mx: 0.5,
  fontWeight: "normal",
};

const hideWhenPrintingSx = {
  "@media print": { display: "none" },
};

const disableBackgroundSx = (openSlide: boolean) => ({
  transition: "all 225ms ease-in-out",
  filter: openSlide ? "grayscale(1) opacity(0.3)" : "none",
  pointerEvents: openSlide ? "none" : "unset",
});

const CardWrapper = forwardRef<React.ElementRef<typeof Grid>, CardWrapperArgs & React.ComponentProps<typeof Grid>>(
  ({ children, className, ...gridProps }, ref) => {
    const isSingleColumnLayout = useIsSingleColumnLayout();

    return (
      <Observer>
        {() => (
          <Grid
            {...gridProps}
            sx={{
              position: "absolute",
              right: 0,
              top: 0,
              bottom: 0,
              zIndex: 100,
              width: "100%",
            }}
            className={className}
            ref={ref}
            onClick={preventEventBubbling()}
            size={isSingleColumnLayout ? 12 : 9}
          >
            {children}
          </Grid>
        )}
      </Observer>
    );
  },
);

CardWrapper.displayName = "CardWrapper";

function BigButton({ icon, onClick }: { onClick: () => void; icon: React.ReactNode }): React.ReactNode {
  return (
    <IconButton
      component="div"
      size="small"
      color="primary"
      onClick={onClick}
      sx={{
        p: 0,
        cursor: "pointer",
        m: 0.25,
        "& svg": { width: "2rem", height: "2rem" },
      }}
    >
      {icon}
    </IconButton>
  );
}

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
    return (
      <Box sx={[barWrapperSx, { mb: isSingleColumn ? 2 : 0.5 }]}>
        <Box sx={spacedBetweenRowSx}>
          <TextField
            variant="standard"
            sx={[textFieldSx, !canEdit && disabledBlackInputSx, { flex: 5 }]}
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
            sx={[textFieldSx, { flex: 1 }]}
            label="ID"
            margin="dense"
            value={currentList?.id ?? "-"}
            disabled
          />
        </Box>
        <TextField
          variant="standard"
          sx={[textFieldSx, !canEdit && disabledBlackInputSx]}
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
      </Box>
    );
  },
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
    const { moveStore, materialsStore } = useStores();
    const anyDataInList = (currentList?.materials.length ?? -1) > 0;
    const originalList = materialsStore.originalList;
    const canEditQuantities = currentList?.canEditQuantities;
    const editingMode = currentList?.editingMode;
    const currentUser = getRootStore().peopleStore.currentUser;

    const allOnBench =
      currentUser === null
        ? false
        : (currentList?.materials.every((m) => {
            return hasLocation(m.invRec)
              .map((r) => currentUser && r.isDirectlyOnWorkbenchOfUser(currentUser))
              .orElse(true);
          }) ?? false);

    const moveAllToBenchValidation = () => {
      if (currentList?.materials.length === 0) return IsInvalid("Nothing to move.");
      if (originalList && !currentList?.isEqual(originalList))
        return IsInvalid("Cannot move whilst there are unsaved changes.");
      if (allOnBench) return IsInvalid("All items are already on your bench.");
      return IsValid();
    };

    return (
      <Grid container spacing={2} sx={{ ...hideWhenPrintingSx, mb: 1 }}>
        <Grid>
          <Button
            color="primary"
            variant="contained"
            disableElevation
            onClick={preventEventBubbling<React.MouseEvent<HTMLButtonElement>>(() => {
              setOpenPicker(true);
            })}
            disabled={!canEdit}
          >
            Add items
          </Button>
        </Grid>
        <Grid>
          <Button
            color="primary"
            variant="contained"
            disableElevation
            onClick={preventEventBubbling<React.MouseEvent<HTMLButtonElement>>(() => {
              currentList?.setEditingMode(!editingMode);
            })}
            disabled={!canEditQuantities}
          >
            {editingMode ? "Close Quantity Editor" : "Edit Quantities"}
          </Button>
        </Grid>
        <Grid>
          <ValidatingSubmitButton
            onClick={() => {
              void (async () => {
                if (currentList) {
                  await currentList.moveAllToBench();
                }
              })();
            }}
            loading={moveStore.submitting === "TO-OTHER"}
            validationResult={moveAllToBenchValidation()}
            color="primary"
          >
            Move all to my bench
          </ValidatingSubmitButton>
        </Grid>
        {!standalonePage && currentList?.id !== null && (
          <Grid>
            <CustomTooltip title="View in new tab">
              <BigButton icon={<PopoutPrintIcon />} onClick={onOpenStandalone} />
            </CustomTooltip>
          </Grid>
        )}
        {standalonePage && (
          <Grid>
            <BigButton
              icon={<PrintIcon />}
              onClick={() => {
                window.print();
              }}
            />
          </Grid>
        )}
        {anyDataInList && (
          <Grid>
            <Typography variant="inherit" component="p" sx={{ margin: 0 }}>
              Tip: to edit an item click its Global ID, then the Edit button in the new browser tab.
            </Typography>
          </Grid>
        )}
      </Grid>
    );
  },
);

type DialogArgs = {
  open: boolean;
  setOpen: (open: boolean) => void;
  standalonePage?: boolean;
};

function MaterialsDialog({ open, setOpen, standalonePage = false }: DialogArgs): React.ReactNode {
  const { materialsStore } = useStores();
  const isSingleColumn = useIsSingleColumnLayout();
  const fullScreen = isSingleColumn || standalonePage;

  const [openPicker, setOpenPicker] = useState<boolean>(false);
  const [openExporter, setOpenExporter] = useState<boolean>(false);

  const openSlide = openPicker || openExporter;

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
      "listOfMaterials",
    ),
  );

  const refetch = () => {
    if (currentList) void materialsStore.getFieldMaterialsListings(currentList.elnFieldId);
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
        const opener = window.opener as Window | null;
        if (opener) opener.postMessage("deleted", window.origin);
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
    <Analytics>
      <ErrorBoundary topOfViewport>
        <Portal>
          <Alerts>
            <DialogBoundary>
              <Dialog
                onClose={() => {
                  materialsStore.setCurrentList(materialsStore.originalList);
                  setOpen(false);
                }}
                open={open}
                maxWidth="lg"
                fullWidth
                fullScreen={fullScreen}
                slotProps={{
                  paper: {
                    sx: {
                      overflow: "hidden",
                      // this is to avoid intercom help button
                      maxHeight: fullScreen ? "unset" : "86vh",
                      // this is to ensure the picker has enough height even when list is empty
                      minHeight: "86vh",
                    },
                  },
                }}
                onClick={() => {
                  setOpenPicker(false);
                  setOpenExporter(false);
                }}
              >
                <DialogTitle sx={{ pb: 0.5 }}>
                  {currentList?.id === undefined && "New "} List of Materials (Inventory)&nbsp;
                  <HelpLinkIcon link={docLinks.listOfMaterials} title="Info on using Lists of Materials." />
                  {!isSingleColumn && (
                    <MetadataBar currentList={currentList} canEdit={canEdit} isSingleColumn={false} />
                  )}
                </DialogTitle>
                <DialogContent
                  sx={{
                    overscrollBehavior: "contain",
                    WebkitOverflowScrolling: "unset",
                  }}
                >
                  <Grid container>
                    <Grid sx={disableBackgroundSx(openSlide)} size={12}>
                      {isSingleColumn && (
                        <MetadataBar currentList={currentList} canEdit={canEdit} isSingleColumn={isSingleColumn} />
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
                      {currentList && currentList.materials.length === 0 && (
                        <Typography component="div" variant="body2" color="textPrimary" align="center">
                          Use &quot;Add items&quot; to add materials to this list.
                        </Typography>
                      )}
                    </Grid>
                    <Slide in={openSlide} direction="left">
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
                            selectedResults={currentList.materials.map((m) => m.invRec)}
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
                  <Box sx={{ mr: 3 }}>
                    <WarningBar />
                  </Box>
                )}
                <DialogActions sx={[barWrapperSx, disableBackgroundSx(openSlide), hideWhenPrintingSx]}>
                  <Box sx={spacedBetweenRowSx}>
                    <Button
                      color="primary"
                      variant="contained"
                      disableElevation
                      onClick={preventEventBubbling<React.MouseEvent<HTMLButtonElement>>(() => {
                        void (async () => {
                          if (currentList) {
                            const changed = materialsStore.hasListChanged;
                            if (changed) {
                              await showToastWhilstPending(`Saving changes...`, currentList.update());
                              materialsStore.setCurrentList(currentList);
                              refetch();
                            }
                            setOpenExporter(true);
                          }
                        })();
                      })}
                      disabled={!isListExisting || !isListValid}
                    >
                      Export
                    </Button>
                    {isListExisting && (
                      <Button
                        sx={[{ color: "warningRed" }, { mx: 1 }]}
                        disableElevation
                        onClick={() => void confirmListDeletion()}
                        disabled={!canEdit}
                      >
                        Delete List
                      </Button>
                    )}
                    <div>
                      <Button
                        sx={{ mx: 1 }}
                        onClick={
                          isUnchanged
                            ? () => setOpen(false)
                            : () => {
                                materialsStore.setCurrentList(materialsStore.originalList);
                                if (isListNew) setOpen(false);
                              }
                        }
                      >
                        {isUnchanged ? "Close" : "Cancel"}
                      </Button>
                      <ValidatingSubmitButton
                        onClick={() => {
                          void (async () => {
                            if (currentList && isListValid) {
                              if (isListNew) {
                                await showToastWhilstPending(`Creating list...`, currentList.create());
                              }
                              if (isListExisting) {
                                const changed = materialsStore.hasListChanged;
                                if (changed) await showToastWhilstPending(`Updating list...`, currentList.update());
                              }
                              materialsStore.setCurrentList(currentList);
                              refetch();
                            }
                          })();
                        }}
                        loading={isListLoading}
                        validationResult={materialsStore.cantSaveCurrentList}
                      >
                        Save
                      </ValidatingSubmitButton>
                    </div>
                  </Box>
                </DialogActions>
                <Confirm />
              </Dialog>
            </DialogBoundary>
          </Alerts>
        </Portal>
      </ErrorBoundary>
    </Analytics>
  );
}

export default observer(MaterialsDialog);
