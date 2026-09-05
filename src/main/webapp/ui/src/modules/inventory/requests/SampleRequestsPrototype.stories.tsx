/** @prototype Storybook-only UI exploration; not production-ready. */
import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import MenuItem from "@mui/material/MenuItem";
import Pagination from "@mui/material/Pagination";
import Paper from "@mui/material/Paper";
import Snackbar from "@mui/material/Snackbar";
import Stack from "@mui/material/Stack";
import { ThemeProvider } from "@mui/material/styles";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import TextField from "@mui/material/TextField";
import ToggleButton from "@mui/material/ToggleButton";
import ToggleButtonGroup from "@mui/material/ToggleButtonGroup";
import Typography from "@mui/material/Typography";
import type { Meta, StoryObj } from "@storybook/react-vite";
import React from "react";
import { useTranslation } from "react-i18next";
import { expect, userEvent, waitFor, within } from "storybook/test";
import { HeadingContext } from "@/components/DynamicHeadingLevel";
import GlobalId from "@/components/GlobalId";
import FormField from "@/components/Inputs/FormField";
import RecordTypeIcon from "@/components/RecordTypeIcon";
import VisuallyHiddenHeading from "@/components/VisuallyHiddenHeading";
import Layout2x1 from "@/Inventory/components/Layout/Layout2x1";
import StepperPanel from "@/Inventory/components/Stepper/StepperPanel";
import ActivitySectionPrototype from "@/Inventory/Requests/ActivitySectionPrototype";
import FulfilmentDialogPrototype, { FulfilmentBodyPrototype } from "@/Inventory/Requests/FulfilmentDialogPrototype";
import OwnerRequestsSectionPrototype from "@/Inventory/Requests/OwnerRequestsSectionPrototype";
import RequestableChipPrototype from "@/Inventory/Requests/RequestableChipPrototype";
import RequestDetailPrototype from "@/Inventory/Requests/RequestDetailPrototype";
import RequesterRequestsSectionPrototype from "@/Inventory/Requests/RequesterRequestsSectionPrototype";
import RequestStatusChipPrototype from "@/Inventory/Requests/RequestStatusChipPrototype";
import type { RequestsTab } from "@/Inventory/Requests/RequestsListPrototype";
import {
  type ProcessOperation,
  requestOperationPlan,
  type SampleRequest,
  validRequestOperation,
} from "@/Inventory/Requests/types";
import I18nRoot from "@/modules/common/i18n/I18nRoot";
import FormSectionsContext, { type AllowedFormTypes } from "@/stores/contexts/FormSections";
import materialTheme from "@/theme";
import InventoryChromePrototype, { RequesterFilterPrototype, startApiAs } from "./InventoryChromePrototype";
import SampleFilterPickerPrototype from "./SampleFilterPickerPrototype";
import {
  DEFAULT_REQUESTS,
  FULFILLED_REQUEST,
  initialState,
  OTHER_SAMPLES,
  OWNER,
  PENDING_REQUEST,
  PRODUCED_FOR_RACHEL,
  type PrototypeAction,
  type PrototypeState,
  RACHEL,
  REJECTED_REQUEST,
  reducer,
  SAMPLE,
  scenarioState,
} from "./sampleRequestsPrototype";

/*
 * Sample Requests prototype (biobank aspect 7, RPD-304), rebuilt on the
 * production Inventory record UI: the same StepperPanel sections, FormControl,
 * GlobalId chips, RecordTypeIcon and MUI theme the Sample form uses, so each
 * story can be compared against a production sample record. Components in
 * `src/Inventory/Requests` are prototypes, not production-ready implementations.
 *
 * The real Sample form is not mounted. The Overview section is a static
 * stand-in; selected production components use Storybook-only MSW handlers.
 */

/** In-memory replacement for SynchroniseFormSections, which persists via the UI-preferences API. */
function FormSectionsPrototype({ children }: { children: React.ReactNode }) {
  const [collapsed, setCollapsed] = React.useState<ReadonlySet<string>>(new Set());
  const value = React.useMemo(
    () => ({
      isExpanded: (type: AllowedFormTypes, section: string) => !collapsed.has(`${type}.${section}`),
      setExpanded: (type: AllowedFormTypes, section: string, open: boolean) =>
        setCollapsed((prev) => {
          const next = new Set(prev);
          if (open) next.delete(`${type}.${section}`);
          else next.add(`${type}.${section}`);
          return next;
        }),
      setAllExpanded: () => {},
    }),
    [collapsed],
  );
  return <FormSectionsContext.Provider value={value}>{children}</FormSectionsContext.Provider>;
}

function RecordHeaderPrototype({ requestable }: { requestable: boolean }) {
  const { t } = useTranslation("inventory");
  return (
    <Stack useFlexGap direction="row" spacing={1.5} sx={{ alignItems: "center", flexWrap: "wrap", px: 2, py: 1.5 }}>
      <RecordTypeIcon
        record={{ iconName: "sample", recordTypeLabel: t("recordTypes.sample.singular") }}
        style={{ fontSize: "1.5rem" }}
      />
      {/* The production Stepper title is an h2 under the app bar's h1 */}
      <Typography variant="h5" component="h2" sx={{ m: 0, flex: "1 1 12rem", minWidth: 0, overflowWrap: "anywhere" }}>
        {SAMPLE.name}
      </Typography>
      <GlobalId
        record={{
          id: 4021,
          name: SAMPLE.name,
          globalId: SAMPLE.globalId,
          permalinkURL: "/inventory/sample/4021",
          iconName: "sample",
          recordTypeLabel: t("recordTypes.sample.singular"),
        }}
      />
      {requestable && <RequestableChipPrototype />}
    </Stack>
  );
}

/** Static stand-in for the production Overview section (see note at the top). */
function OverviewSectionPrototype() {
  const { t } = useTranslation("inventory");
  return (
    <StepperPanel icon="sample" title={t("formSections.overview")} sectionName="overview" recordType="sample">
      <FormField
        value={undefined}
        label={t("fields.name.label")}
        disabled
        renderInput={() => <Typography variant="body1">{SAMPLE.name}</Typography>}
      />
      <FormField
        value={undefined}
        label={t("fields.owner.label")}
        disabled
        renderInput={() => <Typography variant="body1">{SAMPLE.owner.fullName}</Typography>}
      />
    </StepperPanel>
  );
}

type SharedWorkflow = { sharedState?: PrototypeState; sharedDispatch?: React.Dispatch<PrototypeAction> };

type SampleRecordArgs = SharedWorkflow & {
  onManageRequests?: () => void;
  persona: "owner" | "requester";
  requestsEnabled: boolean;
  defaultOperation: ProcessOperation;
  requests: ReadonlyArray<SampleRequest>;
};

function SampleRecordPrototype({
  onManageRequests,
  persona,
  requestsEnabled,
  defaultOperation,
  requests,
  sharedState,
  sharedDispatch,
}: SampleRecordArgs) {
  const { t } = useTranslation("inventory");
  const [localState, localDispatch] = React.useReducer(
    reducer,
    { requestable: requestsEnabled, requests, operation: defaultOperation },
    initialState,
  );
  const state = sharedState ?? localState;
  const dispatch = sharedDispatch ?? localDispatch;
  const active = state.requests.find((r) => r.id === state.active?.requestId);
  // the record shows only the requests raised against it
  const requestsHere = state.requests.filter((r) => r.sample.globalId === SAMPLE.globalId);
  const mine = requestsHere.filter((request) => request.requester.id === RACHEL.id);
  const myRequest = mine.find((request) => !["fulfilled", "rejected"].includes(request.status)) ?? mine[0] ?? null;

  return (
    <Stack sx={{ width: "100%", maxWidth: 880, bgcolor: "background.alt" }}>
      <RecordHeaderPrototype requestable={state.requestable} />
      {/* Same level the production Stepper gives its sections, so FormControl labels render as h4 */}
      <HeadingContext level={3}>
        <OverviewSectionPrototype />
        {persona === "owner" ? (
          <OwnerRequestsSectionPrototype
            managementAction={
              onManageRequests && (
                <Button variant="contained" onClick={onManageRequests} sx={{ alignSelf: "flex-start" }}>
                  Manage requests
                </Button>
              )
            }
            requestable={state.requestable}
            onRequestableChange={() => dispatch({ type: "toggleRequestable" })}
            requests={requestsHere}
            onReview={(request) => dispatch({ type: "openRequest", requestId: request.id })}
            onTransfer={(request) => dispatch({ type: "transfer", requestId: request.id })}
          />
        ) : (
          <RequesterRequestsSectionPrototype
            sample={SAMPLE}
            requestable={state.requestable}
            myRequest={myRequest}
            onSubmit={(note) => dispatch({ type: "submitRequest", note })}
          />
        )}
        {sharedState && (
          <Typography variant="caption">
            Showing up to 25 recent events. Open Request workspace for the complete searchable history.
          </Typography>
        )}
        <ActivitySectionPrototype
          compact={Boolean(sharedState)}
          entries={sharedState ? state.activity.slice(0, 25) : state.activity}
        />
      </HeadingContext>
      {active && state.active && (
        <FulfilmentDialogPrototype
          open
          sample={active.sample}
          request={active}
          step={state.active.step}
          operation={active.operation ?? "aliquot"}
          onOperationChange={(operation) => dispatch({ type: "setOperation", operation })}
          onOperationPlanChange={(plan) => dispatch({ type: "setOperationPlan", plan })}
          onApprove={() => dispatch({ type: "approve" })}
          onReject={(reason) => {
            dispatch({ type: "reject", reason });
            dispatch({ type: "closeDialog" });
          }}
          onRunOperation={() =>
            dispatch({
              type: "runOperation",
              producedRecordTypeLabel: t("recordTypes.sample.singular"),
            })
          }
          onTransfer={() => dispatch({ type: "transfer", requestId: active.id })}
          onClose={() => dispatch({ type: "closeDialog" })}
        />
      )}
      <Snackbar
        open={state.toast !== null}
        message={state.toast}
        autoHideDuration={2600}
        onClose={() => dispatch({ type: "clearToast" })}
      />
    </Stack>
  );
}

const meta = {
  title: "Inventory/Sample Requests",
  component: SampleRecordPrototype,
  // Keep isolated regression stories runnable, but expose one coherent prototype in the catalog.
  tags: ["!dev", "!autodocs"],
  args: {
    persona: "owner",
    requestsEnabled: true,
    defaultOperation: "aliquot",
    requests: DEFAULT_REQUESTS,
  },
  argTypes: {
    persona: { control: "radio", options: ["owner", "requester"] },
    defaultOperation: { control: "radio", options: ["aliquot", "derive"] },
    requests: { table: { disable: true } },
  },
  decorators: [
    (Story) => (
      <ThemeProvider theme={materialTheme}>
        <I18nRoot namespaces={["inventory", "common"]}>
          <FormSectionsPrototype>
            <Story />
          </FormSectionsPrototype>
        </I18nRoot>
      </ThemeProvider>
    ),
  ],
} satisfies Meta<typeof SampleRecordPrototype>;

export default meta;

/** Selected variant B: add request details at confirmation in the mocked operation wizard. */
function RequestInOperationWizardPrototype({
  state,
  dispatch,
  onClose,
}: {
  state: PrototypeState;
  dispatch: React.Dispatch<PrototypeAction>;
  onClose: () => void;
}) {
  const [step, setStep] = React.useState(0);
  const [operation, setOperation] = React.useState<ProcessOperation>("aliquot");
  const sample = state.requests.find((r) => r.sample.globalId === SAMPLE.globalId)?.sample ?? SAMPLE;
  const [draft, setDraft] = React.useState<SampleRequest>(() => ({
    ...PENDING_REQUEST,
    id: "Draft request",
    sample,
    status: "approved",
    note: "",
    operationPlan: requestOperationPlan({ ...PENDING_REQUEST, sample }),
  }));
  const request = { ...draft, sample };
  const stages = ["Operation", "Confirm and add request"];
  const last = step === stages.length - 1;
  const requestValid = Boolean(draft.note.trim());
  const operationValid = validRequestOperation(request, operation);
  const requestFields = (
    <Stack useFlexGap spacing={2}>
      <TextField
        select
        label="Requested for"
        value={draft.requester.id}
        onChange={(event) =>
          setDraft({
            ...draft,
            requester: Number(event.target.value) === RACHEL.id ? RACHEL : FULFILLED_REQUEST.requester,
          })
        }
      >
        <MenuItem value={RACHEL.id}>{RACHEL.fullName}</MenuItem>
        <MenuItem value={FULFILLED_REQUEST.requester.id}>{FULFILLED_REQUEST.requester.fullName}</MenuItem>
      </TextField>
      <TextField
        label="Request note"
        required
        multiline
        minRows={2}
        value={draft.note}
        onChange={(event) => setDraft({ ...draft, note: event.target.value })}
      />
    </Stack>
  );
  return (
    <Paper variant="outlined" sx={{ m: 2, p: 2, maxWidth: 760, minWidth: 0, overflowWrap: "anywhere" }}>
      <Stack useFlexGap spacing={2}>
        <Typography component="h2" variant="h5">
          Operation wizard with a request
        </Typography>
        <Alert severity="info">
          This sample is owned by {sample.owner.fullName}. You can submit the request at the end.
        </Alert>
        <Typography variant="body2" color="text.secondary">
          Owner-side prototype: record a researcher's request while preparing material. Nothing is saved until Run
          operation. The owner approves the request as part of this action; ownership transfer remains separate.
        </Typography>
        <Typography component="h3" variant="h6">
          Step {step + 1} of {stages.length}: {stages[step]}
        </Typography>
        {last && requestFields}
        {!last && (
          <FulfilmentBodyPrototype
            sample={sample}
            request={request}
            step="operation"
            operation={operation}
            onOperationChange={setOperation}
            onOperationPlanChange={(plan) => setDraft({ ...draft, operationPlan: plan })}
            rejecting={false}
            reason=""
            onReasonChange={() => {}}
            onTransfer={() => {}}
          />
        )}
        {last && (
          <>
            <Typography>
              {draft.requester.fullName}: {draft.note || "Add a request note before running."}
            </Typography>
            <Typography>
              {operation === "aliquot" ? "Aliquot" : "Derive"}: {requestOperationPlan(request).sampleName}. Create{" "}
              {requestOperationPlan(request).count} subsamples of {requestOperationPlan(request).eachAmount} mL; take{" "}
              {requestOperationPlan(request).amountTaken} mL from {requestOperationPlan(request).originId}.
            </Typography>
            <Alert severity="info">
              Run operation records and approves the request, then prepares a new sample. It does not transfer
              ownership.
            </Alert>
          </>
        )}
        <Stack useFlexGap direction="row" spacing={1} sx={{ flexWrap: "wrap" }}>
          <Button onClick={onClose}>Cancel wizard</Button>
          {step > 0 && <Button onClick={() => setStep(step - 1)}>Back</Button>}
          {last ? (
            <Button
              variant="contained"
              disabled={!requestValid || !operationValid}
              onClick={() => {
                dispatch({ type: "prepareRequest", request, operation, producedRecordTypeLabel: "Sample" });
                onClose();
              }}
            >
              Run operation
            </Button>
          ) : (
            <Button variant="contained" disabled={!operationValid} onClick={() => setStep(step + 1)}>
              Next
            </Button>
          )}
        </Stack>
        <Box component="details">
          <summary>Inspect wizard draft</summary>
          <Box component="pre" sx={{ whiteSpace: "pre-wrap", overflowWrap: "anywhere" }}>
            {JSON.stringify({ operation, request }, null, 2)}
          </Box>
        </Box>
      </Stack>
    </Paper>
  );
}

function UnifiedWorkspacePrototype({
  scenario,
  controls,
}: {
  scenario: Parameters<typeof scenarioState>[0];
  controls: React.ReactNode;
}) {
  const [state, dispatch] = React.useReducer(reducer, scenario, scenarioState);
  const [persona, setPersona] = React.useState<"owner" | "requester">("owner");
  const [view, setView] = React.useState("requests");
  const [queueEntry, setQueueEntry] = React.useState({ sampleId: "", revision: 0 });
  const [switching, setSwitching] = React.useState(false);
  const [personaError, setPersonaError] = React.useState(false);
  React.useEffect(() => {
    void startApiAs(OWNER).catch(() => setPersonaError(true));
  }, []);
  const go = (next: string) => {
    dispatch({ type: "closeDialog" });
    setView(next);
  };
  return (
    <InventoryChromePrototype
      selected={view === "record" ? "samples" : "requests"}
      requestsBadge={state.requests.filter((r) => r.status === "pending").length}
    >
      {controls}
      <Stack useFlexGap direction="row" spacing={1} sx={{ p: 2, flexWrap: "wrap", alignItems: "center" }}>
        <TextField
          select
          size="small"
          label="Act as"
          value={persona}
          disabled={switching}
          onChange={async (event) => {
            const next = event.target.value === "owner" ? "owner" : "requester";
            setSwitching(true);
            setPersonaError(false);
            try {
              await startApiAs(next === "owner" ? OWNER : RACHEL);
              setPersona(next);
              go("requests");
            } catch {
              setPersonaError(true);
            } finally {
              setSwitching(false);
            }
          }}
        >
          <MenuItem value="owner">Dana, sample owner</MenuItem>
          <MenuItem value="requester">Rachel, requester</MenuItem>
        </TextField>
        <Button aria-pressed={view === "requests"} onClick={() => go("requests")}>
          Request workspace
        </Button>
        <Button aria-pressed={view === "record"} onClick={() => go("record")}>
          Sample record
        </Button>
        {persona === "owner" && (
          <Button aria-pressed={view === "wizard"} onClick={() => go("wizard")}>
            Operation wizard
          </Button>
        )}
      </Stack>
      {personaError && <Alert severity="error">Could not switch the simulated user. Try again.</Alert>}
      <Box sx={{ minWidth: 0 }}>
        {view === "wizard" && (
          <RequestInOperationWizardPrototype state={state} dispatch={dispatch} onClose={() => setView("requests")} />
        )}
        <Box sx={{ display: view === "requests" ? "block" : "none" }}>
          <RequestsPagePrototype
            key={`${persona}-${queueEntry.revision}`}
            initialSampleFilter={queueEntry.sampleId}
            persona={persona}
            requests={state.requests}
            tab={persona === "owner" ? "incoming" : "outgoing"}
            sharedState={state}
            sharedDispatch={dispatch}
          />
        </Box>
        {view === "record" && (
          <Box sx={{ p: 2, minWidth: 0, overflowWrap: "anywhere" }}>
            <SampleRecordPrototype
              onManageRequests={() => {
                setQueueEntry({ sampleId: SAMPLE.globalId, revision: queueEntry.revision + 1 });
                go("requests");
              }}
              persona={persona}
              requestsEnabled={state.requestable}
              defaultOperation="aliquot"
              requests={state.requests}
              sharedState={state}
              sharedDispatch={dispatch}
            />
          </Box>
        )}
        <Box component="details" sx={{ m: 2 }}>
          <summary>Inspect simulated state</summary>
          <Box
            component="pre"
            sx={{ whiteSpace: "pre-wrap", overflowWrap: "anywhere", maxHeight: 300, overflow: "auto" }}
          >
            {JSON.stringify(state, null, 2)}
          </Box>
        </Box>
      </Box>
    </InventoryChromePrototype>
  );
}

function SampleRequestsPrototype() {
  const [scenario, setScenario] = React.useState<Parameters<typeof scenarioState>[0]>(() => {
    const selected = new URLSearchParams(location.search).get("scenario");
    return selected === "new" || selected === "stress" || selected === "empty" ? selected : "standard";
  });
  const [revision, setRevision] = React.useState(0);
  return (
    <UnifiedWorkspacePrototype
      key={`${scenario}-${revision}`}
      scenario={scenario}
      controls={
        <Stack useFlexGap direction="row" spacing={2} sx={{ p: 2, alignItems: "center", flexWrap: "wrap" }}>
          <Typography component="h1" variant="h6">
            Sample Requests prototype
          </Typography>
          <TextField
            select
            label="Scenario (resets workflow)"
            sx={{ width: { xs: "100%", sm: 260 }, minWidth: 0 }}
            size="small"
            value={scenario}
            onChange={(event) => {
              const next = event.target.value;
              if (next !== "standard" && next !== "new" && next !== "stress" && next !== "empty") return;
              setScenario(next);
              const url = new URL(location.href);
              url.searchParams.set("scenario", next);
              history.replaceState(null, "", url);
            }}
          >
            <MenuItem value="standard">Standard workflow</MenuItem>
            <MenuItem value="new">New request walkthrough</MenuItem>
            <MenuItem value="stress">Stress: 100 requests / 500 events</MenuItem>
            <MenuItem value="empty">Empty queue</MenuItem>
          </TextField>
          <Button onClick={() => setRevision(revision + 1)}>Reset scenario</Button>
          <Typography variant="caption">
            In-memory only. Views share state; changing or resetting a scenario clears it.
          </Typography>
        </Stack>
      }
    />
  );
}

export const Prototype: Story = {
  tags: ["dev"],
  parameters: { layout: "fullscreen" },
  loaders: [() => startApiAs(OWNER)],
  render: () => <SampleRequestsPrototype />,
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await userEvent.click(await canvas.findByRole("button", { name: "Review REQ-118" }));
    await userEvent.click(canvas.getByRole("button", { name: "Approve" }));
    await userEvent.click(canvas.getByRole("button", { name: "Run operation" }));
    await userEvent.click(canvas.getByRole("button", { name: "Transfer ownership" }));
    await userEvent.click(canvas.getByRole("button", { name: /^Sample record$/ }));
    await userEvent.click(canvas.getByRole("button", { name: "Manage requests" }));
    await expect(canvas.getAllByText("Fulfilled").length).toBeGreaterThan(1);
    await userEvent.click(canvas.getByRole("combobox", { name: "Act as" }));
    await userEvent.click(await within(document.body).findByRole("option", { name: "Rachel, requester" }));
    await userEvent.click(await canvas.findByRole("button", { name: "View REQ-118" }));
    await expect(canvas.queryByRole("button", { name: /Notifications/ })).not.toBeInTheDocument();
    await expect(canvas.getByRole("link", { name: "SA4088" })).toBeVisible();
    await expect(canvas.queryByRole("button", { name: "Approve" })).not.toBeInTheDocument();
  },
};

type Story = StoryObj<typeof meta>;

export const SpacingCheck: Story = {
  ...Prototype,
  tags: ["!dev"],
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await canvas.findByRole("button", { name: "Review REQ-118" });
    await userEvent.click(canvas.getByRole("button", { name: "Review REQ-118" }));
    const queueHeading = canvas.getByRole("heading", { name: "Request queue" }).getBoundingClientRect();
    const detailHeading = canvas.getByRole("heading", { name: "REQ-118 · SA4021" }).getBoundingClientRect();
    await expect(Math.abs(queueHeading.top - detailHeading.top)).toBeLessThanOrEqual(1);
    await userEvent.click(canvas.getByRole("button", { name: "Approve" }));
    const heading = canvas.getByRole("heading", { name: "REQ-118 · SA4021" });
    const panel = heading.parentElement?.parentElement;
    if (!panel) throw new Error("Request detail panel missing");
    panel.scrollTop = 100;
    await new Promise(requestAnimationFrame);
    await expect(panel.scrollTop).toBeGreaterThan(0);
    await expect(
      Math.abs(
        heading.getBoundingClientRect().top -
          canvas.getByRole("heading", { name: "Request queue" }).getBoundingClientRect().top,
      ),
    ).toBeLessThanOrEqual(1);
    await expect(document.documentElement.scrollHeight).toBeLessThanOrEqual(window.innerHeight + 1);
    await userEvent.click(canvas.getByRole("button", { name: /^Sample record$/ }));
    const region = canvas.getByRole("region", { name: /^Requests/ });
    await expect(within(region).queryByRole("list", { name: "Requests" })).not.toBeInTheDocument();
    await expect(within(region).getByRole("button", { name: "Manage requests" })).toBeVisible();
    await expect(region.scrollWidth).toBeLessThanOrEqual(region.clientWidth + 1);
    await userEvent.click(canvas.getByRole("button", { name: /^Operation wizard$/ }));
    await userEvent.click(canvas.getByRole("button", { name: "Next" }));
    const cancel = canvas.getByRole("button", { name: "Cancel wizard" }).getBoundingClientRect();
    const run = canvas.getByRole("button", { name: "Run operation" }).getBoundingClientRect();
    await expect(run.top >= cancel.bottom || run.left >= cancel.right).toBe(true);
    await expect(document.documentElement.scrollWidth).toBeLessThanOrEqual(window.innerWidth);
  },
};

export const RequestFiltersCheck: Story = {
  ...Prototype,
  tags: ["!dev"],
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await expect(canvas.queryByRole("textbox", { name: "Search requests" })).not.toBeInTheDocument();
    await userEvent.click(await canvas.findByRole("combobox", { name: "Requester" }));
    await userEvent.type(canvas.getByRole("combobox", { name: "Requester" }), "mlin");
    await userEvent.click(await within(document.body).findByRole("option", { name: "Mei Lin (mlin)" }));
    await expect(canvas.getByText("1 matching requests")).toBeVisible();
    await expect(canvas.getByRole("button", { name: "View REQ-112" })).toBeVisible();
    await expect(canvas.queryByRole("button", { name: "Review REQ-118" })).not.toBeInTheDocument();
    await userEvent.click(canvas.getByRole("button", { name: "Requested sample" }));
    const picker = within(await within(document.body).findByRole("dialog", { name: "Requested sample" }));
    await picker.findByText("Donor PBMC vial · D-114");
    await userEvent.type(picker.getByRole("searchbox"), "PBMC{Enter}");
    await waitFor(() => expect(picker.queryByText(SAMPLE.name)).not.toBeInTheDocument());
    await userEvent.click(await picker.findByText("Donor PBMC vial · D-114"));
    await userEvent.click(picker.getByRole("button", { name: "Choose" }));
    await expect(canvas.getByText("0 matching requests")).toBeVisible();
    await userEvent.click(canvas.getByRole("button", { name: "Sample record" }));
    const region = canvas.getByRole("region", { name: /^Requests/ });
    await expect(within(region).queryByRole("list")).not.toBeInTheDocument();
    await userEvent.click(within(region).getByRole("button", { name: "Manage requests" }));
    await expect(canvas.getByRole("button", { name: "Requested sample" })).toHaveTextContent(SAMPLE.globalId);
    await expect(canvas.getByRole("combobox", { name: "Requester" })).toHaveValue("");
    await expect(canvas.getByText("3 matching requests")).toBeVisible();
    await userEvent.click(canvas.getByRole("button", { name: "Clear filters" }));
    await expect(canvas.getByRole("button", { name: "Requested sample" })).not.toHaveTextContent(SAMPLE.globalId);
  },
};

export const RequestInWizardCheck: Story = {
  ...Prototype,
  tags: ["!dev"],
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await userEvent.click(await canvas.findByRole("button", { name: /^Operation wizard$/ }));
    await expect(canvas.getByRole("alert")).toHaveTextContent(
      "This sample is owned by Dana Marsh. You can submit the request at the end.",
    );
    await expect(canvas.queryByRole("combobox", { name: "Request placement" })).not.toBeInTheDocument();
    await expect(canvas.queryByRole("textbox", { name: /Request note/ })).not.toBeInTheDocument();
    await userEvent.click(canvas.getByRole("button", { name: "Next" }));
    await expect(canvas.getByRole("button", { name: "Run operation" })).toBeDisabled();
    await userEvent.type(canvas.getByRole("textbox", { name: /Request note/ }), "Material for Rachel's assay");
    await userEvent.click(canvas.getByRole("button", { name: "Back" }));
    await expect(canvas.getByRole("alert")).toHaveTextContent("owned by Dana Marsh");
    await userEvent.click(canvas.getByRole("button", { name: "Next" }));
    await expect(canvas.getByRole("textbox", { name: /Request note/ })).toHaveValue("Material for Rachel's assay");
    await userEvent.click(canvas.getByRole("button", { name: "Run operation" }));
    await expect(canvas.getByRole("heading", { name: "REQ-119 · SA4021" })).toBeVisible();
    await expect(canvas.getByRole("link", { name: "SA4088" })).toBeVisible();
    await expect(canvas.getByRole("button", { name: "Transfer ownership" })).toBeEnabled();
    await userEvent.click(canvas.getByRole("button", { name: /^Operation wizard$/ }));
    await userEvent.click(canvas.getByRole("button", { name: "Cancel wizard" }));
    await expect(canvas.queryByRole("button", { name: /REQ-120/ })).not.toBeInTheDocument();
  },
};

export const RequestDirectionCheck: Story = {
  tags: ["!dev"],
  render: () => (
    <Box sx={{ width: 768, maxWidth: "100%" }}>
      <UnifiedWorkspacePrototype scenario="standard" controls={null} />
    </Box>
  ),
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await userEvent.click(await canvas.findByRole("button", { name: "Review REQ-118" }));
    await userEvent.click(canvas.getByRole("button", { name: "All requests" }));
    await userEvent.click(canvas.getByRole("button", { name: "My requests" }));
    await expect(canvas.getByRole("heading", { name: "Request queue" })).toBeVisible();
    await expect(canvas.getByRole("button", { name: "View REQ-117" })).toBeVisible();
    await expect(canvas.queryByRole("heading", { name: "REQ-117 · SA3980" })).not.toBeInTheDocument();
    await userEvent.click(canvas.getByRole("button", { name: "View REQ-117" }));
    await expect(canvas.getByRole("heading", { name: "REQ-117 · SA3980" })).toBeVisible();
  },
};

export const PastRequestsCheck: Story = {
  ...Prototype,
  tags: ["!dev"],
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await userEvent.click(await canvas.findByRole("button", { name: "Past requests (2)" }));
    await expect(canvas.queryByRole("button", { name: "Review REQ-118" })).not.toBeInTheDocument();
    await userEvent.click(canvas.getByRole("button", { name: "View REQ-104" }));
    await expect(canvas.getByRole("alert")).toHaveTextContent("Over the volume the biobank can release");
    await expect(canvas.queryByRole("button", { name: "Approve" })).not.toBeInTheDocument();
    await userEvent.click(canvas.getByRole("button", { name: "View REQ-112" }));
    await expect(canvas.getByRole("link", { name: "SA4076" })).toBeVisible();
    await expect(canvas.getByRole("button", { name: "Transferred" })).toBeDisabled();
    await userEvent.click(canvas.getByRole("button", { name: "Active (1)" }));
    await userEvent.click(canvas.getByRole("button", { name: "Review REQ-118" }));
    await userEvent.click(canvas.getByRole("button", { name: "Approve" }));
    await userEvent.click(canvas.getByRole("button", { name: "Run operation" }));
    await userEvent.click(canvas.getByRole("button", { name: "Transfer ownership" }));
    await userEvent.click(canvas.getByRole("button", { name: "Past requests (3)" }));
    await expect(canvas.getByRole("button", { name: "View REQ-118" })).toBeVisible();
  },
};

export const UnifiedStressCheck: Story = {
  ...Prototype,
  tags: ["!dev"],
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await userEvent.click(await canvas.findByRole("combobox", { name: "Scenario (resets workflow)" }));
    await userEvent.click(
      await within(document.body).findByRole("option", { name: "Stress: 100 requests / 500 events" }),
    );
    await userEvent.click(canvas.getByRole("button", { name: "Go to page 2" }));
    await userEvent.click(canvas.getByRole("button", { name: "Review REQ-1010" }));
    const history = canvas.getByRole("list", { name: "Request history" });
    await expect(within(history).getAllByRole("listitem")).toHaveLength(5);
    await expect(canvas.queryByRole("button", { name: "All sample activity" })).not.toBeInTheDocument();
    await expect(canvas.queryByRole("textbox", { name: "Search activity" })).not.toBeInTheDocument();
    await userEvent.click(canvas.getByRole("button", { name: "All requests" }));
    await expect(canvas.getByRole("button", { name: "Review REQ-1010" })).toHaveFocus();
    await expect(canvas.getByRole("button", { name: "page 2" })).toHaveAttribute("aria-current", "page");
  },
};

/** Dana Marsh owns SA4021: one pending request, one fulfilled, one rejected. */
export const OwnerView: Story = {
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await expect(await canvas.findByRole("switch", { name: /requestable/i })).toBeChecked();
    await expect(canvas.getByText("REQ-118")).toBeInTheDocument();
    await expect(canvas.getByText("1 pending")).toBeInTheDocument();
    const heading = canvas.getByRole("heading", { name: /^Requests/ });
    const label = within(heading).getByText("Requests").getBoundingClientRect();
    // Measure the decorative icon and visible label: both must occupy the same line.
    const icon = heading.querySelector("svg")?.getBoundingClientRect();
    await expect(icon).toBeDefined();
    await expect(label.top).toBeLessThan(icon?.bottom ?? 0);
    await expect(label.bottom).toBeGreaterThan(icon?.top ?? 0);
  },
};

/** Review, approve, run the operation and transfer ownership: REQ-118 ends fulfilled. */
export const OwnerFulfilsRequest: Story = {
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await userEvent.click(await canvas.findByRole("button", { name: "Review" }));
    const dialog = within(await within(document.body).findByRole("dialog"));
    await userEvent.click(dialog.getByRole("button", { name: "Approve" }));
    await userEvent.click(dialog.getByRole("radio", { name: /aliquot/i }));
    await userEvent.click(dialog.getByRole("button", { name: "Run operation" }));
    await expect(dialog.getByRole("link", { name: "SA4088" })).toBeInTheDocument();
    await userEvent.click(dialog.getByRole("button", { name: "Transfer ownership" }));
    await expect(dialog.getByRole("button", { name: "Transferred" })).toBeDisabled();
    await userEvent.click(dialog.getByRole("button", { name: "Close" }));
    await expect(canvas.queryByText("1 pending")).not.toBeInTheDocument();
    await expect(canvas.getAllByText("Fulfilled")).toHaveLength(2);
  },
};

/** Rejecting asks for a reason first; the reason lands on the request and in the activity log. */
export const OwnerRejectsRequest: Story = {
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await userEvent.click(await canvas.findByRole("button", { name: "Review" }));
    const dialog = within(await within(document.body).findByRole("dialog"));
    await userEvent.click(dialog.getByRole("button", { name: "Reject" }));
    await userEvent.type(dialog.getByLabelText("Reason for rejection"), "Lot B-2411 is reserved for the trial.");
    await userEvent.click(dialog.getByRole("button", { name: "Confirm rejection" }));
    await expect(canvas.getByText(/Lot B-2411 is reserved/)).toBeInTheDocument();
  },
};

/** Switching the sample off removes the Requestable chip from the header. */
export const OwnerTurnsRequestsOff: Story = {
  args: { requests: [FULFILLED_REQUEST, REJECTED_REQUEST] },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await userEvent.click(await canvas.findByRole("switch", { name: /requestable/i }));
    await expect(canvas.queryByText("Requestable", { selector: ".MuiChip-label" })).not.toBeInTheDocument();
  },
};

/** Rachel Okafor has not asked yet: the request form is offered. */
export const RequesterCanRequest: Story = {
  args: { persona: "requester", requests: [FULFILLED_REQUEST, REJECTED_REQUEST] },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await userEvent.type(
      await canvas.findByRole("textbox", { name: "What you need" }),
      "Two 1 mL aliquots for a titration.",
    );
    await userEvent.click(canvas.getByRole("button", { name: "Send request" }));
    await expect(canvas.getByText("Your request REQ-119")).toBeInTheDocument();
    await expect(canvas.getByText("Pending", { selector: ".MuiChip-label" })).toBeInTheDocument();
  },
};

/** Rachel's REQ-118 is waiting on the owner. */
export const RequesterPending: Story = {
  args: { persona: "requester" },
};

/** REQ-118 was fulfilled: the new sample and its subsamples are hers. */
export const RequesterFulfilled: Story = {
  args: {
    persona: "requester",
    requests: [
      {
        ...PENDING_REQUEST,
        status: "fulfilled",
        operation: "aliquot",
        produced: PRODUCED_FOR_RACHEL,
      },
      FULFILLED_REQUEST,
      REJECTED_REQUEST,
    ],
  },
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await userEvent.type(await canvas.findByRole("textbox"), "Another vial, please");
    await userEvent.click(canvas.getByRole("button", { name: "Send request" }));
    await expect(canvas.getByText("Your request REQ-119")).toBeVisible();
    await expect(canvas.queryByRole("button", { name: "Send request" })).not.toBeInTheDocument();
  },
};

export const RequesterRejectedRetry: Story = {
  ...RequesterFulfilled,
  args: {
    persona: "requester",
    requests: [{ ...REJECTED_REQUEST, requester: RACHEL }],
  },
};

/** The owner never switched the sample on. */
export const RequesterNotRequestable: Story = {
  args: { persona: "requester", requestsEnabled: false, requests: [] },
};

function DialogStepPrototype({
  step,
  status,
  operation = "aliquot",
}: {
  step: "review" | "operation" | "done";
  status: SampleRequest["status"];
  operation?: ProcessOperation;
}) {
  const [op, setOp] = React.useState<ProcessOperation>(operation);
  const request: SampleRequest =
    status === "pending" || status === "approved"
      ? { ...PENDING_REQUEST, status }
      : {
          ...PENDING_REQUEST,
          status,
          operation,
          produced: PRODUCED_FOR_RACHEL,
        };
  return (
    <FulfilmentDialogPrototype
      open
      disablePortal
      sample={SAMPLE}
      request={request}
      step={step}
      operation={op}
      onOperationChange={setOp}
      onApprove={() => {}}
      onReject={() => {}}
      onRunOperation={() => {}}
      onTransfer={() => {}}
      onClose={() => {}}
    />
  );
}

export const FulfilmentReview: Story = {
  render: () => <DialogStepPrototype step="review" status="pending" />,
};

export const FulfilmentOperation: Story = {
  render: () => <DialogStepPrototype step="operation" status="approved" />,
};

export const FulfilmentPrepared: Story = {
  render: () => <DialogStepPrototype step="done" status="prepared" />,
};

export const FulfilmentFulfilled: Story = {
  render: () => <DialogStepPrototype step="done" status="fulfilled" />,
};

/** Stand-in for the production results table (LeftPanelView needs the MobX search store). */
function ResultsTablePrototype({ selectedGlobalId }: { selectedGlobalId?: string }) {
  return (
    <TableContainer sx={{ width: "100%", overflowX: "auto" }}>
      <Table size="small" aria-label="Samples">
        <TableHead>
          <TableRow>
            <TableCell>Name</TableCell>
            <TableCell>Global ID</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {OTHER_SAMPLES.map((sample) => (
            <TableRow key={sample.globalId} hover selected={sample.globalId === selectedGlobalId}>
              <TableCell>
                <Stack useFlexGap direction="row" spacing={1} sx={{ alignItems: "center", flexWrap: "wrap" }}>
                  <span>{sample.name}</span>
                  {sample.requestable && <RequestableChipPrototype />}
                </Stack>
              </TableCell>
              <TableCell>
                <GlobalId
                  record={{
                    id: Number(sample.globalId.slice(2)),
                    name: sample.name,
                    globalId: sample.globalId,
                    permalinkURL: `/inventory/sample/${sample.globalId.slice(2)}`,
                    iconName: "sample",
                    recordTypeLabel: "Sample",
                  }}
                />
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
}

/** How requestable material reads in the Inventory list view: a chip beside the name. */
export const ResultsListChip: Story = {
  render: () => <ResultsTablePrototype />,
};

/*
 * Approvals walkthrough. The notification system carries text and a link
 * only, so a notification cannot host Approve/Reject buttons. Instead it links
 * to the Requests page (`/inventory/requests`, a sidebar entry like IGSN IDs):
 * a list of requests to review, and a page per request where the owner
 * approves or rejects.
 */

type RequestsPageArgs = SharedWorkflow & {
  initialSampleFilter?: string;
  persona: "owner" | "requester";
  requests: ReadonlyArray<SampleRequest>;
  tab?: RequestsTab;
  /** Start on a request's page, as a notification link would. */
  openId?: string;
};

/** Stand-in for the page at /inventory/requests; the in-context stories wrap it in InventoryChromePrototype. */
function RequestsPagePrototype({
  initialSampleFilter = "",
  persona,
  requests,
  tab: initialTab = "incoming",
  openId,
  sharedState,
  sharedDispatch,
}: RequestsPageArgs) {
  const { t } = useTranslation("inventory");
  const [showDetail, setShowDetail] = React.useState(Boolean(openId));
  const [sampleFilter, setSampleFilter] = React.useState(initialSampleFilter);
  const [samplePickerOpen, setSamplePickerOpen] = React.useState(false);
  const [requesterFilter, setRequesterFilter] = React.useState<number | null>(null);
  const [page, setPage] = React.useState(1);
  React.useEffect(() => {
    if (sharedState?.active) setShowDetail(true);
  }, [sharedState?.active?.requestId]);
  const queueRef = React.useRef<HTMLDivElement>(null);
  const headingRef = React.useRef<HTMLHeadingElement>(null);
  const [tab, setTab] = React.useState<RequestsTab>(initialTab);
  const [requestPeriod, setRequestPeriod] = React.useState("all");
  const me = persona === "owner" ? OWNER : RACHEL;
  const [localState, localDispatch] = React.useReducer(reducer, { requests }, (init) => {
    const base = initialState(init);
    const first =
      openId ??
      requests.find((r) => (initialTab === "incoming" ? r.sample.owner.id === me.id : r.requester.id === me.id))?.id;
    return first ? reducer(base, { type: "openRequest", requestId: first }) : base;
  });
  const state = sharedState ?? localState;
  const dispatch = sharedDispatch ?? localDispatch;
  const active = state.requests.find((r) => r.id === state.active?.requestId);
  const tabRows = state.requests.filter((r) =>
    tab === "incoming" ? r.sample.owner.id === me.id : r.requester.id === me.id,
  );
  const samples = [
    ...new Map([SAMPLE, ...state.requests.map((r) => r.sample)].map((sample) => [sample.globalId, sample])).values(),
  ];
  const requesters = [...new Map(tabRows.map((r) => [r.requester.id, r.requester])).values()];
  const rows = tabRows.filter(
    (r) =>
      (!sampleFilter || r.sample.globalId === sampleFilter) &&
      (requesterFilter === null || r.requester.id === requesterFilter),
  );
  const pastCount = rows.filter((r) => ["fulfilled", "rejected"].includes(r.status)).length;
  const matches = rows.filter(
    (r) => requestPeriod === "all" || (requestPeriod === "past") === ["fulfilled", "rejected"].includes(r.status),
  );
  // ponytail: mock audit entries have no requestId; token matching suffices until the real audit API is wired.
  const events = state.activity.filter(
    (event) =>
      active &&
      (event.text.split(/[^\w-]+/).includes(active.id) ||
        (event.kind === "produced" && event.text.split(/[^\w-]+/).includes(active.produced?.globalId ?? ""))),
  );
  const openRequest = (id: string) => {
    dispatch({ type: "openRequest", requestId: id });
    setShowDetail(true);
    requestAnimationFrame(() => headingRef.current?.focus());
  };
  const backToQueue = () => {
    setShowDetail(false);
    requestAnimationFrame(() =>
      queueRef.current
        ?.querySelector<HTMLButtonElement>(`[data-request-id="${active?.id}"]`)
        ?.focus({ preventScroll: true }),
    );
  };

  return (
    <Box
      sx={{
        p: 2,
        containerType: "inline-size",
        containerName: "request-workspace",
        maxWidth: 1400,
        width: "100%",
        minWidth: 0,
        overflowWrap: "anywhere",
      }}
    >
      <VisuallyHiddenHeading variant="h2">{t("requests.sectionTitle")}</VisuallyHiddenHeading>
      <HeadingContext level={3}>
        <Box
          sx={{
            display: "grid",
            gridTemplateColumns: "minmax(280px, 1fr) minmax(0, 2fr)",
            "@container request-workspace (max-width: 900px)": { gridTemplateColumns: "minmax(0, 1fr)" },
            gap: 2,
            alignItems: "start",
          }}
        >
          <Paper
            variant="outlined"
            sx={{
              p: 2,
              minWidth: 0,
              "@container request-workspace (max-width: 900px)": { display: showDetail ? "none" : "block" },
            }}
          >
            <Typography component="h3" variant="h6">
              Request queue
            </Typography>
            <ToggleButtonGroup
              exclusive
              value={tab}
              color="primary"
              size="small"
              aria-label="Request direction"
              sx={{ mb: 2 }}
            >
              {(["incoming", "outgoing"] as const).map((value) => (
                <ToggleButton
                  key={value}
                  value={value}
                  onClick={() => {
                    if (value === tab) return;
                    setTab(value);
                    setRequesterFilter(null);
                    setPage(1);
                    dispatch({ type: "closeDialog" });
                    setShowDetail(false);
                  }}
                >
                  {t(`requests.list.tabs.${value}`)}
                </ToggleButton>
              ))}
            </ToggleButtonGroup>
            <ToggleButtonGroup
              exclusive
              value={requestPeriod}
              color="primary"
              size="small"
              fullWidth
              sx={{ mb: 1 }}
              aria-label="Request history filters"
            >
              {(["all", "active", "past"] as const).map((period) => (
                <ToggleButton
                  key={period}
                  value={period}
                  onClick={() => {
                    setRequestPeriod(period);
                    setPage(1);
                    queueRef.current?.scrollTo(0, 0);
                  }}
                >
                  {period === "all"
                    ? `All (${rows.length})`
                    : period === "active"
                      ? `Active (${rows.length - pastCount})`
                      : `Past requests (${pastCount})`}
                </ToggleButton>
              ))}
            </ToggleButtonGroup>
            <Stack useFlexGap spacing={2} sx={{ my: 2 }}>
              <Button
                variant="outlined"
                aria-label="Requested sample"
                onClick={() => setSamplePickerOpen(true)}
                sx={{ justifyContent: "flex-start", textTransform: "none" }}
              >
                {sampleFilter ? `Requested sample: ${sampleFilter}` : "Requested sample"}
              </Button>
              {samplePickerOpen && (
                <SampleFilterPickerPrototype
                  title="Requested sample"
                  samples={samples}
                  onClose={() => setSamplePickerOpen(false)}
                  onPick={(id) => {
                    setSampleFilter(id);
                    setPage(1);
                    setSamplePickerOpen(false);
                  }}
                />
              )}
              <RequesterFilterPrototype
                label="Requester"
                requesters={requesters}
                selectedId={requesterFilter}
                onSelection={(id) => {
                  setRequesterFilter(id);
                  setPage(1);
                }}
              />
            </Stack>
            {(sampleFilter || requesterFilter !== null || requestPeriod !== "all") && (
              <Button
                onClick={() => {
                  setSampleFilter("");
                  setRequesterFilter(null);
                  setRequestPeriod("all");
                  setPage(1);
                }}
              >
                Clear filters
              </Button>
            )}
            <Typography role="status" variant="caption">
              {matches.length} matching requests
            </Typography>
            <Box ref={queueRef} sx={{ maxHeight: "60vh", overflowY: "auto" }}>
              {matches.slice((page - 1) * 10, page * 10).map((request) => {
                const action =
                  tab === "incoming" && !["fulfilled", "rejected"].includes(request.status)
                    ? request.status === "pending"
                      ? "review"
                      : "resume"
                    : "view";
                return (
                  <Button
                    key={request.id}
                    fullWidth
                    aria-pressed={active?.id === request.id}
                    aria-label={`${t(`requests.actions.${action}`)} ${request.id}`}
                    data-request-id={request.id}
                    onClick={() => openRequest(request.id)}
                    sx={{
                      textAlign: "left",
                      justifyContent: "flex-start",
                      textTransform: "none",
                      my: 0.5,
                      p: 1.5,
                      bgcolor: active?.id === request.id ? "action.selected" : undefined,
                    }}
                  >
                    <Stack
                      useFlexGap
                      spacing={0.5}
                      sx={{ minWidth: 0, width: "100%", "& .MuiChip-root": { alignSelf: "flex-start" } }}
                    >
                      <Typography component="span" sx={{ fontWeight: "bold" }}>
                        {request.id} · {request.sample.globalId}
                      </Typography>
                      <RequestStatusChipPrototype status={request.status} />
                      <Typography
                        component="span"
                        variant="body2"
                        sx={{
                          display: "-webkit-box",
                          WebkitLineClamp: 2,
                          WebkitBoxOrient: "vertical",
                          overflow: "hidden",
                        }}
                      >
                        {request.sample.name}
                      </Typography>
                      <Typography component="span" variant="caption">
                        {tab === "incoming" ? request.requester.fullName : request.sample.owner.fullName}
                      </Typography>
                    </Stack>
                  </Button>
                );
              })}
              {matches.length === 0 && (
                <Typography sx={{ py: 2 }}>
                  {sampleFilter || requesterFilter !== null
                    ? "No matching requests. Try different filters."
                    : requestPeriod === "past"
                      ? "No past requests."
                      : requestPeriod === "active"
                        ? "No active requests."
                        : t(`requests.list.empty.${tab}`)}
                </Typography>
              )}
            </Box>
            {matches.length > 10 && (
              <Pagination
                size="small"
                aria-label="Request pages"
                count={Math.ceil(matches.length / 10)}
                page={page}
                onChange={(_, value) => {
                  setPage(value);
                  queueRef.current?.scrollTo(0, 0);
                }}
              />
            )}
          </Paper>
          <Paper
            variant="outlined"
            sx={{
              p: 2,
              minWidth: 0,
              maxHeight: "80vh",
              overflowY: "auto",
              "@container request-workspace (max-width: 900px)": { display: showDetail ? "block" : "none" },
            }}
          >
            {active && state.active ? (
              <>
                <Box
                  sx={{ position: "sticky", top: -16, mt: -2, pt: 2, bgcolor: "background.paper", zIndex: 1, pb: 1 }}
                >
                  <Typography component="h3" variant="h6" tabIndex={-1} ref={headingRef}>
                    {active.id} · {active.sample.globalId}
                  </Typography>
                </Box>
                {!matches.some((r) => r.id === active.id) && (
                  <Alert severity="info">The selected request is outside the current filters.</Alert>
                )}
                <Box>
                  <RequestDetailPrototype
                    compact
                    key={active.id}
                    persona={active.sample.owner.id === me.id ? "owner" : "requester"}
                    request={active}
                    step={state.active.step}
                    operation={active.operation ?? "aliquot"}
                    onOperationChange={(operation) => dispatch({ type: "setOperation", operation })}
                    onOperationPlanChange={(plan) => dispatch({ type: "setOperationPlan", plan })}
                    onApprove={() => dispatch({ type: "approve" })}
                    onReject={(reason) => dispatch({ type: "reject", reason })}
                    onRunOperation={() =>
                      dispatch({
                        type: "runOperation",
                        producedRecordTypeLabel: t("recordTypes.sample.singular"),
                      })
                    }
                    onTransfer={() => dispatch({ type: "transfer", requestId: active.id })}
                    onBack={backToQueue}
                  />
                </Box>
                <Box sx={{ mt: 2 }}>
                  <ActivitySectionPrototype compact title="Request history" entries={events} />
                </Box>
              </>
            ) : (
              <Typography>Select a request to view its details.</Typography>
            )}
          </Paper>
        </Box>
      </HeadingContext>
      <Snackbar
        open={state.toast !== null}
        message={state.toast}
        autoHideDuration={2600}
        onClose={() => dispatch({ type: "clearToast" })}
      />
    </Box>
  );
}

/** Step 2: the list Dana lands on. Pending requests carry a Review call to action. */
export const RequestsListToReview: Story = {
  render: () => <RequestsPagePrototype persona="owner" requests={DEFAULT_REQUESTS} />,
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await expect(await canvas.findByRole("button", { name: "Review REQ-118" })).toBeInTheDocument();
    await expect(canvas.getByRole("button", { name: "View REQ-112" })).toBeInTheDocument();
    await expect(canvas.queryByText("REQ-117")).not.toBeInTheDocument();
  },
};

/** The other tab: what Dana has asked of other owners. */
export const RequestsListMine: Story = {
  render: () => <RequestsPagePrototype persona="owner" requests={DEFAULT_REQUESTS} tab="outgoing" />,
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await expect(await canvas.findByRole("button", { name: "View REQ-117" })).toBeInTheDocument();
    await expect(canvas.getByText("Mei Lin")).toBeInTheDocument();
    await expect(canvas.queryByRole("button", { name: "Approve" })).not.toBeInTheDocument();
  },
};

export const RequestsListEmpty: Story = {
  render: () => <RequestsPagePrototype persona="owner" requests={[]} />,
};

/** Step 3a: approve. Opened from the notification link, Dana approves, runs the aliquot and transfers ownership. */
export const RequestApprove: Story = {
  render: () => <RequestsPagePrototype persona="owner" requests={DEFAULT_REQUESTS} openId="REQ-118" />,
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    const history = within(await canvas.findByRole("list", { name: "Request history" }));
    await expect(history.getAllByRole("listitem")).toHaveLength(1);
    await expect(getComputedStyle(history.getByRole("listitem")).borderBottomWidth).toBe("0px");
    await userEvent.click(await canvas.findByRole("button", { name: "Approve" }));
    const entries = history.getAllByRole("listitem");
    await expect(entries).toHaveLength(2);
    await expect(getComputedStyle(entries[0]).borderBottomWidth).toBe("1px");
    await expect(getComputedStyle(entries[1]).borderBottomWidth).toBe("0px");
    await userEvent.click(canvas.getByRole("radio", { name: /aliquot/i }));
    await userEvent.click(canvas.getByRole("button", { name: "Run operation" }));
    await expect(canvas.getByRole("link", { name: "SA4088" })).toBeInTheDocument();
    await userEvent.click(canvas.getByRole("button", { name: "Transfer ownership" }));
    await expect(canvas.getByRole("button", { name: "Transferred" })).toBeDisabled();
    await expect(canvas.getByRole("heading", { name: "Request history" })).toBeInTheDocument();
    await expect(canvas.getByText(/REQ-118 fulfilled\. SA4088 ownership transferred/)).toBeInTheDocument();
    await expect(canvas.queryByText(/REQ-104 rejected by/)).not.toBeInTheDocument();
    await expect(canvas.queryByRole("button", { name: "Activity" })).not.toBeInTheDocument();
    await userEvent.click(canvas.getByRole("button", { name: "All requests" }));
    await expect(canvas.getByRole("button", { name: "View REQ-118" })).toBeInTheDocument();
  },
};

/** Step 3b: deny. Reject reveals the reason field; confirming records it on the request. */
export const RequestDeny: Story = {
  render: () => <RequestsPagePrototype persona="owner" requests={DEFAULT_REQUESTS} openId="REQ-118" />,
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await userEvent.click(await canvas.findByRole("button", { name: "Reject" }));
    await userEvent.type(canvas.getByLabelText("Reason for rejection"), "Lot B-2411 is reserved for the trial.");
    await userEvent.click(canvas.getByRole("button", { name: "Confirm rejection" }));
    await expect(canvas.getByText(/Lot B-2411 is reserved/)).toBeInTheDocument();
    await userEvent.click(canvas.getByRole("button", { name: "All requests" }));
    await expect(canvas.getByRole("button", { name: "View REQ-118" })).toBeInTheDocument();
  },
};

/** A closed request is read only. */
export const RequestDetailRejected: Story = {
  render: () => <RequestsPagePrototype persona="owner" requests={DEFAULT_REQUESTS} openId="REQ-104" />,
};

/** Approved but not yet run: the owner resumes at the operation step. */
export const RequestDetailApproved: Story = {
  render: () => (
    <RequestsPagePrototype
      persona="owner"
      requests={[{ ...PENDING_REQUEST, status: "approved" }, FULFILLED_REQUEST]}
      openId="REQ-118"
    />
  ),
};

export const RequestDeriveValidation: Story = {
  render: () => <RequestsPagePrototype persona="owner" requests={DEFAULT_REQUESTS} openId="REQ-118" />,
  play: async ({ canvasElement }) => {
    const canvas = within(canvasElement);
    await userEvent.click(await canvas.findByRole("button", { name: "Approve" }));
    await userEvent.click(canvas.getByRole("radio", { name: /derive/i }));
    await expect(canvas.getByRole("button", { name: "Run operation" })).toBeDisabled();
    await userEvent.type(canvas.getByRole("textbox", { name: /Process name/ }), "Dilution");
    const taken = canvas.getByRole("spinbutton", { name: "Amount taken from origin (mL)" });
    await userEvent.clear(taken);
    await userEvent.type(taken, "11");
    await expect(canvas.getByRole("button", { name: "Run operation" })).toBeDisabled();
    await userEvent.clear(taken);
    await userEvent.type(taken, "2");
    await userEvent.click(canvas.getByRole("button", { name: "Run operation" }));
    await expect(canvas.getByRole("link", { name: "SA4088" })).toBeVisible();
    await expect(canvas.getByText(/new sample links to SS4022 with IsDerivedFrom/)).toBeVisible();
    await expect(canvas.getByText(/Consumed 2 mL; 8 mL remained/)).toBeVisible();
  },
};

/** The requester's side of the same page: Rachel follows REQ-118 through to fulfilment. */
export const RequestDetailRequester: Story = {
  render: () => (
    <RequestsPagePrototype persona="requester" requests={DEFAULT_REQUESTS} tab="outgoing" openId="REQ-118" />
  ),
};

/*
 * In context: the same prototypes inside the Inventory page frame that
 * SearchRouter and the IGSN page compose (real Header, sidebar, Main and the
 * two-column Layout2x1). See InventoryChromePrototype.tsx for what is real and what is
 * a stand-in. The app bar is served by MSW, so these stories start a worker.
 */

/** The app bar's avatar spins until the persona has loaded; axe must not run before then. */
async function appBarLoaded(canvasElement: HTMLElement) {
  const canvas = within(canvasElement);
  await waitFor(() => expect(canvas.queryByRole("progressbar")).not.toBeInTheDocument());
  return canvas;
}

/** The sample record as it sits in Inventory: results on the left, the record with its Requests section on the right. */
export const InInventoryRecord: Story = {
  parameters: { layout: "fullscreen" },
  loaders: [() => startApiAs(OWNER)],
  render: () => (
    <InventoryChromePrototype selected="samples" requestsBadge={1}>
      <Layout2x1
        colLeft={<ResultsTablePrototype selectedGlobalId={SAMPLE.globalId} />}
        colRight={
          <SampleRecordPrototype
            persona="owner"
            requestsEnabled
            defaultOperation="aliquot"
            requests={DEFAULT_REQUESTS}
          />
        }
      />
    </InventoryChromePrototype>
  ),
  play: async ({ canvasElement }) => {
    const canvas = await appBarLoaded(canvasElement);
    await expect(await canvas.findByRole("button", { name: "Review" })).toBeInTheDocument();
    await expect(canvas.getByRole("button", { name: /^Requests/ })).toBeInTheDocument();
  },
};

/** The Requests page reached from a notification link or the new sidebar entry. */
export const InInventoryRequestsPage: Story = {
  parameters: { layout: "fullscreen" },
  loaders: [() => startApiAs(OWNER)],
  render: () => (
    <InventoryChromePrototype selected="requests" requestsBadge={1}>
      <RequestsPagePrototype persona="owner" requests={DEFAULT_REQUESTS} />
    </InventoryChromePrototype>
  ),
  play: async ({ canvasElement }) => {
    const canvas = await appBarLoaded(canvasElement);
    await expect(await canvas.findByRole("button", { name: "Review REQ-118" })).toBeInTheDocument();
  },
};

/** A single request's page inside the same frame, as the notification link opens it. */
export const InInventoryRequestDetail: Story = {
  parameters: { layout: "fullscreen" },
  loaders: [() => startApiAs(OWNER)],
  render: () => (
    <InventoryChromePrototype selected="requests" requestsBadge={1}>
      <RequestsPagePrototype persona="owner" requests={DEFAULT_REQUESTS} openId="REQ-118" />
    </InventoryChromePrototype>
  ),
  play: async ({ canvasElement }) => {
    const canvas = await appBarLoaded(canvasElement);
    await expect(await canvas.findByRole("button", { name: "Approve" })).toBeInTheDocument();
  },
};

/** Rachel's view of Inventory: she is signed in, the sample is requestable, and she can ask for material. */
export const InInventoryRequester: Story = {
  parameters: { layout: "fullscreen" },
  loaders: [() => startApiAs(RACHEL)],
  render: () => (
    <InventoryChromePrototype selected="samples">
      <Layout2x1
        colLeft={<ResultsTablePrototype selectedGlobalId={SAMPLE.globalId} />}
        colRight={
          <SampleRecordPrototype
            persona="requester"
            requestsEnabled
            defaultOperation="aliquot"
            requests={[FULFILLED_REQUEST, REJECTED_REQUEST]}
          />
        }
      />
    </InventoryChromePrototype>
  ),
  play: async ({ canvasElement }) => {
    const canvas = await appBarLoaded(canvasElement);
    await expect(await canvas.findByRole("button", { name: "Send request" })).toBeInTheDocument();
  },
};
