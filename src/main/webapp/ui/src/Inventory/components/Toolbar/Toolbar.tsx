import ArrowBackIosIcon from "@mui/icons-material/ArrowBackIos";
import type Alert from "@mui/material/Alert";
import AppBar from "@mui/material/AppBar";
import Box from "@mui/material/Box";
import IconButton from "@mui/material/IconButton";
import Stack from "@mui/material/Stack";
import { useTheme } from "@mui/material/styles";
import Toolbar from "@mui/material/Toolbar";
import Typography from "@mui/material/Typography";
import { observer } from "mobx-react-lite";
import type React from "react";
import GlobalId from "../../../components/GlobalId";
import type { AllowedFormTypes } from "../../../stores/contexts/FormSections";
import type { InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import useStores from "../../../stores/use-stores";
import { UserCancelledAction } from "../../../util/error";
import { useIsSingleColumnLayout } from "../Layout/Layout2x1";
import StickyMenu from "../Stepper/StickyMenu";
import StickyStatus from "../StickyStatus";

type CustomToolbarArgs = {
  title: React.ReactNode;
  record?: InventoryRecord;
  recordType: AllowedFormTypes;
  batch?: boolean;
  stickyAlert?: React.ReactElement<typeof Alert> | null;
};

/**
 * The top-most section of the right-hand side panel of the main Inventory UI,
 * which displays the name of the current record alongside visual elements.
 */
function CustomToolbar({ title, record, recordType, batch, stickyAlert }: CustomToolbarArgs): React.ReactNode {
  const theme = useTheme();
  const {
    uiStore,
    searchStore: { search, activeResult },
  } = useStores();
  const isSingleColumnLayout = useIsSingleColumnLayout();

  const handleBackClick = () => {
    try {
      void (async () => {
        await search.setActiveResult();
        uiStore.setVisiblePanel("left");
      })();
    } catch (e) {
      if (e instanceof UserCancelledAction) return;
      throw e;
    }
  };

  return (
    <AppBar
      position="sticky"
      elevation={0}
      sx={{
        backgroundColor:
          recordType && theme.palette.record[recordType] ? theme.palette.record[recordType].bg : "white !important",
        color: recordType ? "white" : `${theme.palette.text.primary} !important`,
        border: recordType ? "none" : theme.borders.section,
      }}
    >
      <Toolbar
        variant="dense"
        sx={{
          display: "flex",
          alignItems: "center",
          flexWrap: "nowrap",
          gap: 1,
          p: `${theme.spacing(1)} !important`,
          minHeight: "auto",
          overflow: "hidden",
          backgroundColor: "unset !important",
          color: "inherit !important",
        }}
      >
        {isSingleColumnLayout && (
          <IconButton onClick={handleBackClick} sx={{ p: theme.spacing(1, 0.5, 1, 1.5) }}>
            <ArrowBackIosIcon
              fontSize="small"
              data-test-id="backIcon"
              /*
               * Lacking CSS layers support, we have to resort to inline styles
               * here to ensure that we have a higher specificity than the
               * theme
               */
              style={{ color: "white" }}
            />
          </IconButton>
        )}
        <Box sx={{ flexGrow: 1, minWidth: 0 }}>{title}</Box>
        <Box sx={{ minWidth: record?.recordType === "instrument" ? 57 : 90 }}>{record?.illustration}</Box>
        {record && record.id !== null && (
          <Stack>
            <Typography variant="caption" component="span" sx={{ color: "inherit", whiteSpace: "nowrap", pb: 0.25 }}>
              {record.recordTypeLabel.toUpperCase()}
            </Typography>
            <GlobalId record={record} />
          </Stack>
        )}
      </Toolbar>
      {activeResult && <StickyMenu stickyAlert={stickyAlert} />}
      <Box sx={{ pb: 0.25 }} />
      {(record !== undefined || batch === true) && (
        <Box sx={{ position: "relative" }}>
          <StickyStatus recordState={record?.state || "edit"} deleted={record?.deleted || false} />
        </Box>
      )}
    </AppBar>
  );
}

export default observer(CustomToolbar);
