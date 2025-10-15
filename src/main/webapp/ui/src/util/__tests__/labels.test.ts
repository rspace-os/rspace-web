import { encodeA1Z26, layoutToLabel, layoutToLabels } from "@/util/labels";

describe("labels", () => {
  describe("encodeA1Z26", () => {
    it("encodes 1 -> A and 26 -> Z", () => {
      expect(encodeA1Z26(1)).toBe("A");
      expect(encodeA1Z26(26)).toBe("Z");
    });

    it("documents behaviour for values > 26 (char arithmetic)", () => {
      expect(encodeA1Z26(27)).toBe(String.fromCharCode(64 + 27));
    });
  });

  describe("layoutToLabel", () => {
    it("returns numeric ascending for N123", () => {
      const layout = "N123";
      expect(layoutToLabel(layout, 10, 3)).toBe("3");
    });

    it("returns numeric descending for N321", () => {
      const layout = "N321";
      expect(layoutToLabel(layout, 10, 3)).toBe("8");
    });

    it("returns letter ascending for ABC", () => {
      const layout = "ABC";
      expect(layoutToLabel(layout, 5, 2)).toBe("B");
    });

    it("returns letter descending for CBA", () => {
      const layout = "CBA";
      expect(layoutToLabel(layout, 5, 2)).toBe("D");
    });

    it("throws when n is greater than 24", () => {
      const layout = "N123";
      expect(() => layoutToLabel(layout, 25, 1)).toThrow("grid larger than 24x24 are currently not supported");
    });

    it("throws when position is out of range (less than 1)", () => {
      const layout = "N123";
      expect(() => layoutToLabel(layout, 5, 0)).toThrow("position must be between 1 and n");
    });

    it("throws when position is out of range (greater than n)", () => {
      const layout = "N123";
      expect(() => layoutToLabel(layout, 5, 6)).toThrow("position must be between 1 and n");
    });
  });

  describe("layoutToLabels", () => {
    it("generates an array of value/label for N123", () => {
      const layout = "N123";
      const labels = layoutToLabels(layout, 4);
      expect(labels).toEqual([
        { value: 1, label: "1" },
        { value: 2, label: "2" },
        { value: 3, label: "3" },
        { value: 4, label: "4" },
      ]);
    });

    it("generates an array of letter labels for ABC", () => {
      const layout = "ABC";
      const labels = layoutToLabels(layout, 3);
      expect(labels).toEqual([
        { value: 1, label: "A" },
        { value: 2, label: "B" },
        { value: 3, label: "C" },
      ]);
    });

    it("throws when n is greater than 24", () => {
      const layout = "ABC";
      expect(() => layoutToLabels(layout, 25)).toThrow("grid larger than 24x24 are currently not supported");
    });
  });
});
