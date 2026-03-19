import { describe, expect, it, vi } from "vitest";
import { resolveToken } from "@/modules/common/utils/auth";

describe("resolveToken", () => {
  it("returns direct token when provided and does not call getToken", async () => {
    const getToken = vi.fn(async () => "callback-token");

    const result = await resolveToken({ token: "direct-token", getToken });

    expect(result).toBe("direct-token");
    expect(getToken).not.toHaveBeenCalled();
  });

  it("uses getToken when direct token is not provided", async () => {
    const getToken = vi.fn(async () => "callback-token");

    const result = await resolveToken({ getToken });

    expect(result).toBe("callback-token");
    expect(getToken).toHaveBeenCalledTimes(1);
  });

  it("throws when neither token nor getToken is provided", async () => {
    await expect(resolveToken({})).rejects.toThrow(
      "Token is required to perform this operation",
    );
  });
});

