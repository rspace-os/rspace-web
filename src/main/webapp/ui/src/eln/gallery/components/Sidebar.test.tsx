import { beforeEach, describe, expect, test, vi } from "vitest";
import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import MockAdapter from "axios-mock-adapter";
import axios from "@/common/axios";
import { DefaultSidebar } from "./Sidebar.story";

vi.mock("@/hooks/api/integrationHelpers", () => ({
  useIntegrationIsAllowedAndEnabled: () => ({
    tag: "success",
    value: false,
  }),
}));

vi.mock("@/hooks/auth/useOauthToken", () => ({
  __esModule: true,
  default: () => ({
    getToken: () => Promise.resolve("token"),
  }),
}));

const mockAxios = new MockAdapter(axios);

beforeEach(() => {
  mockAxios.reset();
  vi.stubGlobal(
    "matchMedia",
    vi.fn().mockImplementation((query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener() {},
      removeListener() {},
      addEventListener() {},
      removeEventListener() {},
      dispatchEvent() {
        return false;
      },
    })),
  );
  mockAxios.onGet("/session/ajax/analyticsProperties").reply(200, { analyticsEnabled: false });
  mockAxios.onGet(/\/userform\/ajax\/preference.*/).reply(200, {});
  mockAxios.onGet(/\/deploymentproperties\/ajax\/property.*/).reply(200, false);
  mockAxios.onGet("/integration/integrationInfo?name=DMPTOOL").reply(200, {
    data: { name: "DMPTOOL", displayName: "DMPtool", available: true, enabled: false, oauthConnected: false, options: {} },
    error: null,
    success: true,
    errorMsg: null,
  });
  mockAxios.onGet("/integration/integrationInfo?name=DMPONLINE").reply(200, {
    data: { name: "DMPONLINE", displayName: "DMPonline", available: true, enabled: true, oauthConnected: false, options: {} },
    error: null,
    success: true,
    errorMsg: null,
  });
  mockAxios.onGet("/integration/integrationInfo?name=ARGOS").reply(200, {
    data: { name: "ARGOS", displayName: "Argos", available: false, enabled: false, oauthConnected: false, options: {} },
    error: null,
    success: true,
    errorMsg: null,
  });
  mockAxios.onGet("allIntegrations").reply(200, {
    success: true,
    data: [],
    error: null,
    errorMsg: null,
  });
  mockAxios.onGet("/userform/ajax/inventoryOauthToken").reply(200, { data: "token" });
  mockAxios.onGet("/api/v1/gallery/filesystems").reply(200, [
    { id: 1, name: "irods test", url: "irods-test.researchspace.com", clientType: "IRODS", authType: "PASSWORD", options: {}, loggedAs: null },
  ]);
  mockAxios.onGet("/gallery/getUploadedFiles").reply(200, {
    data: { parentId: 1, items: { results: [] } },
  });
  mockAxios.onPost(/createFolder$/).reply(200, {
    data: true,
    error: null,
    success: true,
    errorMsg: null,
  });
});

describe("Gallery Sidebar", () => {
  test("opens the New Folder dialog from the Create menu", async () => {
    const user = userEvent.setup();
    render(<DefaultSidebar />);

    await user.click(await screen.findByRole("button", { name: "Create" }));
    await user.click(await screen.findByRole("menuitem", { name: /new folder/i }));

    expect(await screen.findByRole("dialog")).toBeVisible();
    expect(screen.getByRole("heading", { name: /new folder/i })).toBeVisible();
  });

  test("submits a folder creation request", async () => {
    const user = userEvent.setup();
    render(<DefaultSidebar />);

    await user.click(await screen.findByRole("button", { name: "Create" }));
    await user.click(await screen.findByRole("menuitem", { name: /new folder/i }));
    await user.type(await screen.findByRole("textbox", { name: /name/i }), "test");
    await user.click(screen.getByRole("button", { name: /^create$/i }));

    await waitFor(() => {
      expect(mockAxios.history.post.some(({ url }) => /createFolder$/.test(url ?? ""))).toBe(true);
    });
  });

  test("is accessible", async () => {
    const { container } = render(<DefaultSidebar />);
    await screen.findByRole("button", { name: "Create" });
    /* eslint-disable @typescript-eslint/no-unsafe-call */
    // @ts-expect-error toBeAccessible is provided by @sa11y/vitest
    await expect(container).toBeAccessible();
    /* eslint-enable @typescript-eslint/no-unsafe-call */
  });
});
