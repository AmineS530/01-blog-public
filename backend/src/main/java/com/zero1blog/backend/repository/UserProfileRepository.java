package com.zero1blog.backend.repository;

import com.zero1blog.backend.model.UserProfile;
import com.zero1blog.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {
    Optional<UserProfile> findByUser(User user);

    @Query("SELECT up FROM UserProfile up JOIN FETCH up.user u WHERE u.id IN :userIds")
    List<UserProfile> findByUserIdInWithUser(@Param("userIds") List<Long> userIds);

    List<UserProfile> findByUserIdIn(List<Long> userIds);
}
