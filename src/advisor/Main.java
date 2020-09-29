package advisor;

public class Main {

    public static void main(String[] args) {
        SpotifyClient spotifyClient = SpotifyClient.getInstance();
        spotifyClient.start(args);
    }

}

