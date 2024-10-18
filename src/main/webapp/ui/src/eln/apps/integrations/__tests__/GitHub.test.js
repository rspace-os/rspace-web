/*
 * @jest-environment jsdom
 */
//@flow strict
/* eslint-env jest */
import React from "react";
import { cleanup, screen, fireEvent, waitFor } from "@testing-library/react";
import "@testing-library/jest-dom";
import GitHub from "../GitHub";
import { Optional } from "../../../../util/optional";
import MockAdapter from "axios-mock-adapter";
import * as axios from "axios";
import { observable } from "mobx";
import { render, within } from "../../../../__tests__/customQueries";
import { type IntegrationStates } from "../../useIntegrationsEndpoint";
import { axe, toHaveNoViolations } from "jest-axe";
import "../../../../../__mocks__/matchMedia";

expect.extend(toHaveNoViolations);

beforeEach(() => {
  jest.clearAllMocks();
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
        />
      );

      fireEvent.click(screen.getByRole("button"));

      expect(await screen.findByRole("dialog")).toBeVisible();

      // $FlowExpectedError[incompatible-call] See expect.extend above
      expect(await axe(baseElement)).toHaveNoViolations();
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
        />
      );

      fireEvent.click(screen.getByRole("button"));

      expect(
        screen.getByText("There are no linked repositories.")
      ).toBeVisible();
    });
    test("The names of repositories should be shown in a table.", () => {
      render(
        <GitHub
          integrationState={{
            mode: "DISABLED",
            credentials: [
              Optional.present({
                GITHUB_ACCESS_TOKEN: Optional.present("access token"),
                GITHUB_REPOSITORY_FULL_NAME: "username/someRepo",
                optionsId: "1",
              }),
            ],
          }}
          update={() => {}}
        />
      );

      fireEvent.click(screen.getByRole("button"));

      expect(
        within(screen.getByRole("table")).getByText("username/someRepo")
      ).toBeVisible();
    });

    test("If the server responds with a missing ACCESS_TOKEN then the repo should be shown in an invalid state", () => {
      render(
        <GitHub
          integrationState={{
            mode: "DISABLED",
            credentials: [
              Optional.present({
                GITHUB_ACCESS_TOKEN: Optional.empty(),
                GITHUB_REPOSITORY_FULL_NAME: "username/someRepo",
                optionsId: "1",
              }),
            ],
          }}
          update={() => {}}
        />
      );

      fireEvent.click(screen.getByRole("button"));

      expect(
        within(screen.getByRole("table")).getByText("username/someRepo")
      ).toBeVisible();

      expect(
        screen.getByText(
          "Repository is in an invalid state. Please remove and re-add."
        )
      ).toBeVisible();
    });
  });

  describe("Adding repositories", () => {
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

      jest.spyOn(window, "open").mockImplementation(() => ({
        document: {
          URL: "https://test.researchspace.com/github/redirect_uri",
          getElementById: () => ({ value: "oauth token" }),
        },
        addEventListener: (_, f) => {
          f();
        },
        removeEventListener: () => {},
        close: () => {},
      }));

      render(
        <GitHub
          integrationState={{
            mode: "DISABLED",
            credentials: [],
          }}
          update={() => {}}
        />
      );

      fireEvent.click(screen.getByRole("button"));

      fireEvent.click(screen.getByRole("button", { name: /add/i }));

      await waitFor(() => {
        expect(screen.getAllByRole("table").length).toBe(2);
      });

      const newReposTable = screen.getAllByRole("table")[1];
      expect(
        await within(newReposTable).findTableCell({
          columnHeading: "Repository Name",
          rowIndex: 0,
        })
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

      jest.spyOn(window, "open").mockImplementation(() => ({
        document: {
          URL: "https://test.researchspace.com/github/redirect_uri",
          getElementById: () => ({ value: "oauth token" }),
        },
        addEventListener: (_, f) => {
          f();
        },
        removeEventListener: () => {},
        close: () => {},
      }));

      render(
        <GitHub
          integrationState={{
            mode: "DISABLED",
            credentials: [],
          }}
          update={() => {}}
        />
      );

      fireEvent.click(screen.getByRole("button"));

      fireEvent.click(screen.getByRole("button", { name: /add/i }));

      await waitFor(() => {
        expect(screen.getAllByRole("table").length).toBe(2);
      });

      const allReposTable = screen.getAllByRole("table")[1];
      fireEvent.click(
        within(
          within(within(allReposTable).getAllByRole("row")[1]).getAllByRole(
            "cell"
          )[1]
        ).getByRole("button", { name: /add/i })
      );

      expect(mockAxios.history.post.length).toBe(1);
      expect(mockAxios.history.post[0].params.get("appName")).toEqual("GITHUB");
      expect(JSON.parse(mockAxios.history.post[0].data)).toEqual({
        GITHUB_REPOSITORY_FULL_NAME: "a repo",
        GITHUB_ACCESS_TOKEN: "oauth token",
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
              GITHUB_ACCESS_TOKEN: "oauth token",
            },
          },
        },
      });

      jest.spyOn(window, "open").mockImplementation(() => ({
        document: {
          URL: "https://test.researchspace.com/github/redirect_uri",
          getElementById: () => ({ value: "oauth token" }),
        },
        addEventListener: (_, f) => {
          f();
        },
        removeEventListener: () => {},
        close: () => {},
      }));

      render(
        <GitHub
          integrationState={observable({
            mode: "DISABLED",
            credentials: [],
          })}
          update={() => {}}
        />
      );

      fireEvent.click(screen.getByRole("button"));

      fireEvent.click(screen.getByRole("button", { name: /add/i }));

      await waitFor(() => {
        expect(screen.getAllByRole("table").length).toBe(2);
      });

      const allReposTable = screen.getAllByRole("table")[1];
      fireEvent.click(
        within(
          within(within(allReposTable).getAllByRole("row")[1]).getAllByRole(
            "cell"
          )[1]
        ).getByRole("button", { name: /add/i })
      );

      await waitFor(() => {
        expect(
          screen.queryByText("There are no linked repositories.")
        ).not.toBeInTheDocument();
      });

      const connectedReposTable = screen.getAllByRole("table")[0];
      expect(
        await within(connectedReposTable).findTableCell({
          columnHeading: "Repository Name",
          rowIndex: 0,
        })
      ).toHaveTextContent("a repo");

      expect(
        await within(allReposTable).findTableCell({
          columnHeading: "Repository Name",
          rowIndex: 0,
        })
      ).toHaveTextContent("There are no available repositories.");
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
              GITHUB_ACCESS_TOKEN: "oauth token",
            },
          },
        },
      });

      jest.spyOn(window, "open").mockImplementation(() => ({
        document: {
          URL: "https://test.researchspace.com/github/redirect_uri",
          getElementById: () => ({ value: "oauth token" }),
        },
        addEventListener: (_, f) => {
          f();
        },
        removeEventListener: () => {},
        close: () => {},
      }));

      render(<GitHub integrationState={integrationState} update={() => {}} />);

      fireEvent.click(screen.getByRole("button"));

      fireEvent.click(screen.getByRole("button", { name: /add/i }));

      await waitFor(() => {
        expect(screen.getAllByRole("table").length).toBe(2);
      });

      const allReposTable = screen.getAllByRole("table")[1];
      fireEvent.click(
        within(
          within(within(allReposTable).getAllByRole("row")[1]).getAllByRole(
            "cell"
          )[1]
        ).getByRole("button", { name: /add/i })
      );

      await waitFor(() => {
        expect(
          screen.queryByText("There are no linked repositories.")
        ).not.toBeInTheDocument();
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
                GITHUB_ACCESS_TOKEN: Optional.empty(),
                GITHUB_REPOSITORY_FULL_NAME: "username/someRepo",
                optionsId: "1",
              }),
            ],
          }}
          update={() => {}}
        />
      );

      fireEvent.click(screen.getByRole("button"));

      fireEvent.click(screen.getByRole("button", { name: /remove/i }));

      expect(mockAxios.history.post.length).toBe(1);
      expect(mockAxios.history.post[0].params.get("appName")).toEqual("GITHUB");
      expect(mockAxios.history.post[0].data.get("optionsId")).toBe("1");

      const table = screen.getByRole("table");

      await waitFor(() => {
        expect(
          within(table).queryByText("username/someRepo")
        ).not.toBeInTheDocument();
      });
    });
    test("Removing a repository should mutate the integration state being passed as a prop.", async () => {
      const integrationState = observable({
        mode: "DISABLED",
        credentials: [
          Optional.present({
            GITHUB_ACCESS_TOKEN: Optional.present("access token"),
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

      fireEvent.click(screen.getByRole("button", { name: /remove/i }));

      await waitFor(() => {
        expect(screen.queryByText("username/someRepo")).not.toBeInTheDocument();
      });

      expect(integrationState.credentials.length).toBe(0);
    });
  });
});
