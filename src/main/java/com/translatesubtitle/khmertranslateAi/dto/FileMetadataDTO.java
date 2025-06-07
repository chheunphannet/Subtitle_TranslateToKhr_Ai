package com.translatesubtitle.khmertranslateAi.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor // Creates a constructor with all fields
public class FileMetadataDTO {
	private String originalFilename;
	private String contentType;
	private long size;
}