// @flow strict

import Alert from "@mui/material/Alert";
import AlertTitle from "@mui/material/AlertTitle";
import Badge from "@mui/material/Badge";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import Collapse from "@mui/material/Collapse";
import ErrorIcon from "@mui/icons-material/Error";
import GlobalId from "../GlobalId";
import Grid from "@mui/material/Grid";
import InfoIcon from "@mui/icons-material/Info";
import SnackbarContent from "@mui/material/SnackbarContent";
import WarningIcon from "@mui/icons-material/Warning";
import clsx from "clsx";
import { green } from "@mui/material/colors";
import { default as React, type ComponentType, forwardRef, useId } from "react";
import { makeStyles } from "tss-react/mui";
import DismissButton from "./Buttons/Dismiss";
import ExpandButton from "./Buttons/Expand";
import RetryButton from "./Buttons/Retry";
import { observer } from "mobx-react-lite";
import Snackbar from "@mui/material/Snackbar";
import useViewportDimensions from "../../util/useViewportDimensions";
import { type Alert as AlertType } from "../../stores/contexts/Alert";

const useStyles = makeStyles()((theme, { verySmallLayout }) => ({
  success: {
    backgroundColor: green[600],
  },
  error: {
    backgroundColor: theme.palette.error.main,
  },
  warning: {
    backgroundColor: theme.palette.warning.main,
  },
  icon: {
    fontSize: 20,
  },
  iconVariant: {
    opacity: 0.9,
    marginRight: theme.spacing(1),
  },
  spacer: {
    flexGrow: 1,
  },
  buttons: {
    margin: -12,
    flexShrink: 0,
  },
  content: {
    width: "100%",
  },
  badge: {
    right: 8,
    top: 2,
    width: 20,
    border: "2px solid white",
  },
  successBadge: {
    backgroundColor: theme.palette.success.main,
  },
  alertTitle: {
    whiteSpace: "normal",
  },
  alertContainer: {
    maxHeight: verySmallLayout ? "calc(100vh - 210px)" : "max(175px, 50vh)",
    overflowY: "auto",
  },
  detailedAlert: {
    alignItems: "center",
  },
  detailedText: {
    wordBreak: "break-word",
  },
}));

const variantIcon = {
  success: CheckCircleIcon,
  warning: WarningIcon,
  error: ErrorIcon,
  notice: InfoIcon,
};

type SnackbarContentWrapperArgs = {|
  className?: string,
  onClose: () => void,
  alert: AlertType,
  setExpanded: (boolean) => void,
  expanded: boolean,
  onInteraction: () => void,
|};

const SnackbarContentWrapper = forwardRef<
  SnackbarContentWrapperArgs,
  typeof Snackbar
>(
  (
    {
      className,
      onClose,
      alert,
      expanded,
      setExpanded,
      onInteraction,
      ...other
    }: SnackbarContentWrapperArgs,
    ref
  ) => {
    const { isViewportVerySmall } = useViewportDimensions();
    const { classes } = useStyles({ verySmallLayout: isViewportVerySmall });
    const Icon = variantIcon[alert.variant];
    const nameId = useId();

    const standardSnackbarContent = (
      <Grid item>
        <Grid container wrap="nowrap">
          <Grid item>
            <Badge
              badgeContent={alert.detailsCount}
              classes={{
                badge: clsx(
                  classes.badge,
                  alert.variant === "success" && classes.successBadge
                ),
              }}
              color="error"
              aria-hidden="true"
            >
              {alert.icon ?? (
                <Icon className={clsx(classes.icon, classes.iconVariant)} />
              )}
            </Badge>
          </Grid>
          <Grid item>
            <Box
              pl={1}
              pt={0.25}
              pr={2}
              whiteSpace="normal"
              style={{
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
          <Grid item className={classes.spacer}></Grid>
          <Grid item className={classes.buttons}>
            {alert.actionLabel !== null &&
              typeof alert.actionLabel !== "undefined" && (
                <Button
                  size="small"
                  onClick={() => {
                    onInteraction();
                    alert.onActionClick();
                    onClose();
                  }}
                  color="primary"
                >
                  {alert.actionLabel.toUpperCase()}
                </Button>
              )}
            {alert.retryFunction && (
              <RetryButton
                retryFunction={alert.retryFunction}
                onClose={onClose}
              />
            )}
            {alert.details.length > 0 && (
              <ExpandButton
                ariaLabel={`${alert.detailsCount} sub-messages. Toggle to ${
                  expanded ? "hide" : "show"
                }`}
                expanded={expanded}
                setExpanded={(e) => {
                  setExpanded(e);
                  onInteraction();
                }}
              />
            )}
            {alert.allowClosing && (
              <DismissButton onClose={onClose} alert={alert} />
            )}
          </Grid>
        </Grid>
      </Grid>
    );

    const detailedSnackbarContent = (
      <Grid item xs={12}>
        <Box mt={2}>
          <Grid container spacing={1} className={classes.alertContainer}>
            {alert.details.map(({ record, variant, title, help }, index) => (
              <Grid item key={index} xs={12}>
                <Alert
                  className={classes.detailedAlert}
                  severity={variant}
                  action={
                    record && (
                      <GlobalId
                        record={record}
                        onClick={() => setExpanded(false)}
                      />
                    )
                  }
                >
                  <Box className={classes.detailedText}>
                    {help !== null && typeof help !== "undefined" ? (
                      <>
                        <AlertTitle className={classes.alertTitle}>
                          {title}
                        </AlertTitle>
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
              <Grid item xs={12}>
                <Alert
                  className={classes.detailedAlert}
                  severity={alert.variant}
                >
                  <Box className={classes.detailedText}>
                    {`And ${alert.detailsCount - alert.details.length} more...`}
                  </Box>
                </Alert>
              </Grid>
            )}
          </Grid>
        </Box>
      </Grid>
    );

    return (
      <SnackbarContent
        {...other}
        data-test-id="toast-content"
        className={clsx(classes[alert.variant], className)}
        classes={{
          message: classes.content,
        }}
        ref={ref}
        aria-labelledby={nameId}
        message={
          <Grid container direction="column">
            {standardSnackbarContent}
            <Collapse
              in={expanded}
              aria-hidden={!expanded}
              component="div"
              collapsedSize={0}
            >
              {detailedSnackbarContent}
            </Collapse>
          </Grid>
        }
      />
    );
  }
);

SnackbarContentWrapper.displayName = "SnackbarContentWrapper";

export default (observer(
  SnackbarContentWrapper
): ComponentType<SnackbarContentWrapperArgs>);
