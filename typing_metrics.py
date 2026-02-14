import csv
import math
from collections import Counter, defaultdict
from itertools import combinations

# Helper: Read CSV and return list of lists (skip header)
def read_csv(filepath):
    # Detect delimiter (tab or comma) from first line
    with open(filepath, newline='') as csvfile:
        first_line = csvfile.readline()
        delimiter = '\t' if '\t' in first_line else ','
    with open(filepath, newline='') as csvfile:
        reader = csv.reader(csvfile, delimiter=delimiter)
        header = next(reader)
        data = []
        for row in reader:
            alleles = []
            for x in row[1:]:  # skip ST column
                val = x.strip()
                if val == '' or val == '?' or val is None:
                    alleles.append(-1)
                else:
                    try:
                        alleles.append(int(val))
                    except ValueError:
                        alleles.append(-1)
            data.append(alleles)
        return data

# Directional Hamming Distance (as per Java logic)
def directional_hamming(seq1, seq2):
    if len(seq1) != len(seq2):
        raise ValueError('Sequences must be of equal length')
    dist = 0
    for a, b in zip(seq1, seq2):
        if (b in (-1, 0)) and (a not in (-1, 0)):
            dist += 1
        elif (b not in (-1, 0)) and (a in (-1, 0)):
            continue
        elif a > b:
            dist += 1
    return dist

def compute_pairwise_distances(data):
    dists = []
    for i, j in combinations(range(len(data)), 2):
        d = directional_hamming(data[i], data[j])
        dists.append(d)
    return dists

def compute_entropy(data):
    # data: list of lists
    n = len(data[0])
    entropies = []
    for col in range(n):
        values = [row[col] for row in data]
        counts = Counter(values)
        total = sum(counts.values())
        entropy = 0.0
        for count in counts.values():
            p = count / total
            entropy -= p * math.log2(p)
        entropies.append(entropy)
    return entropies

def main(filepath):
    data = read_csv(filepath)
    dists = compute_pairwise_distances(data)
    if not dists:
        print('Not enough data for pairwise distances.')
        return
    print('Max distance:', max(dists))
    print('Min distance:', min(dists))
    print('Mean distance:', sum(dists)/len(dists))
    dists_sorted = sorted(dists)
    n = len(dists_sorted)
    median = dists_sorted[n//2] if n%2==1 else (dists_sorted[n//2-1]+dists_sorted[n//2])/2
    print('Median distance:', median)
    mode = Counter(dists).most_common(1)[0][0]
    print('Modal distance:', mode)
    entropies = compute_entropy(data)
    for idx, ent in enumerate(entropies):
        print(f'Entropy at position {idx}: {ent:.4f}')

if __name__ == '__main__':
    import sys
    if len(sys.argv) != 2:
        print('Usage: python typing_metrics.py <csv_file>')
    else:
        main(sys.argv[1])
