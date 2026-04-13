export type TokenParams = {
  token?: string;
  getToken?: () => Promise<string>;
};

export const resolveToken = async ({ token, getToken }: TokenParams) => {
  if (token) {
	  return token;
  }
  if (getToken) {
	  return getToken();
  }
  throw new Error("Token is required to perform this operation");
};

