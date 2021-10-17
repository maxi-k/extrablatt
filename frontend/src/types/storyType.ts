export type StoryType = {
  author: string;
  descendants: number;
  id: string;
  isHot: boolean;
  previewImage: string;
  score: number;
  time: number;
  title: string;
  type: "story" | "comment";
  url: string;
};
