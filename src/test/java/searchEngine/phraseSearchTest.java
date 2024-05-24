package searchEngine;

import org.junit.Test;

import java.util.Vector;

import java.io.IOException;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class phraseSearchTest {
    Searcher searcher = null;
    public void initSearcher(){
        if (searcher == null)
            searcher = new Searcher();
    }

    @Test
    public void testPhraseSearch1() throws IOException {
        initSearcher();
        Searcher.QueryInfo info = searcher.processQueryString("\"test page\"");
        HashSet<String> set = searcher.phraseSearch(info.phrase);
        Vector<String> pred = new Vector<>();
        Vector<String> tru = new Vector<>();
        tru.add("https://www.cse.ust.hk/~kwtleung/COMP4321/testpage.htm");
        for (String s : set)
            pred.add(((Spider.backwardPageIndexData) searcher.index.backwardPageIndex.get(s)).pageURL);
        assertEquals(pred, tru);
    }

    @Test
    public void testPhraseSearch2() throws IOException {
        initSearcher();
        Searcher.QueryInfo info = searcher.processQueryString("\"asdf d2 a\"");
        assertNull(info);
    }
}
