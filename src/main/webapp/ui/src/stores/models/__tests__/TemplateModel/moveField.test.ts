/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import fc from "fast-check";
import { makeMockTemplate } from "./mocking";
import { arrayOfSameElements } from "../../../../util/__tests__/helpers";
import { type FieldModelAttrs } from "../../FieldModel";

jest.mock("../../../../common/InvApiService", () => {}); // break import cycle
jest.mock("../../../../stores/stores/RootStore", () => () => ({
  unitStore: {
    assertValidUnitId: () => {},
  },
}));

const fieldData: Array<FieldModelAttrs> = [
  {
    id: 19,
    globalId: "SF19",
    name: "MyNumber",
    type: "number",
    content: "23",
    columnIndex: 1,
    definition: null,
    selectedOptions: null,
    attachment: null,
    mandatory: false,
  },
  {
    id: 20,
    globalId: "SF20",
    name: "MyDate",
    type: "date",
    content: "2020-10-01",
    columnIndex: 2,
    definition: null,
    selectedOptions: null,
    attachment: null,
    mandatory: false,
  },
  {
    id: 21,
    globalId: "SF21",
    name: "MyString",
    type: "string",
    content: "Default string value",
    columnIndex: 3,
    definition: null,
    selectedOptions: null,
    attachment: null,
    mandatory: false,
  },
  {
    id: 22,
    globalId: "SF22",
    name: "MyText",
    type: "text",
    content: "Default text value",
    columnIndex: 4,
    definition: null,
    selectedOptions: null,
    attachment: null,
    mandatory: false,
  },
  {
    id: 23,
    globalId: "SF23",
    name: "MyURL",
    type: "uri",
    content: "https://www.google.com",
    columnIndex: 5,
    definition: null,
    selectedOptions: null,
    attachment: null,
    mandatory: false,
  },
  {
    id: 24,
    globalId: "SF24",
    name: "My reference",
    type: "reference",
    content: "",
    columnIndex: 6,
    definition: null,
    selectedOptions: null,
    attachment: null,
    mandatory: false,
  },
  {
    id: 25,
    globalId: "SF25",
    name: "MyAttachment",
    type: "attachment",
    content: "",
    columnIndex: 7,
    definition: null,
    selectedOptions: null,
    attachment: null,
    mandatory: false,
  },
  {
    id: 26,
    globalId: "SF26",
    name: "radioField",
    type: "radio",
    content: "",
    columnIndex: 8,
    definition: {
      options: ["option1", "option2"],
    },
    selectedOptions: [] as Array<string>,
    attachment: null,
    mandatory: false,
  },
  {
    id: 27,
    globalId: "SF27",
    name: "choiceField",
    type: "choice",
    content: "",
    columnIndex: 9,
    definition: {
      options: ["optionA", "optionB"],
    },
    selectedOptions: [] as Array<string>,
    attachment: null,
    mandatory: false,
  },
];

describe("action: moveField", () => {
  describe("Property Tests", () => {
    test("Moving to top should be idempotent.", () => {
      fc.assert(
        fc.property(fc.nat(fieldData.length - 1), (i) => {
          const template = makeMockTemplate({
            fields: fieldData,
          });
          const field = template.fields[i];
          template.moveField(field, 0);
          const afterOnce = [...template.fields];
          template.moveField(field, 0);
          const afterTwice = [...template.fields];
          expect(arrayOfSameElements(afterOnce, afterTwice)).toBe(true);
        })
      );
    });
    test("Moving to bottom should be idempotent.", () => {
      fc.assert(
        fc.property(fc.nat(fieldData.length - 1), (i) => {
          const template = makeMockTemplate({
            fields: fieldData,
          });
          const field = template.fields[i];
          template.moveField(field, template.fields.length - 1);
          const afterOnce = [...template.fields];
          template.moveField(field, template.fields.length - 1);
          const afterTwice = [...template.fields];
          expect(arrayOfSameElements(afterOnce, afterTwice)).toBe(true);
        })
      );
    });
    test("Re-ordering never changes count.", () => {
      fc.assert(
        fc.property(
          fc
            .nat(fieldData.length - 1)
            .chain((maxLength) =>
              fc.array(fc.tuple(fc.nat(maxLength), fc.nat(maxLength)))
            ),
          (changes) => {
            const template = makeMockTemplate({
              fields: fieldData,
            });
            for (const [fieldIndex, positionIndex] of changes) {
              const field = template.fields[fieldIndex];
              template.moveField(field, positionIndex);
            }
            expect(template.fields.length).toBe(fieldData.length);
          }
        )
      );
    });
  });
});
