package com.translatesubtitle.khmertranslateAi.controller;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.translatesubtitle.khmertranslateAi.dto.SubtitleEntry;
import com.translatesubtitle.khmertranslateAi.service.FileProcessingService;
import com.translatesubtitle.khmertranslateAi.service.SRTService;
import com.translatesubtitle.khmertranslateAi.service.SubtitleParsingService;
import com.translatesubtitle.khmertranslateAi.serviceImpl.GeminiTranslationService;

@RestController
@RequestMapping("/api")
//@CrossOrigin(origins = { "http://192.168.1.2:8080/api" })
public class SubtitleController {
	@Autowired
	private SubtitleParsingService parsingService;
	@Autowired
	private FileProcessingService fileProcessing;
	@Autowired
	private SRTService srtService;

	private final GeminiTranslationService translationService;

	public SubtitleController(GeminiTranslationService translationService) {
		this.translationService = translationService;
	}

	private List<SubtitleEntry> entries = new ArrayList<>();
	private List<Path> processFiles = new ArrayList<>();

	@PostMapping("/upload")
	public ResponseEntity<?> handleFileUpload(@RequestParam MultipartFile[] files) {
		if (files == null || files.length == 0) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Please provide one or more files to upload.");
		}

		List<MultipartFile> validFiles = Arrays.stream(files).filter(file -> file != null && !file.isEmpty())
				.collect(Collectors.toList());

		if (validFiles.isEmpty()) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.body("Please select one or more valid files to upload.");
		}
		processFiles = fileProcessing.processFiles(validFiles);
		entries = parsingService.entries(processFiles);

		return ResponseEntity.status(HttpStatus.ACCEPTED).body(processFiles.size() + " file uplaod sucessfully!");
	}

	@PostMapping("/startTranslate")
	public ResponseEntity<?> startTranslate() {
		List<SubtitleEntry> block = translationService.translateSubtitles(entries, "Khmer").block();
		for (Path p : processFiles) {
			try {
				srtService.generateAndSaveSRT(block, p.toString());
			} catch (IOException e) {
				System.out.println(e.getMessage());
			}
		}
		return ResponseEntity.ok(block);
	}

	@PostMapping("/download")
    public ResponseEntity<?> download() {
        if (processFiles.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No files available for download.");
        }

        // Use an iterator for safe removal while iterating
        Iterator<Path> iterator = processFiles.iterator();
        Path pathToDownload = null;
        Resource resourceToReturn = null;
        String originalFilename = null;
        
        while (iterator.hasNext()) {
            Path currentPath = iterator.next();
            try {
                Resource resource = new UrlResource(currentPath.toUri());
                if (resource.exists() && resource.isReadable()) {
                    pathToDownload = currentPath;
                    originalFilename = resource.getFilename(); // Get filename before any potential deletion
                    
                    // Read file content into memory
                    byte[] fileContent = Files.readAllBytes(pathToDownload);
                    resourceToReturn = new ByteArrayResource(fileContent);
                    
                    // Now that content is in memory, delete the directory
                    Path parentFolder = pathToDownload.getParent();
                    if (parentFolder != null) { // Ensure parent exists
                    	srtService.deleteDirectoryRecursively(parentFolder);
                        System.out.println("Deleted directory: " + parentFolder);
                    } else {
                        // If no parent, just delete the file (though your logic implies a parent)
                        Files.deleteIfExists(pathToDownload);
                         System.out.println("Deleted file: " + pathToDownload);
                    }
                    // Remove from the list
                    iterator.remove(); 
                    break; // Found a file, prepared it, and marked for removal. Exit loop.
                } else {
                    System.out.println("File not found or not readable, removing from list: " + currentPath);
                    iterator.remove(); // Clean up non-existent/readable files from the list
                }
            } catch (MalformedURLException ex) {
                System.err.println("Malformed URL for path: " + currentPath + " - " + ex.getMessage());
                iterator.remove(); // Remove invalid path
            } catch (IOException ex) {
                System.err.println("IO Error processing path: " + currentPath + " - " + ex.getMessage());
            }
        }

        if (resourceToReturn != null && originalFilename != null) {
            String contentType = "application/x-subrip"; // Standard for SRT files

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + originalFilename + "\"")
                    .body(resourceToReturn);
        } else {
            // If loop finished and resourceToReturn is still null, no downloadable files were found
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No valid files available for download or all processed files had issues.");
        }
    }

    
}
