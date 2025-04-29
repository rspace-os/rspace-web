/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import React from "react";
import { cleanup, screen, fireEvent, waitFor } from "@testing-library/react";
import "@testing-library/jest-dom";
import MSTeams from "../MSTeams";
import { Optional } from "../../../../util/optional";
import { render, within } from "../../../../__tests__/customQueries";
import MockAdapter from "axios-mock-adapter";
import axios from "@/common/axios";
import Alerts from "../../../../components/Alerts/Alerts";
import { observable } from "mobx";
import { type IntegrationStates } from "../../useIntegrationsEndpoint";
import "../../../../../__mocks__/matchMedia";

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("MSTeams", () => {
  describe("Should render correctly.", () => {
    test("Channel names should be shown in a table.", async () => {
      render(
        <MSTeams
          integrationState={{
            mode: "DISABLED",
            credentials: [
              Optional.present({
                optionsId: "1",
                MSTEAMS_CHANNEL_LABEL: "foo",
                MSTEAMS_WEBHOOK_URL: "example.com",
              }),
            ],
          }}
          update={() => {}}
        />
      );

      fireEvent.click(screen.getByRole("button"));

      const table = screen.getByRole("table");
      expect(
        // @ts-expect-error findTableCell comes from customQueries
        await within(table).findTableCell({
          columnHeading: "Channel Connector Name",
          rowIndex: 0,
        })
      ).toHaveTextContent("foo");
    });
    test("Remove button should make the right API call.", async () => {
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

      const integrationState = observable({
        mode: "DISABLED" as const,
        credentials: [
          Optional.present({
            optionsId: "1",
            MSTEAMS_CHANNEL_LABEL: "foo",
            MSTEAMS_WEBHOOK_URL: "example.com",
          }),
        ],
      });
      render(
        <Alerts>
          <MSTeams integrationState={integrationState} update={() => {}} />
        </Alerts>
      );

      fireEvent.click(screen.getByRole("button"));

      fireEvent.click(screen.getByRole("button", { name: /remove/i }));

      expect(mockAxios.history.post.length).toBe(1);
      expect(mockAxios.history.post[0].params.get("appName")).toEqual(
        "MSTEAMS"
      );
      expect(mockAxios.history.post[0].data.get("optionsId")).toBe("1");

      expect(
        await screen.findByRole("alert", { name: /Successfully/ })
      ).toBeVisible();

      const table = screen.getByRole("table");

      await waitFor(() => {
        expect(
          within(table).queryByText("username/someRepo")
        ).not.toBeInTheDocument();
      });

      expect(integrationState.credentials.length).toBe(0);
    });
    test("Add button should make the right API call.", async () => {
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

      const integrationState = observable<IntegrationStates["MSTEAMS"]>({
        mode: "DISABLED",
        credentials: [],
      });
      render(
        <Alerts>
          <MSTeams integrationState={integrationState} update={() => {}} />
        </Alerts>
      );

      fireEvent.click(screen.getByRole("button"));

      fireEvent.click(screen.getByRole("button", { name: /add/i }));

      await waitFor(() => {
        expect(
          screen.queryByRole("button", { name: /add/i })
        ).not.toBeInTheDocument();
      });

      fireEvent.change(
        screen.getByRole("textbox", { name: /channel connector name/i }),
        { target: { value: "new name" } }
      );

      fireEvent.change(screen.getByRole("textbox", { name: /webhook url/i }), {
        target: { value: "example.com" },
      });

      fireEvent.click(screen.getByRole("button", { name: /save/i }));

      expect(
        await screen.findByRole("alert", { name: /Successfully/ })
      ).toBeVisible();

      expect(mockAxios.history.post.length).toBe(1);
      expect(mockAxios.history.post[0].params.get("appName")).toEqual(
        "MSTEAMS"
      );
      expect(JSON.parse(mockAxios.history.post[0].data)).toEqual({
        MSTEAMS_CHANNEL_LABEL: "new name",
        MSTEAMS_WEBHOOK_URL: "example.com",
      });

      expect(integrationState.credentials.length).toBe(1);

      expect(screen.getByRole("button", { name: /add/i })).toBeVisible();
    });
  });
});
