import React, { useState, useEffect, useContext } from "react";
import Button from "@mui/material/Button";
import { Dialog, DialogBoundary } from "../../components/DialogBoundary";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import Grid from "@mui/material/Grid";
import { withStyles } from "Styles";
import { observer } from "mobx-react-lite";
import SubmitSpinnerButton from "../../components/SubmitSpinnerButton";
import Typography from "@mui/material/Typography";
import { type UseState } from "../../util/types";
import {
  type PlanSummary,
  fetchPlanSummaries,
  type SearchParameters,
} from "./PlanSummary";
import StringField from "../../components/Inputs/StringField";
import DropdownButton from "../../components/DropdownButton";
import Popover from "@mui/material/Popover";
import Chip from "@mui/material/Chip";
import { importPlan } from "./ImportIntoGallery";
import TablePagination from "@mui/material/TablePagination";
import { paginationOptions, DataGridColumn } from "../../util/table";
import AlertContext, { mkAlert } from "../../stores/contexts/Alert";
import useViewportDimensions from "../../util/useViewportDimensions";
import Portal from "@mui/material/Portal";
import ValidatingSubmitButton, {
  IsInvalid,
  IsValid,
} from "../../components/ValidatingSubmitButton";
import docLinks from "../../assets/DocLinks";
import Link from "@mui/material/Link";
import createAccentedTheme from "../../accentedTheme";
import { ThemeProvider } from "@mui/material/styles";
import { DataGrid, GridRowSelectionModel } from "@mui/x-data-grid";
import Radio from "@mui/material/Radio";
import Stack from "@mui/material/Stack";
import DialogTitle from "@mui/material/DialogTitle";
import AppBar from "../../components/AppBar";
import { ACCENT_COLOR } from "../../assets/branding/argos";

const CustomTablePagination = withStyles<
  React.ComponentProps<typeof TablePagination> & { component?: string },
  { root: string }
>(() => ({
  root: {
    overflow: "unset",
  },
}))((props) => (
  <nav>
    <TablePagination
      labelRowsPerPage=""
      component="div"
      {...props}
      backIconButtonProps={{ size: "small" }}
      nextIconButtonProps={{ size: "small" }}
    />
  </nav>
));

const Panel = ({
  anchorEl,
  children,
  onClose,
  onSubmit,
}: {
  anchorEl: HTMLElement | null;
  children: React.ReactNode;
  onClose: () => void;
  onSubmit: () => void;
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
    PaperProps={{
      variant: "outlined",
      elevation: 0,
      style: {
        minWidth: 300,
      },
    }}
  >
    <form
      onSubmit={(e) => {
        e.preventDefault();
        onSubmit();
        onClose();
      }}
    >
      {Boolean(anchorEl) && children}
    </form>
  </Popover>
);

const CustomStringField = ({
  onChange,
  value,
  autoFocus = false,
  fullWidth = false,
}: {
  onChange: (newValue: string) => void;
  value: string;
  autoFocus?: boolean;
  fullWidth?: boolean;
}) => (
  <StringField
    value={value}
    onChange={({ target: { value: newValue } }) => onChange(newValue)}
    // eslint-disable-next-line jsx-a11y/no-autofocus
    autoFocus={autoFocus}
    fullWidth={fullWidth}
  />
);

type SearchControlArgs = {
  name: string;
  value: string | null;
  onChange: (newValue: string) => void;
  onSubmit: () => void;
};

const Search = ({ name, value, onChange, onSubmit }: SearchControlArgs) => {
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);

  return (
    <Grid item>
      <DropdownButton
        name={name}
        onClick={(e) => {
          setAnchorEl(e.currentTarget);
        }}
      >
        <Panel
          anchorEl={anchorEl}
          onClose={() => setAnchorEl(null)}
          onSubmit={() => onSubmit()}
        >
          <CustomStringField
            value={value ?? ""}
            onChange={(newValue) => onChange(newValue)}
            // eslint-disable-next-line jsx-a11y/no-autofocus
            autoFocus={true}
            fullWidth={true}
          />
        </Panel>
      </DropdownButton>
    </Grid>
  );
};

type ChipArgs = {
  name: string;
  value: string | null;
  onDelete: () => void;
};

const CustomChip = withStyles<ChipArgs, { label: string; root: string }>(
  () => ({
    label: {
      letterSpacing: "0.02em",
      padding: "4px 12px",
    },
    root: {
      maxWidth: "100%",
    },
  })
)(({ name, value, onDelete, classes }) => {
  if (!value) return null;
  return (
    <Grid item>
      <Chip
        size="small"
        label={`${name}: ${value}`}
        onDelete={onDelete}
        classes={classes}
      />
    </Grid>
  );
});

type SearchControlsArgs = {
  setDMPs: (dmps: Array<PlanSummary>) => void;
  fetching: boolean;
  setFetching: (fetching: boolean) => void;
  setTotalCount: (totalCount: number) => void;
  page: number;
  pageSize: number;
  setPage: (page: number) => void;
};

const SearchControls = ({
  setDMPs,
  fetching,
  setFetching,
  setTotalCount,
  page,
  pageSize,
  setPage,
}: SearchControlsArgs) => {
  const { addAlert } = useContext(AlertContext);
  const [searchParameters, setSearchParameters]: UseState<
    Omit<SearchParameters, "page" | "pageSize">
  > = useState({
    like: null as string | null,
    grantsLike: null as string | null,
    fundersLike: null as string | null,
    collaboratorsLike: null as string | null,
  });
  const [appliedSearchParameters, setAppliedSearchParameters]: UseState<
    Omit<SearchParameters, "page" | "pageSize">
  > = useState({
    like: null as string | null,
    grantsLike: null as string | null,
    fundersLike: null as string | null,
    collaboratorsLike: null as string | null,
  });
  const modifySearchParameters = (
    newSearchParameters: Omit<SearchParameters, "page" | "pageSize">
  ) => {
    setSearchParameters(newSearchParameters);
    setPage(0);
  };

  const getDMPs = async (
    newSearchParameters: Omit<SearchParameters, "page" | "pageSize">
  ) => {
    setFetching(true);
    setDMPs([]);
    try {
      const plans = await fetchPlanSummaries({
        ...newSearchParameters,
        page,
        pageSize,
      });
      setTotalCount(plans.totalCount);
      setDMPs(plans.data);
      setAppliedSearchParameters(newSearchParameters);
    } catch (e) {
      console.error(e);
      const errorMsg = typeof e === "string" ? e : "Could not get DMPs";
      addAlert(
        mkAlert({
          title: "Fetch failed.",
          message: errorMsg,
          variant: "error",
        })
      );
    } finally {
      setFetching(false);
    }
  };

  useEffect(() => {
    void getDMPs(searchParameters);
  }, [page, pageSize]);

  return (
    <Grid container direction="column" spacing={1}>
      <Grid
        item
        container
        direction="row"
        spacing={1}
        role="group"
        aria-label="Search filters"
      >
        <CustomChip
          name="Label"
          value={appliedSearchParameters.like}
          onDelete={() => {
            const newSearchParametes = {
              ...appliedSearchParameters,
              like: null,
            };
            modifySearchParameters(newSearchParametes);
            void getDMPs(newSearchParametes);
          }}
        />
        <CustomChip
          name="Grant"
          value={appliedSearchParameters.grantsLike}
          onDelete={() => {
            const newSearchParametes = {
              ...appliedSearchParameters,
              grantsLike: null,
            };
            modifySearchParameters(newSearchParametes);
            void getDMPs(newSearchParametes);
          }}
        />
        <CustomChip
          name="Funder"
          value={appliedSearchParameters.fundersLike}
          onDelete={() => {
            const newSearchParametes = {
              ...appliedSearchParameters,
              fundersLike: null,
            };
            modifySearchParameters(newSearchParametes);
            void getDMPs(newSearchParametes);
          }}
        />
        <CustomChip
          name="Collaborators"
          value={appliedSearchParameters.collaboratorsLike}
          onDelete={() => {
            const newSearchParametes = {
              ...appliedSearchParameters,
              collaboratorsLike: null,
            };
            modifySearchParameters(newSearchParametes);
            void getDMPs(newSearchParametes);
          }}
        />
      </Grid>
      <Grid item>
        <Grid container direction="row" spacing={1} alignItems="end">
          <Search
            name="Label"
            value={searchParameters.like}
            onChange={(like) =>
              setSearchParameters({
                ...searchParameters,
                like,
              })
            }
            onSubmit={() => {
              setPage(0);
              void getDMPs(searchParameters);
            }}
          />
          <Search
            name="Grant"
            value={searchParameters.grantsLike}
            onChange={(grantsLike) =>
              modifySearchParameters({
                ...searchParameters,
                grantsLike,
              })
            }
            onSubmit={() => {
              setPage(0);
              void getDMPs(searchParameters);
            }}
          />
          <Search
            name="Funder"
            value={searchParameters.fundersLike}
            onChange={(fundersLike) =>
              modifySearchParameters({
                ...searchParameters,
                fundersLike,
              })
            }
            onSubmit={() => {
              setPage(0);
              void getDMPs(searchParameters);
            }}
          />
          <Search
            name="Collaborators"
            value={searchParameters.collaboratorsLike}
            onChange={(collaboratorsLike) =>
              setSearchParameters({
                ...searchParameters,
                collaboratorsLike,
              })
            }
            onSubmit={() => {
              setPage(0);
              void getDMPs(searchParameters);
            }}
          />
          <Grid item>
            <SubmitSpinnerButton
              onClick={() => {
                void getDMPs(searchParameters);
              }}
              disabled={fetching}
              loading={fetching}
              label="Refresh"
              type="submit"
              size="small"
            />
          </Grid>
        </Grid>
      </Grid>
    </Grid>
  );
};

const CustomDialog = withStyles<
  { fullScreen: boolean } & React.ComponentProps<typeof Dialog>,
  { paper?: string }
>((theme, { fullScreen }) => ({
  paper: {
    overflow: "hidden",
    margin: fullScreen ? 0 : theme.spacing(2.625),
    maxHeight: "unset",
    minHeight: "unset",

    // this is so that the hights of the dialog's content of constrained and scrollbars appear
    // 24px margin above and below, 3px border above and below
    height: fullScreen ? "100%" : "calc(100% - 48px)",
  },
}))(Dialog);

function DMPDialogContent({
  setOpen,
}: {
  setOpen: (open: boolean) => void;
}): React.ReactNode {
  const { addAlert } = useContext(AlertContext);
  const { isViewportSmall } = useViewportDimensions();

  const [DMPs, setDMPs] = useState<Array<PlanSummary>>([]);
  const [totalCount, setTotalCount] = useState<number>(0);
  const [selectedPlan, setSelectedPlan] = useState<PlanSummary | null>(null);
  const [pageSize, setPageSize]: UseState<number> = useState(10);
  const [page, setPage]: UseState<number> = useState(0);

  const [fetching, setFetching] = useState(false);
  const [importing, setImporting] = useState(false);

  const handleImport = async () => {
    if (!selectedPlan) throw new Error("No plan is selected.");
    const selectedPlanId: string = `${selectedPlan.id}`;
    setImporting(true);
    try {
      await importPlan(selectedPlan);
      addAlert(
        mkAlert({
          title: "Success.",
          message: `DMP ${selectedPlanId} was successfully imported.`,
          variant: "success",
        })
      );

      /*
       * Instead of automatically closing the dialog, which would end up not
       * showing the success toast, we set the selected plan to null. This turns
       * the cancel button into a close button, which the user can then manually
       * close the dialog.
       */
      setSelectedPlan(null);
    } catch (e) {
      console.error(e);
      addAlert(
        mkAlert({
          title: "Import failed.",
          message: "Could not import DMP",
          variant: "error",
        })
      );
    } finally {
      setImporting(false);
    }
  };

  return (
    <>
      <AppBar
        variant="dialog"
        currentPage="Argos"
        accessibilityTips={{
          supportsHighContrastMode: true,
        }}
        helpPage={{
          docLink: docLinks.argos,
          title: "Argos help",
        }}
      />
      <DialogTitle variant="h3">Import a DMP into the Gallery</DialogTitle>
      <DialogContent>
        <Grid
          container
          direction="column"
          spacing={2}
          flexWrap="nowrap"
          // this is so that just the table is scrollable
          height="calc(100% + 16px)"
        >
          <Grid item>
            <Typography variant="body2">
              Importing a DMP from <strong>argos.openaire.eu</strong> will make
              it available to view and reference within RSpace.
            </Typography>
            <Typography variant="body2">
              See{" "}
              <Link href="https://argos.openaire.eu">argos.openaire.eu</Link>{" "}
              and our <Link href={docLinks.argos}>Argos integration docs</Link>{" "}
              for more.
            </Typography>
          </Grid>
          <Grid item>
            <SearchControls
              setDMPs={setDMPs}
              fetching={fetching}
              setFetching={setFetching}
              setTotalCount={setTotalCount}
              page={page}
              pageSize={pageSize}
              setPage={setPage}
            />
          </Grid>
          <Grid item sx={{ overflowY: "auto" }} flexGrow={1}>
            <DataGrid
              columns={[
                {
                  field: "radio",
                  headerName: "Select",
                  renderCell: (params: { row: PlanSummary }) => (
                    <Radio
                      color="primary"
                      value={selectedPlan?.id === params.row.id}
                      checked={selectedPlan?.id === params.row.id}
                      inputProps={{ "aria-label": "Plan selection" }}
                    />
                  ),
                  hideable: false,
                  width: 70,
                  flex: 0,
                  disableColumnMenu: true,
                  sortable: false,
                },
                DataGridColumn.newColumnWithFieldName<"label", PlanSummary>(
                  "label",
                  {
                    headerName: "Label",
                    flex: 1,
                    sortable: false,
                  }
                ),
                DataGridColumn.newColumnWithFieldName<"id", PlanSummary>("id", {
                  headerName: "Id",
                  flex: 1,
                  sortable: false,
                }),
                DataGridColumn.newColumnWithFieldName<"grant", PlanSummary>(
                  "grant",
                  {
                    headerName: "Grant",
                    flex: 1,
                    sortable: false,
                  }
                ),
                DataGridColumn.newColumnWithValueMapper<
                  "createdAt",
                  PlanSummary
                >(
                  "createdAt",
                  (createdAt) => new Date(createdAt).toLocaleString(),
                  {
                    headerName: "Created At",
                    flex: 1,
                    sortable: false,
                  }
                ),
                DataGridColumn.newColumnWithValueMapper<
                  "modifiedAt",
                  PlanSummary
                >(
                  "modifiedAt",
                  (modifiedAt) => new Date(modifiedAt).toLocaleString(),
                  {
                    headerName: "Modified At",
                    flex: 1,
                    sortable: false,
                  }
                ),
              ]}
              rows={fetching ? [] : DMPs}
              initialState={{
                columns: {
                  columnVisibilityModel: {
                    id: !isViewportSmall,
                    grant: false,
                    createdAt: false,
                    modifiedAt: false,
                  },
                },
              }}
              density="compact"
              disableColumnFilter
              hideFooter
              slots={{
                pagination: null,
              }}
              localeText={{
                noRowsLabel: "No DMPs",
              }}
              loading={fetching}
              getRowId={(row) => row.id}
              onRowSelectionModelChange={(
                newSelection: GridRowSelectionModel
              ) => {
                if (newSelection[0]) {
                  setSelectedPlan(
                    DMPs.find((d) => d.id === newSelection[0]) ?? null
                  );
                }
              }}
              getRowHeight={() => "auto"}
              onCellKeyDown={({ id }, e) => {
                if (e.key === " " || e.key === "Enter") {
                  setSelectedPlan(DMPs.find((d) => d.id === id) ?? null);
                  e.stopPropagation();
                }
              }}
            />
          </Grid>
        </Grid>
      </DialogContent>
      <DialogActions>
        <Grid container direction="row" spacing={1}>
          <Grid item>
            <CustomTablePagination
              count={totalCount}
              rowsPerPageOptions={paginationOptions(totalCount)}
              labelRowsPerPage=""
              component="div"
              rowsPerPage={Math.min(pageSize, totalCount)}
              SelectProps={{
                renderValue: (value: unknown): React.ReactNode => {
                  if (typeof value !== "number")
                    throw new Error("Invalid value");
                  return value < totalCount ? value : `${value} (All)`;
                },
              }}
              page={page}
              onPageChange={(_: unknown, newPage: number) => {
                setPage(newPage);
              }}
              onRowsPerPageChange={(e) => {
                setPageSize(parseInt(e.target.value, 10));
                setPage(0);
              }}
            />
          </Grid>
          <Grid item sx={{ ml: "auto" }}>
            <Stack direction="row" spacing={1}>
              <Button onClick={() => setOpen(false)} disabled={importing}>
                {selectedPlan ? "Cancel" : "Close"}
              </Button>
              <ValidatingSubmitButton
                onClick={() => {
                  void handleImport();
                }}
                validationResult={
                  !selectedPlan ? IsInvalid("No DMP selected.") : IsValid()
                }
                loading={importing}
              >
                Import
              </ValidatingSubmitButton>
            </Stack>
          </Grid>
        </Grid>
      </DialogActions>
    </>
  );
}

type DMPDialogArgs = {
  open: boolean;
  setOpen: (open: boolean) => void;
};

/*
 * This simple function just for the outer-most components is so that the
 * content of the dialog can use the Alerts context
 *
 * A11y: note that tabbing through this dialog is not possible because the
 * custom tabbing behaviour of the Gallery page takes control of the tab key
 * events away from the React+MUI tech stack. See ../../../../scripts/global.js
 */
function DMPDialog({ open, setOpen }: DMPDialogArgs): React.ReactNode {
  const { isViewportSmall } = useViewportDimensions();

  /*
   * We use DialogBoundary to wrap the Dialog so that Alerts can be shown atop the dialog whilst
   * keeping them accessible to screen readers. We then have to manually add Portal back (Dialogs
   * normally include a Portal) so that the Dialog isn't rendered inside the Menu where it will
   * not be seen once the Menu is closed.
   */

  return (
    <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
      <Portal>
        <DialogBoundary>
          <CustomDialog
            onClose={() => {
              setOpen(false);
            }}
            open={open}
            maxWidth="lg"
            fullWidth
            fullScreen={isViewportSmall}
          >
            <DMPDialogContent setOpen={setOpen} />
          </CustomDialog>
        </DialogBoundary>
      </Portal>
    </ThemeProvider>
  );
}

export default observer(DMPDialog);
