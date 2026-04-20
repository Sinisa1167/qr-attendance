package com.qrattendance.backend.repository;

import com.qrattendance.backend.model.Subject;
import com.qrattendance.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SubjectRepository extends JpaRepository<Subject, String> {
    
    List<Subject> findByCreatedBy(User user);

    List<Subject> findByCode(String code);

    boolean existsByIdAndStudents_Id(String subjectId, String studentId);

    boolean existsByCodeAndTeachingTypeAndGroupNameAndAcademicYear(
        String code, String teachingType, String groupName, String academicYear);
}