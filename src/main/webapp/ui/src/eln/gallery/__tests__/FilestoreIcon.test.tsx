import Chip from "@mui/material/Chip";
import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import FilestoreIcon from "../FilestoreIcon";

describe("FilestoreIcon", () => {
  it("takes the class a Chip gives its icon", () => {
    const { container } = render(<Chip icon={<FilestoreIcon />} label="run.dat" />);

    expect(container.querySelector("svg")).toHaveClass("MuiChip-icon");
  });
});
