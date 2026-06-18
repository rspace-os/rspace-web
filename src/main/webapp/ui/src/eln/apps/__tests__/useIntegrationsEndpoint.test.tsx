import { render, waitFor } from "@testing-library/react";
import MockAdapter from "axios-mock-adapter";
import { useEffect } from "react";
import { describe, expect, test, vi } from "vitest";
import axios from "@/common/axios";
import { Optional } from "../../../util/optional";
import { type IntegrationStates, useIntegrationsEndpoint } from "../useIntegrationsEndpoint";
import allIntegrationsAreDisabled from "./allIntegrationsAreDisabled.json";

import "@/__tests__/__mocks__/matchMedia";
describe("useIntegrationsEndpoint", () => {
  describe("saveAppOptions", () => {
    function Wrapper() {
      const { saveAppOptions } = useIntegrationsEndpoint();
      useEffect(() => {
        void saveAppOptions("DATAVERSE", Optional.present("1"), {
          foo: "bar",
        }).catch(() => {});
      }, []);
      // biome-ignore lint/complexity/noUselessFragments: initial biome migration
      return <></>;
    }
    test("Should construct valid API call from inputs.", () => {
      const mockAxios = new MockAdapter(axios);
      mockAxios.onGet("integration/allIntegrations").reply(500);

      mockAxios.onPost("integration/saveAppOptions").reply(500);

      render(<Wrapper />);
      expect(mockAxios.history.post.length).toBe(1);
      expect(mockAxios.history.post[0].params.get("appName")).toEqual("DATAVERSE");
      expect(mockAxios.history.post[0].params.get("optionsId")).toEqual("1");
      expect(JSON.parse(mockAxios.history.post[0].data).foo).toBe("bar");
    });
  });

  describe("DMPASSISTANT", () => {
    test("allIntegrations decodes options.ACCESS_TOKEN into credentials.ACCESS_TOKEN", async () => {
      const mockAxios = new MockAdapter(axios);
      const data = {
        ...allIntegrationsAreDisabled.data,
        DMPASSISTANT: {
          ...allIntegrationsAreDisabled.data.DMPASSISTANT,
          options: { ACCESS_TOKEN: "abc123" },
        },
      };
      mockAxios.onGet("integration/allIntegrations").reply(200, { success: true, data, error: null });
      const onSuccess = vi.fn<(states: IntegrationStates) => void>();

      function Wrapper() {
        const { allIntegrations } = useIntegrationsEndpoint();
        useEffect(() => {
          void allIntegrations().then(onSuccess);
        }, []);
        // biome-ignore lint/complexity/noUselessFragments: initial biome migration
        return <></>;
      }
      render(<Wrapper />);

      await waitFor(() => expect(onSuccess).toHaveBeenCalled());
      const states = onSuccess.mock.calls[0][0];
      expect(states.DMPASSISTANT.credentials.ACCESS_TOKEN.isPresent()).toBe(true);
      expect(states.DMPASSISTANT.credentials.ACCESS_TOKEN.orElse("")).toBe("abc123");
    });

    function UpdateWrapper({ token }: { token: Optional<string> }) {
      const { update } = useIntegrationsEndpoint();
      useEffect(() => {
        void update("DMPASSISTANT", {
          mode: "DISABLED",
          credentials: { ACCESS_TOKEN: token },
        }).catch(() => {});
      }, []);
      // biome-ignore lint/complexity/noUselessFragments: initial biome migration
      return <></>;
    }

    test("update encodes a present ACCESS_TOKEN into options", async () => {
      const mockAxios = new MockAdapter(axios);
      mockAxios.onPost("integration/update").reply(500);

      render(<UpdateWrapper token={Optional.present("tok")} />);

      await waitFor(() => expect(mockAxios.history.post.length).toBe(1));
      const posted = JSON.parse(mockAxios.history.post[0].data as string) as {
        name: string;
        options: object;
      };
      expect(posted.name).toBe("DMPASSISTANT");
      expect(posted.options).toEqual({ ACCESS_TOKEN: "tok" });
    });

    test("update encodes an absent ACCESS_TOKEN as empty options", async () => {
      const mockAxios = new MockAdapter(axios);
      mockAxios.onPost("integration/update").reply(500);

      render(<UpdateWrapper token={Optional.empty()} />);

      await waitFor(() => expect(mockAxios.history.post.length).toBe(1));
      const posted = JSON.parse(mockAxios.history.post[0].data as string) as {
        name: string;
        options: object;
      };
      expect(posted.name).toBe("DMPASSISTANT");
      // must not post a blank token that would overwrite the stored one
      expect(posted.options).toEqual({});
    });
  });
});
