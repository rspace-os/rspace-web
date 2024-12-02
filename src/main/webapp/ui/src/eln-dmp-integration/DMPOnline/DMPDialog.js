//@flow

import React, { type Node, type ElementProps } from "react";
import { Dialog, DialogBoundary } from "../../components/DialogBoundary";
import Portal from "@mui/material/Portal";
import useViewportDimensions from "../../util/useViewportDimensions";
import { withStyles } from "Styles";
import DialogContent from "@mui/material/DialogContent";
import DialogActions from "@mui/material/DialogActions";
import Button from "@mui/material/Button";
import Grid from "@mui/material/Grid";
import Typography from "@mui/material/Typography";
import {
  DataGrid,
  GridToolbarContainer,
  GridToolbarColumnsButton,
  GridToolbarDensitySelector,
} from "@mui/x-data-grid";
import Radio from "@mui/material/Radio";
import { makeStyles } from "tss-react/mui";
import { ThemeProvider } from "@mui/material/styles";
import Link from "@mui/material/Link";
import * as FetchingData from "../../util/fetchingData";
import {
  useDmpOnlineEndpoint,
  type DmpListing,
  type DmpSummary,
} from "./useDmpOnlineEndpoint";
import docLinks from "../../assets/DocLinks";
import NoValue from "../../components/NoValue";
import { doNotAwait } from "../../util/Util";
import createAccentedTheme from "../../accentedTheme";
import Toolbar from "@mui/material/Toolbar";
import AppBar from "@mui/material/AppBar";
import AccessibilityTips from "../../components/AccessibilityTips";
import HelpLinkIcon from "../../components/HelpLinkIcon";
import Box from "@mui/material/Box";
import ValidatingSubmitButton, {
  IsValid,
  IsInvalid,
} from "../../components/ValidatingSubmitButton";
import { DataGridColumn } from "../../util/table";

const COLOR = {
  main: {
    hue: 40,
    saturation: 100,
    lightness: 46,
  },
  darker: {
    hue: 39,
    saturation: 93,
    lightness: 33,
  },
  contrastText: {
    hue: 39,
    saturation: 35,
    lightness: 26,
  },
  background: {
    hue: 32,
    saturation: 100,
    lightness: 68,
  },
  backgroundContrastText: {
    hue: 39,
    saturation: 20,
    lightness: 35,
  },
};

const useStyles = makeStyles()((theme, props) => ({
  table: {
    display:
      typeof props === "undefined"
        ? "none"
        : FetchingData.match(props.listing, {
            loading: () => "none",
            error: () => "none",
            success: () => "unset",
          }),
  },
}));

const CustomDialog = withStyles<
  {| fullScreen: boolean, ...ElementProps<typeof Dialog> |},
  {| paper?: string |}
>((theme, { fullScreen }) => ({
  paper: {
    // this is to avoid intercom help button
    maxHeight: fullScreen ? "unset" : "86vh",
  },
}))(Dialog);

const DMPDialogContent = ({ setOpen }: { setOpen: (boolean) => void }) => {
  const [selection, setSelection] = React.useState<DmpSummary | null>(null);
  const [importing, setImporting] = React.useState(false);

  const { firstPage } = useDmpOnlineEndpoint();
  const [listing, setListing] =
    React.useState<FetchingData.Fetched<DmpListing>>(firstPage);
  const { classes } = useStyles({ listing });

  React.useEffect(() => {
    setListing(firstPage);
  }, [firstPage]);

  const onSubmit = async (e: Event) => {
    e.preventDefault();
    if (!selection) return;
    setImporting(true);
    try {
      await selection.importIntoGallery();
    } finally {
      setImporting(false);
    }
  };

  const columns = [
    {
      field: "radio",
      headerName: "Select",
      renderCell: (params: { row: DmpSummary, ... }) => (
        <Radio
          color="primary"
          value={selection?.id === params.row.id}
          checked={selection?.id === params.row.id}
          inputProps={{ "aria-label": "Plan selection" }}
        />
      ),
      hideable: false,
      width: 70,
      flex: 0,
      disableColumnMenu: true,
    },
    // $FlowExpectedError[class-object-subtyping]
    DataGridColumn.newColumnWithFieldName<DmpSummary, _>("title", {
      headerName: "Title",
      hideable: false,
    }),
    {
      field: "contact name",
      headerName: "Contact Name",
      renderCell: (params: { row: DmpSummary, ... }) =>
        params.row.contactName.orElse(<NoValue label="Not Specified" />),
    },
    {
      field: "contact affiliation",
      headerName: "Contact Affiliation",
      renderCell: (params: { row: DmpSummary, ... }) =>
        params.row.contactAffiliationName.orElse(
          <NoValue label="Not Specified" />
        ),
    },
    // $FlowExpectedError[class-object-subtyping]
    DataGridColumn.newColumnWithFieldName<DmpSummary, _>("created", {
      headerName: "Created",
    }),
    // $FlowExpectedError[class-object-subtyping]
    DataGridColumn.newColumnWithFieldName<DmpSummary, _>("modified", {
      headerName: "Modified",
    }),
  ].map((colDefinition) => ({
    sortable: false,
    flex: 1,
    ...colDefinition,
  }));

  return (
    <>
      <AppBar position="relative" open={true}>
        <Toolbar variant="dense">
          <Typography variant="h6" noWrap component="h2">
            DMPonline
          </Typography>
          <Box flexGrow={1}></Box>
          <Box ml={1}>
            <AccessibilityTips supportsHighContrastMode elementType="dialog" />
          </Box>
          <Box ml={1} sx={{ transform: "translateY(2px)" }}>
            <HelpLinkIcon title="DMPonline help" link={docLinks.dmponline} />
          </Box>
        </Toolbar>
      </AppBar>
      <Box sx={{ display: "flex", height: "calc(100% - 48px)" }}>
        <Box
          sx={{
            height: "100%",
            display: "flex",
            flexDirection: "column",
            flexGrow: 1,
          }}
        >
          <form onSubmit={onSubmit}>
            <DialogContent>
              <Grid container direction="column" spacing={2}>
                <Grid item>
                  <Typography variant="h3">
                    Import a DMP into the Gallery
                  </Typography>
                </Grid>
                <Grid item>
                  <Typography variant="body2">
                    Importing a DMP from <strong>dmponline.dcc.ac.uk</strong>{" "}
                    will make it available to view and reference within RSpace.
                  </Typography>
                  <Typography variant="body2">
                    See{" "}
                    <Link href="https://dmponline.dcc.ac.uk">
                      dmponline.dcc.ac.uk
                    </Link>{" "}
                    and our{" "}
                    <Link href={docLinks.dmponline}>
                      DMPonline integration docs
                    </Link>{" "}
                    for more.
                  </Typography>
                </Grid>
                <Grid item sx={{ pr: 2, mt: 1 }}>
                  {FetchingData.match(listing, {
                    loading: () => (
                      <Typography variant="body2">
                        Loading listing of DMPs.
                      </Typography>
                    ),
                    error: (error) => (
                      <>
                        <Typography variant="body2">
                          Failed to load listing of DMPs. Please try refreshing.
                        </Typography>
                        <samp>{error}</samp>
                      </>
                    ),
                    success: () => <></>,
                  })}
                  <DataGrid
                    rows={FetchingData.match(listing, {
                      loading: () => ([]: Array<DmpSummary>),
                      error: () => ([]: Array<DmpSummary>),
                      success: (l) => l.dmps,
                    })}
                    columns={columns}
                    initialState={{
                      columns: {
                        columnVisibilityModel: {
                          created: false,
                          modified: false,
                        },
                      },
                    }}
                    disableColumnFilter
                    density="compact"
                    getRowId={(row: DmpSummary) => row.id}
                    onRowSelectionModelChange={(
                      newSelection: $ReadOnlyArray<DmpSummary["id"]>
                    ) => {
                      FetchingData.match(listing, {
                        loading: () => {},
                        error: () => {},
                        success: (l) => {
                          if (newSelection[0]) {
                            setSelection(l.getById(newSelection[0]));
                          }
                        },
                      });
                    }}
                    hideFooterSelectedRowCount
                    paginationMode="server"
                    rowCount={FetchingData.match(listing, {
                      loading: () => 0,
                      error: () => 0,
                      success: (l) => l.totalCount,
                    })}
                    paginationModel={FetchingData.match(listing, {
                      loading: () => ({ page: 0, pageSize: 0 }),
                      error: () => ({ page: 0, pageSize: 0 }),
                      success: (l) => ({ page: l.page, pageSize: l.pageSize }),
                    })}
                    pageSizeOptions={[10, 25, 100]}
                    onPaginationModelChange={({
                      pageSize: newPageSize,
                      page: newPage,
                    }) => {
                      FetchingData.match(listing, {
                        loading: () => {},
                        error: () => {},
                        success: doNotAwait(async (l) => {
                          try {
                            if (newPage !== l.page) {
                              setListing({ tag: "loading" });
                              setListing({
                                tag: "success",
                                value: await l.setPage(newPage),
                              });
                            }
                          } catch (error) {
                            setListing({ tag: "error", error: error.message });
                          }
                          try {
                            if (newPageSize !== l.pageSize) {
                              setListing({ tag: "loading" });
                              setListing({
                                tag: "success",
                                value: await l.setPageSize(newPageSize),
                              });
                            }
                          } catch (error) {
                            setListing({ tag: "error", error: error.message });
                          }
                        }),
                      });
                    }}
                    slots={{
                      toolbar: () => (
                        <GridToolbarContainer>
                          <GridToolbarColumnsButton variant="outlined" />
                          <GridToolbarDensitySelector variant="outlined" />
                        </GridToolbarContainer>
                      ),
                    }}
                    className={classes.table}
                    {...FetchingData.match(listing, {
                      loading: () => ({ "aria-hidden": true }),
                      error: () => ({ "aria-hidden": true }),
                      success: () => ({}),
                    })}
                  />
                </Grid>
              </Grid>
            </DialogContent>
            <DialogActions>
              <Button onClick={() => setOpen(false)}>Close</Button>
              <ValidatingSubmitButton
                validationResult={
                  selection ? IsValid() : IsInvalid("No DMP is selected.")
                }
                loading={importing}
                onClick={(e) => void onSubmit(e)}
              >
                Import
              </ValidatingSubmitButton>
            </DialogActions>
          </form>
        </Box>
      </Box>
    </>
  );
};

type DMPDialogArgs = {|
  open: boolean,
  setOpen: (boolean) => void,
|};

/*
 * This simple function just for the outer-most components is so that the
 * content of the dialog can use the Alerts context
 *
 * A11y: note that tabbing through this dialog is not possible because the
 * custom tabbing behaviour of the Gallery page takes control of the tab key
 * events away from the React+MUI tech stack. See ../../../../scripts/global.js
 */
export default function DMPDialog({ open, setOpen }: DMPDialogArgs): Node {
  const { isViewportSmall } = useViewportDimensions();

  /*
   * We use DialogBoundary to wrap the Dialog so that Alerts can be shown atop the dialog whilst
   * keeping them accessible to screen readers. We then have to manually add Portal back (Dialogs
   * normally include a Portal) so that the Dialog isn't rendered inside the Menu where it will
   * not be seen once the Menu is closed.
   */

  return (
    <ThemeProvider theme={createAccentedTheme(COLOR)}>
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
