import { fireEvent, render, screen } from "@testing-library/react";
import MockAdapter from "axios-mock-adapter";
import { observable } from "mobx";
import { beforeEach, describe, expect, test, vi } from "vitest";
import axios from "@/common/axios";
import allIntegrationsAreDisabled from "@/eln/apps/__tests__/allIntegrationsAreDisabled.json";
import Alerts from "../../../../components/Alerts/Alerts";
import { Optional } from "../../../../util/optional";
import type { IntegrationStates } from "../../useIntegrationsEndpoint";
import Slack, { SLACK_CONNECTION_CHANNEL } from "../Slack";

import "@/__tests__/__mocks__/matchMedia";

describe("Slack", () => {
  describe("Accessibility", () => {
    test("Should have no axe violations.", async () => {
      const { baseElement } = render(
        <Slack
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
  beforeEach(() => {
    const mockAxios = new MockAdapter(axios);
    mockAxios.onGet("slack/oauthUrl").reply(200, {
      success: true,
      data: "https://slack.com/oauth/authorize?scope=incoming-webhook,commands,channels:history,users:read,files:read,groups:history,im:history,mpim:history&client_id=foo",
      error: null,
    });
    mockAxios.onPost("integration/saveAppOptions").reply(200, {
      success: true,
      data: {
        available: true,
        enabled: false,
        name: "SLACK",
        options: {
          "1": {
            SLACK_TEAM_NAME: "RSpace Dev",
            SLACK_CHANNEL_ID: "CQ391L249",
            SLACK_CHANNEL_NAME: "#rspace-slackpost-test",
            SLACK_USER_ID: "U01A48677SP",
            SLACK_CHANNEL_LABEL: "custom label",
            SLACK_TEAM_ID: "T1R89S3MG",
          },
        },
      },
    });
    vi.spyOn(window, "open").mockReturnValue({
      close: () => {},
    } as unknown as Window);
  });
  test("When Slack connects, the channel the server saved should be shown.", async () => {
    const mockAxios = new MockAdapter(axios);
    mockAxios
      .onGet("slack/oauthUrl")
      .reply(200, { success: true, data: "https://slack.com/oauth/authorize", error: null });
    mockAxios.onGet("integration/allIntegrations").reply(200, {
      success: true,
      data: {
        ...allIntegrationsAreDisabled.data,
        SLACK: {
          ...allIntegrationsAreDisabled.data.SLACK,
          options: {
            "1": {
              SLACK_TEAM_NAME: "RSpace Dev",
              SLACK_CHANNEL_ID: "CQ391L249",
              SLACK_CHANNEL_NAME: "#rspace-slackpost-test",
              SLACK_USER_ID: "U01A48677SP",
              SLACK_CHANNEL_LABEL: "#rspace-slackpost-test",
              SLACK_TEAM_ID: "T1R89S3MG",
            },
          },
        },
      },
      error: null,
    });
    const integrationState = observable<IntegrationStates["SLACK"]>({
      mode: "DISABLED",
      credentials: [],
    });
    render(
      <Alerts>
        <Slack integrationState={integrationState} update={() => {}} />
      </Alerts>,
    );

    fireEvent.click(screen.getByRole("button"));
    fireEvent.click(screen.getByRole("button", { name: "common:actions.add" }));

    // Simulate BroadcastChannel message from the OAuth redirect page
    const bc = new BroadcastChannel(SLACK_CONNECTION_CHANNEL);
    bc.postMessage({ type: "SLACK_CONNECTED" });
    bc.close();

    expect(await screen.findByRole("alert", { name: "apps:integrations.slack.alerts.addSuccess" })).toBeVisible();
    expect(screen.getByText("RSpace Dev")).toBeVisible();
    expect(integrationState.credentials.length).toBe(1);
    expect(mockAxios.history.post.length).toBe(0);
  });
  test("Should render the existing channels.", () => {
    render(
      <Alerts>
        <Slack
          integrationState={{
            mode: "DISABLED",
            credentials: [
              Optional.present({
                SLACK_TEAM_NAME: "RSpace Dev",
                SLACK_CHANNEL_ID: "CQ391L249",
                SLACK_CHANNEL_NAME: "#rspace-slackpost-test",
                SLACK_USER_ID: "U01A48677SP",
                SLACK_CHANNEL_LABEL: "custom label",
                SLACK_TEAM_ID: "T1R89S3MG",
                optionsId: "1",
              }),
            ],
          }}
          update={() => {}}
        />
      </Alerts>,
    );

    fireEvent.click(screen.getByRole("button"));
    expect(screen.getAllByRole("definition")[0]).toHaveTextContent("RSpace Dev");
    expect(screen.getAllByRole("definition")[1]).toHaveTextContent("#rspace-slackpost-test");
    expect(screen.getByRole("textbox")).toHaveValue("custom label");
  });
  test("Channel label should be changeable.", async () => {
    const mockAxios = new MockAdapter(axios);
    mockAxios.onPost("integration/saveAppOptions").reply(200, {
      success: true,
      data: {
        available: true,
        enabled: false,
        name: "SLACK",
        options: {
          "1": {
            SLACK_TEAM_NAME: "RSpace Dev",
            SLACK_CHANNEL_ID: "CQ391L249",
            SLACK_CHANNEL_NAME: "#rspace-slackpost-test",
            SLACK_USER_ID: "U01A48677SP",
            SLACK_CHANNEL_LABEL: "custom label",
            SLACK_TEAM_ID: "T1R89S3MG",
          },
        },
      },
    });
    render(
      <Alerts>
        <Slack
          integrationState={{
            mode: "DISABLED",
            credentials: [
              Optional.present({
                SLACK_TEAM_NAME: "RSpace Dev",
                SLACK_CHANNEL_ID: "CQ391L249",
                SLACK_CHANNEL_NAME: "#rspace-slackpost-test",
                SLACK_USER_ID: "U01A48677SP",
                SLACK_CHANNEL_LABEL: "old label",
                SLACK_TEAM_ID: "T1R89S3MG",
                optionsId: "1",
              }),
            ],
          }}
          update={() => {}}
        />
      </Alerts>,
    );

    fireEvent.click(screen.getByRole("button"));
    fireEvent.change(screen.getByRole("textbox"), {
      target: { value: "custom label" },
    });

    fireEvent.click(screen.getByRole("button", { name: "common:actions.save" }));
    expect(await screen.findByRole("alert", { name: "apps:integrations.slack.alerts.labelSuccess" })).toBeVisible();
    expect(mockAxios.history.post.length).toBe(1);
    expect(mockAxios.history.post[0].params.get("appName")).toEqual("SLACK");
    expect(mockAxios.history.post[0].params.get("optionsId")).toEqual("1");
    expect(JSON.parse(mockAxios.history.post[0].data)).toEqual(
      expect.objectContaining({
        SLACK_CHANNEL_LABEL: "custom label",
      }),
    );
  });
  test("Saving changes to one channel should not overrwrite changes to other.", async () => {
    const mockAxios = new MockAdapter(axios);
    mockAxios.onPost("integration/saveAppOptions").reply(200, {
      success: true,
      data: {
        available: true,
        enabled: false,
        name: "SLACK",
        options: {
          "1": {
            SLACK_TEAM_NAME: "RSpace Dev",
            SLACK_CHANNEL_ID: "CQ391L249",
            SLACK_CHANNEL_NAME: "#rspace-slackpost-test",
            SLACK_USER_ID: "U01A48677SP",
            SLACK_CHANNEL_LABEL: "custom label",
            SLACK_TEAM_ID: "T1R89S3MG",
          },
          "2": {
            SLACK_TEAM_NAME: "RSpace Dev",
            SLACK_CHANNEL_ID: "CQ391L249",
            SLACK_CHANNEL_NAME: "#rspace-slackpost-test",
            SLACK_USER_ID: "U01A48677SP",
            SLACK_CHANNEL_LABEL: "custom label",
            SLACK_TEAM_ID: "T1R89S3MG",
          },
        },
      },
    });
    render(
      <Alerts>
        <Slack
          integrationState={{
            mode: "DISABLED",
            credentials: [
              Optional.present({
                SLACK_TEAM_NAME: "RSpace Dev",
                SLACK_CHANNEL_ID: "CQ391L249",
                SLACK_CHANNEL_NAME: "#rspace-slackpost-test",
                SLACK_USER_ID: "U01A48677SP",
                SLACK_CHANNEL_LABEL: "old label",
                SLACK_TEAM_ID: "T1R89S3MG",
                optionsId: "1",
              }),
              Optional.present({
                SLACK_TEAM_NAME: "RSpace Dev",
                SLACK_CHANNEL_ID: "CQ391L249",
                SLACK_CHANNEL_NAME: "#rspace-slackpost-test",
                SLACK_USER_ID: "U01A48677SP",
                SLACK_CHANNEL_LABEL: "old label",
                SLACK_TEAM_ID: "T1R89S3MG",
                optionsId: "2",
              }),
            ],
          }}
          update={() => {}}
        />
      </Alerts>,
    );

    fireEvent.click(screen.getByRole("button"));
    fireEvent.change(screen.getAllByRole("textbox")[0], {
      target: { value: "custom label" },
    });
    fireEvent.change(screen.getAllByRole("textbox")[1], {
      target: { value: "also custom label" },
    });

    fireEvent.click(screen.getAllByRole("button", { name: "common:actions.save" })[0]);
    expect(await screen.findByRole("alert", { name: "apps:integrations.slack.alerts.labelSuccess" })).toBeVisible();
    expect(screen.getAllByRole("textbox")[1]).toHaveValue("also custom label");
  });
  test("Deleting a channel should make the right API call.", async () => {
    const mockAxios = new MockAdapter(axios);
    mockAxios.onPost("integration/deleteAppOptions").reply(200, {
      success: true,
      data: {
        available: true,
        enabled: false,
        name: "SLACK",
        options: {},
      },
    });
    const integrationState = observable({
      mode: "DISABLED" as const,
      credentials: [
        Optional.present({
          SLACK_TEAM_NAME: "RSpace Dev",
          SLACK_CHANNEL_ID: "CQ391L249",
          SLACK_CHANNEL_NAME: "#rspace-slackpost-test",
          SLACK_USER_ID: "U01A48677SP",
          SLACK_CHANNEL_LABEL: "old label",
          SLACK_TEAM_ID: "T1R89S3MG",
          optionsId: "1",
        }),
      ],
    });
    render(
      <Alerts>
        <Slack integrationState={integrationState} update={() => {}} />
      </Alerts>,
    );

    fireEvent.click(screen.getByRole("button"));

    fireEvent.click(screen.getByRole("button", { name: "common:actions.remove" }));
    expect(await screen.findByRole("alert", { name: "apps:integrations.slack.alerts.deleteSuccess" })).toBeVisible();
    expect(mockAxios.history.post.length).toBe(1);
    expect(mockAxios.history.post[0].params.get("appName")).toEqual("SLACK");

    expect(mockAxios.history.post[0].data.get("optionsId")).toBe("1");
    expect(integrationState.credentials.length).toBe(0);
  });
});
