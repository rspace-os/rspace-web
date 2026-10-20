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
    // RequestableSwitch/RequestMaterialSection key off canSetRequestable, a getter backed by
    // CHANGE_REQUESTABLE in permittedActions - the API's own name for the exact check the backend
    // enforces before actually allowing requestable to change
    // (InventoryPermissionUtils.canUserChangeRequestable: the owner, or a PI/Lab Admin of the
    // owner's group, who can both edit and transfer the sample) - not merely username equality
    // with the owner, not any other group member with mere edit access, and not a sysadmin's
    // admin-override CHANGE_OWNER ability either (see the sysadmin-specific tests below, which
    // simulate that by setting permittedActions' CHANGE_OWNER without CHANGE_REQUESTABLE, the same
    // combination the real API would send for one). The inherited mocking default
    // (["READ", "UPDATE", "CHANGE_OWNER"]) has no CHANGE_REQUESTABLE, so canSetRequestable is
    // false unless a test's own permittedActions override adds it back in.
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
    const sample = makeOwnedSample({
      requestable: false,
      permittedActions: ["READ", "UPDATE", "CHANGE_OWNER", "CHANGE_REQUESTABLE"],
    });
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
    // Not the owner by username - canUserChangeRequestable (and so CHANGE_REQUESTABLE) is true
    // for a PI or Lab Admin of the owner's group the same way it is for the owner, so that's what
    // this simulates (see InventoryPermissionUtils.isPiOrAdminOfRecordOwnerGroup).
    const sample = makeOwnedSample({
      requestable: false,
      permittedActions: ["READ", "UPDATE", "CHANGE_OWNER", "CHANGE_REQUESTABLE"],
    });
    sample.setEditable(new Set(["requestable"]), true);
    renderWithProviders(<RequestableSwitch activeResult={sample} />);

    const toggle = screen.getByRole("switch", { name: "inventory:sample.requestsSection.switchLabel" });
    expect(toggle).not.toBeChecked();

    await user.click(toggle);
    expect(sample.requestable).toBe(true);
  });

  test("does not show the switch for a plain group member who can edit the sample but not transfer it.", () => {
    setCurrentUser("group-member");
    // canSetRequestable stays false here even though this viewer can edit (UPDATE) the sample -
    // exactly the gap between canEdit and canUserChangeRequestable that a group member (sharing
    // access under the default sharing mode, but neither owner nor PI/Lab Admin) occupies. This is
    // the bug report this test guards against: the frontend previously reused canEdit for this
    // switch, wrongly letting such a member manage requestable.
    const sample = makeOwnedSample({
      requestable: false,
      permittedActions: ["READ", "UPDATE"],
    });
    const { container } = renderWithProviders(<RequestableSwitch activeResult={sample} />);
    expect(container).toBeEmptyDOMElement();
  });

  test("does not show the switch for a sysadmin who can transfer ownership but cannot otherwise edit the sample.", () => {
    setCurrentUser("sysadmin-user");
    // CHANGE_OWNER alone (no UPDATE, no CHANGE_REQUESTABLE) is exactly what a sysadmin's
    // admin-override transfer ability looks like in permittedActions (see InventoryPermissionUtils.
    // setPermissionsInApiInventoryRecord's `|| user.hasSysadminRole()` branch, which only ever adds
    // CHANGE_OWNER, never CHANGE_REQUESTABLE, for a sysadmin who isn't otherwise an editor).
    const sample = makeOwnedSample({
      requestable: false,
      permittedActions: ["READ", "CHANGE_OWNER"],
    });
    const { container } = renderWithProviders(<RequestableSwitch activeResult={sample} />);
    expect(container).toBeEmptyDOMElement();
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
    const sample = makeOwnedSample({
      permittedActions: ["READ", "UPDATE", "CHANGE_OWNER", "CHANGE_REQUESTABLE"],
    });
    const { container } = renderWithProviders(<RequestMaterialSection activeResult={sample} />);
    expect(container).toBeEmptyDOMElement();
  });

  test("renders nothing for a PI of the owner's group either, since they see RequestableSwitch instead.", () => {
    setCurrentUser("group-pi");
    const sample = makeOwnedSample({
      permittedActions: ["READ", "UPDATE", "CHANGE_OWNER", "CHANGE_REQUESTABLE"],
    });
    const { container } = renderWithProviders(<RequestMaterialSection activeResult={sample} />);
    expect(container).toBeEmptyDOMElement();
  });

  test("shows the request form, not nothing, for a plain group member who can edit the sample but not transfer it.", async () => {
    setCurrentUser("group-member");
    // Mirrors the equivalent RequestableSwitch test above: being able to edit (UPDATE) the sample
    // isn't the same as being able to manage it directly (canSetRequestable) - this viewer is a
    // genuine candidate to request it from, same as any other non-manager.
    const sample = makeOwnedSample({
      requestable: true,
      permittedActions: ["READ", "UPDATE"],
    });
    const { container } = renderWithProviders(<RequestMaterialSection activeResult={sample} />);
    await waitFor(() =>
      expect(
        screen.getByRole("button", { name: "inventory:sample.requestMaterialSection.requestSampleButton" }),
      ).toBeInTheDocument(),
    );
    expect(container).not.toBeEmptyDOMElement();
  });

  test("shows the request form, not nothing, for a sysadmin who can transfer ownership but cannot otherwise edit the sample.", async () => {
    setCurrentUser("sysadmin-user");
    // Mirrors the equivalent RequestableSwitch test above: CHANGE_OWNER alone (no UPDATE, no
    // CHANGE_REQUESTABLE) must not be treated as "can manage this sample directly" - that
    // admin-only ability leaves this viewer no differently placed than any other non-manager, so
    // they see the request form.
    const sample = makeOwnedSample({
      requestable: true,
      permittedActions: ["READ", "CHANGE_OWNER"],
    });
    const { container } = renderWithProviders(<RequestMaterialSection activeResult={sample} />);
    await waitFor(() =>
      expect(
        screen.getByRole("button", { name: "inventory:sample.requestMaterialSection.requestSampleButton" }),
      ).toBeInTheDocument(),
    );
    expect(container).not.toBeEmptyDOMElement();
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

  test("keeps the Send Request button disabled until the request text box has content.", async () => {
    const user = userEvent.setup();
    setCurrentUser(REQUESTER_USERNAME);
    const sample = makeOwnedSample({ requestable: true, globalId: "SA1" });
    renderWithProviders(<RequestMaterialSection activeResult={sample} />);

    const requestButton = await screen.findByRole("button", {
      name: "inventory:sample.requestMaterialSection.requestSampleButton",
    });
    await user.click(requestButton);

    const textBox = screen.getByPlaceholderText("inventory:sample.requestMaterialSection.whatYouNeedHelperText");
    const sendButton = screen.getByRole("button", {
      name: "inventory:sample.requestMaterialSection.sendRequestButton",
    });
    expect(sendButton).toBeDisabled();

    await user.type(textBox, "   ");
    // Whitespace-only text is not a genuine request description either.
    expect(sendButton).toBeDisabled();

    await user.type(textBox, "Need 5ml please");
    expect(sendButton).toBeEnabled();

    await user.clear(textBox);
    expect(sendButton).toBeDisabled();
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
