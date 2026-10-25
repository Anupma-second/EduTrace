package com.sdms.prototype.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal RFC-4180 style CSV reader (quoted fields, escaped quotes, CRLF). */
public final class CsvUtil {
    private CsvUtil() {}

    public static List<List<String>> parse(String text) {
        if (text != null && text.startsWith("﻿")) text = text.substring(1);
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') { cell.append('"'); i++; }
                    else quoted = false;
                } else cell.append(c);
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                row.add(cell.toString());
                cell.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(cell.toString());
                cell.setLength(0);
                if (!(row.size() == 1 && row.get(0).isBlank())) rows.add(row);
                row = new ArrayList<>();
            } else cell.append(c);
        }
        if (cell.length() > 0 || !row.isEmpty()) {
            row.add(cell.toString());
            if (!(row.size() == 1 && row.get(0).isBlank())) rows.add(row);
        }
        return rows;
    }

    /** First row is the header; header names are lower-cased and trimmed. */
    public static List<Map<String, String>> toMaps(List<List<String>> rows) {
        List<Map<String, String>> out = new ArrayList<>();
        if (rows.isEmpty()) return out;
        List<String> header = new ArrayList<>();
        for (String h : rows.get(0)) header.add(h.trim().toLowerCase());
        for (int i = 1; i < rows.size(); i++) {
            Map<String, String> m = new LinkedHashMap<>();
            List<String> r = rows.get(i);
            for (int j = 0; j < header.size(); j++) m.put(header.get(j), j < r.size() ? r.get(j).trim() : "");
            out.add(m);
        }
        return out;
    }
}
