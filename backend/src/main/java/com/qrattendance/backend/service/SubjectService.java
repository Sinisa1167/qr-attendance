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

    public boolean isStudentEnrolled(Subject subject, User student) {
        return subject.getStudents().contains(student);
    }

    public Subject enrollStudent(Subject subject, User student) {
        if (!isStudentEnrolled(subject, student)) {
            subject.getStudents().add(student);
            return subjectRepository.save(subject);
        }
        return subject;
    }
}