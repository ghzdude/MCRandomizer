package com.ghzdude.randomizer.special.passages;

import java.util.ArrayList;

public record Passage(String author, String title, String body) {
    public static final int MAX_CHARS = 255;
    public static final int MAX_PAGES = 100;

    public ArrayList<String> parseBody() {
        // parse body into proper book format
        // each page has a max of 798 characters
        // each book has a max of 100 pages in JE
        ArrayList<String> formatted = new ArrayList<>();
        int pages = Math.floorDiv(body.length(), MAX_CHARS) + 1;
        if (pages > MAX_PAGES) pages = MAX_PAGES;
        int lastSpace;
        int lastDot;
        int start = 0;

        for (int i = 0; i < pages; i++) {
            // diff -= MAX_CHARS - Math.max(lastSpace, lastDot);
            int end = Math.min(start + MAX_CHARS, body.length());

            // int start = (i * MAX_CHARS + i) - diff;
            // int end = Math.min(start + MAX_CHARS, body.length());
            lastSpace = body.substring(start, end).lastIndexOf(" ");
            lastDot = body.substring(start, end).lastIndexOf(".");
            int index = Math.max(lastSpace,  lastDot) + 1;

            formatted.add(body.substring(start, start + index));
            start += index;
        }
        return formatted;
    }
}
