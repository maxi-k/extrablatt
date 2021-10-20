import { useEffect, useState } from "react";
import { StoryType } from "../../../types/storyType";
import Loader from "../../atoms/Loader/Loader";
import Story from "../../atoms/Story";
import "./Home.css";
const Home = () => {
  const [stories, setStories] = useState<[StoryType]>();
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<boolean>(false);

  useEffect(() => {
    try {
      const fetchStories = async () => {
        setLoading(true);
        const reply = await fetch("http://localhost:8080");
        const data = await reply.json();
        setStories(data);
        setLoading(false);
      };
      fetchStories();
    } catch (error) {
      setError(true);
    }
  }, [setStories]);

  return (
    <div>
      {stories && !loading && !error && (
        <div className="stories">
          {stories.map((item: StoryType) => (
            <Story {...item} />
          ))}
        </div>
      )}
      {loading && <Loader />}
      {error && <div>Error...</div>}
    </div>
  );
};

export default Home;
