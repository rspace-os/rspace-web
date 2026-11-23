import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
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
      const user = userEvent.setup();
      const { baseElement } = render(
        <Dataverse
          integrationState={{
            mode: "DISABLED",
            credentials: [],
          }}
          update={() => {}}
        />,
      );

      await user.click(screen.getByRole("button"));
      expect(await screen.findByRole("dialog")).toBeVisible();

      // @ts-expect-error toBeAccessible is from @sa11y/vitest
      await expect(baseElement).toBeAccessible();
    });
  });
  describe("Adding", () => {
    test("Add should be disabled whilst a new config is being added.", async () => {
      const user = userEvent.setup();
      render(
        <Dataverse
          integrationState={{
            mode: "DISABLED",
            credentials: [],
          }}
          update={() => {}}
        />,
      );

      await user.click(screen.getByRole("button"));

      expect(screen.getByRole("button", { name: "common:actions.add" })).toBeEnabled();

      await user.click(screen.getByRole("button", { name: "common:actions.add" }));
      expect(screen.getByRole("button", { name: "common:actions.add" })).toBeDisabled();
    });
    test("Adding a configuration should mutate the integration state being passed as a prop.", async () => {
      const user = userEvent.setup();
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

      await user.click(screen.getByRole("button"));

      await user.click(screen.getByRole("button", { name: "common:actions.add" }));
      await user.type(screen.getByRole("textbox", { name: "apps:integrations.dataverse.fields.alias" }), "new name");
      await user.type(screen.getByRole("textbox", { name: "apps:integrations.dataverse.fields.serverUrl" }), "new url");
      // see https://github.com/testing-library/dom-testing-library/issues/567
      await user.type(screen.getByLabelText("apps:integrations.dataverse.fields.apiKey"), "new api key");

      await user.click(screen.getByRole("button", { name: "common:actions.save" }));
      await screen.findByRole("alert", {
        name: "apps:integrations.dataverse.alerts.saveNewSuccess",
      });
      expect(integrationState.credentials.length).toBe(1);
    });
  });
  describe("Saving", () => {
    test("Tapping save on existing config should keep the stored API key.", async () => {
      const user = userEvent.setup();
      const mockAxios = new MockAdapter(axios);

      mockAxios.onPost("integration/saveAppOptions");
      render(
        <Dataverse
          integrationState={{
            mode: "DISABLED",
            credentials: [
              Optional.present({
                DATAVERSE_APIKEY: null,
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

      await user.click(screen.getByRole("button"));
      expect(screen.getByLabelText("apps:integrations.dataverse.fields.apiKey")).toHaveAttribute(
        "placeholder",
        "common:inputs.secretField.unchanged",
      );
      const aliasInput = screen.getByRole("textbox", { name: "apps:integrations.dataverse.fields.alias" });
      await user.clear(aliasInput);
      await user.type(aliasInput, "new name");

      await user.click(screen.getByRole("button", { name: "common:actions.save" }));
      expect(mockAxios.history.post.length).toBe(1);
      expect(mockAxios.history.post[0].params.get("appName")).toEqual("DATAVERSE");
      expect(mockAxios.history.post[0].params.get("optionsId")).toEqual("4");
      expect(JSON.parse(mockAxios.history.post[0].data)).toEqual({
        DATAVERSE_APIKEY: null,
        DATAVERSE_URL: "url",
        DATAVERSE_ALIAS: "new name",
      });
    });
    test("Tapping save on a new config should correctly call saveAppOptions endpoint.", async () => {
      const user = userEvent.setup();
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

      await user.click(screen.getByRole("button"));

      await user.click(screen.getByRole("button", { name: "common:actions.add" }));
      await user.type(screen.getByRole("textbox", { name: "apps:integrations.dataverse.fields.alias" }), "new name");
      await user.type(screen.getByRole("textbox", { name: "apps:integrations.dataverse.fields.serverUrl" }), "new url");
      // see https://github.com/testing-library/dom-testing-library/issues/567
      await user.type(screen.getByLabelText("apps:integrations.dataverse.fields.apiKey"), "new api key");

      await user.click(screen.getByRole("button", { name: "common:actions.save" }));
      expect(mockAxios.history.post.length).toBe(1);
      expect(mockAxios.history.post[0].params.get("appName")).toEqual("DATAVERSE");
      expect(JSON.parse(mockAxios.history.post[0].data)).toEqual({
        DATAVERSE_APIKEY: "new api key",
        DATAVERSE_URL: "new url",
        DATAVERSE_ALIAS: "new name",
      });
    });
    test("Saving one config should not discard changes to another.", async () => {
      const user = userEvent.setup();
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

      await user.click(screen.getByRole("button"));
      const input = screen.getAllByRole("textbox", { name: "apps:integrations.dataverse.fields.alias" })[0];
      await user.clear(input);
      await user.type(input, "new name");
      const otherInput = screen.getAllByRole("textbox", { name: "apps:integrations.dataverse.fields.alias" })[1];
      await user.clear(otherInput);
      await user.type(otherInput, "unsaved new name");

      await user.click(screen.getAllByRole("button", { name: "common:actions.save" })[0]);

      await screen.findByRole("alert", { name: "apps:integrations.dataverse.alerts.saveExistingSuccess" });
      expect(screen.getAllByRole("textbox", { name: "apps:integrations.dataverse.fields.alias" })[1]).toHaveValue(
        "unsaved new name",
      );
    });
    test("Saving one config should not discard changes to a new config.", async () => {
      const user = userEvent.setup();
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

      await user.click(screen.getByRole("button"));

      await user.click(screen.getByRole("button", { name: "common:actions.add" }));
      const input = screen.getAllByRole("textbox", { name: "apps:integrations.dataverse.fields.alias" })[0];
      await user.clear(input);
      await user.type(input, "new name");
      const otherInput = screen.getAllByRole("textbox", { name: "apps:integrations.dataverse.fields.alias" })[1];
      await user.clear(otherInput);
      await user.type(otherInput, "unsaved new name");

      await user.click(screen.getAllByRole("button", { name: "common:actions.save" })[0]);

      await screen.findByRole("alert", { name: "apps:integrations.dataverse.alerts.saveExistingSuccess" });
      expect(screen.getAllByRole("textbox", { name: "apps:integrations.dataverse.fields.alias" })[1]).toHaveValue(
        "unsaved new name",
      );
    });
    test("Saving a new config should not discard changes to an existing one.", async () => {
      const user = userEvent.setup();
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

      await user.click(screen.getByRole("button"));

      await user.click(screen.getByRole("button", { name: "common:actions.add" }));
      const input = screen.getAllByRole("textbox", { name: "apps:integrations.dataverse.fields.alias" })[0];
      await user.clear(input);
      await user.type(input, "unsaved new name");
      const otherInput = screen.getAllByRole("textbox", { name: "apps:integrations.dataverse.fields.alias" })[1];
      await user.clear(otherInput);
      await user.type(otherInput, "new name");
      const serverUrlInput = screen.getAllByRole("textbox", {
        name: "apps:integrations.dataverse.fields.serverUrl",
      })[1];
      await user.clear(serverUrlInput);
      await user.type(serverUrlInput, "new url");
      // see https://github.com/testing-library/dom-testing-library/issues/567
      const apiKeyInput = screen.getAllByLabelText("apps:integrations.dataverse.fields.apiKey")[1];
      await user.clear(apiKeyInput);
      await user.type(apiKeyInput, "new api key");

      await user.click(screen.getAllByRole("button", { name: "common:actions.save" })[1]);

      await screen.findByRole("alert", { name: "apps:integrations.dataverse.alerts.saveNewSuccess" });
      expect(screen.getAllByRole("textbox", { name: "apps:integrations.dataverse.fields.alias" })[0]).toHaveValue(
        "unsaved new name",
      );
    });
  });
  describe("Testing", () => {
    test("The test button should be disabled whilst there are unsaved changes.", async () => {
      const user = userEvent.setup();
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

      await user.click(screen.getByRole("button"));
      const aliasInput = screen.getByRole("textbox", { name: "apps:integrations.dataverse.fields.alias" });
      await user.clear(aliasInput);
      await user.type(aliasInput, "new name");

      expect(screen.getByRole("button", { name: "apps:actions.test" })).toBeDisabled();

      await user.click(screen.getByRole("button", { name: "common:actions.save" }));
      await waitFor(() => {
        expect(screen.getByRole("button", { name: "apps:actions.test" })).toBeEnabled();
      });
    });
    test("The test button should make the right API call.", async () => {
      const user = userEvent.setup();
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

      await user.click(screen.getByRole("button"));

      await user.click(screen.getByRole("button", { name: "apps:actions.test" }));
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
      const user = userEvent.setup();
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

      await user.click(screen.getByRole("button"));

      await user.click(screen.getByRole("button", { name: "common:actions.delete" }));
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
      const user = userEvent.setup();
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

      await user.click(screen.getByRole("button"));

      await user.click(screen.getByRole("button", { name: "common:actions.delete" }));
      await waitFor(() => {
        expect(
          screen.queryByRole("textbox", { name: "apps:integrations.dataverse.fields.alias" }),
        ).not.toBeInTheDocument();
      });
      expect(integrationState.credentials.length).toBe(0);
    });
  });
});
