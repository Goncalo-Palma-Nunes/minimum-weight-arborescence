#!/usr/bin/env python3
"""
Parser for entropy metrics file.
Reads entropy values for each sequence position and outputs a sorted JSON.
"""

import re
import json
import sys
from pathlib import Path


def parse_entropy_metrics(input_file, output_file):
    """
    Parse entropy metrics and distance statistics from input file and write to JSON sorted by entropy (descending).
    
    Args:
        input_file: Path to metrics.txt file
        output_file: Path to output JSON file
    """
    entropy_pairs = []
    distances = {}
    
    # Read the metrics file
    with open(input_file, 'r') as f:
        for line in f:
            line = line.strip()
            
            # Match distance statistics at the beginning
            if line.startswith('Max distance:'):
                distances['max_distance'] = float(line.split(':')[1].strip())
            elif line.startswith('Min distance:'):
                distances['min_distance'] = float(line.split(':')[1].strip())
            elif line.startswith('Mean distance:'):
                distances['mean_distance'] = float(line.split(':')[1].strip())
            elif line.startswith('Median distance:'):
                distances['median_distance'] = int(line.split(':')[1].strip())
            elif line.startswith('Modal distance:'):
                distances['modal_distance'] = int(line.split(':')[1].strip())
            
            # Match lines with format "Entropy at position X: Y"
            match = re.match(r'Entropy at position (\d+):\s*([\d.]+)', line)
            if match:
                position = int(match.group(1))
                entropy = float(match.group(2))
                entropy_pairs.append({'index': position, 'entropy': entropy})
    
    # Sort by entropy in descending order
    entropy_pairs.sort(key=lambda x: x['entropy'], reverse=True)
    
    # Create the output structure
    output_data = {
        'distance_statistics': distances,
        'entropy_data': entropy_pairs
    }
    
    # Write to JSON
    with open(output_file, 'w') as f:
        json.dump(output_data, f, indent=2)
    
    print(f"Parsed {len(entropy_pairs)} entropy values")
    print(f"Highest entropy: position {entropy_pairs[0]['index']} with value {entropy_pairs[0]['entropy']}")
    print(f"Lowest entropy: position {entropy_pairs[-1]['index']} with value {entropy_pairs[-1]['entropy']}")
    print(f"\nDistance Statistics:")
    for key, value in sorted(distances.items()):
        print(f"  {key}: {value}")
    print(f"\nOutput written to: {output_file}")


if __name__ == '__main__':
    input_path = Path.home() / 'tese_data' / 'metrics.txt'
    output_path = Path.home() / 'tese_data' / 'entropy_sorted.json'
    
    if not input_path.exists():
        print(f"Error: Input file not found: {input_path}", file=sys.stderr)
        sys.exit(1)
    
    parse_entropy_metrics(input_path, output_path)
