import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import type React from "react";
import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import ApiService from "@/common/InvApiService";
import { useDeploymentProperty } from "@/hooks/api/useDeploymentProperty";
import useWhoAmI from "@/hooks/api/useWhoAmI";
import { mkAlert } from "@/stores/contexts/Alert";
import AlwaysNewFactory from "@/stores/models/Factory/AlwaysNewFactory";
import SubSampleModel, { type SubSampleAttrs } from "@/stores/models/SubSampleModel";
import useStores from "@/stores/use-stores";
import * as FetchingData from "@/util/fetchingData";
import * as Parsers from "@/util/parsers";
import type { OperationResult } from "../components/Operations/operationsApi";
import { useOperationWizardLauncher } from "../components/Operations/useOperationWizardLauncher";
import ApprovalResultFields from "./ApprovalResultFields";
import ChooseMethodDialog from "./ChooseMethodDialog";
import CollapsibleSection from "./CollapsibleSection";
import FulfilRequestDialog from "./FulfilRequestDialog";
import OwnerActionsPanel from "./OwnerActionsPanel";
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
import { type ApiSampleRequestListItem, userDisplayName } from "./sampleRequest";
import TransferOwnershipDialog from "./TransferOwnershipDialog";
import type { PreparationMethod } from "./types";
import { useRequestActions } from "./useRequestActions";

// Preparing a sample for a request always ends in transferring it to the requester, so
// destroying the origin has no sensible place in this flow; Pool requires multiple origins,
// but this flow only ever offers the single subsample selected in Sample Locations.
const OPERATION_WIZARD_EXCLUDED_KEYS = new Set(["destroy", "pool"]);

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
  const [wizardOrigin, setWizardOrigin] = useState<SubSampleModel | null>(null);
  // Covers the gap between clicking Proceed (which has already closed the Choose dialog) on the
  // "wizard" radio and the wizard actually opening: fetching the selected subsample, then - once
  // that succeeds and wizardOrigin is set below - useOperationWizardLauncher's own edit-lock
  // acquisition. Rolled into isProcessingAction so every other action is disabled for the same
  // window a mutation would disable them, rather than only while a request status mutation is
  // itself in flight.
  const [isLaunchingWizard, setIsLaunchingWizard] = useState(false);
  // Set only when the Transfer Ownership dialog is reached via the "wizard" radio's Operations
  // Wizard having just created a new sample; null (the ordinary case, including every "transfer"
  // radio route into this dialog) means the dialog acts on request.sample exactly as before.
  const [wizardCreatedSample, setWizardCreatedSample] = useState<OperationResult | null>(null);
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
  // Reassigned below, after `request` is known non-null, so it always calls this render's own
  // openTransferDialog rather than a stale one from whichever earlier render first constructed
  // the (necessarily hook-stable) callback this hook-ordering requires declaring up here.
  const onWizardPerformedRef = useRef<(sample: OperationResult | null) => void>(() => {});
  const wizardOrigins = wizardOrigin ? [wizardOrigin] : [];
  const { launch: launchOperationWizard, wizard: operationWizard } = useOperationWizardLauncher(wizardOrigins, {
    onPerformed: (sample) => onWizardPerformedRef.current(sample),
    onClose: () => setWizardOrigin(null),
    excludedOperationKeys: OPERATION_WIZARD_EXCLUDED_KEYS,
  });

  // launchOperationWizard is deliberately excluded from the deps: it is a fresh closure every
  // render (not memoised by the hook), so including it would refire this on every render rather
  // than only when a freshly-fetched origin is set.
  useEffect(() => {
    if (wizardOrigin) {
      // isLaunchingWizard started true back in launchOperationsWizardForSelectedSubsample, when
      // the subsample fetch that produced this origin began; it only stops now, once edit-lock
      // acquisition has also settled - whether that opened the wizard or (as the hook's own alert
      // already reports) failed to, either way there's nothing left still in flight to show.
      void launchOperationWizard().finally(() => setIsLaunchingWizard(false));
    }
  }, [wizardOrigin]);

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
  const hasPendingOtherActiveRequest = (otherActiveRequests ?? []).some((r) => r.status === "PENDING");
  const hasApprovedOtherActiveRequest = (otherActiveRequests ?? []).some((r) => r.status === "APPROVED");

  const {
    approve,
    reject,
    fulfil,
    cancel,
    transfer,
    isProcessingAction: isProcessingMutation,
    showActionError,
  } = useRequestActions();
  const isProcessingAction = isProcessingMutation || isLaunchingWizard;

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
  // to look up or wait on before showing it. createdSample names the Operations Wizard's newly
  // created sample when reached via the "wizard" radio; every other route into this dialog
  // (including every other call site below) leaves it null, meaning request.sample as always.
  const openTransferDialog = (createdSample: OperationResult | null = null) => {
    setWizardCreatedSample(createdSample);
    setFlow({ kind: "transfer" });
  };

  // Kept in sync every render (see the ref declaration above): the Operations Wizard's onPerformed
  // hands back the new sample it created, which is what then gets offered up in the Transfer
  // Ownership dialog, exactly as the "transfer" radio does for the originally requested sample.
  onWizardPerformedRef.current = (sample) => {
    setWizardOrigin(null);
    if (sample) openTransferDialog(sample);
  };

  const launchOperationsWizardForSelectedSubsample = () => {
    if (selectedSubsample === null) return;
    setIsLaunchingWizard(true);
    ApiService.get<SubSampleAttrs>("subSamples", selectedSubsample.id)
      .then(({ data }) => {
        setWizardOrigin(new SubSampleModel(new AlwaysNewFactory(), data));
      })
      .catch((error: unknown) => {
        // The Choose dialog has already closed by this point (see proceedWithPreparationMethod),
        // so without this the user is left looking at the unchanged detail pane with no idea
        // Proceed did anything at all. isLaunchingWizard only clears here, on failure - a success
        // instead sets wizardOrigin above, and the effect above clears it once edit-lock
        // acquisition (the rest of what Proceed promised to do) has also settled.
        setIsLaunchingWizard(false);
        showActionError(t("requestsManagement.detail.actionErrors.prepareWizard"), error);
      });
  };

  const proceedWithPreparationMethod = () => {
    if (flow.kind !== "chooseMethod" || !flow.method) return;
    const method = flow.method;
    setFlow({ kind: "idle" });
    if (method === "wizard") {
      launchOperationsWizardForSelectedSubsample();
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
    // The "wizard" radio transfers the sample the Operations Wizard just created, not the
    // originally requested one; wizardCreatedSample is null for every other route into this
    // dialog, in which case it's the same sample as always - matching the dialog's own heading,
    // which names the same sample the same way.
    const transferredSampleName = wizardCreatedSample?.name ?? request.sample.name;
    transfer.mutate(
      { requestId: request.id, sampleToTransferGlobalId: wizardCreatedSample?.globalId },
      {
        onSuccess: () => {
          setFlow({ kind: "idle" });
          uiStore.addAlert(
            mkAlert({
              variant: "success",
              message: t("requestsManagement.detail.transferSuccessMessage", {
                id: request.id,
                sampleName: transferredSampleName,
                requester: requesterFullName,
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
            // A wizard-created sample has already been created in the current owner's own
            // Inventory (see launchOperationsWizardForSelectedSubsample's onCreate above) - unlike
            // the originally requested sample, it never belonged to the request itself, so it
            // simply stays put rather than needing any manual follow-up to relocate it.
            uiStore.addAlert(
              mkAlert({
                variant: "error",
                message: wizardCreatedSample
                  ? t("requestsManagement.detail.transferCancelledWizardSampleErrorMessage", {
                      sampleName: wizardCreatedSample.name,
                    })
                  : t("requestsManagement.detail.transferCancelledErrorMessage", {
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

  const requesterFullName = userDisplayName(request.requester, t);
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
                requester: requesterFullName,
              })
        : null;
  // Named for which other active requests exist, so the warning can say specifically "Pending"
  // or "Approved" rather than always the vaguer "either...or" - which is still used when both
  // are present, since then there genuinely isn't a single specific state to name.
  const otherActiveRequestsWarningText = (leadsWithAlso: boolean): string => {
    if (hasPendingOtherActiveRequest && hasApprovedOtherActiveRequest) {
      return leadsWithAlso
        ? t("requestsManagement.detail.chooseMethodDialog.otherActiveRequestsWarningCombined")
        : t("requestsManagement.detail.chooseMethodDialog.otherActiveRequestsWarning");
    }
    if (hasPendingOtherActiveRequest) {
      return leadsWithAlso
        ? t("requestsManagement.detail.chooseMethodDialog.otherActiveRequestsWarningPendingCombined")
        : t("requestsManagement.detail.chooseMethodDialog.otherActiveRequestsWarningPending");
    }
    return leadsWithAlso
      ? t("requestsManagement.detail.chooseMethodDialog.otherActiveRequestsWarningApprovedCombined")
      : t("requestsManagement.detail.chooseMethodDialog.otherActiveRequestsWarningApproved");
  };

  const preparationMethod = flow.kind === "chooseMethod" ? flow.method : null;
  // Combined into one alert rather than one each: both warn about consequences of the same
  // "transfer" choice in the Choose Sample to Prepare dialog, so showing both at once as two
  // separate boxes read as more alarming than warranted.
  const showsSubsampleWarning = preparationMethod === "transfer" && subSampleCount !== null && subSampleCount > 1;
  const showsOtherActiveRequestsWarning =
    preparationMethod === "transfer" && otherActiveRequestsCount !== null && otherActiveRequestsCount > 0;
  const chooseMethodTransferWarnings: Array<string> = (
    [
      showsSubsampleWarning
        ? t("requestsManagement.detail.chooseMethodDialog.transferWarning", {
            count: subSampleCount,
            requester: requesterFullName,
          })
        : null,
      // Leads with "also" only when it follows the subsamples warning in the same alert; read
      // on its own, "also" would imply some earlier warning that was never shown.
      showsOtherActiveRequestsWarning ? otherActiveRequestsWarningText(showsSubsampleWarning) : null,
    ] as Array<string | null>
  ).filter((warning): warning is string => warning !== null);

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
        preparationMethod={preparationMethod}
        onMethodChange={(method) => setFlow({ kind: "chooseMethod", method })}
        warnings={chooseMethodTransferWarnings}
        onClose={() => setFlow({ kind: "idle" })}
        onProceed={proceedWithPreparationMethod}
      />
      {operationWizard}
      <TransferOwnershipDialog
        open={flow.kind === "transfer"}
        requestId={request.id}
        sampleName={wizardCreatedSample?.name ?? request.sample.name}
        requesterId={request.requester.id}
        requesterFullName={requesterFullName}
        isNewlyCreatedSample={wizardCreatedSample !== null}
        subSampleCount={subSampleCount}
        otherActiveRequests={otherActiveRequests}
        isProcessingAction={isProcessingAction}
        onClose={() => setFlow({ kind: "idle" })}
        onSubmit={submitTransfer}
      />
    </Box>
  );
}
