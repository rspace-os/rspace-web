import { expectTypeOf, test } from "vitest";
import type { NavItem } from "@/modules/common/app/AppBar.types";

type RouterTo = NonNullable<NavItem["routerTo"]>;

test("routerTo only accepts generated route paths", () => {
  expectTypeOf<"/booking">().toExtend<RouterTo>();
  expectTypeOf<"/not-a-route">().not.toExtend<RouterTo>();
  expectTypeOf<"/nested/booking">().not.toExtend<RouterTo>();
});
