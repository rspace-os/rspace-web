import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import ErrorIcon from "@mui/icons-material/Error";
import InfoIcon from "@mui/icons-material/Info";
import WarningIcon from "@mui/icons-material/Warning";
import Alert from "@mui/material/Alert";
import AlertTitle from "@mui/material/AlertTitle";
import Badge, { badgeClasses } from "@mui/material/Badge";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Collapse from "@mui/material/Collapse";
import { green } from "@mui/material/colors";
import Grid from "@mui/material/Grid";
import SnackbarContent, { snackbarContentClasses } from "@mui/material/SnackbarContent";
import Stack from "@mui/material/Stack";
import { useTheme } from "@mui/material/styles";
import { observer } from "mobx-react-lite";
// biome-ignore lint/correctness/noUnusedImports: initial biome migration
import React, { forwardRef, useId } from "react";
import useViewportDimensions from "../../hooks/browser/useViewportDimensions";
// biome-ignore lint/style/useImportType: initial biome migration
import { type Alert as AlertType } from "../../stores/contexts/Alert";
import GlobalId from "../GlobalId";
import DismissButton from "./Buttons/Dismiss";
import ExpandButton from "./Buttons/Expand";
import RetryButton from "./Buttons/Retry";

declare module "@mui/material/Alert" {
  interface AlertPropsColorOverrides {
    notice: true;
  }
}

const variantIcon = {
  success: CheckCircleIcon,
  warning: WarningIcon,
  error: ErrorIcon,
  notice: InfoIcon,
};

type SnackbarContentWrapperArgs = {
  onClose: () => void;
  alert: AlertType;
  setExpanded: (newExpanded: boolean) => void;
  expanded: boolean;
  onInteraction: () => void;
};

const SnackbarContentWrapper = forwardRef<HTMLDivElement, SnackbarContentWrapperArgs>(
  ({ onClose, alert, expanded, setExpanded, onInteraction, ...other }: SnackbarContentWrapperArgs, ref) => {
    const { isViewportVerySmall } = useViewportDimensions();
    const theme = useTheme();
    const Icon = variantIcon[alert.variant];
    const nameId = useId();
    const backgroundColorByVariant: Partial<Record<AlertType["variant"], string>> = {
      success: green[600],
      error: theme.palette.error.main,
      warning: theme.palette.warning.main,
    };
    const backgroundColor = backgroundColorByVariant[alert.variant];

    const standardSnackbarContent = (
      <Grid container sx={{ flexWrap: "nowrap" }}>
        <Grid>
          <Badge
            badgeContent={alert.detailsCount}
            color="error"
            aria-hidden="true"
            sx={{
              [`& .${badgeClasses.badge}`]: {
                right: 8,
                top: 2,
                width: 20,
                border: "2px solid white",
                ...(alert.variant === "success" ? { backgroundColor: theme.palette.success.main } : {}),
              },
            }}
          >
            {alert.icon ?? <Icon sx={{ fontSize: 20, opacity: 0.9, mr: 1 }} />}
          </Badge>
        </Grid>
        <Grid>
          <Box
            sx={{
              pl: 1,
              pt: 0.25,
              pr: 2,
              whiteSpace: "normal",
              overflowWrap: "anywhere",
              hyphens: "auto",
            }}
            id={nameId}
          >
            {alert.title !== null && typeof alert.title !== "undefined" ? (
              <>
                <strong>{alert.title}</strong>
                <br />
                {alert.message}
              </>
            ) : (
              alert.message
            )}
          </Box>
        </Grid>
        <Grid sx={{ flexGrow: 1 }}></Grid>
        <Grid sx={{ flexShrink: 0 }}>
          {alert.actionLabel !== null && typeof alert.actionLabel !== "undefined" && (
            <Button
              size="small"
              onClick={() => {
                onInteraction();
                alert.onActionClick();
                onClose();
              }}
              variant="outlined"
              sx={{ color: "white", borderColor: "white" }}
            >
              {alert.actionLabel.toUpperCase()}
            </Button>
          )}
          {alert.retryFunction && <RetryButton retryFunction={alert.retryFunction} onClose={onClose} />}
          {alert.details.length > 0 && (
            <ExpandButton
              ariaLabel={`${alert.detailsCount} sub-messages. Toggle to ${expanded ? "hide" : "show"}`}
              expanded={expanded}
              setExpanded={(e) => {
                setExpanded(e);
                onInteraction();
              }}
            />
          )}
          {alert.allowClosing && <DismissButton onClose={onClose} />}
        </Grid>
      </Grid>
    );

    const detailedSnackbarContent = (
      <Grid size={12} sx={{ mt: 2 }}>
        <Grid
          container
          spacing={1}
          sx={{
            maxHeight: isViewportVerySmall ? "calc(100vh - 210px)" : "max(175px, 50vh)",
            overflowY: "auto",
          }}
        >
          {alert.details.map(({ record, variant, title, help }, index) => (
            <Grid key={index} size={12}>
              <Alert
                sx={{ alignItems: "center" }}
                severity={variant}
                action={record && <GlobalId record={record} onClick={() => setExpanded(false)} />}
              >
                <Box sx={{ wordBreak: "break-word" }}>
                  {help !== null && typeof help !== "undefined" ? (
                    <>
                      <AlertTitle sx={{ whiteSpace: "normal" }}>{title}</AlertTitle>
                      {help}
                    </>
                  ) : (
                    title
                  )}
                </Box>
              </Alert>
            </Grid>
          ))}
          {alert.detailsCount > alert.details.length && (
            <Grid size={12}>
              <Alert sx={{ alignItems: "center" }} severity={alert.variant}>
                <Box sx={{ wordBreak: "break-word" }}>{`And ${alert.detailsCount - alert.details.length} more...`}</Box>
              </Alert>
            </Grid>
          )}
        </Grid>
      </Grid>
    );

    return (
      <SnackbarContent
        {...other}
        data-test-id="toast-content"
        ref={ref}
        aria-labelledby={nameId}
        sx={{
          ...(backgroundColor ? { backgroundColor } : {}),
          [`& .${snackbarContentClasses.message}`]: {
            width: "100%",
          },
        }}
        message={
          <Stack>
            {standardSnackbarContent}
            <Collapse in={expanded} aria-hidden={!expanded} component="div" collapsedSize={0}>
              {detailedSnackbarContent}
            </Collapse>
          </Stack>
        }
      />
    );
  },
);

/**
 * The actual content of the alert toast.
 */
export default observer(SnackbarContentWrapper);
