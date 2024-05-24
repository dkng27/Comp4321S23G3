# Group 3 Search Engine Project Phase 1 - Spider
## Libraries Used:
Spider makes use of 4 libraries, all of which are jar'd and put under the `libs/` directory.
1. `jdbm-1.0.jar` This is the jdbm library used in lab 1.
2. `htmlparser.jar` This is the htmlparser used in lab 2.
3. `IRUtilities.jar` This is the Porter implementation from lab 3; We've jar'd it to make it more portable. The Stemmer class was moved into the source folder and compiled alongside the spider.
4. `jsoup-1.17.2.jar` The JSoup library is also used for parsing html. It provides functionalities not available in htmlparser, *i.e.*, getting http headers and page size.

## For Grader:
The test program for the spider *is* the main function of the Spider class.
Please observe the following when running the program:
* Windows / Linux / macOS platform
* Java version >= 17 SE (18 should be fine)

A batch file `build_and_run.bat` is provided to automate the building and running of the test program.
But if you want to do it manually, run the following 2 lines:
```agsl
javac -cp libs/* src/main/java/searchEngine/Spider.java src/main/java/searchEngine/Stemmer.java -d bin
java -cp bin;libs/* searchEngine.Spider
```

Alternatively, for Linux / macOS, run `build_and_run.sh` instead, or the following 2 lines:
```sh
javac -cp 'libs/*' src/main/java/searchEngine/Spider.java src/main/java/searchEngine/Stemmer.java -d bin
java -cp 'bin:libs/*' searchEngine.Spider
```

Note: running the test program takes ~5s to write to the output file, it's not stuck.