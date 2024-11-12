// @flow
import AddIcon from "@mui/icons-material/Add";
import React, { type Node, useContext, type ComponentType } from "react";
import useStores from "../../stores/use-stores";
import NavigateContext from "../../stores/contexts/Navigate";
import { UserCancelledAction } from "../../util/error";
import { styled, ThemeProvider } from "@mui/material/styles";
import Menu from "@mui/material/Menu";
import NewMenuItem from "../../eln/gallery/components/NewMenuItem";
import RecordTypeIcon from "../../components/RecordTypeIcon";
import Divider from "@mui/material/Divider";
import Button from "@mui/material/Button";
import Box from "@mui/material/Box";
import { observer } from "mobx-react-lite";
import Dialog from "@mui/material/Dialog";
import createAccentedTheme from "../../accentedTheme";
import Toolbar from "@mui/material/Toolbar";
import AppBar from "@mui/material/AppBar";
import AccessibilityTips from "../../components/AccessibilityTips";
import HelpLinkIcon from "../../components/HelpLinkIcon";
import DialogContent from "@mui/material/DialogContent";
import Grid from "@mui/material/Grid";
import Link from "@mui/material/Link";
import Typography from "@mui/material/Typography";
import FieldmarkIcon from "../../eln/apps/icons/fieldmark.svg";
import CardMedia from "@mui/material/CardMedia";

export const FIELDMARK_COLOR = {
  main: {
    hue: 82,
    saturation: 80,
    lightness: 33,
  },
  darker: {
    hue: 82,
    saturation: 80,
    lightness: 22,
  },
  contrastText: {
    hue: 82,
    saturation: 80,
    lightness: 19,
  },
  background: {
    hue: 82,
    saturation: 46,
    lightness: 66,
  },
  backgroundContrastText: {
    hue: 82,
    saturation: 70,
    lightness: 22,
  },
};

const StyledMenu = styled(Menu)(({ open }) => ({
  "& .MuiPaper-root": {
    ...(open
      ? {
          transform: "translate(-4px, 4px) !important",
          boxShadow: "none",
          border: `2px solid hsl(198deg, 37%, 80%)`,
        }
      : {}),
  },
}));

type CreateNewArgs = {|
  /**
   * Called whenever a menu item is clicked, and SHOULD cause the menu to
   * close.
   */
  onClick: () => void,
|};

/**
 * The menu for creating new items in Inventory, be that creating new items
 * from scratch by opening the create new forms for the respective record types
 * or to import data from outside sources.
 *
 * The button and menu is styled to be consistent with create menus across
 * other parts of the product, providing a consistent look-and-feel. The
 * button that triggers the menu is styled with the `callToAction` colour so
 * that the button stands out on the page.
 */
function CreateNew({ onClick }: CreateNewArgs): Node {
  const { searchStore, trackingStore, uiStore, importStore } = useStores();
  const { useNavigate } = useContext(NavigateContext);
  const navigate = useNavigate();
  const [anchorEl, setAnchorEl] = React.useState(null);
  const [fieldmarkOpen, setFieldmarkOpen] = React.useState(false);

  const handleCreate = async (
    recordType: "sample" | "container" | "template"
  ) => {
    trackingStore.trackEvent("CreateInventoryRecordClicked", {
      type: recordType,
    });
    try {
      const newRecord = await searchStore.createNew(recordType);
      onClick();
      const params = searchStore.fetcher.generateNewQuery(
        newRecord.showNewlyCreatedRecordSearchParams
      );
      navigate(`/inventory/search?${params.toString()}`, {
        modifyVisiblePanel: false,
      });
      setAnchorEl(null);
    } catch (e) {
      if (e instanceof UserCancelledAction) return;
      throw e;
    }
  };

  const handleImport = async (
    recordType: "SAMPLES" | "CONTAINERS" | "SUBSAMPLES"
  ) => {
    if (await uiStore.confirmDiscardAnyChanges()) {
      await importStore.initializeNewImport(recordType);
      navigate(`/inventory/import?recordType=${recordType}`);
      onClick();
      setAnchorEl(null);
    }
  };

  const controls = React.useId();
  return (
    <Box sx={{ p: 1.5, pt: 0 }}>
      <Button
        variant="contained"
        color="callToAction"
        fullWidth
        aria-controls={controls}
        aria-haspopup="true"
        onClick={(event) => setAnchorEl(event.currentTarget)}
        startIcon={
          <AddIcon
            style={{
              transition: window.matchMedia("(prefers-reduced-motion: reduce)")
                .matches
                ? "none"
                : "all .2s cubic-bezier(0.4, 0, 0.2, 1) .2s",
              transform: uiStore.sidebarOpen
                ? "translateX(0px)"
                : "translateX(33px)",
            }}
          />
        }
        sx={{ minWidth: "unset", overflow: "hidden" }}
      >
        <span
          style={{
            transition: window.matchMedia("(prefers-reduced-motion: reduce)")
              .matches
              ? "none"
              : "all .2s cubic-bezier(0.4, 0, 0.2, 1) .2s",
            transform: uiStore.sidebarOpen
              ? "translateX(0px)"
              : "translateX(46px)",
          }}
        >
          Create
        </span>
      </Button>
      <StyledMenu
        open={Boolean(anchorEl)}
        anchorEl={anchorEl}
        id={controls}
        keepMounted
        onClose={() => {
          setAnchorEl(null);
        }}
        MenuListProps={{
          disablePadding: true,
        }}
      >
        <NewMenuItem
          compact
          title="New Sample"
          avatar={
            <RecordTypeIcon
              record={{
                recordTypeLabel: "",
                iconName: "sample",
              }}
              color=""
              style={{
                width: "22px",
                height: "22px",
                backgroundColor: "hsl(198 37% 80% / 1)",
                padding: "5px",
                color: "hsl(198 13% 25% / 1)",
              }}
            />
          }
          backgroundColor={{ hue: 198, saturation: 37, lightness: 80 }}
          foregroundColor={{ hue: 198, saturation: 13, lightness: 25 }}
          onClick={() => {
            void handleCreate("sample");
          }}
        />
        <NewMenuItem
          compact
          title="New Container"
          avatar={
            <RecordTypeIcon
              record={{
                recordTypeLabel: "",
                iconName: "container",
              }}
              color=""
              style={{
                width: "22px",
                height: "22px",
                backgroundColor: "hsl(198 37% 80% / 1)",
                padding: "5px",
                color: "hsl(198 13% 25% / 1)",
              }}
            />
          }
          backgroundColor={{ hue: 198, saturation: 37, lightness: 80 }}
          foregroundColor={{ hue: 198, saturation: 13, lightness: 25 }}
          onClick={() => {
            void handleCreate("container");
          }}
        />
        <NewMenuItem
          compact
          title="New Template"
          avatar={
            <RecordTypeIcon
              record={{
                recordTypeLabel: "",
                iconName: "template",
              }}
              color=""
              style={{
                width: "32px",
                height: "32px",
                backgroundColor: "hsl(198 37% 80% / 1)",
                padding: "2px",
                paddingTop: "5px",
                paddingLeft: "5px",
                color: "hsl(198 13% 25% / 1)",
              }}
            />
          }
          backgroundColor={{ hue: 198, saturation: 37, lightness: 80 }}
          foregroundColor={{ hue: 198, saturation: 13, lightness: 25 }}
          onClick={() => {
            void handleCreate("template");
          }}
        />
        <Divider textAlign="left" aria-label="CSV import">
          CSV Import
        </Divider>
        <NewMenuItem
          compact
          title="Samples"
          avatar={
            <RecordTypeIcon
              record={{
                recordTypeLabel: "",
                iconName: "sample",
              }}
              color=""
              style={{
                width: "22px",
                height: "22px",
                backgroundColor: "hsl(198 37% 80% / 1)",
                padding: "5px",
                color: "hsl(198 13% 25% / 1)",
              }}
            />
          }
          backgroundColor={{ hue: 198, saturation: 37, lightness: 80 }}
          foregroundColor={{ hue: 198, saturation: 13, lightness: 25 }}
          onClick={() => {
            void handleImport("SAMPLES");
          }}
        />
        <NewMenuItem
          compact
          title="Subsamples"
          avatar={
            <RecordTypeIcon
              record={{
                recordTypeLabel: "",
                iconName: "subsample",
              }}
              color=""
              style={{
                width: "22px",
                height: "22px",
                backgroundColor: "hsl(198 37% 80% / 1)",
                padding: "5px",
                color: "hsl(198 13% 25% / 1)",
              }}
            />
          }
          backgroundColor={{ hue: 198, saturation: 37, lightness: 80 }}
          foregroundColor={{ hue: 198, saturation: 13, lightness: 25 }}
          onClick={() => {
            void handleImport("SUBSAMPLES");
          }}
        />
        <NewMenuItem
          compact
          title="Containers"
          avatar={
            <RecordTypeIcon
              record={{
                recordTypeLabel: "",
                iconName: "container",
              }}
              color=""
              style={{
                width: "22px",
                height: "22px",
                backgroundColor: "hsl(198 37% 80% / 1)",
                padding: "5px",
                color: "hsl(198 13% 25% / 1)",
              }}
            />
          }
          backgroundColor={{ hue: 198, saturation: 37, lightness: 80 }}
          foregroundColor={{ hue: 198, saturation: 13, lightness: 25 }}
          onClick={() => {
            void handleImport("CONTAINERS");
          }}
        />
        <Divider textAlign="left" aria-label="Other import">
          Other Import
        </Divider>
        <NewMenuItem
          title="Fieldmark"
          avatar={<CardMedia image={FieldmarkIcon} />}
          subheader="Import Fieldmark notebooks and records"
          backgroundColor={{
            hue: FIELDMARK_COLOR.background.hue,
            saturation: FIELDMARK_COLOR.background.saturation,
            lightness: FIELDMARK_COLOR.background.lightness,
          }}
          foregroundColor={{
            hue: FIELDMARK_COLOR.backgroundContrastText.hue,
            saturation: FIELDMARK_COLOR.backgroundContrastText.saturation,
            lightness: FIELDMARK_COLOR.backgroundContrastText.lightness,
          }}
          onClick={() => {
            setFieldmarkOpen(true);
          }}
        />
      </StyledMenu>
      <ThemeProvider theme={createAccentedTheme(FIELDMARK_COLOR)}>
        <Dialog
          open={fieldmarkOpen}
          onClose={() => {
            setFieldmarkOpen(false);
            setAnchorEl(null);
          }}
        >
          <AppBar position="relative" open={true}>
            <Toolbar variant="dense">
              <Typography variant="h6" noWrap component="h2">
                Fieldmark
              </Typography>
              <Box flexGrow={1}></Box>
              <Box ml={1}>
                <AccessibilityTips
                  supportsHighContrastMode
                  elementType="dialog"
                />
              </Box>
              <Box ml={1} sx={{ transform: "translateY(2px)" }}>
                <HelpLinkIcon title="Fieldmark help" link="#" />
              </Box>
            </Toolbar>
          </AppBar>
          <Box sx={{ display: "flex", minHeight: 0 }}>
            <DialogContent>
              <Grid
                container
                direction="column"
                spacing={2}
                sx={{ height: "100%", flexWrap: "nowrap" }}
              >
                <Grid item>
                  <Typography variant="h3">Import from Fieldmark</Typography>
                </Grid>
                <Grid item>
                  <Typography variant="body2">
                    Choose a Fieldmark notebook to import into Inventory. The
                    new list container will be placed on your bench.
                  </Typography>
                  <Typography variant="body2">
                    See <Link href="#">docs.fieldmark.au</Link> and our{" "}
                    <Link href={"#"}>Fieldmark integration docs</Link> for more.
                  </Typography>
                </Grid>
              </Grid>
            </DialogContent>
          </Box>
        </Dialog>
      </ThemeProvider>
    </Box>
  );
}

export default (observer(CreateNew): ComponentType<CreateNewArgs>);
