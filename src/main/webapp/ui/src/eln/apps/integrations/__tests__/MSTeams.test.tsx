import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import MockAdapter from "axios-mock-adapter";
import { describe, expect, test } from "vitest";

import { findTableCell } from "@/__tests__/tableQueries";
import axios from "@/common/axios";
import Alerts from "../../../../components/Alerts/Alerts";
import { Optional } from "../../../../util/optional";
import type { IntegrationStates } from "../../useIntegrationsEndpoint";
import MSTeams from "../MSTeams";

import "@/__tests__/__mocks__/matchMedia";
import { cachedState, createIntegrationsClient, WithIntegrations } from "../../__tests__/integrationsQueryClient";

describe("MSTeams", () => {
  describe("Should render correctly.", () => {
    test("Channel names should be shown in a table.", async () => {
      const user = userEvent.setup();
      render(
        <WithIntegrations
          client={createIntegrationsClient({
            MSTEAMS: {
              mode: "DISABLED",
              credentials: [
                Optional.present({
                  optionsId: "1",
                  MSTEAMS_CHANNEL_LABEL: "foo",
                  MSTEAMS_WEBHOOK_URL: "example.com",
                }),
              ],
            },
          })}
        >
          <MSTeams />
        </WithIntegrations>,
      );

      await user.click(screen.getByRole("button"));
      const table = screen.getByRole("table");
      expect(
        await findTableCell(table, {
          columnHeading: "apps:integrations.msteams.tableHeader",
          rowIndex: 0,
        }),
      ).toHaveTextContent("foo");
    });
    test("Remove button should make the right API call.", async () => {
      const user = userEvent.setup();
      const mockAxios = new MockAdapter(axios);
      mockAxios.onPost("integration/deleteAppOptions").reply(200, {
        success: true,
        data: {
          available: true,
          enabled: false,
          name: "MSTEAMS",
          options: {},
        },
      });
      const integrationState: IntegrationStates["MSTEAMS"] = {
        mode: "DISABLED",
        credentials: [
          Optional.present({
            optionsId: "1",
            MSTEAMS_CHANNEL_LABEL: "foo",
            MSTEAMS_WEBHOOK_URL: "example.com",
          }),
        ],
      };
      const queryClient = createIntegrationsClient({ MSTEAMS: integrationState });
      render(
        <Alerts>
          <WithIntegrations client={queryClient}>
            <MSTeams />
          </WithIntegrations>
        </Alerts>,
      );

      await user.click(screen.getByRole("button"));

      await user.click(screen.getByRole("button", { name: "common:actions.remove" }));
      expect(mockAxios.history.post.length).toBe(1);
      expect(mockAxios.history.post[0].params.get("appName")).toEqual("MSTEAMS");

      expect(mockAxios.history.post[0].data.get("optionsId")).toBe("1");
      expect(
        await screen.findByRole("alert", { name: "apps:integrations.msteams.alerts.removeSuccess" }),
      ).toBeVisible();

      const table = screen.getByRole("table");
      await waitFor(() => {
        expect(within(table).queryByText("foo")).not.toBeInTheDocument();
      });
      expect(cachedState(queryClient, "MSTEAMS").credentials.length).toBe(0);
    });
    test("Add button should make the right API call.", async () => {
      const user = userEvent.setup();
      const mockAxios = new MockAdapter(axios);
      mockAxios.onPost("integration/saveAppOptions").reply(200, {
        success: true,
        data: {
          available: true,
          enabled: false,
          name: "MSTEAMS",
          options: {
            "1": {
              MSTEAMS_CHANNEL_LABEL: "new name",
              MSTEAMS_WEBHOOK_URL: "example.com",
            },
          },
        },
      });
      const integrationState: IntegrationStates["MSTEAMS"] = {
        mode: "DISABLED",
        credentials: [],
      };
      const queryClient = createIntegrationsClient({ MSTEAMS: integrationState });
      render(
        <Alerts>
          <WithIntegrations client={queryClient}>
            <MSTeams />
          </WithIntegrations>
        </Alerts>,
      );

      await user.click(screen.getByRole("button"));

      await user.click(screen.getByRole("button", { name: "common:actions.add" }));
      await waitFor(() => {
        expect(screen.queryByRole("button", { name: "common:actions.add" })).not.toBeInTheDocument();
      });
      const channelNameInput = screen.getByRole("textbox", { name: "apps:integrations.msteams.fields.channelName" });
      await user.clear(channelNameInput);
      await user.type(channelNameInput, "new name");
      const webhookField = screen.getByLabelText("apps:integrations.msteams.fields.webhookUrl");
      expect(webhookField).toHaveAttribute("type", "password");
      await user.clear(webhookField);
      await user.type(webhookField, "example.com");

      await user.click(screen.getByRole("button", { name: "common:actions.save" }));
      expect(await screen.findByRole("alert", { name: "apps:integrations.msteams.alerts.addSuccess" })).toBeVisible();
      expect(mockAxios.history.post.length).toBe(1);
      expect(mockAxios.history.post[0].params.get("appName")).toEqual("MSTEAMS");
      expect(JSON.parse(mockAxios.history.post[0].data)).toEqual({
        MSTEAMS_CHANNEL_LABEL: "new name",
        MSTEAMS_WEBHOOK_URL: "example.com",
      });

      expect(cachedState(queryClient, "MSTEAMS").credentials.length).toBe(1);
      expect(within(screen.getByRole("table")).getByText("new name")).toBeVisible();
      expect(screen.getByRole("button", { name: "common:actions.add" })).toBeVisible();
    });
  });
});
