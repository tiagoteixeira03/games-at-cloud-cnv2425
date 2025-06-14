import requests
import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import os

webserver_ip = "localhost"  # Change to your server's IP if needed
server_url = f"http://{webserver_ip}:8000"

def send_request(game, params):
    """
    Sends a GET request to the server and prints the response.
    """
    try:
        response = requests.get(f"{server_url}/{game}", params=params)
        elapsed_time = response.elapsed.total_seconds()
        if response.status_code != 200:
            print(f"Error: {response.status_code} - {response.text}")
            return None, elapsed_time
        requestStatistics = response.json().get("requestStatistics")
        #print(f"Params: {params}, requestStatistics: {requestStatistics}, Elapsed Time: {elapsed_time:.4f} seconds")
        return requestStatistics, elapsed_time
    except requests.exceptions.RequestException as e:
        print("Error sending request:", e)

def analyze_elapsedtimes(game, params_list):
    results = []
    elapsed_times = []
    for params in params_list:
        requestStatistics, elapsed_time = send_request(game, params)
        elapsed_times.append(elapsed_time)
        
    return sum(elapsed_times) / len(elapsed_times) if elapsed_times else 0


def main():
    capturetheflag_params = [
        {"gridSize": 30, "numBlueAgents": blue, "numRedAgents": red, "flagPlacementType": "A"}
        for blue, red in zip(range(5, 30), range(5, 30))
    ]
    fifteenpuzzle_params = [
        {"size": size, "shuffles": shuffles}
        for size in range(5, 15, 2) for shuffles in range(10, 50, 10)
    ]
    gameoflife_params = [
        {"mapFilename": "glider-10-10.json", "iterations": iterations}
        for iterations in range(1000, 20000, 1000)
    ]
    params_list = {
        "capturetheflag": capturetheflag_params,
        "fifteenpuzzle": fifteenpuzzle_params,
        "gameoflife": gameoflife_params
    }

    for game, params in params_list.items():
        average_elapsed_time = analyze_elapsedtimes(game, params)
        print(f"Average elapsed time for {game}: {average_elapsed_time:.4f} seconds")

if __name__ == "__main__":
    main()