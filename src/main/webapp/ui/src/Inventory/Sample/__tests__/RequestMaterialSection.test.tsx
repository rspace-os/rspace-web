import { ThemeProvider } from "@mui/material/styles";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { runInAction } from "mobx";
import { HttpResponse, http } from "msw";
import type React from "react";
import { beforeEach, describe, expect, test } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { server } from "@/__tests__/mswServer";
import { DeploymentPropertyContext } from "@/hooks/api/useDeploymentProperty";
import { personAttrs } from "@/stores/models/__tests__/PersonModel/mocking";
import { makeMockSample } from "@/stores/models/__tests__/SampleModel/mocking";
import PersonModel from "@/stores/models/PersonModel";
import type SampleModel from "@/stores/models/SampleModel";
import getRootStore from "@/stores/stores/getRootStore";
import materialTheme from "@/theme";
import { RequestableSwitch, RequestMaterialSection } from "../Form";

const SAMPLE_REQUESTS_URL = "/api/inventory/v1/sampleRequests";

const OWNER_USERNAME = "sample-owner";
const REQUESTER_USERNAME = "sample-requester";

function setCurrentUser(username: string) {
  const { peopleStore } = getRootStore();
  runInAction(() => {
    peopleStore.currentUser = new PersonModel(personAttrs({ username }));
  });
}

function deploymentProperties(available: boolean) {
  return new Map<string, unknown>([["inventory.sampleRequests.available", available ? "ALLOWED" : "DENIED"]]);
}

function renderWithProviders(ui: React.ReactNode, { available = true }: { available?: boolean } = {}) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={materialTheme}>
        <DeploymentPropertyContext.Provider value={deploymentProperties(available)}>
          {ui}
        </DeploymentPropertyContext.Provider>
      </ThemeProvider>
    </QueryClientProvider>,
  );
}

function makeOwnedSample(attrs: Partial<Parameters<typeof makeMockSample>[0]> = {}): SampleModel {
  return makeMockSample({
    owner: personAttrs({ username: OWNER_USERNAME }),
    requestable: false,
    // RequestableSwitch/RequestMaterialSection key off canTransfer (CHANGE_OWNER in
    // permittedActions), matching the backend's own check for toggling requestable
    // (SampleApiManagerImpl.assertRequestableChangeAllowed -> canUserTransferInventoryRecord) -
    // the owner, or a PI/Lab Admin of the owner's group, not merely username equality with the
    // owner. Defaults to the plain non-owner, non-PI set; owner/PI-simulating tests below add
    // CHANGE_OWNER back in, the same way the real API would for those viewers specifically.
    permittedActions: ["READ", "UPDATE"],
    ...attrs,
  });
}

beforeEach(() => {
  // ApiService's query/post/update calls all wait on `!authStore.isSynchronizing` before firing
  // (see ApiServiceBase) - true by default on the real singleton, which would otherwise hang every
  // request below forever.
  const { authStore } = getRootStore();
  runInAction(() => {
    authStore.isSynchronizing = false;
  });
  server.use(http.get(SAMPLE_REQUESTS_URL, () => HttpResponse.json({ requests: [] })));
});

describe("RequestableSwitch", () => {
  test("renders nothing when sample requests are not available.", () => {
    setCurrentUser(OWNER_USERNAME);
    const sample = makeOwnedSample();
    const { container } = renderWithProviders(<RequestableSwitch activeResult={sample} />, { available: false });
    expect(container).toBeEmptyDOMElement();
  });

  test("renders nothing when the current user is not the sample's owner.", () => {
    setCurrentUser(REQUESTER_USERNAME);
    const sample = makeOwnedSample();
    const { container } = renderWithProviders(<RequestableSwitch activeResult={sample} />);
    expect(container).toBeEmptyDOMElement();
  });

  test("the owner can toggle requestable on and off, and the switch has no axe violations.", async () => {
    const user = userEvent.setup();
    setCurrentUser(OWNER_USERNAME);
    const sample = makeOwnedSample({ requestable: false, permittedActions: ["READ", "UPDATE", "CHANGE_OWNER"] });
    sample.setEditable(new Set(["requestable"]), true);
    const { container } = renderWithProviders(<RequestableSwitch activeResult={sample} />);
    await expectAccessible(container);

    expect(screen.getByRole("link", { name: "inventory:requestsManagement.helpTitle" })).toHaveAttribute(
      "href",
      expect.stringContaining("common:help.sampleRequests"),
    );

    const toggle = screen.getByRole("switch", { name: "inventory:sample.requestsSection.switchLabel" });
    expect(toggle).not.toBeChecked();

    await user.click(toggle);
    expect(sample.requestable).toBe(true);

    await user.click(toggle);
    expect(sample.requestable).toBe(false);
  });

  test("a PI of the owner's group can also toggle requestable, not just the owner.", async () => {
    const user = userEvent.setup();
    const GROUP_PI_USERNAME = "group-pi";
    setCurrentUser(GROUP_PI_USERNAME);
    // Not the owner by username - the API determines CHANGE_OWNER access for a PI or Lab Admin of
    // the owner's group the same way it does for the owner, so that's what this simulates (see
    // InventoryPermissionUtils.canUserTransferInventoryRecord/isPiOrAdminOfRecordOwnerGroup).
    const sample = makeOwnedSample({ requestable: false, permittedActions: ["READ", "UPDATE", "CHANGE_OWNER"] });
    sample.setEditable(new Set(["requestable"]), true);
    renderWithProviders(<RequestableSwitch activeResult={sample} />);

    const toggle = screen.getByRole("switch", { name: "inventory:sample.requestsSection.switchLabel" });
    expect(toggle).not.toBeChecked();

    await user.click(toggle);
    expect(sample.requestable).toBe(true);
  });
});

describe("RequestMaterialSection", () => {
  test("renders nothing when sample requests are not available.", () => {
    setCurrentUser(REQUESTER_USERNAME);
    const sample = makeOwnedSample();
    const { container } = renderWithProviders(<RequestMaterialSection activeResult={sample} />, { available: false });
    expect(container).toBeEmptyDOMElement();
  });

  test("renders nothing for the sample's own owner.", () => {
    setCurrentUser(OWNER_USERNAME);
    const sample = makeOwnedSample({ permittedActions: ["READ", "UPDATE", "CHANGE_OWNER"] });
    const { container } = renderWithProviders(<RequestMaterialSection activeResult={sample} />);
    expect(container).toBeEmptyDOMElement();
  });

  test("renders nothing for a PI of the owner's group either, since they see RequestableSwitch instead.", () => {
    setCurrentUser("group-pi");
    const sample = makeOwnedSample({ permittedActions: ["READ", "UPDATE", "CHANGE_OWNER"] });
    const { container } = renderWithProviders(<RequestMaterialSection activeResult={sample} />);
    expect(container).toBeEmptyDOMElement();
  });

  test("shows the not-available message when the sample isn't marked requestable.", async () => {
    setCurrentUser(REQUESTER_USERNAME);
    const sample = makeOwnedSample({ requestable: false });
    const { container } = renderWithProviders(<RequestMaterialSection activeResult={sample} />);
    await waitFor(() =>
      expect(screen.getByText("inventory:sample.requestMaterialSection.notAvailableHeader")).toBeInTheDocument(),
    );
    await expectAccessible(container);
    expect(screen.getByRole("link", { name: "inventory:requestsManagement.helpTitle" })).toHaveAttribute(
      "href",
      expect.stringContaining("common:help.sampleRequests"),
    );
  });

  test("a non-owner can send a request for a requestable sample, and the dialog closes on success.", async () => {
    const user = userEvent.setup();
    setCurrentUser(REQUESTER_USERNAME);
    const sample = makeOwnedSample({ requestable: true, globalId: "SA1" });
    let postedBody: unknown = null;
    server.use(
      http.post(SAMPLE_REQUESTS_URL, async ({ request }) => {
        postedBody = await request.json();
        return HttpResponse.json({ id: 1, status: "PENDING", created: "2026-01-01T00:00:00Z" });
      }),
    );
    const { container } = renderWithProviders(<RequestMaterialSection activeResult={sample} />);

    const requestButton = await screen.findByRole("button", {
      name: "inventory:sample.requestMaterialSection.requestSampleButton",
    });
    await expectAccessible(container);
    expect(screen.getByRole("link", { name: "inventory:requestsManagement.helpTitle" })).toHaveAttribute(
      "href",
      expect.stringContaining("common:help.sampleRequests"),
    );
    await user.click(requestButton);

    const dialog = await screen.findByRole("dialog");
    await user.type(
      screen.getByPlaceholderText("inventory:sample.requestMaterialSection.whatYouNeedHelperText"),
      "Need 5ml please",
    );
    await user.click(screen.getByRole("button", { name: "inventory:sample.requestMaterialSection.sendRequestButton" }));

    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(dialog).not.toBeInTheDocument();
    expect(postedBody).toEqual({ sampleGlobalId: "SA1", note: "Need 5ml please" });
  });

  test("shows a pending request's status with a Cancel button, which cancels it.", async () => {
    const user = userEvent.setup();
    setCurrentUser(REQUESTER_USERNAME);
    const sample = makeOwnedSample({ requestable: true, globalId: "SA1", id: 7 });
    let cancelledStatus: unknown = null;
    server.use(
      http.get(SAMPLE_REQUESTS_URL, () =>
        HttpResponse.json({ requests: [{ id: 42, status: "PENDING", created: "2026-01-01T00:00:00Z" }] }),
      ),
      http.put(`${SAMPLE_REQUESTS_URL}/42/status`, async ({ request }) => {
        cancelledStatus = (await request.json()) as { status: string };
        return HttpResponse.json({ status: "CANCELLED" });
      }),
    );
    const { container } = renderWithProviders(<RequestMaterialSection activeResult={sample} />);

    const cancelButton = await screen.findByRole("button", {
      name: "common:actions.cancel",
    });
    await expectAccessible(container);
    await user.click(cancelButton);

    await waitFor(() => expect(cancelledStatus).toEqual({ status: "CANCELLED" }));
  });
});
