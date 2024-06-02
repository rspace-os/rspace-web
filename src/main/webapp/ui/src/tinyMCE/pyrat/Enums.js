const ErrorReason = {
  None: -1,
  NetworkError: 0,
  APIVersion: 1,
  Unauthorized: 2,
  Timeout: 3,
  BadRequest: 4,
  Unknown: 5,
};
Object.freeze(ErrorReason);

const AnimalType = {
  Animal: 0,
  Pup: 1,
};
Object.freeze(AnimalType);

const AnimalState = {
  Live: "live",
  Sacrificed: "sacrificed",
};
Object.freeze(AnimalState);

const Order = {
  asc: "asc",
  desc: "desc",
};
Object.freeze(Order);

const Sex = {
  None: "",
  Female: "f",
  Male: "m",
  Unknown: "?",
};
Object.freeze(Sex);

export { ErrorReason, AnimalType, AnimalState, Order, Sex };
