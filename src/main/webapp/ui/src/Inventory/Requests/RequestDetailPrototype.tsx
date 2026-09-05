/** @prototype Storybook-only UI exploration; not production-ready. */
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import Alert from "@mui/material/Alert";
import Button from "@mui/material/Button";
import Stack from "@mui/material/Stack";
import type React from "react";
import { useTranslation } from "react-i18next";
import TitledBox from "../../components/TitledBox";
import {
  FulfilmentActionsPrototype,
  FulfilmentBodyPrototype,
  type FulfilmentStep,
  useRejection,
} from "./FulfilmentDialogPrototype";
import { MyRequestPrototype } from "./RequesterRequestsSectionPrototype";
import RequestStatusChipPrototype from "./RequestStatusChipPrototype";
import type { ProcessOperation, RequestOperationPlan, SampleRequest } from "./types";

type RequestDetailArgs = {
  compact?: boolean;
  /** Owners approve or reject; requesters see the progress of their request. */
  persona: "owner" | "requester";
  request: SampleRequest;
  step: FulfilmentStep;
  operation: ProcessOperation;
  onOperationChange: (operation: ProcessOperation) => void;
  onOperationPlanChange?: (plan: RequestOperationPlan) => void;
  onApprove: () => void;
  onReject: (reason: string) => void;
  onRunOperation: () => void;
  onTransfer: () => void;
  onBack: () => void;
};

/**
 * A single request's page, `/inventory/requests/:id`. This is what a
 * notification links to: the owner approves (then runs the operation and
 * transfers ownership) or rejects with a reason, all inline.
 */
export default function RequestDetailPrototype({
  compact = false,
  persona,
  request,
  step,
  operation,
  onOperationChange,
  onOperationPlanChange,
  onApprove,
  onReject,
  onRunOperation,
  onTransfer,
  onBack,
}: RequestDetailArgs): React.ReactNode {
  const { t } = useTranslation("inventory");
  const rejection = useRejection(onReject);
  const rejected = request.status === "rejected";

  return (
    <Stack useFlexGap spacing={1} sx={{ minWidth: 0 }}>
      <Button sx={{ alignSelf: "flex-start" }} startIcon={<ArrowBackIcon />} onClick={onBack}>
        {t("requests.detail.back")}
      </Button>
      <TitledBox
        border
        title={
          <Stack useFlexGap direction="row" spacing={1.5} sx={{ alignItems: "center", flexWrap: "wrap" }}>
            <span>{t("requests.detail.title", { id: request.id })}</span>
            <RequestStatusChipPrototype status={request.status} />
          </Stack>
        }
      >
        {persona === "requester" ? (
          <MyRequestPrototype sample={request.sample} request={request} />
        ) : (
          <Stack useFlexGap spacing={2}>
            <FulfilmentBodyPrototype
              onOperationPlanChange={onOperationPlanChange}
              compact={compact}
              sample={request.sample}
              request={request}
              step={rejected ? "review" : step}
              operation={operation}
              onOperationChange={onOperationChange}
              rejecting={rejection.rejecting}
              reason={rejection.reason}
              onReasonChange={rejection.setReason}
              onTransfer={onTransfer}
            />
            {rejected && (
              <Alert severity="error">
                {t("requests.outcome.rejected", { reason: request.rejectionReason ?? t("requests.fulfil.noReason") })}
              </Alert>
            )}
            {!rejected && step !== "done" && (
              <Stack
                useFlexGap
                direction="row"
                spacing={1}
                sx={{
                  flexWrap: "wrap",
                  width: "100%",
                  ...(compact && { position: "sticky", bottom: -16, bgcolor: "background.paper", pb: 1 }),
                }}
              >
                <FulfilmentActionsPrototype
                  operation={operation}
                  request={request}
                  step={step}
                  rejecting={rejection.rejecting}
                  onReject={rejection.reject}
                  onApprove={onApprove}
                  onRunOperation={onRunOperation}
                />
              </Stack>
            )}
          </Stack>
        )}
      </TitledBox>
    </Stack>
  );
}
