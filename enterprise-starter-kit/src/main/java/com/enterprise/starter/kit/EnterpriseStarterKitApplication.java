package com.enterprise.starter.kit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import com.enterprise.starter.kit.shared.tenant.TenantAwareRepository;

/// Main entry point for the Arab Enterprise Kit (Lite) application.
@SpringBootApplication
@EnableScheduling
@EnableJpaRepositories(repositoryBaseClass = TenantAwareRepository.class)
@EnableCaching
public class EnterpriseStarterKitApplication {

    public static void main(String[] args) {
        System.setProperty("spring.main.lazy-initialization", "true");
        SpringApplication.run(EnterpriseStarterKitApplication.class, args);
    }

}
