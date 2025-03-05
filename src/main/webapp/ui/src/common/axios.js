//@flow strict

/**
 * @module axios
 * @summary Set the HTTP headers that are common to all requests.
 *
 * Rather than import axios directly, the entire application MUST import this
 * module as it sets the headers that are common to all requests. To do this,
 * simply import this module as follows:
 * ```
 * import axios from "@/common/axios";
 * ```
 */

import axios from "axios";

export type {
  Axios,
  AxiosPromise,
  AxiosXHRConfigBase
} from "axios";

/*
 * The server remembers the last the page the user attempted to load when
 * redirecting to the login page, so that any links the user taps such as
 * browser bookmarks will resolve after they have logged in. This is a common
 * pattern in web applications and is used to ensure that the user is taken to
 * the page they were trying to access after they have authenticated.
 *
 * For this to work, we have to the tell the server which requests should not
 * be remembered as otherwise API calls will be navigated to after the user
 * has logged in, if they happen to be the last request from the user's
 * browser, and the user will be presented with a wall of JSON.
 *
 * By setting X-Requested-With to XMLHttpRequest, we are telling the server that
 * this request was made by JavaScript and should not be remembered as the last
 * page the user was trying to access.
 *
 */
// $FlowExpectedError[incompatible-use]
axios.defaults.headers.common["X-Requested-With"] = "XMLHttpRequest";

export default axios;

