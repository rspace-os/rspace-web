import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import MockAdapter from "axios-mock-adapter";
import axios from "@/common/axios";
import UserDetails from "../UserDetails";

vi.mock("@/common/axios", async () => {
  const actual = await vi.importActual<typeof import("axios")>("axios");
  const instance = actual.default?.create
    ? actual.default.create()
    : actual.default;
  return { ...actual, default: instance };
});

describe("UserDetails", () => {
  let mockAxios: MockAdapter;
  let createRequestDialogApi: {
    data: ReturnType<typeof vi.fn>;
    dialog: ReturnType<typeof vi.fn>;
    length: number;
  };
  let bodyApi: {
    find: ReturnType<typeof vi.fn>;
    length: number;
  };

  beforeEach(() => {
    mockAxios = new MockAdapter(axios);
    createRequestDialogApi = {
      data: vi.fn().mockReturnThis(),
      dialog: vi.fn().mockReturnThis(),
      length: 1,
    };
    bodyApi = {
      find: vi.fn().mockReturnValue({ length: 1 }),
      length: 1,
    };

    vi.stubGlobal("$", (selector: string) => {
      if (selector === "#createRequestDlg") {
        return createRequestDialogApi;
      }
      if (selector === "body") {
        return bodyApi;
      }
      return {
        data: vi.fn().mockReturnThis(),
        dialog: vi.fn().mockReturnThis(),
        find: vi.fn().mockReturnValue({ length: 0 }),
        length: 0,
      };
    });
  });

  afterEach(() => {
    mockAxios.restore();
    vi.unstubAllGlobals();
  });

  test("uses the custom label, supports outlined chips, and sends a message when enabled", async () => {
    const onOpen = vi.fn();
    const user = userEvent.setup();

    mockAxios.onGet("/userform/ajax/miniprofile/42").reply(200, {
      data: {
        accountEnabled: true,
        email: "ada@example.com",
        fullname: "Ada Lovelace",
        groups: [],
        lastLogin: "2025-01-01T00:00:00.000Z",
        profileImageLink: "",
        username: "ada",
      },
    });

    render(
      <UserDetails
        userId={42}
        fullName="Ada Lovelace"
        label="ada"
        position={["bottom", "right"]}
        variant="outlined"
        allowMessaging
        onOpen={onOpen}
      />,
    );

    const activator = screen.getByRole("button", { name: "ada" });

    expect(activator).toHaveClass("MuiChip-outlined");

    await user.click(activator);

    await waitFor(() => {
      expect(onOpen).toHaveBeenCalledTimes(1);
      expect(screen.getByRole("link", { name: "Open profile" })).toBeVisible();
      expect(screen.getByRole("link", { name: "Send a message" })).toBeVisible();
    });

    await user.click(screen.getByRole("link", { name: "Send a message" }));

    expect(createRequestDialogApi.data).toHaveBeenCalledWith(
      "recipient",
      "ada<Ada Lovelace>,",
    );
    expect(createRequestDialogApi.dialog).toHaveBeenCalledWith("open");
  });
});


