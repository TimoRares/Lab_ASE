package com.instakill.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${media.base-path:media}")
    private String mediaBasePath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadDir = Paths.get(mediaBasePath);
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException e) {
            throw new RuntimeException("Nu s-a putut crea directorul media", e);
        }
        
        // toUri() produce un URL "file:///..." corect si pe Windows; se termina cu "/" pentru ca directorul exista deja
        registry.addResourceHandler("/media/**")
                .addResourceLocations(uploadDir.toAbsolutePath().normalize().toUri().toString());
    }
}