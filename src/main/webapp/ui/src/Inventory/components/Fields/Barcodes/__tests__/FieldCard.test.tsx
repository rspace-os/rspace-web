import { describe, expect, test, vi } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import "@/__tests__/__mocks__/barcode-detection-api";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { mockFactory } from "../../../../../stores/definitions/__tests__/Factory/mocking";
import FieldCard from "../FieldCard";

vi.mock("../../../../../common/InvApiService", () => ({ default: {} }));
vi.mock("../../../../../stores/stores/getRootStore", () => ({
  default: () => ({}),
}));
describe("FieldCard", () => {
  describe("Has a delete button", () => {
    test("That behaves correctly when tapped when deletedCopy returns an object.", async () => {
      const user = userEvent.setup();
      const setFieldsDirty = vi.fn();
      render(
        <FieldCard
          fieldOwner={{
            isFieldEditable: () => true,
            fieldValues: {
              barcodes: [
                {
                  data: "foo",
                  format: "qr_code" as const,
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
                    data: "foo",
                    format: "qr_code" as const,
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
        />,
      );

      await user.click(screen.getByRole("button", { name: "common:actions.remove" }));
      expect(setFieldsDirty).toHaveBeenCalledWith({
        barcodes: [expect.objectContaining({ deleted: true })],
      });
    });
    test("That behaves correctly when tapped when deletedCopy returns null.", async () => {
      const user = userEvent.setup();
      const setFieldsDirty = vi.fn();
      render(
        <FieldCard
          fieldOwner={{
            isFieldEditable: () => true,
            fieldValues: {
              barcodes: [
                {
                  data: "foo",
                  format: "qr_code" as const,
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
        />,
      );

      await user.click(screen.getByRole("button", { name: "common:actions.remove" }));
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
                  data: "foo",
                  format: "qr_code" as const,
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
        />,
      );
      expect(screen.getByLabelText("inventory:fields.barcodes.actions.scan")).toBeVisible();
    });
    test("Tapping the add button opens a scanner dialog with an accessible name.", async () => {
      const user = userEvent.setup();
      vi.spyOn(HTMLVideoElement.prototype, "play").mockImplementation(() => Promise.resolve());
      render(
        <FieldCard
          fieldOwner={{
            isFieldEditable: () => true,
            fieldValues: {
              barcodes: [],
            },
            setFieldsDirty: () => {},
            canChooseWhichToEdit: false,
            setFieldEditable: () => {},
            noValueLabel: { barcodes: null },
          }}
          factory={mockFactory()}
        />,
      );

      await user.click(screen.getByLabelText("inventory:fields.barcodes.actions.scan"));

      /*
       * The accessible name must be on the element with role="dialog" (MUI's
       * paper slot), not the modal wrapper.
       */
      expect(screen.getByRole("dialog", { name: "inventory:fields.barcodes.actions.scan" })).toBeVisible();
    });
  });
});
