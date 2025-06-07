package com.translatesubtitle.khmertranslateAi.serviceImpl;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.translatesubtitle.khmertranslateAi.dto.SubtitleEntry;
import com.translatesubtitle.khmertranslateAi.service.SRTService;
@Service
public class SRTServiceImpl implements SRTService {

	/**
     * Generate SRT content from list of SubtitleEntry
     */
	@Override
    public String generateSRT(List<SubtitleEntry> subtitleEntries) {
        StringBuilder srtContent = new StringBuilder();
        
        for (SubtitleEntry entry : subtitleEntries) {
            srtContent.append(entry.getSequence()).append("\n");
            srtContent.append(entry.getStartTime())
                     .append(" --> ")
                     .append(entry.getEndTime())
                     .append("\n");
            srtContent.append(entry.getTranslatedText()).append("\n");
            srtContent.append("\n"); // Empty line between entries
        }
        
        return srtContent.toString();
    }
    
    /**
     * Save SRT content to file
     */
	@Override
    public void saveSRTToFile(String srtContent, String filePath) throws IOException {
        File file = new File(filePath);
        // Create directories if they don't exist
        file.getParentFile().mkdirs();
        
        try (FileWriter writer = new FileWriter(file, StandardCharsets.UTF_8)) {
            writer.write(srtContent);
        }
    }
    
    /**
     * Generate and save SRT file in one step
     */
	@Override
    public void generateAndSaveSRT(List<SubtitleEntry> subtitleEntries, String filePath) throws IOException {
        String srtContent = generateSRT(subtitleEntries);
        saveSRTToFile(srtContent, filePath);
    }
    
    /**
     * Validate SRT time format (HH:MM:SS,mmm)
     */
	@Override
    public boolean isValidSRTTimeFormat(String time) {
        return time.matches("\\d{2}:\\d{2}:\\d{2},\\d{3}");
    }
    
    /**
     * Validate subtitle entry for SRT format
     */
    @Override
    public boolean isValidSRTEntry(SubtitleEntry entry) {
        return entry.getSequence() > 0 && 
               isValidSRTTimeFormat(entry.getStartTime()) && 
               isValidSRTTimeFormat(entry.getEndTime()) && 
               entry.getTranslatedText() != null && 
               !entry.getTranslatedText().trim().isEmpty();
    }
     
    /**
     * Generate SRT with validation
     */
    @Override
    public String generateSRTWithValidation(List<SubtitleEntry> subtitleEntries) throws IllegalArgumentException {
        for (SubtitleEntry entry : subtitleEntries) {
            if (!isValidSRTEntry(entry)) {
                throw new IllegalArgumentException("Invalid subtitle entry at sequence: " + entry.getSequence());
            }
        }
        return generateSRT(subtitleEntries);
    }
    @Override
    public void deleteDirectoryRecursively(Path directory) {
        System.out.println("Attempting to delete directory: " + directory);
        try {
            if (Files.exists(directory)) {
                Files.walk(directory)
                    .sorted(Comparator.reverseOrder()) // Delete files before directories
                    .map(Path::toFile)
                    .forEach(file -> {
                        // System.out.println("Deleting: " + file.getAbsolutePath());
                        if (!file.delete()) {
                            System.err.println("Failed to delete: " + file.getAbsolutePath());
                        }
                    });
                System.out.println("Successfully deleted directory: " + directory);
            } else {
                System.out.println("Directory not found, nothing to delete: " + directory);
            }
        } catch (IOException e) {
            System.err.println("Failed to delete directory: " + directory + " - " + e.getMessage());
        }
    }
    
}
