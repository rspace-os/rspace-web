import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Collapse from "@mui/material/Collapse";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Divider from "@mui/material/Divider";
import FormControl from "@mui/material/FormControl";
import IconButton from "@mui/material/IconButton";
import { useTheme } from "@mui/material/styles";
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
import LinkableRecordFromGlobalId from "@/stores/models/LinkableRecordFromGlobalId";
import * as FetchingData from "@/util/fetchingData";
import { isoToLocale } from "@/util/Util";
import ApiService from "../../common/InvApiService";
import RequestHistoryTable, { type ApiSampleRequestStatusChangeItem } from "./RequestHistoryTable";
import RequestSampleLocations from "./RequestSampleLocations";
import type { ApiSampleRequestListItem } from "./RequestsList";
import RequestsStatusChip from "./RequestsStatusChip";
import { notifySampleRequestStatusChanged } from "./sampleRequestEvents";

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
  const [status, setStatus] = useState(request?.status);
  const [rejectDialogOpen, setRejectDialogOpen] = useState(false);
  const [rejectReason, setRejectReason] = useState("");
  const [statusChanges, setStatusChanges] = useState<Array<ApiSampleRequestStatusChangeItem>>([]);
  const currentUser = useWhoAmI();
  const isSampleOwner = FetchingData.getSuccessValue(currentUser)
    .map((user) => request != null && user.id === request.sample.owner.id)
    .orElse(false);

  useEffect(() => {
    if (!request) return;
    let cancelled = false;
    ApiService.get<{ statusChanges: Array<ApiSampleRequestStatusChangeItem> }>("sampleRequests", request.id)
      .then(({ data }) => {
        if (cancelled) return;
        setStatusChanges(data.statusChanges);
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
              <DetailField
                label={t("requestsManagement.detail.fields.sampleLocation")}
                tooltip={t("requestsManagement.detail.fields.sampleLocationTooltip")}
              >
                <RequestSampleLocations sampleId={request.sample.id} />
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
              </DetailField>
              <DetailField label={t("requestsManagement.detail.fields.commentFromApprover")}>
                {comment ? comment : <NoValue label={t("requestsManagement.detail.fields.noComment")} />}
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
        {isSampleOwner && status === "PENDING" && (
          <>
            <Box sx={{ p: 1, backgroundColor: theme.palette.grey[100] }}>
              <Typography variant="subtitle1">{t("requestsManagement.detail.sections.approveReject")}</Typography>
            </Box>
            <Divider />
            <Box sx={{ p: 2, display: "flex", gap: 2 }}>
              <Button variant="contained" color="success" sx={{ "&&": { color: "white" } }} onClick={approveRequest}>
                {t("requestsManagement.detail.approveButton")}
              </Button>
              <Button
                variant="contained"
                color="error"
                sx={{ "&&": { color: "white" } }}
                onClick={() => setRejectDialogOpen(true)}
              >
                {t("requestsManagement.detail.rejectButton")}
              </Button>
            </Box>
          </>
        )}
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
            color="error"
            variant="contained"
            sx={{ "&&": { color: "white" } }}
            disabled={!rejectReason.trim()}
            onClick={rejectRequest}
          >
            {t("requestsManagement.detail.rejectDialog.rejectRequestButton")}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
