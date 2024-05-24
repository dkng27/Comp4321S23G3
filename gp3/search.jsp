<%@ page import="searchEngine.Searcher" %>
<%@ page import="searchEngine.Searcher.*" %>
<%@ page import="java.util.*" %>

<%
List<searchResult> results = (List<searchResult>) request.getAttribute("results");
String query = (String) request.getAttribute("query");
String htmlQuery = "";
if (query!=null)
for (char c : query.toCharArray()){
  if (c == '"') htmlQuery += "&quot;";
  else htmlQuery += c;
}
if (results == null) {results = new Vector<searchResult>(); System.out.println("results null");}
%>

<!DOCTYPE html>
<html>
<head>
    <title><%=(query != null && !query.isBlank())?(query+" - "):""%>gp3Search</title>
    <link rel="icon" type="image/x-icon" href="images/favicon.ico">
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap@4.3.1/dist/css/bootstrap.min.css" integrity="sha384-ggOyR0iXCbMQv3Xipma34MD+dH/1fQ784/j6cY/iJTQUOhcWr7x9JvoRxT2MZw1T" crossorigin="anonymous">
    <script src="https://cdn.jsdelivr.net/npm/bootstrap@4.3.1/dist/js/bootstrap.min.js" integrity="sha384-JjSmVgyd0p3pXB1rRibZUAYoIIy6OrQ6VrjIEaFf/nJGzIxFDsf4x0xIM+B07jRM" crossorigin="anonymous"></script>    <style>
        /* Fix the search box width */
       #search-bar-wrapper {
            margin: 0 auto;
            display: flex;
            justify-content: center;
            margin-bottom: 10px;
        }

        #search-bar{
            margin-right: 0.5%;
        }

        .card{
            max-width: 60%;
            margin: 1% auto;
        }

        img{
          display: block;
          margin: 10% auto;
          margin-bottom: 2%;
        }

        body{
          background-color: #f8f9fa;
        }

        #results-header{
          text-align: center;
        }
    </style>
    <script>
      function lookup(searchString){
      console.log(searchString)
        // Get the search bar and search button elements
        const searchBar = document.getElementById("search-bar");
        const searchButton = document.getElementById("search-button");
        // simulate search
        searchBar.value = searchString;
        searchButton.click();
      }
    </script>
</head>

<body>
    <img src="images/title2.gif" alt="logo">
    <div id="searchbox">
        <form action="${contextPath}/gp3/search" method="get" class="form-inline" id="search-bar-wrapper">
          <input type="text" class="form-control" name="query" placeholder="Enter search query here" required id="search-bar" value="<%=(query==null)?"":htmlQuery %>">
          <button type="submit" class="btn btn-primary" id="search-button">Search</button>
        </form>
    </div>

  <% if (query != null && !query.isBlank()){ %>
    <div id="results-header"><%=results.size() %> result(s) for <%=query %></div>
  <% } %>
    

    <div id="results-list">
        <%
            for (searchResult result : results) {
        %>
        <div class="card bg-light mb-3">
            <div class="card-header">Score: <%= result.score %></div>
            <div class="card-body">
                <h5 class="card-title text-primary"><a href="<%=result.pageUrl %>"><%= result.pageTitle %></a></h5>
                <h6 class="card-subtitle mb-2 text-muted"><%= result.pageUrl %></h6>
                <div class="card-text"><%=result.metaData %></div>
                <div class="card-text"><%= result.keywordFreqs %><button class="btn btn-link" onclick="lookup('<%=result.keywords %>')">Get similar pages</button></div>
                <br>
                <h6 class="card-subtitle mb-2">Parent links:</h6>
                <%
                  for (String parentLink : result.parentLinks) {
                %>    
                <div class="card-link"><%=parentLink %></div>
                <%
                  }
                %>
                <br>
                <h6 class="card-subtitle mb-2">Child links:</h6>
                <%
                  for (String childLink : result.childLinks) {
                %>    
                <div class="card-link"><%=childLink %></div>
                <%
                  }
                %>

            </div>
        </div>
        <%
            }
        %>
    </div>

</body>
</html>