/*
 */
import { describe, expect, it, vi } from "vitest";
import "@testing-library/jest-dom/vitest";
import { makeMockTemplate } from "./mocking";
import fc from "fast-check";

vi.mock("../../../../common/InvApiService", () => ({ default: {} })); // break import cycle

describe("method: validate", () => {
  describe("Asserts subSampleAlias", () => {
    it("Returns false when the alias is a single character.", () => {
      fc.assert(
        fc.property(fc.string({ maxLength: 1 }), (alias) => {
          const template = makeMockTemplate({
            subSampleAlias: { alias, plural: "validPlural" },
          });

          expect(template.validate().isOk).toBe(false);
        })
      );
    });

    it("Returns false when the plural is a single character.", () => {
      fc.assert(
        fc.property(fc.string({ maxLength: 1 }), (plural) => {
          const template = makeMockTemplate({
            subSampleAlias: { alias: "validAlias", plural },
          });

          expect(template.validate().isOk).toBe(false);
        })
      );
    });

    it("Returns false when alias is more than 30 characters.", () => {
      fc.assert(
        fc.property(fc.string({ minLength: 31 }), (alias) => {
          const template = makeMockTemplate({
            subSampleAlias: { alias, plural: "validPlural" },
          });

          expect(template.validate().isOk).toBe(false);
        })
      );
    });

    it("Returns false when plural is more than 30 characters.", () => {
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
    it("Return true when quantity is explicitly null.", () => {
      const template = makeMockTemplate({
        quantity: null,
      });

      expect(template.validate().isOk).toBe(true);
    });
    it("Return true when quantity is implicitly null.", () => {
      const template = makeMockTemplate({});

      expect(template.validate().isOk).toBe(true);
    });
  });

  describe("All field names should be unique.", () => {
    it("Returns false when field names are duplicated.", () => {
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
});

