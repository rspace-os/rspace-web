import { HttpResponse, http } from "msw";
import { expect, expectTypeOf, it } from "vitest";
import { server } from "@/__tests__/mswServer";
import { getCurrentUserProfileImage } from "./generated/users/users";

it("returns a binary success from the generated client as a Blob with its headers", async () => {
  const bytes = new Uint8Array([137, 80, 78, 71]);
  server.use(
    http.get(
      "/api/v2/users/me/profile-image",
      () => new HttpResponse(bytes, { headers: { "Content-Type": "image/png", "Cache-Control": "no-store" } }),
    ),
  );

  const image = await getCurrentUserProfileImage();
  expectTypeOf(image).toEqualTypeOf<{ body: Blob; headers: Headers }>();
  expect(new Uint8Array(await image.body.arrayBuffer())).toEqual(bytes);
  expect(image.headers.get("Cache-Control")).toBe("no-store");
});
