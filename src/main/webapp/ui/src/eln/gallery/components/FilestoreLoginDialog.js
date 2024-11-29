//@flow

import React, { type Node } from "react";

const FilestoreLoginContext = React.createContext<{|
  login: () => Promise<boolean>,
|}>({
  login: () =>
    Promise.reject(
      new Error("FilestoreLoginDialog is not included in the DOM")
    ),
});

export function useFilestoreLogin(): {|
  login: () => Promise<boolean>,
|} {
  const { login } = React.useContext(FilestoreLoginContext);
  return {
    login,
  };
}

export function FilestoreLoginDialog({ children }: {| children: Node |}): Node {
  const [resolve, setResolve] = React.useState<null | {|
    r: (boolean) => void,
  |}>(null);

  const login = (): Promise<boolean> => {
    return new Promise((r) => {
      setResolve({ r });
    });
  };

  return (
    <>
      <FilestoreLoginContext.Provider value={{ login }}>
        {children}
      </FilestoreLoginContext.Provider>
      {resolve !== null && (
        <button
          onClick={() => {
            resolve.r(true);
          }}
        >
          resolve the promise
        </button>
      )}
    </>
  );
}
