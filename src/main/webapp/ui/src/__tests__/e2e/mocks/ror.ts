import { HttpResponse, http } from "msw";
import universidadDeLosAndes from "./fixtures/ror-02mhbdp94.json" with { type: "json" };

// RSpace requests `{ror.api.url}/ror.org/{id}` (RoRClient strips "https://" from the ROR ID).
// The fixture is a recorded copy of the real api.ror.org v2 response.
const organisations: Record<string, unknown> = {
  "02mhbdp94": universidadDeLosAndes,
};

export const rorHandlers = [
  http.get("/ror/ror.org/:id", ({ params }) => {
    const organisation = organisations[String(params.id)];
    if (organisation) {
      return HttpResponse.json(organisation);
    }
    return HttpResponse.json({ errors: [`'ror.org/${params.id}' is not a valid ROR ID`] }, { status: 404 });
  }),
];
