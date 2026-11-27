import type React from "react";
import { useContext, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useLandmark } from "@/components/LandmarksContext";
import VisuallyHiddenHeading from "@/components/VisuallyHiddenHeading";
import { UiPreferences } from "@/hooks/api/useUiPreference";
import NavigateContext from "@/stores/contexts/Navigate";
import useStores from "@/stores/use-stores";
import Layout2x1 from "../components/Layout/Layout2x1";
import Main from "../Main";
import DetailPlaceholder from "./DetailPlaceholder";
import { useSampleRequestDetailQuery } from "./queries";
import RequestDetailPanel from "./RequestDetailPanel";
import RequestsList from "./RequestsList";
import type { ApiSampleRequestListItem } from "./sampleRequest";

/**
 * Lets a user see all of the requests they have made, and (as an owner)
 * all of the requests made against their own samples.
 */
function RequestsPageContent(): React.ReactNode {
  const { t } = useTranslation("inventory");
  const { uiStore } = useStores();
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

  // uiStore.visiblePanel is shared with the other Inventory pages, so a "right" left over from
  // one of them would otherwise open this page on an empty detail pane in a single-column layout.
  // Must stay above the restore and deep-link effects: with the request already cached they run in
  // the same commit, and their "right" has to win.
  useEffect(() => {
    uiStore.setVisiblePanel("left");
  }, [uiStore]);

  // Brings back the request shown when the user last left this page (see
  // uiStore.lastSelectedSampleRequestId). Only read on mount, and never when the URL names a request.
  const [restoreRequestId] = useState(() => (rawDeepLinkId === null ? uiStore.lastSelectedSampleRequestId : null));
  const restoreQuery = useSampleRequestDetailQuery(restoreRequestId);
  const [restoreHandled, setRestoreHandled] = useState(restoreRequestId === null);

  useEffect(() => {
    if (restoreHandled) return;
    // A deep link followed while this loaded is more recent, and supersedes it for good: checking
    // the URL only when the response arrived would let returning to the plain URL re-enable it.
    if (rawDeepLinkId !== null) {
      setRestoreHandled(true);
      return;
    }
    if (restoreQuery.isSuccess) {
      setRestoreHandled(true);
      // A row picked while this loaded is more recent and so wins.
      if (selectedRequest !== null) return;
      setSelectedRequest(restoreQuery.data);
      // Reopens the details only if they were showing when the user left, not if they had already
      // gone back to the list.
      if (uiStore.lastSampleRequestsPanel === "right") uiStore.setVisiblePanel("right");
    } else if (restoreQuery.isError) {
      setRestoreHandled(true);
      // Deleted, or no longer visible to this user; forget it unless something newer replaced it.
      if (uiStore.lastSelectedSampleRequestId === restoreRequestId) uiStore.setLastSelectedSampleRequestId(null);
    }
  }, [
    restoreHandled,
    restoreQuery.isSuccess,
    restoreQuery.isError,
    restoreQuery.data,
    rawDeepLinkId,
    restoreRequestId,
    selectedRequest,
    uiStore,
  ]);

  useEffect(() => {
    if (selectedRequest) uiStore.setLastSelectedSampleRequestId(selectedRequest.id);
  }, [selectedRequest, uiStore]);

  useEffect(() => {
    if (rawDeepLinkId === null) {
      // Leaving the deep link (e.g. the sidebar's plain /inventory/requests link) must clear its
      // error and bookkeeping rather than leave them stuck with no id left to compare against.
      // Only clears a selection the deep link itself set - a manual list pick survives, as it does
      // against a stale background refetch too.
      setLastHandledDeepLinkKey(null);
      // Browser Back/Forward don't go through RouterNavigationContext, which would otherwise reset
      // the panel, so return to the list here rather than leave it behind an empty detail pane.
      if (deepLinkSelected || showDeepLinkError) uiStore.setSampleRequestsPanel("left");
      setShowDeepLinkError(false);
      if (deepLinkSelected) {
        setSelectedRequest(null);
        setDeepLinkSelected(false);
        uiStore.setLastSelectedSampleRequestId(null);
      }
      return;
    }
    if (invalidDeepLinkId) {
      // No query to wait on for a known-bad id, so show its error immediately - gated by the same
      // "already handled" rule as a valid id's error, keyed on the raw string instead.
      if (lastHandledDeepLinkKey === rawDeepLinkId) return;
      setLastHandledDeepLinkKey(rawDeepLinkId);
      setShowDeepLinkError(true);
      uiStore.setSampleRequestsPanel("right");
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
      uiStore.setSampleRequestsPanel("right");
      return;
    }
    if (deepLinkQuery.isError) {
      if (lastHandledDeepLinkKey === rawDeepLinkId) return;
      setLastHandledDeepLinkKey(rawDeepLinkId);
      setShowDeepLinkError(true);
      uiStore.setSampleRequestsPanel("right");
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
    uiStore,
  ]);

  function selectRequest(request: ApiSampleRequestListItem) {
    // Supersedes the current URL's deep link, so its still-pending result can't replace this pick
    // when it arrives; a later navigation to a different requestId is still followed.
    if (rawDeepLinkId !== null) setLastHandledDeepLinkKey(rawDeepLinkId);
    setShowDeepLinkError(false);
    setDeepLinkSelected(false);
    setSelectedRequest(request);
    uiStore.setSampleRequestsPanel("right");
  }

  return (
    <Main ref={mainContentRef} role="main" aria-label={t("requestsManagement.landmark")}>
      <VisuallyHiddenHeading variant="h2">{t("requestsManagement.pageTitle")}</VisuallyHiddenHeading>
      <Layout2x1
        colLeft={<RequestsList selectedRequestId={selectedRequest?.id ?? null} onSelect={selectRequest} />}
        colRight={
          showDeepLinkError ? (
            <DetailPlaceholder message={t("requestsManagement.deepLinkError")} />
          ) : (
            <RequestDetailPanel key={selectedRequest?.id ?? "none"} request={selectedRequest} />
          )
        }
      />
    </Main>
  );
}

/**
 * Loads the saved UI preferences, such as whether the user has hidden the right panel, which
 * Layout2x1 shares with the Inventory search pages.
 */
export default function RequestsPage(): React.ReactNode {
  return (
    <UiPreferences>
      <RequestsPageContent />
    </UiPreferences>
  );
}
