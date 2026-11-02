import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { beforeEach, describe, expect, it } from "vitest";
import { silenceConsole } from "@/__tests__/helpers/silenceConsole";
import { server } from "@/__tests__/mswServer";
import {
  addAlert,
  apiGet,
  apiQuery,
  apiUpdate,
  baseRequest,
  deploymentProperties,
  failFulfilByTransfer,
  OWNER,
  REQUESTER,
  renderPanel,
  resetRequestDetailPanelFixtures,
  SAMPLE_REQUEST_DETAIL_URL,
  SAMPLE_REQUEST_FULFIL_BY_TRANSFER_URL,
  SAMPLE_REQUESTS_URL,
  SAMPLE_URL,
  waitForInitialFetches,
} from "./RequestDetailPanel.fixtures";

beforeEach(() => {
  resetRequestDetailPanelFixtures();
});

describe("RequestDetailPanel transfer", () => {
  it("prepares an approved request via the Choose Sample to Prepare dialog and transfers it directly", async () => {
    const user = userEvent.setup();
    // Neither property gates this route toward skipping the dialog.
    deploymentProperties["inventory.sampleRequests.available"] = "DENIED";
    deploymentProperties["inventory.operations.available"] = "ALLOWED";
    renderPanel(baseRequest({ status: "APPROVED" }));
    await waitForInitialFetches();

    await user.click(screen.getByRole("button", { name: "mock-select-subsample" }));
    const prepareButton = screen.getByRole("button", {
      name: "inventory:requestsManagement.detail.prepareSampleButton",
    });
    expect(prepareButton).toBeEnabled();
    await user.click(prepareButton);

    const chooseDialog = screen.getByRole("dialog", {
      name: "inventory:requestsManagement.detail.chooseMethodDialog.title",
    });
    await user.click(
      within(chooseDialog).getByRole("radio", {
        name: "inventory:requestsManagement.detail.chooseMethodDialog.transferOption",
      }),
    );
    await user.click(
      within(chooseDialog).getByRole("button", {
        name: "inventory:requestsManagement.detail.chooseMethodDialog.proceedButton",
      }),
    );

    const transferDialog = await screen.findByRole("dialog", {
      name: "inventory:requestsManagement.detail.transferDialog.heading",
    });
    expect(within(transferDialog).getByText(`${REQUESTER.firstName} ${REQUESTER.lastName}`)).toBeInTheDocument();

    await user.click(within(transferDialog).getByRole("button", { name: "common:actions.transfer" }));

    await waitFor(() =>
      expect(apiUpdate).toHaveBeenCalledWith("sampleRequests", "101/actions/fulfilByTransfer", {
        sampleToTransferGlobalId: undefined,
      }),
    );
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
    expect(addAlert).toHaveBeenCalledWith(expect.objectContaining({ variant: "success" }));
    // fulfilByTransfer (../mutations.ts) invalidates every sampleRequests query once it succeeds,
    // which both refreshes this panel's own detail query and leaves the status chip showing the
    // mutation's own authoritative response.
    await waitFor(() =>
      expect(screen.getAllByText("inventory:requestsManagement.status.fulfilled").length).toBeGreaterThan(0),
    );
  });

  it("does not transfer ownership, closes the dialog, and shows the cancelled-specific error when the requester cancelled the request first", async () => {
    const restoreConsole = silenceConsole(["error"], ["Failed to transfer sample ownership: request was cancelled"]);
    const user = userEvent.setup();
    // Neither property gates this route toward skipping the dialog.
    deploymentProperties["inventory.sampleRequests.available"] = "DENIED";
    deploymentProperties["inventory.operations.available"] = "ALLOWED";
    // The error itself carries no distinct code for "the requester cancelled it first" (see
    // RequestDetailPanel.tsx's submitTransfer) - any failure does, so long as the request has
    // actually moved to CANCELLED by the time onError re-fetches it below.
    failFulfilByTransfer(422, "A sample request cannot move from CANCELLED to FULFILLED.");
    // The request is still APPROVED when the panel mounts (matching the prop below) - that's what
    // makes the Prepare Sample button clickable in the first place - but has been CANCELLED by the
    // time submitTransfer's onError re-fetches it, simulating someone else cancelling it in
    // between. The panel should pick that up rather than carry on showing the stale APPROVED
    // status this attempt started from.
    let sampleRequestsGetCalls = 0;
    server.use(
      http.get(SAMPLE_REQUEST_DETAIL_URL, ({ params }) => {
        apiGet("sampleRequests", Number(params.id));
        sampleRequestsGetCalls += 1;
        return HttpResponse.json({
          status: sampleRequestsGetCalls === 1 ? "APPROVED" : "CANCELLED",
          statusChanges: [],
          sample: { owner: { firstName: OWNER.firstName, lastName: OWNER.lastName } },
        });
      }),
    );
    renderPanel(baseRequest({ status: "APPROVED" }));
    await waitForInitialFetches();

    await user.click(screen.getByRole("button", { name: "mock-select-subsample" }));
    await user.click(screen.getByRole("button", { name: "inventory:requestsManagement.detail.prepareSampleButton" }));

    const chooseDialog = screen.getByRole("dialog", {
      name: "inventory:requestsManagement.detail.chooseMethodDialog.title",
    });
    await user.click(
      within(chooseDialog).getByRole("radio", {
        name: "inventory:requestsManagement.detail.chooseMethodDialog.transferOption",
      }),
    );
    await user.click(
      within(chooseDialog).getByRole("button", {
        name: "inventory:requestsManagement.detail.chooseMethodDialog.proceedButton",
      }),
    );

    const transferDialog = await screen.findByRole("dialog", {
      name: "inventory:requestsManagement.detail.transferDialog.heading",
    });
    await user.click(within(transferDialog).getByRole("button", { name: "common:actions.transfer" }));

    await waitFor(() =>
      expect(apiUpdate).toHaveBeenCalledWith("sampleRequests", "101/actions/fulfilByTransfer", expect.anything()),
    );
    await waitFor(() =>
      expect(
        screen.queryByRole("dialog", { name: "inventory:requestsManagement.detail.transferDialog.heading" }),
      ).toBeNull(),
    );
    expect(addAlert).not.toHaveBeenCalledWith(expect.objectContaining({ variant: "success" }));
    expect(addAlert).toHaveBeenCalledWith(
      expect.objectContaining({
        variant: "error",
        message: "inventory:requestsManagement.detail.transferCancelledErrorMessage",
      }),
    );
    // Refreshed from the backend rather than left showing the stale APPROVED status this attempt
    // started from. The left-hand Requests list and the Sidebar's pending-count badge both share
    // the same invalidation (sampleRequestsQueryKeys.all in ../mutations.ts), so they'd pick this
    // up too without needing a test of their own to prove it here.
    await waitFor(() =>
      expect(screen.getAllByText("inventory:requestsManagement.status.cancelled").length).toBeGreaterThan(0),
    );
    restoreConsole();
  });

  it("closes the dialog and shows the generic action error when the transfer fails for a reason other than cancellation", async () => {
    const restoreConsole = silenceConsole(["error"], ["inventory:requestsManagement.detail.actionErrors.transfer"]);
    const user = userEvent.setup();
    deploymentProperties["inventory.sampleRequests.available"] = "DENIED";
    deploymentProperties["inventory.operations.available"] = "ALLOWED";
    failFulfilByTransfer(422, "You are not permitted to make this change to the request.");
    // Unlike the previous test, the request is still APPROVED once submitTransfer's onError
    // re-fetches it - nobody cancelled it, so the generic error should show instead.
    server.use(
      http.get(SAMPLE_REQUEST_DETAIL_URL, ({ params }) => {
        apiGet("sampleRequests", Number(params.id));
        return HttpResponse.json({
          status: "APPROVED",
          statusChanges: [],
          sample: { owner: { firstName: OWNER.firstName, lastName: OWNER.lastName } },
        });
      }),
    );
    renderPanel(baseRequest({ status: "APPROVED" }));
    await waitForInitialFetches();

    await user.click(screen.getByRole("button", { name: "mock-select-subsample" }));
    await user.click(screen.getByRole("button", { name: "inventory:requestsManagement.detail.prepareSampleButton" }));
    const chooseDialog = screen.getByRole("dialog", {
      name: "inventory:requestsManagement.detail.chooseMethodDialog.title",
    });
    await user.click(
      within(chooseDialog).getByRole("radio", {
        name: "inventory:requestsManagement.detail.chooseMethodDialog.transferOption",
      }),
    );
    await user.click(
      within(chooseDialog).getByRole("button", {
        name: "inventory:requestsManagement.detail.chooseMethodDialog.proceedButton",
      }),
    );

    const transferDialog = await screen.findByRole("dialog", {
      name: "inventory:requestsManagement.detail.transferDialog.heading",
    });
    await user.click(within(transferDialog).getByRole("button", { name: "common:actions.transfer" }));

    await waitFor(() =>
      expect(
        screen.queryByRole("dialog", { name: "inventory:requestsManagement.detail.transferDialog.heading" }),
      ).toBeNull(),
    );
    expect(addAlert).not.toHaveBeenCalledWith(expect.objectContaining({ variant: "success" }));
    expect(addAlert).toHaveBeenCalledWith(
      expect.objectContaining({
        variant: "error",
        message: "inventory:errors.genericActionError",
      }),
    );
    restoreConsole();
  });

  describe("Transfer Ownership dialog content", () => {
    /** Reaches the Transfer Ownership dialog by the shortest route (the skip-dialog path). */
    async function openTransferDialog(user: ReturnType<typeof userEvent.setup>) {
      deploymentProperties["inventory.sampleRequests.available"] = "ALLOWED";
      deploymentProperties["inventory.operations.available"] = "DENIED";
      renderPanel(baseRequest({ status: "APPROVED" }));
      await waitForInitialFetches();
      await user.click(screen.getByRole("button", { name: "mock-select-subsample" }));
      await user.click(
        screen.getByRole("button", { name: "inventory:requestsManagement.detail.transferSampleButton" }),
      );
      return screen.findByRole("dialog", { name: "inventory:requestsManagement.detail.transferDialog.heading" });
    }

    it("shows the heading, ownership warning, and only the always-present bullets for a single subsample with no other requests", async () => {
      const user = userEvent.setup();
      const dialog = await openTransferDialog(user);

      // Confirmed by openTransferDialog's findByRole itself: the dialog's title IS the heading.
      expect(within(dialog).getByRole("alert")).toHaveTextContent(
        "inventory:requestsManagement.detail.transferDialog.warning",
      );
      expect(
        within(dialog).getByText("inventory:requestsManagement.detail.transferDialog.whatWillHappen"),
      ).toBeInTheDocument();
      expect(
        within(dialog).getByText("inventory:requestsManagement.detail.transferDialog.bullets.subsamplesMoved"),
      ).toBeInTheDocument();
      expect(
        within(dialog).getByText("inventory:requestsManagement.detail.transferDialog.bullets.requestFulfilled"),
      ).toBeInTheDocument();
      expect(
        within(dialog).queryByText("inventory:requestsManagement.detail.transferDialog.bullets.subsamplesTransferred"),
      ).toBeNull();
      expect(
        within(dialog).queryByText("inventory:requestsManagement.detail.transferDialog.bullets.otherRequestsRejected"),
      ).toBeNull();
    });

    it("shows the requester as the fixed recipient", async () => {
      const user = userEvent.setup();
      const dialog = await openTransferDialog(user);

      // There is only ever one legal recipient (the backend always transfers to the request's
      // own requester - see useTransferSampleOwnershipMutation's doc comment in ./mutations.ts),
      // so this is read-only confirmation of who that is, not a field the owner can change.
      expect(within(dialog).getByText(`${REQUESTER.firstName} ${REQUESTER.lastName}`)).toBeInTheDocument();
    });

    it("disables the Transfer button while the transfer is in flight, preventing a duplicate submission", async () => {
      const user = userEvent.setup();
      let resolveTransfer: (() => void) | undefined;
      server.use(
        http.put(SAMPLE_REQUEST_FULFIL_BY_TRANSFER_URL, async ({ params, request }) => {
          const body = await request.json();
          apiUpdate("sampleRequests", `${params.id}/actions/fulfilByTransfer`, body);
          await new Promise<void>((resolve) => {
            resolveTransfer = resolve;
          });
          return HttpResponse.json({ status: "FULFILLED" });
        }),
      );
      const dialog = await openTransferDialog(user);

      const transferButton = within(dialog).getByRole("button", { name: "common:actions.transfer" });
      expect(transferButton).toBeEnabled();
      await user.click(transferButton);
      await waitFor(() => expect(transferButton).toBeDisabled());
      // A disabled button is a no-op click as far as a real user is concerned; userEvent enforces
      // that by refusing the interaction outright, so a raw DOM event stands in for "clicked again
      // while disabled" here.
      fireEvent.click(transferButton);

      // Exactly the one fulfilByTransfer call - not a second from the blocked click.
      expect(apiUpdate).toHaveBeenCalledTimes(1);
      resolveTransfer?.();
      await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull());
      expect(apiUpdate).toHaveBeenCalledTimes(1);
    });

    // The "Both subsamples" (=2) vs "All N subsamples" (other) ICU plural wording is one key as of
    // RSDEV-1309 (see inventory.json's subsamplesTransferred) - in cimode this suite otherwise runs
    // in, both render the identical key name regardless of count, so the two render as the same
    // text here; TransferOwnershipDialog.realI18n.test.tsx verifies the actual plural wording
    // against a real English catalog instead.
    it("adds the multiple-subsamples bullet when the sample has more than one subsample", async () => {
      const user = userEvent.setup();
      server.use(
        http.get(SAMPLE_URL, ({ params }) => {
          apiGet("samples", Number(params.id));
          return HttpResponse.json({ subSamples: [{ id: 1 }, { id: 2 }, { id: 3 }] });
        }),
      );
      const dialog = await openTransferDialog(user);

      expect(
        await within(dialog).findByText(
          "inventory:requestsManagement.detail.transferDialog.bullets.subsamplesTransferred",
        ),
      ).toBeInTheDocument();
    });

    it("adds the other-requests-rejected bullet when other active requests exist on the same sample", async () => {
      const user = userEvent.setup();
      server.use(
        http.get(SAMPLE_REQUESTS_URL, ({ request }) => {
          apiQuery("sampleRequests", new URL(request.url).searchParams);
          return HttpResponse.json({
            requests: [
              { id: 202, requester: { firstName: "Sam", lastName: "Second" } },
              { id: 203, requester: { firstName: "Tara", lastName: "Third" } },
            ],
          });
        }),
      );
      const dialog = await openTransferDialog(user);

      expect(
        await within(dialog).findByText(
          "inventory:requestsManagement.detail.transferDialog.bullets.otherRequestsRejected",
        ),
      ).toBeInTheDocument();
    });

    it("omits the other-requests-rejected bullet once other active requests have resolved to none", async () => {
      const user = userEvent.setup();
      const dialog = await openTransferDialog(user);

      await waitFor(() => expect(apiQuery).toHaveBeenCalled());
      expect(
        within(dialog).queryByText("inventory:requestsManagement.detail.transferDialog.bullets.otherRequestsRejected"),
      ).toBeNull();
    });
  });
});
