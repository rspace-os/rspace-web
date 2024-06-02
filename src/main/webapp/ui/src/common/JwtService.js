// @flow

import jwtDecode from "jwt-decode";

class JwtService {
  // if you change this, change login.jsp and logout.jsp accordingly
  static ID_TOKEN_KEY: string = "id_token";
  static JWT_TOKEN_PATTERN: RegExp = /^.+\..+\..+$/;

  static getToken(): string {
    return window.sessionStorage.getItem(JwtService.ID_TOKEN_KEY);
  }

  static saveToken(token: string): void {
    window.sessionStorage.setItem(JwtService.ID_TOKEN_KEY, token);
  }

  static destroyToken(): void {
    window.sessionStorage.removeItem(JwtService.ID_TOKEN_KEY);
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
