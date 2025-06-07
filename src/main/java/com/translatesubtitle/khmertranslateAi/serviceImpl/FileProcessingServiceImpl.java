package com.translatesubtitle.khmertranslateAi.serviceImpl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.translatesubtitle.khmertranslateAi.service.FileProcessingService;
@Service
public class FileProcessingServiceImpl implements FileProcessingService {
	
	private static final Logger logger = LoggerFactory.getLogger(FileProcessingServiceImpl.class);
	private final Path fileStorageLocation;

	public FileProcessingServiceImpl() {
		// Simple temporary storage. For production, use a configurable path.
		this.fileStorageLocation = Paths.get("temp-uploads").toAbsolutePath().normalize();
		try {
			Files.createDirectories(this.fileStorageLocation);
		} catch (Exception ex) {
			throw new RuntimeException("Could not create the directory where the uploaded files will be stored.", ex);
		}
	}

	private Path saveFile(MultipartFile file, String jobId) throws IOException{
		if (file.getOriginalFilename() == null) {
            throw new IllegalArgumentException("File name cannot be null.");
        }
		// Sanitize file name (basic example)
        String fileName = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));
        
        Path jobDirectory = this.fileStorageLocation.resolve(jobId);
        Files.createDirectories(jobDirectory);
        
        Path targetLocation = jobDirectory.resolve(fileName); 
        Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
        logger.info("Saved file {} to {}", fileName, targetLocation);
        return targetLocation;
	}

	@Override
	public List<Path> processFiles(List<MultipartFile> files) {
		String jobId = UUID.randomUUID().toString();
		List<Path> savedFilePaths = files.stream().map(file -> {
            try {
                return saveFile(file, jobId);
            } catch (IOException e) {
                logger.error("Could not store file {} for job {}: {}", file.getOriginalFilename(), jobId, e.getMessage());
                throw new RuntimeException("Failed to save file " + file.getOriginalFilename(), e);
            }
        }).collect(Collectors.toList());
		return savedFilePaths;
	}
	
	

}
