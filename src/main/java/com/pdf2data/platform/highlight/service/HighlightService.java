package com.pdf2data.platform.highlight.service;

import com.pdf2data.platform.highlight.dto.HighlightRegion;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@Service
public class HighlightService {

    private static final int MIN_SNIPPET_LENGTH = 3;

    public List<HighlightRegion> mapHighlights(Map<String, Object> fields, String fullRawText) {
        return mapHighlights(fields, fullRawText, null);
    }

    public List<HighlightRegion> mapHighlights(Map<String, Object> fields, String fullRawText, List<String> pages) {
        List<HighlightRegion> regions = new ArrayList<>();
        if (fields == null || fullRawText == null) {
            return regions;
        }

        String haystack = fullRawText.toLowerCase();

        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            String field = entry.getKey();
            if (field.startsWith("_")) {
                continue;
            }
            Object rawValue = entry.getValue();
            if (rawValue == null) {
                continue;
            }
            String value = String.valueOf(rawValue).trim();
            if (value.length() < MIN_SNIPPET_LENGTH) {
                continue;
            }

            String needle = value.toLowerCase();
            int idx = haystack.indexOf(needle);

            if (idx == -1) {
                regions.add(HighlightRegion.builder()
                        .field(field)
                        .matchedText(value)
                        .page(-1)
                        .startOffset(-1)
                        .endOffset(-1)
                        .found(false)
                        .build());
                continue;
            }

            int page = pages == null ? -1 : locatePage(pages, idx);

            regions.add(HighlightRegion.builder()
                    .field(field)
                    .matchedText(value)
                    .page(page)
                    .startOffset(idx)
                    .endOffset(idx + value.length())
                    .found(true)
                    .build());
        }

        return regions;
    }

    private int locatePage(List<String> pages, int globalOffset) {
        int consumed = 0;
        for (int i = 0; i < pages.size(); i++) {
            String page = pages.get(i) == null ? "" : pages.get(i);
            int pageLength = page.length() + 1;
            if (globalOffset < consumed + pageLength) {
                return i + 1;
            }
            consumed += pageLength;
        }
        return pages.isEmpty() ? -1 : pages.size();
    }
}
