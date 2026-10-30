import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import type React from "react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useDeploymentProperty } from "@/hooks/api/useDeploymentProperty";
import useWhoAmI from "@/hooks/api/useWhoAmI";
import { mkAlert } from "@/stores/contexts/Alert";
import useStores from "@/stores/use-stores";
import * as FetchingData from "@/util/fetchingData";
import * as Parsers from "@/util/parsers";
import ApprovalResultFields from "./ApprovalResultFields";
import ChooseMethodDialog from "./ChooseMethodDialog";
import CollapsibleSection from "./CollapsibleSection";
import FulfilRequestDialog from "./FulfilRequestDialog";
import OwnerActionsPanel from "./OwnerActionsPanel";
import PrepareSampleDialog from "./PrepareSampleDialog";
import {
  useOtherActiveSampleRequestsQuery,
  useSampleRequestDetailQuery,
  useSampleWithSubSamplesQuery,
} from "./queries";
import RejectRequestDialog from "./RejectRequestDialog";
import RequestDetailHeader from "./RequestDetailHeader";
import RequestDetailsFields from "./RequestDetailsFields";
import RequesterCancelPanel from "./RequesterCancelPanel";
import RequestHistoryTable, { type ApiSampleRequestStatusChangeItem } from "./RequestHistoryTable";
import RequestSampleLocations from "./RequestSampleLocations";
import type { ApiSampleRequestListItem } from "./sampleRequest";
import TransferOwnershipDialog from "./TransferOwnershipDialog";
import type { PreparationMethod } from "./types";
import { useRequestActions } from "./useRequestActions";

const STATUS_HELP_KEY = {
  FULFILLED: "requestsManagement.detail.statusHelp.fulfilled",
  REJECTED: "requestsManagement.detail.statusHelp.rejected",
  CANCELLED: "requestsManagement.detail.statusHelp.cancelled",
} as const;

function statusHelpKey(status: string): (typeof STATUS_HELP_KEY)[keyof typeof STATUS_HELP_KEY] | null {
  return status in STATUS_HELP_KEY ? STATUS_HELP_KEY[status as keyof typeof STATUS_HELP_KEY] : null;
}

/** The most recent (by `created`) of `statusChanges` whose own `status` matches `status`. */
function latestStatusChange(
  statusChanges: Array<ApiSampleRequestStatusChangeItem>,
  status: string,
): ApiSampleRequestStatusChangeItem | null {
  return statusChanges
    .filter((change) => change.status === status)
    .reduce<ApiSampleRequestStatusChangeItem | null>(
      (latest, change) =>
        !latest || new Date(change.created).getTime() > new Date(latest.created).getTime() ? change : latest,
      null,
    );
}

/**
 * Only one dialog/step of the owner's prepare-or-transfer flow is ever open at a time, so it's one
 * value rather than a handful of independently-toggleable booleans - making "two dialogs open at
 * once" (and the bookkeeping that would otherwise be needed to prevent it) unrepresentable.
 */
type Flow =
  | { kind: "idle" }
  | { kind: "reject"; reason: string }
  | { kind: "fulfil" }
  | { kind: "chooseMethod"; method: PreparationMethod | null }
  | { kind: "prepare" }
  | { kind: "transfer" };

/**
 * The right-hand detail pane for the Requests page: the header shows the
 * request id and the requested sample's name, and the "Details" section
 * shows the requester, submission date, requested sample, and any note.
 */
export default function RequestDetailPanel({ request }: { request: ApiSampleRequestListItem | null }): React.ReactNode {
  const { t } = useTranslation(["inventory", "common"]);
  const [flow, setFlow] = useState<Flow>({ kind: "idle" });
  const [selectedSubsample, setSelectedSubsample] = useState<{ id: number; name: string } | null>(null);
  const currentUser = useWhoAmI();
  const { uiStore } = useStores();
  const isSampleOwner = FetchingData.getSuccessValue(currentUser)
    .map((user) => request != null && user.id === request.sample.owner.id)
    .orElse(false);
  const sampleRequestsAvailable = FetchingData.getSuccessValue(
    useDeploymentProperty("inventory.sampleRequests.available"),
  )
    .flatMap(Parsers.isString)
    .map((value) => value === "ALLOWED")
    .orElse(false);
  const operationsAvailable = FetchingData.getSuccessValue(useDeploymentProperty("inventory.operations.available"))
    .flatMap(Parsers.isString)
    .map((value) => value === "ALLOWED")
    .orElse(false);
  // Without the Operations Wizard enabled, "Create a new sample derived from the existing
  // sample" has nothing to offer, so the Choose Sample to Prepare dialog would only ever
  // sensibly end in a direct transfer; skip straight to it instead of making the owner pick.
  const skipChooseMethodDialog = sampleRequestsAvailable && !operationsAvailable;

  const requestId = request?.id ?? null;
  const sampleId = request?.sample.id ?? null;

  // status/statusChanges/sampleOwnerName all come from this one query rather than local state:
  // every action mutation below invalidates it on success (see ./mutations.ts), so a status change
  // made here or in any other mounted component sharing this request shows up automatically,
  // without each action handler needing to set it from its own response.
  const detailQuery = useSampleRequestDetailQuery(requestId);
  const status = detailQuery.data?.status ?? request?.status;
  const statusChanges = detailQuery.data?.statusChanges ?? [];
  const sampleOwnerName = detailQuery.data
    ? `${detailQuery.data.sample.owner.firstName} ${detailQuery.data.sample.owner.lastName}`
    : null;

  // Shared with RequestSampleLocations' own call to the same hook below (one cached fetch, not
  // two) - this only needs the count, to size the "transferring will move all subsamples too"
  // warning in the Choose Sample to Prepare dialog; `subSamples` comes back null for a restricted
  // (non-owner) viewer, in which case there's no count to show.
  const sampleWithSubSamplesQuery = useSampleWithSubSamplesQuery(sampleId);
  const subSampleCount = sampleWithSubSamplesQuery.data?.subSamples?.length ?? null;

  // Backs both the "other requests will be closed automatically" warning in the Choose Sample to
  // Prepare dialog and the Transfer Ownership dialog's "will be automatically rejected" bullet,
  // which also needs each other request's requester name. Invalidated (and so refetched)
  // alongside the detail query above whenever this request's own status changes, since that can
  // move it into or out of the "active" set counted here.
  const otherActiveRequestsQuery = useOtherActiveSampleRequestsQuery(sampleId, requestId ?? -1);
  // The formatted display name is this hook's caller's concern, not the query's.
  const otherActiveRequests =
    otherActiveRequestsQuery.data?.map((r) => ({
      id: r.id,
      requesterName: `${r.requester.firstName} ${r.requester.lastName}`,
    })) ?? null;

  const otherActiveRequestsCount = otherActiveRequests?.length ?? null;

  const { approve, reject, fulfil, cancel, transfer, isProcessingAction, showActionError } = useRequestActions();

  const comment = status ? latestStatusChange(statusChanges, status)?.reason : undefined;

  if (!request) {
    return (
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
          {t("requestsManagement.noSelection")}
        </Typography>
      </Box>
    );
  }

  // Falls back to the originally requested sample when the fulfilling transition didn't
  // record a transferredSample (e.g. handled outside of RSpace, via the "Fulfil" dialog) - always
  // a real globalId, since request.sample.globalId (unlike the lookup above) can't be absent.
  const transferredSampleGlobalId =
    latestStatusChange(statusChanges, "FULFILLED")?.transferredSample?.globalId ?? request.sample.globalId;

  const approveRequest = () => {
    if (isProcessingAction) return;
    approve.mutate(request.id, {
      onError: (error) => showActionError(t("requestsManagement.detail.actionErrors.approve"), error),
    });
  };

  const rejectRequest = () => {
    if (isProcessingAction || flow.kind !== "reject") return;
    reject.mutate(
      { requestId: request.id, reason: flow.reason },
      {
        onSuccess: () => setFlow({ kind: "idle" }),
        onError: (error) => showActionError(t("requestsManagement.detail.actionErrors.reject"), error),
      },
    );
  };

  const fulfilRequest = () => {
    if (isProcessingAction) return;
    fulfil.mutate(request.id, {
      onSuccess: () => setFlow({ kind: "idle" }),
      onError: (error) => showActionError(t("requestsManagement.detail.actionErrors.fulfil"), error),
    });
  };

  // There's only ever one legal recipient (the requester) - the backend itself always transfers
  // to them regardless of anything picked in the dialog (see
  // useTransferSampleOwnershipMutation's doc comment in ./mutations.ts) - so there's nothing left
  // to look up or wait on before showing it.
  const openTransferDialog = () => setFlow({ kind: "transfer" });

  const proceedWithPreparationMethod = () => {
    if (flow.kind !== "chooseMethod" || !flow.method) return;
    if (flow.method === "wizard") {
      setFlow({ kind: "prepare" });
    } else {
      openTransferDialog();
    }
  };

  // A SubSample has no owner of its own (it always derives from its parent Sample), so
  // "preparing" a subsample for transfer means transferring ownership of the whole Sample.
  // Fulfilling the request and transferring the sample are a single atomic backend call (see
  // useTransferSampleOwnershipMutation's doc comment in ./mutations.ts) - there is no
  // half-succeeded state to account for here.
  const submitTransfer = () => {
    if (isProcessingAction) return;
    transfer.mutate(
      { requestId: request.id },
      {
        onSuccess: () => {
          setFlow({ kind: "idle" });
          uiStore.addAlert(
            mkAlert({
              variant: "success",
              message: t("requestsManagement.detail.transferSuccessMessage", {
                id: request.id,
                sampleName: request.sample.name,
                requester: `${request.requester.firstName} ${request.requester.lastName}`,
              }),
            }),
          );
        },
        onError: async (error) => {
          // Closing the dialog rather than leaving it open avoids a retry that can only fail the
          // same way again. The backend call is a single transaction, so a failure here means
          // nothing was committed - but the error itself carries no distinct code for "the
          // requester cancelled it first" versus every other way this can fail (wrong actor, the
          // requester's account having been deleted, an edit lock, etc. - see ./mutations.ts), so
          // re-fetching the request directly is the only reliable way to tell those apart.
          setFlow({ kind: "idle" });
          const refetched = await detailQuery.refetch();
          if (refetched.data?.status === "CANCELLED") {
            console.error("Failed to transfer sample ownership: request was cancelled", error);
            uiStore.addAlert(
              mkAlert({
                variant: "error",
                message: t("requestsManagement.detail.transferCancelledErrorMessage", {
                  sampleName: request.sample.name,
                }),
              }),
            );
          } else {
            showActionError(t("requestsManagement.detail.actionErrors.transfer"), error);
          }
        },
      },
    );
  };

  // Matches the "Cancel" button behaviour in the Sample form's "Request this sample" box:
  // cancelling is only legal for the requester, and only while the request is PENDING.
  const cancelRequest = () => {
    if (isProcessingAction) return;
    cancel.mutate(request.id, {
      onError: (error) => showActionError(t("requestsManagement.detail.actionErrors.cancel"), error),
    });
  };

  const requesterFullName = `${request.requester.firstName} ${request.requester.lastName}`;
  const isActionableState = status === "PENDING" || status === "APPROVED";
  // A subsample selection only matters when there's a choice to make about which one to prepare
  // (via the Operations Wizard) versus transferring the whole sample directly. With the wizard
  // unavailable, skipChooseMethodDialog means every route through this button already ends in a
  // direct transfer of the whole sample (all of its subsamples), so no particular one needs to be
  // selected first.
  const prepareSampleEnabled = isActionableState && (skipChooseMethodDialog || selectedSubsample !== null);
  const statusHelpTranslationKey = status ? statusHelpKey(status) : null;
  const statusHelpText = statusHelpTranslationKey ? t(statusHelpTranslationKey) : null;
  const infoBoxText =
    status === "PENDING"
      ? skipChooseMethodDialog
        ? // Without the Operations Wizard to choose between, "prepare a subsample" (the generic
          // wording below) isn't accurate - every route from here ends in transferring the whole
          // sample directly.
          t("requestsManagement.detail.preparationHint.pendingTransferOnly")
        : t("requestsManagement.detail.preparationHint.pending")
      : status === "APPROVED"
        ? skipChooseMethodDialog
          ? // No subsample selection to call out here - the button above is already enabled, and
            // transfers the whole sample regardless of which (if any) subsample is selected.
            t("requestsManagement.detail.preparationHint.approvedReadyToTransfer", { requester: requesterFullName })
          : selectedSubsample === null
            ? t("requestsManagement.detail.preparationHint.approvedNoSelection")
            : t("requestsManagement.detail.preparationHint.approvedSelected", {
                subsample: selectedSubsample.name,
                requester: `${request.requester.firstName} ${request.requester.lastName}`,
              })
        : null;

  return (
    <Box sx={{ display: "flex", flexDirection: "column", flexGrow: 1, width: "100%", height: "100%", minWidth: 0 }}>
      <RequestDetailHeader id={request.id} sampleName={request.sample.name} status={status} />
      <Box sx={{ overflow: "auto", flexGrow: 1 }}>
        <CollapsibleSection title={t("requestsManagement.detail.sections.details")}>
          <RequestDetailsFields request={request} />
        </CollapsibleSection>
        <CollapsibleSection title={t("requestsManagement.detail.sections.approvalResult")}>
          <ApprovalResultFields
            status={status ?? request.status}
            isSampleOwner={isSampleOwner}
            sampleOwnerName={sampleOwnerName}
            comment={comment}
            transferredSampleGlobalId={transferredSampleGlobalId}
          />
        </CollapsibleSection>
        {isSampleOwner && (
          <OwnerActionsPanel
            status={status}
            isActionableState={isActionableState}
            prepareSampleEnabled={prepareSampleEnabled}
            skipChooseMethodDialog={skipChooseMethodDialog}
            isProcessingAction={isProcessingAction}
            infoBoxText={infoBoxText}
            statusHelpText={statusHelpText}
            onReject={() => setFlow({ kind: "reject", reason: "" })}
            onApprove={approveRequest}
            onPrepareOrTransfer={() =>
              skipChooseMethodDialog ? openTransferDialog() : setFlow({ kind: "chooseMethod", method: null })
            }
            onMarkFulfilled={() => setFlow({ kind: "fulfil" })}
          />
        )}
        {!isSampleOwner && status === "PENDING" && (
          <RequesterCancelPanel isProcessingAction={isProcessingAction} onCancel={cancelRequest} />
        )}
        {isSampleOwner && (
          <CollapsibleSection
            title={t("requestsManagement.detail.fields.sampleLocation")}
            tooltip={t("requestsManagement.detail.fields.sampleLocationTooltip")}
          >
            <Box sx={{ p: 2 }}>
              <RequestSampleLocations
                sampleId={request.sample.id}
                selectable={status === "PENDING" || status === "APPROVED"}
                selectedSubsampleId={selectedSubsample?.id ?? null}
                onSelectSubsample={setSelectedSubsample}
              />
            </Box>
          </CollapsibleSection>
        )}
        <CollapsibleSection title={t("requestsManagement.detail.history.sectionTitle")}>
          <RequestHistoryTable statusChanges={statusChanges} />
        </CollapsibleSection>
      </Box>
      <RejectRequestDialog
        open={flow.kind === "reject"}
        reason={flow.kind === "reject" ? flow.reason : ""}
        onReasonChange={(reason) => setFlow({ kind: "reject", reason })}
        isProcessingAction={isProcessingAction}
        onClose={() => setFlow({ kind: "idle" })}
        onConfirm={rejectRequest}
      />
      <FulfilRequestDialog
        open={flow.kind === "fulfil"}
        isProcessingAction={isProcessingAction}
        onClose={() => setFlow({ kind: "idle" })}
        onConfirm={fulfilRequest}
      />
      <ChooseMethodDialog
        open={flow.kind === "chooseMethod"}
        preparationMethod={flow.kind === "chooseMethod" ? flow.method : null}
        onMethodChange={(method) => setFlow({ kind: "chooseMethod", method })}
        subSampleCount={subSampleCount}
        otherActiveRequestsCount={otherActiveRequestsCount}
        requesterFullName={requesterFullName}
        onClose={() => setFlow({ kind: "idle" })}
        onProceed={proceedWithPreparationMethod}
      />
      <PrepareSampleDialog
        open={flow.kind === "prepare"}
        selectedSubsampleName={selectedSubsample?.name ?? null}
        onClose={() => setFlow({ kind: "idle" })}
        onNext={openTransferDialog}
      />
      <TransferOwnershipDialog
        open={flow.kind === "transfer"}
        requestId={request.id}
        sampleName={request.sample.name}
        requesterId={request.requester.id}
        requesterFullName={requesterFullName}
        subSampleCount={subSampleCount}
        otherActiveRequests={otherActiveRequests}
        isProcessingAction={isProcessingAction}
        onClose={() => setFlow({ kind: "idle" })}
        onSubmit={submitTransfer}
      />
    </Box>
  );
}
