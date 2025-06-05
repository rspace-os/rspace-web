/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import "@testing-library/jest-dom";
import { makeMockTemplate } from "./mocking";
import fc from "fast-check";

jest.mock("../../../../common/InvApiService", () => {}); // break import cycle

describe("method: validate", () => {
  describe("Asserts subSampleAlias", () => {
    test("Returns false when the alias is a single character.", () => {
      fc.assert(
        fc.property(fc.string({ maxLength: 1 }), (alias) => {
          const template = makeMockTemplate({
            subSampleAlias: { alias, plural: "validPlural" },
          });

          expect(template.validate().isOk).toBe(false);
        })
      );
    });

    test("Returns false when the plural is a single character.", () => {
      fc.assert(
        fc.property(fc.string({ maxLength: 1 }), (plural) => {
          const template = makeMockTemplate({
            subSampleAlias: { alias: "validAlias", plural },
          });

          expect(template.validate().isOk).toBe(false);
        })
      );
    });

    test("Returns false when alias is more than 30 characters.", () => {
      fc.assert(
        fc.property(fc.string({ minLength: 31 }), (alias) => {
          const template = makeMockTemplate({
            subSampleAlias: { alias, plural: "validPlural" },
          });

          expect(template.validate().isOk).toBe(false);
        })
      );
    });

    test("Returns false when plural is more than 30 characters.", () => {
      fc.assert(
        fc.property(fc.string({ minLength: 31 }), (plural) => {
          const template = makeMockTemplate({
            subSampleAlias: { alias: "validAlias", plural },
          });

          expect(template.validate().isOk).toBe(false);
        })
      );
    });
  });

  describe("Does not assert a positive quantity", () => {
    test("Return true when quantity is explicitly null.", () => {
      const template = makeMockTemplate({
        quantity: null,
      });

      expect(template.validate().isOk).toBe(true);
    });
    test("Return true when quantity is implicitly null.", () => {
      const template = makeMockTemplate({});

      expect(template.validate().isOk).toBe(true);
    });
  });

  describe("All field names should be unique.", () => {
    fc.assert(
      fc.property(fc.string(), (name) => {
        const template = makeMockTemplate({
          fields: [
            {
              type: "text",
              name,
              selectedOptions: null,
              columnIndex: 0,
              attachment: null,
              mandatory: false,
            },
            {
              type: "text",
              name,
              selectedOptions: null,
              columnIndex: 1,
              attachment: null,
              mandatory: false,
            },
          ],
        });

        expect(template.validate().isOk).toBe(false);
      })
    );
  });
});
