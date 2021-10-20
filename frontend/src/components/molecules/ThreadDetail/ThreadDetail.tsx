import { ThreadType } from "../../../types/threadType";
import './ThreadDetail.css';

type ThreadDetailProps = { thread: ThreadType; level: number };
const ThreadDetail = (props: ThreadDetailProps) => {
  const { thread, level } = props;
  const margin = level * 20 + "px";
  const evenClass = level % 2 === 0 ? "thread-even" : "thread-odd";
  const content =
    "text" in thread
      ? thread.text
      : "title" in thread
      ? thread["title"]
      : "none";
  const contentProps = {
        className: 'thread__content',
        dangerouslySetInnerHTML: { __html: content }
  }
  return (
    <div
      className={`thread thread-level-${level} ${evenClass}`}>
        { 'url' in thread 
        ? <a href={thread['url']} {...contentProps} />
        : <p {...contentProps} />
      }
      <div className={`thread__comments`}>
        {thread.comments.map((item: ThreadType) => (
          <ThreadDetail key={item.id} thread={item} level={level + 1} />
        ))}
      </div>
    </div>
  );
};

export default ThreadDetail;
