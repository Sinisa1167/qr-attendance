package com.qrattendance.backend.service;

import com.qrattendance.backend.model.Subject;
import com.qrattendance.backend.model.User;
import com.qrattendance.backend.repository.SubjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SubjectService {

    private final SubjectRepository subjectRepository;

    public List<Subject> getSubjectsForEmployee(User user) {
        return subjectRepository.findByCreatedBy(user);
    }

    public Optional<Subject> findById(String id) {
        return subjectRepository.findById(id);
    }

    public Subject save(Subject subject) {
        return subjectRepository.save(subject);
    }

    public void delete(String id) {
        subjectRepository.deleteById(id);
    }

    public boolean isStudentEnrolled(String subjectId, String studentId) {
        // Koristi optimizovani SQL EXISTS upit umjesto učitavanja cijele liste u memoriju
        return subjectRepository.existsByIdAndStudents_Id(subjectId, studentId);
    }

    public Subject enrollStudent(Subject subject, User student) {
        if (!isStudentEnrolled(subject.getId(), student.getId())) {
            subject.getStudents().add(student);
            return subjectRepository.save(subject);
        }
        return subject;
    }

    public boolean existsByCodeAndTeachingTypeAndGroupNameAndAcademicYear(
            String code, String teachingType, String groupName, String academicYear) {
        return subjectRepository.existsByCodeAndTeachingTypeAndGroupNameAndAcademicYear(
                code, teachingType, groupName, academicYear);
    }

    public boolean existsForOwner(
            String code, String teachingType, String groupName, String academicYear, User owner) {
        return subjectRepository.existsByCodeAndTeachingTypeAndGroupNameAndAcademicYearAndCreatedBy(
                code, teachingType, groupName, academicYear, owner);
    }
}
