import { act, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { server } from "@/__tests__/mswServer";
import i18n from "@/modules/common/i18n";
import { sampleAttrs } from "@/stores/models/__tests__/SampleModel/mocking";
import { subsampleAttrs } from "@/stores/models/__tests__/SubSampleModel/mocking";
import {
  apiUpdate,
  baseRequest,
  currentUser,
  REQUESTER,
  renderPanel,
  resetRequestDetailPanelFixtures,
  waitForInitialFetches,
} from "./RequestDetailPanel.fixtures";

// Duplicates RequestDetailPanel.fixtures.tsx's own identical mock of the same module: this file's
// sampleAttrs/subsampleAttrs imports above construct real SubSampleModel instances (for the
// Operations Wizard launch tests below), whose transitive imports would otherwise resolve the real
// getRootStore - vi.mock calls only hoist within the file that literally writes them, so without
// its own copy here, whether this file's imports or the fixtures' mock wins would depend on plain
// import order (alphabetised by the formatter) rather than being guaranteed safe either way.
vi.mock("@/stores/stores/getRootStore", () => ({
  __esModule: true,
  default: () => ({
    authStore: { isSynchronizing: false },
    unitStore: { getUnit: () => ({ label: "ml" }) },
  }),
}));

const SUBSAMPLE_URL = "/api/inventory/v1/subSamples/:id";

// The real hook drives a whole lock-acquisition/wizard-dialog flow unrelated to what this suite is
// verifying - that the panel asks for the right origin and the right excluded operations. A stub
// records both and exposes a fake "wizard" marker so a test can assert the launch happened.
const launchOperationWizard = vi.fn().mockResolvedValue(true);
let lastWizardOrigins: ReadonlyArray<{ id: number | null; globalId: string | null }> = [];
let lastExcludedOperationKeys: ReadonlySet<string> | undefined;
let lastOnPerformed: ((sample: { id: number; globalId: string; name: string } | null) => void) | undefined;
vi.mock("../../components/Operations/useOperationWizardLauncher", () => ({
  useOperationWizardLauncher: (
    origins: ReadonlyArray<{ id: number | null; globalId: string | null }>,
    options: {
      onPerformed?: (sample: { id: number; globalId: string; name: string } | null) => void;
      excludedOperationKeys?: ReadonlySet<string>;
    } = {},
  ) => {
    lastWizardOrigins = origins;
    lastExcludedOperationKeys = options.excludedOperationKeys;
    lastOnPerformed = options.onPerformed;
    return {
      launch: launchOperationWizard,
      wizard: origins.length > 0 ? <div data-testid="mock-operation-wizard" /> : null,
    };
  },
}));

beforeEach(() => {
  resetRequestDetailPanelFixtures();
  launchOperationWizard.mockClear();
  lastWizardOrigins = [];
});

// Approve/reject/fulfil/prepare/transfer/cancel each have their own sibling test file; this one
// covers the panel's own layout and composition - the header, the collapsible sections' shared
// chrome, and which top-level panels appear for an owner versus a non-owner.
describe("RequestDetailPanel", () => {
  it("shows a placeholder when no request is selected", () => {
    renderPanel(null);
    expect(screen.getByText("inventory:requestsManagement.noSelection")).toBeInTheDocument();
  });

  it("renders the request header, requester, and requested sample for the sample owner", async () => {
    const { container } = renderPanel(baseRequest());
    await waitForInitialFetches();

    expect(screen.getByText("inventory:requestsManagement.detail.title")).toBeInTheDocument();
    expect(screen.getByText("Rita Requester")).toBeInTheDocument();
    expect(screen.getAllByText("inventory:requestsManagement.status.pending").length).toBeGreaterThan(0);
    // The owner-only Actions and Sample Locations sections should both be present.
    expect(screen.getByRole("button", { name: "inventory:requestsManagement.detail.approveButton" })).toBeEnabled();
    expect(screen.getByRole("button", { name: "inventory:requestsManagement.detail.rejectButton" })).toBeEnabled();
    await expectAccessible(container);
  });

  it("marks a collapsible section header's toggle button with aria-expanded, reflecting its live state", async () => {
    const user = userEvent.setup();
    renderPanel(baseRequest());
    await waitForInitialFetches();

    // All four section headers (Details, Approval Result, Sample Locations, Request History)
    // share the same markup, so this one stands in for the rest.
    const detailsHeading = screen.getByText("inventory:requestsManagement.detail.sections.details");
    const detailsHeader = detailsHeading.closest("div");
    if (!detailsHeader) throw new Error("Could not find the Details section's header container");
    const toggleButton = within(detailsHeader).getByRole("button");
    expect(toggleButton).toHaveAttribute("aria-expanded", "true");

    await user.click(toggleButton);
    expect(toggleButton).toHaveAttribute("aria-expanded", "false");
  });

  it("gives each collapsible section's toggle button its own aria-controls, each pointing to a real element", async () => {
    renderPanel(baseRequest());
    await waitForInitialFetches();

    const toggleButtons = screen.getAllByRole("button").filter((button) => button.hasAttribute("aria-controls"));
    // Details, Approval Result, Sample Locations (owner-only - the default signed-in user here is
    // the owner), and Request History.
    expect(toggleButtons).toHaveLength(4);

    const controlsIds = toggleButtons.map((button) => button.getAttribute("aria-controls"));
    // The bug this guards against: aria-controls being entirely absent, or every button pointing
    // at the same id - either way leaving a screen reader user with no way to tell which content
    // region a given "Collapse section"/"Expand section" button actually affects.
    expect(new Set(controlsIds).size).toBe(4);
    for (const id of controlsIds) {
      expect(document.getElementById(id as string)).not.toBeNull();
    }
  });

  it("hides owner-only actions and shows a Cancel option for a non-owner viewing a pending request", async () => {
    currentUser.value = REQUESTER;
    renderPanel(baseRequest());
    await waitForInitialFetches();

    expect(screen.queryByRole("button", { name: "inventory:requestsManagement.detail.approveButton" })).toBeNull();
    expect(screen.getByRole("button", { name: "common:actions.cancel" })).toBeInTheDocument();
  });

  // What proceeding with the "wizard" radio (ChooseMethodDialog's own sibling test file covers the
  // dialog itself, not what proceeding with it actually launches) does to the rest of the panel.
  describe("Operations Wizard launch", () => {
    async function proceedWithWizard(user: ReturnType<typeof userEvent.setup>) {
      renderPanel(baseRequest({ status: "APPROVED" }));
      await waitForInitialFetches();

      await user.click(screen.getByRole("button", { name: "mock-select-subsample" }));
      await user.click(screen.getByRole("button", { name: "inventory:requestsManagement.detail.prepareSampleButton" }));
      const chooseDialog = screen.getByRole("dialog", {
        name: "inventory:requestsManagement.detail.chooseMethodDialog.title",
      });
      await user.click(
        within(chooseDialog).getByRole("radio", {
          name: "inventory:requestsManagement.detail.chooseMethodDialog.wizardOption",
        }),
      );
      await user.click(
        within(chooseDialog).getByRole("button", {
          name: "inventory:requestsManagement.detail.chooseMethodDialog.proceedButton",
        }),
      );
    }

    it("launches the Operations Wizard with the selected subsample as its origin when 'Create a new sample' is proceeded", async () => {
      const user = userEvent.setup();
      server.use(
        http.get(SUBSAMPLE_URL, () =>
          HttpResponse.json(subsampleAttrs({ id: 42, globalId: "SS42", sample: sampleAttrs() })),
        ),
      );

      await proceedWithWizard(user);

      expect(await screen.findByTestId("mock-operation-wizard")).toBeInTheDocument();
      await waitFor(() => expect(launchOperationWizard).toHaveBeenCalled());
      expect(lastWizardOrigins).toHaveLength(1);
      expect(lastWizardOrigins[0]).toMatchObject({ id: 42, globalId: "SS42" });
      // Destroying the origin, or pooling (which needs multiple), has no sensible place in a flow
      // that only ever offers the single subsample selected in Sample Locations.
      expect(lastExcludedOperationKeys).toEqual(new Set(["destroy", "pool"]));
    });

    it("opens the Transfer Ownership dialog for the sample the Operations Wizard just created, once it reports one performed", async () => {
      // cimode rendering (see src/__tests__/setup.ts) shows only the bare translation key, never
      // the interpolated copy - asserting on rendered text alone can't tell the new sample's name
      // apart from the originally requested one, so this spies on the real t() call instead.
      const tSpy = vi.spyOn(i18n, "t");
      const user = userEvent.setup();
      server.use(
        http.get(SUBSAMPLE_URL, () =>
          HttpResponse.json(subsampleAttrs({ id: 42, globalId: "SS42", sample: sampleAttrs() })),
        ),
      );

      await proceedWithWizard(user);
      await screen.findByTestId("mock-operation-wizard");

      // Simulates the real Operations Wizard calling its onPerformed prop once an operation (e.g.
      // Split or Create) completes and hands back the sample it created - not driven through the
      // mocked wizard div itself, which has no UI of its own here.
      act(() => {
        lastOnPerformed?.({ id: 999, globalId: "SA999", name: "New Sample From Wizard" });
      });

      await screen.findByRole("dialog", { name: "inventory:requestsManagement.detail.transferDialog.heading" });
      // Not the originally requested sample's name ("Sample Fifty Five") - the Operations Wizard
      // created a new sample, and that is what actually gets transferred to the requester.
      expect(tSpy).toHaveBeenCalledWith(
        "requestsManagement.detail.transferDialog.heading",
        expect.objectContaining({ sampleName: "New Sample From Wizard" }),
      );
      tSpy.mockRestore();
    });

    it("transfers the sample the Operations Wizard created, not the originally requested one", async () => {
      const user = userEvent.setup();
      server.use(
        http.get(SUBSAMPLE_URL, () =>
          HttpResponse.json(subsampleAttrs({ id: 42, globalId: "SS42", sample: sampleAttrs() })),
        ),
      );

      await proceedWithWizard(user);
      await screen.findByTestId("mock-operation-wizard");
      act(() => {
        lastOnPerformed?.({ id: 999, globalId: "SA999", name: "New Sample From Wizard" });
      });

      const transferDialog = await screen.findByRole("dialog", {
        name: "inventory:requestsManagement.detail.transferDialog.heading",
      });
      await user.click(within(transferDialog).getByRole("button", { name: "common:actions.transfer" }));

      // Not the originally requested sample's global id ("SA55") - the Operations Wizard created a
      // new sample, and that is the one the backend must actually hand to the requester.
      await waitFor(() =>
        expect(apiUpdate).toHaveBeenCalledWith("sampleRequests", "101/actions/fulfilByTransfer", {
          sampleToTransferGlobalId: "SA999",
        }),
      );
    });
  });
});
