//@flow

import React, { type Node } from "react";

const FilestoreLoginContext = React.createContext<{|
  login: ((boolean) => void) => void,
|}>({
  login: () => {
    throw new Error("FilestoreLoginDialog is not included in the DOM");
  },
});

export function useFilestoreLogin(): {|
  login: ((boolean) => void) => void,
|} {
  const { login } = React.useContext(FilestoreLoginContext);
  return {
    login,
  };
}

export function FilestoreLoginDialog({ children }: {| children: Node |}): Node {
  const [cb, setCb] = React.useState<null | {| cb: (boolean) => void |}>(null);

  const login = (newCb: (boolean) => void) => {
    setCb({ cb: newCb });
  };

  return (
    <>
      <FilestoreLoginContext.Provider value={{ login }}>
        {children}
      </FilestoreLoginContext.Provider>
      {cb !== null && (
        <button
          onClick={() => {
            cb.cb(true);
          }}
        >
          resolve the promise
        </button>
      )}
    </>
  );
}
