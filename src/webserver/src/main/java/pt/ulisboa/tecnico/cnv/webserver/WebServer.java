package pt.ulisboa.tecnico.cnv.webserver;

import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpServer;

import pt.ulisboa.tecnico.cnv.capturetheflag.CaptureTheFlagHandler;
import pt.ulisboa.tecnico.cnv.fifteenpuzzle.FifteenPuzzleHandler;
import pt.ulisboa.tecnico.cnv.gameoflife.GameOfLifeHandler;

public class WebServer {
    public static void main(String[] args) throws Exception {
        int port = 8000; // default port
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number provided. Using default port 8000.");
            }
        }
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.createContext("/", new RootHandler());
        server.createContext("/gameoflife", new GameOfLifeHandler());
        server.createContext("/fifteenpuzzle", new FifteenPuzzleHandler());
        server.createContext("/capturetheflag", new CaptureTheFlagHandler());
        server.createContext("/test", new TestHandler());
        server.start();
        System.out.println("Web server started on port " + port);
    }
}
