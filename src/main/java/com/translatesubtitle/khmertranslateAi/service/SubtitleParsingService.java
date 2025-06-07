package com.translatesubtitle.khmertranslateAi.service;

import java.nio.file.Path;
import java.util.List;

import com.translatesubtitle.khmertranslateAi.dto.SubtitleEntry;

public interface SubtitleParsingService {
	List<SubtitleEntry> entries(List<Path> path);
}
