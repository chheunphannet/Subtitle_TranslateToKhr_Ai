package com.translatesubtitle.khmertranslateAi.MapperService;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.translatesubtitle.khmertranslateAi.dto.FileMetadataDTO;

public interface FileMetaDataService {
	List<FileMetadataDTO> fileMetadataList(List<MultipartFile> validFiles);
}
