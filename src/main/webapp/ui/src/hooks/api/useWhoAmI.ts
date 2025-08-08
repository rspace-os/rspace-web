import React from "react";
import axios from "@/common/axios";
import { Person, PersonAttrs } from "@/stores/definitions/Person";
import { Fetched } from "@/util/fetchingData";
import { doNotAwait } from "@/util/Util";
import useOauthToken from "./useOauthToken";

/**
 * Get the current user's information from the /api/v1/userDetails/whoami endpoint.
 */
export default function useWhoAmI(): Fetched<Person> {
  const getToken = useOauthToken();
  const [currentUser, setCurrentUser] = React.useState<Fetched<Person>>({
    tag: "loading",
  });

  React.useEffect(
    doNotAwait(async () => {
      try {
        const { data } = await axios.get<PersonAttrs>(
          "/api/v1/userDetails/whoami",
          {
            headers: {
              Authorization: `Bearer ${await getToken.getToken()}`,
            },
          },
        );
        setCurrentUser({
          tag: "success",
          value: {
            id: data.id,
            username: data.username,
            firstName: data.firstName,
            lastName: data.lastName,
            hasPiRole: data.hasPiRole,
            hasSysAdminRole: data.hasSysAdminRole,
            email: data.email,
            bench: null,
            workbenchId: data.workbenchId,
            getBench: () =>
              Promise.reject(
                new Error("Not implemented by this Person implementation"),
              ),
            isCurrentUser: true,
            fullName: `${data.firstName} ${data.lastName}`,
            label: `${data.firstName} ${data.lastName} (${data.username})`,
          },
        });
      } catch (error) {
        setCurrentUser({
          tag: "error",
          error: error instanceof Error ? error.message : "Unknown error",
        });
      }
    }),
    [],
  );

  return currentUser;
}
