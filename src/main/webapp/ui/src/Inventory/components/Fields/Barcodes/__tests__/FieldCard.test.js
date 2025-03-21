/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "../../../../../../__mocks__/matchMedia";
import React from "react";
import { render, cleanup, screen } from "@testing-library/react";
import "@testing-library/jest-dom";
import { mockFactory } from "../../../../../stores/definitions/__tests__/Factory/mocking";
import FieldCard from "../FieldCard";
import { type BarcodeRecord } from "../../../../../stores/definitions/Barcode";
import userEvent from "@testing-library/user-event";

jest.mock("../../../../../common/InvApiService", () => {});
jest.mock("../../../../../stores/stores/RootStore", () => () => ({}));

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("FieldCard", () => {
  describe("Has a delete button", () => {
    test("That behaves correctly when tapped when deletedCopy returns an object.", async () => {
      const user = userEvent.setup();
      const setFieldsDirty = jest.fn<
        [{ barcodes: Array<BarcodeRecord> }],
        void
      >(() => {});
      render(
        <FieldCard
          fieldOwner={{
            isFieldEditable: () => true,
            fieldValues: {
              barcodes: [
                {
                  id: 1,
                  data: "foo",
                  format: "qr_code",
                  description: "",
                  isDeleted: false,
                  generated: false,
                  imageUrl: null,
                  descriptionIsEditable: true,
                  renderedDescription: <></>,
                  isDeletable: true,
                  fetchImage: () => {
                    throw new Error("not implemented");
                  },
                  deletedCopy: () => ({
                    id: 1,
                    data: "foo",
                    format: "qr_code",
                    description: "",
                    deleted: true,
                    deletedCopy: () => {
                      throw new Error("not implemented");
                    },
                    paramsForBackend: {},
                    setDescription: () => {},
                    isDeleted: true,
                    generated: false,
                    imageUrl: null,
                    descriptionIsEditable: false,
                    renderedDescription: <></>,
                    isDeletable: false,
                    fetchImage: () => {
                      throw new Error("not implemented");
                    },
                  }),
                  paramsForBackend: {},
                  setDescription: () => {},
                },
              ],
            },
            setFieldsDirty,
            canChooseWhichToEdit: false,
            setFieldEditable: () => {},
            noValueLabel: { barcodes: null },
          }}
          factory={mockFactory()}
        />
      );

      await user.click(screen.getByRole("button", { name: "Remove" }));

      expect(setFieldsDirty).toHaveBeenCalledWith({
        barcodes: [expect.objectContaining({ id: 1, deleted: true })],
      });
    });
    test("That behaves correctly when tapped when deletedCopy returns null.", async () => {
      const user = userEvent.setup();
      const setFieldsDirty = jest.fn<
        [{ barcodes: Array<BarcodeRecord> }],
        void
      >(() => {});
      render(
        <FieldCard
          fieldOwner={{
            isFieldEditable: () => true,
            fieldValues: {
              barcodes: [
                {
                  id: 1,
                  data: "foo",
                  format: "qr_code",
                  description: "",
                  isDeleted: false,
                  generated: false,
                  imageUrl: null,
                  descriptionIsEditable: true,
                  renderedDescription: <></>,
                  isDeletable: true,
                  fetchImage: () => {
                    throw new Error("not implemented");
                  },
                  deletedCopy: () => null,
                  paramsForBackend: {},
                  setDescription: () => {},
                },
              ],
            },
            setFieldsDirty,
            canChooseWhichToEdit: false,
            setFieldEditable: () => {},
            noValueLabel: { barcodes: null },
          }}
          factory={mockFactory()}
        />
      );

      await user.click(screen.getByRole("button", { name: "Remove" }));

      expect(setFieldsDirty).toHaveBeenCalledWith({
        barcodes: [],
      });
    });
  });
  describe("AddButton", () => {
    test("When connectedItem is not specified, there should be an add button.", () => {
      render(
        <FieldCard
          fieldOwner={{
            isFieldEditable: () => true,
            fieldValues: {
              barcodes: [
                {
                  id: 1,
                  data: "foo",
                  format: "qr_code",
                  description: "",
                  isDeleted: false,
                  generated: false,
                  imageUrl: null,
                  descriptionIsEditable: true,
                  renderedDescription: <></>,
                  isDeletable: true,
                  fetchImage: () => {
                    throw new Error("not implemented");
                  },
                  deletedCopy: () => null,
                  paramsForBackend: {},
                  setDescription: () => {},
                },
              ],
            },
            setFieldsDirty: () => {},
            canChooseWhichToEdit: false,
            setFieldEditable: () => {},
            noValueLabel: { barcodes: null },
          }}
          factory={mockFactory()}
        />
      );

      expect(
        screen.getByLabelText("Scan a barcode to associate.")
      ).toBeVisible();
    });
  });
});
