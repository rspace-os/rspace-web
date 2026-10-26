import { describe, expect, it } from "vitest";
import i18n from "@/modules/common/i18n";
import type { RootStore } from "../RootStore";
import UnitStore, { type Unit } from "../UnitStore";

describe("UnitStore display labels", () => {
  it("localizes the dimensionless items label without changing cached API units", async () => {
    const previousLanguage = i18n.language;
    const previousCommonBundle = i18n.hasResourceBundle("fr-CA", "common")
      ? structuredClone(i18n.getResourceBundle("fr-CA", "common"))
      : undefined;
    const previousCache = localStorage.getItem("units");

    try {
      i18n.addResourceBundle("fr-CA", "common", { units: { items: "articles" } }, true, true);
      await i18n.changeLanguage("fr-CA");

      const store = new UnitStore({} as RootStore);
      const apiUnits: Array<Unit> = [
        { id: 1, label: "items", category: "dimensionless", description: "" },
        { id: 2, label: "dozens", category: "dimensionless", description: "" },
        { id: 3, label: "ml", category: "volume", description: "" },
      ];
      store.loading = false;
      store.units = apiUnits;
      store.persistUnits();

      const displayedItems = store.getUnit(1);
      const dimensionlessUnits = store.unitsOfCategory(["dimensionless"]);

      expect(displayedItems).toEqual({ ...apiUnits[0], label: "articles" });
      expect(displayedItems).not.toBe(store.units[0]);
      expect(dimensionlessUnits).toEqual([{ ...apiUnits[0], label: "articles" }, apiUnits[1]]);
      expect(dimensionlessUnits[0]).not.toBe(store.units[0]);
      expect(store.getUnit(2)).toEqual(apiUnits[1]);
      expect(store.getUnit(3)).toEqual(apiUnits[2]);
      expect(store.getUnit(3)).toBe(store.units[2]);
      expect(store.unitsOfCategory(["volume"])).toEqual([apiUnits[2]]);
      expect(store.allCategories).toEqual(["dimensionless", "volume"]);
      expect(store.units).toEqual(apiUnits);
      expect(store.units[0].label).toBe("items");
      expect(JSON.parse(localStorage.getItem("units") ?? "[]")).toEqual(apiUnits);
    } finally {
      await i18n.changeLanguage(previousLanguage);
      if (previousCommonBundle === undefined) {
        i18n.removeResourceBundle("fr-CA", "common");
      } else {
        i18n.addResourceBundle("fr-CA", "common", previousCommonBundle, true, true);
      }
      if (previousCache === null) {
        localStorage.removeItem("units");
      } else {
        localStorage.setItem("units", previousCache);
      }
    }
  });
});
