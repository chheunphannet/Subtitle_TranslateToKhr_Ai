package com.translatesubtitle.khmertranslateAi.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import com.translatesubtitle.khmertranslateAi.dto.SubtitleEntry;

public interface SRTService {
	String generateSRT(List<SubtitleEntry> subtitleEntries);
	void saveSRTToFile(String srtContent, String filePath) throws IOException;
	void generateAndSaveSRT(List<SubtitleEntry> subtitleEntries, String filePath) throws IOException;
	boolean isValidSRTTimeFormat(String time);
	boolean isValidSRTEntry(SubtitleEntry entry);
	String generateSRTWithValidation(List<SubtitleEntry> subtitleEntries) throws IllegalArgumentException;
	void deleteDirectoryRecursively(Path directory);
}
