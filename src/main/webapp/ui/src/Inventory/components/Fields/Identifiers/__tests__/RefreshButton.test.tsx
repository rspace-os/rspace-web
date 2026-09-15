import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, test, vi } from "vitest";
import type InvApiService from "../../../../../common/InvApiService";
import type { Identifier, PublishingState } from "../../../../../stores/definitions/Identifier";
import type { InventoryRecord } from "../../../../../stores/definitions/InventoryRecord";
import { makeMockSample } from "../../../../../stores/models/__tests__/SampleModel/mocking";
import IdentifierModel from "../../../../../stores/models/IdentifierModel";
import { IdentifiersList } from "../Identifiers";
import RefreshButton from "../RefreshButton";
import { mockIGSNAttrs, mockIGSNIdentifier } from "./mocking";
import "@/__tests__/__mocks__/matchMedia";
import { ThemeProvider } from "@mui/material/styles";
import materialTheme from "../../../../../theme";

const REFRESH = "inventory:fields.identifiers.list.refresh";
const PUBLISH = "common:actions.publish";
const stateLabel = (state: PublishingState) => `inventory:fields.identifiers.list.stateLabels.${state}`;

const submittedPidinst = (overrides?: Partial<Identifier>): Identifier => ({
  ...mockIGSNIdentifier("instrument"),
  doiType: "PIDINST_B2INST",
  state: "submitted",
  refresh: vi.fn().mockResolvedValue(undefined),
  ...overrides,
});

describe("RefreshButton", () => {
  test("renders for a submitted identifier and calls refresh on click", async () => {
    const user = userEvent.setup();
    const identifier = submittedPidinst();
    render(
      <ThemeProvider theme={materialTheme}>
        <RefreshButton identifier={identifier} />
      </ThemeProvider>,
    );

    await user.click(screen.getByRole("button", { name: REFRESH }));

    const refresh = identifier.refresh;
    expect(vi.mocked(refresh)).toHaveBeenCalled();
  });

  test.each(["draft", "created", "declined", "findable"] as const)("renders nothing when the state is %s", (state) => {
    render(
      <ThemeProvider theme={materialTheme}>
        <RefreshButton identifier={submittedPidinst({ state })} />
      </ThemeProvider>,
    );
    expect(screen.queryByRole("button", { name: REFRESH })).not.toBeInTheDocument();
  });

  /*
   * Acceptance takes the minted ePIC PID out of the published record's loosely typed `pids` block,
   * which can come back without one. Refresh is the only action that can pick it up later, so it
   * stays offered until there is a Handle (Copilot review, PR 1066).
   */
  test("stays available for an accepted identifier that has no Handle yet", () => {
    render(
      <ThemeProvider theme={materialTheme}>
        <RefreshButton identifier={submittedPidinst({ state: "accepted", publicUrl: null })} />
      </ThemeProvider>,
    );
    expect(screen.getByRole("button", { name: REFRESH })).toBeVisible();
  });

  test("retires once an accepted identifier has its Handle", () => {
    render(
      <ThemeProvider theme={materialTheme}>
        <RefreshButton
          identifier={submittedPidinst({
            state: "accepted",
            publicUrl: "http://hdl.handle.net/21.T11975/k2j9p-7yh21",
          })}
        />
      </ThemeProvider>,
    );
    expect(screen.queryByRole("button", { name: REFRESH })).not.toBeInTheDocument();
  });

  test("is disabled when the record is being edited", () => {
    render(
      <ThemeProvider theme={materialTheme}>
        <RefreshButton identifier={submittedPidinst()} disabled />
      </ThemeProvider>,
    );
    expect(screen.getByRole("button", { name: REFRESH })).toBeDisabled();
  });
});

/*
 * The suite above covers "the button calls refresh" with refresh stubbed, and
 * IdentifierModel.test.ts covers "the model applies the response". Neither shows the PR's headline
 * behaviour: that a status pulled from B2INST actually reaches the rendered identifier. This joins
 * them with a real IdentifierModel and only the HTTP call faked (parallel review, PR 1066).
 */
describe("a refreshed status reaching the UI", () => {
  const submittedB2instRecord = (post: ReturnType<typeof vi.fn>): InventoryRecord => {
    const record = makeMockSample();
    record.identifiers = [
      new IdentifierModel({ ...mockIGSNAttrs(), doiType: "PIDINST_B2INST", state: "submitted" }, "IN1", {
        post,
      } as unknown as typeof InvApiService),
    ];
    return record;
  };

  test("an accepted review replaces the state, retires Refresh and closes Publish", async () => {
    const user = userEvent.setup();
    const post = vi.fn().mockResolvedValue({
      data: {
        state: "accepted",
        url: "https://institution.example.org/instruments/nmr-400",
        publicUrl: "http://hdl.handle.net/21.T11975/k2j9p-7yh21",
        providerUrl: "https://b2inst-test.gwdg.de/records/k2j9p-7yh21",
      },
    });
    render(
      <ThemeProvider theme={materialTheme}>
        <IdentifiersList activeResult={submittedB2instRecord(post)} />
      </ThemeProvider>,
    );

    // while the review is open: Submitted, Refresh offered, Publish held back
    expect(screen.getByText(stateLabel("submitted"))).toBeVisible();
    expect(screen.getByRole("button", { name: PUBLISH })).toBeDisabled();

    await user.click(screen.getByRole("button", { name: REFRESH }));

    // after the curator accepted: the new state is rendered, and Refresh has nothing left to do
    expect(await screen.findByText(stateLabel("accepted"))).toBeVisible();
    expect(post).toHaveBeenCalledWith("/identifiers/1/refresh", {});
    expect(screen.queryByRole("button", { name: REFRESH })).not.toBeInTheDocument();
    // an accepted B2INST PID cannot be published again, and B2INST has no retract
    expect(screen.getByRole("button", { name: PUBLISH })).toBeDisabled();
  });

  test("a declined review is rendered and also retires Refresh", async () => {
    const user = userEvent.setup();
    const post = vi.fn().mockResolvedValue({
      data: { state: "declined", url: null, publicUrl: null, providerUrl: null },
    });
    render(
      <ThemeProvider theme={materialTheme}>
        <IdentifiersList activeResult={submittedB2instRecord(post)} />
      </ThemeProvider>,
    );

    await user.click(screen.getByRole("button", { name: REFRESH }));

    expect(await screen.findByText(stateLabel("declined"))).toBeVisible();
    expect(screen.queryByRole("button", { name: REFRESH })).not.toBeInTheDocument();
  });
});
