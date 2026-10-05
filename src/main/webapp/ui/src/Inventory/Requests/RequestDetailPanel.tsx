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
import { useEffect, useId, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import CustomTooltip from "@/components/CustomTooltip";
import { Heading, HeadingContext } from "@/components/DynamicHeadingLevel";
import GlobalId from "@/components/GlobalId";
import NoValue from "@/components/NoValue";
import UserDetails from "@/components/UserDetails";
import { useDeploymentProperty } from "@/hooks/api/useDeploymentProperty";
import useWhoAmI from "@/hooks/api/useWhoAmI";
import TransRichText from "@/modules/common/i18n/TransRichText";
import { mkAlert } from "@/stores/contexts/Alert";
import AlwaysNewFactory from "@/stores/models/Factory/AlwaysNewFactory";
import LinkableRecordFromGlobalId from "@/stores/models/LinkableRecordFromGlobalId";
import type PersonModel from "@/stores/models/PersonModel";
import SubSampleModel, { type SubSampleAttrs } from "@/stores/models/SubSampleModel";
import useStores from "@/stores/use-stores";
import * as FetchingData from "@/util/fetchingData";
import * as Parsers from "@/util/parsers";
import { isoToLocale } from "@/util/Util";
import ApiService from "../../common/InvApiService";
import PeopleField from "../components/Inputs/PeopleField";
import type { OperationResult } from "../components/Operations/operationsApi";
import { useOperationWizardLauncher } from "../components/Operations/useOperationWizardLauncher";
import RequestHistoryTable, { type ApiSampleRequestStatusChangeItem } from "./RequestHistoryTable";
import RequestSampleLocations from "./RequestSampleLocations";
import { type ApiSampleRequestListItem, userDisplayName } from "./RequestsList";
import RequestsStatusChip, { STATUS_BACKGROUND } from "./RequestsStatusChip";
import { notifySampleRequestStatusChanged } from "./sampleRequestEvents";

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

/** "Alice", "Alice and Bob", or "Alice, Bob and Carol", for the Transfer Ownership dialog's bullet. */
function formatNameList(names: ReadonlyArray<string>): string {
  if (names.length <= 1) return names[0] ?? "";
  return `${names.slice(0, -1).join(", ")} and ${names[names.length - 1]}`;
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
  const { t } = useTranslation(["inventory", "common"]);
  const theme = useTheme();
  const reasonFieldId = useId();
  const [detailsExpanded, setDetailsExpanded] = useState(true);
  const [approvalResultExpanded, setApprovalResultExpanded] = useState(true);
  const [requestHistoryExpanded, setRequestHistoryExpanded] = useState(true);
  const [sampleLocationsExpanded, setSampleLocationsExpanded] = useState(true);
  const [status, setStatus] = useState(request?.status);
  const [rejectDialogOpen, setRejectDialogOpen] = useState(false);
  const [rejectReason, setRejectReason] = useState("");
  const [fulfilDialogOpen, setFulfilDialogOpen] = useState(false);
  const [selectedSubsampleId, setSelectedSubsampleId] = useState<number | null>(null);
  const [selectedSubsampleName, setSelectedSubsampleName] = useState<string | null>(null);
  const [chooseMethodDialogOpen, setChooseMethodDialogOpen] = useState(false);
  const [preparationMethod, setPreparationMethod] = useState<"wizard" | "transfer" | null>(null);
  const [wizardOrigin, setWizardOrigin] = useState<SubSampleModel | null>(null);
  const [transferDialogOpen, setTransferDialogOpen] = useState(false);
  // Set only when the Transfer Ownership dialog is reached via the "wizard" radio's Operations
  // Wizard having just created a new sample; null (the ordinary case, including every "transfer"
  // radio route into this dialog) means the dialog acts on request.sample exactly as before.
  const [wizardCreatedSample, setWizardCreatedSample] = useState<OperationResult | null>(null);
  const [transferRecipient, setTransferRecipient] = useState<PersonModel | null>(null);
  const [statusChanges, setStatusChanges] = useState<Array<ApiSampleRequestStatusChangeItem>>([]);
  const [sampleOwnerName, setSampleOwnerName] = useState<string | null>(null);
  const [subSampleCount, setSubSampleCount] = useState<number | null>(null);
  const [otherActiveRequests, setOtherActiveRequests] = useState<Array<{
    id: number;
    requesterName: string;
    status: "PENDING" | "APPROVED";
  }> | null>(null);
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
      void launchOperationWizard();
    }
  }, [wizardOrigin]);

  useEffect(() => {
    if (!request) return;
    let cancelled = false;
    ApiService.get<{
      statusChanges: Array<ApiSampleRequestStatusChangeItem>;
      sample: { owner: { firstName: string; lastName: string } };
    }>("sampleRequests", request.id)
      .then(({ data }) => {
        if (cancelled) return;
        setStatusChanges(data.statusChanges);
        setSampleOwnerName(`${data.sample.owner.firstName} ${data.sample.owner.lastName}`);
      })
      .catch((error: unknown) => {
        if (cancelled) return;
        console.error("Failed to fetch sample request status changes", error);
        setStatusChanges([]);
      });
    return () => {
      cancelled = true;
    };
  }, [request, status]);

  // Only needed to size the "transferring will move all subsamples too" warning in the
  // Choose Sample to Prepare dialog, so a plain count suffices; `subSamples` comes back
  // null for a restricted (non-owner) viewer, same as in RequestSampleLocations.
  useEffect(() => {
    if (!request) return;
    let cancelled = false;
    ApiService.get<{ subSamples: Array<{ id: number }> | null }>("samples", request.sample.id)
      .then(({ data }) => {
        if (cancelled) return;
        setSubSampleCount(data.subSamples?.length ?? null);
      })
      .catch((error: unknown) => {
        if (cancelled) return;
        console.error("Failed to fetch sample subsample count", error);
        setSubSampleCount(null);
      });
    return () => {
      cancelled = true;
    };
  }, [request]);

  // Backs both the "other requests will be closed automatically" warning in the Choose Sample to
  // Prepare dialog and the Transfer Ownership dialog's "will be automatically rejected" bullet,
  // which also needs each other request's requester name. Refetched whenever this request's own
  // status changes, since that can move it into or out of the "active" set counted here.
  useEffect(() => {
    if (!request) return;
    let cancelled = false;
    const params = new URLSearchParams({
      sampleId: String(request.sample.id),
      status: "PENDING,APPROVED",
      pageSize: "100",
    });
    ApiService.query<{
      requests: Array<{
        id: number;
        status: "PENDING" | "APPROVED";
        requester: { firstName: string; lastName: string };
      }>;
    }>("sampleRequests", params)
      .then(({ data }) => {
        if (cancelled) return;
        setOtherActiveRequests(
          data.requests
            .filter((r) => r.id !== request.id)
            .map((r) => ({
              id: r.id,
              requesterName: `${r.requester.firstName} ${r.requester.lastName}`,
              status: r.status,
            })),
        );
      })
      .catch((error: unknown) => {
        if (cancelled) return;
        console.error("Failed to fetch other active sample requests", error);
        setOtherActiveRequests(null);
      });
    return () => {
      cancelled = true;
    };
  }, [request, status]);

  const otherActiveRequestsCount = otherActiveRequests?.length ?? null;
  const hasPendingOtherActiveRequest = (otherActiveRequests ?? []).some((r) => r.status === "PENDING");
  const hasApprovedOtherActiveRequest = (otherActiveRequests ?? []).some((r) => r.status === "APPROVED");

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

  const approveRequest = () => {
    void ApiService.update<{ status: string }>("sampleRequests", `${request.id}/status`, {
      status: "APPROVED",
    })
      .then(({ data }) => {
        setStatus(data.status);
        notifySampleRequestStatusChanged();
      })
      .catch((error: unknown) => {
        console.error("Failed to approve sample request", error);
      });
  };

  const rejectRequest = () => {
    void ApiService.update<{ status: string }>("sampleRequests", `${request.id}/status`, {
      status: "REJECTED",
      reason: rejectReason,
    })
      .then(({ data }) => {
        setStatus(data.status);
        setRejectDialogOpen(false);
        notifySampleRequestStatusChanged();
      })
      .catch((error: unknown) => {
        console.error("Failed to reject sample request", error);
      });
  };

  // transferredSampleGlobalId is only accepted by the backend when fulfilling, and only ever
  // names a sample other than the one originally requested when the Operations Wizard created
  // it (see submitTransfer) - every other caller (this one included) omits it, leaving the
  // request's history to imply the originally requested sample, which is correct for them too.
  const markRequestFulfilled = (transferredSampleGlobalId?: string) => {
    return ApiService.update<{ status: string }>("sampleRequests", `${request.id}/status`, {
      status: "FULFILLED",
      ...(transferredSampleGlobalId !== undefined ? { transferredSampleGlobalId } : {}),
    })
      .then(({ data }) => {
        setStatus(data.status);
        notifySampleRequestStatusChanged();
      })
      .catch((error: unknown) => {
        console.error("Failed to fulfil sample request", error);
      });
  };

  const fulfilRequest = () => {
    void markRequestFulfilled().then(() => setFulfilDialogOpen(false));
  };

  // The requester is pre-fetched as a PersonModel via peopleStore.getUser the first time this
  // opens (for either route into it: the "transfer" radio directly, or the "wizard" radio once
  // the Operations Wizard has created a new sample); if that lookup hasn't resolved yet, the
  // field just starts empty and the owner can pick a recipient manually.
  const openTransferDialog = (createdSample: OperationResult | null = null) => {
    setWizardCreatedSample(createdSample);
    setTransferDialogOpen(true);
    if (!transferRecipient) {
      void peopleStore.getUser(request.requester.username).then((person) => {
        if (person) setTransferRecipient(person);
      });
    }
  };

  // Kept in sync every render (see the ref declaration above): the Operations Wizard's onPerformed
  // hands back the new sample it created, which is what then gets offered up in the Transfer
  // Ownership dialog, exactly as the "transfer" radio does for the originally requested sample.
  onWizardPerformedRef.current = (sample) => {
    setWizardOrigin(null);
    if (sample) openTransferDialog(sample);
  };

  const launchOperationsWizardForSelectedSubsample = () => {
    if (selectedSubsampleId === null) return;
    ApiService.get<SubSampleAttrs>("subSamples", selectedSubsampleId)
      .then(({ data }) => {
        setWizardOrigin(new SubSampleModel(new AlwaysNewFactory(), data));
      })
      .catch((error: unknown) => {
        console.error("Failed to load the selected subsample for the Operations Wizard", error);
      });
  };

  const proceedWithPreparationMethod = () => {
    if (!preparationMethod) return;
    setChooseMethodDialogOpen(false);
    setPreparationMethod(null);
    if (preparationMethod === "wizard") {
      launchOperationsWizardForSelectedSubsample();
    } else {
      openTransferDialog();
    }
  };

  // A SubSample has no owner of its own (it always derives from its parent Sample), so
  // "preparing" a subsample for transfer means transferring ownership of a whole Sample: either
  // the originally requested one (the "transfer" radio), or the new one the Operations Wizard
  // just created (the "wizard" radio) - wizardCreatedSample names which.
  //
  // The request is marked fulfilled BEFORE the transfer, not after: the backend authorises
  // the fulfil transition against the transferred sample's current owner, and that's still the
  // caller here. Doing the transfer first would change that sample's owner away from the caller,
  // so the follow-up fulfil call would then fail as the caller no longer being party to
  // the request (reported back as 404, to avoid disclosing the request's existence).
  const submitTransfer = () => {
    if (!transferRecipient) return;
    const targetId = wizardCreatedSample?.id ?? request.sample.id;
    const targetName = wizardCreatedSample?.name ?? request.sample.name;
    void markRequestFulfilled(wizardCreatedSample?.globalId)
      .then(() =>
        ApiService.update<{ id: number }>("samples", `${targetId}/actions/changeOwner`, {
          owner: { username: transferRecipient.username },
        }),
      )
      .then(() => {
        setTransferDialogOpen(false);
        // The transfer just took effect, which server-side may have auto-rejected other
        // requests against the same sample (see SampleApiManagerImpl.changeApiSampleOwner);
        // notify again, now that's actually happened, so the list picks up their new status
        // too. The earlier notification from markRequestFulfilled fires before this transfer
        // call even runs, so it can't have reflected that on its own.
        notifySampleRequestStatusChanged();
        uiStore.addAlert(
          mkAlert({
            variant: "success",
            message: t("requestsManagement.detail.transferSuccessMessage", {
              id: request.id,
              sampleName: targetName,
              requester: `${request.requester.firstName} ${request.requester.lastName}`,
            }),
          }),
        );
      })
      .catch((error: unknown) => {
        console.error("Failed to transfer sample ownership", error);
      });
  };

  // Matches the "Cancel" button behaviour in the Sample form's "Request this sample" box:
  // cancelling is only legal for the requester, and only while the request is PENDING.
  const cancelRequest = () => {
    void ApiService.update<{ status: string }>("sampleRequests", `${request.id}/status`, {
      status: "CANCELLED",
    })
      .then(({ data }) => {
        setStatus(data.status);
        notifySampleRequestStatusChanged();
      })
      .catch((error: unknown) => {
        console.error("Failed to cancel sample request", error);
      });
  };

  const requesterFullName = userDisplayName(request.requester, t);
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
            aria-label={detailsExpanded ? t("formSections.collapseSection") : t("formSections.expandSection")}
            sx={{
              transform: detailsExpanded ? "rotate(180deg)" : "rotate(0deg)",
              transition: theme.transitions.create("transform"),
            }}
          >
            <ExpandMoreIcon />
          </IconButton>
        </Box>
        <Divider />
        <Collapse in={detailsExpanded}>
          <HeadingContext level={4}>
            <Box sx={{ p: 2, display: "flex", flexDirection: "column", gap: 2 }}>
              <DetailField label={t("requestsManagement.detail.fields.requester")}>
                {request.requester.id === null ? (
                  requesterFullName
                ) : (
                  <UserDetails
                    userId={request.requester.id}
                    fullName={requesterFullName}
                    position={["bottom", "right"]}
                  />
                )}
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
            aria-label={approvalResultExpanded ? t("formSections.collapseSection") : t("formSections.expandSection")}
            sx={{
              transform: approvalResultExpanded ? "rotate(180deg)" : "rotate(0deg)",
              transition: theme.transitions.create("transform"),
            }}
          >
            <ExpandMoreIcon />
          </IconButton>
        </Box>
        <Divider />
        <Collapse in={approvalResultExpanded}>
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
                  disabled={status !== "PENDING"}
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
                aria-label={
                  sampleLocationsExpanded ? t("formSections.collapseSection") : t("formSections.expandSection")
                }
                sx={{
                  transform: sampleLocationsExpanded ? "rotate(180deg)" : "rotate(0deg)",
                  transition: theme.transitions.create("transform"),
                }}
              >
                <ExpandMoreIcon />
              </IconButton>
            </Box>
            <Divider />
            <Collapse in={sampleLocationsExpanded}>
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
            aria-label={requestHistoryExpanded ? t("formSections.collapseSection") : t("formSections.expandSection")}
            sx={{
              transform: requestHistoryExpanded ? "rotate(180deg)" : "rotate(0deg)",
              transition: theme.transitions.create("transform"),
            }}
          >
            <ExpandMoreIcon />
          </IconButton>
        </Box>
        <Divider />
        <Collapse in={requestHistoryExpanded}>
          <RequestHistoryTable statusChanges={statusChanges} />
        </Collapse>
      </Box>
      <Dialog open={rejectDialogOpen} onClose={() => setRejectDialogOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>{t("requestsManagement.detail.rejectDialog.title")}</DialogTitle>
        <DialogContent>
          <Box sx={{ display: "flex", flexDirection: "column", gap: 2, pt: 1 }}>
            <Typography variant="body2">{t("requestsManagement.detail.rejectDialog.reasonLabel")}</Typography>
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
            disabled={!rejectReason.trim()}
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
          {chooseMethodTransferWarnings.length > 0 && (
            <Alert severity="warning" sx={{ mt: 2 }}>
              {chooseMethodTransferWarnings.join(" ")}
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
          <Button
            variant="contained"
            color="callToAction"
            disableElevation
            disabled={preparationMethod === null}
            onClick={proceedWithPreparationMethod}
          >
            {t("requestsManagement.detail.chooseMethodDialog.proceedButton")}
          </Button>
        </DialogActions>
      </Dialog>
      {operationWizard}
      <Dialog open={transferDialogOpen} onClose={() => setTransferDialogOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>
          {t("requestsManagement.detail.transferDialog.heading", {
            sampleName: wizardCreatedSample?.name ?? request.sample.name,
            requester: requesterFullName,
          })}
        </DialogTitle>
        <DialogContent>
          <Alert severity="info" sx={{ mb: 2 }}>
            {wizardCreatedSample ? (
              // Reached via the "wizard" radio: the sample being offered up didn't exist before
              // this flow started, so the usual ownership warning (which presumes an existing
              // sample the owner is giving up) doesn't apply - this explains what the new sample
              // is and where it stays if the transfer is cancelled instead.
              t("requestsManagement.detail.transferDialog.newSampleHint", { sampleName: wizardCreatedSample.name })
            ) : (
              <TransRichText
                i18nKey="inventory:requestsManagement.detail.transferDialog.warning"
                values={{ requester: requesterFullName }}
              />
            )}
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
              {wizardCreatedSample
                ? t("requestsManagement.detail.transferDialog.bullets.newSampleMoved", {
                    requester: requesterFullName,
                  })
                : t("requestsManagement.detail.transferDialog.bullets.subsamplesMoved", {
                    requester: requesterFullName,
                  })}
            </Typography>
            <Typography component="li" variant="body2">
              {t("requestsManagement.detail.transferDialog.bullets.requestFulfilled", { id: request.id })}
            </Typography>
            {otherActiveRequests !== null && otherActiveRequests.length > 0 && (
              <Typography component="li" variant="body2">
                {t("requestsManagement.detail.transferDialog.bullets.otherRequestsRejected", {
                  count: otherActiveRequests.length,
                  names: formatNameList(otherActiveRequests.map((r) => r.requesterName)),
                })}
              </Typography>
            )}
          </Box>
          <FormControl component="fieldset" fullWidth>
            <PeopleField
              onSelection={(person) => setTransferRecipient(person as PersonModel | null)}
              label={t("contextMenu.transfer.dialog.recipientLabel")}
              recipient={transferRecipient}
              restrictToUser={transferRecipient ?? undefined}
              // The requester is looked up asynchronously (see openTransferDialog), so
              // restrictToUser above is only set on a later render. disableAutoOpen is
              // true from this dialog's very first render, avoiding a race against the
              // field's autoFocus where openOnFocus would still read as unrestricted.
              disableAutoOpen
            />
          </FormControl>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setTransferDialogOpen(false)}>{t("common:actions.cancel")}</Button>
          <Button
            variant="contained"
            color="callToAction"
            disableElevation
            disabled={transferRecipient === null}
            onClick={submitTransfer}
          >
            {t("common:actions.transfer")}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
