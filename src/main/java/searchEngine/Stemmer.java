package searchEngine;

import IRUtilities.*;

import java.io.*;
import java.util.HashSet;

public class Stemmer {
    private Porter porter;
    private HashSet<String> stopWords;

    public boolean isStopWord(String str) {
        return stopWords.contains(str);
    }

    public Stemmer(String str) {
        porter = new Porter();
        stopWords = new HashSet<String>();

        // use BufferedReader to extract the stopwords in stopwords.txt
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(str));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        String line;
        try {
            while ((line = reader.readLine()) != null) {
                stopWords.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String stem(String str) {
        if (str.isEmpty() || isStopWord(str))
            return "";
        return porter.stripAffixes(str);
    }
}
