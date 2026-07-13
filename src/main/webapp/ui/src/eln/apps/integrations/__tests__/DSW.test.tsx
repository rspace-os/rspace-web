import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import MockAdapter from "axios-mock-adapter";
import { observable } from "mobx";
import { describe, expect, test } from "vitest";
import axios from "@/common/axios";
import Alerts from "../../../../components/Alerts/Alerts";
import { Optional } from "../../../../util/optional";
import type { IntegrationStates } from "../../useIntegrationsEndpoint";

import "@/__tests__/__mocks__/matchMedia";
import DSW from "@/eln/apps/integrations/DSW";

describe("DSW", () => {
  describe("Accessibility", () => {
    test("Should have no axe violations.", async () => {
      const { baseElement } = render(
        <DSW
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
        <DSW
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
          name: "DSW",
          options: {
            "1": {
              DSW_APIKEY: "new api key",
              DSW_URL: "new url",
              DSW_ALIAS: "new name",
            },
          },
        },
      });
      const integrationState = observable<IntegrationStates["DSW"]>({
        mode: "DISABLED",
        credentials: [],
      });
      render(
        <Alerts>
          <DSW integrationState={integrationState} update={() => {}} />
        </Alerts>,
      );

      fireEvent.click(screen.getByRole("button"));

      fireEvent.click(screen.getByRole("button", { name: "common:actions.add" }));
      fireEvent.input(screen.getByRole("textbox", { name: "apps:integrations.dsw.fields.label" }), {
        target: { value: "new name" },
      });
      fireEvent.input(screen.getByRole("textbox", { name: "apps:integrations.dsw.fields.serverUrl" }), {
        target: { value: "new url" },
      });
      // see https://github.com/testing-library/dom-testing-library/issues/567
      fireEvent.input(screen.getByLabelText("apps:integrations.dsw.fields.apiKey"), {
        target: { value: "new api key" },
      });

      fireEvent.click(screen.getByRole("button", { name: "common:actions.save" }));
      await screen.findByRole("alert", {
        name: "apps:integrations.dsw.alerts.saveNewSuccess",
      });
      expect(integrationState.credentials.length).toBe(1);
    });
  });
  describe("Saving", () => {
    test("Tapping save on existing config should correctly call saveAppOptions endpoint.", () => {
      const mockAxios = new MockAdapter(axios);

      mockAxios.onPost("integration/saveAppOptions");
      render(
        <DSW
          integrationState={{
            mode: "DISABLED",
            credentials: [
              Optional.present({
                DSW_APIKEY: "apikey",
                DSW_URL: "url",
                DSW_ALIAS: "alias",
                optionsId: "4",
              }),
            ],
          }}
          update={() => {}}
        />,
      );

      fireEvent.click(screen.getByRole("button"));
      fireEvent.input(screen.getByRole("textbox", { name: "apps:integrations.dsw.fields.label" }), {
        target: { value: "new name" },
      });

      fireEvent.click(screen.getByRole("button", { name: "common:actions.save" }));
      expect(mockAxios.history.post.length).toBe(1);
      expect(mockAxios.history.post[0].params.get("appName")).toEqual("DSW");
      expect(mockAxios.history.post[0].params.get("optionsId")).toEqual("4");
      expect(JSON.parse(mockAxios.history.post[0].data)).toEqual({
        DSW_APIKEY: "apikey",
        DSW_URL: "url",
        DSW_ALIAS: "new name",
      });
    });
    test("Tapping save on a new config should correctly call saveAppOptions endpoint.", () => {
      const mockAxios = new MockAdapter(axios);

      mockAxios.onPost("integration/saveAppOptions");
      render(
        <DSW
          integrationState={{
            mode: "DISABLED",
            credentials: [],
          }}
          update={() => {}}
        />,
      );

      fireEvent.click(screen.getByRole("button"));

      fireEvent.click(screen.getByRole("button", { name: "common:actions.add" }));
      fireEvent.input(screen.getByRole("textbox", { name: "apps:integrations.dsw.fields.label" }), {
        target: { value: "new name" },
      });
      fireEvent.input(screen.getByRole("textbox", { name: "apps:integrations.dsw.fields.serverUrl" }), {
        target: { value: "new url" },
      });
      // see https://github.com/testing-library/dom-testing-library/issues/567
      fireEvent.input(screen.getByLabelText("apps:integrations.dsw.fields.apiKey"), {
        target: { value: "new api key" },
      });

      fireEvent.click(screen.getByRole("button", { name: "common:actions.save" }));
      expect(mockAxios.history.post.length).toBe(1);
      expect(mockAxios.history.post[0].params.get("appName")).toEqual("DSW");
      expect(JSON.parse(mockAxios.history.post[0].data)).toEqual({
        DSW_APIKEY: "new api key",
        DSW_URL: "new url",
        DSW_ALIAS: "new name",
      });
    });
    test("Saving one config should not discard changes to another.", async () => {
      const mockAxios = new MockAdapter(axios);
      mockAxios.onPost("integration/saveAppOptions").reply(200, {
        success: true,
        data: {
          available: true,
          enabled: false,
          name: "DSW",
          options: {
            "1": {
              DSW_APIKEY: "apikey",
              DSW_URL: "url",
              DSW_ALIAS: "new name",
            },
            "2": {
              DSW_APIKEY: "apikey",
              DSW_URL: "url",
              DSW_ALIAS: "new name",
            },
          },
        },
      });
      render(
        <Alerts>
          <DSW
            integrationState={{
              mode: "DISABLED",
              credentials: [
                Optional.present({
                  DSW_APIKEY: "apikey",
                  DSW_URL: "url",
                  DSW_ALIAS: "new name",
                  optionsId: "1",
                }),
                Optional.present({
                  DSW_APIKEY: "apikey",
                  DSW_URL: "url",
                  DSW_ALIAS: "new name",
                  optionsId: "2",
                }),
              ],
            }}
            update={() => {}}
          />
        </Alerts>,
      );

      fireEvent.click(screen.getByRole("button"));
      fireEvent.input(screen.getAllByRole("textbox", { name: "apps:integrations.dsw.fields.label" })[0], {
        target: { value: "new name" },
      });
      fireEvent.input(screen.getAllByRole("textbox", { name: "apps:integrations.dsw.fields.label" })[1], {
        target: { value: "unsaved new name" },
      });

      fireEvent.click(screen.getAllByRole("button", { name: "common:actions.save" })[0]);

      await screen.findByRole("alert", { name: "apps:integrations.dsw.alerts.saveExistingSuccess" });
      expect(screen.getAllByRole("textbox", { name: "apps:integrations.dsw.fields.label" })[1]).toHaveValue(
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
          name: "DSW",
          options: {
            "1": {
              DSW_APIKEY: "apikey",
              DSW_URL: "url",
              DSW_ALIAS: "new name",
            },
          },
        },
      });
      render(
        <Alerts>
          <DSW
            integrationState={{
              mode: "DISABLED",
              credentials: [
                Optional.present({
                  DSW_APIKEY: "apikey",
                  DSW_URL: "url",
                  DSW_ALIAS: "new name",
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
      fireEvent.input(screen.getAllByRole("textbox", { name: "apps:integrations.dsw.fields.label" })[0], {
        target: { value: "new name" },
      });
      fireEvent.input(screen.getAllByRole("textbox", { name: "apps:integrations.dsw.fields.label" })[1], {
        target: { value: "unsaved new name" },
      });

      fireEvent.click(screen.getAllByRole("button", { name: "common:actions.save" })[0]);

      await screen.findByRole("alert", { name: "apps:integrations.dsw.alerts.saveExistingSuccess" });
      expect(screen.getAllByRole("textbox", { name: "apps:integrations.dsw.fields.label" })[1]).toHaveValue(
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
          name: "DSW",
          options: {
            "1": {
              DSW_APIKEY: "apikey",
              DSW_URL: "url",
              DSW_ALIAS: "new name",
            },
          },
        },
      });
      render(
        <Alerts>
          <DSW
            integrationState={{
              mode: "DISABLED",
              credentials: [
                Optional.present({
                  DSW_APIKEY: "apikey",
                  DSW_URL: "url",
                  DSW_ALIAS: "name",
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
      fireEvent.input(screen.getAllByRole("textbox", { name: "apps:integrations.dsw.fields.label" })[0], {
        target: { value: "unsaved new name" },
      });
      fireEvent.input(screen.getAllByRole("textbox", { name: "apps:integrations.dsw.fields.label" })[1], {
        target: { value: "new name" },
      });
      fireEvent.input(screen.getAllByRole("textbox", { name: "apps:integrations.dsw.fields.serverUrl" })[1], {
        target: { value: "new url" },
      });
      // see https://github.com/testing-library/dom-testing-library/issues/567
      fireEvent.input(screen.getAllByLabelText("apps:integrations.dsw.fields.apiKey")[1], {
        target: { value: "new api key" },
      });

      fireEvent.click(screen.getAllByRole("button", { name: "common:actions.save" })[1]);

      await screen.findByRole("alert", { name: "apps:integrations.dsw.alerts.saveNewSuccess" });
      expect(screen.getAllByRole("textbox", { name: "apps:integrations.dsw.fields.label" })[0]).toHaveValue(
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
          name: "DSW",
          options: {
            "1": {
              DSW_APIKEY: "apikey",
              DSW_URL: "url",
              DSW_ALIAS: "new name",
            },
          },
        },
      });
      render(
        <DSW
          integrationState={{
            mode: "DISABLED",
            credentials: [
              Optional.present({
                DSW_APIKEY: "apikey",
                DSW_URL: "url",
                DSW_ALIAS: "alias",
                optionsId: "1",
              }),
            ],
          }}
          update={() => {}}
        />,
      );

      fireEvent.click(screen.getByRole("button"));
      fireEvent.input(screen.getByRole("textbox", { name: "apps:integrations.dsw.fields.label" }), {
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
      mockAxios
        .onGet("/currentUser?serverAlias=name")

        .reply(200, "Success! Test connection OK!");
      render(
        <Alerts>
          <DSW
            integrationState={{
              mode: "DISABLED",
              credentials: [
                Optional.present({
                  DSW_APIKEY: "apikey",
                  DSW_URL: "url",
                  DSW_ALIAS: "name",
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
          name: "apps:integrations.dsw.alerts.testValid",
        }),
      ).toBeVisible();
      expect(mockAxios.history.get.length).toBe(1);
      expect(mockAxios.history.get[0].url).toBe("/currentUser?serverAlias=name");
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
          name: "DSW",
          options: {},
        },
      });
      render(
        <Alerts>
          <DSW
            integrationState={{
              mode: "DISABLED",
              credentials: [
                Optional.present({
                  DSW_APIKEY: "apikey",
                  DSW_URL: "url",
                  DSW_ALIAS: "name",
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
      expect(mockAxios.history.post[0].params.get("appName")).toEqual("DSW");

      expect(mockAxios.history.post[0].data.get("optionsId")).toBe("1");
      await waitFor(() => {
        expect(screen.queryByRole("textbox", { name: "apps:integrations.dsw.fields.label" })).not.toBeInTheDocument();
      });
    });
    test("Deleting a config should mutate the integration state being passed as a prop.", async () => {
      const integrationState = observable({
        mode: "DISABLED" as const,
        credentials: [
          Optional.present({
            DSW_APIKEY: "apikey",
            DSW_URL: "url",
            DSW_ALIAS: "name",
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
          name: "DSW",
          options: {},
        },
      });
      render(
        <Alerts>
          <DSW integrationState={integrationState} update={() => {}} />
        </Alerts>,
      );

      fireEvent.click(screen.getByRole("button"));

      fireEvent.click(screen.getByRole("button", { name: "common:actions.delete" }));
      await waitFor(() => {
        expect(screen.queryByRole("textbox", { name: "apps:integrations.dsw.fields.label" })).not.toBeInTheDocument();
      });
      expect(integrationState.credentials.length).toBe(0);
    });
  });
});
