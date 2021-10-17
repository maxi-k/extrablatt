import { useEffect, useState } from "react";
const Home = () => {
  const [data, setData] = useState<any>();
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<boolean>(false);

  useEffect(() => {
    setLoading(true);
    try {
      fetch("http://localhost:8080")
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
        <div>
          {data.map((item: any) => (
            <div key={item.id}>
              <h1>{item.title}</h1>
            </div>
          ))}
        </div>
      )}
			{loading && <div>Loading...</div>}
			{error && <div>Error...</div>}
    </div>
  );
};

export default Home;
