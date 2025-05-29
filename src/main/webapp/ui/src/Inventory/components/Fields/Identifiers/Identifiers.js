//@flow

import React, {
  useState,
  type Node,
  type ComponentType,
  useContext,
} from "react";
import { observer } from "mobx-react-lite";
import { runInAction } from "mobx";
import useStores from "../../../../stores/use-stores";
import {
  match,
  capitaliseJustFirstChar,
  doNotAwait,
} from "../../../../util/Util";
import docLinks from "../../../../assets/DocLinks";
import InputWrapper from "../../../../components/Inputs/InputWrapper";
import CustomTooltip from "../../../../components/CustomTooltip";
import ExpandCollapseIcon from "../../../../components/ExpandCollapseIcon";
import Alert from "@mui/material/Alert";
import Button from "@mui/material/Button";
import Card from "@mui/material/Card";
import Collapse from "@mui/material/Collapse";
import IconButton from "@mui/material/IconButton";
import Grid from "@mui/material/Grid";
import { type InventoryRecord } from "../../../../stores/definitions/InventoryRecord";
import type { HasEditableFields } from "../../../../stores/definitions/Editable";
import {
  type Identifier,
  type IdentifierField,
  type IGSNPublishingState,
  type CreatorType,
} from "../../../../stores/definitions/Identifier";
import FormControl from "@mui/material/FormControl";
import Typography from "@mui/material/Typography";
import TextField from "@mui/material/TextField";
import RadioField from "../../../../components/Inputs/RadioField";
import PublicPreviewDialog, { MissingDataAlert } from "./PublicPreviewDialog";
import { makeStyles } from "tss-react/mui";
import MultipleInputHandler from "./MultipleInputHandler";
import axios from "@/common/axios";
import AlertContext, { mkAlert } from "../../../../stores/contexts/Alert";
import PublishButton from "./PublishButton";
import FormControlLabel from "@mui/material/FormControlLabel";
import Checkbox from "@mui/material/Checkbox";
import Stack from "@mui/material/Stack";
import Dialog from "@mui/material/Dialog";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import DialogActions from "@mui/material/DialogActions";
import { ThemeProvider } from "@mui/material/styles";
import createAccentedTheme from "../../../../accentedTheme";
import { ACCENT_COLOR } from "../../../../assets/branding/rspace/inventory";
import ValidatingSubmitButton, {
  IsValid,
  IsInvalid,
} from "../../../../components/ValidatingSubmitButton";
import IgsnTable from "../../../Identifiers/IGSN/IgsnTable";
import RsSet from "../../../../util/set";
import {
  type Identifier as IdentifierInTable,
  useIdentifiers,
} from "../../../useIdentifiers";

const useStyles = makeStyles()((theme) => ({
  primary: {
    color: theme.palette.primary.main,
  },
  published: {
    border: "2px solid green",
  },
  bottomSpaced: { marginBottom: theme.spacing(1) },
  highlight: { color: theme.palette.modifiedHighlight },
}));

const IdentifierWrapper = observer(
  ({
    activeResult,
    id,
    editable,
  }: {
    activeResult: InventoryRecord,
    id: Identifier,
    editable: boolean,
  }): Node => {
    const isRadio = (field: IdentifierField): boolean =>
      Boolean(field.radioOptions);

    /* different name to avoid confusion with 'editable' (parent) */
    const fixedValue = (field: IdentifierField) => Boolean(!field.handler);

    const [openRecommendedSection, setOpenRecommendedSection] = useState(true);

    const handleUpdate = (
      f: IdentifierField,
      value: string | number | CreatorType
    ) => {
      if (f.handler) f.handler(value);
      /* setAttributesDirty on item */
      activeResult.updateIdentifiers();
    };

    return (
      <>
        <section>
          <Typography variant="h6" component="h4">
            Required Identifier Properties
          </Typography>
          {id.requiredFields.map((f) => (
            <Grid
              item
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
                      // $FlowFixMe[incompatible-type]
                      value={f.value}
                      // $FlowExpectedError[incompatible-type] as isRadio(f), we know options are defined
                      options={f.radioOptions}
                      onChange={({ target: { value } }) => {
                        if (value) handleUpdate(f, value);
                      }}
                    />
                  ) : (
                    <>
                      <TextField
                        InputLabelProps={{ shrink: true }}
                        size="small"
                        variant="standard"
                        fullWidth
                        id={`IdentifierField-${f.key}`}
                        disabled={!editable || fixedValue(f)}
                        value={f.value ?? ""}
                        placeholder={
                          editable ? `Enter value for ${f.key}` : "None"
                        }
                        onChange={({ target: { value } }) =>
                          handleUpdate(f, value)
                        }
                        error={
                          editable &&
                          (f.value === "" ||
                            !(f.isValid ?? ((_) => true))(f.value))
                        }
                        helperText={
                          editable &&
                          (f.value === "" ||
                            !(f.isValid ?? ((_) => true))(f.value))
                            ? "In order to publish the identifier, a valid value is required."
                            : null
                        }
                      />
                    </>
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
            justifyContent={"space-between"}
            spacing={1}
            sx={{ width: "100%", mb: 1, fontWeight: "bold" }}
          >
            <Grid item>
              <Typography variant="h6" component="h4">
                Recommended Identifier Properties
              </Typography>
            </Grid>
            <Grid item>
              <CustomTooltip
                title={match<void, string>([
                  [
                    () => openRecommendedSection,
                    "Hide recommended fields section",
                  ],
                  [() => true, "Show recommended fields section"],
                ])()}
              >
                <IconButton
                  onClick={() =>
                    setOpenRecommendedSection(!openRecommendedSection)
                  }
                  disabled={false}
                  aria-label="Toggle recommended fields section"
                >
                  <ExpandCollapseIcon open={openRecommendedSection} />
                </IconButton>
              </CustomTooltip>
            </Grid>
          </Grid>
          <Collapse in={openRecommendedSection}>
            {id.recommendedFields.map((f) => (
              <Grid
                item
                key={f.key}
                sx={{
                  width: "100%",
                  mb: 1,
                  borderBottom: editable ? "0px" : "1px dotted grey",
                }}
              >
                <FormControl component="fieldset" fullWidth>
                  <MultipleInputHandler
                    field={f}
                    activeResult={activeResult}
                    editable={editable}
                  />
                </FormControl>
              </Grid>
            ))}
          </Collapse>
        </section>
        <section>
          <Typography variant="h6" component="h4" sx={{ mb: 1 }}>
            Inventory Fields
          </Typography>
          <Alert severity="info">
            You can include Inventory fields in the item’s landing page, to
            openly share domain-specific metadata outside the IGSN schema.{" "}
            <strong>
              Before publishing the IGSN ID, please ensure the fields do not
              contain sensitive information.
            </strong>
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
            label="Include Inventory fields on landing page"
          />
          {editable && (
            <Typography variant="body2">
              The following fields will be included:
              <ul>
                <li>Description</li>
                <li>Tags</li>
                <li>
                  Custom Fields
                  <ul>
                    {/* $FlowExpectedError[prop-missing] for samples */}
                    {/* $FlowExpectedError[unnecessary-optional-chain] for samples */}
                    {activeResult.fields?.map((f) => (
                      <li key={f.id}>{f.name}</li>
                    ))}
                  </ul>
                </li>
                <li>
                  Extra Fields
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
  }
);

type IdentifiersListArgs = {| activeResult: InventoryRecord |};

export const IdentifiersList: ComponentType<IdentifiersListArgs> = observer(
  ({ activeResult }: IdentifiersListArgs) => {
    const { classes } = useStyles();
    const editable = activeResult.isFieldEditable("identifiers");
    const { addAlert } = useContext(AlertContext);
    const { uiStore } = useStores();

    const StateInfo = ({
      identifierState,
      identifierUrl,
    }: {
      identifierState: IGSNPublishingState,
      identifierUrl: ?string,
    }): Node => {
      if (identifierState === "draft")
        return (
          <>
            This IGSN ID is a Draft. Metadata can be specified, but no
            information is publicly available.
          </>
        );
      if (identifierState === "findable")
        return (
          <>
            This IGSN ID is Findable. The IGSN ID is a citable URL that
            redirects to the{" "}
            <a href={identifierUrl} target="_blank" rel="noreferrer">
              RSpace landing page
            </a>
            . The metadata is publicly available through the landing page,
            DataCite Commons and the DataCite APIs.
          </>
        );
      if (identifierState === "registered")
        return (
          <>
            This IGSN ID is Registered. The metadata is not publicly available
            through the{" "}
            <a href={identifierUrl} target="_blank" rel="noreferrer">
              RSpace landing page
            </a>
            , DataCite Commons or the Public API, but is available through the
            Members API.
          </>
        );
      throw new Error("Invalid state");
    };

    const [selectedIdentifier, setSelectedIdentifier] = useState<?Identifier>();
    const [openPreviewDialog, setOpenPreviewDialog] = useState(false);

    /*
     *Published IGSNs will have any RoR data set by back end code.
     *IGSNs in other states have no RoR data set by back end code.
     */
    const fetchAndSetRoRData = async (id: Identifier): Promise<> => {
      if (id.state !== "findable") {
        try {
          const idResponse: { data: string } = await axios.get(
            "/global/ror/existingGlobalRoRID"
          );
          const nameResponse: { data: string } = await axios.get(
            "/global/ror/existingGlobalRoRName"
          );
          id.creatorAffiliationIdentifier = idResponse.data;
          id.creatorAffiliation = nameResponse.data;
        } catch (e) {
          console.error(e);
          addAlert(
            mkAlert({
              variant: "error",
              message: "Could not get RoR data.",
            })
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
      <Grid mt={1} sx={{ padding: "0px 12px" }}>
        <Grid
          container
          direction="column"
          alignItems="center"
          sx={{
            width: "100%",
            fontSize: "14px",
          }}
        >
          {activeResult.state === "preview" &&
            activeResult.identifiers.length > 0 && (
              <Alert
                severity="info"
                className={classes.bottomSpaced}
                sx={{ width: "100%" }}
              >
                To update any details, press Edit first.
              </Alert>
            )}
          {activeResult.identifiers.map((id) => (
            <Grid item key={id.doi} sx={{ width: "100%" }}>
              <Grid
                container
                direction="row"
                spacing={1}
                sx={{ width: "100%", marginBottom: "8px", fontWeight: "bold" }}
              >
                <Grid item xs={6}>
                  Identifier
                </Grid>
                <Grid item xs={2}>
                  Type
                </Grid>
                <Grid item xs={2}>
                  State
                </Grid>
                <Grid item xs={2}>
                  {openIdForm ? "Hide" : "Show"}
                </Grid>
              </Grid>
              <Grid
                container
                direction="row"
                spacing={1}
                sx={{ width: "100%", marginBottom: "8px" }}
              >
                <Grid item xs={6} sx={{ padding: "6px" }}>
                  {id.state === "draft" ? (
                    id.doi
                  ) : (
                    <a href={id.publicUrl} target="_blank" rel="noreferrer">
                      {id.publicUrl}
                    </a>
                  )}
                </Grid>
                <Grid item xs={2}>
                  {id.doiTypeLabel}
                </Grid>
                <Grid
                  item
                  xs={2}
                  className={id.state === "findable" ? classes.highlight : null}
                  data-testid="identifier-state"
                >
                  {capitaliseJustFirstChar(id.state)}
                </Grid>
                <Grid item xs={2}>
                  <CustomTooltip
                    title={match<void, string>([
                      [() => openIdForm, "Hide identifier's details"],
                      [() => true, "Show identifier's details"],
                    ])()}
                  >
                    <IconButton
                      onClick={() => setOpenIdForm(!openIdForm)}
                      disabled={false}
                      aria-label="Toggle identifier details"
                    >
                      <ExpandCollapseIcon open={openIdForm} />
                    </IconButton>
                  </CustomTooltip>
                </Grid>
              </Grid>
              <Grid
                container
                direction="row"
                justifyContent="flex-start"
                spacing={2}
                sx={{ width: "100%", marginBottom: "12px" }}
              >
                <Grid item>
                  <CustomTooltip
                    title={
                      id.isValid ? "Preview Landing Page" : "Some missing data"
                    }
                  >
                    <Button
                      color="primary"
                      variant="outlined"
                      size="small"
                      onClick={() => handlePreview(id)}
                      disabled={activeResult.state === "edit" || !id.isValid}
                    >
                      Preview
                    </Button>
                  </CustomTooltip>
                </Grid>
                <Grid item>
                  <PublishButton
                    identifier={id}
                    disabled={activeResult.state === "edit"}
                  />
                </Grid>
                <Grid item>
                  <CustomTooltip
                    title={
                      id.state === "draft"
                        ? "Delete Draft"
                        : id.state === "findable"
                        ? "Retract"
                        : "Not published yet"
                    }
                  >
                    <Button
                      color="secondary"
                      variant="outlined"
                      size="small"
                      onClick={
                        id.state === "draft"
                          ? () => handleDelete(id)
                          : () => handleRetract(id)
                      }
                      disabled={
                        activeResult.state === "edit" ||
                        id.state === "registered"
                      }
                    >
                      {id.state === "draft" ? "Delete" : "Retract"}
                    </Button>
                  </CustomTooltip>
                </Grid>
                {!id.isValid && (
                  <Grid item className={classes.bottomSpaced}>
                    <MissingDataAlert />
                  </Grid>
                )}
              </Grid>
              <Alert
                severity="info"
                className={classes.bottomSpaced}
                sx={{ width: "100%" }}
              >
                <StateInfo identifierState={id.state} identifierUrl={id.url} />{" "}
                <a
                  href={docLinks.IGSNIdentifiers}
                  target="_blank"
                  rel="noreferrer"
                >
                  See IGSN Documentation for details
                </a>
              </Alert>
              <Collapse in={openIdForm}>
                <IdentifierWrapper
                  activeResult={activeResult}
                  id={id}
                  editable={editable}
                />
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
  }
);

const AssignDialog = observer(
  ({
    open,
    onClose,
    recordToAssignTo,
  }: {|
    open: boolean,
    onClose: () => void,
    recordToAssignTo: InventoryRecord,
  |}): Node => {
    const { assignIdentifier } = useIdentifiers();
    const [selectedIgsns, setSelectedIgsns] = React.useState<
      RsSet<IdentifierInTable>
    >(new RsSet([]));
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
        >
          <DialogTitle>Link existing IGSN ID</DialogTitle>
          <DialogContent>
            <Stack spacing={2}>
              <Typography>
                Select an existing IGSN ID to link to this item.
              </Typography>
              <IgsnTable
                selectedIgsns={selectedIgsns}
                setSelectedIgsns={setSelectedIgsns}
                disableMultipleRowSelection
                controlDefaults={{
                  isAssociated: false,
                  state: "draft",
                }}
              />
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
              Cancel
            </Button>
            <ValidatingSubmitButton
              loading={false}
              validationResult={
                selectedIgsns.some((igsn) => igsn.associatedGlobalId !== null)
                  ? IsInvalid(
                      "The selected IGSN ID is already assigned to another item."
                    )
                  : IsValid()
              }
              onClick={doNotAwait(async () => {
                await selectedIgsns.only
                  .toResult(
                    () =>
                      new Error(
                        "Invalid state: zero or many identifiers are selected"
                      )
                  )
                  .doAsync((igsn) => assignIdentifier(igsn, recordToAssignTo));
                await recordToAssignTo.fetchAdditionalInfo();
                setSelectedIgsns(new RsSet([]));
                onClose();
              })}
            >
              Link
            </ValidatingSubmitButton>
          </DialogActions>
        </Dialog>
      </ThemeProvider>
    );
  }
);

const IdentifiersCard = observer((): Node => {
  const {
    searchStore: { activeResult },
  } = useStores();
  if (!activeResult) throw new Error("ActiveResult must be a Record");
  const identifiers = activeResult.identifiers ?? [];
  const [assignDialogOpen, setAssignDialogOpen] = useState(false);
  return (
    <>
      {activeResult.state === "create" && (
        <Alert severity="info">
          This item has not been created yet. Please save the item first.
        </Alert>
      )}
      {activeResult.state !== "create" && identifiers.length === 0 && (
        <Stack direction="row" spacing={1}>
          <Button
            color="primary"
            variant="outlined"
            onClick={doNotAwait(() => activeResult.addIdentifier())}
          >
            Mint new IGSN ID
          </Button>
          <Button
            color="primary"
            variant="outlined"
            onClick={() => {
              setAssignDialogOpen(true);
            }}
          >
            Link existing IGSN ID
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
  Fields: {
    identifiers: Array<Identifier>,
    ...
  },
  FieldOwner: HasEditableFields<Fields>
>({ fieldOwner }: {| fieldOwner: FieldOwner |}): Node {
  return (
    <InputWrapper
      label=""
      error={false}
      explanation={
        fieldOwner.isFieldEditable("identifiers") ? (
          <>
            See the Documentation for information on{" "}
            <a href={docLinks.IGSNIdentifiers} target="_blank" rel="noreferrer">
              adding and publishing identifiers
            </a>
            .
          </>
        ) : null
      }
    >
      <IdentifiersCard />
    </InputWrapper>
  );
}

export default (observer(Identifiers): typeof Identifiers);
