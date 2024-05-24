package searchEngine;

import jdbm.htree.HTree;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class Searcher {

    static String ROOT = "https://www.cse.ust.hk/~kwtleung/COMP4321/testpage.htm";
    static int MAX_NUM_PAGE_TO_CRAWL = 300;
    static int NUM_KEYWORDS_TO_DISPLAY = 5;
    static int MAX_PAGE_HITS = 50;
    static int MAX_NUM_LINK_TO_SHOW = 5;
    static String PHRASE_SEARCH_CHAR = "\"";

    Spider index;


    /**
     * Creates new searcher instance and loads index database
     */
    public Searcher() {
        // reindex if necessary
        index = new Spider(ROOT, MAX_NUM_PAGE_TO_CRAWL);
        index.crawl();
        index.generateDocVectors();
        try {
            index.commit();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public searchResult getPageInfoFromPageId(String pageId, double score) {

        Spider.backwardPageIndexData backwardData = null;
        Spider.forwardPageIndexData forwardData = null;

        try {
            backwardData = (Spider.backwardPageIndexData) index.backwardPageIndex.get(pageId);
            forwardData = (Spider.forwardPageIndexData) index.forwardPageIndex.get(backwardData.pageURL);
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("pageId does not exist :(");
            return null;
        }

        // title
        String pageTitle = backwardData.pageTitle;
        // url
        String pageUrl =backwardData.pageURL;
        // last modified, page size
        String metaData = "Last modified on " + (new Date(forwardData.lastModified)) + "; size = " + forwardData.pageSize + " bytes";
        // top keywords
        List<String[]> keywords_ = getTopNKeywords(backwardData.pageWords, NUM_KEYWORDS_TO_DISPLAY);
        StringBuilder keywords = new StringBuilder();
        try {
            for (String[] keyword : keywords_) {
                keywords.append(((Spider.backwardWordIndexData) index.backwardWordIndex.get(keyword[0])).stemmed).append(" ");
            }
        } catch (Exception e){
            e.printStackTrace();
        }
        String keywordFreqs = keywordsListToString(keywords_, index.backwardWordIndex);


        HashSet<String> parentLinks = index.parentLinksTable.get(backwardData.pageURL);
        List<String> parentLinksList = new ArrayList<>(parentLinks.stream().limit(MAX_NUM_LINK_TO_SHOW).toList());
        if (parentLinksList.isEmpty()) parentLinksList.add("None");
        else if (parentLinks.size() > MAX_NUM_LINK_TO_SHOW) parentLinksList.add((parentLinks.size() - MAX_NUM_LINK_TO_SHOW) + " more links hidden...");

        // child links
        List<String> childLinks = new ArrayList<>(backwardData.childURLs.stream().limit(MAX_NUM_LINK_TO_SHOW).toList());
        if (childLinks.isEmpty()) childLinks.add("None");
        else if (backwardData.childURLs.size() > MAX_NUM_LINK_TO_SHOW) childLinks.add((backwardData.childURLs.size() - MAX_NUM_LINK_TO_SHOW) + " more links hidden...");

        return new searchResult(score, pageTitle, metaData, pageUrl, parentLinksList, childLinks, keywordFreqs, keywords.toString().trim());
    }

    public static class searchResult {
        public double score;
        int titlescore;
        public String pageId;
        public String pageTitle;
        public String metaData;
        public String pageUrl;
        public List<String> parentLinks;
        public List<String> childLinks;
        public String keywordFreqs;
        public String keywords;

        public searchResult(double score, int titlescore, String pageId) {
            this.score = score;
            this.titlescore = titlescore;
            this.pageId = pageId;

        }
        public searchResult addTitleScore(){
            this.titlescore++;
            return this;
        }

        public searchResult(double score, String pageTitle, String metaData, String pageUrl, List<String> parentLinks, List<String> childLinks, String keywordFreqs, String keywords){
            this.score = score;
            this.pageTitle = pageTitle;
            this.metaData = metaData;
            this.pageUrl = pageUrl;
            this.parentLinks = parentLinks;
            this.childLinks = childLinks;
            this.keywordFreqs = keywordFreqs;
            this.keywords = keywords;
        }
    }

    /**
     * Returns an array of pageIds given a query.
     *
     * @param queryString The query string
     * @return the list of pageIds of relevant pages, in descending order of relevance.
     */
    public List<searchResult> search(String queryString) throws IOException {
        QueryInfo query = processQueryString(queryString);

        // from process query: if query is null then phrase search contains word doesn't exist -> all pages filtered
        if (query == null) return new Vector<>();

        HashMap<String, searchResult> results = new HashMap<>();
        HashSet<String> filteredPageIds = phraseSearch(query.phrase);

        // find the maxtf for each page and set up the search result tuples
        for (String pageId : filteredPageIds) {
            // page info is added after filtering; null for now
            results.put(pageId, new searchResult(0, 0, pageId));
        }

        HashMap<String, Integer> queryCounts = new HashMap<>();

        // prepare query vector, count occurrences of each word
        for (String queryWordId : query.wordIds) {
            if (!queryCounts.containsKey(queryWordId)) {
                queryCounts.put(queryWordId, 1);
            } else {
                queryCounts.put(queryWordId, queryCounts.get(queryWordId) + 1);
            }
        }
        // find tfmax of query
        int queryTfMax = 0;
        for (Map.Entry<String, Integer> queryCountEntry : queryCounts.entrySet()) {
            if (queryCountEntry.getValue() > queryTfMax) queryTfMax = queryCountEntry.getValue();
        }

        HashMap<String, Double> queryWeights = new HashMap<>();
        for (Map.Entry<String, Integer> queryCountEntry : queryCounts.entrySet()) {
            queryWeights.put(queryCountEntry.getKey(), Spider.getTfIdf(queryCountEntry.getValue(), index.getDf(queryCountEntry.getKey()), index.numPagesInDB, queryTfMax));
//            System.out.println(queryCountEntry.getValue().toString() + " " + index.getDf(queryCountEntry.getKey()) + " " + index.numPagesInDB + " " + queryTfMax);
//            System.out.println(Spider.getTfIdf(queryCountEntry.getValue(), index.getDf(queryCountEntry.getKey()), index.numPagesInDB, queryTfMax));
        }

        for (Map.Entry<String, Double> queryWeightEntry : queryWeights.entrySet()) {
            HashMap<String, Vector<Integer>> titleWordPages = ((HashMap<String, Vector<Integer>>) index.pageTitleIndex.get(queryWeightEntry.getKey()));
            for (String pageId : filteredPageIds) {
                searchResult pageResult = results.get(pageId);
                double di;
                try {
                    di = index.docVectors.get(pageId).get(queryWeightEntry.getKey());

                } catch (Exception e) {
                    di = 0;
                }
                pageResult.score += di * queryWeightEntry.getValue();
                if (titleWordPages.containsKey(pageId)){
                    results.put(pageId, results.get(pageId).addTitleScore());
                }
                results.put(pageId, pageResult);
            }
        }

        for (String pageId : filteredPageIds) {
            searchResult pageResult = results.get(pageId);
            pageResult.score = pageResult.score / getVectorEuclideanSize(queryWeights) / getVectorEuclideanSize(index.docVectors.get(pageId));
            results.put(pageId, pageResult);
        }

        // return top 50 pages with highest scores
        return results.entrySet()
                .stream()
                .filter(result -> result.getValue().score > 0.0025)
                .sorted(Comparator.comparing(k -> -k.getValue().score))
                .sorted(Comparator.comparingInt(k -> -k.getValue().titlescore))
                .limit(MAX_PAGE_HITS)
                .map(result -> getPageInfoFromPageId(result.getValue().pageId, result.getValue().score))
                .collect(Collectors.toList());
    }

    private double getVectorEuclideanSize(HashMap<String, Double> weights) {
        double result = 0;
        for (Map.Entry<String, Double> num : weights.entrySet()) {
            result += num.getValue() * num.getValue();
        }
        return Math.sqrt(result);
    }

    /**
     * Find if a page contains the phrase
     *
     * @param phraseIds The phrase (vector of wordIds) to be searched for
     * @return a hashset of pageIds that contain a phrase match
     */
    HashSet<String> phraseSearch(Vector<String> phraseIds) {
        // no phrase search, all pages eligible
        if (phraseIds == null || phraseIds.isEmpty()) return index.crawledPageIds;
        Vector<String> phrase = (Vector<String>) phraseIds.clone();
        HashSet<String> eligiblePageIds = new HashSet<>();
        try {
            // the () of the first word in phrase
            Spider.backwardWordIndexData backwardData = (Spider.backwardWordIndexData) index.backwardWordIndex.get(phrase.firstElement());
            Vector<String> subPhrase = new Vector<>(phrase.subList(1, phrase.size()));
            for (Map.Entry<String, Vector<Integer>> posInPage : backwardData.wordPages.entrySet()) {
                for (int pos : posInPage.getValue()) {
                    // do not modify the phrase outside dfs
                    Vector<String> phrase_ = (Vector<String>) subPhrase.clone();
                    if (phraseSearchHelper(phrase_, ((Spider.backwardPageIndexData) index.backwardPageIndex.get(posInPage.getKey())).pageWords, pos + 1)) {
                        // this page has phrase in it
                        eligiblePageIds.add(posInPage.getKey());
                        break;
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("contains word doesn't exist :(");
            return new HashSet<>();
        }
        // some pages containing the phrase might not be accessible anymore
        eligiblePageIds.retainAll(index.crawledPageIds);
        return eligiblePageIds;
    }

    // DFS approach, prob could be made faster
    private boolean phraseSearchHelper(Vector<String> phrase, HashMap<String, Vector<Integer>> pageWords, int pos) throws IOException {
        if (phrase.isEmpty()) return true;
        Vector<Integer> v;
        if (((v = pageWords.get(phrase.firstElement())) != null) && v.contains(pos)) {
            phrase.remove(0);
            return phraseSearchHelper(phrase, pageWords, pos + 1);
        } else return false;
    }

    public static List<String[]> getTopNKeywords(HashMap<String, Vector<Integer>> map, int n) {
        return map.entrySet().stream().sorted(Comparator.comparingInt(k -> -k.getValue().size())).limit(n).map(k -> new String[]{k.getKey(), String.valueOf(k.getValue().size())}).collect(Collectors.toList());
    }

    public static String keywordsListToString(List<String[]> list, HTree backwardWordIndex) {
        String s = "";
        for (String[] a : list) {
            try {
                s += (((Spider.backwardWordIndexData) backwardWordIndex.get(a[0])).stemmed + " " + a[1] + "; ");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return s;
    }

    public static class QueryInfo {
        // if start and end are both -1 then no phrase search

        Vector<String> wordIds;
        Vector<String> phrase;

        QueryInfo(Vector<String> ids, Vector<String> phrase) {
            this.wordIds = ids;
            this.phrase = phrase;
        }
    }

    public QueryInfo processQueryString(String queryString) {

        boolean quotesOpened = false;
        boolean closeQuotes = false;

        // normal tokenization keeping quotes
        Vector<String> withQuotes = new Vector<>(List.of(queryString.split("[, .\t\r\n']+")));
        Vector<String> wordIds = new Vector<>();
        Vector<String> phrase = new Vector<>();
        for (int i = 0; i < withQuotes.size(); i++) {
            String stemmed = "";
            if (withQuotes.get(i).contains(PHRASE_SEARCH_CHAR)) {
                withQuotes.set(i, withQuotes.get(i).replace(PHRASE_SEARCH_CHAR, ""));
                if (quotesOpened) closeQuotes = true;
                else quotesOpened = true;
            }
            stemmed = index.stemmer.stem(withQuotes.get(i));
            if (!stemmed.isEmpty()) {
                try {
                    // if word doesn't exist then will throw an error, catch below
                    String wordId = index.forwardWordIndex.get(stemmed).toString();
                    wordIds.add(wordId);
                    if (quotesOpened) phrase.add(wordId);
                } catch (Exception e) {
                    System.err.println(stemmed + " doesnot exist in db :(");
                    // if phrase search contains an unseen word then no page will match; null queryinfo
                    if (quotesOpened) return null;
                }
            }
            if (closeQuotes) {
                quotesOpened = false;
                closeQuotes = false;
            }
        }
        // no phrase search
        if (phrase.isEmpty()) return new QueryInfo(wordIds, null);
        // normal return
        return new QueryInfo(wordIds, phrase);
    }

    public static void main(String[] args) throws IOException {
        Searcher searcher = new Searcher();
//        QueryInfo info = searcher.processQueryString("test");
        Scanner scanner = new Scanner(System.in);
        String query = "";
        while (!query.equals("exit")){
            query = scanner.nextLine();
            List<searchResult> results = searcher.search(query);
            for (searchResult result : results) {
                System.out.println(result.score + " " + result.titlescore + "\t");
            }
            System.out.println("==============================");
        }
    }
}
