import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class HealthCheck {

	private HealthCheck() {
	}

	public static void main(String[] args) throws IOException, InterruptedException {
		HttpClient client = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(2))
			.build();
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:8080/actuator/health"))
			.timeout(Duration.ofSeconds(2))
			.GET()
			.build();

		if (client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() != 200) {
			System.exit(1);
		}
	}
}
