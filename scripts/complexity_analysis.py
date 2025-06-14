import requests
import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import os
from scipy.stats import linregress

webserver_ip = "localhost"  # Change to your server's IP if needed
webserver_port = 8000
server_url = f"http://{webserver_ip}:{webserver_port}"

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
        print(f"Params: {params}, requestStatistics: {requestStatistics}, Elapsed Time: {elapsed_time:.4f} seconds")
        return requestStatistics, abs(elapsed_time)
    except requests.exceptions.RequestException as e:
        print("Error sending request:", e)

def analyze_complexity(game, params_list):
    results = []
    for params in params_list:
        requestStatistics, elapsed_time = send_request(game, params)
        if requestStatistics is not None:
            results.append({
                "complexity": requestStatistics.get("complexity", 0),
                "nblocks": requestStatistics.get("nblocks", 0),
                "nmethod": requestStatistics.get("nmethod", 0),
                "ninsts": requestStatistics.get("ninsts", 0),
                "ndataWrites": requestStatistics.get("ndataWrites", 0),
                "ndataReads": requestStatistics.get("ndataReads", 0),
                "elapsed_time": elapsed_time
            })
        else:
            print(f"Failed to get complexity for params: {params}")
    return pd.DataFrame(results)

def plot_results(df, game_name):
    """
    Creates and saves a separate plot for each metric vs elapsed time.
    Includes a linear regression line and R² value.
    Returns a dictionary of R² values per metric.
    """
    metrics = ['complexity', 'nblocks', 'nmethod', 'ninsts', 'ndataWrites', 'ndataReads']
    df_sorted = df.sort_values(by='elapsed_time')

    os.makedirs("charts", exist_ok=True)

    r2_results = {}

    for metric in metrics:
        x = df_sorted['elapsed_time'].values
        y = df_sorted[metric].values

        # Linear regression
        slope, intercept, r_value, p_value, std_err = linregress(x, y)
        y_pred = slope * x + intercept
        r_squared = r_value ** 2
        r2_results[metric] = r_squared

        # Plotting
        plt.figure(figsize=(10, 6))
        plt.plot(x, y, 'o', alpha=0.7, label=f'{metric} (data)')
        plt.plot(x, y_pred, 'r-', label=f'Linear Fit: y={slope:.2f}x + {intercept:.2f}\nR² = {r_squared:.4f}')
        plt.xlabel('Elapsed Time (s)')
        plt.ylabel(metric)
        plt.title(f'{metric} vs Elapsed Time - {game_name}')
        plt.grid(True, alpha=0.3)
        plt.legend()
        plt.tight_layout()

        # Save the figure
        filename = f"charts/{game_name}_{metric}_vs_elapsed_time.png"
        plt.savefig(filename)
        plt.close()

    return r2_results

def save_r2_table_image(r2_data, output_path="charts/r2_comparison_table.png"):
    """
    Saves a table of R² values as an image.
    """
    r2_df = pd.DataFrame(r2_data).T  # Games as rows
    fig, ax = plt.subplots(figsize=(12, 4))
    ax.axis('off')
    table = ax.table(
        cellText=np.round(r2_df.values, 4),
        colLabels=r2_df.columns,
        rowLabels=r2_df.index,
        cellLoc='center',
        loc='center'
    )
    table.scale(1, 1.5)
    table.auto_set_font_size(False)
    table.set_fontsize(10)
    plt.title("R² Comparison Table", fontsize=14, pad=20)
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    plt.savefig(output_path, bbox_inches='tight')
    plt.close()
    print(f"R² comparison table saved as image: {output_path}")

def main():
    capturetheflag_params = [
        {"gridSize": gridSize, "numBlueAgents": blue, "numRedAgents": red, "flagPlacementType": flagtype}
        for gridSize, blue, red in zip(range(10,40), range(5, 35), range(5, 35))
        for flagtype in ["A", "B", "C"]
    ]
    fifteenpuzzle_params = [
        {"size": size, "shuffles": shuffles}
        for shuffles in range(70, 76, 1)
        for size in range(10, 15, 1)
    ]
    gameoflife_params = [
        {"mapFilename": "glider-10-10.json", "iterations": iterations}
        for iterations in range(1000, 20000, 1000)
    ]
    params_list = {
        #"capturetheflag": capturetheflag_params,
        "fifteenpuzzle": fifteenpuzzle_params,
        "gameoflife": gameoflife_params
    }

    all_r2 = {}

    for game, params in params_list.items():
        df = analyze_complexity(game, params)
        # r2_results = plot_results(df, game)
        # all_r2[game] = r2_results

    # Save R² table as image
    # save_r2_table_image(all_r2)

if __name__ == "__main__":
    main()
