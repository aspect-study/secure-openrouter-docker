package com.openrouter.gateway.conversation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Post-processes assembled LLM responses to produce clean, universally
 * renderable GFM markdown before the text is persisted to the database.
 *
 * This is the second layer of the formatting defense. The first layer is the
 * system prompt injected in ConversationService. This layer catches what small
 * or non-compliant models still get wrong.
 *
 * Operations (applied in order):
 *   1. Split merged rows — a single line containing both header and separator is
 *      split into two lines.
 *   2. Pad table cells — ensure one space around each cell's content.
 *   3. Enforce consistent column count — pad or trim rows to match the header.
 *   4. Inject missing separator row — if a table has no |---| row between header
 *      and first data row, one is injected.
 *   5. Enforce blank line before block elements — tables, fenced code blocks,
 *      and headings get a blank line before them when preceded by non-blank content.
 *
 * All operations are line-based and produce deterministic, idempotent output.
 */
@Component
public class MarkdownNormalizer {

    private static final Logger log = LoggerFactory.getLogger(MarkdownNormalizer.class);

    // A separator cell contains only dashes, colons, and spaces
    private static final Pattern SEPARATOR_CELL = Pattern.compile("^\\s*:?-{1,}:?\\s*$");

    /**
     * Normalize the full assembled response text.
     * Returns the input unchanged if it is null or blank.
     */
    public String normalize(String text) {
        if (text == null || text.isBlank()) return text;

        try {
            List<String> lines = new ArrayList<>(Arrays.asList(text.split("\n", -1)));
            lines = splitMergedTableRows(lines);
            lines = normalizeTableCells(lines);
            lines = enforceBlankBeforeBlocks(lines);
            return String.join("\n", lines);
        } catch (Exception e) {
            // Normalization failure must never affect the user response
            log.warn("MarkdownNormalizer failed (returning original): {}", e.getMessage());
            return text;
        }
    }

    // ── Step 1: Split merged rows ─────────────────────────────────────────────

    /**
     * Detects lines where a header row and a separator row were merged onto one
     * line by the model (e.g. "|A|B|C|---|---|---|") and splits them.
     *
     * Strategy: scan cells of any pipe row. If the row contains at least one
     * separator-pattern cell AND at least one non-separator content cell, split
     * at the first separator cell boundary.
     */
    private List<String> splitMergedTableRows(List<String> lines) {
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            if (!isPipeRow(line)) {
                out.add(line);
                continue;
            }

            String[] cells = splitCells(line);
            int splitIdx = findSplitIndex(cells);
            if (splitIdx < 0) {
                out.add(line);
                continue;
            }

            // First part: cells before the split point
            String headerRow = buildRow(Arrays.copyOfRange(cells, 0, splitIdx));
            // Second part: cells from the split point onward
            String separatorRow = buildRow(Arrays.copyOfRange(cells, splitIdx, cells.length));

            if (!headerRow.isEmpty()) out.add(headerRow);
            if (!separatorRow.isEmpty()) out.add(separatorRow);
        }
        return out;
    }

    /**
     * Find the index in the cell array where cells transition from content to separator.
     * Returns -1 if no split is needed (all separator or all content).
     */
    private int findSplitIndex(String[] cells) {
        boolean seenContent = false;
        for (int i = 0; i < cells.length; i++) {
            boolean isSep = SEPARATOR_CELL.matcher(cells[i]).matches();
            if (!isSep) seenContent = true;
            if (seenContent && isSep) return i;
        }
        return -1;
    }

    // ── Step 2: Normalize table cell padding ──────────────────────────────────

    /**
     * Ensures every cell in every pipe row has exactly one space of padding,
     * and enforces consistent column count based on the header row.
     * Also injects a missing separator row if needed.
     */
    private List<String> normalizeTableCells(List<String> lines) {
        List<String> out = new ArrayList<>();
        int tableHeaderCols = -1;
        boolean awaitingSeparator = false;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);

            if (!isPipeRow(line)) {
                tableHeaderCols = -1;
                awaitingSeparator = false;
                out.add(line);
                continue;
            }

            String[] cells = splitCells(line);
            boolean isSepRow = Arrays.stream(cells).allMatch(c -> SEPARATOR_CELL.matcher(c).matches());

            if (tableHeaderCols < 0) {
                // This is the header row
                tableHeaderCols = cells.length;
                awaitingSeparator = true;
                out.add(padRow(cells, tableHeaderCols, false));
                continue;
            }

            if (awaitingSeparator && !isSepRow) {
                // Missing separator row — inject one before this data row
                out.add(buildSeparatorRow(tableHeaderCols));
                awaitingSeparator = false;
            } else if (isSepRow) {
                awaitingSeparator = false;
            }

            out.add(padRow(cells, tableHeaderCols, isSepRow));
        }
        return out;
    }

    /** Pad/trim a row to exactly {@code cols} columns with proper cell spacing. */
    private String padRow(String[] cells, int cols, boolean isSep) {
        List<String> padded = new ArrayList<>();
        for (int i = 0; i < cols; i++) {
            if (i < cells.length) {
                String c = cells[i].trim();
                padded.add(isSep ? " " + normalizeSeparator(c) + " " : " " + c + " ");
            } else {
                padded.add(isSep ? " --- " : "  ");
            }
        }
        return "|" + String.join("|", padded) + "|";
    }

    private String normalizeSeparator(String cell) {
        // Normalise to simple dashes, preserving alignment colons
        boolean leftAlign  = cell.startsWith(":");
        boolean rightAlign = cell.endsWith(":");
        if (leftAlign && rightAlign) return ":---:";
        if (leftAlign)               return ":---";
        if (rightAlign)              return "---:";
        return "---";
    }

    private String buildSeparatorRow(int cols) {
        List<String> cells = new ArrayList<>();
        for (int i = 0; i < cols; i++) cells.add(" --- ");
        return "|" + String.join("|", cells) + "|";
    }

    // ── Step 3: Blank lines before block elements ─────────────────────────────

    /**
     * Ensures a blank line precedes tables, fenced code blocks, and headings
     * when they follow a non-blank line. remark-gfm and most parsers require
     * this to correctly identify block-level elements.
     */
    private List<String> enforceBlankBeforeBlocks(List<String> lines) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            boolean isBlock = isPipeRow(line)
                    || line.startsWith("```")
                    || line.startsWith("#");

            if (isBlock && !out.isEmpty()) {
                String prev = out.get(out.size() - 1);
                boolean prevIsBlock = isPipeRow(prev) || prev.startsWith("```") || prev.startsWith("#");
                if (!prev.isBlank() && !prevIsBlock) {
                    out.add("");
                }
            }
            out.add(line);
        }
        return out;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isPipeRow(String line) {
        String t = line.trim();
        return t.startsWith("|") || (t.contains("|") && t.endsWith("|"));
    }

    /** Split a pipe row into cell contents (excluding the outer empty tokens). */
    private String[] splitCells(String line) {
        String[] parts = line.trim().split("\\|", -1);
        // parts[0] is empty (before leading |), parts[last] is empty (after trailing |)
        int start = (parts.length > 0 && parts[0].trim().isEmpty()) ? 1 : 0;
        int end   = (parts.length > 1 && parts[parts.length - 1].trim().isEmpty())
                ? parts.length - 1 : parts.length;
        return Arrays.copyOfRange(parts, start, end);
    }

    private String buildRow(String[] cells) {
        if (cells.length == 0) return "";
        List<String> padded = new ArrayList<>();
        for (String c : cells) padded.add(" " + c.trim() + " ");
        return "|" + String.join("|", padded) + "|";
    }
}
