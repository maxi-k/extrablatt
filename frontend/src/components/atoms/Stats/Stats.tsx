import { useEffect, useState } from "react";
import { StatsType } from "../../../types/statsType";
import { relativeTimeStamp } from "../../../util";
import "./Stats.css";

const Stats = () => {
  const [stats, setStats] = useState({} as StatsType);
  useEffect(() => {
    try {
      const fetchStories = async () => {
        const reply = await fetch(`${process.env.REACT_APP_API_URL}/stats`);
        const data: StatsType = await reply.json();
        setStats(data);
      };
      fetchStories();
    } catch (error) {
      console.log(error);
    }
  }, []);

  return (
    <div className="stats">
      <p>items cached: {stats.itemsCached}</p>
      <p>items to fetch: {stats.itemsToFetch}</p>
      <p>images crawled: {stats.imagesCrawled}</p>
      <p>images found: {stats.imagesFound}</p>
      <p>
        last startpage update:{" "}
        {stats.lastStartpageUpdate &&
          relativeTimeStamp(stats.lastStartpageUpdate)}
      </p>
    </div>
  );
};
export default Stats;
