//@flow
import { Material, type MaterialAttrs } from "../../MaterialsModel";
import { makeMockContainer } from "../ContainerModel/mocking";

export const makeMockMaterial = (attrs?: Partial<MaterialAttrs>): Material =>
  new Material({
    invRec: makeMockContainer(),
    usedQuantity: { unitId: 3, numericValue: 0 },
    ...attrs,
  });
