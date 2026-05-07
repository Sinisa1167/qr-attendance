package com.qrattendance.backend.config;

import com.qrattendance.backend.model.*;
import com.qrattendance.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final SubjectRepository subjectRepository;
    private final SessionRepository sessionRepository;
    private final AttendanceRepository attendanceRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void run(String... args) {
     /*   // Pokreni samo ako baza prazna
        if (subjectRepository.count() > 0) {
            System.out.println("=== Baza već ima podatke, preskačem inicijalizaciju ===");
            return;
        }

        System.out.println("=== Inicijalizacija mock podataka ===");

        // Kreiranje profesora
        User profesor = new User();
        profesor.setEmail("profesor1@university.ba");
        profesor.setFirstName("Marko");
        profesor.setLastName("Marković");
        profesor.setRole(User.UserRole.ZAPOSLENI);
        profesor = userRepository.save(profesor);

        // Kreiranje 30 studenata
        List<User> students = createStudents(30);

        // Predmet 1: Informacioni sistemi - tri grupe
        Subject is_g1 = createSubject(profesor, "Informacioni sistemi", "IS1",
                "Računarstvo i informatika", "Bachelor", 2, 3,
                "лабораторијске вјежбе", "G1", "2025/26");
        Subject is_g2 = createSubject(profesor, "Informacioni sistemi", "IS1",
                "Računarstvo i informatika", "Bachelor", 2, 3,
                "лабораторијске вјежбе", "G2", "2025/26");
        Subject is_g3 = createSubject(profesor, "Informacioni sistemi", "IS1",
                "Računarstvo i informatika", "Bachelor", 2, 3,
                "лабораторијске вјежбе", "G3", "2025/26");

        // Predmet 2: Programiranje - dvije grupe
        Subject pr_g1 = createSubject(profesor, "Programiranje 1", "PR1",
                "Računarstvo i informatika", "Bachelor", 1, 1,
                "вјежбе", "G1", "2025/26");
        Subject pr_g2 = createSubject(profesor, "Programiranje 1", "PR1",
                "Računarstvo i informatika", "Bachelor", 1, 1,
                "вјежбе", "G2", "2025/26");

        // Rasporedi studente po grupama
        enrollStudents(is_g1, students.subList(0, 10));
        enrollStudents(is_g2, students.subList(10, 20));
        enrollStudents(is_g3, students.subList(20, 30));
        enrollStudents(pr_g1, students.subList(0, 15));
        enrollStudents(pr_g2, students.subList(15, 30));

        subjectRepository.save(is_g1);
        subjectRepository.save(is_g2);
        subjectRepository.save(is_g3);
        subjectRepository.save(pr_g1);
        subjectRepository.save(pr_g2);

        // Kreiraj sesije sa različitim stopama prisustva
        createSessions(is_g1, 10, 0.85); // G1 - odlično prisustvo
        createSessions(is_g2, 10, 0.60); // G2 - prosječno
        createSessions(is_g3, 10, 0.35); // G3 - kritično prisustvo

        // PR grupe
        createSessions(pr_g1, 8, 0.75);
        createSessions(pr_g2, 8, 0.55);

    }

    private List<User> createStudents(int count) {
        String[] firstNames = {
            "Petar","Ana","Marko","Sara","Luka","Elena","Nikola","Marija","Stefan","Jovana",
            "David","Mila","Vuk","Sofija","Aleksa","Teodora","Filip","Ivana","Bogdan","Katarina",
            "Ognjen","Dunja","Relja","Hana","Lazar","Milena","Igor","Dragana","Nemanja","Jelena"
        };
        String[] lastNames = {
            "Petrović","Anić","Marković","Sarić","Lukić","Radić","Jovanović","Milošević","Nikolić","Pavlović",
            "Đorđević","Kostić","Stanković","Tomić","Živković","Simić","Popović","Ilić","Matović","Vasić",
            "Matić","Petković","Lazarević","Stojanović","Đukić","Ristić","Đorić","Savić","Vuković","Knežević"
        };

        List<User> students = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            User student = new User();
            student.setFirstName(firstNames[i]);
            student.setLastName(lastNames[i]);
            student.setEmail(firstNames[i].toLowerCase() + "." + lastNames[i].toLowerCase() + "@student.etf.unibl.org");
            student.setIndexNumber(String.format("%04d/25", 1100 + i * 5));
            student.setRole(User.UserRole.STUDENT);
            student.setStudentStatus("Redovan");
            students.add(userRepository.save(student));
        }
        return students;
    }

    private Subject createSubject(User profesor, String name, String code,
                                   String studyProgram, String studyType,
                                   int studyYear, int semester,
                                   String teachingType, String groupName,
                                   String academicYear) {
        Subject s = new Subject();
        s.setName(name);
        s.setCode(code);
        s.setStudyProgram(studyProgram);
        s.setStudyType(studyType);
        s.setStudyYear(studyYear);
        s.setSemester(semester);
        s.setTeachingType(teachingType);
        s.setGroupName(groupName);
        s.setAcademicYear(academicYear);
        s.setCreatedBy(profesor);
        s.setStudents(new ArrayList<>());
        return subjectRepository.save(s);
    }

    private void enrollStudents(Subject subject, List<User> students) {
        subject.setStudents(new ArrayList<>(students));
    }

    private void createSessions(Subject subject, int count, double attendanceRate) {
        Random rnd = new Random(subject.getId().hashCode());
        LocalDate startDate = LocalDate.of(2026, 2, 10);
        Session.ActivityType[] types = Session.ActivityType.values();

        for (int i = 0; i < count; i++) {
            Session session = new Session();
            session.setSubject(subject);
            session.setDate(startDate.plusWeeks(i));
            session.setStartTime(LocalTime.of(8, 0));
            session.setEndTime(LocalTime.of(10, 0));
            session.setActivityType(types[i % types.length]);
            session.setStatus(Session.SessionStatus.CLOSED);
            session.setGroupNumber(1);
            session.setCreatedAt(LocalDateTime.now().minusWeeks(count - i));
            session.setActivatedAt(LocalDateTime.now().minusWeeks(count - i).plusMinutes(5));
            session.setClosedAt(LocalDateTime.now().minusWeeks(count - i).plusHours(2));
            session = sessionRepository.save(session);

            // Varijacija prisustva po sesiji
            double sessionRate = attendanceRate + (rnd.nextDouble() - 0.5) * 0.25;
            sessionRate = Math.max(0.15, Math.min(1.0, sessionRate));

            List<User> students = new ArrayList<>(subject.getStudents());
            Collections.shuffle(students, rnd);

            int presentCount = Math.max(1, (int)(students.size() * sessionRate));
            presentCount = Math.min(presentCount, students.size());

            for (User student : students.subList(0, presentCount)) {
                Attendance a = new Attendance();
                a.setSession(session);
                a.setStudent(student);
                a.setIpAddress("192.168.1." + (10 + rnd.nextInt(240)));
                a.setUserAgent("Mozilla/5.0 (Mock DataInitializer)");
                a.setTokenUsed("mock-token-" + i + "-" + student.getId().substring(0, 8));
                attendanceRepository.save(a);
            }
        }*/
    }
}