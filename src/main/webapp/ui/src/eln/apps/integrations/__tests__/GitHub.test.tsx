import { act, cleanup, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import MockAdapter from "axios-mock-adapter";
import { HttpResponse, http } from "msw/http";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import { findTableCell } from "@/__tests__/tableQueries";
import axios from "@/common/axios";
import Alerts from "@/components/Alerts/Alerts";
import allIntegrationsAreDisabled from "@/eln/apps/__tests__/allIntegrationsAreDisabled.json";
import { Optional } from "../../../../util/optional";
import type { IntegrationStates } from "../../useIntegrationsEndpoint";
import GitHub, { type GitHubConnectedMessage } from "../GitHub";

import "@/__tests__/__mocks__/matchMedia";
import { cachedState, createIntegrationsClient, WithIntegrations } from "../../__tests__/integrationsQueryClient";

const broadcastHandlers: Array<(e: MessageEvent<GitHubConnectedMessage>) => void> = [];
vi.mock("@/modules/common/hooks/broadcast", () => ({
  useBroadcastChannel: (_channel: string, handler: (e: MessageEvent<GitHubConnectedMessage>) => void) => {
    broadcastHandlers.push(handler);
  },
}));

beforeEach(() => {
  vi.clearAllMocks();
  broadcastHandlers.length = 0;
});

afterEach(cleanup);

describe("GitHub", () => {
  describe("Accessibility", () => {
    test("Should have no axe violations.", async () => {
      const user = userEvent.setup();
      const { baseElement } = render(
        <WithIntegrations
          client={createIntegrationsClient({
            GITHUB: {
              mode: "DISABLED",
              credentials: [],
            },
          })}
        >
          <GitHub />
        </WithIntegrations>,
      );

      await user.click(screen.getByRole("button"));
      expect(await screen.findByRole("dialog")).toBeVisible();

      // @ts-expect-error toBeAccessible is from @sa11y/vitest
      await expect(baseElement).toBeAccessible();
    });
  });
  describe("Correct rendering", () => {
    test("When there are no repositories, there is a label.", async () => {
      const user = userEvent.setup();
      render(
        <WithIntegrations
          client={createIntegrationsClient({
            GITHUB: {
              mode: "DISABLED",
              credentials: [],
            },
          })}
        >
          <GitHub />
        </WithIntegrations>,
      );

      await user.click(screen.getByRole("button"));
      expect(screen.getByText("apps:integrations.github.repositories.noLinked")).toBeVisible();
    });
    test("The names of repositories should be shown in a table.", async () => {
      const user = userEvent.setup();
      render(
        <WithIntegrations
          client={createIntegrationsClient({
            GITHUB: {
              mode: "DISABLED",
              credentials: [
                Optional.present({
                  GITHUB_REPOSITORY_FULL_NAME: "username/someRepo",
                  optionsId: "1",
                }),
              ],
            },
          })}
        >
          <GitHub />
        </WithIntegrations>,
      );

      await user.click(screen.getByRole("button"));
      expect(within(screen.getByRole("table")).getByText("username/someRepo")).toBeVisible();
    });
  });
  describe("Adding repositories", () => {
    test("can authorize again after pending authorization expires or is cleared without closing the chooser", async () => {
      const user = userEvent.setup();
      let authorized = false;
      server.use(
        http.get("/github/oauthUrl", () => {
          authorized = true;
          return HttpResponse.json({ success: true, data: "https://github.com/login/oauth/authorize", error: null });
        }),
        http.get("/github/allRepositories", () =>
          HttpResponse.json({ success: true, data: [{ full_name: "owner/repo", description: "" }], error: null }),
        ),
        http.post("/integration/saveAppOptions", () =>
          HttpResponse.json(
            authorized
              ? {
                  success: true,
                  data: {
                    available: true,
                    enabled: false,
                    name: "GITHUB",
                    options: { "1": { GITHUB_REPOSITORY_FULL_NAME: "owner/repo" } },
                  },
                }
              : { success: false, data: null, errorMsg: "GitHub OAuth connection is required" },
          ),
        ),
      );
      const openWindow = vi.spyOn(window, "open").mockReturnValue(window);
      render(
        <Alerts>
          <WithIntegrations client={createIntegrationsClient({ GITHUB: { mode: "DISABLED", credentials: [] } })}>
            <GitHub />
          </WithIntegrations>
        </Alerts>,
      );
      await user.click(screen.getByRole("button", { name: "apps:integrations.github.name" }));
      const dialog = screen.getByRole("dialog");
      await user.click(within(dialog).getByRole("button", { name: "common:actions.add" }));
      await waitFor(() => expect(openWindow).toHaveBeenCalledOnce());
      act(() => {
        broadcastHandlers.at(-1)?.(new MessageEvent("message", { data: { type: "GITHUB_CONNECTED" } }));
      });
      const repoRow = await within(dialog).findByRole("row", { name: /owner\/repo/ });
      authorized = false;
      await user.click(within(repoRow).getByRole("button", { name: "common:actions.add" }));
      expect(await screen.findByText("GitHub OAuth connection is required")).toBeVisible();

      await user.click(within(dialog).getByRole("button", { name: "apps:actions.connect" }));
      await waitFor(() => expect(openWindow).toHaveBeenCalledTimes(2));
      act(() => {
        broadcastHandlers.at(-1)?.(new MessageEvent("message", { data: { type: "GITHUB_CONNECTED" } }));
      });
      await within(dialog).findByRole("button", { name: "apps:actions.connect" });
      await user.click(within(repoRow).getByRole("button", { name: "common:actions.add" }));
      expect(await screen.findByText("apps:integrations.github.alerts.addSuccess")).toBeVisible();
      expect(within(within(dialog).getAllByRole("table")[0]).getByRole("cell", { name: "owner/repo" })).toBeVisible();
      expect(dialog).toBeVisible();
    });

    test("When requested, all repositories should be listed in a table.", async () => {
      const user = userEvent.setup();
      const mockAxios = new MockAdapter(axios);
      mockAxios.onGet("github/oauthUrl").reply(200, {
        success: true,
        data: "https://github.com/login/oauth/authorize?scope=repo,user&client_id=",
        error: null,
      });
      mockAxios.onGet("github/allRepositories").reply(200, {
        success: true,
        data: [{ full_name: "a repo", description: "" }],
        error: null,
      });

      vi.spyOn(window, "open").mockImplementation(
        () =>
          ({
            close: () => {},
          }) as unknown as Window,
      );
      render(
        <WithIntegrations
          client={createIntegrationsClient({
            GITHUB: {
              mode: "DISABLED",
              credentials: [],
            },
          })}
        >
          <GitHub />
        </WithIntegrations>,
      );

      await user.click(screen.getByRole("button"));

      await user.click(screen.getByRole("button", { name: "common:actions.add" }));

      act(() => {
        // biome-ignore lint/suspicious/useIterableCallbackReturn: initial biome migration
        broadcastHandlers.forEach((handler) =>
          handler({
            data: { type: "GITHUB_CONNECTED" },
          } as MessageEvent<GitHubConnectedMessage>),
        );
      });

      await waitFor(() => {
        expect(screen.getAllByRole("table").length).toBe(2);
      });
      const newReposTable = screen.getAllByRole("table")[1];
      expect(
        await findTableCell(newReposTable, {
          columnHeading: "apps:integrations.github.repositories.nameHeader",
          rowIndex: 0,
        }),
      ).toHaveTextContent("a repo");
    });
    test("Repositories that are already linked are not offered again after reconnecting.", async () => {
      const user = userEvent.setup();
      const mockAxios = new MockAdapter(axios);
      mockAxios.onGet("github/oauthUrl").reply(200, {
        success: true,
        data: "https://github.com/login/oauth/authorize?scope=repo,user&client_id=",
        error: null,
      });
      mockAxios.onGet("github/allRepositories").reply(200, {
        success: true,
        data: [
          { full_name: "linked repo", description: "" },
          { full_name: "other repo", description: "" },
        ],
        error: null,
      });
      vi.spyOn(window, "open").mockImplementation(
        () =>
          ({
            close: () => {},
          }) as unknown as Window,
      );
      render(
        <WithIntegrations
          client={createIntegrationsClient({
            GITHUB: {
              mode: "DISABLED",
              credentials: [Optional.present({ GITHUB_REPOSITORY_FULL_NAME: "linked repo", optionsId: "1" })],
            },
          })}
        >
          <GitHub />
        </WithIntegrations>,
      );

      await user.click(screen.getByRole("button"));
      await user.click(screen.getByRole("button", { name: "common:actions.add" }));
      act(() => {
        // biome-ignore lint/suspicious/useIterableCallbackReturn: matches the other broadcast tests
        broadcastHandlers.forEach((handler) =>
          handler({
            data: { type: "GITHUB_CONNECTED" },
          } as MessageEvent<GitHubConnectedMessage>),
        );
      });

      await waitFor(() => {
        expect(screen.getAllByRole("table").length).toBe(2);
      });
      const newReposTable = screen.getAllByRole("table")[1];
      expect(await within(newReposTable).findByText("other repo")).toBeInTheDocument();
      expect(within(newReposTable).queryByText("linked repo")).not.toBeInTheDocument();
    });

    test("When tapped, the add button in the repositories table should make the right API call.", async () => {
      const user = userEvent.setup();
      const mockAxios = new MockAdapter(axios);
      mockAxios.onGet("github/oauthUrl").reply(200, {
        success: true,
        data: "https://github.com/login/oauth/authorize?scope=repo,user&client_id=",
        error: null,
      });
      mockAxios.onGet("github/allRepositories").reply(200, {
        success: true,
        data: [{ full_name: "a repo", description: "" }],
        error: null,
      });
      mockAxios.onPost("integration/saveAppOptions");

      vi.spyOn(window, "open").mockImplementation(
        () =>
          ({
            close: () => {},
          }) as unknown as Window,
      );
      render(
        <WithIntegrations
          client={createIntegrationsClient({
            GITHUB: {
              mode: "DISABLED",
              credentials: [],
            },
          })}
        >
          <GitHub />
        </WithIntegrations>,
      );

      await user.click(screen.getByRole("button"));

      await user.click(screen.getByRole("button", { name: "common:actions.add" }));

      act(() => {
        // biome-ignore lint/suspicious/useIterableCallbackReturn: initial biome migration
        broadcastHandlers.forEach((handler) =>
          handler({
            data: { type: "GITHUB_CONNECTED" },
          } as MessageEvent<GitHubConnectedMessage>),
        );
      });

      await waitFor(() => {
        expect(screen.getAllByRole("table").length).toBe(2);
      });
      const allReposTable = screen.getAllByRole("table")[1];
      await user.click(
        within(within(within(allReposTable).getAllByRole("row")[1]).getAllByRole("cell")[1]).getByRole("button", {
          name: "common:actions.add",
        }),
      );
      expect(mockAxios.history.post.length).toBe(1);
      expect(mockAxios.history.post[0].params.get("appName")).toEqual("GITHUB");
      expect(JSON.parse(mockAxios.history.post[0].data)).toEqual({
        GITHUB_REPOSITORY_FULL_NAME: "a repo",
      });
    });
    test("When the add button next to a repo is tapped, it should be added to the conncted repos table and removed from the all repos table.", async () => {
      const user = userEvent.setup();
      const mockAxios = new MockAdapter(axios);
      mockAxios.onGet("github/oauthUrl").reply(200, {
        success: true,
        data: "https://github.com/login/oauth/authorize?scope=repo,user&client_id=",
        error: null,
      });
      mockAxios.onGet("github/allRepositories").reply(200, {
        success: true,
        data: [{ full_name: "a repo", description: "" }],
        error: null,
      });
      mockAxios.onPost("integration/saveAppOptions").reply(200, {
        success: true,
        data: {
          available: true,
          enabled: false,
          name: "GITHUB",
          options: {
            "1": {
              GITHUB_REPOSITORY_FULL_NAME: "a repo",
            },
          },
        },
      });

      vi.spyOn(window, "open").mockImplementation(
        () =>
          ({
            close: () => {},
          }) as unknown as Window,
      );
      render(
        <WithIntegrations
          client={createIntegrationsClient({
            GITHUB: {
              mode: "DISABLED",
              credentials: [],
            },
          })}
        >
          <GitHub />
        </WithIntegrations>,
      );

      await user.click(screen.getByRole("button"));

      await user.click(screen.getByRole("button", { name: "common:actions.add" }));

      act(() => {
        // biome-ignore lint/suspicious/useIterableCallbackReturn: initial biome migration
        broadcastHandlers.forEach((handler) =>
          handler({
            data: { type: "GITHUB_CONNECTED" },
          } as MessageEvent<GitHubConnectedMessage>),
        );
      });

      await waitFor(() => {
        expect(screen.getAllByRole("table").length).toBe(2);
      });
      const allReposTable = screen.getAllByRole("table")[1];
      await user.click(
        within(within(within(allReposTable).getAllByRole("row")[1]).getAllByRole("cell")[1]).getByRole("button", {
          name: "common:actions.add",
        }),
      );
      await waitFor(() => {
        expect(screen.queryByText("apps:integrations.github.repositories.noLinked")).not.toBeInTheDocument();
      });
      const connectedReposTable = screen.getAllByRole("table")[0];
      expect(
        await findTableCell(connectedReposTable, {
          columnHeading: "apps:integrations.github.repositories.nameHeader",
          rowIndex: 0,
        }),
      ).toHaveTextContent("a repo");
      expect(
        await findTableCell(allReposTable, {
          columnHeading: "apps:integrations.github.repositories.nameHeader",
          rowIndex: 0,
        }),
      ).toHaveTextContent("apps:integrations.github.repositories.noAvailable");
    });
    test("Adding a repository should update the integration's credentials.", async () => {
      const user = userEvent.setup();
      const integrationState: IntegrationStates["GITHUB"] = {
        mode: "DISABLED",
        credentials: [],
      };
      const queryClient = createIntegrationsClient({ GITHUB: integrationState });
      const mockAxios = new MockAdapter(axios);
      mockAxios.onGet("github/oauthUrl").reply(200, {
        success: true,
        data: "https://github.com/login/oauth/authorize?scope=repo,user&client_id=",
        error: null,
      });
      mockAxios.onGet("github/allRepositories").reply(200, {
        success: true,
        data: [{ full_name: "a repo", description: "" }],
        error: null,
      });
      mockAxios.onPost("integration/saveAppOptions").reply(200, {
        success: true,
        data: {
          available: true,
          enabled: false,
          name: "GITHUB",
          options: {
            "1": {
              GITHUB_REPOSITORY_FULL_NAME: "a repo",
            },
          },
        },
      });

      vi.spyOn(window, "open").mockImplementation(
        () =>
          ({
            close: () => {},
          }) as unknown as Window,
      );

      render(
        <WithIntegrations client={queryClient}>
          <GitHub />
        </WithIntegrations>,
      );

      await user.click(screen.getByRole("button"));

      await user.click(screen.getByRole("button", { name: "common:actions.add" }));

      act(() => {
        // biome-ignore lint/suspicious/useIterableCallbackReturn: initial biome migration
        broadcastHandlers.forEach((handler) =>
          handler({
            data: { type: "GITHUB_CONNECTED" },
          } as MessageEvent<GitHubConnectedMessage>),
        );
      });

      await waitFor(() => {
        expect(screen.getAllByRole("table").length).toBe(2);
      });
      const allReposTable = screen.getAllByRole("table")[1];
      await user.click(
        within(within(within(allReposTable).getAllByRole("row")[1]).getAllByRole("cell")[1]).getByRole("button", {
          name: "common:actions.add",
        }),
      );
      await waitFor(() => {
        expect(screen.queryByText("apps:integrations.github.repositories.noLinked")).not.toBeInTheDocument();
      });
      expect(cachedState(queryClient, "GITHUB").credentials.length).toBe(1);
    });

    test("Two additions pending at once each show their own repository.", async () => {
      const user = userEvent.setup();
      const mockAxios = new MockAdapter(axios);
      mockAxios.onGet("github/oauthUrl").reply(200, {
        success: true,
        data: "https://github.com/login/oauth/authorize?scope=repo,user&client_id=",
        error: null,
      });
      mockAxios.onGet("github/allRepositories").reply(200, {
        success: true,
        data: [
          { full_name: "first repo", description: "" },
          { full_name: "second repo", description: "" },
        ],
        error: null,
      });
      const saved = (options: Record<string, { GITHUB_REPOSITORY_FULL_NAME: string }>): [number, unknown] => [
        200,
        { success: true, data: { available: true, enabled: false, name: "GITHUB", options } },
      ];
      let finishFirst!: () => void;
      mockAxios
        .onPost("integration/saveAppOptions")
        .replyOnce(
          () =>
            new Promise((resolve) => {
              finishFirst = () => resolve(saved({ "1": { GITHUB_REPOSITORY_FULL_NAME: "first repo" } }));
            }),
        )
        .onPost("integration/saveAppOptions")
        .replyOnce(() =>
          saved({
            "1": { GITHUB_REPOSITORY_FULL_NAME: "first repo" },
            "2": { GITHUB_REPOSITORY_FULL_NAME: "second repo" },
          }),
        );
      // the first response may be older than the second, so once both have settled the integrations are refetched
      mockAxios.onGet("integration/allIntegrations").reply(200, {
        success: true,
        data: {
          ...allIntegrationsAreDisabled.data,
          GITHUB: {
            ...allIntegrationsAreDisabled.data.GITHUB,
            options: {
              "1": { GITHUB_REPOSITORY_FULL_NAME: "first repo" },
              "2": { GITHUB_REPOSITORY_FULL_NAME: "second repo" },
            },
          },
        },
        error: null,
      });
      vi.spyOn(window, "open").mockImplementation(
        () =>
          ({
            close: () => {},
          }) as unknown as Window,
      );

      render(
        <WithIntegrations client={createIntegrationsClient({ GITHUB: { mode: "DISABLED", credentials: [] } })}>
          <GitHub />
        </WithIntegrations>,
      );
      await user.click(screen.getByRole("button"));
      await user.click(screen.getByRole("button", { name: "common:actions.add" }));
      act(() => {
        // biome-ignore lint/suspicious/useIterableCallbackReturn: matches the other broadcast tests
        broadcastHandlers.forEach((handler) =>
          handler({
            data: { type: "GITHUB_CONNECTED" },
          } as MessageEvent<GitHubConnectedMessage>),
        );
      });
      await waitFor(() => {
        expect(screen.getAllByRole("table").length).toBe(2);
      });
      const addButtonInRow = (name: string) =>
        within(within(screen.getAllByRole("table")[1]).getByText(name).closest("tr") as HTMLElement).getByRole(
          "button",
          { name: "common:actions.add" },
        );

      await user.click(addButtonInRow("first repo"));
      await user.click(addButtonInRow("second repo"));
      await waitFor(() => {
        expect(within(screen.getAllByRole("table")[0]).getByText("second repo")).toBeInTheDocument();
      });
      act(() => finishFirst());

      await waitFor(() => {
        expect(mockAxios.history.get.map(({ url }) => url)).toContain("allIntegrations");
      });
      const linkedTable = screen.getAllByRole("table")[0];
      await waitFor(() => {
        expect(within(linkedTable).getByText("first repo")).toBeInTheDocument();
      });
      expect(within(linkedTable).getAllByText(/repo$/)).toHaveLength(2);
    });
  });
  describe("Removing repositories", () => {
    test("Removing a repository should make the correct API call.", async () => {
      const user = userEvent.setup();
      const mockAxios = new MockAdapter(axios);
      mockAxios.onPost("integration/deleteAppOptions").reply(200, {
        success: true,
        data: {
          available: true,
          enabled: true,
          name: "GITHUB",
          options: {},
        },
      });
      render(
        <WithIntegrations
          client={createIntegrationsClient({
            GITHUB: {
              mode: "DISABLED",
              credentials: [
                Optional.present({
                  GITHUB_REPOSITORY_FULL_NAME: "username/someRepo",
                  optionsId: "1",
                }),
              ],
            },
          })}
        >
          <GitHub />
        </WithIntegrations>,
      );

      await user.click(screen.getByRole("button"));

      await user.click(screen.getByRole("button", { name: "common:actions.remove" }));
      expect(mockAxios.history.post.length).toBe(1);
      expect(mockAxios.history.post[0].params.get("appName")).toEqual("GITHUB");

      expect(mockAxios.history.post[0].data.get("optionsId")).toBe("1");

      const table = screen.getByRole("table");
      await waitFor(() => {
        expect(within(table).queryByText("username/someRepo")).not.toBeInTheDocument();
      });
    });
    test("Removing a repository should update the integration's credentials.", async () => {
      const user = userEvent.setup();
      const integrationState: IntegrationStates["GITHUB"] = {
        mode: "DISABLED",
        credentials: [
          Optional.present({
            GITHUB_REPOSITORY_FULL_NAME: "username/someRepo",
            optionsId: "1",
          }),
        ],
      };
      const queryClient = createIntegrationsClient({ GITHUB: integrationState });
      const mockAxios = new MockAdapter(axios);
      mockAxios.onPost("integration/deleteAppOptions").reply(200, {
        success: true,
        data: {
          available: true,
          enabled: false,
          name: "DATAVERSE",
          options: {},
        },
      });

      render(
        <WithIntegrations client={queryClient}>
          <GitHub />
        </WithIntegrations>,
      );

      await user.click(screen.getByRole("button"));

      await user.click(screen.getByRole("button", { name: "common:actions.remove" }));
      await waitFor(() => {
        expect(screen.queryByText("username/someRepo")).not.toBeInTheDocument();
      });
      expect(cachedState(queryClient, "GITHUB").credentials.length).toBe(0);
    });
    test("Removing the last repository asks for GitHub authorisation again before another can be added.", async () => {
      const user = userEvent.setup();
      const mockAxios = new MockAdapter(axios);
      mockAxios.onGet("github/oauthUrl").reply(200, {
        success: true,
        data: "https://github.com/login/oauth/authorize?scope=repo,user&client_id=",
        error: null,
      });
      mockAxios.onGet("github/allRepositories").reply(200, {
        success: true,
        data: [{ full_name: "a repo", description: "" }],
        error: null,
      });
      mockAxios.onPost("integration/saveAppOptions").reply(200, {
        success: true,
        data: {
          available: true,
          enabled: false,
          name: "GITHUB",
          options: { "1": { GITHUB_REPOSITORY_FULL_NAME: "a repo" } },
        },
      });
      mockAxios.onPost("integration/deleteAppOptions").reply(200, {
        success: true,
        data: { available: true, enabled: false, name: "GITHUB", options: {} },
      });
      const openWindow = vi.spyOn(window, "open").mockImplementation(() => ({ close: () => {} }) as unknown as Window);
      render(
        <WithIntegrations client={createIntegrationsClient({ GITHUB: { mode: "DISABLED", credentials: [] } })}>
          <GitHub />
        </WithIntegrations>,
      );

      await user.click(screen.getByRole("button"));
      await user.click(screen.getByRole("button", { name: "common:actions.add" }));
      act(() => {
        for (const handler of broadcastHandlers)
          handler({ data: { type: "GITHUB_CONNECTED" } } as MessageEvent<GitHubConnectedMessage>);
      });
      await waitFor(() => {
        expect(screen.getAllByRole("table").length).toBe(2);
      });
      await user.click(within(screen.getAllByRole("table")[1]).getByRole("button", { name: "common:actions.add" }));
      await user.click(await screen.findByRole("button", { name: "common:actions.remove" }));

      await waitFor(() => {
        expect(screen.getAllByRole("table").length).toBe(1);
      });
      await user.click(screen.getByRole("button", { name: "common:actions.add" }));
      await waitFor(() => {
        expect(openWindow).toHaveBeenCalledTimes(2);
      });
    });
  });
});
