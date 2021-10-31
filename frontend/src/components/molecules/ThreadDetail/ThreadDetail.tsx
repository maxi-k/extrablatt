import { useState } from "react";
import { ThreadType } from "../../../types/threadType";
import { relativeTimeStamp } from "../../../util";
import "./ThreadDetail.css";

type ThreadDetailProps = { thread: ThreadType; level: number };
const ThreadDetail = (props: ThreadDetailProps) => {
  const { thread, level } = props;
  const [displayComments, setDisplayComments] = useState(true);

  const recursiveThread = (thread: ThreadType) => (
    <div className={`thread__comments`}>
      {thread.comments.map((item: ThreadType) => (
        <ThreadDetail key={item.id} thread={item} level={level + 1} />
      ))}
    </div>
  );

  if (level === 0) {
    const onClick = () => {
      if (thread.url) window.open(thread.url, "_blank");
    };

    return (
      <div className="thread__header">
        <div className="thread__header__title clickable" onClick={onClick}>
          {thread.title}
        </div>
        <p className="thread__header__subtitle">
          {thread.descendants} comments, posted by {thread.author}{" "}{relativeTimeStamp(thread.time)}
        </p>
        <div className={`thread__comments`}>{recursiveThread(thread)}</div>
      </div>
    );
  } else {
    const contentProps = {
      dangerouslySetInnerHTML: { __html: thread.text },
    };
    return (
      <div className={`thread thread-level-${level}`}>
        <div
          className="thread__content"
          onClick={() => setDisplayComments(!displayComments)}
        >
          <p {...contentProps} />
          {!displayComments &&
            thread.comments &&
            thread.comments.length > 0 && (
              <p className="clickable">[+] click to expand</p>
            )}
        </div>
        {displayComments && (
          <div className={`thread__comments`}>{recursiveThread(thread)}</div>
        )}
      </div>
    );
  }
};

export default ThreadDetail;
