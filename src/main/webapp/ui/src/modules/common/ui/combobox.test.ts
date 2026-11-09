import type { ComponentProps } from "react";
import { expectTypeOf, test } from "vitest";
import type { ComboboxChip, ComboboxInput } from "./combobox";

test("requires labels for each rendered icon button", () => {
  type Input = ComponentProps<typeof ComboboxInput>;
  type Chip = ComponentProps<typeof ComboboxChip>;
  expectTypeOf<Record<never, never>>().not.toExtend<Input>();
  expectTypeOf<{ showTrigger: false }>().toExtend<Input>();
  expectTypeOf<{ triggerLabel: string }>().toExtend<Input>();
  expectTypeOf<{ triggerLabel: string; showClear: true }>().not.toExtend<Input>();
  expectTypeOf<{ triggerLabel: string; showClear: true; clearLabel: string }>().toExtend<Input>();
  expectTypeOf<Record<never, never>>().not.toExtend<Chip>();
  expectTypeOf<{ showRemove: false }>().toExtend<Chip>();
  expectTypeOf<{ removeLabel: string }>().toExtend<Chip>();
});
