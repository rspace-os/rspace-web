import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import MockAdapter from "axios-mock-adapter";
import { observable } from "mobx";
import { describe, expect, test } from "vitest";
import axios from "@/common/axios";
import Alerts from "../../../../components/Alerts/Alerts";
import { Optional } from "../../../../util/optional";
import type { IntegrationStates } from "../../useIntegrationsEndpoint";
import Dataverse from "../Dataverse";

import "@/__tests__/__mocks__/matchMedia";

describe("Dataverse", () => {
  describe("Accessibility", () => {
    test("Should have no axe violations.", async () => {
      const { baseElement } = render(
        <Dataverse
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
  describe("Adding", () => {
    test("Add should be disabled whilst a new config is being added.", () => {
      render(
        <Dataverse
          integrationState={{
            mode: "DISABLED",
            credentials: [],
          }}
          update={() => {}}
        />,
      );

      fireEvent.click(screen.getByRole("button"));

      expect(screen.getByRole("button", { name: "common:actions.add" })).toBeEnabled();

      fireEvent.click(screen.getByRole("button", { name: "common:actions.add" }));
      expect(screen.getByRole("button", { name: "common:actions.add" })).toBeDisabled();
    });
    test("Adding a configuration should mutate the integration state being passed as a prop.", async () => {
      const mockAxios = new MockAdapter(axios);
      mockAxios.onPost("integration/saveAppOptions").reply(200, {
        success: true,
        data: {
          available: true,
          enabled: false,
          name: "DATAVERSE",
          options: {
            "1": {
              DATAVERSE_APIKEY: "new api key",
              DATAVERSE_URL: "new url",
              DATAVERSE_ALIAS: "new name",
              _label: "label",
            },
          },
        },
      });
      const integrationState = observable<IntegrationStates["DATAVERSE"]>({
        mode: "DISABLED",
        credentials: [],
      });
      render(
        <Alerts>
          <Dataverse integrationState={integrationState} update={() => {}} />
        </Alerts>,
      );

      fireEvent.click(screen.getByRole("button"));

      fireEvent.click(screen.getByRole("button", { name: "common:actions.add" }));
      fireEvent.input(screen.getByRole("textbox", { name: "apps:integrations.dataverse.fields.alias" }), {
        target: { value: "new name" },
      });
      fireEvent.input(screen.getByRole("textbox", { name: "apps:integrations.dataverse.fields.serverUrl" }), {
        target: { value: "new url" },
      });
      // see https://github.com/testing-library/dom-testing-library/issues/567
      fireEvent.input(screen.getByLabelText("apps:integrations.dataverse.fields.apiKey"), {
        target: { value: "new api key" },
      });

      fireEvent.click(screen.getByRole("button", { name: "common:actions.save" }));
      await screen.findByRole("alert", {
        name: "apps:integrations.dataverse.alerts.saveNewSuccess",
      });
      expect(integrationState.credentials.length).toBe(1);
    });
  });
  describe("Saving", () => {
    test("Tapping save on existing config should correctly call saveAppOptions endpoint.", () => {
      const mockAxios = new MockAdapter(axios);

      mockAxios.onPost("integration/saveAppOptions");
      render(
        <Dataverse
          integrationState={{
            mode: "DISABLED",
            credentials: [
              Optional.present({
                DATAVERSE_APIKEY: "apikey",
                DATAVERSE_URL: "url",
                DATAVERSE_ALIAS: "alias",
                _label: "label",
                optionsId: "4",
              }),
            ],
          }}
          update={() => {}}
        />,
      );

      fireEvent.click(screen.getByRole("button"));
      fireEvent.input(screen.getByRole("textbox", { name: "apps:integrations.dataverse.fields.alias" }), {
        target: { value: "new name" },
      });

      fireEvent.click(screen.getByRole("button", { name: "common:actions.save" }));
      expect(mockAxios.history.post.length).toBe(1);
      expect(mockAxios.history.post[0].params.get("appName")).toEqual("DATAVERSE");
      expect(mockAxios.history.post[0].params.get("optionsId")).toEqual("4");
      expect(JSON.parse(mockAxios.history.post[0].data)).toEqual({
        DATAVERSE_APIKEY: "apikey",
        DATAVERSE_URL: "url",
        DATAVERSE_ALIAS: "new name",
      });
    });
    test("Tapping save on a new config should correctly call saveAppOptions endpoint.", () => {
      const mockAxios = new MockAdapter(axios);

      mockAxios.onPost("integration/saveAppOptions");
      render(
        <Dataverse
          integrationState={{
            mode: "DISABLED",
            credentials: [],
          }}
          update={() => {}}
        />,
      );

      fireEvent.click(screen.getByRole("button"));

      fireEvent.click(screen.getByRole("button", { name: "common:actions.add" }));
      fireEvent.input(screen.getByRole("textbox", { name: "apps:integrations.dataverse.fields.alias" }), {
        target: { value: "new name" },
      });
      fireEvent.input(screen.getByRole("textbox", { name: "apps:integrations.dataverse.fields.serverUrl" }), {
        target: { value: "new url" },
      });
      // see https://github.com/testing-library/dom-testing-library/issues/567
      fireEvent.input(screen.getByLabelText("apps:integrations.dataverse.fields.apiKey"), {
        target: { value: "new api key" },
      });

      fireEvent.click(screen.getByRole("button", { name: "common:actions.save" }));
      expect(mockAxios.history.post.length).toBe(1);
      expect(mockAxios.history.post[0].params.get("appName")).toEqual("DATAVERSE");
      expect(JSON.parse(mockAxios.history.post[0].data)).toEqual({
        DATAVERSE_APIKEY: "new api key",
        DATAVERSE_URL: "new url",
        DATAVERSE_ALIAS: "new name",
      });
    });
    test("Saving one config should not discard changes to another.", async () => {
      const mockAxios = new MockAdapter(axios);
      mockAxios.onPost("integration/saveAppOptions").reply(200, {
        success: true,
        data: {
          available: true,
          enabled: false,
          name: "DATAVERSE",
          options: {
            "1": {
              DATAVERSE_APIKEY: "apikey",
              DATAVERSE_URL: "url",
              DATAVERSE_ALIAS: "new name",
              _label: "label",
            },
            "2": {
              DATAVERSE_APIKEY: "apikey",
              DATAVERSE_URL: "url",
              DATAVERSE_ALIAS: "new name",
              _label: "label",
            },
          },
        },
      });
      render(
        <Alerts>
          <Dataverse
            integrationState={{
              mode: "DISABLED",
              credentials: [
                Optional.present({
                  DATAVERSE_APIKEY: "apikey",
                  DATAVERSE_URL: "url",
                  DATAVERSE_ALIAS: "new name",
                  _label: "label",
                  optionsId: "1",
                }),
                Optional.present({
                  DATAVERSE_APIKEY: "apikey",
                  DATAVERSE_URL: "url",
                  DATAVERSE_ALIAS: "new name",
                  _label: "label",
                  optionsId: "2",
                }),
              ],
            }}
            update={() => {}}
          />
        </Alerts>,
      );

      fireEvent.click(screen.getByRole("button"));
      fireEvent.input(screen.getAllByRole("textbox", { name: "apps:integrations.dataverse.fields.alias" })[0], {
        target: { value: "new name" },
      });
      fireEvent.input(screen.getAllByRole("textbox", { name: "apps:integrations.dataverse.fields.alias" })[1], {
        target: { value: "unsaved new name" },
      });

      fireEvent.click(screen.getAllByRole("button", { name: "common:actions.save" })[0]);

      await screen.findByRole("alert", { name: "apps:integrations.dataverse.alerts.saveExistingSuccess" });
      expect(screen.getAllByRole("textbox", { name: "apps:integrations.dataverse.fields.alias" })[1]).toHaveValue(
        "unsaved new name",
      );
    });
    test("Saving one config should not discard changes to a new config.", async () => {
      const mockAxios = new MockAdapter(axios);
      mockAxios.onPost("integration/saveAppOptions").reply(200, {
        success: true,
        data: {
          available: true,
          enabled: false,
          name: "DATAVERSE",
          options: {
            "1": {
              DATAVERSE_APIKEY: "apikey",
              DATAVERSE_URL: "url",
              DATAVERSE_ALIAS: "new name",
              _label: "label",
            },
          },
        },
      });
      render(
        <Alerts>
          <Dataverse
            integrationState={{
              mode: "DISABLED",
              credentials: [
                Optional.present({
                  DATAVERSE_APIKEY: "apikey",
                  DATAVERSE_URL: "url",
                  DATAVERSE_ALIAS: "new name",
                  _label: "label",
                  optionsId: "1",
                }),
              ],
            }}
            update={() => {}}
          />
        </Alerts>,
      );

      fireEvent.click(screen.getByRole("button"));

      fireEvent.click(screen.getByRole("button", { name: "common:actions.add" }));
      fireEvent.input(screen.getAllByRole("textbox", { name: "apps:integrations.dataverse.fields.alias" })[0], {
        target: { value: "new name" },
      });
      fireEvent.input(screen.getAllByRole("textbox", { name: "apps:integrations.dataverse.fields.alias" })[1], {
        target: { value: "unsaved new name" },
      });

      fireEvent.click(screen.getAllByRole("button", { name: "common:actions.save" })[0]);

      await screen.findByRole("alert", { name: "apps:integrations.dataverse.alerts.saveExistingSuccess" });
      expect(screen.getAllByRole("textbox", { name: "apps:integrations.dataverse.fields.alias" })[1]).toHaveValue(
        "unsaved new name",
      );
    });
    test("Saving a new config should not discard changes to an existing one.", async () => {
      const mockAxios = new MockAdapter(axios);
      mockAxios.onPost("integration/saveAppOptions").reply(200, {
        success: true,
        data: {
          available: true,
          enabled: false,
          name: "DATAVERSE",
          options: {
            "1": {
              DATAVERSE_APIKEY: "apikey",
              DATAVERSE_URL: "url",
              DATAVERSE_ALIAS: "new name",
              _label: "label",
            },
          },
        },
      });
      render(
        <Alerts>
          <Dataverse
            integrationState={{
              mode: "DISABLED",
              credentials: [
                Optional.present({
                  DATAVERSE_APIKEY: "apikey",
                  DATAVERSE_URL: "url",
                  DATAVERSE_ALIAS: "name",
                  _label: "label",
                  optionsId: "1",
                }),
              ],
            }}
            update={() => {}}
          />
        </Alerts>,
      );

      fireEvent.click(screen.getByRole("button"));

      fireEvent.click(screen.getByRole("button", { name: "common:actions.add" }));
      fireEvent.input(screen.getAllByRole("textbox", { name: "apps:integrations.dataverse.fields.alias" })[0], {
        target: { value: "unsaved new name" },
      });
      fireEvent.input(screen.getAllByRole("textbox", { name: "apps:integrations.dataverse.fields.alias" })[1], {
        target: { value: "new name" },
      });
      fireEvent.input(screen.getAllByRole("textbox", { name: "apps:integrations.dataverse.fields.serverUrl" })[1], {
        target: { value: "new url" },
      });
      // see https://github.com/testing-library/dom-testing-library/issues/567
      fireEvent.input(screen.getAllByLabelText("apps:integrations.dataverse.fields.apiKey")[1], {
        target: { value: "new api key" },
      });

      fireEvent.click(screen.getAllByRole("button", { name: "common:actions.save" })[1]);

      await screen.findByRole("alert", { name: "apps:integrations.dataverse.alerts.saveNewSuccess" });
      expect(screen.getAllByRole("textbox", { name: "apps:integrations.dataverse.fields.alias" })[0]).toHaveValue(
        "unsaved new name",
      );
    });
  });
  describe("Testing", () => {
    test("The test button should be disabled whilst there are unsaved changes.", async () => {
      const mockAxios = new MockAdapter(axios);
      mockAxios.onPost("integration/saveAppOptions").reply(200, {
        success: true,
        data: {
          available: true,
          enabled: false,
          name: "DATAVERSE",
          options: {
            "1": {
              DATAVERSE_APIKEY: "apikey",
              DATAVERSE_URL: "url",
              DATAVERSE_ALIAS: "new name",
              _label: "label",
            },
          },
        },
      });
      render(
        <Dataverse
          integrationState={{
            mode: "DISABLED",
            credentials: [
              Optional.present({
                DATAVERSE_APIKEY: "apikey",
                DATAVERSE_URL: "url",
                DATAVERSE_ALIAS: "alias",
                _label: "label",
                optionsId: "1",
              }),
            ],
          }}
          update={() => {}}
        />,
      );

      fireEvent.click(screen.getByRole("button"));
      fireEvent.input(screen.getByRole("textbox", { name: "apps:integrations.dataverse.fields.alias" }), {
        target: { value: "new name" },
      });

      expect(screen.getByRole("button", { name: "apps:actions.test" })).toBeDisabled();

      fireEvent.click(screen.getByRole("button", { name: "common:actions.save" }));
      await waitFor(() => {
        expect(screen.getByRole("button", { name: "apps:actions.test" })).toBeEnabled();
      });
    });
    test("The test button should make the right API call.", async () => {
      const mockAxios = new MockAdapter(axios);
      mockAxios.onGet("/repository/ajax/testRepository/1").reply(200, "Success! Test connection OK!");
      render(
        <Alerts>
          <Dataverse
            integrationState={{
              mode: "DISABLED",
              credentials: [
                Optional.present({
                  DATAVERSE_APIKEY: "apikey",
                  DATAVERSE_URL: "url",
                  DATAVERSE_ALIAS: "name",
                  _label: "label",
                  optionsId: "1",
                }),
              ],
            }}
            update={() => {}}
          />
        </Alerts>,
      );

      fireEvent.click(screen.getByRole("button"));

      fireEvent.click(screen.getByRole("button", { name: "apps:actions.test" }));
      expect(
        await screen.findByRole("alert", {
          name: "apps:integrations.dataverse.alerts.testValid",
        }),
      ).toBeVisible();
      expect(mockAxios.history.get.length).toBe(1);
      expect(mockAxios.history.get[0]).toMatchObject({
        baseURL: "/repository/ajax/testRepository",
        url: "1",
      });
    });
  });
  describe("Deleting", () => {
    test("Deleting an existing config should make the correct API call.", async () => {
      const mockAxios = new MockAdapter(axios);
      mockAxios.onPost("integration/deleteAppOptions").reply(200, {
        success: true,
        data: {
          available: true,
          enabled: true,
          name: "DATAVERSE",
          options: {},
        },
      });
      render(
        <Alerts>
          <Dataverse
            integrationState={{
              mode: "DISABLED",
              credentials: [
                Optional.present({
                  DATAVERSE_APIKEY: "apikey",
                  DATAVERSE_URL: "url",
                  DATAVERSE_ALIAS: "name",
                  _label: "label",
                  optionsId: "1",
                }),
              ],
            }}
            update={() => {}}
          />
        </Alerts>,
      );

      fireEvent.click(screen.getByRole("button"));

      fireEvent.click(screen.getByRole("button", { name: "common:actions.delete" }));
      expect(mockAxios.history.post.length).toBe(1);
      expect(mockAxios.history.post[0].params.get("appName")).toEqual("DATAVERSE");

      expect(mockAxios.history.post[0].data.get("optionsId")).toBe("1");
      await waitFor(() => {
        expect(
          screen.queryByRole("textbox", { name: "apps:integrations.dataverse.fields.alias" }),
        ).not.toBeInTheDocument();
      });
    });
    test("Deleting a config should mutate the integration state being passed as a prop.", async () => {
      const integrationState = observable({
        mode: "DISABLED" as const,
        credentials: [
          Optional.present({
            DATAVERSE_APIKEY: "apikey",
            DATAVERSE_URL: "url",
            DATAVERSE_ALIAS: "name",
            _label: "label",
            optionsId: "1",
          }),
        ],
      });
      const mockAxios = new MockAdapter(axios);
      mockAxios.onPost("integration/deleteAppOptions").reply(200, {
        success: true,
        data: {
          available: true,
          enabled: true,
          name: "DATAVERSE",
          options: {},
        },
      });
      render(
        <Alerts>
          <Dataverse integrationState={integrationState} update={() => {}} />
        </Alerts>,
      );

      fireEvent.click(screen.getByRole("button"));

      fireEvent.click(screen.getByRole("button", { name: "common:actions.delete" }));
      await waitFor(() => {
        expect(
          screen.queryByRole("textbox", { name: "apps:integrations.dataverse.fields.alias" }),
        ).not.toBeInTheDocument();
      });
      expect(integrationState.credentials.length).toBe(0);
    });
  });
});
