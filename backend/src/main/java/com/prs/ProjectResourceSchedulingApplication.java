package com.prs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@org.springframework.scheduling.annotation.EnableScheduling
@SpringBootApplication
public class ProjectResourceSchedulingApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProjectResourceSchedulingApplication.class, args);
    }

    @org.springframework.context.annotation.Bean
    public org.springframework.boot.CommandLineRunner run(
            org.springframework.security.crypto.password.PasswordEncoder passwordEncoder,
            com.prs.service.ProjectScheduleService scheduleService,
            com.prs.repository.ProjectRepository projectRepository) {
        return args -> {
            try {
                for (var project : projectRepository.findAll()) {
                    scheduleService.recalculateSchedule(project.getId());
                }
            } catch (Exception ignored) {}
        };
    }
}
