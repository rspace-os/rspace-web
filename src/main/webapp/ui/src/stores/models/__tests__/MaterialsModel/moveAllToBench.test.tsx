import { screen, within } from "@testing-library/react";
import type React from "react";
import { expect, test, vi } from "vitest";
import { renderWithRealI18n } from "@/__tests__/helpers/realI18n";
import inventory from "@/modules/common/i18n/locales/en-US/inventory.json";
import { ListOfMaterials } from "../../MaterialsModel";
import PersonModel from "../../PersonModel";
import { makeMockContainer } from "../ContainerModel/mocking";
import { personAttrs } from "../PersonModel/mocking";
import { makeMockMaterial } from "./mocking";

const confirm = vi.fn<(title: React.ReactNode, message: React.ReactNode) => Promise<boolean>>();
const mockRootStore = {
  peopleStore: { currentUser: new PersonModel(personAttrs()) },
  materialsStore: { canEdit: true },
  uiStore: { confirm },
};

vi.mock("@/common/InvApiService", () => ({ default: {} }));
vi.mock("@/stores/stores/getRootStore", () => ({
  default: () => mockRootStore,
}));

function makeContainerInsideBench(attrs: { globalId: string; name: string }) {
  const container = makeMockContainer(attrs);
  vi.spyOn(container, "isOnWorkbenchOfUser").mockReturnValue(true);
  vi.spyOn(container, "isDirectlyOnWorkbenchOfUser").mockReturnValue(false);
  return container;
}

test("lists the items already inside the bench within the complete confirmation message", async () => {
  confirm.mockResolvedValue(false);
  const list = new ListOfMaterials({
    id: 1,
    name: "foo",
    description: "",
    elnFieldId: 0,
    materials: [],
  });
  list.materials = [
    makeMockMaterial({ invRec: makeContainerInsideBench({ globalId: "IC1", name: "Box" }) }),
    makeMockMaterial({ invRec: makeContainerInsideBench({ globalId: "IC2", name: " " }) }),
  ];

  await list.moveAllToBench();

  const message = confirm.mock.calls[0]?.[1];
  await renderWithRealI18n(<div data-testid="message">{message}</div>, {
    resources: { inventory },
    defaultNS: "inventory",
  });
  const rendered = screen.getByTestId("message");
  expect(rendered).toMatchTextContent("The items are:");
  expect(rendered).toMatchTextContent("Do you want to move them to your bench?");
  expect(
    within(rendered)
      .getAllByRole("listitem")
      .map((item) => item.textContent),
  ).toEqual(["Box (IC1)", "(IC2)"]);
});
