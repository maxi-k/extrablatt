import { formatDistance } from "date-fns";
import { fromUnixTime } from "date-fns/esm";
import { useHistory } from "react-router";
import { StoryType } from "../../../types/storyType";
import "./Story.css";

const Story = (props: StoryType) => {
  const history = useHistory();

  const openLink = () => {
    history.push("/thread/" + props.id);
  };

  const relativeTimeStamp = formatDistance(
    fromUnixTime(props.time),
    new Date(),
    { addSuffix: true }
  );

  const truncate = (str: string, n: number) => {
    return str?.length > n ? str.substr(0, n - 4) + "..." : str;
  };

  return (
    <div className="story" onClick={openLink}>
      <div className="story__image__container">
        <img className="story__image" src={props.previewImage} alt="story" />
      </div>
      <div className="story__content">
        <div className="story__content__text">
          <p>
            {props.isHot && <i className="story__hot fas fa-fire"></i>} {truncate(props.title, 50)}
          </p>
        </div>
        </div>
        <div className="story__meta">
          <div className="story__content__author__name">
            <p>@{truncate(props.author, 15)}</p>
          </div>
          <div className="story__content__timestamp">
            <p>{relativeTimeStamp}</p>
          </div>
      </div>
    </div>
  );
};

export default Story;
