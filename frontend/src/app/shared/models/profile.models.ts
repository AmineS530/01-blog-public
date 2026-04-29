export interface ProfileResponse {
  username: string;
  fullName: string;
  bio: string;
  avatarUrl: string;
  followerCount: number;
  followingCount: number;
  isFollowing: boolean;
  isBlocked: boolean;
  isBlockingMe: boolean;
}

export interface ProfileUpdateRequest {
  fullName: string;
  bio: string;
  avatarUrl: string;
}
