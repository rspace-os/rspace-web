/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import React from "react";
import {
  cleanup,
  screen,
  fireEvent,
  waitFor,
  render,
} from "@testing-library/react";
import "@testing-library/jest-dom";
import Slack from "../Slack";
import MockAdapter from "axios-mock-adapter";
import axios from "@/common/axios";
import { observable } from "mobx";
import Alerts from "../../../../components/Alerts/Alerts";
import { type IntegrationStates } from "../../useIntegrationsEndpoint";
import { Optional } from "../../../../util/optional";
import { axe, toHaveNoViolations } from "jest-axe";
import "../../../../../__mocks__/matchMedia";

expect.extend(toHaveNoViolations);

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

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
        />
      );

      fireEvent.click(screen.getByRole("button"));

      expect(await screen.findByRole("dialog")).toBeVisible();

      expect(await axe(baseElement)).toHaveNoViolations();
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
            SLACK_USER_ACCESS_TOKEN:
              "xoxp-59281887730-1344278245907-6168781142897-b182fa0fca1f05b6ed1a3055dae34a18",
            SLACK_TEAM_ID: "T1R89S3MG",
            SLACK_WEBHOOK_URL:
              "https://hooks.slack.com/services/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",
          },
        },
      },
    });

    const channelDetails = {
      ok: true,
      access_token:
        "xoxp-59281887730-1344278245907-6168781142897-b182fa0fca1f05b6ed1a3055dae34a18",
      scope:
        "identify,commands,incoming-webhook,channels:history,groups:history,im:history,mpim:history,files:read,users:read,users:read.email",
      user_id: "U01A48677SP",
      team_id: "T1R89S3MG",
      enterprise_id: null,
      team_name: "RSpace Dev",
      incoming_webhook: {
        channel: "#rspace-slackpost-test",
        channel_id: "CQ391L249",
        configuration_url: "https://rspacedev.slack.com/services/B064623LHHD",
        url: "https://hooks.slack.com/services/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",
      },
      response_metadata: {
        messages: [
          "[WARN] Auth request OAuth redirect url must use https",
          "[WARN] Registered OAuth redirect url must use https",
        ],
      },
    };
    jest.spyOn(window, "open").mockImplementation(
      () =>
        ({
          document: {
            URL: "https://test.researchspace.com/slack/redirect_uri",
            getElementById: () => ({ value: JSON.stringify(channelDetails) }),
          },
          addEventListener: (_: unknown, f: () => void) => {
            f();
          },
          removeEventListener: () => {},
          close: () => {},
        } as unknown as Window)
    );
    jest
      .spyOn(window, "setInterval")
      .mockImplementation((f) => f() as unknown as NodeJS.Timeout);
  });
  test("When the add flow is triggered, the channel details should be shown.", async () => {
    const integrationState = observable<IntegrationStates["SLACK"]>({
      mode: "DISABLED",
      credentials: [],
    });
    render(
      <Alerts>
        <Slack integrationState={integrationState} update={() => {}} />
      </Alerts>
    );

    fireEvent.click(screen.getByRole("button"));

    fireEvent.click(screen.getByRole("button", { name: /add/i }));

    await waitFor(() => {
      expect(screen.getByText(/RSpace Dev/)).toBeVisible();
    });

    fireEvent.change(screen.getByRole("textbox", { name: /rspace label/i }), {
      target: { value: "custom label" },
    });

    fireEvent.click(screen.getByRole("button", { name: /save/i }));

    expect(
      await screen.findByRole("alert", { name: /Successfully/ })
    ).toBeVisible();

    expect(integrationState.credentials.length).toBe(1);
  });
  test("When the add flow is triggered, there should be a cancel button.", async () => {
    render(
      <Alerts>
        <Slack
          integrationState={{
            mode: "DISABLED",
            credentials: [],
          }}
          update={() => {}}
        />
      </Alerts>
    );

    fireEvent.click(screen.getByRole("button"));

    fireEvent.click(screen.getByRole("button", { name: /add/i }));

    await waitFor(() => {
      expect(screen.getByText(/RSpace Dev/)).toBeVisible();
    });

    fireEvent.click(screen.getByRole("button", { name: /cancel/i }));

    await waitFor(() => {
      expect(screen.queryByText(/RSpace Dev/)).not.toBeInTheDocument();
    });

    expect(screen.getByRole("button", { name: /add/i })).toBeVisible();
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
                SLACK_USER_ACCESS_TOKEN:
                  "xoxp-59281887730-1344278245907-6168781142897-b182fa0fca1f05b6ed1a3055dae34a18",
                SLACK_TEAM_ID: "T1R89S3MG",
                SLACK_WEBHOOK_URL:
                  "https://hooks.slack.com/services/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",
                optionsId: "1",
              }),
            ],
          }}
          update={() => {}}
        />
      </Alerts>
    );

    fireEvent.click(screen.getByRole("button"));

    expect(screen.getAllByRole("definition")[0]).toHaveTextContent(
      "RSpace Dev"
    );
    expect(screen.getAllByRole("definition")[1]).toHaveTextContent(
      "#rspace-slackpost-test"
    );
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
            SLACK_USER_ACCESS_TOKEN:
              "xoxp-59281887730-1344278245907-6168781142897-b182fa0fca1f05b6ed1a3055dae34a18",
            SLACK_TEAM_ID: "T1R89S3MG",
            SLACK_WEBHOOK_URL:
              "https://hooks.slack.com/services/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",
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
                SLACK_USER_ACCESS_TOKEN:
                  "xoxp-59281887730-1344278245907-6168781142897-b182fa0fca1f05b6ed1a3055dae34a18",
                SLACK_TEAM_ID: "T1R89S3MG",
                SLACK_WEBHOOK_URL:
                  "https://hooks.slack.com/services/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",
                optionsId: "1",
              }),
            ],
          }}
          update={() => {}}
        />
      </Alerts>
    );

    fireEvent.click(screen.getByRole("button"));

    fireEvent.change(screen.getByRole("textbox"), {
      target: { value: "custom label" },
    });

    fireEvent.click(screen.getByRole("button", { name: /save/i }));

    expect(
      await screen.findByRole("alert", { name: /Successfully/ })
    ).toBeVisible();

    expect(mockAxios.history.post.length).toBe(1);
    expect(mockAxios.history.post[0].params.get("appName")).toEqual("SLACK");
    expect(mockAxios.history.post[0].params.get("optionsId")).toEqual("1");
    expect(JSON.parse(mockAxios.history.post[0].data)).toEqual(
      expect.objectContaining({
        SLACK_CHANNEL_LABEL: "custom label",
      })
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
            SLACK_USER_ACCESS_TOKEN:
              "xoxp-59281887730-1344278245907-6168781142897-b182fa0fca1f05b6ed1a3055dae34a18",
            SLACK_TEAM_ID: "T1R89S3MG",
            SLACK_WEBHOOK_URL:
              "https://hooks.slack.com/services/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",
          },
          "2": {
            SLACK_TEAM_NAME: "RSpace Dev",
            SLACK_CHANNEL_ID: "CQ391L249",
            SLACK_CHANNEL_NAME: "#rspace-slackpost-test",
            SLACK_USER_ID: "U01A48677SP",
            SLACK_CHANNEL_LABEL: "custom label",
            SLACK_USER_ACCESS_TOKEN:
              "xoxp-59281887730-1344278245907-6168781142897-b182fa0fca1f05b6ed1a3055dae34a18",
            SLACK_TEAM_ID: "T1R89S3MG",
            SLACK_WEBHOOK_URL:
              "https://hooks.slack.com/services/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",
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
                SLACK_USER_ACCESS_TOKEN:
                  "xoxp-59281887730-1344278245907-6168781142897-b182fa0fca1f05b6ed1a3055dae34a18",
                SLACK_TEAM_ID: "T1R89S3MG",
                SLACK_WEBHOOK_URL:
                  "https://hooks.slack.com/services/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",
                optionsId: "1",
              }),
              Optional.present({
                SLACK_TEAM_NAME: "RSpace Dev",
                SLACK_CHANNEL_ID: "CQ391L249",
                SLACK_CHANNEL_NAME: "#rspace-slackpost-test",
                SLACK_USER_ID: "U01A48677SP",
                SLACK_CHANNEL_LABEL: "old label",
                SLACK_USER_ACCESS_TOKEN:
                  "xoxp-59281887730-1344278245907-6168781142897-b182fa0fca1f05b6ed1a3055dae34a18",
                SLACK_TEAM_ID: "T1R89S3MG",
                SLACK_WEBHOOK_URL:
                  "https://hooks.slack.com/services/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",
                optionsId: "2",
              }),
            ],
          }}
          update={() => {}}
        />
      </Alerts>
    );

    fireEvent.click(screen.getByRole("button"));

    fireEvent.change(screen.getAllByRole("textbox")[0], {
      target: { value: "custom label" },
    });

    fireEvent.change(screen.getAllByRole("textbox")[1], {
      target: { value: "also custom label" },
    });

    fireEvent.click(screen.getAllByRole("button", { name: /save/i })[0]);

    expect(
      await screen.findByRole("alert", { name: /Successfully/ })
    ).toBeVisible();

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
          SLACK_USER_ACCESS_TOKEN:
            "xoxp-59281887730-1344278245907-6168781142897-b182fa0fca1f05b6ed1a3055dae34a18",
          SLACK_TEAM_ID: "T1R89S3MG",
          SLACK_WEBHOOK_URL:
            "https://hooks.slack.com/services/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",
          optionsId: "1",
        }),
      ],
    });
    render(
      <Alerts>
        <Slack integrationState={integrationState} update={() => {}} />
      </Alerts>
    );

    fireEvent.click(screen.getByRole("button"));

    fireEvent.click(screen.getByRole("button", { name: /remove/i }));

    expect(
      await screen.findByRole("alert", { name: /Successfully/ })
    ).toBeVisible();

    expect(mockAxios.history.post.length).toBe(1);
    expect(mockAxios.history.post[0].params.get("appName")).toEqual("SLACK");
    expect(mockAxios.history.post[0].data.get("optionsId")).toBe("1");

    expect(integrationState.credentials.length).toBe(0);
  });
});
