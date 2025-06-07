package com.translatesubtitle.khmertranslateAi.MapperImpl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.translatesubtitle.khmertranslateAi.MapperService.FileMetaDataService;
import com.translatesubtitle.khmertranslateAi.dto.FileMetadataDTO;
@Service
public class FileMetadetaDtoMapperImpl implements FileMetaDataService{

	@Override
	public List<FileMetadataDTO> fileMetadataList(List<MultipartFile> validFiles) {
		return validFiles.stream()
                .map(file -> new FileMetadataDTO(
                        file.getOriginalFilename(),
                        file.getContentType(),
                        file.getSize()))
                .collect(Collectors.toList());
	}


}