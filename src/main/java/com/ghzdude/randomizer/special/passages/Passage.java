package com.ghzdude.randomizer.special.passages;

import java.util.ArrayList;
import java.util.List;

public class Passage {
    private final String author;
    private final String title;
    private final ArrayList<String> pages;

    Passage (String author, String title, ArrayList<String> pages) {
        this.author = author;
        this.title = title;
        this.pages = pages;
    }
    Passage (String author, String title, String page) {
        this.author = author;
        this.title = title;
        this.pages = new ArrayList<>(List.of(page));
    }

    public String getAuthor() {
        return author;
    }

    public String getTitle() {
        return title;
    }

    public ArrayList<String> getPages() {
        return pages;
    }
}
