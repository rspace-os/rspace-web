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
  const deepLinkRequestId = (() => {
    const raw = new URLSearchParams(location.search).get("requestId");
    return raw === null ? null : Number(raw);
  })();
  const deepLinkQuery = useSampleRequestDetailQuery(deepLinkRequestId);

  // Tracks the last URL id this page has actually acted on (promoted to selectedRequest, shown an
  // error for, or both at once as this effect's single source of truth updates them together),
  // separately from `selectedRequest` itself. Without this, an unrelated mutation elsewhere
  // invalidating sampleRequestsQueryKeys.all would refetch this still-enabled query in the
  // background, and reacting to every data/error-reference change would re-show a stale error (or
  // clobber a request the user had since picked from the list) even though the URL never changed.
  const [lastHandledDeepLinkId, setLastHandledDeepLinkId] = useState<number | null>(null);
  // A genuine piece of state, not derived from `selectedRequest`: an explicit list selection below
  // clears this directly (the same way it overwrites `selectedRequest`), so picking a valid request
  // after a failed deep link recovers immediately - deriving this from `lastHandledDeepLinkId`
  // alone left it permanently true once set, since only a *new* URL id (not a list click) could
  // ever change that comparison, permanently hiding the request the user just chose.
  const [showDeepLinkError, setShowDeepLinkError] = useState(false);

  useEffect(() => {
    if (deepLinkRequestId === null) return;
    if (lastHandledDeepLinkId === deepLinkRequestId) return;
    if (deepLinkQuery.isError) {
      setLastHandledDeepLinkId(deepLinkRequestId);
      setShowDeepLinkError(true);
      return;
    }
    if (!deepLinkQuery.data) return;
    setLastHandledDeepLinkId(deepLinkRequestId);
    setShowDeepLinkError(false);
    setSelectedRequest(deepLinkQuery.data);
  }, [deepLinkRequestId, deepLinkQuery.data, deepLinkQuery.isError, lastHandledDeepLinkId]);

  function selectRequest(request: ApiSampleRequestListItem) {
    setShowDeepLinkError(false);
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
