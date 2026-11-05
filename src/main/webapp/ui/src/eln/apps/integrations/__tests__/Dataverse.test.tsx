import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import MockAdapter from "axios-mock-adapter";
import { observable } from "mobx";
import { describe, expect, test } from "vitest";
import { renderWithRealI18n } from "@/__tests__/helpers/realI18n";
import axios from "@/common/axios";
import apps from "@/modules/common/i18n/locales/en-US/apps.json";
import common from "@/modules/common/i18n/locales/en-US/common.json";
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
    test("Tapping save on existing config should keep the stored API key.", () => {
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

      fireEvent.click(screen.getByRole("button"));
      expect(screen.getByLabelText("apps:integrations.dataverse.fields.apiKey")).toHaveAttribute(
        "placeholder",
        "common:inputs.secretField.unchanged",
      );
      fireEvent.input(screen.getByRole("textbox", { name: "apps:integrations.dataverse.fields.alias" }), {
        target: { value: "new name" },
      });

      fireEvent.click(screen.getByRole("button", { name: "common:actions.save" }));
      expect(mockAxios.history.post.length).toBe(1);
      expect(mockAxios.history.post[0].params.get("appName")).toEqual("DATAVERSE");
      expect(mockAxios.history.post[0].params.get("optionsId")).toEqual("4");
      expect(JSON.parse(mockAxios.history.post[0].data)).toEqual({
        DATAVERSE_APIKEY: null,
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
    test("Only confirming deletion sends the selected configuration to the API and removes its form.", async () => {
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
      await renderWithRealI18n(
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
        { resources: { apps, common }, defaultNS: "apps" },
      );

      await user.click(screen.getByRole("button"));
      await user.click(screen.getByRole("button", { name: "Delete" }));
      const confirmation = await screen.findByRole("dialog", { name: "Delete Dataverse configuration?" });
      expect(within(confirmation).getByText('Delete the Dataverse configuration "name"?')).toBeVisible();
      expect(mockAxios.history.post).toHaveLength(0);
      await user.click(within(confirmation).getByRole("button", { name: "Confirm" }));
      expect(await screen.findByRole("alert", { name: "Successfully deleted configuration." })).toBeVisible();
      expect(mockAxios.history.post.length).toBe(1);
      expect(mockAxios.history.post[0].params.get("appName")).toEqual("DATAVERSE");

      expect(mockAxios.history.post[0].data.get("optionsId")).toBe("1");
      await waitFor(() => {
        expect(screen.queryByRole("textbox", { name: "Dataverse Name" })).not.toBeInTheDocument();
      });
    });
    test.each([true, false])("Confirmed deletion updates state only on success: %s", async (success) => {
      const user = userEvent.setup();
      const integrationState = observable({
        mode: "DISABLED" as const,
        credentials: [
          Optional.present({
            DATAVERSE_APIKEY: "retained key",
            DATAVERSE_URL: "retained url",
            DATAVERSE_ALIAS: "retained name",
            _label: "retained label",
            optionsId: "2",
          }),
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
        success,
        errorMsg: "Local deletion failure",
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

      const forms = screen.getAllByRole("form");
      await user.click(within(forms[1]).getByRole("button", { name: "common:actions.delete" }));
      await user.click(
        within(
          await screen.findByRole("dialog", {
            name: "apps:integrations.dataverse.deleteConfirm.title",
          }),
        ).getByRole("button", { name: "common:actions.confirm" }),
      );
      const alert = await screen.findByRole("alert");
      expect(
        within(alert).getByText(
          success
            ? "apps:integrations.dataverse.alerts.deleteConfigSuccess"
            : "apps:integrations.dataverse.alerts.deleteConfigError",
        ),
      ).toBeVisible();
      if (!success) expect(within(alert).getByText("Local deletion failure")).toBeVisible();
      expect(integrationState.credentials).toHaveLength(success ? 1 : 2);
      expect(within(forms[0]).getByRole("textbox", { name: "apps:integrations.dataverse.fields.alias" })).toHaveValue(
        "retained name",
      );
      if (success) {
        expect(forms[1]).not.toBeInTheDocument();
      } else {
        expect(within(forms[1]).getByRole("textbox", { name: "apps:integrations.dataverse.fields.alias" })).toHaveValue(
          "name",
        );
        expect(within(forms[1]).getByRole("button", { name: "common:actions.delete" })).toBeEnabled();
      }
    });
    test("Cancel preserves the configuration and edited fields without sending a delete request.", async () => {
      const user = userEvent.setup();
      const mockAxios = new MockAdapter(axios);
      const integrationState = observable<IntegrationStates["DATAVERSE"]>({
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
      });
      render(<Dataverse integrationState={integrationState} update={() => {}} />);
      await user.click(screen.getByRole("button"));
      const alias = screen.getByRole("textbox", { name: "apps:integrations.dataverse.fields.alias" });
      await user.clear(alias);
      await user.type(alias, "edited name");
      await user.click(screen.getByRole("button", { name: "common:actions.delete" }));
      const confirmation = await screen.findByRole("dialog", {
        name: "apps:integrations.dataverse.deleteConfirm.title",
      });
      await user.click(within(confirmation).getByRole("button", { name: "common:actions.cancel" }));
      await waitFor(() => expect(confirmation).not.toBeInTheDocument());
      expect(alias).toHaveValue("edited name");
      expect(screen.getByRole("textbox", { name: "apps:integrations.dataverse.fields.serverUrl" })).toHaveValue("url");
      expect(screen.getByLabelText("apps:integrations.dataverse.fields.apiKey")).toHaveValue("apikey");
      expect(screen.getByRole("button", { name: "apps:actions.test" })).toBeDisabled();
      expect(integrationState.credentials).toHaveLength(1);
      expect(mockAxios.history.post).toHaveLength(0);
    });
  });
});
