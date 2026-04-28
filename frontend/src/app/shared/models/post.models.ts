export interface PostRequest {
  title: string;
  content: string;
}

export interface PostResponse {
  id: number;
  title: string;
  content: string;
  authorUsername: string;
  createdAt: string;
  updatedAt: string;
  commentCount: number;
  likeCount: number;
  isLikedByCurrentUser: boolean;
}

export interface CommentRequest {
  content: string;
}

export interface CommentResponse {
  id: number;
  content: string;
  authorUsername: string;
  createdAt: string;
  likeCount: number;
  isLikedByCurrentUser: boolean;
}