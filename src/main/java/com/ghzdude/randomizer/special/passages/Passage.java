package com.ghzdude.randomizer.special.passages;

import java.util.ArrayList;

public class Passage {
    public static final int MAX_CHARS = 798;
    private final String author;
    private final String title;
    private final String body;
    public Passage (String author, String title, String body) {
        this.author = author;
        this.title = title;
        this.body = body;
    }

    public String getAuthor() {
        return author;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public ArrayList<String> parseBody() {
        // parse body into proper book format
        // each page has a max of 798 characters
        // each book has a max of 100 pages in JE
        ArrayList<String> formatted = new ArrayList<>();
        int pages = Math.floorDiv(MAX_CHARS, body.length());

        for (int i = 1; i <= pages; i++) {
            int start = (i - 1) * MAX_CHARS;
            int end = (i) * MAX_CHARS;
            String s = body.substring(start, end);
            int lastSpace = s.lastIndexOf(" ");
            s = body.substring(start, lastSpace);

        }


        return formatted;
    }
}
