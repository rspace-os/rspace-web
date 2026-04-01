import { jwtDecode } from "jwt-decode";

class JwtService {
  // if you change this, change login.jsp and logout.jsp accordingly
  static ID_TOKEN_KEY: string = "id_token";
  static JWT_TOKEN_PATTERN: RegExp = /^.+\..+\..+$/;

  private static getSessionStorage(): Storage | null {
    try {
      return typeof globalThis.sessionStorage?.getItem === "function"
        ? globalThis.sessionStorage
        : null;
    } catch {
      return null;
    }
  }

  static getToken(): string | null {
    try {
      return JwtService.getSessionStorage()?.getItem(JwtService.ID_TOKEN_KEY) ?? null;
    } catch {
      return null;
    }
  }

  static saveToken(token: string): void {
    try {
      JwtService.getSessionStorage()?.setItem(JwtService.ID_TOKEN_KEY, token);
    } catch {
      // Ignore storage access failures in non-browser or restricted environments.
    }
  }

  static destroyToken(): void {
    try {
      JwtService.getSessionStorage()?.removeItem(JwtService.ID_TOKEN_KEY);
    } catch {
      // Ignore storage access failures in non-browser or restricted environments.
    }
  }

  static secondsToExpiry(token: string): number {
    if (!token.match(this.JWT_TOKEN_PATTERN)) {
      // This is an API key
      return Infinity;
    }

    const expiresAt: number = jwtDecode<{ exp: number }>(token).exp;
    const timeNow = Math.floor(Date.now() / 1000);

    return expiresAt - timeNow;
  }

  static isExpiringSoon(token: string): boolean {
    return JwtService.secondsToExpiry(token) < 300;
  }
}

export default JwtService;
