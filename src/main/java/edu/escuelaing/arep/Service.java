package edu.escuelaing.arep;

/**
 * The Service interface defines a contract for implementing web services.
 * Classes or lambda expressions that implement this interface must provide
 * a concrete implementation of the getValue method, which is responsible
 * for processing HTTP requests and generating appropriate responses.
 */
public interface Service {

    /**
     * Processes an HTTP request and generates a response.
     *
     * @param request  The incoming HTTP request, typically containing details such as the URL, query parameters, and body.
     * @param response The outgoing HTTP response, which will be populated and returned to the client.
     * @return A string representing the body of the HTTP response.
     */
    public String getValue(String request, String response);
}
