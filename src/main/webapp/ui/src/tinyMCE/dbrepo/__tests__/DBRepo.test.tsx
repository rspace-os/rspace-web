import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import DBRepo from "../DBRepo";

describe("DBRepo dialog body", () => {
  it("renders without crashing", () => {
    const { container } = render(<DBRepo />);
    expect(container).toBeTruthy();
  });
});
