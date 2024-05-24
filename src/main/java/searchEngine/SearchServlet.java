package searchEngine;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;

public class SearchServlet extends HttpServlet {
    Searcher searcher;

    @Override
    public void init(ServletConfig config) throws ServletException {
        ServletContext servletContext = config.getServletContext();
        // Initialize the searcher instance and store it in the servlet context
        searcher = new Searcher();
        servletContext.setAttribute("searcher", searcher);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // Retrieve the searcher instance from the servlet context
//        Searcher searcher = (Searcher) getServletContext().getAttribute("searcher");

        // Get the query parameter from the request
        String query = req.getParameter("query");

        if (query == null) query = "";

        // Use the searcher instance to perform the search
        List<Searcher.searchResult> results = searcher.search(query);
        for (Searcher.searchResult result : results) {
            System.out.println(result.pageTitle);
        }

        // Set the results as a request attribute
        req.setAttribute("results", results);

        // Set the query
        req.setAttribute("query", query);

        // Render the search results using the same JSP file
        req.getRequestDispatcher("search.jsp").include(req, resp);
    }

}