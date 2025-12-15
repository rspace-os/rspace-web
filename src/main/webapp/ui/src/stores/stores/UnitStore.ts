import {
  runInAction,
  action,
  computed,
  observable,
  makeObservable,
} from "mobx";
import type { RootStore } from "./RootStore";
import ElnApiService from "../../common/ElnApiService";

export type UnitCategory =
  | "dimensionless"
  | "volume"
  | "mass"
  | "temperature"
  | "molarity"
  | "concentration";

export type Unit = {
  id: number;
  label: string;
  category: UnitCategory;
  description: string;
};

const SAVED_UNITS: Array<Unit> =
  localStorage.getItem("units") === null
    ? []
    : JSON.parse(localStorage.getItem("units") as string) ?? [];

export default class UnitStore {
  rootStore: RootStore;
  loading: boolean = true;
  units: Array<Unit> = SAVED_UNITS;

  constructor(rootStore: RootStore) {
    makeObservable(this, {
      loading: observable,
      units: observable,
      fetchUnits: action,
      temperatureUnits: computed,
      massUnits: computed,
      volumeUnits: computed,
      allCategories: computed,
    });
    this.rootStore = rootStore;
  }

  async fetchUnits(): Promise<void> {
    try {
      const { data } = await ElnApiService.get<Array<Unit>>("units");
      runInAction(() => {
        this.units = data;
        this.persistUnits();
        this.loading = false;
      });
    } catch {
      console.error("Could not fetch units");
    }
  }

  get temperatureUnits(): Array<Unit> {
    return this.unitsOfCategory(["temperature"]);
  }

  get massUnits(): Array<Unit> {
    return this.unitsOfCategory(["mass"]);
  }

  get volumeUnits(): Array<Unit> {
    return this.unitsOfCategory(["volume"]);
  }

  unitsOfCategory(categories: Array<string>): Array<Unit> {
    return this.units.filter((u: Unit) => categories.includes(u.category));
  }

  getUnit(unitId: number): Unit | undefined {
    if (this.loading || this.units.length < 1)
      return SAVED_UNITS.find((u: Unit) => u.id === unitId);
    return this.units.find((u: Unit) => u.id === unitId);
  }

  persistUnits(): void {
    localStorage.setItem("units", JSON.stringify(this.units));
  }

  get allCategories(): Array<string> {
    return [...new Set(this.units.map((u) => u.category))];
  }
}
