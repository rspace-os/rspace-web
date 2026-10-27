import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import type React from "react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useDeploymentProperty } from "@/hooks/api/useDeploymentProperty";
import useWhoAmI from "@/hooks/api/useWhoAmI";
import { mkAlert } from "@/stores/contexts/Alert";
import type PersonModel from "@/stores/models/PersonModel";
import useStores from "@/stores/use-stores";
import { getErrorMessage } from "@/util/error";
import * as FetchingData from "@/util/fetchingData";
import * as Parsers from "@/util/parsers";
import ApprovalResultFields from "./ApprovalResultFields";
import ChooseMethodDialog from "./ChooseMethodDialog";
import CollapsibleSection from "./CollapsibleSection";
import FulfilRequestDialog from "./FulfilRequestDialog";
import {
  useApproveSampleRequestMutation,
  useCancelSampleRequestMutation,
  useFulfilSampleRequestMutation,
  useRejectSampleRequestMutation,
  useTransferSampleOwnershipMutation,
} from "./mutations";
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
import type { ApiSampleRequestListItem } from "./RequestsList";
import TransferOwnershipDialog from "./TransferOwnershipDialog";

const STATUS_HELP_KEY = {
  FULFILLED: "requestsManagement.detail.statusHelp.fulfilled",
  REJECTED: "requestsManagement.detail.statusHelp.rejected",
  CANCELLED: "requestsManagement.detail.statusHelp.cancelled",
} as const;

function statusHelpKey(status: string): (typeof STATUS_HELP_KEY)[keyof typeof STATUS_HELP_KEY] | null {
  return status in STATUS_HELP_KEY ? STATUS_HELP_KEY[status as keyof typeof STATUS_HELP_KEY] : null;
}

/**
 * The right-hand detail pane for the Requests page: the header shows the
 * request id and the requested sample's name, and the "Details" section
 * shows the requester, submission date, requested sample, and any note.
 */
export default function RequestDetailPanel({ request }: { request: ApiSampleRequestListItem | null }): React.ReactNode {
  const { t } = useTranslation(["inventory", "common"]);
  const [detailsExpanded, setDetailsExpanded] = useState(true);
  const [approvalResultExpanded, setApprovalResultExpanded] = useState(true);
  const [requestHistoryExpanded, setRequestHistoryExpanded] = useState(true);
  const [sampleLocationsExpanded, setSampleLocationsExpanded] = useState(true);
  const [rejectDialogOpen, setRejectDialogOpen] = useState(false);
  const [rejectReason, setRejectReason] = useState("");
  const [fulfilDialogOpen, setFulfilDialogOpen] = useState(false);
  const [selectedSubsampleId, setSelectedSubsampleId] = useState<number | null>(null);
  const [selectedSubsampleName, setSelectedSubsampleName] = useState<string | null>(null);
  const [chooseMethodDialogOpen, setChooseMethodDialogOpen] = useState(false);
  const [preparationMethod, setPreparationMethod] = useState<"wizard" | "transfer" | null>(null);
  const [prepareDialogOpen, setPrepareDialogOpen] = useState(false);
  const [transferDialogOpen, setTransferDialogOpen] = useState(false);
  const [transferRecipient, setTransferRecipient] = useState<PersonModel | null>(null);
  // Separate from transferRecipient: that field can be cleared back to an unrestricted search by
  // the owner (e.g. to double-check who else is available), but this dialog only ever exists to
  // hand the sample to the requester specifically, so the field's choices should stay restricted
  // to them regardless. Fetched once and never cleared, unlike transferRecipient.
  const [transferRequesterPerson, setTransferRequesterPerson] = useState<PersonModel | null>(null);
  const currentUser = useWhoAmI();
  const { peopleStore, uiStore } = useStores();
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
  const otherActiveRequests = otherActiveRequestsQuery.data ?? null;

  const otherActiveRequestsCount = otherActiveRequests?.length ?? null;

  // Guards every top-level action below (approve/reject/cancel/fulfil/transfer) against
  // double-clicks and cross-action races - at most one is ever genuinely legitimate at a time,
  // since each needs the previous one's resulting status change to even become clickable again.
  const approveMutation = useApproveSampleRequestMutation();
  const rejectMutation = useRejectSampleRequestMutation();
  const fulfilMutation = useFulfilSampleRequestMutation();
  const cancelMutation = useCancelSampleRequestMutation();
  const transferMutation = useTransferSampleOwnershipMutation();
  const isProcessingAction =
    approveMutation.isPending ||
    rejectMutation.isPending ||
    fulfilMutation.isPending ||
    cancelMutation.isPending ||
    transferMutation.isPending;

  const comment = statusChanges
    .filter((change) => change.status === status)
    .reduce<ApiSampleRequestStatusChangeItem | null>(
      (latest, change) =>
        !latest || new Date(change.created).getTime() > new Date(latest.created).getTime() ? change : latest,
      null,
    )?.reason;

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
    statusChanges
      .filter((change) => change.status === "FULFILLED")
      .reduce<ApiSampleRequestStatusChangeItem | null>(
        (latest, change) =>
          !latest || new Date(change.created).getTime() > new Date(latest.created).getTime() ? change : latest,
        null,
      )?.transferredSample?.globalId ?? request.sample.globalId;

  // Shared by every top-level action below: each hits the same "silent failure" shape, so the
  // user sees nothing go wrong while the UI quietly stays in its pre-click state.
  const showActionError = (action: string, error: unknown) => {
    console.error(action, error);
    uiStore.addAlert(
      mkAlert({
        variant: "error",
        message: t("errors.genericActionError", { error: getErrorMessage(error, t("errors.unknownReason")) }),
      }),
    );
  };

  const approveRequest = () => {
    if (isProcessingAction) return;
    approveMutation.mutate(request.id, {
      onError: (error) => showActionError("Failed to approve sample request", error),
    });
  };

  const rejectRequest = () => {
    if (isProcessingAction) return;
    rejectMutation.mutate(
      { requestId: request.id, reason: rejectReason },
      {
        onSuccess: () => {
          setRejectDialogOpen(false);
          setRejectReason("");
        },
        onError: (error) => showActionError("Failed to reject sample request", error),
      },
    );
  };

  const fulfilRequest = () => {
    if (isProcessingAction) return;
    fulfilMutation.mutate(request.id, {
      onSuccess: () => setFulfilDialogOpen(false),
      onError: (error) => showActionError("Failed to fulfil sample request", error),
    });
  };

  // The requester was pre-fetched as a PersonModel via peopleStore.getUser when the
  // Preparing Sample dialog's "Next" button was pressed; if that lookup hasn't resolved
  // yet, the field just starts empty and the owner can pick a recipient manually.
  const openTransferDialog = () => {
    setPrepareDialogOpen(false);
    setTransferDialogOpen(true);
    if (!transferRecipient || !transferRequesterPerson) {
      void peopleStore.getUser(request.requester.username).then((person) => {
        if (!person) return;
        if (!transferRecipient) setTransferRecipient(person);
        setTransferRequesterPerson(person);
      });
    }
  };

  const proceedWithPreparationMethod = () => {
    if (!preparationMethod) return;
    setChooseMethodDialogOpen(false);
    setPreparationMethod(null);
    if (preparationMethod === "wizard") {
      setPrepareDialogOpen(true);
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
    // transferRequesterPerson also gates the button below, but is re-checked here too: until it
    // has loaded, PeopleField's restrictToUser is unset and so searches everyone (see
    // openTransferDialog's comment), meaning transferRecipient could be any user, not just the
    // requester this dialog exists to hand the sample to.
    if (!transferRecipient || !transferRequesterPerson || isProcessingAction) return;
    transferMutation.mutate(
      { requestId: request.id },
      {
        onSuccess: () => {
          setTransferDialogOpen(false);
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
        onError: (error) => {
          console.error("Failed to transfer sample ownership", error);
          // Closing the dialog rather than leaving it open avoids a retry that can only fail the
          // same way again; the mutation's own onError (see ./mutations.ts) has already
          // invalidated every query above, so the status chip, history, and action buttons pick
          // up whatever the request's real state now is on their own, without a bespoke
          // refetch-and-resync here. The backend call is a single transaction, so a failure here
          // means nothing was committed - most likely because the request was closed by someone
          // else in the meantime.
          setTransferDialogOpen(false);
          uiStore.addAlert(
            mkAlert({
              variant: "error",
              message: t("requestsManagement.detail.transferCancelledErrorMessage", {
                sampleName: request.sample.name,
              }),
            }),
          );
        },
      },
    );
  };

  // Matches the "Cancel" button behaviour in the Sample form's "Request this sample" box:
  // cancelling is only legal for the requester, and only while the request is PENDING.
  const cancelRequest = () => {
    if (isProcessingAction) return;
    cancelMutation.mutate(request.id, {
      onError: (error) => showActionError("Failed to cancel sample request", error),
    });
  };

  const requesterFullName = `${request.requester.firstName} ${request.requester.lastName}`;
  const isActionableState = status === "PENDING" || status === "APPROVED";
  // A subsample selection only matters when there's a choice to make about which one to prepare
  // (via the Operations Wizard) versus transferring the whole sample directly. With the wizard
  // unavailable, skipChooseMethodDialog means every route through this button already ends in a
  // direct transfer of the whole sample (all of its subsamples), so no particular one needs to be
  // selected first.
  const prepareSampleEnabled = isActionableState && (skipChooseMethodDialog || selectedSubsampleId !== null);
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
          : selectedSubsampleId === null || selectedSubsampleName === null
            ? t("requestsManagement.detail.preparationHint.approvedNoSelection")
            : t("requestsManagement.detail.preparationHint.approvedSelected", {
                subsample: selectedSubsampleName,
                requester: `${request.requester.firstName} ${request.requester.lastName}`,
              })
        : null;

  return (
    <Box sx={{ display: "flex", flexDirection: "column", flexGrow: 1, width: "100%", height: "100%", minWidth: 0 }}>
      <RequestDetailHeader id={request.id} sampleName={request.sample.name} status={status} />
      <Box sx={{ overflow: "auto", flexGrow: 1 }}>
        <CollapsibleSection
          title={t("requestsManagement.detail.sections.details")}
          expanded={detailsExpanded}
          onToggle={() => setDetailsExpanded(!detailsExpanded)}
        >
          <RequestDetailsFields request={request} />
        </CollapsibleSection>
        <CollapsibleSection
          title={t("requestsManagement.detail.sections.approvalResult")}
          expanded={approvalResultExpanded}
          onToggle={() => setApprovalResultExpanded(!approvalResultExpanded)}
        >
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
            onReject={() => setRejectDialogOpen(true)}
            onApprove={approveRequest}
            onPrepareOrTransfer={() =>
              skipChooseMethodDialog ? openTransferDialog() : setChooseMethodDialogOpen(true)
            }
            onMarkFulfilled={() => setFulfilDialogOpen(true)}
          />
        )}
        {!isSampleOwner && status === "PENDING" && (
          <RequesterCancelPanel isProcessingAction={isProcessingAction} onCancel={cancelRequest} />
        )}
        {isSampleOwner && (
          <CollapsibleSection
            title={t("requestsManagement.detail.fields.sampleLocation")}
            tooltip={t("requestsManagement.detail.fields.sampleLocationTooltip")}
            expanded={sampleLocationsExpanded}
            onToggle={() => setSampleLocationsExpanded(!sampleLocationsExpanded)}
          >
            <Box sx={{ p: 2 }}>
              <RequestSampleLocations
                sampleId={request.sample.id}
                selectable={status === "PENDING" || status === "APPROVED"}
                selectedSubsampleId={selectedSubsampleId}
                onSelectSubsample={(subSample) => {
                  setSelectedSubsampleId(subSample.id);
                  setSelectedSubsampleName(subSample.name);
                }}
              />
            </Box>
          </CollapsibleSection>
        )}
        <CollapsibleSection
          title={t("requestsManagement.detail.history.sectionTitle")}
          expanded={requestHistoryExpanded}
          onToggle={() => setRequestHistoryExpanded(!requestHistoryExpanded)}
        >
          <RequestHistoryTable statusChanges={statusChanges} />
        </CollapsibleSection>
      </Box>
      <RejectRequestDialog
        open={rejectDialogOpen}
        reason={rejectReason}
        onReasonChange={setRejectReason}
        isProcessingAction={isProcessingAction}
        onClose={() => setRejectDialogOpen(false)}
        onConfirm={rejectRequest}
      />
      <FulfilRequestDialog
        open={fulfilDialogOpen}
        isProcessingAction={isProcessingAction}
        onClose={() => setFulfilDialogOpen(false)}
        onConfirm={fulfilRequest}
      />
      <ChooseMethodDialog
        open={chooseMethodDialogOpen}
        preparationMethod={preparationMethod}
        onMethodChange={setPreparationMethod}
        subSampleCount={subSampleCount}
        otherActiveRequestsCount={otherActiveRequestsCount}
        requesterFullName={requesterFullName}
        onClose={() => {
          setChooseMethodDialogOpen(false);
          setPreparationMethod(null);
        }}
        onProceed={proceedWithPreparationMethod}
      />
      <PrepareSampleDialog
        open={prepareDialogOpen}
        selectedSubsampleName={selectedSubsampleName}
        onClose={() => setPrepareDialogOpen(false)}
        onNext={openTransferDialog}
      />
      <TransferOwnershipDialog
        open={transferDialogOpen}
        requestId={request.id}
        sampleName={request.sample.name}
        requesterFullName={requesterFullName}
        subSampleCount={subSampleCount}
        otherActiveRequests={otherActiveRequests}
        transferRecipient={transferRecipient}
        transferRequesterPerson={transferRequesterPerson}
        isProcessingAction={isProcessingAction}
        onRecipientChange={setTransferRecipient}
        onClose={() => setTransferDialogOpen(false)}
        onSubmit={submitTransfer}
      />
    </Box>
  );
}
