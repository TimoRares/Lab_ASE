package com.instakill.api.rest;

import com.instakill.common.error.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Upload de imagini/videoclipuri in directorul local configurat prin {@code media.base-path}.
 * Fisierele sunt servite apoi de Spring la {@code /media/**} (vezi WebMvcConfig).
 * Endpoint-ul este protejat: /api/** necesita autentificare (SecurityConfig).
 */
@RestController
@RequestMapping("/api/media")
public class MediaController {

    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("jpg", "jpeg", "png", "gif", "webp", "mp4", "mov", "webm");

    private final Path uploadDir;

    public MediaController(@Value("${media.base-path:media}") String mediaBasePath) {
        this.uploadDir = Paths.get(mediaBasePath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadDir); // creat la pornire daca lipseste
        } catch (IOException e) {
            throw new UncheckedIOException("Nu s-a putut crea directorul media: " + uploadDir, e);
        }
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> uploadMedia(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Fisierul este gol");
        }

        String extension = extractExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BadRequestException("Extensie nepermisa. Permise: " + ALLOWED_EXTENSIONS);
        }

        String contentType = file.getContentType();
        if (contentType != null && !contentType.startsWith("image/") && !contentType.startsWith("video/")
                && !contentType.equals("application/octet-stream")) {
            throw new BadRequestException("Sunt permise doar imagini si videoclipuri");
        }

        String newFilename = UUID.randomUUID() + "." + extension;
        Path target = uploadDir.resolve(newFilename).normalize();
        if (!target.startsWith(uploadDir)) {
            throw new BadRequestException("Nume de fisier invalid");
        }

        Files.createDirectories(uploadDir); // in caz ca a fost sters in timpul rularii
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("mediaUrl", "/media/" + newFilename));
    }

    /** Intoarce extensia in litere mici, fara punct; doar caractere alfanumerice (previne ../ in nume). */
    private static String extractExtension(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot == originalFilename.length() - 1) {
            return "";
        }
        String ext = originalFilename.substring(dot + 1).toLowerCase(Locale.ROOT);
        return ext.matches("[a-z0-9]{1,10}") ? ext : "";
    }
}
