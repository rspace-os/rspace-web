import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import FormControlLabel from "@mui/material/FormControlLabel";
import Switch from "@mui/material/Switch";
import { useTheme } from "@mui/material/styles";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useContext, useId, useState } from "react";
import { useTranslation } from "react-i18next";
import PadlockIcon from "../../assets/graphics/PadlockIcon";
import { Heading } from "../../components/DynamicHeadingLevel";
import FieldLabel from "../../components/Inputs/FieldLabel";
import { useDeploymentProperty } from "../../hooks/api/useDeploymentProperty";
import { mkAlert } from "../../stores/contexts/Alert";
import NavigateContext from "../../stores/contexts/Navigate";
import type { Person } from "../../stores/definitions/Person";
import SampleModel from "../../stores/models/SampleModel";
import useStores from "../../stores/use-stores";
import { getErrorMessage } from "../../util/error";
import * as FetchingData from "../../util/fetchingData";
import * as Parser from "../../util/parsers";
import { capitaliseJustFirstChar } from "../../util/Util";
import AccessPermissions from "../components/Fields/AccessPermissions";
import AttachmentsField from "../components/Fields/Attachments/Attachments";
import BarcodesField from "../components/Fields/Barcodes/FormField";
import Description from "../components/Fields/Description";
import ExtraFields from "../components/Fields/ExtraFields/ExtraFields";
import IdentifiersField from "../components/Fields/Identifiers/Identifiers";
import ImageField from "../components/Fields/Image";
import NameField from "../components/Fields/Name";
import OwnerField from "../components/Fields/Owner";
import Tags from "../components/Fields/Tags";
import HistoricalVersionAlert from "../components/HistoricalVersionAlert";
import LimitedAccessAlert from "../components/LimitedAccessAlert";
import Stepper from "../components/Stepper/Stepper";
import StepperPanel from "../components/Stepper/StepperPanel";
import { setFormSectionError, useFormSectionError } from "../components/Stepper/StepperPanelHeader";
import { useCancelSampleRequestMutation, useSendSampleRequestMutation } from "../Requests/mutations";
import { useExistingSampleRequestQuery } from "../Requests/queries";
import RequestsStatusChip from "../Requests/RequestsStatusChip";
import SubsampleDetails from "./Content/SubsampleDetails";
import SubsampleListing from "./Content/SubsampleListing";
import Expiry from "./Fields/Expiry";
import Quantity from "./Fields/Quantity";
import Source from "./Fields/Source";
import StorageTemperature from "./Fields/StorageTemperature";
import TemplateField from "./Fields/Template/Template";
import Fields from "./Fields/TemplateFields/Fields";

const OverviewSection = observer(({ activeResult }: { activeResult: SampleModel }) => {
  const { t } = useTranslation("inventory");
  const theme = useTheme();
  const formSectionError = useFormSectionError({
    editing: activeResult.editing,
    globalId: activeResult.globalId,
  });
  const sampleRequestsAvailable = FetchingData.getSuccessValue(
    useDeploymentProperty("inventory.sampleRequests.available"),
  )
    .flatMap(Parser.isString)
    .map((value) => value === "ALLOWED")
    .orElse(false);

  return (
    <StepperPanel
      icon="sample"
      title={t("formSections.overview")}
      sectionName="overview"
      formSectionError={formSectionError}
      recordType="sample"
    >
      {sampleRequestsAvailable && activeResult.currentUserIsOwner && (
        <Box
          sx={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            gap: 2,
            p: 1.5,
            borderRadius: 1,
            border: `1px solid ${theme.palette.divider}`,
            backgroundColor: theme.palette.record.sample.lighter,
          }}
        >
          <Box>
            <Heading sx={{ mt: 0 }}>{t("sample.requestsSection.title")}</Heading>
            <Typography variant="body2">{t("sample.requestsSection.description")}</Typography>
          </Box>
          <FormControlLabel
            sx={{ m: 0 }}
            control={
              <Switch
                checked={activeResult.requestable}
                onChange={({ target: { checked } }) => activeResult.setAttributesDirty({ requestable: checked })}
                color="primary"
                disabled={!activeResult.isFieldEditable("requestable")}
              />
            }
            label={t("sample.requestsSection.switchLabel")}
          />
        </Box>
      )}
      <RequestMaterialSection key={activeResult.globalId} activeResult={activeResult} />
      <NameField
        fieldOwner={activeResult}
        record={activeResult}
        onErrorStateChange={(e) => setFormSectionError(formSectionError, "name", e)}
      />
      <OwnerField fieldOwner={activeResult} />
      {activeResult.readAccessLevel !== "public" && (
        <>
          <TemplateField />
          <ImageField fieldOwner={activeResult} alt={t("sample.imageAlt")} />
        </>
      )}
    </StepperPanel>
  );
});

const DetailsSection = observer(({ activeResult }: { activeResult: SampleModel }) => {
  const { t } = useTranslation("inventory");
  const formSectionError = useFormSectionError({
    editing: activeResult.editing,
    globalId: activeResult.globalId,
  });

  return (
    <StepperPanel
      icon="sample"
      title={t("formSections.details")}
      sectionName="details"
      formSectionError={formSectionError}
      recordType="sample"
    >
      <Quantity
        sample={activeResult}
        onErrorStateChange={(value) => setFormSectionError(formSectionError, "quantity", value)}
      />
      <Expiry
        fieldOwner={activeResult}
        onErrorStateChange={(value) => setFormSectionError(formSectionError, "expiry", value)}
      />
      <Source fieldOwner={activeResult} />
      <StorageTemperature
        fieldOwner={activeResult}
        onErrorStateChange={(value) => setFormSectionError(formSectionError, "temperature", value)}
      />
      <Description
        fieldOwner={activeResult}
        onErrorStateChange={(e) => setFormSectionError(formSectionError, "description", e)}
      />
      <Tags fieldOwner={activeResult} />
    </StepperPanel>
  );
});

function RequestSampleButton({ onClick }: { onClick: () => void }): React.ReactNode {
  const { t } = useTranslation("inventory");
  const theme = useTheme();
  return (
    <Button
      variant="outlined"
      onClick={onClick}
      sx={{
        color: theme.palette.record.sample.lighter,
        backgroundColor: theme.palette.primary.main,
        borderColor: theme.palette.primary.main,
        "&:hover": {
          backgroundColor: theme.palette.primary.dark,
          borderColor: theme.palette.primary.dark,
        },
      }}
    >
      {t("sample.requestMaterialSection.requestSampleButton")}
    </Button>
  );
}

const RequestMaterialSection = observer(({ activeResult }: { activeResult: SampleModel }) => {
  const { t } = useTranslation(["inventory", "common"]);
  const theme = useTheme();
  const requestTextFieldId = useId();
  const [requestText, setRequestText] = useState("");
  const [dialogOpen, setDialogOpen] = useState(false);
  const { useNavigate } = useContext(NavigateContext);
  const navigate = useNavigate();
  const { uiStore } = useStores();
  const sampleRequestsAvailable = FetchingData.getSuccessValue(
    useDeploymentProperty("inventory.sampleRequests.available"),
  )
    .flatMap(Parser.isString)
    .map((value) => value === "ALLOWED")
    .orElse(false);

  // Check whether the current user already has a request against this sample, so that the
  // request button can be replaced with the existing request's status instead. Further UI
  // enhancements around this (richer status display, etc.) will come later.
  //
  // Gated on sampleRequestsAvailable too, not just below in the render: this whole component
  // renders nothing once the property is DENIED (see the early return below), so without this
  // guard the fetch would still fire on every mount for no UI benefit at all.
  const existingRequestQuery = useExistingSampleRequestQuery(
    activeResult.id,
    sampleRequestsAvailable && activeResult.requestable && activeResult.id != null,
  );
  const existingRequest = existingRequestQuery.data ?? null;
  const checkingForExistingRequest = existingRequestQuery.isLoading;
  // Guards sendRequest/cancelRequest below against double-clicks; the two are never both
  // available at once (the Cancel button only shows once a request already exists), so one
  // shared flag is enough.
  const sendRequestMutation = useSendSampleRequestMutation();
  const cancelRequestMutation = useCancelSampleRequestMutation();
  const isProcessingAction = sendRequestMutation.isPending || cancelRequestMutation.isPending;
  const viewRequest = () => {
    if (!existingRequest) return;
    navigate(`/inventory/requests?requestId=${existingRequest.id}`);
  };

  if (!sampleRequestsAvailable || activeResult.currentUserIsOwner) return null;

  const showActionError = (action: string, error: unknown) => {
    console.error(action, error);
    uiStore.addAlert(
      mkAlert({
        variant: "error",
        message: t("errors.genericActionError", { error: getErrorMessage(error, t("errors.unknownReason")) }),
      }),
    );
  };

  // On success the newly-created request's status simply replaces the request button, and the
  // dialog closes.
  const sendRequest = () => {
    if (!activeResult.globalId || isProcessingAction) return;
    sendRequestMutation.mutate(
      { sampleGlobalId: activeResult.globalId, note: requestText },
      {
        onSuccess: () => {
          setDialogOpen(false);
          setRequestText("");
        },
        onError: (error) => showActionError("Failed to send sample request", error),
      },
    );
  };

  // Cancelling is only legal for the requester, and only while the request is PENDING.
  const cancelRequest = () => {
    if (!existingRequest || isProcessingAction) return;
    cancelRequestMutation.mutate(existingRequest.id, {
      onError: (error) => showActionError("Failed to cancel sample request", error),
    });
  };

  return (
    <Box
      sx={{
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: 2,
        p: 1.5,
        borderRadius: 1,
        border: `1px solid ${theme.palette.divider}`,
        backgroundColor: theme.palette.record.sample.lighter,
      }}
    >
      {activeResult.requestable ? (
        <>
          <Box>
            <Heading sx={{ mt: 0 }}>{t("sample.requestMaterialSection.compactTitle")}</Heading>
            <Typography variant="body2">
              {existingRequest?.status === "PENDING"
                ? t("sample.requestMaterialSection.pendingDescription", {
                    owner: activeResult.owner?.fullName ?? "",
                  })
                : t("sample.requestMaterialSection.compactDescription", {
                    owner: activeResult.owner?.fullName ?? "",
                  })}
            </Typography>
          </Box>
          {checkingForExistingRequest ? null : existingRequest && existingRequest.status !== "CANCELLED" ? (
            existingRequest.status === "PENDING" ? (
              <Box sx={{ display: "flex", flexDirection: "column", alignItems: "flex-end", gap: 0.5 }}>
                <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                  <RequestsStatusChip status={existingRequest.status} onClick={viewRequest} bordered />
                  <Button
                    size="small"
                    variant="outlined"
                    disabled={isProcessingAction}
                    onClick={cancelRequest}
                    sx={{
                      // The Inventory accented theme's MuiButton override out-specifies a plain
                      // sx class; "&&" repeats this rule's own selector to match and win.
                      "&&": {
                        backgroundColor: "white",
                        "&:hover": { backgroundColor: "white" },
                      },
                    }}
                  >
                    {t("sample.requestMaterialSection.cancelRequestButton")}
                  </Button>
                </Box>
                <Typography variant="body2">
                  {t("sample.requestMaterialSection.pendingSentText", {
                    date: new Date(existingRequest.created).toLocaleDateString(),
                    owner: activeResult.owner?.fullName ?? "",
                  })}
                </Typography>
              </Box>
            ) : existingRequest.status !== "APPROVED" ? (
              // The button is available whenever the most recent request is in any state
              // other than APPROVED (PENDING is handled separately, above).
              <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                <RequestsStatusChip status={existingRequest.status} onClick={viewRequest} bordered />
                <RequestSampleButton onClick={() => setDialogOpen(true)} />
              </Box>
            ) : (
              <RequestsStatusChip status={existingRequest.status} onClick={viewRequest} bordered />
            )
          ) : (
            <RequestSampleButton onClick={() => setDialogOpen(true)} />
          )}
        </>
      ) : (
        <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
          <PadlockIcon color={theme.palette.action.disabled} />
          <Box>
            <Typography variant="subtitle2" sx={{ fontSize: "1rem" }}>
              {t("sample.requestMaterialSection.notAvailableHeader")}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {t("sample.requestMaterialSection.notAvailableBody")}
            </Typography>
          </Box>
        </Box>
      )}
      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>{t("sample.requestMaterialSection.dialogTitle", { sampleName: activeResult.name })}</DialogTitle>
        <DialogContent>
          <Box sx={{ display: "flex", flexDirection: "column", gap: 2, pt: 1 }}>
            <Typography variant="body2">
              {t("sample.requestMaterialSection.reviewText", { owner: activeResult.owner?.fullName ?? "" })}
            </Typography>
            <Box>
              <FieldLabel htmlFor={requestTextFieldId} sx={{ textTransform: "uppercase" }}>
                {t("sample.requestMaterialSection.whatYouNeedLabel")}
              </FieldLabel>
              <TextField
                id={requestTextFieldId}
                placeholder={t("sample.requestMaterialSection.whatYouNeedHelperText")}
                multiline
                minRows={3}
                fullWidth
                value={requestText}
                onChange={({ target: { value } }) => setRequestText(value)}
              />
            </Box>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)}>{t("common:actions.cancel")}</Button>
          <Button color="primary" variant="contained" disabled={isProcessingAction} onClick={sendRequest}>
            {t("sample.requestMaterialSection.sendRequestButton")}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
});

const MoreFieldsSection = observer(({ activeResult }: { activeResult: SampleModel }) => {
  const { t } = useTranslation("inventory");
  const formSectionError = useFormSectionError({
    editing: activeResult.editing,
    globalId: activeResult.globalId,
  });

  return (
    <StepperPanel
      icon="sample"
      title={t("formSections.customFields")}
      sectionName="customFields"
      formSectionError={formSectionError}
      recordType="sample"
    >
      <Fields
        onErrorStateChange={(field, value) => setFormSectionError(formSectionError, field, value)}
        sample={activeResult}
      />
      <ExtraFields
        onErrorStateChange={(field, value) => setFormSectionError(formSectionError, field, value)}
        result={activeResult}
      />
    </StepperPanel>
  );
});

function Form(): React.ReactNode {
  const { t } = useTranslation("inventory");
  const {
    searchStore: { activeResult },
  } = useStores();
  if (!activeResult || !(activeResult instanceof SampleModel)) throw new Error("ActiveResult must be a Sample");
  if (!activeResult.owner) throw new Error("Sample does not have an owner");
  const owner: Person = activeResult.owner;

  return (
    <Stepper
      stickyAlert={activeResult.historicalVersion ? <HistoricalVersionAlert record={activeResult} /> : null}
      titleText={activeResult.name}
      resetScrollPosition={activeResult}
      factory={activeResult.factory}
    >
      <LimitedAccessAlert
        readAccessLevel={activeResult.readAccessLevel}
        owner={owner}
        whatLabel={t("recordTypes.sample.lower")}
      />
      <OverviewSection activeResult={activeResult} />
      {activeResult.readAccessLevel !== "public" && (
        <>
          <DetailsSection activeResult={activeResult} />
          <StepperPanel icon="sample" title={t("formSections.barcodes")} sectionName="barcodes" recordType="sample">
            <BarcodesField fieldOwner={activeResult} factory={activeResult.factory} connectedItem={activeResult} />
          </StepperPanel>
        </>
      )}
      {activeResult.readAccessLevel === "full" && (
        <>
          <StepperPanel
            icon="sample"
            title={t("formSections.identifiers")}
            sectionName="identifiers"
            recordType="sample"
          >
            <IdentifiersField fieldOwner={activeResult} />
          </StepperPanel>
          <StepperPanel
            icon="sample"
            title={t("formSections.attachments")}
            sectionName="attachments"
            recordType="sample"
          >
            <AttachmentsField fieldOwner={activeResult} />
          </StepperPanel>
          <StepperPanel
            icon="sample"
            title={t("formSections.accessPermissions")}
            sectionName="permissions"
            recordType="sample"
          >
            <AccessPermissions fieldOwner={activeResult} additionalExplanation={t("sample.permissionsExplanation")} />
          </StepperPanel>
          <MoreFieldsSection activeResult={activeResult} />
          {activeResult.state === "preview" ? (
            <StepperPanel
              icon="subsample"
              title={t("sample.subsamplesSection.title", {
                count: activeResult.subSamples.length,
                alias: capitaliseJustFirstChar(
                  activeResult.subSamples.length === 1
                    ? activeResult.subSampleAlias.alias
                    : activeResult.subSampleAlias.plural,
                ),
              })}
              sectionName="subsamples"
              recordType="sample"
            >
              {/*
               * We say "one of the {plural}" here instead of "a {alias}"
               * because adding the logic to get the grammar of "a" versus
               * "an" right would be too much of a pain.
               */}
              <Typography variant="body1">
                {t("sample.subsamplesSection.tapToPreview", { plural: activeResult.subSampleAlias.plural })}
              </Typography>
              <SubsampleListing sample={activeResult} />
              <SubsampleDetails search={activeResult.search} />
            </StepperPanel>
          ) : null}
        </>
      )}
    </Stepper>
  );
}

export default observer(Form);
