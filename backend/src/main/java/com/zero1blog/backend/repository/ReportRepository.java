package com.zero1blog.backend.repository;

import com.zero1blog.backend.model.Report;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {
    Page<Report> findByStatus(String status, Pageable pageable);
    
    long countByStatus(String status);
    
    long countByTargetUserId(Long targetUserId);
}
