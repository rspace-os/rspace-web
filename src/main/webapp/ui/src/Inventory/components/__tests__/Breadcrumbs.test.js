/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen } from "@testing-library/react";
import "@testing-library/jest-dom";
import { makeMockSubSample } from "../../../stores/models/__tests__/SubSampleModel/mocking";
import Breadcrumbs from "../Breadcrumbs";

jest.mock("../../../common/InvApiService", () => {});
jest.mock("../../../stores/stores/RootStore", () => () => ({}));

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("Breadcrumbs", () => {
  describe("When the passed record is deleted", () => {
    test("In Trash should be shown.", () => {
      const subsample = makeMockSubSample({
        deleted: true,
      });

      render(<Breadcrumbs record={subsample} />);

      expect(screen.getByRole("navigation")).toHaveTextContent("In Trash");
    });
  });
});
