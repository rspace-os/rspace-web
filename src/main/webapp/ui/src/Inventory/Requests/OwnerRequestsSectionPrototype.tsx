/** @prototype Storybook-only UI exploration; not production-ready. */
import CancelIcon from "@mui/icons-material/Cancel";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import PendingIcon from "@mui/icons-material/Pending";
import PlayArrowIcon from "@mui/icons-material/PlayArrow";
import SwapHorizIcon from "@mui/icons-material/SwapHoriz";
import Avatar from "@mui/material/Avatar";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import FormControlLabel from "@mui/material/FormControlLabel";
import List from "@mui/material/List";
import ListItem from "@mui/material/ListItem";
import Stack from "@mui/material/Stack";
import Switch from "@mui/material/Switch";
import Typography from "@mui/material/Typography";
import type React from "react";
import { useTranslation } from "react-i18next";
import GlobalId from "../../components/GlobalId";
import FormField from "../../components/Inputs/FormField";
import StepperPanel from "../components/Stepper/StepperPanel";
import RequestStatusChipPrototype from "./RequestStatusChipPrototype";
import { isRequestActionable, type SampleRequest } from "./types";

export function initials(fullName: string): string {
  return fullName
    .split(" ")
    .map((part) => part.charAt(0))
    .join("")
    .toUpperCase();
}

function OutcomePrototype({ request }: { request: SampleRequest }): React.ReactNode {
  const { t } = useTranslation("inventory");
  if (request.status === "rejected") {
    return (
      <>
        <CancelIcon fontSize="small" color="error" aria-hidden />
        <Typography variant="body2" color="text.secondary">
          {t("requests.outcome.rejected", { reason: request.rejectionReason ?? "" })}
        </Typography>
      </>
    );
  }
  if (request.status === "prepared" && request.produced) {
    return (
      <>
        <PendingIcon fontSize="small" color="warning" aria-hidden />
        <Typography variant="body2" color="text.secondary">
          {t("requests.outcome.prepared", { globalId: request.produced.globalId })}
        </Typography>
        <GlobalId record={request.produced} />
      </>
    );
  }
  if (request.status === "fulfilled" && request.produced) {
    return (
      <>
        <CheckCircleIcon fontSize="small" color="success" aria-hidden />
        <Typography variant="body2" color="text.secondary">
          {t("requests.outcome.fulfilled", { name: request.requester.fullName })}
        </Typography>
        <GlobalId record={request.produced} />
      </>
    );
  }
  return null;
}

function RequestRowPrototype({
  request,
  onReview,
  onTransfer,
}: {
  request: SampleRequest;
  onReview: (request: SampleRequest) => void;
  onTransfer: (request: SampleRequest) => void;
}): React.ReactNode {
  const { t } = useTranslation("inventory");
  const hasOutcome =
    request.status === "rejected" || (["prepared", "fulfilled"].includes(request.status) && Boolean(request.produced));
  return (
    <ListItem divider alignItems="flex-start" sx={{ px: 0, flexDirection: "column", gap: 1 }}>
      <Stack
        useFlexGap
        direction="row"
        spacing={1.5}
        sx={{ alignItems: "flex-start", flexWrap: "wrap", rowGap: 1, width: "100%" }}
      >
        <Avatar aria-hidden sx={{ width: 34, height: 34, fontSize: "0.75rem", bgcolor: "grey.700" }}>
          {initials(request.requester.fullName)}
        </Avatar>
        <Stack sx={{ flex: "1 1 12rem", minWidth: 0, overflowWrap: "anywhere" }}>
          <Stack useFlexGap direction="row" spacing={1} sx={{ alignItems: "center", flexWrap: "wrap" }}>
            <Typography variant="body2" sx={{ fontWeight: "fontWeightMedium" }}>
              {request.requester.fullName}
            </Typography>
            <Typography variant="caption" color="text.secondary" sx={{ fontFamily: "monospace" }}>
              {request.id}
            </Typography>
            <RequestStatusChipPrototype status={request.status} />
          </Stack>
          <Typography variant="body2" color="text.secondary">
            {t("requests.quoted", { note: request.note })}
          </Typography>
        </Stack>
        <Typography variant="caption" color="text.secondary" sx={{ flexShrink: 0, ml: "auto", pt: 0.5 }}>
          {request.when}
        </Typography>
        {isRequestActionable(request) && (
          <Button
            variant="contained"
            color="callToAction"
            size="small"
            startIcon={<PlayArrowIcon />}
            onClick={() => onReview(request)}
            sx={{ flexShrink: 0 }}
          >
            {request.status === "approved" ? t("requests.actions.resume") : t("requests.actions.review")}
          </Button>
        )}
      </Stack>
      {hasOutcome && (
        <Stack
          useFlexGap
          direction="row"
          spacing={1}
          sx={{ alignItems: "center", flexWrap: "wrap", pl: { xs: 0, sm: 6 }, width: "100%" }}
        >
          <OutcomePrototype request={request} />
          {request.status === "prepared" && (
            <Button variant="outlined" size="small" startIcon={<SwapHorizIcon />} onClick={() => onTransfer(request)}>
              {t("requests.actions.transferTo", { name: request.requester.fullName.split(" ")[0] })}
            </Button>
          )}
        </Stack>
      )}
    </ListItem>
  );
}

type OwnerRequestsSectionArgs = {
  managementAction?: React.ReactNode;
  requestable: boolean;
  onRequestableChange: (requestable: boolean) => void;
  requests: ReadonlyArray<SampleRequest>;
  onReview: (request: SampleRequest) => void;
  onTransfer: (request: SampleRequest) => void;
};

/**
 * The owner's view of a sample's requests: the "Available for request"
 * switch plus every request raised against the sample, pending first.
 * Drops into `Sample/Form.tsx` as one more `StepperPanel`.
 */
export default function OwnerRequestsSectionPrototype({
  managementAction,
  requestable,
  onRequestableChange,
  requests,
  onReview,
  onTransfer,
}: OwnerRequestsSectionArgs): React.ReactNode {
  const { t } = useTranslation("inventory");
  const pendingCount = requests.filter((r) => r.status === "pending").length;

  return (
    <StepperPanel
      icon="sample"
      title={
        <Stack
          useFlexGap
          component="span"
          direction="row"
          spacing={1}
          sx={{ display: "inline-flex", alignItems: "center", verticalAlign: "middle" }}
        >
          <span>{t("requests.sectionTitle")}</span>
          {pendingCount > 0 && (
            <Chip
              component="span"
              size="small"
              color="error"
              label={t("requests.pendingCount", { count: pendingCount })}
            />
          )}
        </Stack>
      }
      sectionName="requests"
      recordType="sample"
    >
      <FormField
        value={requestable}
        label={t("requests.toggle.label")}
        explanation={requestable ? t("requests.toggle.on") : t("requests.toggle.off")}
        doNotAttachIdToLabel
        renderInput={() => (
          <FormControlLabel
            control={<Switch checked={requestable} onChange={(event) => onRequestableChange(event.target.checked)} />}
            label={t("requests.toggle.switchLabel")}
          />
        )}
      />
      {managementAction ??
        (requests.length === 0 ? (
          <Typography variant="body2" color="text.secondary">
            {t("requests.empty")}
          </Typography>
        ) : (
          <List disablePadding aria-label={t("requests.sectionTitle")}>
            {requests.map((request) => (
              <RequestRowPrototype key={request.id} request={request} onReview={onReview} onTransfer={onTransfer} />
            ))}
          </List>
        ))}
    </StepperPanel>
  );
}
