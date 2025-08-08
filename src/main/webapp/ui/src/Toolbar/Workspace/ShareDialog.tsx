import React from "react";
import Portal from "@mui/material/Portal";
import ErrorBoundary from "../../components/ErrorBoundary";
import Alerts from "../../components/Alerts/Alerts";
import { Dialog, DialogBoundary } from "../../components/DialogBoundary";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Button from "@mui/material/Button";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import Typography from "@mui/material/Typography";
import Paper from "@mui/material/Paper";
import Chip from "@mui/material/Chip";
import CircularProgress from "@mui/material/CircularProgress";
import Box from "@mui/material/Box";
import Link from "@mui/material/Link";
import Analytics from "../../components/Analytics";
import AnalyticsContext from "../../stores/contexts/Analytics";
import ValidatingSubmitButton from "../../components/ValidatingSubmitButton";
import Result from "../../util/result";
import useShare, { ShareInfo } from "../../hooks/api/useShare";
import UserDetails from "../../Inventory/components/UserDetails";
import { ThemeProvider } from "@mui/material/styles";
import createAccentedTheme from "../../accentedTheme";
import { ACCENT_COLOR } from "../../assets/branding/rspace/workspace";

export default function Wrapper(): React.ReactNode {
  return (
    <Analytics>
      <ErrorBoundary topOfViewport>
        <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
          <Portal>
            <Alerts>
              <DialogBoundary>
                <ShareDialog />
              </DialogBoundary>
            </Alerts>
          </Portal>
        </ThemeProvider>
      </ErrorBoundary>
    </Analytics>
  );
}

const ShareDialog = () => {
  const [open, setOpen] = React.useState(false);
  const [globalIds, setGlobalIds] = React.useState<string[]>([]);
  const [names, setNames] = React.useState<string[]>([]);
  const [shareData, setShareData] = React.useState<Map<string, ShareInfo[]>>(
    new Map(),
  );
  const [loading, setLoading] = React.useState(false);
  const { trackEvent } = React.useContext(AnalyticsContext);
  const { getShareInfoForMultiple } = useShare();

  React.useEffect(() => {
    function handler(event: Event) {
      // @ts-expect-error there will be a detail
      const { globalIds, names } = event.detail;
      setOpen(true);
      setGlobalIds(globalIds || []);
      setNames(names || []);
      setShareData(new Map());
    }
    window.addEventListener("OPEN_SHARE_DIALOG", handler);
    return () => {
      window.removeEventListener("OPEN_SHARE_DIALOG", handler);
    };
  }, []);

  // Fetch sharing data when dialog opens and we have globalIds
  React.useEffect(() => {
    if (open && globalIds.length > 0) {
      setLoading(true);
      getShareInfoForMultiple(globalIds)
        .then((data) => {
          setShareData(data);
        })
        .catch((error) => {
          console.error("Failed to fetch sharing information:", error);
        })
        .finally(() => {
          setLoading(false);
        });
    }
  }, [open, globalIds]);

  function handleClose() {
    setOpen(false);
    setGlobalIds([]);
    setNames([]);
    setShareData(new Map());
    setLoading(false);
  }

  return (
    <Dialog
      open={open}
      onClose={() => {
        handleClose();
      }}
      onKeyDown={(e) => {
        e.stopPropagation();
      }}
      maxWidth="md"
      fullWidth
    >
      <DialogTitle>Share</DialogTitle>
      <DialogContent>
        {loading ? (
          <Box
            display="flex"
            justifyContent="center"
            alignItems="center"
            minHeight="200px"
          >
            <CircularProgress />
          </Box>
        ) : (
          <Box>
            {globalIds.map((globalId, index) => {
              const shares = shareData.get(globalId) || [];
              const documentName = names[index] || `Document ${globalId}`;

              return (
                <Box key={globalId} mb={3}>
                  <Typography variant="h6" gutterBottom>
                    {documentName}
                  </Typography>

                  {shares.length === 0 ? (
                    <Typography
                      variant="body2"
                      color="text.secondary"
                      style={{ fontStyle: "italic" }}
                    >
                      This document is not shared with anyone.
                    </Typography>
                  ) : (
                    <TableContainer component={Paper} variant="outlined">
                      <Table size="small">
                        <TableHead>
                          <TableRow>
                            <TableCell>Shared With</TableCell>
                            <TableCell>Type</TableCell>
                            <TableCell>Permission</TableCell>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {shares.map((share) => (
                            <TableRow key={share.id}>
                              <TableCell>
                                <Box>
                                  {share.sharedTargetType === "USER" ? (
                                    <UserDetails
                                      userId={share.sharedTargetId}
                                      fullName={share.sharedTargetDisplayName}
                                      position={["bottom", "right"]}
                                    />
                                  ) : (
                                    <Link
                                      component="button"
                                      variant="body2"
                                      onClick={(e) => {
                                        e.preventDefault();
                                        e.stopPropagation();
                                        window.open(`/groups/view/${share.sharedTargetId}`, '_blank');
                                      }}
                                      sx={{ 
                                        fontWeight: 'medium',
                                        textAlign: 'left',
                                        textDecoration: 'none',
                                        '&:hover': {
                                          textDecoration: 'underline'
                                        }
                                      }}
                                    >
                                      {share.sharedTargetDisplayName}
                                    </Link>
                                  )}
                                  <Typography
                                    variant="caption"
                                    color="text.secondary"
                                    display="block"
                                  >
                                    {share.sharedTargetName}
                                  </Typography>
                                </Box>
                              </TableCell>
                              <TableCell>
                                <Chip
                                  size="small"
                                  label={share.sharedTargetType}
                                  color={
                                    share.sharedTargetType === "USER"
                                      ? "primary"
                                      : "secondary"
                                  }
                                  variant="outlined"
                                />
                              </TableCell>
                              <TableCell>
                                <Chip
                                  size="small"
                                  label={share.permission}
                                  color={
                                    share.permission === "EDIT"
                                      ? "success"
                                      : "default"
                                  }
                                  variant="filled"
                                />
                              </TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    </TableContainer>
                  )}
                </Box>
              );
            })}

            {globalIds.length === 0 && (
              <Typography variant="body2" color="text.secondary">
                No documents selected.
              </Typography>
            )}
          </Box>
        )}
      </DialogContent>
      <DialogActions>
        <Button
          onClick={() => {
            handleClose();
          }}
        >
          Cancel
        </Button>
        <ValidatingSubmitButton
          loading={false}
          onClick={() => {
            // TODO: Implement sharing logic
            trackEvent("user:opens:share_dialog:workspace");
            handleClose();
          }}
          validationResult={Result.Ok(null)}
        >
          Share
        </ValidatingSubmitButton>
      </DialogActions>
    </Dialog>
  );
};
