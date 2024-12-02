// @flow
import AddIcon from "@mui/icons-material/Add";
import React, { type Node, useContext, type ComponentType } from "react";
import useStores from "../../stores/use-stores";
import NavigateContext from "../../stores/contexts/Navigate";
import { UserCancelledAction } from "../../util/error";
import { styled } from "@mui/material/styles";
import Menu from "@mui/material/Menu";
import NewMenuItem from "../../eln/gallery/components/NewMenuItem";
import RecordTypeIcon from "../../components/RecordTypeIcon";
import Divider from "@mui/material/Divider";
import Button from "@mui/material/Button";
import Box from "@mui/material/Box";
import { observer } from "mobx-react-lite";
import FieldmarkIcon from "../../eln/apps/icons/fieldmark.svg";
import CardMedia from "@mui/material/CardMedia";
import FieldmarkImportDialog, {
  FIELDMARK_COLOR,
} from "./FieldmarkImportDialog";
import { useIntegrationIsAllowedAndEnabled } from "../../common/integrationHelpers";
import * as FetchingData from "../../util/fetchingData";

const StyledMenu = styled(Menu)(({ open }) => ({
  "& .MuiPaper-root": {
    ...(open
      ? {
          transform: "translate(-4px, 4px) !important",
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
  const showFieldmark = FetchingData.getSuccessValue(
    useIntegrationIsAllowedAndEnabled("FIELDMARK")
  ).orElse(false);

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
                width: "18px",
                height: "18px",
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
                width: "18px",
                height: "18px",
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
                width: "28px",
                height: "28px",
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
                width: "18px",
                height: "18px",
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
                width: "18px",
                height: "18px",
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
                width: "18px",
                height: "18px",
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
        {showFieldmark && (
          <>
            <Divider textAlign="left" aria-label="Other import">
              Third-Party Import
            </Divider>
            <NewMenuItem
              compact
              title="Fieldmark"
              avatar={<CardMedia image={FieldmarkIcon} />}
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
                /*
                 * We close the create menu when the fieldmark dialog is opened,
                 * rather than leaving it open in the background as would normally
                 * be the case, because the create menu is portalled to the root of
                 * the DOM and so floats over all else whereas the fieldmark dialog
                 * is added to the DOM as a child of a sibling of the `<section>`
                 * that wraps all of the alert toasts. This is so that alerts
                 * displayed by the contents of the dialog are reachable by screen
                 * readers but has the effect of meaning that the create new dialog
                 * would render over it. By closing the menu when the dialog is
                 * opened we prevent this bug.
                 */
                setAnchorEl(null);
              }}
              aria-haspopup="dialog"
            />
          </>
        )}
      </StyledMenu>
      <FieldmarkImportDialog
        open={fieldmarkOpen}
        onClose={() => {
          setFieldmarkOpen(false);
        }}
      />
    </Box>
  );
}

export default (observer(CreateNew): ComponentType<CreateNewArgs>);
