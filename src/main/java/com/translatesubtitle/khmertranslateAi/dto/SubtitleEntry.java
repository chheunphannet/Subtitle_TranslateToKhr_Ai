package com.translatesubtitle.khmertranslateAi.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@AllArgsConstructor
@ToString // Useful for debugging
public class SubtitleEntry {
	private int sequence;
	private String startTime;
	private String endTime;
	private String text;
	private String translatedText;
	public SubtitleEntry(int sequence, String startTime, String endTime, String text) {
		this.sequence = sequence;
		this.startTime = startTime;
		this.endTime = endTime;
		this.text = text;
	}
	
	public SubtitleEntry() {}
	
	// In SubtitleEntry.java
	public SubtitleEntry(SubtitleEntry other) {
	    this.sequence = other.sequence;
	    this.startTime = other.startTime;
	    this.endTime = other.endTime;
	    this.text = other.text;
	    this.translatedText = other.translatedText;
	    // ... copy all other relevant fields
	}
	
}
