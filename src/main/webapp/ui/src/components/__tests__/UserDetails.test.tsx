import { chipClasses } from "@mui/material/Chip";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import MockAdapter from "axios-mock-adapter";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";

import { renderWithRealI18n } from "@/__tests__/helpers/realI18n";
import axios from "@/common/axios";
import commonEn from "@/modules/common/i18n/locales/en-US/common.json";
import UserDetails from "../UserDetails";

vi.mock("@/common/axios", async () => {
  const actual = await vi.importActual<typeof import("axios")>("axios");
  const instance = actual.default?.create ? actual.default.create() : actual.default;
  return { ...actual, default: instance };
});

describe("UserDetails", () => {
  let mockAxios: MockAdapter;
  let dialogRecipient = "";

  beforeEach(() => {
    mockAxios = new MockAdapter(axios);

    const createRequestDialog = document.createElement("div");
    createRequestDialog.id = "createRequestDlg";
    createRequestDialog.addEventListener("OPEN_CREATE_REQUEST_DIALOG", (event) => {
      dialogRecipient = (event as CustomEvent<{ recipient: string }>).detail.recipient;
    });
    document.body.appendChild(createRequestDialog);

    const messageDialogTrigger = document.createElement("div");
    messageDialogTrigger.setAttribute("aria-describedby", "messageDlg");
    document.body.appendChild(messageDialogTrigger);
  });

  afterEach(() => {
    mockAxios.restore();
    dialogRecipient = "";
    document.body.innerHTML = "";
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

    expect(activator).toHaveClass(chipClasses.outlined);

    await user.click(activator);

    await waitFor(() => {
      expect(onOpen).toHaveBeenCalledTimes(1);
      expect(screen.getByRole("link", { name: "common:userDetails.openProfile" })).toBeVisible();
      expect(screen.getByRole("link", { name: "common:userDetails.sendMessage" })).toBeVisible();
    });

    await user.click(screen.getByRole("link", { name: "common:userDetails.sendMessage" }));

    expect(dialogRecipient).toBe("ada<Ada Lovelace>,");
  });

  // Real i18n rather than the suite's cimode, which drops the interpolated role.
  test("labels each group role, not just PI and member", async () => {
    const user = userEvent.setup();

    mockAxios.onGet("/userform/ajax/miniprofile/42").reply(200, {
      data: {
        accountEnabled: true,
        email: "ada@example.com",
        fullname: "Ada Lovelace",
        groups: [
          { groupId: 1, groupName: "Alpha", roleInGroup: "PI" },
          { groupId: 2, groupName: "Beta", roleInGroup: "DEFAULT" },
          { groupId: 3, groupName: "Gamma", roleInGroup: "RS_LAB_ADMIN" },
          { groupId: 4, groupName: "Delta", roleInGroup: "GROUP_OWNER" },
        ],
        lastLogin: "2025-01-01T00:00:00.000Z",
        profileImageLink: "",
        username: "ada",
      },
    });

    const { unmount } = await renderWithRealI18n(
      <UserDetails userId={42} fullName="Ada Lovelace" label="ada" position={["bottom", "right"]} />,
      { resources: { common: commonEn }, defaultNS: "common" },
    );

    await user.click(screen.getByRole("button", { name: "ada" }));

    await waitFor(() => {
      expect(screen.getByText("PI at")).toBeVisible();
    });
    expect(screen.getByText("User at")).toBeVisible();
    expect(screen.getByText("Lab Admin at")).toBeVisible();
    expect(screen.getByText("Group Owner at")).toBeVisible();

    // The suite's afterEach clears document.body, which races React's unmount of the portal.
    unmount();
  });
});
