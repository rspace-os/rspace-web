/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import React, { useEffect } from "react";
import { render, cleanup } from "@testing-library/react";
import "@testing-library/jest-dom";
import { useIntegrationsEndpoint } from "../useIntegrationsEndpoint";
import MockAdapter from "axios-mock-adapter";
import axios from "@/common/axios";
import { Optional } from "../../../util/optional";
import "../../../../__mocks__/matchMedia";

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("useIntegrationsEndpoint", () => {
  describe("saveAppOptions", () => {
    function Wrapper() {
      const { saveAppOptions } = useIntegrationsEndpoint();
      useEffect(() => {
        void saveAppOptions("DATAVERSE", Optional.present("1"), {
          foo: "bar",
        }).catch(() => {});
      }, []);
      return <></>;
    }

    test("Should construct valid API call from inputs.", () => {
      const mockAxios = new MockAdapter(axios);
      mockAxios.onGet("integration/allIntegrations").reply(500);
      mockAxios.onPost("integration/saveAppOptions").reply(500);

      render(<Wrapper />);

      expect(mockAxios.history.post.length).toBe(1);
      expect(mockAxios.history.post[0].params.get("appName")).toEqual(
        "DATAVERSE"
      );
      expect(mockAxios.history.post[0].params.get("optionsId")).toEqual("1");
      expect(JSON.parse(mockAxios.history.post[0].data).foo).toBe("bar");
    });
  });
});
