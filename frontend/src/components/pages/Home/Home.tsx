import { useEffect, useState } from "react";
import { StoryType } from "../../../types/storyType";
import Story from "../../atoms/Story";
import "./Home.css";
const Home = () => {
  const [data, setData] = useState<[StoryType]>();
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<boolean>(false);

  useEffect(() => {
    setLoading(true);
    try {
      fetch("http://localhost:1001")
        .then((res) => res.json())
        .then((res) => {
          setData(res);
        });
    } catch (error) {
      setError(true);
    }
    setLoading(false);
  }, [setData]);

  return (
    <div>
      {data && !loading && !error && (
        <div className="stories">
          {data.map((item: StoryType) => (
            <Story {...item} />
          ))}
        </div>
      )}
      {loading && <div>Loading...</div>}
      {error && <div>Error...</div>}
    </div>
  );
};

export default Home;
