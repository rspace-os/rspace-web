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
import { useEffect, useId, useState } from "react";
import { useTranslation } from "react-i18next";
import PadlockIcon from "../../assets/graphics/PadlockIcon";
import ApiService from "../../common/InvApiService";
import { Heading } from "../../components/DynamicHeadingLevel";
import FieldLabel from "../../components/Inputs/FieldLabel";
import { useDeploymentProperty } from "../../hooks/api/useDeploymentProperty";
import type { Person } from "../../stores/definitions/Person";
import SampleModel from "../../stores/models/SampleModel";
import useStores from "../../stores/use-stores";
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
  const sampleRequestsAvailable = FetchingData.getSuccessValue(useDeploymentProperty("sampleRequests.available"))
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
                slotProps={{ input: { role: "checkbox" } }}
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

const RequestMaterialSection = observer(({ activeResult }: { activeResult: SampleModel }) => {
  const { t } = useTranslation(["inventory", "common"]);
  const theme = useTheme();
  const requestTextFieldId = useId();
  const [requestText, setRequestText] = useState("");
  const [dialogOpen, setDialogOpen] = useState(false);
  const [checkingForExistingRequest, setCheckingForExistingRequest] = useState(true);
  const [existingRequest, setExistingRequest] = useState<{ status: string; created: string } | null>(null);
  const sampleRequestsAvailable = FetchingData.getSuccessValue(useDeploymentProperty("sampleRequests.available"))
    .flatMap(Parser.isString)
    .map((value) => value === "ALLOWED")
    .orElse(false);

  // Check whether the current user already has a request against this sample, so that the
  // request button can be replaced with the existing request's status instead. Further UI
  // enhancements around this (richer status display, etc.) will come later.
  useEffect(() => {
    if (!activeResult.requestable || activeResult.id == null) {
      setCheckingForExistingRequest(false);
      setExistingRequest(null);
      return;
    }
    let cancelled = false;
    setCheckingForExistingRequest(true);
    ApiService.query<{ requests: Array<{ status: string; created: string }> }>(
      "sampleRequests",
      new URLSearchParams({ sampleId: String(activeResult.id) }),
    )
      .then(({ data }) => {
        if (cancelled) return;
        const mostRecent = data.requests.reduce<{ status: string; created: string } | null>(
          (latest, request) =>
            !latest || new Date(request.created).getTime() > new Date(latest.created).getTime() ? request : latest,
          null,
        );
        setExistingRequest(mostRecent);
      })
      .catch((error: unknown) => {
        if (cancelled) return;
        console.error("Failed to check for an existing sample request", error);
        setExistingRequest(null);
      })
      .finally(() => {
        if (!cancelled) setCheckingForExistingRequest(false);
      });
    return () => {
      cancelled = true;
    };
  }, [activeResult.id, activeResult.requestable]);

  if (!sampleRequestsAvailable || activeResult.currentUserIsOwner) return null;

  // Richer UI responses to the outcome (error state, etc.) are handled separately; for now,
  // on success the newly-created request's status simply replaces the request button, and the
  // dialog closes.
  const sendRequest = () => {
    if (!activeResult.globalId) return;
    void ApiService.post<{ status: string; created: string }>("sampleRequests", {
      sampleGlobalId: activeResult.globalId,
      note: requestText,
    })
      .then(({ data }) => {
        setExistingRequest(data);
        setDialogOpen(false);
      })
      .catch((error: unknown) => {
        console.error("Failed to send sample request", error);
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
          {checkingForExistingRequest ? null : existingRequest ? (
            existingRequest.status === "PENDING" ? (
              <Box sx={{ display: "flex", flexDirection: "column", alignItems: "flex-end", gap: 0.5 }}>
                <Box
                  sx={{
                    display: "inline-flex",
                    alignItems: "center",
                    gap: 0.75,
                    px: 2,
                    py: 0.75,
                    borderRadius: 1,
                    backgroundColor: "rgb(251, 241, 222)",
                    color: "rgb(183, 121, 31)",
                    fontSize: "0.8125rem",
                    fontWeight: 500,
                  }}
                >
                  <Box
                    component="span"
                    sx={{
                      width: 8,
                      height: 8,
                      borderRadius: "50%",
                      backgroundColor: "currentColor",
                    }}
                  />
                  {t("sample.requestMaterialSection.pendingChipLabel")}
                </Box>
                <Typography variant="body2">
                  {t("sample.requestMaterialSection.pendingSentText", {
                    date: new Date(existingRequest.created).toLocaleDateString(),
                    owner: activeResult.owner?.fullName ?? "",
                  })}
                </Typography>
              </Box>
            ) : (
              <Typography variant="body2">{existingRequest.status}</Typography>
            )
          ) : (
            <Button
              variant="outlined"
              onClick={() => setDialogOpen(true)}
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
          <Button color="primary" variant="contained" onClick={sendRequest}>
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
