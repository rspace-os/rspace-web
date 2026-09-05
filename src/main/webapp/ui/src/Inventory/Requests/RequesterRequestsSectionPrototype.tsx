/** @prototype Storybook-only UI exploration; not production-ready. */
import LockIcon from "@mui/icons-material/Lock";
import Alert from "@mui/material/Alert";
import Button from "@mui/material/Button";
import Stack from "@mui/material/Stack";
import Step from "@mui/material/Step";
import StepContent from "@mui/material/StepContent";
import StepLabel from "@mui/material/StepLabel";
import Stepper from "@mui/material/Stepper";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import React from "react";
import { useTranslation } from "react-i18next";
import GlobalId from "../../components/GlobalId";
import FormField from "../../components/Inputs/FormField";
import StepperPanel from "../components/Stepper/StepperPanel";
import RequestStatusChipPrototype from "./RequestStatusChipPrototype";
import type { RequestableSample, RequestStatus, SampleRequest } from "./types";

const STEP_ORDER: ReadonlyArray<RequestStatus> = ["pending", "approved", "prepared", "fulfilled"];

function RequestFormPrototype({
  sample,
  onSubmit,
}: {
  sample: RequestableSample;
  onSubmit: (note: string) => void;
}): React.ReactNode {
  const { t } = useTranslation("inventory");
  const [note, setNote] = React.useState("");
  return (
    <>
      <Typography variant="body2" color="text.secondary">
        {t("requests.request.explanation", { owner: sample.owner.fullName })}
      </Typography>
      <FormField
        value={note}
        label={t("requests.request.noteLabel")}
        renderInput={({ id }) => (
          <TextField
            id={id}
            multiline
            minRows={3}
            fullWidth
            value={note}
            onChange={(event) => setNote(event.target.value)}
            placeholder={t("requests.request.notePlaceholder")}
          />
        )}
      />
      <Stack
        useFlexGap
        direction="row"
        spacing={2}
        sx={{ alignItems: "center", flexWrap: "wrap", justifyContent: "space-between", rowGap: 1 }}
      >
        <Typography variant="caption" color="text.secondary" sx={{ minWidth: 0, flex: 1, overflowWrap: "anywhere" }}>
          {t("requests.request.recordedAgainst", { globalId: sample.globalId })}
        </Typography>
        <Button variant="contained" color="callToAction" onClick={() => onSubmit(note)} sx={{ ml: "auto" }}>
          {t("requests.request.send")}
        </Button>
      </Stack>
    </>
  );
}

export function MyRequestPrototype({
  sample,
  request,
}: {
  sample: RequestableSample;
  request: SampleRequest;
}): React.ReactNode {
  const { t } = useTranslation("inventory");
  const rejected = request.status === "rejected";
  const reached = rejected ? 1 : STEP_ORDER.indexOf(request.status);
  const produced = request.produced?.globalId ?? "";
  const steps = [
    {
      title: t("requests.mine.steps.pending.title"),
      body: t("requests.mine.steps.pending.body", { globalId: sample.globalId }),
    },
    rejected
      ? { title: t("requests.status.rejected"), body: request.rejectionReason ?? "" }
      : {
          title: t("requests.mine.steps.approved.title"),
          body:
            reached >= 1
              ? t("requests.mine.steps.approved.done", { owner: sample.owner.fullName })
              : t("requests.mine.steps.approved.waiting"),
        },
    {
      title: t("requests.mine.steps.prepared.title"),
      body:
        reached >= 2
          ? t("requests.mine.steps.prepared.done", { produced, globalId: sample.globalId })
          : t("requests.mine.steps.prepared.waiting"),
    },
    {
      title: t("requests.mine.steps.fulfilled.title"),
      body:
        reached >= 3
          ? t("requests.mine.steps.fulfilled.done", { produced })
          : t("requests.mine.steps.fulfilled.waiting"),
    },
  ];

  return (
    <Stack useFlexGap spacing={2} sx={{ minWidth: 0 }}>
      <Stack useFlexGap direction="row" spacing={1.5} sx={{ alignItems: "flex-start", flexWrap: "wrap", rowGap: 1 }}>
        <Stack sx={{ flex: "1 1 12rem", minWidth: 0, overflowWrap: "anywhere" }}>
          <Typography variant="body1" sx={{ fontWeight: "fontWeightMedium" }}>
            {t("requests.mine.title", { id: request.id })}
          </Typography>
          <Stack direction="row" sx={{ mt: 0.5 }}>
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
          </Stack>
        </Stack>
        <RequestStatusChipPrototype status={request.status} />
      </Stack>
      <Stepper orientation="vertical" activeStep={rejected ? 1 : reached + 1} sx={{ pl: 0.5 }}>
        {steps.map((step, index) => (
          <Step key={step.title} completed={!rejected && index <= reached} expanded>
            <StepLabel error={rejected && index === 1}>{step.title}</StepLabel>
            <StepContent>
              <Typography variant="body2" color="text.secondary">
                {step.body}
              </Typography>
            </StepContent>
          </Step>
        ))}
      </Stepper>
      {request.status === "fulfilled" && request.produced && (
        <Alert severity="success" icon={false}>
          <Stack useFlexGap direction="row" spacing={1.5} sx={{ alignItems: "center", flexWrap: "wrap" }}>
            <Stack sx={{ minWidth: 0, flex: 1 }}>
              <Typography variant="body2" sx={{ fontWeight: "fontWeightMedium", overflowWrap: "anywhere" }}>
                {request.produced.name}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {t("requests.mine.received", { globalId: sample.globalId })}
              </Typography>
            </Stack>
            <GlobalId record={request.produced} />
          </Stack>
        </Alert>
      )}
    </Stack>
  );
}

type RequesterRequestsSectionArgs = {
  sample: RequestableSample;
  requestable: boolean;
  /** The current user's own request against this sample, if any. */
  myRequest: SampleRequest | null;
  onSubmit: (note: string) => void;
};

/**
 * What a researcher who does not own the sample sees: a request form when the
 * sample is requestable, their request's progress once sent, or a locked
 * notice when the owner has not opened the sample up.
 */
export default function RequesterRequestsSectionPrototype({
  sample,
  requestable,
  myRequest,
  onSubmit,
}: RequesterRequestsSectionArgs): React.ReactNode {
  const { t } = useTranslation("inventory");
  return (
    <StepperPanel
      icon="sample"
      title={myRequest ? t("requests.sectionTitle") : t("requests.request.title")}
      sectionName="requests"
      recordType="sample"
    >
      {myRequest ? (
        <Stack useFlexGap spacing={2}>
          <MyRequestPrototype sample={sample} request={myRequest} />
          {requestable && ["fulfilled", "rejected"].includes(myRequest.status) && (
            <RequestFormPrototype sample={sample} onSubmit={onSubmit} />
          )}
        </Stack>
      ) : requestable ? (
        <RequestFormPrototype sample={sample} onSubmit={onSubmit} />
      ) : (
        <Stack useFlexGap direction="row" spacing={1.5} sx={{ alignItems: "center" }}>
          <LockIcon color="disabled" aria-hidden />
          <Stack sx={{ minWidth: 0 }}>
            <Typography variant="body1" sx={{ fontWeight: "fontWeightMedium" }}>
              {t("requests.blocked.title")}
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ overflowWrap: "anywhere" }}>
              {t("requests.blocked.body")}
            </Typography>
          </Stack>
        </Stack>
      )}
    </StepperPanel>
  );
}
