javac -cp libs/* src/main/java/searchEngine/Spider.java src/main/java/searchEngine/Stemmer.java -d bin
java -cp bin;libs/* searchEngine.Spider