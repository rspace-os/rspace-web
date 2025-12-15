/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import React from "react";
import { render, cleanup, waitFor } from "@testing-library/react";
import "@testing-library/jest-dom";
import MockAdapter from "axios-mock-adapter";
import axios from "@/common/axios";
import { useIntegrationIsAllowedAndEnabled } from "../../hooks/api/integrationHelpers";
import * as FetchingData from "../../util/fetchingData";
import fc from "fast-check";

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("integrationHelpers", () => {
  describe("useIntegrationIsAllowedAndEnabled", () => {
    function Wrapper() {
      const allowed = useIntegrationIsAllowedAndEnabled("foo");
      return FetchingData.match(allowed, {
        loading: () => "loading",
        error: () => "error",
        success: (value) => (value ? "true" : "false"),
      });
    }

    test("If the sysadmin has not allowed the integration, then false should be returned.", async () => {
      await fc.assert(
        fc.asyncProperty(fc.boolean(), async (enabled) => {
          const mockAxios = new MockAdapter(axios);
          mockAxios.onGet("/integration/integrationInfo").reply(200, {
            data: {
              name: "FOO",
              displayName: "Foo",
              available: false,
              enabled,
              oauthConnected: false,
              options: {},
            },
            error: null,
            success: true,
            errorMsg: null,
          });
          const { container } = render(<Wrapper />);

          await waitFor(() => {
            expect(container).not.toHaveTextContent("loading");
          });

          expect(container).toHaveTextContent("false");
        }),
      );
    });

    test("If the user has not enabled the integration, then false should be returned.", async () => {
      await fc.assert(
        fc.asyncProperty(fc.boolean(), async (available) => {
          const mockAxios = new MockAdapter(axios);
          mockAxios.onGet("/integration/integrationInfo").reply(200, {
            data: {
              name: "FOO",
              displayName: "Foo",
              available,
              enabled: false,
              oauthConnected: false,
              options: {},
            },
            error: null,
            success: true,
            errorMsg: null,
          });
          const { container } = render(<Wrapper />);

          await waitFor(() => {
            expect(container).not.toHaveTextContent("loading");
          });

          expect(container).toHaveTextContent("false");
        }),
      );
    });

    test("When both available and enabled, should true be returned.", async () => {
      const mockAxios = new MockAdapter(axios);
      mockAxios.onGet("/integration/integrationInfo").reply(200, {
        data: {
          name: "FOO",
          displayName: "Foo",
          available: true,
          enabled: true,
          oauthConnected: false,
          options: {},
        },
        error: null,
        success: true,
        errorMsg: null,
      });
      const { container } = render(<Wrapper />);

      await waitFor(() => {
        expect(container).not.toHaveTextContent("loading");
      });

      expect(container).toHaveTextContent("true");
    });

    test("When there is an error, an error state should be returned.", async () => {
      const mockAxios = new MockAdapter(axios);
      mockAxios.onGet("/integration/integrationInfo").reply(404);
      const { container } = render(<Wrapper />);

      await waitFor(() => {
        expect(container).not.toHaveTextContent("loading");
      });

      expect(container).toHaveTextContent("error");
    });
  });
});
