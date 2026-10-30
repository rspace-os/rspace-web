import Box from "@mui/material/Box";
import { useTheme } from "@mui/material/styles";
import Typography from "@mui/material/Typography";
import type React from "react";
import { useContext, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useLandmark } from "@/components/LandmarksContext";
import VisuallyHiddenHeading from "@/components/VisuallyHiddenHeading";
import NavigateContext from "@/stores/contexts/Navigate";
import Main from "../Main";
import { useSampleRequestDetailQuery } from "./queries";
import RequestDetailPanel from "./RequestDetailPanel";
import RequestsList from "./RequestsList";
import type { ApiSampleRequestListItem } from "./sampleRequest";

/**
 * Lets a user see all of the requests they have made, and (as an owner)
 * all of the requests made against their own samples.
 */
export default function RequestsPage(): React.ReactNode {
  const { t } = useTranslation("inventory");
  const theme = useTheme();
  const mainContentRef = useLandmark(t("requestsManagement.landmark"));
  const [selectedRequest, setSelectedRequest] = useState<ApiSampleRequestListItem | null>(null);
  const { useLocation } = useContext(NavigateContext);
  const location = useLocation();

  // Deep-links to a request via ?requestId= (e.g. from the Sample form's status chip), reusing
  // RequestDetailPanel's own query/cache entry rather than a second GET, and turning a bad or
  // forbidden id into a visible message instead of a silent console.error.
  // Recomputed from location.search every render, not gated on selectedRequest - gating on it
  // would stop tracking the URL after the first id resolved, missing later ?requestId= changes.
  const rawDeepLinkId = new URLSearchParams(location.search).get("requestId");
  const deepLinkRequestId = (() => {
    if (rawDeepLinkId === null) return null;
    const parsed = Number(rawDeepLinkId);
    return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
  })();
  // requestId was present but can never be a real id (non-numeric, "", a decimal, <= 0). Disables
  // the query entirely rather than passing NaN/0 through: besides never resolving, NaN !== NaN
  // would break the "already handled" equality checks below.
  const invalidDeepLinkId = rawDeepLinkId !== null && deepLinkRequestId === null;
  const deepLinkQuery = useSampleRequestDetailQuery(deepLinkRequestId);

  // The last URL requestId this effect has actually acted on - keyed on the raw string (not the
  // parsed id) so an invalid id can be tracked the same way. Without it, a background refetch from
  // an unrelated mutation invalidating sampleRequestsQueryKeys.all would re-run this effect and
  // re-show a stale error (or clobber a request picked from the list since) even though the URL
  // never changed.
  const [lastHandledDeepLinkKey, setLastHandledDeepLinkKey] = useState<string | null>(null);
  // Not derived from lastHandledDeepLinkKey: a list pick clears this directly, so it can recover
  // immediately from a failed deep link - deriving it instead would stay true until a *new* URL id
  // arrived, permanently hiding the request just chosen. Also lets the isSuccess branch below tell
  // "already handled, nothing since" apart from "already handled, but picked something else since" -
  // both match on lastHandledDeepLinkKey alone, only the latter also leaves this false.
  const [showDeepLinkError, setShowDeepLinkError] = useState(false);
  // Marks a selectedRequest the deep-link effect itself set, so leaving the deep link can drop its
  // own stale selection without discarding one the user picked themselves since.
  const [deepLinkSelected, setDeepLinkSelected] = useState(false);

  useEffect(() => {
    if (rawDeepLinkId === null) {
      // Leaving the deep link (e.g. the sidebar's plain /inventory/requests link) must clear its
      // error and bookkeeping rather than leave them stuck with no id left to compare against.
      // Only clears a selection the deep link itself set - a manual list pick survives, as it does
      // against a stale background refetch too.
      setLastHandledDeepLinkKey(null);
      setShowDeepLinkError(false);
      if (deepLinkSelected) {
        setSelectedRequest(null);
        setDeepLinkSelected(false);
      }
      return;
    }
    if (invalidDeepLinkId) {
      // No query to wait on for a known-bad id, so show its error immediately - gated by the same
      // "already handled" rule as a valid id's error, keyed on the raw string instead.
      if (lastHandledDeepLinkKey === rawDeepLinkId) return;
      setLastHandledDeepLinkKey(rawDeepLinkId);
      setShowDeepLinkError(true);
      return;
    }
    if (deepLinkQuery.isSuccess) {
      // A retry that succeeds after an earlier failure (transient 503, retried on focus/reconnect)
      // must still replace the error and select the request - see showDeepLinkError's own comment
      // for why !showDeepLinkError is part of the skip condition here.
      if (lastHandledDeepLinkKey === rawDeepLinkId && !showDeepLinkError) return;
      setLastHandledDeepLinkKey(rawDeepLinkId);
      setShowDeepLinkError(false);
      setDeepLinkSelected(true);
      setSelectedRequest(deepLinkQuery.data);
      return;
    }
    if (deepLinkQuery.isError) {
      if (lastHandledDeepLinkKey === rawDeepLinkId) return;
      setLastHandledDeepLinkKey(rawDeepLinkId);
      setShowDeepLinkError(true);
    }
  }, [
    rawDeepLinkId,
    invalidDeepLinkId,
    deepLinkQuery.data,
    deepLinkQuery.isSuccess,
    deepLinkQuery.isError,
    lastHandledDeepLinkKey,
    showDeepLinkError,
    deepLinkSelected,
  ]);

  function selectRequest(request: ApiSampleRequestListItem) {
    setShowDeepLinkError(false);
    setDeepLinkSelected(false);
    setSelectedRequest(request);
  }

  return (
    <Main ref={mainContentRef} role="main" aria-label={t("requestsManagement.landmark")}>
      <VisuallyHiddenHeading variant="h2">{t("requestsManagement.pageTitle")}</VisuallyHiddenHeading>
      <Box sx={{ display: "flex", height: "100%" }}>
        <Box
          sx={{
            display: "flex",
            flex: "1 1 40%",
            minWidth: 0,
            borderRight: `1px solid ${theme.palette.divider}`,
          }}
        >
          <RequestsList selectedRequestId={selectedRequest?.id ?? null} onSelect={selectRequest} />
        </Box>
        <Box sx={{ display: "flex", flex: "1 1 60%", minWidth: 0 }}>
          {showDeepLinkError ? (
            <Box
              sx={{
                display: "flex",
                flexGrow: 1,
                width: "100%",
                height: "100%",
                alignItems: "center",
                justifyContent: "center",
              }}
            >
              <Typography variant="body1" color="text.secondary">
                {t("requestsManagement.deepLinkError")}
              </Typography>
            </Box>
          ) : (
            <RequestDetailPanel key={selectedRequest?.id ?? "none"} request={selectedRequest} />
          )}
        </Box>
      </Box>
    </Main>
  );
}
