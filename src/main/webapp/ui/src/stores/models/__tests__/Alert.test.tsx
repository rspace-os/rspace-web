/*
 * @jest-environment jsdom
 */
/* eslint-env jest */
import { mkAlert } from "../../contexts/Alert";

describe("Alert", () => {
  test("`id` property should always been unique, even if created immediately one after another.", () => {
    const alert1 = mkAlert({ message: "Foo" });
    const alert2 = mkAlert({ message: "Bar" });

    expect(alert1.id).not.toEqual(alert2.id);
  });
});
