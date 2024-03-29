import { useHistory } from "react-router";
import { StoryType } from "../../../types/storyType";
import { relativeTimeStamp } from "../../../util";
import "./Story.css";

const Story = (props: StoryType) => {
  const history = useHistory();

  const openLink = () => {
    history.push("/thread/" + props.id);
  };

  let image = null;
  if (props.previewImage) {
    image = (
      <img className="story__image" src={props.previewImage} alt="story" />
    );
  } else {
    image = (
      <div className="story__image__placeholder">
        <p>No preview available</p>
      </div>
    );
  }

  const followLink = (e: {
    stopPropagation: () => void;
    preventDefault: () => void;
  }) => {
    e.stopPropagation();
    e.preventDefault();
    window.open(props.url, "_blank");
  };

  const truncate = (str: string, n: number) => {
    return str?.length > n ? str.substr(0, n - 4) + "..." : str;
  };

  return (
    <div className="story" onClick={openLink}>
      <div className="story__image__container">{image}</div>
      <div className="story__meta">
        <div className="story__meta__author__name">
          <p>@{truncate(props.author, 15)}</p>
        </div>
        <div className="story__meta__timestamp">
          <p>{relativeTimeStamp(props.time)}</p>
        </div>
      </div>
      <div className="story__content">
        <div className="story__content__text">
          <p onClick={followLink}>
            {props.isHot && <i className="story__hot fas fa-fire"></i>}{" "}
            {truncate(props.title, 50)}
          </p>
        </div>
      </div>
      <div className="story__meta__discussion">
        {!!props.descendants ? (
          <p>
            {props.descendants}{" "}
            {props.descendants === 1 ? "comment" : "comments"}
          </p>
        ) : (
          <p>No comments</p>
        )}
      </div>
    </div>
  );
};

export default Story;
