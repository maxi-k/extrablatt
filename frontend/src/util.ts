import { formatDistance, fromUnixTime } from "date-fns";

export const relativeTimeStamp = (input: number) =>
  formatDistance(fromUnixTime(input), new Date(), { addSuffix: true });
