export interface ProfileResponse {
  id: number;
  publicId: string;
  username: string;
  displayName: string;
  bio: string;
  avatarUrl: string;
  followerCount: number;
  followingCount: number;
  isFollowing: boolean;
  isBlocked: boolean;
  isBlockingMe: boolean;
}

export interface ProfileUpdateRequest {
  displayName: string;
  bio: string;
  avatarUrl: string;
}
