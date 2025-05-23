/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import ResultCollection from "../../InventoryBaseRecordCollection";
import { PersistedBarcode } from "../../Barcode";
import { makeMockContainer } from "../ContainerModel/mocking";
import RsSet from "../../../../util/set";

jest.mock("../../../../common/InvApiService", () => {});
jest.mock("../../../../stores/stores/RootStore", () => () => ({}));

describe("Computed: fieldValues", () => {
  describe("Should support batch editing of barcodes.", () => {
    test("Should return any shared new barcodes.", () => {
      const containers = new RsSet([
        makeMockContainer({
          id: 1,
          globalId: "IC1",
        }),
        makeMockContainer({
          id: 2,
          globalId: "IC2",
        }),
      ]);

      const collection = new ResultCollection(containers);

      const barcode = new PersistedBarcode({
        data: "foo",
        newBarcodeRequest: true,
        description: "bar",
      });

      containers.forEach((container) => {
        container.setFieldsDirty({
          barcodes: [barcode],
        });
      });

      expect(collection.fieldValues.barcodes).toEqual([barcode]);
    });

    test("Should not return any shared existing barcodes.", () => {
      const containers = new RsSet([
        makeMockContainer({
          id: 1,
          globalId: "IC1",
          barcodes: [
            {
              id: 1,
              created: "2022-07-21T14:00:00",
              createdBy: "user1a",
              data: "foo",
              description: "bar",
              _links: [],
            },
          ],
        }),
        makeMockContainer({
          id: 2,
          globalId: "IC2",
          barcodes: [
            {
              id: 2,
              created: "2022-07-21T14:00:00",
              createdBy: "user1a",
              data: "foo",
              description: "bar",
              _links: [],
            },
          ],
        }),
      ]);

      const collection = new ResultCollection(containers);

      expect(collection.fieldValues.barcodes).toEqual([]);
    });
  });
});
