import extrablatt from "../../../logo.png";
import { useHistory } from "react-router";

const Header = () => {
  const history = useHistory();

  const goHome = () => {
    history.push("/");
  };

  return (
    <img
      onClick={goHome}
      src={extrablatt}
      alt="extrablatt"
      className="extrablatt"
      style={{ cursor: "pointer" }}
    />
  );
};

export default Header;
