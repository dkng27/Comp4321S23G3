package searchEngine;

import jdbm.RecordManager;
import jdbm.RecordManagerFactory;
import jdbm.helper.*;
import jdbm.htree.HTree;
import jdbm.helper.Serializer;

import org.htmlparser.beans.LinkBean;
import org.htmlparser.beans.StringBean;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static java.util.UUID.randomUUID;

import org.jsoup.Connection;
import org.jsoup.Jsoup;

public class Spider {

    private RecordManager rm;
    private String root;
    private int maxPage = 30;
    private int numPagesCrawled = 0;
    private ArrayDeque<String> discoveredPages = new ArrayDeque<>();

    int numPagesInDB;

    HTree forwardPageIndex;     // url -> pageId, lastModified, pageSize
    HTree backwardPageIndex;    // pid -> url, title, pageWords, childURLs

    HTree forwardWordIndex;     // stemmed -> wordId
    HTree backwardWordIndex;    // wordId -> stemmed, wordPages

    HTree pageTitleIndex;   // wordId -> titleWordPages

    Stemmer stemmer;

    HashSet<String> crawledPageIds;
    HashSet<String> crawledPages;

    HashMap<String, Integer> dfCache = new HashMap<>();
    HashMap<String, Integer> tfmaxCache = new HashMap<>();
    HashMap<String, HashSet<String>> parentLinksTable = new HashMap<>();

    HashMap<String, HashMap<String, Double>> docVectors = new HashMap<>();

    static public class backwardPageIndexData implements Serializable {
        @Serial
        static final long serialVersionUID = 1L;
        String pageURL;
        String pageTitle;
        HashMap<String, Vector<Integer>> pageWords;
        Vector<String> childURLs;

        public backwardPageIndexData(String pageURL, String pageTitle, HashMap<String, Vector<Integer>> pageWords, Vector<String> childURLs) {
            this.pageURL = pageURL;
            this.pageTitle = pageTitle;
            this.pageWords = pageWords;
            this.childURLs = childURLs;
        }

    }

    static public class forwardPageIndexData implements Serializable {

        @Serial
        static final long serialVersionUID = 1L;
        String pageId;
        long lastModified;
        int pageSize;

        public forwardPageIndexData(String pageId, long lastModified, int pageSize) {
            this.pageId = pageId;
            this.lastModified = lastModified;
            this.pageSize = pageSize;
        }

    }

    static public class backwardWordIndexData implements Serializable {

        @Serial
        static final long serialVersionUID = 1L;
        String stemmed;
        HashMap<String, Vector<Integer>> wordPages;

        public backwardWordIndexData(String stemmed, HashMap<String, Vector<Integer>> wordPages) {
            this.stemmed = stemmed;
            this.wordPages = wordPages;
        }
    }


    /**
     * Creates a Spider instance.
     *
     * @param root    A String. The root URL of website to be crawled
     * @param maxPage Integer. Number of pages to be crawled from root. Default is 30.
     */
    public Spider(String root, int maxPage) {
        this.root = root;
        this.maxPage = maxPage;
        discoveredPages.add(this.root);

        stemmer = new Stemmer("stopwords.txt");

        try {
            rm = RecordManagerFactory.createRecordManager("searchEngineRM");

            // load up the relations, should have a better implementation but copy & paste for now

            long recid = rm.getNamedObject("forwardPageIndex");
            if (recid != 0) {
//                System.out.println("forwardPageIndex loaded");
                forwardPageIndex = HTree.load(rm, recid);
            } else {
                forwardPageIndex = HTree.createInstance(rm);
                rm.setNamedObject("forwardPageIndex", forwardPageIndex.getRecid());
            }

            recid = rm.getNamedObject("backwardPageIndex");
            if (recid != 0) {
//                System.out.println("backwardPageIndex loaded");
                backwardPageIndex = HTree.load(rm, recid);
            } else {
                backwardPageIndex = HTree.createInstance(rm);
                rm.setNamedObject("backwardPageIndex", backwardPageIndex.getRecid());
            }

            recid = rm.getNamedObject("forwardWordIndex");
            if (recid != 0) {
//                System.out.println("forwardWordIndex loaded");
                forwardWordIndex = HTree.load(rm, recid);
            } else {
                forwardWordIndex = HTree.createInstance(rm);
                rm.setNamedObject("forwardWordIndex", forwardWordIndex.getRecid());
            }

            recid = rm.getNamedObject("backwardWordIndex");
            if (recid != 0) {
//                System.out.println("backwardWordIndex loaded");
                backwardWordIndex = HTree.load(rm, recid);
            } else {
                backwardWordIndex = HTree.createInstance(rm);
                rm.setNamedObject("backwardWordIndex", backwardWordIndex.getRecid());
            }

            recid = rm.getNamedObject("pageTitleIndex");
            if (recid != 0) {
//                System.out.println("pageTitleIndex loaded");
                pageTitleIndex = HTree.load(rm, recid);
            } else {
                pageTitleIndex = HTree.createInstance(rm);
                rm.setNamedObject("pageTitleIndex", pageTitleIndex.getRecid());
            }


        } catch (IOException ioe) {
            ioe.printStackTrace();  // eh
        }
    }

    public static long getLastModified(String _url) {
        try {
            URL url = new URL(_url);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            long lastModifiedTimestamp = connection.getLastModified();
            return lastModifiedTimestamp;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public static String getPageTitle(String url) {
        // Use Jsoup to efficiently get title
        try {
            return Jsoup.connect(url).get().title();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "Title not found";
    }

    public static int getPageSize(String url) {
        try {
            Connection connection = Jsoup.connect(url);
            String headerSizeStr = connection.execute().header("Content-Length");    // size in bytes from header
            if (headerSizeStr != null) return Integer.parseInt(headerSizeStr);
            else {
                // header does not have content length
                // count number of characters
                return connection.get().text().length();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return 0;   // if fails to connect return 0
    }

    protected String[] toStemmedTokens(String s) {
        String[] stemmedArr = s.split("[, .\t\r\n'\"]+");
        for (int i = 0; i < stemmedArr.length; i++) {
            stemmedArr[i] = stemmer.stem(stemmedArr[i]);
        }
        return stemmedArr;
    }

    /**
     * update forward and backward page indices to accommodate new given page
     *
     * @param url link to the page to be added
     * @return the ID of the new page; will return null if page is already crawled and unmodified; return existing pageId if page has been updated
     * @throws IOException
     */
    private String addPage(String url) throws IOException {

        String pageId;
        long lastMdf = getLastModified(url);
        forwardPageIndexData forwardPageData = (forwardPageIndexData) forwardPageIndex.get(url);

        // page is new
        if (forwardPageData == null) pageId = randomUUID().toString();

            // page hasn't been updated
        else if (lastMdf <= forwardPageData.lastModified) return null;

            // page updated
        else {
            pageId = forwardPageData.pageId;
        }

        // page is new or updated, new tuple or clear old tuple
        forwardPageData = new forwardPageIndexData(pageId, lastMdf, getPageSize(url));
        forwardPageIndex.put(url, forwardPageData);

        String pageTitle = getPageTitle(url);

        // add new empty record for backward page
        backwardPageIndexData backwardPageData = new backwardPageIndexData(url, pageTitle, new HashMap<>(), new Vector<>());
        backwardPageIndex.put(pageId, backwardPageData);

        // add words into inverted title index
        String[] stemmedTokens = toStemmedTokens(pageTitle);
        for (int i = 0; i < stemmedTokens.length; i++) {
            addTitleWord(stemmedTokens[i], pageId, i);
        }

        return pageId;
    }

    private String getWordIdfromStem(String stemmed) throws IOException {
        String wordId;
        if ((wordId = (String) forwardWordIndex.get(stemmed)) == null) {
            // new word, add new word id
            wordId = UUID.randomUUID().toString();
            forwardWordIndex.put(stemmed, wordId);
            backwardWordIndex.put(wordId, new backwardWordIndexData(stemmed, new HashMap<>()));
            pageTitleIndex.put(wordId, new HashMap<>());
        }
        return wordId;
    }

    /**
     * Add new word entry into forward and backward word indices, and backward page index word positions
     *
     * @param stemmed the stemmed form of the word to be added
     * @param pageId  ID of the page at which the word appears.
     * @param i       the position of the word in the page.
     * @throws IOException
     */
    private void addWord(String stemmed, String pageId, int i) throws IOException {
        String wordId = getWordIdfromStem(stemmed);
        // update word records
        backwardWordIndexData wordDetails = (backwardWordIndexData) backwardWordIndex.get(wordId);
        Vector<Integer> positions;
        if ((positions = wordDetails.wordPages.get(pageId)) != null) {   // if page already contains this word
            positions.add(i);
            wordDetails.wordPages.put(pageId, positions);
        } else {
            wordDetails.wordPages.put(pageId, new Vector<>(List.of(i))); // first time seeing this word in page
        }
        backwardWordIndex.put(wordId, wordDetails);

        // update page records
        backwardPageIndexData pageDetails = (backwardPageIndexData) backwardPageIndex.get(pageId);
        if ((positions = pageDetails.pageWords.get(wordId)) != null) {  // if page already contains this word
            positions.add(i);
            pageDetails.pageWords.put(wordId, positions);
        } else {
            pageDetails.pageWords.put(wordId, new Vector<>(List.of(i)));         // first time seeing this word in page
        }
        backwardPageIndex.put(pageId, pageDetails);

    }

    private void addTitleWord(String stemmed, String pageId, int i) throws IOException {
        String wordId = getWordIdfromStem(stemmed);

        // update word records
        HashMap<String, Vector<Integer>> titleWordDetails = (HashMap<String, Vector<Integer>>) pageTitleIndex.get(wordId);
        Vector<Integer> positions;  // positions of words in page title
        if ((positions = titleWordDetails.get(pageId)) != null) {   // if page title already contains this word
            positions.add(i);
            titleWordDetails.put(pageId, positions);
        } else {
            titleWordDetails.put(pageId, new Vector<>(List.of(i))); // first time seeing this word in page title
        }
        pageTitleIndex.put(wordId, titleWordDetails);
    }

    public void crawl() {
        numPagesCrawled = 0;
        crawledPageIds = new HashSet<>();
        crawledPages = new HashSet<>();
        try {
            LinkBean lb = new LinkBean();
            StringBean sb = new StringBean();
            boolean skipCurrentPage = false;
            while (!discoveredPages.isEmpty() && numPagesCrawled < maxPage) {
                String currentUrl = discoveredPages.poll();

                String currentPageId = addPage(currentUrl);
                // if return null then this page has already been crawled
                if (currentPageId == null) {
                    System.out.println("skipped " + currentUrl);
                    currentPageId = ((forwardPageIndexData) forwardPageIndex.get(currentUrl)).pageId;
                    skipCurrentPage = true;
                }

                if (!skipCurrentPage) {

                    System.out.println("crawling " + currentUrl);

                    lb.setURL(currentUrl);
                    sb.setURL(currentUrl);

                    // get all text from the page (inc title) and tokenize into string array
                    // turns out stringbeans already gives you the title
                    String[] words = toStemmedTokens(sb.getStrings());

                    // for each token, stem it, and if it's a "solid" word then add word into records
                    for (int i = 0, j = 0; i < words.length; i++) {
//                        String stemmed = stemmer.stem(words[i]);
                        String stemmed = words[i];
                        if (!stemmed.isEmpty()) {
                            addWord(stemmed, currentPageId, j++);
                        }
                    }

                    backwardPageIndexData backwardData = (backwardPageIndexData) backwardPageIndex.get(currentPageId);
                    Vector<String> childLinkRecord = backwardData.childURLs;

                    // get all child links on current page and add to discovered (but uncrawled) pages for BFS crawling
                    // also update childLink record in backwardPageIndex
                    URL[] childURLs = lb.getLinks();
                    //
                    HashSet<String> parentLink;
                    for (URL link : childURLs) {
                        String linkStr = link.toString();
                        childLinkRecord.add(linkStr);
                        if (!crawledPages.contains(linkStr) && !discoveredPages.contains(linkStr))  // page already in waitlist will not be added twice
                            discoveredPages.add(linkStr);
                        if ((parentLink = parentLinksTable.get(linkStr)) == null)
                            parentLink = new HashSet<>();
                        parentLink.add(currentUrl);
                        parentLinksTable.put(linkStr, parentLink);
                    }
                    backwardPageIndex.put(currentPageId, backwardData);

                } else {
                    skipCurrentPage = false;
                    Vector<String> childURLs = ((backwardPageIndexData) backwardPageIndex.get(currentPageId)).childURLs;
                    HashSet<String> parentLink;
                    for (String link : childURLs) {
                        if (!crawledPages.contains(link) && !discoveredPages.contains(link))  // page already in waitlist will not be added twice
                            discoveredPages.add(link);
                        if ((parentLink = parentLinksTable.get(link)) == null)
                            parentLink = new HashSet<>();
                        parentLink.add(currentUrl);
                        parentLinksTable.put(link, parentLink);
                    }
                }

                // current page crawled
                numPagesCrawled++;
                crawledPages.add(currentUrl);
                crawledPageIds.add(currentPageId);
            }

            {
                // find N
                FastIterator iter = forwardPageIndex.keys();
                String key;
                numPagesInDB = 0;
                while ((key = (String) iter.next()) != null) {
                    numPagesInDB++;
                }
            }

        } catch (
                Exception e) {
            e.printStackTrace();    // in case anything messes up
        }

    }

    // for phase 1 txt output
    public void dumpDB() {
        try {
            // check
            BufferedWriter writer = new BufferedWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream("spider_result.txt"), StandardCharsets.UTF_8)));

            FastIterator iter = forwardPageIndex.keys();
            String key;
            while ((key = (String) iter.next()) != null) {
                // retrieve data from db
                forwardPageIndexData forwardData = (forwardPageIndexData) forwardPageIndex.get(key);
                backwardPageIndexData backwardData = (backwardPageIndexData) backwardPageIndex.get(forwardData.pageId);
                // write title
                writer.write(backwardData.pageTitle);
                writer.newLine();
                // write url
                writer.write(key);
                writer.newLine();
                // write mdf and page size
                writer.write(new Date(forwardData.lastModified).toString() + " " + String.valueOf(forwardData.pageSize) + " bytes");
                writer.newLine();
                // print keywords
                writer.write(Searcher.keywordsListToString(Searcher.getTopNKeywords(backwardData.pageWords, 50), backwardWordIndex));
                writer.newLine();

                // print child links
                for (String child : backwardData.childURLs) {
                    writer.write(child);
                    writer.newLine();
                }

                writer.write("———————————————");
                writer.newLine();

            }

            writer.close();

        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    int getDf(String wordId) {
        try {
            if (dfCache.containsKey(wordId)) {
                return dfCache.get(wordId);
            } else {
                int df = ((Spider.backwardWordIndexData) backwardWordIndex.get(wordId)).wordPages.size();
                dfCache.put(wordId, df);
                return df;
            }
        } catch (Exception e) {
            return 1;
        }
    }

    int getTfMax(String pageId) {
        try {
            if (tfmaxCache.containsKey(pageId)) {
                return tfmaxCache.get(pageId);
            } else {
                int tfMax = 0;
                HashMap<String, Vector<Integer>> pageWords = ((Spider.backwardPageIndexData) backwardPageIndex.get(pageId)).pageWords;
                for (Map.Entry<String, Vector<Integer>> pageWordsEntry : pageWords.entrySet()) {
                    if (tfMax < pageWordsEntry.getValue().size()) tfMax = pageWordsEntry.getValue().size();
                }
                tfmaxCache.put(pageId, tfMax);
                return tfMax;
            }
        } catch (Exception e) {
            return 0;
        }
    }

    static double getTfIdf(int tf, int df, int N, int tfmax) {
        return tf * Math.log((double) N / df) / Math.log(2) / tfmax;
    }

    void generateDocVectors() {
        for (String pageId : crawledPageIds) {
            HashMap<String, Double> weights = new HashMap<>();
            try {
                backwardPageIndexData backwardData = (backwardPageIndexData) backwardPageIndex.get(pageId);
                for (Map.Entry<String, Vector<Integer>> pageWordsEntry : backwardData.pageWords.entrySet()) {
                    weights.put(pageWordsEntry.getKey(), getTfIdf(pageWordsEntry.getValue().size(), getDf(pageWordsEntry.getKey()), numPagesInDB, getTfMax(pageId)));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            docVectors.put(pageId, weights);
        }
    }

    public void close() throws IOException {
        rm.commit();
        rm.close();
    }

    public void commit() throws IOException {
        rm.commit();
    }

    // entry point for class
    public static void main(String[] args) {
        Spider spider = new Spider("https://www.cse.ust.hk/~kwtleung/COMP4321/testpage.htm", 30);
        try {
            spider.crawl();
            spider.dumpDB();
            spider.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
