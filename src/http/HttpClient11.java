package http;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.URL;

/**
 * Implements a http 1.1 client
 * @author luistripa
 */
public class HttpClient11 implements HttpClient {

    private static final String HTTP_SUCCESS = "200";
    private static final String HTTP_PARTIAL = "206";
    private static final String GET_FORMAT_STR = "GET %s HTTP/1.1\r\nHost: localhost:9999\r\n%s\r\nConnection: keep-alive\r\nAccept: */*\r\n\r\n";
    private static final String GET_RANGE_FORMAT_STR = "GET %s HTTP/1.1\r\nHost: localhost:9999\r\n%s\r\n%s\r\nConnection: keep-alive\r\nAccept: */*\r\n\r\n";
    private static final String RANGE_HEADER = "Range: bytes=%d-%d";

    private Socket socket;

    static private byte[] getContents(InputStream in) throws IOException {
        String reply = Http.readLine(in);
        if (!(reply.contains(HTTP_SUCCESS)||reply.contains(HTTP_PARTIAL))) {
            throw new RuntimeException(String.format("HTTP request failed: [%s]", reply));
        }

        int length = 0;
        while ((reply = Http.readLine(in)).length() > 0) {
            if (reply.startsWith("Content-Length")) {
                reply = reply.replace("Content-Length: ", "");
                length = Integer.parseInt(reply);
            }
        }
        return in.readNBytes(length);
    }

    /**
     * Gets the full contents of a resource
     *
     * @param urlStr - the url of the requested resource
     * @return the byte contents of the resource, or null if an error occurred
     */
    @Override
    public byte[] doGet(String urlStr) {

        try {
            URL url = new URL(urlStr);
            int port = url.getPort();
            if (socket == null || socket.isClosed()) {
                socket = new Socket(url.getHost(), port < 0 ? url.getDefaultPort() : port);
            }
            String request = String.format(GET_FORMAT_STR, url.getFile(), USER_AGENT);
            //System.out.println(request);
            socket.getOutputStream().write(request.getBytes());
            return getContents(socket.getInputStream());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Gets a range of a resource' contents from a given offset
     *
     * @param urlStr   - the url of the requested resource
     * @param start - the start offset of the requested range
     * @return the contents range of the resource, or null if an error occurred
     */
    @Override
    public byte[] doGetRange(String urlStr, long start) {
        return new byte[0];
    }

    /**
     * Gets a range of a resource' contents
     *
     * @param urlStr   - the url of the requested resource
     * @param start - the start offset of the requested range
     * @param end   - the end offset of the requested range (inclusive)
     * @return the contents range of the resource, or null if an error occurred
     */
    @Override
    public byte[] doGetRange(String urlStr, long start, long end) {
        try {
            URL url = new URL(urlStr);
            int port = url.getPort();

            if (socket == null || socket.isClosed()) {
                socket = new Socket(url.getHost(), port < 0 ? url.getDefaultPort() : port);
            }

            String request = String.format(
                    GET_RANGE_FORMAT_STR,
                    url.getFile(),
                    USER_AGENT,
                    String.format(RANGE_HEADER, start, end));
            //System.out.println(request);
            socket.getOutputStream().write(request.getBytes());
            return getContents(socket.getInputStream());

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
