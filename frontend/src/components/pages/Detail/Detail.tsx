import React, { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { ThreadType } from "../../../types/threadType";
import Loader from "../../atoms/Loader/Loader";
import Header from "../../atoms/Header/Header";
import ThreadDetail from "../../molecules/ThreadDetail";
import "./Detail.css";

const Detail = () => {
  const { id }: { id: string } = useParams();
  const [thread, setThread] = useState<ThreadType>();
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<boolean>(false);

  useEffect(() => {
    try {
      const fetchStories = async () => {
        setLoading(true);
        const reply = await fetch(
          `https://0f86cca2a48392.lhr.domains/thread/${id}`
        );
        const data = await reply.json();
        setThread(data);
        setLoading(false);
      };
      fetchStories();
    } catch (error) {
      setError(true);
    }
  }, [setThread]);

  return (
    <div className="detail">
      <Header />
      {thread && !loading && !error && (
        <ThreadDetail key={thread.id} thread={thread} level={0} />
      )}
      {loading && <Loader />}
      {error && <div>Error...</div>}
    </div>
  );
};

export default Detail;
