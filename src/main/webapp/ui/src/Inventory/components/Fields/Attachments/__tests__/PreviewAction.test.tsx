import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, test, vi } from "vitest";
import { mockAttachment } from "../../../../../stores/definitions/__tests__/Attachment/mocking";
import { makeMockRootStore } from "../../../../../stores/stores/__tests__/RootStore/mocking";
import { storesContext } from "../../../../../stores/stores-context";
import PreviewAction from "../PreviewAction";

vi.mock("../../../../../common/InvApiService", () => ({ default: {} }));
vi.mock("../../../../../stores/stores/getRootStore", () => ({
  default: () => ({}),
}));
describe("PreviewAction", () => {
  test("An error should be shown if the image cannot be fetched.", async () => {
    const user = userEvent.setup();
    const rootStore = makeMockRootStore({
      uiStore: {
        addAlert: () => {},
      },
    });
    const addAlertMock = vi
      .spyOn(rootStore.uiStore, "addAlert")

      .mockImplementation(() => {});
    const attachment = mockAttachment({
      setImageLink: () => Promise.reject({ message: "foo" }),
    });
    render(
      <storesContext.Provider value={rootStore}>
        <PreviewAction attachment={attachment} />
      </storesContext.Provider>,
    );
    await user.click(screen.getByRole("button", { name: "inventory:fields.attachments.tooltips.previewImage" }));
    await waitFor(() => {
      expect(addAlertMock).toHaveBeenCalledWith(expect.objectContaining({ message: "foo", variant: "error" }));
    });
  });
});
