package com.pdf2data.platform.highlight.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HighlightRegion {
    private String field;
    private String matchedText;
    private int page;
    private int startOffset;
    private int endOffset;
    private boolean found;
}
