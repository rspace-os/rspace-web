import { act, cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import MockAdapter from "axios-mock-adapter";
import { observable } from "mobx";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { server } from "@/__tests__/mswServer";
import { findTableCell } from "@/__tests__/tableQueries";
import axios from "@/common/axios";
import Alerts from "@/components/Alerts/Alerts";
import { Optional } from "../../../../util/optional";
import type { IntegrationStates } from "../../useIntegrationsEndpoint";
import GitHub, { type GitHubConnectedMessage } from "../GitHub";

import "@/__tests__/__mocks__/matchMedia";

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
      const { baseElement } = render(
        <GitHub
          integrationState={{
            mode: "DISABLED",
            credentials: [],
          }}
          update={() => {}}
        />,
      );

      fireEvent.click(screen.getByRole("button"));
      expect(await screen.findByRole("dialog")).toBeVisible();

      // @ts-expect-error toBeAccessible is from @sa11y/vitest
      await expect(baseElement).toBeAccessible();
    });
  });
  describe("Correct rendering", () => {
    test("When there are no repositories, there is a label.", () => {
      render(
        <GitHub
          integrationState={{
            mode: "DISABLED",
            credentials: [],
          }}
          update={() => {}}
        />,
      );

      fireEvent.click(screen.getByRole("button"));
      expect(screen.getByText("apps:integrations.github.repositories.noLinked")).toBeVisible();
    });
    test("The names of repositories should be shown in a table.", () => {
      render(
        <GitHub
          integrationState={{
            mode: "DISABLED",
            credentials: [
              Optional.present({
                GITHUB_REPOSITORY_FULL_NAME: "username/someRepo",
                optionsId: "1",
              }),
            ],
          }}
          update={() => {}}
        />,
      );

      fireEvent.click(screen.getByRole("button"));
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
          <GitHub integrationState={observable({ mode: "DISABLED", credentials: [] })} update={() => {}} />
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
        <GitHub
          integrationState={{
            mode: "DISABLED",
            credentials: [],
          }}
          update={() => {}}
        />,
      );

      fireEvent.click(screen.getByRole("button"));

      fireEvent.click(screen.getByRole("button", { name: "common:actions.add" }));

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
    test("When tapped, the add button in the repositories table should make the right API call.", async () => {
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
        <GitHub
          integrationState={{
            mode: "DISABLED",
            credentials: [],
          }}
          update={() => {}}
        />,
      );

      fireEvent.click(screen.getByRole("button"));

      fireEvent.click(screen.getByRole("button", { name: "common:actions.add" }));

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
      fireEvent.click(
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
        <GitHub
          integrationState={observable({
            mode: "DISABLED",
            credentials: [],
          })}
          update={() => {}}
        />,
      );

      fireEvent.click(screen.getByRole("button"));

      fireEvent.click(screen.getByRole("button", { name: "common:actions.add" }));

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
      fireEvent.click(
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
    test("Adding a repository should mutate the integration state being passed as a prop.", async () => {
      const integrationState = observable<IntegrationStates["GITHUB"]>({
        mode: "DISABLED",
        credentials: [],
      });
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

      render(<GitHub integrationState={integrationState} update={() => {}} />);

      fireEvent.click(screen.getByRole("button"));

      fireEvent.click(screen.getByRole("button", { name: "common:actions.add" }));

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
      fireEvent.click(
        within(within(within(allReposTable).getAllByRole("row")[1]).getAllByRole("cell")[1]).getByRole("button", {
          name: "common:actions.add",
        }),
      );
      await waitFor(() => {
        expect(screen.queryByText("apps:integrations.github.repositories.noLinked")).not.toBeInTheDocument();
      });
      expect(integrationState.credentials.length).toBe(1);
    });
  });
  describe("Removing repositories", () => {
    test("Removing a repository should make the correct API call.", async () => {
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
        <GitHub
          integrationState={{
            mode: "DISABLED",
            credentials: [
              Optional.present({
                GITHUB_REPOSITORY_FULL_NAME: "username/someRepo",
                optionsId: "1",
              }),
            ],
          }}
          update={() => {}}
        />,
      );

      fireEvent.click(screen.getByRole("button"));

      fireEvent.click(screen.getByRole("button", { name: "common:actions.remove" }));
      expect(mockAxios.history.post.length).toBe(1);
      expect(mockAxios.history.post[0].params.get("appName")).toEqual("GITHUB");

      expect(mockAxios.history.post[0].data.get("optionsId")).toBe("1");

      const table = screen.getByRole("table");
      await waitFor(() => {
        expect(within(table).queryByText("username/someRepo")).not.toBeInTheDocument();
      });
    });
    test("Removing a repository should mutate the integration state being passed as a prop.", async () => {
      const integrationState = observable({
        mode: "DISABLED" as const,
        credentials: [
          Optional.present({
            GITHUB_REPOSITORY_FULL_NAME: "username/someRepo",
            optionsId: "1",
          }),
        ],
      });
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

      render(<GitHub integrationState={integrationState} update={() => {}} />);

      fireEvent.click(screen.getByRole("button"));

      fireEvent.click(screen.getByRole("button", { name: "common:actions.remove" }));
      await waitFor(() => {
        expect(screen.queryByText("username/someRepo")).not.toBeInTheDocument();
      });
      expect(integrationState.credentials.length).toBe(0);
    });
    test("Removing the last repository asks for GitHub authorisation again before another can be added.", async () => {
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
      render(<GitHub integrationState={observable({ mode: "DISABLED", credentials: [] })} update={() => {}} />);

      fireEvent.click(screen.getByRole("button"));
      fireEvent.click(screen.getByRole("button", { name: "common:actions.add" }));
      act(() => {
        for (const handler of broadcastHandlers)
          handler({ data: { type: "GITHUB_CONNECTED" } } as MessageEvent<GitHubConnectedMessage>);
      });
      await waitFor(() => {
        expect(screen.getAllByRole("table").length).toBe(2);
      });
      fireEvent.click(within(screen.getAllByRole("table")[1]).getByRole("button", { name: "common:actions.add" }));
      fireEvent.click(await screen.findByRole("button", { name: "common:actions.remove" }));

      await waitFor(() => {
        expect(screen.getAllByRole("table").length).toBe(1);
      });
      fireEvent.click(screen.getByRole("button", { name: "common:actions.add" }));
      await waitFor(() => {
        expect(openWindow).toHaveBeenCalledTimes(2);
      });
    });
  });
});
