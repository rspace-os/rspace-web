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
import RequestsList, { type ApiSampleRequestListItem } from "./RequestsList";

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

  // Supports deep-linking to a specific request (e.g. from the status chip in the Sample form's
  // "Request this sample" box) via a `requestId` query param. Reuses RequestDetailPanel's own
  // query/cache entry for that id - rather than a second, separate GET sampleRequests/{id} - and,
  // via isError below, surfaces a bad or forbidden id as a visible message instead of only a
  // console.error that leaves the user looking at an unexplained "no selection" pane.
  //
  // Derived from location.search on every render (not gated on whether a request is already
  // selected), so navigating to a different ?requestId= while this page stays mounted is always
  // picked up - gating on `selectedRequest` instead would permanently stop the query from ever
  // tracking the URL again after the first id resolved.
  const rawDeepLinkId = new URLSearchParams(location.search).get("requestId");
  const deepLinkRequestId = (() => {
    if (rawDeepLinkId === null) return null;
    const parsed = Number(rawDeepLinkId);
    return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
  })();
  // A requestId was present in the URL but could never be a real id (non-numeric, "", a decimal, 0
  // or negative) - shown as an error immediately below, without enabling the query at all: it would
  // otherwise key a real GET sampleRequests/{id} request for a value that could never resolve, and
  // for "abc" specifically, key the query on Number("abc") = NaN, which never equals itself and so
  // never satisfies the "already handled" checks below.
  const invalidDeepLinkId = rawDeepLinkId !== null && deepLinkRequestId === null;
  const deepLinkQuery = useSampleRequestDetailQuery(deepLinkRequestId);

  // Tracks the last URL requestId this page has actually acted on (promoted to selectedRequest,
  // shown an error for, or both at once as this effect's single source of truth updates them
  // together), separately from `selectedRequest` itself. Keyed on the raw URL string, not the
  // parsed id, so an invalid id (which has no parsed id to key on) can be tracked the same way a
  // valid one is. Without this, an unrelated mutation elsewhere invalidating
  // sampleRequestsQueryKeys.all would refetch this still-enabled query in the background, and
  // reacting to every data/error-reference change would re-show a stale error (or clobber a request
  // the user had since picked from the list) even though the URL never changed.
  const [lastHandledDeepLinkKey, setLastHandledDeepLinkKey] = useState<string | null>(null);
  // A genuine piece of state, not derived from `selectedRequest`: an explicit list selection below
  // clears this directly (the same way it overwrites `selectedRequest`), so picking a valid request
  // after a failed deep link recovers immediately - deriving this from `lastHandledDeepLinkKey`
  // alone left it permanently true once set, since only a *new* URL id (not a list click) could
  // ever change that comparison, permanently hiding the request the user just chose. It also
  // doubles as the signal the success branch below needs to tell "already handled, nothing since
  // happened" apart from "already handled, but the user has since picked something else" - both
  // leave `lastHandledDeepLinkKey === rawDeepLinkId`, but only the latter also leaves this false.
  const [showDeepLinkError, setShowDeepLinkError] = useState(false);
  // Distinguishes a `selectedRequest` the deep-link effect itself promoted from one a plain list
  // click set, so leaving the deep link (see the reset branch below) can drop its own stale
  // selection without also discarding a selection the user picked themselves in the meantime.
  const [deepLinkSelected, setDeepLinkSelected] = useState(false);

  useEffect(() => {
    if (rawDeepLinkId === null) {
      // Leaving the deep link entirely - e.g. the sidebar's plain /inventory/requests link, which
      // keeps this page mounted - must not leave its error or its bookkeeping stuck forever just
      // because there's no id left to compare against. Only a selection the deep link itself set
      // is cleared here; a manual list pick survives, the same way it already survives a stale
      // background refetch above.
      setLastHandledDeepLinkKey(null);
      setShowDeepLinkError(false);
      if (deepLinkSelected) {
        setSelectedRequest(null);
        setDeepLinkSelected(false);
      }
      return;
    }
    if (invalidDeepLinkId) {
      // A known-bad id has no query to wait on, so show its error immediately rather than only
      // once some (never-to-arrive) fetch settles - gated by the same "already handled" rule as a
      // valid id's error, just keyed on the raw string since there's no parsed id for this one.
      if (lastHandledDeepLinkKey === rawDeepLinkId) return;
      setLastHandledDeepLinkKey(rawDeepLinkId);
      setShowDeepLinkError(true);
      return;
    }
    if (deepLinkQuery.isSuccess) {
      // Marking an id "handled" on error (below) must not be permanent for its *success* outcome:
      // a retry that succeeds after an earlier failure for this same id (a transient 503, retried
      // on window focus/reconnect) still needs to replace the error and select the request. Only
      // skip when this id was already resolved into a non-error state - either a prior success, or
      // a manual list pick made since the error (both clear showDeepLinkError) - so a late retry
      // never clobbers a selection the user has since made themselves.
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
