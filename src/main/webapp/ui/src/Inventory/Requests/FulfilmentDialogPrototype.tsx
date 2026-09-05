/** @prototype Storybook-only UI exploration; not production-ready. */
import AccountTreeIcon from "@mui/icons-material/AccountTree";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import CloseIcon from "@mui/icons-material/Close";
import InfoOutlinedIcon from "@mui/icons-material/InfoOutlined";
import Inventory2Icon from "@mui/icons-material/Inventory2";
import ScienceIcon from "@mui/icons-material/Science";
import SwapHorizIcon from "@mui/icons-material/SwapHoriz";
import Alert from "@mui/material/Alert";
import Avatar from "@mui/material/Avatar";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import ButtonGroup from "@mui/material/ButtonGroup";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import FormControlLabel from "@mui/material/FormControlLabel";
import IconButton from "@mui/material/IconButton";
import MenuItem from "@mui/material/MenuItem";
import Radio from "@mui/material/Radio";
import RadioGroup from "@mui/material/RadioGroup";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import React from "react";
import { useTranslation } from "react-i18next";
import GlobalId from "../../components/GlobalId";
import { initials } from "./OwnerRequestsSectionPrototype";
import {
  type ProcessOperation,
  type RequestableSample,
  type RequestOperationPlan,
  requestOperationPlan,
  type SampleRequest,
  validRequestOperation,
} from "./types";

export type FulfilmentStep = "review" | "operation" | "done";

/** Rejecting is two clicks: the first reveals the reason field, the second confirms. */
export function useRejection(onReject: (reason: string) => void) {
  const { t } = useTranslation("inventory");
  const [rejecting, setRejecting] = React.useState(false);
  const [reason, setReason] = React.useState("");
  const reset = () => {
    setRejecting(false);
    setReason("");
  };
  return {
    rejecting,
    reason,
    setReason,
    reset,
    reject: () => {
      if (!rejecting) {
        setRejecting(true);
        return;
      }
      onReject(reason.trim() || t("requests.fulfil.noReason"));
      reset();
    },
  };
}

export function fulfilmentTitle(
  t: ReturnType<typeof useTranslation<"inventory">>["t"],
  request: SampleRequest,
  step: FulfilmentStep,
): string {
  return {
    review: t("requests.fulfil.reviewTitle", { id: request.id }),
    operation: t("requests.fulfil.operationTitle", { id: request.id }),
    done:
      request.status === "fulfilled"
        ? t("requests.fulfil.fulfilledTitle", { id: request.id })
        : t("requests.fulfil.preparedTitle", { id: request.id }),
  }[step];
}

type FulfilmentBodyArgs = {
  compact?: boolean;
  sample: RequestableSample;
  request: SampleRequest;
  step: FulfilmentStep;
  operation: ProcessOperation;
  onOperationChange: (operation: ProcessOperation) => void;
  onOperationPlanChange?: (plan: RequestOperationPlan) => void;
  rejecting: boolean;
  reason: string;
  onReasonChange: (reason: string) => void;
  onTransfer: () => void;
};

/**
 * Owner-side fulfilment content: review (with an inline rejection reason),
 * pick the process operation that produces the material, then transfer
 * ownership. This prototype models the RSDEV-1231 single-origin Aliquot/Derive
 * effects with mL fixtures, no template and no SOP. It does not call the API.
 */
export function FulfilmentBodyPrototype({
  compact = false,
  sample,
  request,
  step,
  operation,
  onOperationChange,
  onOperationPlanChange,
  rejecting,
  reason,
  onReasonChange,
  onTransfer,
}: FulfilmentBodyArgs): React.ReactNode {
  const { t } = useTranslation("inventory");
  const fulfilled = request.status === "fulfilled";
  const firstName = request.requester.fullName.split(" ")[0];
  const produced = request.produced;
  const plan = requestOperationPlan(request);

  const operations: ReadonlyArray<{ value: ProcessOperation; icon: React.ReactNode }> = [
    { value: "aliquot", icon: <ScienceIcon /> },
    { value: "derive", icon: <AccountTreeIcon /> },
  ];

  return (
    <>
      {step === "review" && (
        <Stack useFlexGap spacing={2}>
          <Stack
            useFlexGap
            direction="row"
            spacing={1.5}
            sx={{ alignItems: "center", p: 1.5, bgcolor: "action.hover", borderRadius: 1 }}
          >
            <Avatar aria-hidden sx={{ bgcolor: "grey.700" }}>
              {initials(request.requester.fullName)}
            </Avatar>
            <Stack sx={{ minWidth: 0, flex: 1 }}>
              <Typography variant="body1" sx={{ fontWeight: "fontWeightMedium", overflowWrap: "anywhere" }}>
                {request.requester.fullName}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                {t("requests.fulfil.requestMeta", {
                  id: request.id,
                  username: request.requester.username,
                  when: request.when,
                })}
              </Typography>
            </Stack>
          </Stack>
          <Box sx={{ alignSelf: "flex-start" }}>
            <GlobalId
              record={{
                id: Number(sample.globalId.slice(2)),
                name: sample.name,
                globalId: sample.globalId,
                permalinkURL: `/inventory/sample/${sample.globalId.slice(2)}`,
                iconName: "sample",
                recordTypeLabel: t("recordTypes.sample.singular"),
              }}
            />
          </Box>
          <Box
            sx={(theme) => ({
              borderLeft: `3px solid ${theme.palette.record.sample.bg}`,
              pl: 2,
              py: 0.5,
            })}
          >
            {compact && request.note.length > 240 ? (
              <Box component="details" sx={{ overflowWrap: "anywhere" }}>
                <summary>{`${t("requests.list.columns.note")}: ${request.note.slice(0, 160)}…`}</summary>
                <Typography>{request.note}</Typography>
              </Box>
            ) : (
              <Typography variant="body1" sx={{ overflowWrap: "anywhere" }}>
                {request.note}
              </Typography>
            )}
          </Box>
          {rejecting && (
            <TextField
              label={t("requests.fulfil.rejectReasonLabel")}
              placeholder={t("requests.fulfil.rejectReasonPlaceholder")}
              multiline
              minRows={2}
              fullWidth
              color="error"
              focused
              value={reason}
              onChange={(event) => onReasonChange(event.target.value)}
            />
          )}
        </Stack>
      )}

      {step === "operation" && (
        <Stack useFlexGap spacing={2}>
          <RadioGroup
            sx={{ gap: 1 }}
            aria-label={t("requests.fulfil.operationLabel")}
            value={operation}
            onChange={(event) => onOperationChange(event.target.value as ProcessOperation)}
          >
            {operations.map((option) => (
              <FormControlLabel
                key={option.value}
                value={option.value}
                control={<Radio />}
                sx={(theme) => ({
                  alignItems: "flex-start",
                  m: 0,
                  minWidth: 0,
                  p: 1.5,
                  width: "100%",
                  border: `1px solid ${operation === option.value ? theme.palette.record.sample.bg : theme.palette.divider}`,
                  borderRadius: 1,
                  bgcolor: operation === option.value ? theme.palette.record.sample.lighter : undefined,
                })}
                label={
                  <Stack useFlexGap direction="row" spacing={1.5} sx={{ alignItems: "flex-start", minWidth: 0, pt: 1 }}>
                    <Box sx={{ color: "text.secondary", display: "flex", flexShrink: 0 }}>{option.icon}</Box>
                    <Stack sx={{ minWidth: 0 }}>
                      <Typography variant="body1" sx={{ fontWeight: "fontWeightMedium", overflowWrap: "anywhere" }}>
                        {t(`requests.fulfil.ops.${option.value}.label`)}
                      </Typography>
                      <Typography variant="body2" color="text.secondary" sx={{ overflowWrap: "anywhere" }}>
                        {t(`requests.fulfil.ops.${option.value}.body`, { globalId: sample.globalId })}
                      </Typography>
                    </Stack>
                  </Stack>
                }
              />
            ))}
          </RadioGroup>
          <TextField
            select
            label={t("requests.fulfil.plan.origin")}
            value={plan.originId}
            onChange={(event) => onOperationPlanChange?.({ ...plan, originId: event.target.value })}
          >
            {(sample.origins ?? []).map((origin) => (
              <MenuItem
                key={origin.globalId}
                value={origin.globalId}
              >{`${origin.globalId} · ${origin.quantity} mL`}</MenuItem>
            ))}
          </TextField>
          <TextField
            label={t("requests.fulfil.plan.name")}
            value={plan.sampleName}
            onChange={(event) => onOperationPlanChange?.({ ...plan, sampleName: event.target.value })}
          />
          {operation === "derive" && (
            <TextField
              required
              label={t("requests.fulfil.plan.process")}
              value={plan.processName}
              onChange={(event) => onOperationPlanChange?.({ ...plan, processName: event.target.value })}
            />
          )}
          {(["count", "eachAmount", "amountTaken"] as const).map((field) => (
            <TextField
              key={field}
              type="number"
              label={t(`requests.fulfil.plan.${field}`)}
              value={plan[field]}
              onChange={(event) => onOperationPlanChange?.({ ...plan, [field]: Number(event.target.value) })}
              slotProps={{
                htmlInput: {
                  min: field === "count" ? 1 : 0.001,
                  step: field === "count" ? 1 : 0.001,
                  ...(field === "count" ? { max: 100 } : {}),
                },
              }}
            />
          ))}
          {!validRequestOperation(request, operation) && (
            <Alert severity="warning">{t("requests.fulfil.plan.invalid")}</Alert>
          )}
          <Typography variant="body2">
            {t("requests.fulfil.plan.summary", {
              origin: plan.originId,
              count: plan.count,
              amount: plan.eachAmount,
              taken: plan.amountTaken,
              relation: operation === "aliquot" ? "IsPartOf" : "IsDerivedFrom",
            })}
          </Typography>
          <Stack useFlexGap direction="row" spacing={1.5} sx={{ minWidth: 0 }}>
            <InfoOutlinedIcon fontSize="small" color="action" aria-hidden />
            <Typography variant="body2" color="text.secondary" sx={{ minWidth: 0, overflowWrap: "anywhere" }}>
              {t("requests.fulfil.opsInfo", { globalId: sample.globalId })}
            </Typography>
          </Stack>
        </Stack>
      )}

      {step === "done" && produced && (
        <Stack useFlexGap spacing={2} sx={{ alignItems: "center", textAlign: "center" }}>
          {fulfilled ? (
            <CheckCircleIcon color="success" sx={{ fontSize: 44 }} aria-hidden />
          ) : (
            <Inventory2Icon color="warning" sx={{ fontSize: 44 }} aria-hidden />
          )}
          <Typography variant="body1" sx={{ maxWidth: 440 }}>
            {fulfilled
              ? t("requests.fulfil.doneBody", {
                  produced: produced.name,
                  producedId: produced.globalId,
                  name: request.requester.fullName,
                  firstName,
                  globalId: sample.globalId,
                })
              : t("requests.fulfil.preparedBody", {
                  produced: produced.name,
                  producedId: produced.globalId,
                  firstName,
                  globalId: sample.globalId,
                })}
          </Typography>
          <GlobalId record={produced} />
          {request.preparation && (
            <Typography variant="body2">
              {t("requests.fulfil.plan.result", { ...request.preparation, origin: request.preparation.originId })}
            </Typography>
          )}
          <Stack
            useFlexGap
            direction="row"
            spacing={1.5}
            sx={(theme) => ({
              alignItems: "center",
              flexWrap: "wrap",
              width: "100%",
              textAlign: "left",
              p: 2,
              border: `1px solid ${theme.palette.divider}`,
              borderRadius: 1,
            })}
          >
            <SwapHorizIcon color="action" aria-hidden />
            <Typography
              variant="body1"
              sx={{ minWidth: 0, flex: "1 1 12rem", fontWeight: "fontWeightMedium", overflowWrap: "anywhere" }}
            >
              {t("requests.fulfil.transferTitle", { name: request.requester.fullName })}
            </Typography>
            <Button variant="contained" color="callToAction" onClick={onTransfer} disabled={fulfilled}>
              {fulfilled ? t("requests.fulfil.transferred") : t("requests.fulfil.transfer")}
            </Button>
          </Stack>
        </Stack>
      )}
    </>
  );
}

type FulfilmentActionsArgs = {
  request: SampleRequest;
  step: FulfilmentStep;
  rejecting: boolean;
  onReject: () => void;
  onApprove: () => void;
  onRunOperation: () => void;
  operation?: ProcessOperation;
  /** Omit on the request page, where there is nothing to close. */
  onClose?: () => void;
};

/** The action row that goes with FulfilmentBodyPrototype: Reject / Approve, Run operation, Close. */
export function FulfilmentActionsPrototype({
  request,
  step,
  rejecting,
  onReject,
  onApprove,
  onRunOperation,
  operation = "aliquot",
  onClose,
}: FulfilmentActionsArgs): React.ReactNode {
  const { t } = useTranslation("inventory");
  const fulfilled = request.status === "fulfilled";
  return (
    <>
      {step === "review" && (
        <ButtonGroup
          variant="outlined"
          color="primary"
          aria-label={t("requests.detail.title", { id: request.id })}
          sx={{ maxWidth: "100%" }}
        >
          <Button color="error" onClick={onReject}>
            {rejecting ? t("requests.fulfil.confirmReject") : t("requests.fulfil.reject")}
          </Button>
          <Button variant="contained" color="callToAction" onClick={onApprove}>
            {t("requests.fulfil.approve")}
          </Button>
        </ButtonGroup>
      )}
      <Box sx={{ flex: "1 1 auto", minWidth: 0 }} />
      {step === "operation" && (
        <Button
          variant="contained"
          color="callToAction"
          onClick={onRunOperation}
          disabled={!validRequestOperation(request, operation)}
        >
          {t("requests.fulfil.runOperation")}
        </Button>
      )}
      {step === "done" &&
        onClose &&
        (fulfilled ? (
          <Button variant="contained" color="callToAction" onClick={onClose}>
            {t("requests.fulfil.close")}
          </Button>
        ) : (
          <Button variant="outlined" onClick={onClose}>
            {t("requests.fulfil.notNow")}
          </Button>
        ))}
    </>
  );
}

type FulfilmentDialogArgs = {
  open: boolean;
  /** Only the dialog is rendered in Storybook step stories, so it can render in place. */
  disablePortal?: boolean;
  sample: RequestableSample;
  request: SampleRequest;
  step: FulfilmentStep;
  operation: ProcessOperation;
  onOperationChange: (operation: ProcessOperation) => void;
  onOperationPlanChange?: (plan: RequestOperationPlan) => void;
  onApprove: () => void;
  onReject: (reason: string) => void;
  onRunOperation: () => void;
  onTransfer: () => void;
  onClose: () => void;
};

/** FulfilmentBodyPrototype in a dialog, for the Review button on the sample record itself. */
export default function FulfilmentDialogPrototype({
  open,
  disablePortal,
  sample,
  request,
  step,
  operation,
  onOperationChange,
  onOperationPlanChange,
  onApprove,
  onReject,
  onRunOperation,
  onTransfer,
  onClose,
}: FulfilmentDialogArgs): React.ReactNode {
  const { t } = useTranslation("inventory");
  const rejection = useRejection(onReject);

  const handleClose = () => {
    rejection.reset();
    onClose();
  };

  return (
    <Dialog open={open} onClose={handleClose} disablePortal={disablePortal} fullWidth maxWidth="sm">
      <DialogTitle sx={{ display: "flex", alignItems: "center", gap: 1.5, pr: 7 }}>
        {fulfilmentTitle(t, request, step)}
      </DialogTitle>
      <IconButton
        aria-label={t("requests.fulfil.closeDialog")}
        onClick={handleClose}
        sx={{ position: "absolute", right: 12, top: 12 }}
      >
        <CloseIcon />
      </IconButton>
      <DialogContent dividers>
        <FulfilmentBodyPrototype
          onOperationPlanChange={onOperationPlanChange}
          sample={sample}
          request={request}
          step={step}
          operation={operation}
          onOperationChange={onOperationChange}
          rejecting={rejection.rejecting}
          reason={rejection.reason}
          onReasonChange={rejection.setReason}
          onTransfer={onTransfer}
        />
      </DialogContent>
      <DialogActions sx={{ flexWrap: "wrap", gap: 1 }}>
        <FulfilmentActionsPrototype
          operation={operation}
          request={request}
          step={step}
          rejecting={rejection.rejecting}
          onReject={rejection.reject}
          onApprove={onApprove}
          onRunOperation={onRunOperation}
          onClose={handleClose}
        />
      </DialogActions>
    </Dialog>
  );
}
