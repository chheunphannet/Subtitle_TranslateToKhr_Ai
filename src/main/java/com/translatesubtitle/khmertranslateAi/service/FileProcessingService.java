package com.translatesubtitle.khmertranslateAi.service;

import java.nio.file.Path;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;
public interface FileProcessingService {
	List<Path> processFiles(List<MultipartFile> files);
}
