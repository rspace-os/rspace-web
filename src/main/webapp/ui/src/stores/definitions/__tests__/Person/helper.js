//@flow

import fc, { type Arbitrary } from "fast-check";
import { type Person } from "../../Person";
import { type Container } from "../../Container";

export const arbitraryPerson: Arbitrary<Person> = fc
  .record<$Diff<Person, {| fullName: string, label: string |}>>({
    id: fc.nat(),
    username: fc.string(),
    firstName: fc.string(),
    lastName: fc.string(),
    bench: fc.constant(null),
    workbenchId: fc.nat(),
    getBench: fc.func<[], Promise<void>>(fc.constant(Promise.resolve())),
    isCurrentUser: fc.boolean(),
    hasSysAdminRole: fc.constant(false),
    hasPiRole: fc.constant(false),
    email: fc.option(fc.string()),
  })
  .map((arbs) => ({
    ...arbs,
    fullName: `${arbs.firstName} ${arbs.lastName}`,
    label: `${arbs.firstName} ${arbs.lastName} (${arbs.username})`,
    getBench: () => Promise.reject<Container>(),
  }));
