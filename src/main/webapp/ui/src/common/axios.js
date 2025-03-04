//@flow

/**
 * @module axios
 * @summary This module returns an axios instance with all of the headers set
 *          that are common to all requests.
 */

import axios from "axios";

// $FlowExpectedError[incompatible-use]
axios.defaults.headers.common["X-Requested-With"] = "XMLHttpRequest";
export default axios;
