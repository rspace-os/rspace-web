/*
 */
import {
  describe,
  expect,
  beforeEach,
  it,
  vi,
} from "vitest";
import React,
  { useState } from "react";
import {
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import "@testing-library/jest-dom/vitest";
import { when } from "vitest-when";
import Zenodo from "../Zenodo";
import { Optional } from "../../../../util/optional";
import "../../../../../__mocks__/matchMedia";
import { type IntegrationStates } from "../../useIntegrationsEndpoint";

const update = vi.fn<IntegrationStates["ZENODO"], [IntegrationStates["ZENODO"]]>();

const ZenodoWrapper = ({
  state = {
    mode: "DISABLED" as const,
    credentials: { ZENODO_USER_TOKEN: Optional.present("") },
  },
}: {
  state?: IntegrationStates["ZENODO"];
} = {}) => {
  const [_state, setState] = useState<IntegrationStates["ZENODO"]>(state);
  return (
    <Zenodo
      integrationState={_state}
      update={(newState) => {
        const result = update(newState);
        setState(result);
      }}
    />
  );
};

describe("Zenodo", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // Default implementation: return the input state
    update.mockImplementation((state) => state);
  });

  it("should open dialog when Zenodo button is clicked", async () => {
    const user = userEvent.setup();
    render(<ZenodoWrapper />);

    await waitFor(() => {
      screen.getAllByText("Zenodo")[0];
    });

    await user.click(screen.getAllByRole("button", { name: /^zenodo/i })[0]);
    expect(screen.getByRole("dialog")).toBeVisible();
  });

  it("should close dialog when close button is clicked", async () => {
    const user = userEvent.setup();
    render(<ZenodoWrapper />);

    await waitFor(() => {
      screen.getAllByText("Zenodo")[0];
    });

    // Open dialog
    await user.click(screen.getAllByRole("button", { name: /^zenodo/i })[0]);
    expect(screen.getByRole("dialog")).toBeVisible();

    // Close dialog
    await user.click(
      within(screen.getByRole("dialog")).getByRole("button", { name: /close/i })
    );
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
  });

  it("should enable integration when dialog is open and integration is disabled", async () => {
    const user = userEvent.setup();

    const expectedState = {
      mode: "ENABLED" as const,
      credentials: { ZENODO_USER_TOKEN: Optional.present("") },
    };

    when(update)
      .calledWith(expectedState)
      .thenReturn(expectedState);

    render(<ZenodoWrapper />);

    await waitFor(() => {
      screen.getAllByText("Zenodo")[0];
    });

    // Open dialog
    await user.click(screen.getAllByRole("button", { name: /^zenodo/i })[0]);
    expect(screen.getByRole("dialog")).toBeVisible();

    // Enable integration
    await user.click(
      within(screen.getByRole("dialog")).getByRole("button", { name: /enable/i })
    );
    await waitFor(() => {
      expect(
        within(screen.getByRole("dialog")).getByRole("button", { name: /disable/i })
      ).toBeVisible();
    });

    // Verify update was called with correct arguments
    expect(update).toHaveBeenCalledWith(expectedState);
  });

  it("should disable integration when dialog is open and integration is enabled", async () => {
    const user = userEvent.setup();

    const initialState = {
      mode: "ENABLED" as const,
      credentials: { ZENODO_USER_TOKEN: Optional.present("test-token") },
    };

    const expectedState = {
      mode: "DISABLED" as const,
      credentials: { ZENODO_USER_TOKEN: Optional.present("test-token") },
    };

    when(update)
      .calledWith(expectedState)
      .thenReturn(expectedState);

    render(
      <ZenodoWrapper
        state={initialState}
      />
    );

    await waitFor(() => {
      screen.getAllByText("Zenodo")[0];
    });

    // Open dialog
    await user.click(screen.getAllByRole("button", { name: /^zenodo/i })[0]);
    expect(screen.getByRole("dialog")).toBeVisible();

    // Disable integration
    await user.click(
      within(screen.getByRole("dialog")).getByRole("button", { name: /disable/i })
    );
    await waitFor(() => {
      expect(
        within(screen.getByRole("dialog")).getByRole("button", { name: /enable/i })
      ).toBeVisible();
    });

    // Verify update was called with correct arguments
    expect(update).toHaveBeenCalledWith(expectedState);
  });

  it("should set API key when dialog is open", async () => {
    const user = userEvent.setup();
    const apiKey = "test-api-key-123";

    const expectedState = {
      mode: "DISABLED" as const,
      credentials: { ZENODO_USER_TOKEN: Optional.present(apiKey) },
    };

    when(update)
      .calledWith(expectedState)
      .thenReturn(expectedState);

    render(<ZenodoWrapper />);

    await waitFor(() => {
      screen.getAllByText("Zenodo")[0];
    });

    // Open dialog
    await user.click(screen.getAllByRole("button", { name: /^zenodo/i })[0]);
    expect(screen.getByRole("dialog")).toBeVisible();

    // Set API key
    /*
     * We have to use getByLabelText instead of getByRole because password
     * fields do not have a role. For more info, see
     * https://github.com/testing-library/dom-testing-library/issues/567
     */
    await user.type(screen.getAllByLabelText("API Key")[0], apiKey);
    await user.click(screen.getByRole("button", { name: /save/i }));

    // Verify update was called with correct arguments
    expect(update).toHaveBeenCalledWith(expectedState);
  });

  it("should open, set API key, enable, and close dialog in sequence", async () => {
    const user = userEvent.setup();
    const apiKey = "my-zenodo-api-key";

    const apiKeyState = {
      mode: "DISABLED" as const,
      credentials: { ZENODO_USER_TOKEN: Optional.present(apiKey) },
    };

    const enabledState = {
      mode: "ENABLED" as const,
      credentials: { ZENODO_USER_TOKEN: Optional.present(apiKey) },
    };

    when(update)
      .calledWith(apiKeyState)
      .thenReturn(apiKeyState);

    when(update)
      .calledWith(enabledState)
      .thenReturn(enabledState);

    render(<ZenodoWrapper />);

    await waitFor(() => {
      screen.getAllByText("Zenodo")[0];
    });

    // Open dialog
    await user.click(screen.getAllByRole("button", { name: /^zenodo/i })[0]);
    expect(screen.getByRole("dialog")).toBeVisible();

    // Set API key
    await user.type(screen.getAllByLabelText("API Key")[0], apiKey);
    await user.click(screen.getByRole("button", { name: /save/i }));

    // Verify update was called with correct arguments for setting API key
    expect(update).toHaveBeenCalledWith(apiKeyState);

    // Enable integration
    await user.click(
      within(screen.getByRole("dialog")).getByRole("button", { name: /enable/i })
    );
    await waitFor(() => {
      expect(
        within(screen.getByRole("dialog")).getByRole("button", { name: /disable/i })
      ).toBeVisible();
    });

    // Verify update was called with correct arguments for enabling
    expect(update).toHaveBeenCalledWith(enabledState);

    // Close dialog
    await user.click(
      within(screen.getByRole("dialog")).getByRole("button", { name: /close/i })
    );
    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    });
  });
});
