import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class MockServerHealthcheck {

    public static void main(String[] args) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:1080/mockserver/status"))
                .PUT(HttpRequest.BodyPublishers.noBody())
                .build();
        int status = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        System.exit(status == 200 ? 0 : 1);
    }
}
