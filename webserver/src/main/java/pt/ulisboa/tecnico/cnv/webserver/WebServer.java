package pt.ulisboa.tecnico.cnv.webserver;

import java.net.InetSocketAddress;
import com.sun.net.httpserver.HttpServer;

import pt.ulisboa.tecnico.cnv.capturetheflag.CaptureTheFlagHandler;
import pt.ulisboa.tecnico.cnv.fifteenpuzzle.FifteenPuzzleHandler;
import pt.ulisboa.tecnico.cnv.gameoflife.GameOfLifeHandler;

public class WebServer {
    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.createContext("/", new RootHandler());
        server.createContext("/gameoflife", new GameOfLifeHandler());
        server.createContext("/fifteenpuzzle", new FifteenPuzzleHandler());
        server.createContext("/capturetheflag", new CaptureTheFlagHandler());
        server.createContext("/test", new TestHandler());
        server.start();
        System.out.println("Web server started on port 8000!");
    }
}
