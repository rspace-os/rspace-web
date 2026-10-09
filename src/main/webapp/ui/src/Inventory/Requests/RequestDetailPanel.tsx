import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Collapse from "@mui/material/Collapse";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Divider from "@mui/material/Divider";
import FormControl from "@mui/material/FormControl";
import FormControlLabel from "@mui/material/FormControlLabel";
import IconButton from "@mui/material/IconButton";
import Radio from "@mui/material/Radio";
import RadioGroup from "@mui/material/RadioGroup";
import { darken, useTheme } from "@mui/material/styles";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import type React from "react";
import { useId, useState } from "react";
import { useTranslation } from "react-i18next";
import CustomTooltip from "@/components/CustomTooltip";
import { Heading, HeadingContext } from "@/components/DynamicHeadingLevel";
import GlobalId from "@/components/GlobalId";
import NoValue from "@/components/NoValue";
import UserDetails from "@/components/UserDetails";
import { useDeploymentProperty } from "@/hooks/api/useDeploymentProperty";
import useWhoAmI from "@/hooks/api/useWhoAmI";
import { formatList } from "@/modules/common/i18n/listFormat";
import TransRichText from "@/modules/common/i18n/TransRichText";
import { mkAlert } from "@/stores/contexts/Alert";
import LinkableRecordFromGlobalId from "@/stores/models/LinkableRecordFromGlobalId";
import type PersonModel from "@/stores/models/PersonModel";
import useStores from "@/stores/use-stores";
import { getErrorMessage } from "@/util/error";
import * as FetchingData from "@/util/fetchingData";
import * as Parsers from "@/util/parsers";
import { isoToLocale } from "@/util/Util";
import PeopleField from "../components/Inputs/PeopleField";
import {
  SampleOwnershipTransferError,
  useApproveSampleRequestMutation,
  useCancelSampleRequestMutation,
  useFulfilSampleRequestMutation,
  useRejectSampleRequestMutation,
  useTransferSampleOwnershipMutation,
} from "./mutations";
import {
  useOtherActiveSampleRequestsQuery,
  useSampleRequestDetailQuery,
  useSampleWithSubSamplesQuery,
} from "./queries";
import RequestHistoryTable, { type ApiSampleRequestStatusChangeItem } from "./RequestHistoryTable";
import RequestSampleLocations from "./RequestSampleLocations";
import type { ApiSampleRequestListItem } from "./RequestsList";
import RequestsStatusChip, { STATUS_BACKGROUND } from "./RequestsStatusChip";

const STATUS_HELP_KEY = {
  FULFILLED: "requestsManagement.detail.statusHelp.fulfilled",
  REJECTED: "requestsManagement.detail.statusHelp.rejected",
  CANCELLED: "requestsManagement.detail.statusHelp.cancelled",
} as const;

function statusHelpKey(status: string): (typeof STATUS_HELP_KEY)[keyof typeof STATUS_HELP_KEY] | null {
  return status in STATUS_HELP_KEY ? STATUS_HELP_KEY[status as keyof typeof STATUS_HELP_KEY] : null;
}

function DetailField({
  label,
  tooltip,
  children,
}: {
  label: string;
  tooltip?: string;
  children: React.ReactNode;
}): React.ReactNode {
  const labelId = useId();
  const heading = (
    <Heading sx={{ mt: 0 }} id={labelId}>
      {label}
    </Heading>
  );
  return (
    <FormControl fullWidth role="group" aria-labelledby={labelId}>
      {tooltip ? <CustomTooltip title={tooltip}>{heading}</CustomTooltip> : heading}
      {/* break-word only splits a word that's too long to fit on its own line (e.g. a long
          global id), rather than break-all's every-line mid-word wrapping of ordinary text
          like the requester's note or the approver's comment. */}
      <Box sx={{ overflowWrap: "break-word" }}>{children}</Box>
    </FormControl>
  );
}

/**
 * The right-hand detail pane for the Requests page: the header shows the
 * request id and the requested sample's name, and the "Details" section
 * shows the requester, submission date, requested sample, and any note.
 */
export default function RequestDetailPanel({ request }: { request: ApiSampleRequestListItem | null }): React.ReactNode {
  const { t, i18n } = useTranslation(["inventory", "common"]);
  const theme = useTheme();
  const reasonFieldId = useId();
  // Content region ids for the collapsible sections' aria-controls below - each toggle button's
  // aria-label also names its own section (rather than the four sharing one generic "Collapse
  // section"/"Expand section" name, indistinguishable from each other to a screen reader user).
  const detailsSectionId = useId();
  const approvalResultSectionId = useId();
  const sampleLocationsSectionId = useId();
  const requestHistorySectionId = useId();
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

  // Guards every top-level action below (approve/reject/cancel/fulfil) against double-clicks and
  // cross-action races - at most one is ever genuinely legitimate at a time, since each needs the
  // previous one's resulting status change to even become clickable again.
  const approveMutation = useApproveSampleRequestMutation();
  const rejectMutation = useRejectSampleRequestMutation();
  const fulfilMutation = useFulfilSampleRequestMutation();
  const cancelMutation = useCancelSampleRequestMutation();
  const transferMutation = useTransferSampleOwnershipMutation();
  const isProcessingAction =
    approveMutation.isPending || rejectMutation.isPending || fulfilMutation.isPending || cancelMutation.isPending;

  const comment = statusChanges
    .filter((change) => change.status === status)
    .reduce<ApiSampleRequestStatusChangeItem | null>(
      (latest, change) =>
        !latest || new Date(change.created).getTime() > new Date(latest.created).getTime() ? change : latest,
      null,
    )?.reason;

  // Falls back to the originally requested sample when the fulfilling transition didn't
  // record a transferredSample (e.g. handled outside of RSpace, via the "Fulfil" dialog).
  const transferredSampleGlobalId =
    statusChanges
      .filter((change) => change.status === "FULFILLED")
      .reduce<ApiSampleRequestStatusChangeItem | null>(
        (latest, change) =>
          !latest || new Date(change.created).getTime() > new Date(latest.created).getTime() ? change : latest,
        null,
      )?.transferredSample?.globalId ?? request?.sample.globalId;

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
  //
  // The request is marked fulfilled BEFORE the transfer, not after: the backend authorises
  // the fulfil transition against the sample's current owner, and that's still the caller
  // here. Doing the transfer first would change the sample's owner away from the caller,
  // so the follow-up fulfil call would then fail as the caller no longer being party to
  // the request (reported back as 404, to avoid disclosing the request's existence).
  const submitTransfer = () => {
    // transferRequesterPerson also gates the button below, but is re-checked here too: until it
    // has loaded, PeopleField's restrictToUser is unset and so searches everyone (see
    // openTransferDialog's comment), meaning transferRecipient could be any user, not just the
    // requester this dialog exists to hand the sample to.
    if (!transferRecipient || !transferRequesterPerson) return;
    transferMutation.mutate(
      { requestId: request.id, sampleId: request.sample.id, newOwnerUsername: transferRecipient.username },
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
          // Closing the dialog rather than leaving it open avoids a retry that, in either case
          // below, can only fail (or succeed-but-mislead) the same way again; the mutation's own
          // onError (see ./mutations.ts) has already invalidated every query above, so the status
          // chip, history, and action buttons pick up whatever the request's real state now is on
          // their own, without a bespoke refetch-and-resync here.
          setTransferDialogOpen(false);
          // Which half of the mutation failed changes what actually happened, and so what's true
          // to tell the user - see SampleOwnershipTransferError's definition in ./mutations.ts.
          const sampleTransferItselfFailed =
            error instanceof SampleOwnershipTransferError && error.step === "changeOwner";
          uiStore.addAlert(
            mkAlert({
              variant: "error",
              message: sampleTransferItselfFailed
                ? t("requestsManagement.detail.transferFailedAfterFulfilMessage", {
                    id: request.id,
                    sampleName: request.sample.name,
                    requester: requesterFullName,
                  })
                : t("requestsManagement.detail.transferCancelledErrorMessage", {
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
  const prepareSampleEnabled = isActionableState && selectedSubsampleId !== null;
  const rejectColors = { color: "#C62828", borderColor: "#C4726B", backgroundColor: "white" };
  const approveColors = { backgroundColor: "#0B72A3", borderColor: "#0B72A3", color: "white" };
  const approvedDisabledColors = { color: "#4F6E7C", borderColor: "#C2D6E0", backgroundColor: "#EDF4F8" };
  const markAsFulfilledColors = { color: "#3A4A52", borderColor: "#7D8D95", backgroundColor: "white" };
  const statusHelpTranslationKey = status ? statusHelpKey(status) : null;
  const infoBoxText =
    status === "PENDING"
      ? t("requestsManagement.detail.preparationHint.pending")
      : status === "APPROVED"
        ? selectedSubsampleId === null || selectedSubsampleName === null
          ? t("requestsManagement.detail.preparationHint.approvedNoSelection")
          : t("requestsManagement.detail.preparationHint.approvedSelected", {
              subsample: selectedSubsampleName,
              requester: `${request.requester.firstName} ${request.requester.lastName}`,
            })
        : null;

  return (
    <Box sx={{ display: "flex", flexDirection: "column", flexGrow: 1, width: "100%", height: "100%", minWidth: 0 }}>
      <Box
        sx={{
          p: 2,
          backgroundColor: theme.palette.grey[200],
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          gap: 1.5,
        }}
      >
        <Typography variant="h5">
          {t("requestsManagement.detail.title", { id: request.id, sampleName: request.sample.name })}
        </Typography>
        {status && <RequestsStatusChip status={status} />}
      </Box>
      <Box sx={{ overflow: "auto", flexGrow: 1 }}>
        <Box
          sx={{
            p: 1,
            backgroundColor: theme.palette.grey[100],
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            cursor: "pointer",
          }}
          onClick={() => setDetailsExpanded(!detailsExpanded)}
        >
          <Typography variant="subtitle1">{t("requestsManagement.detail.sections.details")}</Typography>
          <IconButton
            size="small"
            aria-label={t(detailsExpanded ? "formSections.collapseSectionNamed" : "formSections.expandSectionNamed", {
              section: t("requestsManagement.detail.sections.details"),
            })}
            aria-expanded={detailsExpanded}
            aria-controls={detailsSectionId}
            sx={{
              transform: detailsExpanded ? "rotate(180deg)" : "rotate(0deg)",
              transition: theme.transitions.create("transform"),
            }}
          >
            <ExpandMoreIcon />
          </IconButton>
        </Box>
        <Divider />
        <Collapse in={detailsExpanded} id={detailsSectionId}>
          <HeadingContext level={4}>
            <Box sx={{ p: 2, display: "flex", flexDirection: "column", gap: 2 }}>
              <DetailField label={t("requestsManagement.detail.fields.requester")}>
                <UserDetails
                  userId={request.requester.id}
                  fullName={`${request.requester.firstName} ${request.requester.lastName}`}
                  position={["bottom", "right"]}
                />
              </DetailField>
              <DetailField label={t("requestsManagement.detail.fields.submitted")}>
                {isoToLocale(request.created)}
              </DetailField>
              <DetailField label={t("requestsManagement.detail.fields.sampleRequested")}>
                <GlobalId record={new LinkableRecordFromGlobalId(request.sample.globalId)} onClick={() => {}} />
              </DetailField>
              <DetailField label={t("requestsManagement.detail.fields.additionalNotes")}>
                {request.note ? request.note : <NoValue label={t("requestsManagement.detail.fields.noNotes")} />}
              </DetailField>
            </Box>
          </HeadingContext>
        </Collapse>
        <Box
          sx={{
            p: 1,
            backgroundColor: theme.palette.grey[100],
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            cursor: "pointer",
          }}
          onClick={() => setApprovalResultExpanded(!approvalResultExpanded)}
        >
          <Typography variant="subtitle1">{t("requestsManagement.detail.sections.approvalResult")}</Typography>
          <IconButton
            size="small"
            aria-label={t(
              approvalResultExpanded ? "formSections.collapseSectionNamed" : "formSections.expandSectionNamed",
              { section: t("requestsManagement.detail.sections.approvalResult") },
            )}
            aria-expanded={approvalResultExpanded}
            aria-controls={approvalResultSectionId}
            sx={{
              transform: approvalResultExpanded ? "rotate(180deg)" : "rotate(0deg)",
              transition: theme.transitions.create("transform"),
            }}
          >
            <ExpandMoreIcon />
          </IconButton>
        </Box>
        <Divider />
        <Collapse in={approvalResultExpanded} id={approvalResultSectionId}>
          <HeadingContext level={4}>
            <Box sx={{ p: 2, display: "flex", flexDirection: "column", gap: 2 }}>
              <DetailField label={t("requestsManagement.detail.fields.status")}>
                <RequestsStatusChip status={status ?? request.status} />
                {status === "APPROVED" && sampleOwnerName && !isSampleOwner && (
                  <Typography variant="body2" sx={{ mt: 1 }}>
                    {t("requestsManagement.detail.fields.approvedMessage", { owner: sampleOwnerName })}
                  </Typography>
                )}
              </DetailField>
              {status === "FULFILLED" ? (
                <DetailField label={t("requestsManagement.detail.fields.transferredSample")}>
                  {transferredSampleGlobalId ? (
                    <GlobalId record={new LinkableRecordFromGlobalId(transferredSampleGlobalId)} onClick={() => {}} />
                  ) : (
                    <NoValue label={t("requestsManagement.detail.fields.noComment")} />
                  )}
                </DetailField>
              ) : (
                <DetailField label={t("requestsManagement.detail.fields.commentFromApprover")}>
                  {comment ? comment : <NoValue label={t("requestsManagement.detail.fields.noComment")} />}
                </DetailField>
              )}
            </Box>
          </HeadingContext>
        </Collapse>
        {isSampleOwner && (
          <>
            <Box sx={{ p: 1, backgroundColor: theme.palette.grey[100] }}>
              <Typography variant="subtitle1">{t("requestsManagement.detail.sections.approveReject")}</Typography>
            </Box>
            <Divider />
            <Box sx={{ p: 2, display: "flex", flexDirection: "column", gap: 2 }}>
              {isActionableState && infoBoxText && <Alert severity="info">{infoBoxText}</Alert>}
              <Box sx={{ display: "flex", alignItems: "center", gap: 2 }}>
                <Button
                  variant="outlined"
                  disabled={!isActionableState}
                  sx={
                    isActionableState
                      ? {
                          "&&": {
                            ...rejectColors,
                            "&:hover": {
                              borderColor: darken(rejectColors.borderColor, 0.2),
                              backgroundColor: STATUS_BACKGROUND.REJECTED,
                            },
                          },
                        }
                      : undefined
                  }
                  onClick={() => setRejectDialogOpen(true)}
                >
                  {t("requestsManagement.detail.rejectButton")}
                </Button>
                <Button
                  variant="contained"
                  disabled={status !== "PENDING" || isProcessingAction}
                  sx={
                    status === "PENDING"
                      ? {
                          "&&": {
                            ...approveColors,
                            "&:hover": { backgroundColor: darken(approveColors.backgroundColor, 0.15) },
                          },
                        }
                      : status === "APPROVED"
                        ? { "&&": approvedDisabledColors }
                        : undefined
                  }
                  onClick={approveRequest}
                >
                  {status === "APPROVED"
                    ? t("requestsManagement.detail.approvedButton")
                    : t("requestsManagement.detail.approveButton")}
                </Button>
                <Button
                  variant="contained"
                  disabled={!prepareSampleEnabled}
                  sx={
                    prepareSampleEnabled
                      ? {
                          "&&": {
                            ...approveColors,
                            "&:hover": { backgroundColor: darken(approveColors.backgroundColor, 0.15) },
                          },
                        }
                      : undefined
                  }
                  onClick={() => (skipChooseMethodDialog ? openTransferDialog() : setChooseMethodDialogOpen(true))}
                >
                  {skipChooseMethodDialog
                    ? t("requestsManagement.detail.transferSampleButton")
                    : t("requestsManagement.detail.prepareSampleButton")}
                </Button>
                <Button
                  variant="outlined"
                  disabled={status !== "APPROVED"}
                  sx={
                    status === "APPROVED"
                      ? {
                          "&&": {
                            ...markAsFulfilledColors,
                            "&:hover": {
                              borderColor: darken(markAsFulfilledColors.borderColor, 0.2),
                              backgroundColor: STATUS_BACKGROUND.FULFILLED,
                            },
                          },
                        }
                      : undefined
                  }
                  onClick={() => setFulfilDialogOpen(true)}
                >
                  {t("requestsManagement.detail.markAsFulfilledButton")}
                </Button>
              </Box>
              {statusHelpTranslationKey && (
                <Typography variant="body2" color="text.secondary">
                  {t(statusHelpTranslationKey)}
                </Typography>
              )}
            </Box>
          </>
        )}
        {!isSampleOwner && status === "PENDING" && (
          <>
            <Box sx={{ p: 1, backgroundColor: theme.palette.grey[100] }}>
              <Typography variant="subtitle1">{t("requestsManagement.detail.sections.approveReject")}</Typography>
            </Box>
            <Divider />
            <Box sx={{ p: 2, display: "flex", flexDirection: "column", gap: 2 }}>
              <Typography variant="body2">{t("requestsManagement.detail.cancelRequestHint")}</Typography>
              <Box sx={{ display: "flex", gap: 2 }}>
                <Button
                  variant="outlined"
                  disabled={isProcessingAction}
                  sx={{
                    "&&": {
                      color: theme.palette.grey[700],
                      borderColor: theme.palette.grey[500],
                      "&:hover": {
                        borderColor: theme.palette.grey[700],
                        backgroundColor: theme.palette.grey[100],
                      },
                    },
                  }}
                  onClick={cancelRequest}
                >
                  {t("sample.requestMaterialSection.cancelRequestButton")}
                </Button>
              </Box>
            </Box>
          </>
        )}
        {isSampleOwner && (
          <>
            <Box
              sx={{
                p: 1,
                backgroundColor: theme.palette.grey[100],
                display: "flex",
                alignItems: "center",
                justifyContent: "space-between",
                cursor: "pointer",
              }}
              onClick={() => setSampleLocationsExpanded(!sampleLocationsExpanded)}
            >
              <CustomTooltip title={t("requestsManagement.detail.fields.sampleLocationTooltip")}>
                <Typography variant="subtitle1">{t("requestsManagement.detail.fields.sampleLocation")}</Typography>
              </CustomTooltip>
              <IconButton
                size="small"
                aria-label={t(
                  sampleLocationsExpanded ? "formSections.collapseSectionNamed" : "formSections.expandSectionNamed",
                  { section: t("requestsManagement.detail.fields.sampleLocation") },
                )}
                aria-expanded={sampleLocationsExpanded}
                aria-controls={sampleLocationsSectionId}
                sx={{
                  transform: sampleLocationsExpanded ? "rotate(180deg)" : "rotate(0deg)",
                  transition: theme.transitions.create("transform"),
                }}
              >
                <ExpandMoreIcon />
              </IconButton>
            </Box>
            <Divider />
            <Collapse in={sampleLocationsExpanded} id={sampleLocationsSectionId}>
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
            </Collapse>
          </>
        )}
        <Box
          sx={{
            p: 1,
            backgroundColor: theme.palette.grey[100],
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            cursor: "pointer",
          }}
          onClick={() => setRequestHistoryExpanded(!requestHistoryExpanded)}
        >
          <Typography variant="subtitle1">{t("requestsManagement.detail.history.sectionTitle")}</Typography>
          <IconButton
            size="small"
            aria-label={t(
              requestHistoryExpanded ? "formSections.collapseSectionNamed" : "formSections.expandSectionNamed",
              { section: t("requestsManagement.detail.history.sectionTitle") },
            )}
            aria-expanded={requestHistoryExpanded}
            aria-controls={requestHistorySectionId}
            sx={{
              transform: requestHistoryExpanded ? "rotate(180deg)" : "rotate(0deg)",
              transition: theme.transitions.create("transform"),
            }}
          >
            <ExpandMoreIcon />
          </IconButton>
        </Box>
        <Divider />
        <Collapse in={requestHistoryExpanded} id={requestHistorySectionId}>
          <RequestHistoryTable statusChanges={statusChanges} />
        </Collapse>
      </Box>
      <Dialog open={rejectDialogOpen} onClose={() => setRejectDialogOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>{t("requestsManagement.detail.rejectDialog.title")}</DialogTitle>
        <DialogContent>
          <Box sx={{ display: "flex", flexDirection: "column", gap: 2, pt: 1 }}>
            {/* component="label" + htmlFor makes this a real, programmatically linked label for
                the TextField below, rather than inert text sitting above it - with no visual
                change, since Typography's variant/styling applies regardless of the rendered tag. */}
            <Typography component="label" htmlFor={reasonFieldId} variant="body2">
              {t("requestsManagement.detail.rejectDialog.reasonLabel")}
            </Typography>
            <TextField
              id={reasonFieldId}
              multiline
              minRows={3}
              fullWidth
              value={rejectReason}
              onChange={({ target: { value } }) => setRejectReason(value)}
            />
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRejectDialogOpen(false)}>{t("common:actions.cancel")}</Button>
          <Button
            variant="outlined"
            disabled={!rejectReason.trim() || isProcessingAction}
            sx={
              rejectReason.trim()
                ? {
                    "&&": {
                      ...rejectColors,
                      "&:hover": {
                        borderColor: darken(rejectColors.borderColor, 0.2),
                        backgroundColor: STATUS_BACKGROUND.REJECTED,
                      },
                    },
                  }
                : undefined
            }
            onClick={rejectRequest}
          >
            {t("requestsManagement.detail.rejectDialog.rejectRequestButton")}
          </Button>
        </DialogActions>
      </Dialog>
      <Dialog open={fulfilDialogOpen} onClose={() => setFulfilDialogOpen(false)} fullWidth maxWidth="sm">
        <DialogContent>
          <Typography variant="body2">{t("requestsManagement.detail.fulfilDialog.message")}</Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setFulfilDialogOpen(false)}>{t("common:actions.cancel")}</Button>
          <Button
            variant="outlined"
            disabled={isProcessingAction}
            sx={{
              "&&": {
                ...markAsFulfilledColors,
                "&:hover": {
                  borderColor: darken(markAsFulfilledColors.borderColor, 0.2),
                  backgroundColor: STATUS_BACKGROUND.FULFILLED,
                },
              },
            }}
            onClick={fulfilRequest}
          >
            {t("requestsManagement.detail.fulfilDialog.fulfilButton")}
          </Button>
        </DialogActions>
      </Dialog>
      <Dialog
        open={chooseMethodDialogOpen}
        onClose={() => {
          setChooseMethodDialogOpen(false);
          setPreparationMethod(null);
        }}
        fullWidth
        maxWidth="sm"
      >
        <DialogTitle>{t("requestsManagement.detail.chooseMethodDialog.title")}</DialogTitle>
        <DialogContent>
          <Typography variant="body1" sx={{ mb: 2 }}>
            {t("requestsManagement.detail.chooseMethodDialog.body")}
          </Typography>
          <RadioGroup
            value={preparationMethod ?? ""}
            onChange={(_, value) => setPreparationMethod(value as "wizard" | "transfer")}
          >
            <FormControlLabel
              value="wizard"
              control={<Radio />}
              label={t("requestsManagement.detail.chooseMethodDialog.wizardOption")}
            />
            <FormControlLabel
              value="transfer"
              control={<Radio />}
              label={t("requestsManagement.detail.chooseMethodDialog.transferOption")}
            />
          </RadioGroup>
          {preparationMethod === "transfer" && subSampleCount !== null && subSampleCount > 1 && (
            <Alert severity="warning" sx={{ mt: 2 }}>
              {t("requestsManagement.detail.chooseMethodDialog.transferWarning", {
                count: subSampleCount,
                requester: `${request.requester.firstName} ${request.requester.lastName}`,
              })}
            </Alert>
          )}
          {preparationMethod === "transfer" && otherActiveRequestsCount !== null && otherActiveRequestsCount > 0 && (
            <Alert severity="warning" sx={{ mt: 2 }}>
              {t("requestsManagement.detail.chooseMethodDialog.otherActiveRequestsWarning")}
            </Alert>
          )}
        </DialogContent>
        <DialogActions>
          <Button
            onClick={() => {
              setChooseMethodDialogOpen(false);
              setPreparationMethod(null);
            }}
          >
            {t("common:actions.cancel")}
          </Button>
          <Button variant="contained" disabled={preparationMethod === null} onClick={proceedWithPreparationMethod}>
            {t("requestsManagement.detail.chooseMethodDialog.proceedButton")}
          </Button>
        </DialogActions>
      </Dialog>
      <Dialog open={prepareDialogOpen} onClose={() => setPrepareDialogOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>{t("requestsManagement.detail.prepareDialog.title")}</DialogTitle>
        <DialogContent>
          <Typography variant="body1" sx={{ mb: 1 }}>
            {t("requestsManagement.detail.prepareDialog.body", { subsample: selectedSubsampleName ?? "" })}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {t("requestsManagement.detail.prepareDialog.comingSoon")}
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPrepareDialogOpen(false)}>{t("common:actions.cancel")}</Button>
          <Button variant="contained" onClick={openTransferDialog}>
            {t("requestsManagement.detail.prepareDialog.nextButton")}
          </Button>
        </DialogActions>
      </Dialog>
      <Dialog open={transferDialogOpen} onClose={() => setTransferDialogOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>
          {t("requestsManagement.detail.transferDialog.heading", {
            sampleName: request.sample.name,
            requester: requesterFullName,
          })}
        </DialogTitle>
        <DialogContent>
          <Alert severity="info" sx={{ mb: 2 }}>
            <TransRichText
              i18nKey="inventory:requestsManagement.detail.transferDialog.warning"
              values={{ requester: requesterFullName }}
            />
          </Alert>
          <Typography component="p" variant="body1" sx={{ mb: 1 }}>
            {t("requestsManagement.detail.transferDialog.whatWillHappen")}
          </Typography>
          <Box component="ul" sx={{ mt: 0, mb: 2, pl: 3 }}>
            {subSampleCount !== null && subSampleCount > 1 && (
              <Typography component="li" variant="body2">
                {subSampleCount === 2
                  ? t("requestsManagement.detail.transferDialog.bullets.subsamplesTransferredBoth", {
                      requester: requesterFullName,
                    })
                  : t("requestsManagement.detail.transferDialog.bullets.subsamplesTransferred", {
                      count: subSampleCount,
                      requester: requesterFullName,
                    })}
              </Typography>
            )}
            <Typography component="li" variant="body2">
              {t("requestsManagement.detail.transferDialog.bullets.subsamplesMoved", { requester: requesterFullName })}
            </Typography>
            <Typography component="li" variant="body2">
              {t("requestsManagement.detail.transferDialog.bullets.requestFulfilled", { id: request.id })}
            </Typography>
            {otherActiveRequests !== null && otherActiveRequests.length > 0 && (
              <Typography component="li" variant="body2">
                {t("requestsManagement.detail.transferDialog.bullets.otherRequestsRejected", {
                  count: otherActiveRequests.length,
                  names: formatList(
                    otherActiveRequests.map((r) => r.requesterName),
                    i18n.resolvedLanguage ?? i18n.language,
                  ),
                })}
              </Typography>
            )}
          </Box>
          <FormControl component="fieldset" fullWidth>
            <PeopleField
              onSelection={(person) => setTransferRecipient(person as PersonModel | null)}
              label={t("contextMenu.transfer.dialog.recipientLabel")}
              recipient={transferRecipient}
              restrictToUser={transferRequesterPerson ?? undefined}
              // Kept separate from transferRecipient (see its declaration): clearing the field
              // back to an unrestricted search must not lift this restriction. The requester is
              // looked up asynchronously (see openTransferDialog), so restrictToUser above is
              // only set on a later render. disableAutoOpen is true from this dialog's very
              // first render, avoiding a race against the field's autoFocus where openOnFocus
              // would still read as unrestricted.
              disableAutoOpen
            />
          </FormControl>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setTransferDialogOpen(false)}>{t("common:actions.cancel")}</Button>
          <Button
            variant="contained"
            // transferRequesterPerson === null also disables this: until the requester lookup
            // in openTransferDialog resolves, PeopleField's restrictToUser is unset and the field
            // searches every user, not just the requester - submitting before then could hand
            // the sample to whoever was picked during that window instead.
            disabled={transferRecipient === null || transferRequesterPerson === null}
            onClick={submitTransfer}
            sx={{
              backgroundColor: darken(theme.palette.primary.main, 0.5),
              color: "white",
              "&:hover": {
                backgroundColor: darken(theme.palette.primary.main, 0.55),
              },
            }}
          >
            {t("common:actions.transfer")}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
