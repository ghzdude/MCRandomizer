package com.ghzdude.randomizer.special.passages;

public record Passage(String author, String title, String body) {
    public static final int MAX_CHARS = 255;
    public static final int MAX_PAGES = 100;

    public String[] parseBody() {
        // parse body into proper book format
        // each page has a max of 255 characters
        // each book has a max of 100 pages in JE
        int newlinesCount = body.split("\n").length - 1;
        newlinesCount *= Math.floorDiv(MAX_CHARS, 2);
        int pages = Math.floorDiv(body.length() + newlinesCount, MAX_CHARS) + 1;
        if (pages > MAX_PAGES) pages = MAX_PAGES;

        String[] formatted = new String[pages];
        int lastSpace, lastDot, lastNewLine;
        int start = 0;

        for (int i = 0; i < pages; i++) {
            int end = Math.min(start + MAX_CHARS, body.length());

            lastSpace = body.substring(start, end).lastIndexOf(" ");
            lastDot = body.substring(start, end).lastIndexOf(".");
            lastNewLine = body.substring(start, end).lastIndexOf("\n");

            int index = Math.max(lastSpace,  lastDot) + 1;
            if (lastNewLine > 0) {
                index = Math.min(++lastNewLine, index);
            }

            String page = body.substring(start, start + index);
            formatted[i] = page;
            start += index;
        }
        return formatted;
    }
}
