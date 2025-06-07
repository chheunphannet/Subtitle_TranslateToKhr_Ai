package com.translatesubtitle.khmertranslateAi.serviceImpl;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.mozilla.universalchardet.UniversalDetector; 
import org.springframework.stereotype.Service;

import com.translatesubtitle.khmertranslateAi.dto.SubtitleEntry;
import com.translatesubtitle.khmertranslateAi.service.SubtitleParsingService;

@Service
public class SubtitleParsingServiceImpl implements SubtitleParsingService {
	private static final Pattern SRT_TIMESTAMP_PATTERN = Pattern
			.compile("(\\d{2}:\\d{2}:\\d{2}[,.]\\d{3})\\s*-->\\s*(\\d{2}:\\d{2}:\\d{2}[,.]\\d{3})");

	public List<SubtitleEntry> parseSubtitleFiles(Path path) throws IOException {
		if (path == null) {
			return new ArrayList<>(); // No files to parse, return empty list
		}
		List<SubtitleEntry> allEntries = new ArrayList<>();

		if (Files.isDirectory(path)) {
			// Log or handle directories as needed, here we skip them
			System.err.println("Skipping directory: " + path.toString());
			return new ArrayList<>(); // Return empty list for directories
		}

		if (!Files.exists(path) || !Files.isReadable(path)) {
			throw new IOException("File does not exist or is not readable: " + path.toString());
		}

		if (Files.size(path) == 0) {
			throw new IllegalArgumentException("Cannot parse an empty file: " + path.getFileName().toString());
		}

		byte[] fileBytes = Files.readAllBytes(path);
		String detectedEncoding = detectEncoding(fileBytes);
		String fileName = path.getFileName().toString();

		// Add entries from the current file to the main list
		allEntries.addAll(parseSrtContent(fileBytes, detectedEncoding, fileName)); // Use addAll here
		
		return allEntries;
	}

	private String detectEncoding(byte[] data) {
		UniversalDetector detector = new UniversalDetector(null);

		detector.handleData(data, 0, data.length);
		detector.dataEnd();
		String encoding = detector.getDetectedCharset();
		detector.reset();

		if (encoding != null) {
			return encoding;
		} else {
			// Fallback to UTF-8 if detection fails
			return StandardCharsets.UTF_8.name();
		}
	}

	private List<SubtitleEntry> parseSrtContent(byte[] fileBytes, String encoding, String fileName) throws IOException {
		List<SubtitleEntry> entries = new ArrayList<>(); // Change to a List
		Charset charset = Charset.forName(encoding);

		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(new ByteArrayInputStream(fileBytes), charset))) {
			String line;
			int currentSequence = -1;
			String startTime = null;
			String endTime = null;
			StringBuilder textBuffer = new StringBuilder();
			boolean expectingTimestamp = false;
			boolean expectingText = false;

			while ((line = reader.readLine()) != null) {
				String trimmedLine = line.trim();

				if (trimmedLine.isEmpty()) { // Blank line indicates end of an entry
					if (currentSequence != -1 && startTime != null && endTime != null && textBuffer.length() > 0) {
						entries.add(new SubtitleEntry(currentSequence, startTime, endTime, textBuffer.toString().trim())); // Add to the list
						// Reset for next entry
						currentSequence = -1;
						startTime = null;
						endTime = null;
						textBuffer.setLength(0);
						expectingTimestamp = false;
						expectingText = false;
					}
					continue;
				}

				if (currentSequence == -1) {
					try {
						currentSequence = Integer.parseInt(trimmedLine);
						expectingTimestamp = true;
						continue;
					} catch (NumberFormatException e) {
						if (expectingText) {
							if (textBuffer.length() > 0) {
								textBuffer.append("\n");
							}
							textBuffer.append(line);
						} else {
							currentSequence = -1;
						}
						continue;
					}
				}

				if (expectingTimestamp) {
					Matcher matcher = SRT_TIMESTAMP_PATTERN.matcher(trimmedLine);
					if (matcher.matches()) {
						startTime = matcher.group(1);
						endTime = matcher.group(2);
						expectingTimestamp = false;
						expectingText = true;
					} else {
						currentSequence = -1;
						expectingTimestamp = false;
					}
					continue;
				}

				if (expectingText) {
					if (textBuffer.length() > 0) {
						textBuffer.append("\n");
					}
					textBuffer.append(line);
				}
			}

			// Add the last entry if the file doesn't end with a blank line
			if (currentSequence != -1 && startTime != null && endTime != null && textBuffer.length() > 0) {
				entries.add(new SubtitleEntry(currentSequence, startTime, endTime, textBuffer.toString().trim())); // Add to the list
			}

		} catch (IOException e) {
			throw e;
		}
		return entries;
	}

	@Override
	public List<SubtitleEntry> entries(List<Path> paths) {
		List<SubtitleEntry> allParsedEntries = new ArrayList<>(); // Rename to avoid confusion
		for (Path path : paths) {
			try {
				List<SubtitleEntry> subtitleEntriesFromFile = parseSubtitleFiles(path);
				if (subtitleEntriesFromFile != null && !subtitleEntriesFromFile.isEmpty()) { // Check for null and emptiness
					allParsedEntries.addAll(subtitleEntriesFromFile);
				}
			} catch (IOException | IllegalArgumentException e) { // Catch IllegalArgumentException too
				System.err.println("Error parsing file " + path.getFileName() + ": " + e.getMessage());
			}
		}
		return allParsedEntries;
	}

}