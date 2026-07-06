package com.pdf2data.platform.document.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.*;

@Service
public class FileStorageService {
    private final Path root = Paths.get("uploads");

    public FileStorageService() {
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize folder for upload!");
        }
    }


    public String saveFile(MultipartFile file) {
        try {
            String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
            Files.copy(file.getInputStream(), this.root.resolve(fileName));
            return this.root.resolve(fileName).toString();
        } catch (Exception e) {
            throw new RuntimeException("Could not store the file. Error: " + e.getMessage());
        }
    }
}
