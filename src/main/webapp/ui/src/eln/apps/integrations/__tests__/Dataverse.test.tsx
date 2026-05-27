import userEvent from "@testing-library/user-event";
import { replaceValue } from "@/__tests__/helpers/userInteractions";
import { test, describe, expect } from "vitest";
import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import Dataverse from "../Dataverse";
import { Optional } from "../../../../util/optional";
import MockAdapter from "axios-mock-adapter";
import axios from "@/common/axios";
import Alerts from "../../../../components/Alerts/Alerts";
import { observable } from "mobx";
import { type IntegrationStates } from "../../useIntegrationsEndpoint";
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
      await userEvent.click(screen.getByRole("button"));
      expect(await screen.findByRole("dialog")).toBeVisible();

      // @ts-expect-error toBeAccessible is from @sa11y/vitest
      await expect(baseElement).toBeAccessible();
    });
  });
  describe("Adding", () => {
    test("Add should be disabled whilst a new config is being added.", async () => {
      render(
        <Dataverse
          integrationState={{
            mode: "DISABLED",
            credentials: [],
          }}
          update={() => {}}
        />,
      );
      await userEvent.click(screen.getByRole("button"));
      expect(
        screen.getByRole("button", {
          name: /add/i,
        }),
      ).toBeEnabled();
      await userEvent.click(
        screen.getByRole("button", {
          name: /add/i,
        }),
      );
      expect(
        screen.getByRole("button", {
          name: /add/i,
        }),
      ).toBeDisabled();
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
      await userEvent.click(screen.getByRole("button"));
      await userEvent.click(
        screen.getByRole("button", {
          name: /add/i,
        }),
      );
      await replaceValue(
        screen.getByRole("textbox", {
          name: /dataverse name/i,
        }),
        "new name",
      );
      await replaceValue(
        screen.getByRole("textbox", {
          name: /server url/i,
        }),
        "new url",
      );
      // see https://github.com/testing-library/dom-testing-library/issues/567
      await replaceValue(screen.getByLabelText("API key"), "new api key");
      await userEvent.click(
        screen.getByRole("button", {
          name: /save/i,
        }),
      );
      await screen.findByRole("alert", {
        name: /Successfully saved Dataverse details/,
      });
      expect(integrationState.credentials.length).toBe(1);
    });
  });
  describe("Saving", () => {
    test("Tapping save on existing config should correctly call saveAppOptions endpoint.", async () => {
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
      await userEvent.click(screen.getByRole("button"));
      await replaceValue(
        screen.getByRole("textbox", {
          name: /dataverse name/i,
        }),
        "new name",
      );
      await userEvent.click(
        screen.getByRole("button", {
          name: /save/i,
        }),
      );
      expect(mockAxios.history.post.length).toBe(1);
      expect(mockAxios.history.post[0].params.get("appName")).toEqual(
        "DATAVERSE",
      );
      expect(mockAxios.history.post[0].params.get("optionsId")).toEqual("4");
      expect(JSON.parse(mockAxios.history.post[0].data)).toEqual({
        DATAVERSE_APIKEY: "apikey",
        DATAVERSE_URL: "url",
        DATAVERSE_ALIAS: "new name",
      });
    });
    test("Tapping save on a new config should correctly call saveAppOptions endpoint.", async () => {
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
      await userEvent.click(screen.getByRole("button"));
      await userEvent.click(
        screen.getByRole("button", {
          name: /add/i,
        }),
      );
      await replaceValue(
        screen.getByRole("textbox", {
          name: /dataverse name/i,
        }),
        "new name",
      );
      await replaceValue(
        screen.getByRole("textbox", {
          name: /Server URL/i,
        }),
        "new url",
      );
      // see https://github.com/testing-library/dom-testing-library/issues/567
      await replaceValue(screen.getByLabelText("API key"), "new api key");
      await userEvent.click(
        screen.getByRole("button", {
          name: /save/i,
        }),
      );
      expect(mockAxios.history.post.length).toBe(1);
      expect(mockAxios.history.post[0].params.get("appName")).toEqual(
        "DATAVERSE",
      );
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
      await userEvent.click(screen.getByRole("button"));
      await replaceValue(
        screen.getAllByRole("textbox", {
          name: /dataverse name/i,
        })[0],
        "new name",
      );
      await replaceValue(
        screen.getAllByRole("textbox", {
          name: /dataverse name/i,
        })[1],
        "unsaved new name",
      );
      await userEvent.click(
        screen.getAllByRole("button", {
          name: /save/i,
        })[0],
      );
      await screen.findByRole("alert", {
        name: /Successfully/,
      });
      expect(
        screen.getAllByRole("textbox", {
          name: /dataverse name/i,
        })[1],
      ).toHaveValue("unsaved new name");
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
      await userEvent.click(screen.getByRole("button"));
      await userEvent.click(
        screen.getByRole("button", {
          name: /add/i,
        }),
      );
      await replaceValue(
        screen.getAllByRole("textbox", {
          name: /dataverse name/i,
        })[0],
        "new name",
      );
      await replaceValue(
        screen.getAllByRole("textbox", {
          name: /dataverse name/i,
        })[1],
        "unsaved new name",
      );
      await userEvent.click(
        screen.getAllByRole("button", {
          name: /save/i,
        })[0],
      );
      await screen.findByRole("alert", {
        name: /Successfully/,
      });
      expect(
        screen.getAllByRole("textbox", {
          name: /dataverse name/i,
        })[1],
      ).toHaveValue("unsaved new name");
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
      await userEvent.click(screen.getByRole("button"));
      await userEvent.click(
        screen.getByRole("button", {
          name: /add/i,
        }),
      );
      await replaceValue(
        screen.getAllByRole("textbox", {
          name: /dataverse name/i,
        })[0],
        "unsaved new name",
      );
      await replaceValue(
        screen.getAllByRole("textbox", {
          name: /dataverse name/i,
        })[1],
        "new name",
      );
      await replaceValue(
        screen.getAllByRole("textbox", {
          name: /Server URL/i,
        })[1],
        "new url",
      );
      // see https://github.com/testing-library/dom-testing-library/issues/567
      await replaceValue(screen.getAllByLabelText("API key")[1], "new api key");
      await userEvent.click(
        screen.getAllByRole("button", {
          name: /save/i,
        })[1],
      );
      await screen.findByRole("alert", {
        name: /Successfully/,
      });
      expect(
        screen.getAllByRole("textbox", {
          name: /dataverse name/i,
        })[0],
      ).toHaveValue("unsaved new name");
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
      await userEvent.click(screen.getByRole("button"));
      await replaceValue(
        screen.getByRole("textbox", {
          name: /dataverse name/i,
        }),
        "new name",
      );
      expect(
        screen.getByRole("button", {
          name: /test/i,
        }),
      ).toBeDisabled();
      await userEvent.click(
        screen.getByRole("button", {
          name: /save/i,
        }),
      );
      await waitFor(() => {
        expect(
          screen.getByRole("button", {
            name: /test/i,
          }),
        ).toBeEnabled();
      });
    });
    test("The test button should make the right API call.", async () => {
      const mockAxios = new MockAdapter(axios);
      mockAxios
        .onGet(new RegExp("repository/ajax/testRepository/.*"))
        .reply(200, "Success! Test connection OK!");
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
      await userEvent.click(screen.getByRole("button"));
      await userEvent.click(
        screen.getByRole("button", {
          name: /test/i,
        }),
      );
      expect(
        await screen.findByRole("alert", {
          name: /Connection details are valid/,
        }),
      ).toBeVisible();
      expect(mockAxios.history.get.length).toBe(1);
      expect(mockAxios.history.get[0].url).toBe("1");
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
      await userEvent.click(screen.getByRole("button"));
      await userEvent.click(
        screen.getByRole("button", {
          name: /delete/i,
        }),
      );
      expect(mockAxios.history.post.length).toBe(1);
      expect(mockAxios.history.post[0].params.get("appName")).toEqual(
        "DATAVERSE",
      );
      expect(mockAxios.history.post[0].data.get("optionsId")).toBe("1");
      await waitFor(() => {
        expect(
          screen.queryByRole("textbox", {
            name: /dataverse name/i,
          }),
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
      await userEvent.click(screen.getByRole("button"));
      await userEvent.click(
        screen.getByRole("button", {
          name: /delete/i,
        }),
      );
      await waitFor(() => {
        expect(
          screen.queryByRole("textbox", {
            name: /dataverse name/i,
          }),
        ).not.toBeInTheDocument();
      });
      expect(integrationState.credentials.length).toBe(0);
    });
  });
});
