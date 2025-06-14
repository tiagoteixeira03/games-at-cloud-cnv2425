package pt.ulisboa.tecnico.cnv;


public record WorkerResponse(int statusCode, String body) {
    boolean isSuccess() { return statusCode == 200;}
}

