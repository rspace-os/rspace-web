// @flow

export const arraysEqual = <T: string | number>(
  _arr1: Array<T>,
  _arr2: Array<T>
): boolean => {
  if (
    !Array.isArray(_arr1) ||
    !Array.isArray(_arr2) ||
    _arr1.length !== _arr2.length
  )
    return false;

  var arr1 = _arr1.concat<T, T>().sort();
  var arr2 = _arr2.concat<T, T>().sort();

  for (var i = 0; i < arr1.length; i++) {
    if (arr1[i] !== arr2[i]) return false;
  }
  return true;
};

export const isMac = (): boolean => {
  return navigator.platform.indexOf("Mac") > -1;
};

export const humanize = (combination: string): string => {
  if (combination) {
    if (isMac()) {
      combination = combination.replace(/Alt/g, "Option");
      combination = combination.replace(/Meta/g, "Command");
      combination = combination.replace(/control/g, "Command");
      combination = combination.replace(/Ctrl/g, "Control");
    } else {
      combination = combination.replace(/control/g, "Ctrl");
      combination = combination.replace(/Meta/g, "Alt");
    }
  }
  return combination;
};

export const rev_humanize = (combination: string): string => {
  if (combination && isMac()) {
    combination = combination.replace(/Command/g, "Meta");
    combination = combination.replace(/Option/g, "Alt");
    combination = combination.replace(/Control/g, "Ctrl");
  }
  return combination;
};

export const isShortcutSingle = (combination: string): boolean => {
  const combKeys = combination.split(" ");
  var keys = [
    String.fromCharCode(17),
    String.fromCharCode(16),
    String.fromCharCode(18),
    String.fromCharCode(91),
  ];
  const isSingle = combKeys.length === 2 && keys.indexOf(combKeys[1]) !== -1;
  return isSingle;
};

export const isShiftwithsomeKey = (combination: string): boolean => {
  const keys = combination.split(" ");
  let flag = keys.length === 2 && keys[0] === "Shift";
  return flag;
};

export const isShortcutForbidden = (
  combination: string,
  forbidden: Array<string>
): boolean => {
  const keys = combination.split(" ");
  let isForbidden = false;

  forbidden.forEach(function (command) {
    const commandKeys = command.split("+");
    if (arraysEqual(commandKeys, keys)) {
      isForbidden = true;
      return true;
    }
  });

  return isForbidden;
};
