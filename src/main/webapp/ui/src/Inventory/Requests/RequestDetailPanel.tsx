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
import { useEffect, useId, useState } from "react";
import { useTranslation } from "react-i18next";
import CustomTooltip from "@/components/CustomTooltip";
import { Heading, HeadingContext } from "@/components/DynamicHeadingLevel";
import GlobalId from "@/components/GlobalId";
import NoValue from "@/components/NoValue";
import UserDetails from "@/components/UserDetails";
import useWhoAmI from "@/hooks/api/useWhoAmI";
import TransRichText from "@/modules/common/i18n/TransRichText";
import { mkAlert } from "@/stores/contexts/Alert";
import LinkableRecordFromGlobalId from "@/stores/models/LinkableRecordFromGlobalId";
import type PersonModel from "@/stores/models/PersonModel";
import useStores from "@/stores/use-stores";
import * as FetchingData from "@/util/fetchingData";
import { isoToLocale } from "@/util/Util";
import ApiService from "../../common/InvApiService";
import PeopleField from "../components/Inputs/PeopleField";
import RequestHistoryTable, { type ApiSampleRequestStatusChangeItem } from "./RequestHistoryTable";
import RequestSampleLocations from "./RequestSampleLocations";
import type { ApiSampleRequestListItem } from "./RequestsList";
import RequestsStatusChip, { STATUS_BACKGROUND } from "./RequestsStatusChip";
import { notifySampleRequestStatusChanged } from "./sampleRequestEvents";

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
      <Box sx={{ wordBreak: "break-all" }}>{children}</Box>
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
  const [prepareDialogOpen, setPrepareDialogOpen] = useState(false);
  const [transferDialogOpen, setTransferDialogOpen] = useState(false);
  const [transferRecipient, setTransferRecipient] = useState<PersonModel | null>(null);
  const [statusChanges, setStatusChanges] = useState<Array<ApiSampleRequestStatusChangeItem>>([]);
  const [sampleOwnerName, setSampleOwnerName] = useState<string | null>(null);
  const [subSampleCount, setSubSampleCount] = useState<number | null>(null);
  const currentUser = useWhoAmI();
  const { peopleStore, uiStore } = useStores();
  const isSampleOwner = FetchingData.getSuccessValue(currentUser)
    .map((user) => request != null && user.id === request.sample.owner.id)
    .orElse(false);

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

  const markRequestFulfilled = () => {
    return ApiService.update<{ status: string }>("sampleRequests", `${request.id}/status`, {
      status: "FULFILLED",
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

  // The requester was pre-fetched as a PersonModel via peopleStore.getUser when the
  // Preparing Sample dialog's "Next" button was pressed; if that lookup hasn't resolved
  // yet, the field just starts empty and the owner can pick a recipient manually.
  const openTransferDialog = () => {
    setPrepareDialogOpen(false);
    setTransferDialogOpen(true);
    if (!transferRecipient) {
      void peopleStore.getUser(request.requester.username).then((person) => {
        if (person) setTransferRecipient(person);
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
    if (!transferRecipient) return;
    void markRequestFulfilled()
      .then(() =>
        ApiService.update<{ id: number }>("samples", `${request.sample.id}/actions/changeOwner`, {
          owner: { username: transferRecipient.username },
        }),
      )
      .then(() => {
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
                  <Typography variant="body2" sx={{ mt: 1, wordBreak: "normal" }}>
                    {t("requestsManagement.detail.fields.approvedMessage", { owner: sampleOwnerName })}
                  </Typography>
                )}
              </DetailField>
              <DetailField label={t("requestsManagement.detail.fields.commentFromApprover")}>
                {comment ? comment : <NoValue label={t("requestsManagement.detail.fields.noComment")} />}
              </DetailField>
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
                  onClick={() => setChooseMethodDialogOpen(true)}
                >
                  {t("requestsManagement.detail.prepareSampleButton")}
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
          {preparationMethod === "transfer" && subSampleCount !== null && subSampleCount > 1 && (
            <Alert severity="warning" sx={{ mt: 2 }}>
              {t("requestsManagement.detail.chooseMethodDialog.transferWarning", {
                count: subSampleCount,
                requester: `${request.requester.firstName} ${request.requester.lastName}`,
              })}
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
        <DialogTitle>{t("contextMenu.transfer.dialog.title")}</DialogTitle>
        <DialogContent>
          <Typography component="p" variant="body1" sx={{ mb: 2 }}>
            <TransRichText i18nKey="inventory:contextMenu.transfer.dialog.body" />
          </Typography>
          <Typography component="p" variant="body1" sx={{ mb: 2 }}>
            {t("contextMenu.transfer.dialog.recipientSearchHint")}
          </Typography>
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
          <Button variant="contained" disabled={transferRecipient === null} onClick={submitTransfer}>
            {t("common:actions.transfer")}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
