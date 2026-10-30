import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import {
  baseRequest,
  currentUser,
  REQUESTER,
  renderPanel,
  resetRequestDetailPanelFixtures,
  waitForInitialFetches,
} from "./RequestDetailPanel.fixtures";

beforeEach(() => {
  resetRequestDetailPanelFixtures();
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
});
