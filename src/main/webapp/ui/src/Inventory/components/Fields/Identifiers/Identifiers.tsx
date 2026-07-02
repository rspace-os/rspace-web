import Alert from "@mui/material/Alert";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import Checkbox from "@mui/material/Checkbox";
import Collapse from "@mui/material/Collapse";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import FormControl from "@mui/material/FormControl";
import FormControlLabel from "@mui/material/FormControlLabel";
import Grid from "@mui/material/Grid";
import IconButton from "@mui/material/IconButton";
import Stack from "@mui/material/Stack";
import { ThemeProvider, useTheme } from "@mui/material/styles";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import useMediaQuery from "@mui/material/useMediaQuery";
import { runInAction } from "mobx";
import { observer } from "mobx-react-lite";
import React, { type ComponentType, type ReactNode, useContext, useState } from "react";
import { useTranslation } from "react-i18next";
import axios from "@/common/axios";
import TransRichText from "@/modules/common/i18n/TransRichText";
import createAccentedTheme from "../../../../accentedTheme";
import { ACCENT_COLOR } from "../../../../assets/branding/rspace/inventory";
import docLinks from "../../../../assets/DocLinks";
import CustomTooltip from "../../../../components/CustomTooltip";
import ExpandCollapseIcon from "../../../../components/ExpandCollapseIcon";
import InputWrapper from "../../../../components/Inputs/InputWrapper";
import RadioField from "../../../../components/Inputs/RadioField";
import ValidatingSubmitButton, { IsInvalid, IsValid } from "../../../../components/ValidatingSubmitButton";
import AlertContext, { mkAlert } from "../../../../stores/contexts/Alert";
import AnalyticsContext from "../../../../stores/contexts/Analytics";
import type { HasEditableFields } from "../../../../stores/definitions/Editable";
import type { Identifier, IdentifierField, IGSNPublishingState } from "../../../../stores/definitions/Identifier";
import type { InventoryRecord } from "../../../../stores/definitions/InventoryRecord";
import useStores from "../../../../stores/use-stores";
import RsSet from "../../../../util/set";
import { capitaliseJustFirstChar, match } from "../../../../util/Util";
import IgsnTable from "../../../Identifiers/IGSN/IgsnTable";
import { type Identifier as IdentifierInTable, useIdentifiers } from "../../../useIdentifiers";
import MultipleInputHandler from "./MultipleInputHandler";
import PublicPreviewDialog from "./PublicPreviewDialog";
import PublishButton from "./PublishButton";

const IdentifierWrapper = observer(
  ({ activeResult, id, editable }: { activeResult: InventoryRecord; id: Identifier; editable: boolean }): ReactNode => {
    const { t } = useTranslation(["inventory", "common"]);
    const isRadio = (field: IdentifierField): boolean => Boolean(field.radioOptions);

    /* different name to avoid confusion with 'editable' (parent) */
    const fixedValue = (field: IdentifierField) => Boolean(!field.handler);

    const [openRecommendedSection, setOpenRecommendedSection] = useState(true);

    const handleUpdate = (f: IdentifierField, value: string | number) => {
      if (f.handler) f.handler(value);
      /* setAttributesDirty on item */
      activeResult.updateIdentifiers();
    };

    const isFieldInvalid = (field: IdentifierField): boolean => {
      if (field.value === "") return true;
      return field.isValid ? !field.isValid(field.value) : false;
    };

    const isCustomField = (field: unknown): field is { id: string | number; name: string } =>
      typeof field === "object" &&
      field !== null &&
      "id" in field &&
      (typeof field.id === "string" || typeof field.id === "number") &&
      "name" in field &&
      typeof field.name === "string";

    const rawCustomFields: Array<unknown> =
      "fields" in activeResult && Array.isArray(activeResult.fields) ? activeResult.fields : [];
    const customFields = rawCustomFields.filter(isCustomField);

    return (
      <>
        <section>
          <Typography variant="h6" component="h4">
            {t("fields.identifiers.wrapper.required.title")}
          </Typography>
          {id.requiredFields.map((f) => (
            <Grid
              key={f.key}
              sx={{
                width: "100%",
                marginBottom: "8px",
                borderBottom: editable ? "0px" : "1px dotted grey",
              }}
            >
              <FormControl component="fieldset" fullWidth>
                <InputWrapper label={f.key}>
                  {editable && isRadio(f) ? (
                    <RadioField
                      name={`field-${f.key}`}
                      value={f.value as string}
                      // biome-ignore lint/style/noNonNullAssertion: initial biome migration
                      options={f.radioOptions!}
                      onChange={({ target: { value } }) => {
                        if (value) handleUpdate(f, value);
                      }}
                    />
                  ) : (
                    <TextField
                      size="small"
                      variant="standard"
                      fullWidth
                      id={`IdentifierField-${f.key}`}
                      disabled={!editable || fixedValue(f)}
                      value={f.value ?? ""}
                      placeholder={
                        editable
                          ? t("fields.identifiers.wrapper.enterValue", { key: f.key })
                          : t("fields.identifiers.wrapper.none")
                      }
                      onChange={({ target: { value } }) => handleUpdate(f, value)}
                      error={editable && isFieldInvalid(f)}
                      helperText={editable && isFieldInvalid(f) ? t("fields.identifiers.wrapper.fieldInvalid") : null}
                      slotProps={{
                        inputLabel: { shrink: true },
                      }}
                    />
                  )}
                </InputWrapper>
              </FormControl>
            </Grid>
          ))}
        </section>
        <section>
          <Grid
            container
            direction="row"
            spacing={1}
            sx={{
              justifyContent: "space-between",
              width: "100%",
              mb: 1,
              fontWeight: "bold",
            }}
          >
            <Grid>
              <Typography variant="h6" component="h4">
                {t("fields.identifiers.wrapper.recommended.title")}
              </Typography>
            </Grid>
            <Grid>
              <CustomTooltip
                title={match<void, string>([
                  [() => openRecommendedSection, t("fields.identifiers.wrapper.recommended.hide")],
                  [() => true, t("fields.identifiers.wrapper.recommended.show")],
                ])()}
              >
                <IconButton
                  onClick={() => setOpenRecommendedSection(!openRecommendedSection)}
                  disabled={false}
                  aria-label={t("fields.identifiers.wrapper.recommended.label")}
                >
                  <ExpandCollapseIcon open={openRecommendedSection} />
                </IconButton>
              </CustomTooltip>
            </Grid>
          </Grid>
          <Collapse in={openRecommendedSection}>
            {id.recommendedFields.map((f) => (
              <Grid
                key={f.key}
                sx={{
                  width: "100%",
                  mb: 1,
                  borderBottom: editable ? "0px" : "1px dotted grey",
                }}
              >
                <FormControl component="fieldset" fullWidth>
                  <MultipleInputHandler field={f} activeResult={activeResult} editable={editable} />
                </FormControl>
              </Grid>
            ))}
          </Collapse>
        </section>
        <section>
          <Typography variant="h6" component="h4" sx={{ mb: 1 }}>
            {t("fields.identifiers.wrapper.inventoryFields.title")}
          </Typography>
          <Alert severity="info">
            <TransRichText i18nKey="inventory:fields.identifiers.wrapper.inventoryFields.alert" />
          </Alert>
          <FormControlLabel
            control={
              <Checkbox
                disabled={!editable}
                color="primary"
                name="include-inventory-fields"
                value={id.customFieldsOnPublicPage ? "yes" : "no"}
                checked={id.customFieldsOnPublicPage}
                onChange={({ target: { checked } }) => {
                  runInAction(() => {
                    id.customFieldsOnPublicPage = checked;
                  });
                  activeResult.updateIdentifiers();
                }}
              />
            }
            label={t("fields.identifiers.wrapper.inventoryFields.includeOnPage")}
          />
          {editable && (
            <Typography variant="body2" component="div">
              {t("fields.identifiers.wrapper.inventoryFields.followingFields")}
              <ul>
                <li>{t("fields.identifiers.wrapper.inventoryFields.description")}</li>
                <li>{t("fields.identifiers.wrapper.inventoryFields.tags")}</li>
                <li>
                  {t("fields.identifiers.wrapper.inventoryFields.customFields")}
                  <ul>
                    {customFields.map((f) => (
                      <li key={f.id}>{f.name}</li>
                    ))}
                  </ul>
                </li>
                <li>
                  {t("fields.identifiers.wrapper.inventoryFields.extraFields")}
                  <ul>
                    {activeResult.extraFields.map((f) => (
                      <li key={f.id}>{f.name}</li>
                    ))}
                  </ul>
                </li>
              </ul>
            </Typography>
          )}
        </section>
      </>
    );
  },
);

type IdentifiersListArgs = { activeResult: InventoryRecord };

export const IdentifiersList: ComponentType<IdentifiersListArgs> = observer(({ activeResult }: IdentifiersListArgs) => {
  const theme = useTheme();
  const editable = activeResult.isFieldEditable("identifiers");
  const { addAlert } = useContext(AlertContext);
  const { uiStore } = useStores();
  const { t } = useTranslation(["inventory", "common"]);

  const StateInfo = ({
    identifierState,
    identifierUrl,
  }: {
    identifierState: IGSNPublishingState;
    identifierUrl: string | null | undefined;
  }): ReactNode => {
    if (identifierState === "draft") return <>{t("fields.identifiers.list.stateInfo.draft")}</>;
    if (identifierState === "findable")
      return (
        <TransRichText
          i18nKey="inventory:fields.identifiers.list.stateInfo.findable"
          values={{ link: identifierUrl || "" }}
        />
      );
    if (identifierState === "registered")
      return (
        <TransRichText
          i18nKey="inventory:fields.identifiers.list.stateInfo.registered"
          values={{ link: identifierUrl || "" }}
        />
      );
    throw new Error("Invalid state");
  };

  const [selectedIdentifier, setSelectedIdentifier] = useState<Identifier | undefined>();
  const [openPreviewDialog, setOpenPreviewDialog] = useState(false);

  /*
   *Published IGSNs will have any RoR data set by back end code.
   *IGSNs in other states have no RoR data set by back end code.
   */
  const fetchAndSetRoRData = async (id: Identifier): Promise<void> => {
    if (id.state !== "findable") {
      try {
        const idResponse: { data: string } = await axios.get("/global/ror/existingGlobalRoRID");
        const nameResponse: { data: string } = await axios.get("/global/ror/existingGlobalRoRName");
        id.creatorAffiliationIdentifier = idResponse.data;
        id.creatorAffiliation = nameResponse.data;
      } catch (e) {
        console.error(e);
        addAlert(
          mkAlert({
            variant: "error",
            message: t("fields.identifiers.list.rorError"),
          }),
        );
      }
    } else {
      return Promise.resolve();
    }
  };

  const handlePreview = async (id: Identifier) => {
    await fetchAndSetRoRData(id);
    setSelectedIdentifier(id);
    setOpenPreviewDialog(true);
  };

  const handleRetract = (id: Identifier) => {
    void id.retract({
      confirm: (...args) => uiStore.confirm(...args),
      addAlert: (...args) => addAlert(...args),
    });
  };

  const handleDelete = (id: Identifier) => {
    void activeResult.removeIdentifier(id.id);
  };

  const [openIdForm, setOpenIdForm] = useState(true);
  return (
    <Grid sx={{ mt: 1, padding: "0px 12px" }}>
      <Grid
        container
        sx={{
          alignItems: "center",
          flexDirection: "column",
          width: "100%",
          fontSize: "14px",
        }}
      >
        {activeResult.state === "preview" && activeResult.identifiers.length > 0 && (
          <Alert severity="info" sx={{ width: "100%", mb: 1 }}>
            {t("fields.identifiers.list.editFirst")}
          </Alert>
        )}
        {activeResult.identifiers.map((id) => (
          <Grid key={id.doi} sx={{ width: "100%" }}>
            <Grid container direction="row" spacing={1} sx={{ width: "100%", marginBottom: "8px", fontWeight: "bold" }}>
              <Grid size={6}>{t("fields.identifiers.list.headers.identifier")}</Grid>
              <Grid size={2}>{t("fields.identifiers.list.headers.type")}</Grid>
              <Grid size={2}>{t("fields.identifiers.list.headers.state")}</Grid>
              <Grid size={2}>{openIdForm ? t("fields.identifiers.list.hide") : t("fields.identifiers.list.show")}</Grid>
            </Grid>
            <Grid container direction="row" spacing={1} sx={{ width: "100%", marginBottom: "8px" }}>
              <Grid sx={{ padding: "6px" }} size={6}>
                {id.state === "draft" ? (
                  id.doi
                ) : (
                  <a href={id.publicUrl || ""} target="_blank" rel="noreferrer">
                    {id.publicUrl}
                  </a>
                )}
              </Grid>
              <Grid size={2}>{id.doiTypeLabel}</Grid>
              <Grid
                sx={id.state === "findable" ? { color: theme.palette.modifiedHighlight } : undefined}
                data-testid="identifier-state"
                size={2}
              >
                {capitaliseJustFirstChar(id.state)}
              </Grid>
              <Grid size={2}>
                <CustomTooltip
                  title={match<void, string>([
                    [() => openIdForm, t("fields.identifiers.list.toggleId.hide")],
                    [() => true, t("fields.identifiers.list.toggleId.show")],
                  ])()}
                >
                  <IconButton
                    onClick={() => setOpenIdForm(!openIdForm)}
                    disabled={false}
                    aria-label={t("fields.identifiers.list.toggleId.label")}
                  >
                    <ExpandCollapseIcon open={openIdForm} />
                  </IconButton>
                </CustomTooltip>
              </Grid>
            </Grid>
            <Grid
              container
              direction="row"
              spacing={2}
              sx={{
                justifyContent: "flex-start",
                width: "100%",
                marginBottom: "12px",
              }}
            >
              <Grid>
                <CustomTooltip
                  title={
                    id.isValid
                      ? t("fields.identifiers.list.tooltips.previewPage")
                      : t("fields.identifiers.list.tooltips.missingData")
                  }
                >
                  <Button
                    color="callToAction"
                    variant="outlined"
                    size="small"
                    onClick={() => void handlePreview(id)}
                    disabled={
                      activeResult.state === "edit" ||
                      !id.isValid ||
                      // the preview dialog contains a publish action
                      Boolean(activeResult.historicalVersion)
                    }
                  >
                    {t("fields.identifiers.list.preview")}
                  </Button>
                </CustomTooltip>
              </Grid>
              <Grid>
                <PublishButton
                  identifier={id}
                  disabled={activeResult.state === "edit" || Boolean(activeResult.historicalVersion)}
                />
              </Grid>
              <Grid>
                <CustomTooltip
                  title={
                    id.state === "draft"
                      ? t("fields.identifiers.list.tooltips.deleteDraft")
                      : id.state === "findable"
                        ? t("fields.identifiers.list.tooltips.retract")
                        : t("fields.identifiers.list.tooltips.notPublished")
                  }
                >
                  <Button
                    color="secondary"
                    variant="outlined"
                    size="small"
                    onClick={id.state === "draft" ? () => handleDelete(id) : () => handleRetract(id)}
                    disabled={
                      activeResult.state === "edit" ||
                      id.state === "registered" ||
                      Boolean(activeResult.historicalVersion)
                    }
                  >
                    {id.state === "draft"
                      ? t("fields.identifiers.list.deleteOrRetract.delete")
                      : t("fields.identifiers.list.deleteOrRetract.retract")}
                  </Button>
                </CustomTooltip>
              </Grid>
              {!id.isValid && (
                <Grid sx={{ mb: 1 }}>
                  <Alert severity="warning">{t("fields.identifiers.missingDetails")}</Alert>
                </Grid>
              )}
            </Grid>
            <Alert severity="info" sx={{ width: "100%", mb: 1 }}>
              <StateInfo identifierState={id.state} identifierUrl={id.url} />{" "}
              <a href={docLinks.IGSNIdentifiers} target="_blank" rel="noreferrer">
                {t("fields.identifiers.list.igsnDocLink")}
              </a>
            </Alert>
            <Collapse in={openIdForm}>
              <IdentifierWrapper activeResult={activeResult} id={id} editable={editable} />
            </Collapse>
          </Grid>
        ))}
      </Grid>
      {selectedIdentifier && (
        <PublicPreviewDialog
          record={activeResult}
          open={openPreviewDialog}
          onClose={() => setOpenPreviewDialog(false)}
          id={selectedIdentifier}
        />
      )}
    </Grid>
  );
});

const AssignDialog = observer(
  ({
    open,
    onClose,
    recordToAssignTo,
  }: {
    open: boolean;
    onClose: () => void;
    recordToAssignTo: InventoryRecord;
  }): ReactNode => {
    const { assignIdentifier } = useIdentifiers();
    const [selectedIgsns, setSelectedIgsns] = React.useState<RsSet<IdentifierInTable>>(new RsSet([]));
    const theme = useTheme();
    const fullScreen = useMediaQuery(theme.breakpoints.down("md"));
    const { trackEvent } = useContext(AnalyticsContext);
    const { t } = useTranslation(["inventory", "common"]);

    return (
      <ThemeProvider theme={createAccentedTheme(ACCENT_COLOR)}>
        <Dialog
          open={open}
          onClose={() => {
            setSelectedIgsns(new RsSet([]));
            onClose();
          }}
          fullWidth
          maxWidth="lg"
          fullScreen={fullScreen}
        >
          <DialogTitle>{t("fields.identifiers.assignDialog.title")}</DialogTitle>
          <DialogContent>
            <Stack spacing={2}>
              <Typography>{t("fields.identifiers.assignDialog.selectExisting")}</Typography>
              <IgsnTable
                selectedIgsns={selectedIgsns}
                setSelectedIgsns={setSelectedIgsns}
                disableMultipleRowSelection
                controlDefaults={{
                  isAssociated: false,
                  state: "draft",
                }}
              />
              <Alert severity="warning">
                <TransRichText i18nKey="inventory:fields.identifiers.assignDialog.undoWarningFormatted" />
              </Alert>
            </Stack>
          </DialogContent>
          <DialogActions>
            <Button
              onClick={() => {
                setSelectedIgsns(new RsSet([]));
                onClose();
              }}
              color="primary"
            >
              {t("common:actions.cancel")}
            </Button>
            <ValidatingSubmitButton
              loading={false}
              validationResult={selectedIgsns.only
                .map((igsn) =>
                  igsn.associatedGlobalId !== null
                    ? IsInvalid(t("fields.identifiers.assignDialog.alreadyAssigned"))
                    : IsValid(),
                )
                .orElse(IsInvalid(t("fields.identifiers.assignDialog.noIgsnSelected")))}
              onClick={() => {
                void (async () => {
                  await selectedIgsns.only
                    .toResult(() => new Error("Invalid state: zero or many identifiers are selected"))
                    .doAsync((igsn) => assignIdentifier(igsn, recordToAssignTo));
                  await recordToAssignTo.fetchAdditionalInfo();
                  setSelectedIgsns(new RsSet([]));
                  onClose();
                  trackEvent("user:assign-existing-igsn");
                })();
              }}
            >
              {t("fields.identifiers.assignDialog.link")}
            </ValidatingSubmitButton>
          </DialogActions>
        </Dialog>
      </ThemeProvider>
    );
  },
);

const IdentifiersCard = observer((): ReactNode => {
  const {
    searchStore: { activeResult },
  } = useStores();
  if (!activeResult) throw new Error("ActiveResult must be a Record");
  const identifiers = activeResult.identifiers ?? [];
  const [assignDialogOpen, setAssignDialogOpen] = useState(false);
  const { trackEvent } = useContext(AnalyticsContext);
  const { t } = useTranslation(["inventory", "common"]);
  const isInstrument = activeResult.recordType === "instrument" || activeResult.recordType === "instrumentTemplate";
  const identifierLabel = isInstrument ? t("fields.identifiers.card.pidinst") : t("fields.identifiers.card.igsnId");

  return (
    <>
      {activeResult.state === "create" && <Alert severity="info">{t("fields.identifiers.card.notCreatedYet")}</Alert>}
      {activeResult.state !== "create" && identifiers.length === 0 && !activeResult.historicalVersion && (
        <Stack direction="row" spacing={1}>
          <Button
            color="primary"
            variant="outlined"
            disabled={isInstrument}
            onClick={() => void activeResult.addIdentifier()}
          >
            {t("fields.identifiers.card.createNew", { identifierLabel })}
          </Button>
          <Button
            color="primary"
            variant="outlined"
            disabled={isInstrument}
            onClick={() => {
              setAssignDialogOpen(true);
              trackEvent("user:open:assign-existing-igsn-dialog");
            }}
          >
            {t("fields.identifiers.card.linkExisting", { identifierLabel })}
          </Button>
          <AssignDialog
            recordToAssignTo={activeResult}
            open={assignDialogOpen}
            onClose={() => setAssignDialogOpen(false)}
          />
        </Stack>
      )}
      {identifiers.length > 0 && (
        <Card variant="outlined">
          <IdentifiersList activeResult={activeResult} />
        </Card>
      )}
    </>
  );
});

function Identifiers<
  Fields extends {
    identifiers: Array<Identifier>;
  },
  FieldOwner extends HasEditableFields<Fields>,
>({ fieldOwner }: { fieldOwner: FieldOwner }): ReactNode {
  return (
    <InputWrapper
      label=""
      error={false}
      explanation={
        fieldOwner.isFieldEditable("identifiers") ? (
          <TransRichText
            i18nKey="inventory:fields.identifiers.formField.explanation"
            values={{ link: docLinks.IGSNIdentifiers }}
          />
        ) : null
      }
    >
      <IdentifiersCard />
    </InputWrapper>
  );
}

export default observer(Identifiers);
