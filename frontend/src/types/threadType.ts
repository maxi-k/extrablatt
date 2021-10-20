import { StoryType } from "./storyType";

export type ThreadType = StoryType & {
	comments: Array<ThreadType>;
	text: string;
}
