import { act, render, renderHook, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, test, vi } from "vitest";
import { StructureSearchTypeControl, useSingleMoleculeValidation } from "../StructureSearchControls";

const ketcherWith = (ket: Record<string, unknown>) => ({ getKet: () => Promise.resolve(JSON.stringify(ket)) });

describe("useSingleMoleculeValidation", () => {
  test.each([
    ["no molecule", {}, "common:ketcher.structureSearch.validationNoMolecule"],
    ["two molecules", { mol0: {}, mol1: {} }, "common:ketcher.structureSearch.validationMultipleMolecules"],
  ])("rejects %s", async (_, ket, reason) => {
    const { result } = renderHook(() => useSingleMoleculeValidation());

    act(() => result.current.validate(ketcherWith(ket)));

    await waitFor(() => expect(result.current.validationResult.isError).toBe(true));
    expect(result.current.validationResult.orElseGet(([e]) => e.message)).toBe(reason);
  });

  test("accepts exactly one molecule, ignoring other canvas content", async () => {
    const { result } = renderHook(() => useSingleMoleculeValidation());
    act(() => result.current.validate(ketcherWith({ mol0: {}, root: {} })));
    act(() => result.current.validate(ketcherWith({ mol0: {} })));

    await waitFor(() => expect(result.current.validationResult.isOk).toBe(true));
  });
});

describe("StructureSearchTypeControl", () => {
  test("offers substructure and exact matching", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<StructureSearchTypeControl value="SUBSTRUCTURE" onChange={onChange} />);

    const group = screen.getByRole("radiogroup", { name: "common:ketcher.structureSearch.searchType" });
    expect(group).toBeVisible();
    expect(screen.getByRole("radio", { name: "common:ketcher.structureSearch.searchTypeSubstructure" })).toBeChecked();

    await user.click(screen.getByRole("radio", { name: "common:ketcher.structureSearch.searchTypeExact" }));

    expect(onChange).toHaveBeenCalledWith("EXACT");
  });
});
