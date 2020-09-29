package advisor;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

class SpotifyClient {

    static SpotifyClientViewConsole viewInstance = null;
    static SpotifyClientModel modelInstance = null;
    static SpotifyClient controllerInstance = null;

    public static SpotifyClient getInstance() {
        if (controllerInstance == null) {
            controllerInstance = new SpotifyClient();
            viewInstance = new SpotifyClientViewConsole(controllerInstance);
            modelInstance = new SpotifyClientModel("3fa6538be85e4353be7a8c5e5aad8e9e", "ca8a05da79e240818636bcd3b7ebcde1");
        }
        return controllerInstance;
    }

    void start(String[] args) {
        viewInstance.consoleUIProcess(args);
    }

    boolean checkAuth() {
        if (modelInstance.checkAuth()) {
            return true;
        } else {
            viewInstance.showMessage("Please, provide access for application.");
            return false;
        }
    }

    void authorize() {
        modelInstance.authorize();
    }

    void categories() {
        if (! checkAuth()) {
            return;
        }

        try {
            List<String> categoriesNames = modelInstance.getCategories();
            viewInstance.showResults(categoriesNames);
        } catch (HttpRequestSpotifyApiException e) {
            viewInstance.showMessage(e.getMessage());
        }
    }

    void featured() {
        if (! checkAuth()) {
            return;
        }

        try {
            Map<String,String> featuredPlaylists = modelInstance.getFeaturedPlaylists();

            List<String> results = new ArrayList<>();
            for (Map.Entry<String, String> entry : featuredPlaylists.entrySet()) {
                String playlistName = entry.getKey();
                String playlistUrl = entry.getValue();
                results.add(playlistName + "\n" + playlistUrl + "\n");
            }

            viewInstance.showResults(results);
        } catch (HttpRequestSpotifyApiException e) {
            viewInstance.showMessage(e.getMessage());
        }
    }

    void newReleases() {
        if (! checkAuth()) {
            return;
        }

        try {
            List<String> newReleases = modelInstance.getNewReleases();
            viewInstance.showResults(newReleases);
        } catch (HttpRequestSpotifyApiException e) {
            viewInstance.showMessage(e.getMessage());
        }
    }

    void playlists(String categoryName) {
        if (! checkAuth()) {
            return;
        }

        try {
            List<String> playlists = modelInstance.getPlaylists(categoryName);
            viewInstance.showResults(playlists);
        } catch (HttpRequestSpotifyApiException e) {
            viewInstance.showMessage(e.getMessage());
        }
    }

    public void setSpotifyAccessServer(String spotifyAccessServer) {
        modelInstance.spotifyAccessServer = spotifyAccessServer;
    }

    public void setSpotifyApiServer(String spotifyApiServer) {
        modelInstance.spotifyApiServer = spotifyApiServer;
    }
}

class SpotifyClientModel {

    Map<String,String> authorizationCodes = new HashMap<>();

    String clientId;
    String clientSecret;
    String spotifyAccessServer = "https://accounts.spotify.com";
    String spotifyApiServer = "https://api.spotify.com";

    int httpPort = 8000;
    String redirectUri = "http://localhost:" + Integer.toString(httpPort);

    boolean authorized = false;

    HttpServer server;

    SpotifyClientModel(String clientId, String clientSecret) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public boolean checkAuth() {
        if (! authorized) {
            System.out.println("Please, provide access for application.");
        }
        return authorized;
    }

    void startServer() throws IOException {
        server = HttpServer.create();
        server.bind(new InetSocketAddress(httpPort), 0);
        server.createContext("/",
                new HttpHandler() {
                    public void handle(HttpExchange exchange) throws IOException {
                        Map<String, String> queryMap = httpQueriesParamsToMap(exchange.getRequestURI().getQuery());

                        if (queryMap != null && queryMap.containsKey("code")) {
                            System.out.println("code received");
                            String code = queryMap.get("code");
                            authorizationCodes.put("authorization_code", code);
                            String successResponse = "Got the code. Return back to your program.";
                            exchange.sendResponseHeaders(200, successResponse.length());
                            exchange.getResponseBody().write(successResponse.getBytes());
                            exchange.getResponseBody().close();
                            getToken(code, redirectUri);
                        }

                        if (queryMap == null || ! queryMap.containsKey("code") || ! authorized) {
//                            String failResponse = "Authorization code not found. Try again.";
                            String failResponse = "Not found authorization code. Try again.";
                            exchange.sendResponseHeaders(401, failResponse.length());
                            exchange.getResponseBody().write(failResponse.getBytes());
                            exchange.getResponseBody().close();
                        }
                    }
                }
        );
        server.start();
    }

    void stopServer() {
        if (server != null) {
            server.stop(1);
        }
    }

    void getToken(String code, String redirectUri) {
        System.out.println("Making http request for access_token...");

        String tokenUrl = spotifyAccessServer + "/api/token";

        HttpClient client = HttpClient.newBuilder().build();

        HttpRequest request = HttpRequest.newBuilder()
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Authorization", "Basic " + encodeBase64(clientId + ":" + clientSecret))
                .uri(URI.create(tokenUrl))
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=authorization_code&code=" + code + "&redirect_uri=" + redirectUri))
                .build();

        HttpResponse<String> response = null;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException|InterruptedException e) {
            e.printStackTrace();
        }

        if (response != null) {
            System.out.println("Success!");

            JsonObject accessTokenJson = JsonParser.parseString(response.body()).getAsJsonObject();
            String accessToken = accessTokenJson.get("access_token").getAsString();

            authorizationCodes.put("access_token", accessToken);
            authorized = true;
        } else {
//            String errorMessage = "Not found authorization code. Try again.";
//            System.out.println(errorMessage);
            authorized = false;
        }
    }

    String encodeBase64(String textToEncode) {
        return Base64.getEncoder().encodeToString(textToEncode.getBytes());
    }

    void authorize() {
        try {
            startServer();
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println(e.getMessage());
            System.out.println("There was a problem starting the http server in the spotifyAuth ");
        }

        System.out.println("use this link to request the access code:");
        System.out.println(spotifyAccessServer + "/authorize?client_id=" + clientId + "&redirect_uri=http://localhost:8000&response_type=code");
        System.out.println("waiting for code...");

        while (! authorized) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        stopServer();
    }

    Map<String, String> httpQueriesParamsToMap(String query) {
        // TODO Support cases like queries params with & or =. Also it doesn't support cases like x=111&x=222
        if (query == null) {
            return null;
        }

        Map<String, String> result = new HashMap<>();
        for (String param : query.split("&")) {
            String[] entry = param.split("=");
            if (entry.length > 1) {
                result.put(entry[0], entry[1]);
            }else{
                result.put(entry[0], "");
            }
        }
        return result;
    }

    private HttpResponse<String> getHttpSpotifyApiRequest(String apiResource) {
        HttpClient client = HttpClient.newBuilder().build();

        HttpRequest categoriesRequest = HttpRequest.newBuilder()
                .header("Authorization", "Bearer " + getAccessToken())
                .uri(URI.create(spotifyApiServer + apiResource))
                .GET()
                .build();

        HttpResponse<String> response = null;
        try {
            response = client.send(categoriesRequest, HttpResponse.BodyHandlers.ofString());
        } catch (IOException|InterruptedException e) {
            e.printStackTrace();
        }
        return response;
    }

    private String getAccessToken() {
        return authorizationCodes.get("access_token");
    }

    public List<String> getCategories() throws HttpRequestSpotifyApiException {
        HttpResponse<String> response = getHttpSpotifyApiRequest("/v1/browse/categories");

        if (response != null && response.statusCode() == 200) {
            JsonObject categoriesJson = JsonParser.parseString(response.body()).getAsJsonObject().get("categories").getAsJsonObject();

            List<String> categoriesNames = new ArrayList<>();
            for (JsonElement item : categoriesJson.getAsJsonArray("items")) {
                categoriesNames.add(item.getAsJsonObject().get("name").getAsString());
            }
            return categoriesNames;
        } else {
            String requestErrorMessage = (response != null) ? response.body() : "";
            throw new HttpRequestSpotifyApiException("There was an issue getting the categories. See error message: " + requestErrorMessage);
        }
    }

    public Map<String,String> getFeaturedPlaylists() throws HttpRequestSpotifyApiException {

        HttpResponse<String> response = getHttpSpotifyApiRequest("/v1/browse/featured-playlists");

        if (response != null && response.statusCode() == 200) {
            JsonObject playlistsJson = JsonParser.parseString(response.body()).getAsJsonObject().get("playlists").getAsJsonObject();
            Map<String,String> playlists = new LinkedHashMap<>();
            for (JsonElement item : playlistsJson.getAsJsonArray("items")) {
                String playlistName = item.getAsJsonObject().get("name").getAsString();
                String playlistUrl = item.getAsJsonObject().get("external_urls").getAsJsonObject().get("spotify").getAsString();
                playlists.put(playlistName, playlistUrl);
            }
            return playlists;
        } else {
            String requestErrorMessage = (response != null) ? response.body() : "";
            throw new HttpRequestSpotifyApiException("There was an issue getting the featured playlists. See error message: " + requestErrorMessage);
        }
    }

    public List<String> getNewReleases() throws HttpRequestSpotifyApiException {

        HttpResponse<String> response = getHttpSpotifyApiRequest("/v1/browse/new-releases");

        if (response != null && response.statusCode() == 200) {
            JsonObject newAlbumsJson = JsonParser.parseString(response.body()).getAsJsonObject().get("albums").getAsJsonObject();
            List<String> newReleases = new ArrayList<>();
            for (JsonElement item : newAlbumsJson.getAsJsonArray("items")) {
                String albumName = item.getAsJsonObject().get("name").getAsString();

                List<String> albumArtists = new ArrayList<>();
                item.getAsJsonObject().getAsJsonArray("artists").forEach(artistJsonElement -> albumArtists.add(artistJsonElement.getAsJsonObject().get("name").getAsString()));

                String albumUrl = item.getAsJsonObject().get("external_urls").getAsJsonObject().get("spotify").getAsString();

                newReleases.add(albumName + "\n" + albumArtists + "\n" + albumUrl + "\n");
            }
            return newReleases;
        } else {
            String requestErrorMessage = (response != null) ? response.body() : "";
            throw new HttpRequestSpotifyApiException("There was an issue getting the new releases. See error message: " + requestErrorMessage);
        }

    }

    public List<String> getPlaylists(String categoryName) throws HttpRequestSpotifyApiException  {

        String errorMessage = "Specified id doesn't exist";
        String categoryId = errorMessage;

        HttpResponse<String> responseCategories = getHttpSpotifyApiRequest("/v1/browse/categories");
        if (responseCategories != null && responseCategories.statusCode() == 200) {
            JsonObject categoriesJson = JsonParser.parseString(responseCategories.body()).getAsJsonObject().get("categories").getAsJsonObject();
            for (JsonElement item : categoriesJson.getAsJsonArray("items")) {
                if (categoryName.equals(item.getAsJsonObject().get("name").getAsString())) {
                    categoryId = item.getAsJsonObject().get("id").getAsString();
                }
            }
        }

        int responsePlaylistsStatusCode = 500;
        if (! categoryId.equals(errorMessage)) {
            HttpResponse<String> responsePlaylists = getHttpSpotifyApiRequest("/v1/browse/categories/" + categoryId + "/playlists");

            if(responsePlaylists != null && responsePlaylists.statusCode() == 200 && responsePlaylists.body().contains("playlists")) {
                JsonObject categoriesJson = JsonParser.parseString(responsePlaylists.body()).getAsJsonObject().get("playlists").getAsJsonObject();

                List<String> playlists = new ArrayList<>();
                for (JsonElement item : categoriesJson.getAsJsonArray("items")) {
                    String playlistName = item.getAsJsonObject().get("name").getAsString();
                    String playlistUrl = item.getAsJsonObject().get("external_urls").getAsJsonObject().get("spotify").getAsString();

                    playlists.add(playlistName + "\n" + playlistUrl + "\n");
                }
                return playlists;
            } else {
                errorMessage = JsonParser.parseString(responsePlaylists.body()).getAsJsonObject().get("error").getAsJsonObject().get("message").getAsString();
                responsePlaylistsStatusCode = 404;
            }
        }

        if (categoryId.equals(errorMessage) || responsePlaylistsStatusCode != 200 || responseCategories.statusCode() != 200) {
            throw new HttpRequestSpotifyApiException(errorMessage);
        }
        return List.of();
    }
}

class HttpRequestSpotifyApiException extends Exception {
    public HttpRequestSpotifyApiException(String errorMessage) {
        super(errorMessage);
    }
}

interface ConsoleShowResultsAlgorithm {
    void showResults(List<String> messages);
}

class ConsoleShowResultsRaw implements ConsoleShowResultsAlgorithm {

    @Override
    public void showResults(List<String> messages) {
        messages.forEach((String message) -> System.out.println(message));
    }

}

class ConsoleShowResultsPaginated implements ConsoleShowResultsAlgorithm {

    int entriesPerPage;

    ConsoleShowResultsPaginated(int entriesPerPage) {
        this.entriesPerPage = entriesPerPage;
    }

    @Override
    public void showResults(List<String> messages) {
        int currentPage = 1;
        int totalPages = ((messages.size() / entriesPerPage) < 1) ? 1 : messages.size() / entriesPerPage;
        showNResults(messages, currentPage, totalPages);

        Scanner scanner = new Scanner(System.in);
        while (true) {
            String input = scanner.nextLine();
            if (input.equals("prev")) {
                if (currentPage == 1) {
                    System.out.println("No more pages.");
                } else {
                    currentPage--;
                }
            } else if (input.equals("next")) {
                if (currentPage == totalPages) {
                    System.out.println("No more pages.");
                } else {
                    currentPage++;
                }
            } else {
                break;
            }
            showNResults(messages, currentPage, totalPages);
        }
    }

    private void showNResults(List<String> messages, int pageNumber, int totalPages) {
        int fromIndex = (pageNumber - 1) * entriesPerPage;
        List<String> messagesInCurrentPage = messages.subList(fromIndex, fromIndex + entriesPerPage);

        messagesInCurrentPage.forEach((String message) -> System.out.println(message));

        System.out.println("---PAGE " + pageNumber + " OF " + totalPages + "---");
    }

}

class SpotifyClientViewConsole {

    SpotifyClient spotifyClient;
    String spotifyAccessServer = "";
    String spotifyApiServer = "";
    int entriesPerPage = 5;
    private ConsoleShowResultsAlgorithm showResultsAlgorithm;

    SpotifyClientViewConsole(SpotifyClient spotifyClient) {
        this.spotifyClient = spotifyClient;
    }

    public void showResults(List<String> resultsLines) {
        showResultsAlgorithm.showResults(resultsLines);
    }

    public void showMessage(String message) {
        showResultsAlgorithm.showResults(List.of(message));
    }

    public void consoleUIProcess(String[] args) {
        parseArgs(args);
        showResultsAlgorithm = new ConsoleShowResultsPaginated(entriesPerPage);
        if (! spotifyAccessServer.isEmpty()) {
            spotifyClient.setSpotifyAccessServer(spotifyAccessServer);
        }
        if (! spotifyAccessServer.isEmpty()) {
            spotifyClient.setSpotifyApiServer(spotifyApiServer);
        }

        Scanner scanner = new Scanner(System.in);

        while (true) {
            String input = scanner.nextLine();
            if (input.equals("featured")) {
                spotifyClient.featured();
            } else if (input.equals("new")) {
                spotifyClient.newReleases();
            } else if (input.equals("categories")) {
                spotifyClient.categories();
            } else if (input.contains("playlists")) {
                String categoryName = input.substring("playlists".length() + 1);
                spotifyClient.playlists(categoryName);
            } else if (input.equals("auth")) {
                spotifyClient.authorize();
            } else if (input.equals("exit")) {
                exit();
            } else {
                throw new RuntimeException("Command not supported");
            }
        }
    }

    public void parseArgs(String[] args) {
        for (int i = 0; i < args.length; i += 2) {
            if (args[i].equals("-access")) {
                spotifyAccessServer = args[i + 1];
            }
            if (args[i].equals("-resource")) {
                spotifyApiServer = args[i + 1];
            }
            if (args[i].equals("-page")) {
                entriesPerPage = Integer.valueOf(args[i + 1]);
            }
        }
    }

    public void exit() {
        showMessage("---GOODBYE!---");
    }
}




