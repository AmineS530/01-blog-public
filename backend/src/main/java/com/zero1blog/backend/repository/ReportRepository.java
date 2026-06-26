package com.zero1blog.backend.repository;

import com.zero1blog.backend.model.Report;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    @Query(value = "SELECT r FROM Report r LEFT JOIN FETCH r.reporter " +
                   "LEFT JOIN FETCH r.targetUser LEFT JOIN FETCH r.targetPost " +
                   "LEFT JOIN FETCH r.targetComment WHERE r.status = :status",
           countQuery = "SELECT COUNT(r) FROM Report r WHERE r.status = :status")
    Page<Report> findByStatusWithTargets(@Param("status") String status, Pageable pageable);

    Page<Report> findByStatus(String status, Pageable pageable);

    long countByStatus(String status);

    long countByTargetUserId(Long targetUserId);
}
