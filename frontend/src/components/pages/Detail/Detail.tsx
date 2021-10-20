import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { ThreadType } from "../../../types/threadType";
import Loader from "../../atoms/Loader/Loader";
import './Detail.css'

type ThreadDetailProps = {thread: ThreadType, level: number}
const ThreadDetail = (props: ThreadDetailProps) => {
	const { thread, level} = props;
	const margin = (level * 20) + 'px';
	const evenClass = level %2 === 0 ? 'thread-even' : 'thread-odd';
	const content =
    "text" in thread
      ? thread.text
      : "title" in thread
      ? thread["title"]
      : "none";
	return (
    <div className={`thread thread-level-${level} ${evenClass}`} style={{ marginLeft: margin }}>
      <p className={`thread__content`}>{content}</p>
      <p className={`thread__comment_count`}>{thread.comments.length} comments</p>
	  <div className={`thread__comments`}>
      {thread.comments.map((item: ThreadType) =>
        <ThreadDetail
          key={item.id}
          thread={item}
          level={level + 1} />
      )}
	  </div>
    </div>
  );
}

const Detail = () => {
  const { id }: { id: string} = useParams();
  const [thread, setThread] = useState<ThreadType>();
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<boolean>(false);

  useEffect(() => {
    try {
      const fetchStories = async () => {
        setLoading(true);
        const reply = await fetch(`https://0f86cca2a48392.lhr.domains/thread/${id}`);
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
		<>
      {thread && !loading && !error && (
        <div className="stories">
            <ThreadDetail key={thread.id} thread={thread} level={0} />
        </div>
      )}
      {loading && <Loader />}
      {error && <div>Error...</div>}
		</>
	);
}

export default Detail;
