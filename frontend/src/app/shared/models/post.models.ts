export interface PostRequest {
  title: string;
  content: string;
  mediaUrl?: string;
}

export interface PostResponse {
  id: number;
  title: string;
  content: string;
  mediaUrl?: string;
  authorUsername: string;
  authorDisplayName?: string;
  authorAvatarUrl?: string;
  createdAt: string;
  updatedAt: string;
  likeCount: number;
  commentCount: number;
  isLikedByCurrentUser: boolean;
}


export interface CommentRequest {
  content: string;
  mediaUrl?: string;
}

export interface CommentResponse {
  id: number;
  content: string;
  mediaUrl?: string;
  authorUsername: string;
  authorDisplayName?: string;
  authorAvatarUrl?: string;
  createdAt: string;
  updatedAt: string;
  likeCount: number;
  isLikedByCurrentUser: boolean;
}