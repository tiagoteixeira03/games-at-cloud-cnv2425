import csv
from collections import defaultdict

def calculate_average_ratios_per_game(filename):
    ratios_by_game = defaultdict(lambda: {
        'reads_per_method': [],
        'blocks_per_method': [],
        'insts_per_method': []
    })

    with open(filename, newline='') as csvfile:
        reader = csv.DictReader(csvfile)
        for row in reader:
            game = row['game']
            try:
                reads = int(row['nDataReads'])
                blocks = int(row['nblocks'])
                insts = int(row['ninsts'])
                methods = int(row['nmethod'])

                if methods == 0:
                    continue  # avoid division by zero

                ratios_by_game[game]['reads_per_method'].append(reads / methods)
                ratios_by_game[game]['blocks_per_method'].append(blocks / methods)
                ratios_by_game[game]['insts_per_method'].append(insts / methods)

            except (ValueError, KeyError):
                continue  # Skip rows with invalid/missing data

    # Compute averages
    avg_ratios_by_game = {}
    for game, data in ratios_by_game.items():
        avg_ratios_by_game[game] = {
            'reads_per_method': sum(data['reads_per_method']) / len(data['reads_per_method']) if data['reads_per_method'] else 0,
            'blocks_per_method': sum(data['blocks_per_method']) / len(data['blocks_per_method']) if data['blocks_per_method'] else 0,
            'insts_per_method': sum(data['insts_per_method']) / len(data['insts_per_method']) if data['insts_per_method'] else 0,
        }

    return avg_ratios_by_game

# Example usage
filename = 'results.csv'
average_ratios = calculate_average_ratios_per_game(filename)

# Print results
for game, ratios in average_ratios.items():
    print(f"\n{game}:")
    print(f"  nDataReads / nmethod: {ratios['reads_per_method']:.4f}")
    print(f"  nblocks    / nmethod: {ratios['blocks_per_method']:.4f}")
    print(f"  ninsts     / nmethod: {ratios['insts_per_method']:.4f}")
